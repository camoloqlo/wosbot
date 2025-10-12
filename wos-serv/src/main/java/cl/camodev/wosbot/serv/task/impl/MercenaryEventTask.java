package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilRally;
import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.almac.entity.DailyTask;
import cl.camodev.wosbot.almac.repo.DailyTaskRepository;
import cl.camodev.wosbot.almac.repo.IDailyTaskRepository;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import java.awt.Color;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class MercenaryEventTask extends DelayedTask {
    private final IDailyTaskRepository iDailyTaskRepository = DailyTaskRepository.getRepository();
    private final ServTaskManager servTaskManager = ServTaskManager.getInstance();
    private Integer lastMercenaryLevel = null;
    private int attackAttempts = 0;
    private int flagNumber = 0;
    private boolean useFlag = false;
    private final int refreshStaminaLevel = 100;
    private final int minStaminaLevel = 40;
    private boolean scout = false;

    public MercenaryEventTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    protected void execute() {
        logInfo("=== Starting Mercenary Event ===");

        flagNumber = profile.getConfig(EnumConfigurationKey.MERCENARY_FLAG_INT, Integer.class);
        useFlag = flagNumber > 0;

        if (profile.getConfig(EnumConfigurationKey.INTEL_BOOL, Boolean.class)
                && useFlag
                && servTaskManager.getTaskState(profile.getId(), TpDailyTaskEnum.INTEL.getId()).isScheduled()) {
            // Make sure intel isn't about to run
            DailyTask intel = iDailyTaskRepository.findByProfileIdAndTaskName(profile.getId(), TpDailyTaskEnum.INTEL);
            if (ChronoUnit.MINUTES.between(LocalDateTime.now(), intel.getNextSchedule()) < 5) {
                reschedule(LocalDateTime.now().plusMinutes(35)); // Reschedule in 35 minutes, after intel has run
                logWarning(
                        "Intel task is scheduled to run soon. Rescheduling Mercenary Event to run 30min after intel.");
                return;
            }
        }

        // Verify if there's enough stamina to hunt, if not, reschedule the task
        if (!checkStaminaAndMarchesOrReschedule(minStaminaLevel, refreshStaminaLevel))
            return;

        int attempt = 0;
        while (attempt < 2) {
            if (navigateToEventScreen()) {
                handleMercenaryEvent();
                return;
            }
            logDebug("Navigation to Mercenary event failed, attempt " + (attempt + 1));
            sleepTask(300);
            tapBackButton();
            attempt++;
        }

        logWarning("Could not find the Mercenary event tab. Assuming event is unavailable. Rescheduling to reset.");
        reschedule(UtilTime.getGameReset());
    }

    private void handleMercenaryEvent() {
        try {
            // Select a mercenary event level if needed
            if (!selectMercenaryEventLevel()) {
                return; // If level selection failed, exit the task
            }

            // Check for scout or challenge buttons
            DTOImageSearchResult eventButton = findMercenaryEventButton();

            if (eventButton == null) {
                logInfo("No scout or challenge button found, assuming event is completed. Rescheduling to reset.");
                reschedule(UtilTime.getGameReset());
                return;
            }

            // Handle attack loss, if the attack was lost, skip flag selection to use
            // strongest march
            boolean sameLevelAsLastTime = false;
            logInfo("Previous mercenary level: " + lastMercenaryLevel);
            Integer currentLevel = checkMercenaryLevel();
            if (currentLevel != null) {
                sameLevelAsLastTime = (currentLevel.equals(lastMercenaryLevel));
                lastMercenaryLevel = currentLevel;
            }

            if (sameLevelAsLastTime) {
                attackAttempts++;
                logInfo("Mercenary level is the same as last time, indicating a possible attack loss. Skipping flag selection to use strongest march.");
            } else {
                attackAttempts = 0;
                logInfo("Mercenary level has changed since last time. Using flag selection if enabled.");
            }

            scoutAndAttack(eventButton, sameLevelAsLastTime);
        } catch (Exception e) {
            logError("An error occurred during the Mercenary Event task: " + e.getMessage(), e);
            reschedule(LocalDateTime.now().plusMinutes(30)); // Reschedule on error
        }
    }

    private Integer checkMercenaryLevel() {
        DTOTesseractSettings settings = new DTOTesseractSettings.Builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255)) // White text
                .setAllowedChars("0123456789") // Only allow digits and '/'
                .build();

        Integer level = readNumberValue(new DTOPoint(322, 867), new DTOPoint(454, 918), settings);
        if (level == null) {
            logWarning("No mercenary level found after OCR attempts.");
            return null;
        }

        logInfo("Current mercenary level: " + level);
        return level;
    }

    private boolean selectMercenaryEventLevel() {
        // Check if level selection is needed
        try {
            String textEasy = OCRWithRetries(new DTOPoint(112, 919), new DTOPoint(179, 953), 2);
            String textNormal = OCRWithRetries(new DTOPoint(310, 919),
                    new DTOPoint(410, 953), 2);
            String textHard = OCRWithRetries(new DTOPoint(540, 919), new DTOPoint(609, 953), 2);
            logDebug("OCR Results - Easy: '" + textEasy + "', Normal: '" + textNormal + "', Hard: '" + textHard + "'");
            if ((textEasy != null && textEasy.toLowerCase().contains("easy"))
                    || (textNormal != null && textNormal.toLowerCase().contains("normal"))
                    || (textHard != null && textHard.toLowerCase().contains("hard"))) {
                logInfo("Mercenary event level selection detected.");
            } else {
                logInfo("Mercenary event level selection not needed.");
                return true;
            }
        } catch (Exception e) {
            logError("Error checking mercenary event level selection: " + e.getMessage(), e);
            return false;
        }

        // First try to select a level in the Legend's Initiation tab
        tapPoint(new DTOPoint(512, 625)); // Tap Legend's Initiation tab
        sleepTask(1000);

        // Define difficulties in order from highest to lowest
        record DifficultyLevel(String name, DTOPoint point) {
        }
        DifficultyLevel[] difficultyLevels = {
                new DifficultyLevel("Insane", new DTOPoint(467, 1088)),
                new DifficultyLevel("Nightmare", new DTOPoint(252, 1088)),
                new DifficultyLevel("Hard", new DTOPoint(575, 817)),
                new DifficultyLevel("Normal", new DTOPoint(360, 817)),
                new DifficultyLevel("Easy", new DTOPoint(145, 817))
        };

        for (DifficultyLevel level : difficultyLevels) {
            logDebug("Attempting to select difficulty: " + level.name());
            tapPoint(level.point());
            sleepTask(2000);
            DTOImageSearchResult challengeCheck = searchTemplateWithRetries(
                    EnumTemplates.MERCENARY_DIFFICULTY_CHALLENGE, 90, 3);
            if (challengeCheck.isFound()) {
                sleepTask(1000);
                tapPoint(challengeCheck.getPoint());
                sleepTask(1000);
                tapPoint(new DTOPoint(504, 788)); // Tap the confirm button
                logInfo("Selected mercenary event difficulty: " + level.name() + " in Legend's Initiation tab.");
                sleepTask(2000);
                return true;
            }
            sleepTask(1000);
            tapBackButton();
        }

        // If not found, try the Champion's Initiation tab
        tapPoint(new DTOPoint(185, 625)); // Tap Champion's Initiation tab
        sleepTask(1000);

        for (DifficultyLevel level : difficultyLevels) {
            logDebug("Attempting to select difficulty: " + level.name());
            tapPoint(level.point());
            sleepTask(500);
            DTOImageSearchResult challengeCheck = searchTemplateWithRetries(
                    EnumTemplates.MERCENARY_DIFFICULTY_CHALLENGE, 90, 3);
            if (challengeCheck.isFound()) {
                sleepTask(1000);
                tapPoint(challengeCheck.getPoint());
                sleepTask(1000);
                tapPoint(new DTOPoint(504, 788)); // Tap the confirm button
                logInfo("Selected mercenary event difficulty: " + level.name() + " in Champion's Initiation tab.");
                sleepTask(2000);
                return true;
            }
            sleepTask(1000);
            tapBackButton();
        }

        // If no difficulty was selected, log a warning
        logWarning("Could not select a mercenary event difficulty. Rescheduling to try later.");
        reschedule(LocalDateTime.now().plusMinutes(10));
        return false;
    }

    private boolean navigateToEventScreen() {

        // Search for the events button
        DTOImageSearchResult eventsResult = searchTemplateWithRetries(EnumTemplates.HOME_EVENTS_BUTTON, 90, 3);
        if (!eventsResult.isFound()) {
            logWarning("The 'Events' button was not found.");
            return false;
        }

        tapPoint(eventsResult.getPoint());
        sleepTask(2000);

        // Close any windows that may be open
        tapRandomPoint(new DTOPoint(529, 27), new DTOPoint(635, 63), 5, 300);

        // Search for the mercenary within events
        DTOImageSearchResult result = searchTemplateWithRetries(EnumTemplates.MERCENARY_EVENT_TAB, 90, 3);

        if (result.isFound()) {
            tapPoint(result.getPoint());
            sleepTask(1000);
            logInfo("Successfully navigated to the Mercenary event.");
            return true;
        }

        // Swipe completely to the left
        logInfo("Mercenary event not immediately visible. Swiping left to locate it.");
        for (int i = 0; i < 3; i++) {
            swipe(new DTOPoint(80, 120), new DTOPoint(578, 130));
            sleepTask(200);
        }

        int attempts = 0;
        while (attempts < 5) {
            result = searchTemplateWithRetries(EnumTemplates.MERCENARY_EVENT_TAB, 90, 1);

            if (result.isFound()) {
                tapPoint(result.getPoint());
                sleepTask(1000);
                logInfo("Successfully navigated to the Mercenary event.");
                return true;
            }

            logInfo("Mercenary event not found. Swiping right and retrying...");
            swipe(new DTOPoint(630, 143), new DTOPoint(500, 128));
            sleepTask(200);
            attempts++;
        }

        logWarning("Mercenary event not found after multiple attempts. Resheduling task to next reset.");
        reschedule(UtilTime.getGameReset());
        return false;
    }

    /**
     * Finds either the scout button or challenge button for the mercenary event.
     * 
     * @return The search result of the found button, or null if neither button is
     *         found
     */
    private DTOImageSearchResult findMercenaryEventButton() {
        logInfo("Checking for mercenary event buttons.");

        // First check for scout button
        DTOImageSearchResult scoutButton = searchTemplateWithRetries(EnumTemplates.MERCENARY_SCOUT_BUTTON, 90, 3);
        if (scoutButton.isFound()) {
            scout = true;
            logInfo("Found scout button for mercenary event.");
            return scoutButton;
        }

        // If scout button not found, check for challenge button
        DTOImageSearchResult challengeButton = searchTemplateWithRetries(EnumTemplates.MERCENARY_CHALLENGE_BUTTON, 90,
                3);
        if (challengeButton.isFound()) {
            scout = false;
            logInfo("Found challenge button for mercenary event.");
            return challengeButton;
        }

        logInfo("Neither scout nor challenge button found for mercenary event.");
        return null;
    }

    private void scoutAndAttack(DTOImageSearchResult eventButton, boolean sameLevelAsLastTime) {
        logInfo("Starting scout/attack process for mercenary event.");

        if (eventButton == null) {
            logInfo("No scout or challenge button found, assuming event is completed. Rescheduling to reset.");
            reschedule(UtilTime.getGameReset());
            return;
        }

        if (scout) {
            logInfo("Scouting mercenary. Decreasing stamina by 15.");
            StaminaService.getServices().subtractStamina(profile.getId(), 15);
        }

        // Click on the button (whether it's scout or challenge)
        tapPoint(eventButton.getPoint());
        sleepTask(4000); // Wait to travel to mercenary location on map

        // Determine whether to rally or attack
        boolean rally = false;
        DTOImageSearchResult attackOrRallyButton = null;

        if (attackAttempts > 3) {
            logWarning(
                    "Multiple consecutive attack attempts detected without level change. Rallying the mercenary instead of normal attack.");
            attackOrRallyButton = searchTemplateWithRetries(EnumTemplates.RALLY_BUTTON, 90, 3);
            rally = true;
        } else {
            attackOrRallyButton = searchTemplateWithRetries(EnumTemplates.MERCENARY_ATTACK_BUTTON, 90, 3);
        }

        if (attackOrRallyButton == null || !attackOrRallyButton.isFound()) {
            logWarning("Attack/Rally button not found after scouting/challenging. Retrying in 5 minutes.");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }

        logInfo(rally ? "Rallying mercenary." : "Attacking mercenary.");
        tapPoint(attackOrRallyButton.getPoint());
        sleepTask(1000);

        if (rally) {
            tapRandomPoint(new DTOPoint(275, 821), new DTOPoint(444, 856));
            sleepTask(500);
        }

        // Check if the march screen is open before proceeding
        DTOImageSearchResult deployButton = searchTemplateWithRetries(EnumTemplates.DEPLOY_BUTTON, 90, 3);

        if (!deployButton.isFound()) {
            logError(
                    "March queue is full or another issue occurred. Cannot start a new march. Retrying in 10 minutes.");
            reschedule(LocalDateTime.now().plusMinutes(10));
            return;
        }

        // Check if we should use a specific flag
        if (useFlag && !sameLevelAsLastTime) {
            tapPoint(UtilRally.getMarchFlagPoint(flagNumber));
            sleepTask(300);
        }

        // Parse travel time
        long travelTimeSeconds = parseTravelTime();

        // Parse stamina cost
        Integer spentStamina = getSpentStamina();

        // Validate travel time before deploying
        if (travelTimeSeconds <= 0) {
            logError("Failed to parse valid march time via OCR. Using conservative 10 minute fallback reschedule.");
            tapPoint(deployButton.getPoint()); // Deploy anyway since we're already in the march screen
            sleepTask(2000);

            // Update stamina with fallback
            subtractStamina(spentStamina, rally);

            // Reschedule with conservative estimate
            LocalDateTime fallbackTime = LocalDateTime.now().plusMinutes(10);
            reschedule(fallbackTime);
            logInfo("Mercenary march deployed with unknown return time. Task will retry at " +
                    fallbackTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            return;
        }

        // Deploy march with known travel time
        tapPoint(deployButton.getPoint());
        sleepTask(2000);

        // Verify deployment succeeded
        DTOImageSearchResult deployStillPresent = searchTemplateWithRetries(EnumTemplates.DEPLOY_BUTTON, 90, 2);
        if (deployStillPresent.isFound()) {
            logWarning(
                    "Deploy button still present after attempting to deploy. March may have failed. Retrying in 5 minutes.");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }

        logInfo("March deployed successfully.");

        // Calculate return time
        long returnTimeSeconds = (travelTimeSeconds * 2) + 2;
        LocalDateTime rescheduleTime = rally
                ? LocalDateTime.now().plusSeconds(returnTimeSeconds).plusMinutes(5)
                : LocalDateTime.now().plusSeconds(returnTimeSeconds);

        reschedule(rescheduleTime);

        // Update stamina
        subtractStamina(spentStamina, rally);

        logInfo("Mercenary march sent. Task will run again at " +
                rescheduleTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")) +
                " (in " + (returnTimeSeconds / 60) + " minutes).");
    }

    @Override
    public EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.WORLD;
    }

    @Override
    protected boolean consumesStamina() {
        return true;
    }

}
