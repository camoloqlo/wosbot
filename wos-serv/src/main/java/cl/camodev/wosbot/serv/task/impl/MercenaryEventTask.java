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
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import cl.camodev.wosbot.serv.task.helper.NavigationHelper.EventMenu;
import java.awt.Color;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class MercenaryEventTask extends DelayedTask {
    private final IDailyTaskRepository iDailyTaskRepository = DailyTaskRepository.getRepository();
    private final ServTaskManager servTaskManager = ServTaskManager.getInstance();
    private Integer lastMercenaryLevel = null;
    private Integer lastStaminaSpent = null;
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
        if (!staminaHelper.checkStaminaAndMarchesOrReschedule(minStaminaLevel, refreshStaminaLevel, this::reschedule))
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
                staminaHelper.addStamina(lastStaminaSpent);
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
        // Try each initiation type in order: Legends -> Epic -> Champions
        String[] initiationTypes = { "Legends", "Epic", "Champions" };
        EnumTemplates[] unselectedTemplates = {
                EnumTemplates.MERCENARY_LEGENDS_INITIATION_UNSELECTED,
                EnumTemplates.MERCENARY_EPIC_INITIATION_UNSELECTED,
                EnumTemplates.MERCENARY_CHAMPIONS_INITIATION_UNSELECTED
        };
        EnumTemplates[] selectedTemplates = {
                EnumTemplates.MERCENARY_LEGENDS_INITIATION_SELECTED,
                EnumTemplates.MERCENARY_EPIC_INITIATION_SELECTED,
                EnumTemplates.MERCENARY_CHAMPIONS_INITIATION_SELECTED
        };

        for (int i = 0; i < initiationTypes.length; i++) {
            boolean tabIsSelected = false;

            // First check if this initiation type is already selected
            DTOImageSearchResult alreadySelectedTab = templateSearchHelper.searchTemplate(
                    selectedTemplates[i],
                    SearchConfigConstants.SINGLE_WITH_RETRIES);

            if (alreadySelectedTab.isFound()) {
                logInfo(initiationTypes[i]
                        + " Initiation tab is already selected. Proceeding with difficulty selection.");
                tabIsSelected = true;
            } else {
                // Tab is not selected, check if it's unselected (available to select)
                DTOImageSearchResult unselectedTab = templateSearchHelper.searchTemplate(
                        unselectedTemplates[i],
                        SearchConfigConstants.SINGLE_WITH_RETRIES);

                if (unselectedTab.isFound()) {
                    logInfo("Found unselected " + initiationTypes[i] + " Initiation tab. Tapping to open.");

                    // Tap the unselected tab to open it
                    tapPoint(unselectedTab.getPoint());
                    sleepTask(1500);

                    // Verify that the tab changed to selected (not locked)
                    DTOImageSearchResult selectedTab = templateSearchHelper.searchTemplate(
                            selectedTemplates[i],
                            SearchConfigConstants.SINGLE_WITH_RETRIES);

                    if (selectedTab.isFound()) {
                        tabIsSelected = true;
                    } else {
                        logDebug(initiationTypes[i] + " Initiation tab is locked. Skipping to next type.");
                    }
                }
            }

            if (tabIsSelected) {
                logInfo(initiationTypes[i] + " Initiation tab is open. Attempting to select difficulty.");

                // Now select a difficulty within this initiation type
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
                    logDebug("Attempting to select difficulty: " + level.name() + " in " + initiationTypes[i]
                            + " Initiation.");
                    tapPoint(level.point());
                    sleepTask(2000);
                    DTOImageSearchResult challengeCheck = templateSearchHelper.searchTemplate(
                            EnumTemplates.MERCENARY_DIFFICULTY_CHALLENGE,
                            SearchConfigConstants.SINGLE_WITH_RETRIES);
                    if (challengeCheck.isFound()) {
                        sleepTask(1000);
                        tapPoint(challengeCheck.getPoint());
                        sleepTask(1000);
                        tapPoint(new DTOPoint(504, 788)); // Tap the confirm button
                        logInfo("Selected mercenary event difficulty: " + level.name() + " in " + initiationTypes[i]
                                + " Initiation.");
                        sleepTask(2000);
                        return true;
                    }
                    sleepTask(1000);
                    tapBackButton();
                }
            }
        }

        // If no tab was found at all, a level was already selected beforehand
        logInfo("No initiation type found, assuming one was already selected beforehand. Proceeding.");
        return true;
    }

    private boolean navigateToEventScreen() {
        logInfo("Navigating to Mercenary event...");

        boolean success = navigationHelper.navigateToEventMenu(EventMenu.MERCENARY);

        if (!success) {
            logWarning("Failed to navigate to Mercenary event");
            return false;
        }

        sleepTask(2000);
        return true;
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
        DTOImageSearchResult scoutButton = templateSearchHelper.searchTemplate(
                EnumTemplates.MERCENARY_SCOUT_BUTTON,
                SearchConfigConstants.SINGLE_WITH_RETRIES);

        if (scoutButton.isFound()) {
            scout = true;
            logInfo("Found scout button for mercenary event.");
            return scoutButton;
        }

        // If scout button not found, check for challenge button
        DTOImageSearchResult challengeButton = templateSearchHelper.searchTemplate(
                EnumTemplates.MERCENARY_CHALLENGE_BUTTON,
                SearchConfigConstants.SINGLE_WITH_RETRIES);

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
            attackOrRallyButton = templateSearchHelper.searchTemplate(
                    EnumTemplates.RALLY_BUTTON,
                    SearchConfigConstants.SINGLE_WITH_RETRIES);
            rally = true;
        } else {
            attackOrRallyButton = templateSearchHelper.searchTemplate(
                    EnumTemplates.MERCENARY_ATTACK_BUTTON,
                    SearchConfigConstants.SINGLE_WITH_RETRIES);
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
        DTOImageSearchResult deployButton = templateSearchHelper.searchTemplate(
                EnumTemplates.DEPLOY_BUTTON,
                SearchConfigConstants.SINGLE_WITH_RETRIES);

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
        long travelTimeSeconds = staminaHelper.parseTravelTime();

        // Parse stamina cost
        Integer spentStamina = staminaHelper.getSpentStamina();
        lastStaminaSpent = spentStamina;

        // Validate travel time before deploying
        if (travelTimeSeconds <= 0) {
            logError("Failed to parse valid march time via OCR. Using conservative 10 minute fallback reschedule.");
            tapPoint(deployButton.getPoint()); // Deploy anyway since we're already in the march screen
            sleepTask(2000);

            // Update stamina with fallback
            staminaHelper.subtractStamina(spentStamina, rally);

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
        DTOImageSearchResult deployStillPresent = templateSearchHelper.searchTemplate(
                EnumTemplates.DEPLOY_BUTTON,
                SearchConfigConstants.SINGLE_WITH_2_RETRIES);
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
        staminaHelper.subtractStamina(spentStamina, rally);

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
