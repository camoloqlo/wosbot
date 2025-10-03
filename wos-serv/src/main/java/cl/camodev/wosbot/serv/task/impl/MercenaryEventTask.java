package cl.camodev.wosbot.serv.task.impl;

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
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import net.sourceforge.tess4j.TesseractException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MercenaryEventTask extends DelayedTask {
    private final IDailyTaskRepository iDailyTaskRepository = DailyTaskRepository.getRepository();
    private final ServTaskManager servTaskManager = ServTaskManager.getInstance();
    private Integer lastMercenaryLevel = null;
    private int attackAttempts = 0;

    public MercenaryEventTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    public EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.WORLD;
    }

    @Override
    protected void execute() {
        if (profile.getConfig(EnumConfigurationKey.INTEL_BOOL, Boolean.class)
                && profile.getConfig(EnumConfigurationKey.MERCENARY_USE_FLAG_BOOL, Boolean.class)
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

        if (!checkStamina()) {
            logInfo("Stamina check failed or insufficient. Task has been rescheduled.");
            tapBackButton();
            return;
        }

        // After checking stamina, return to world screen
        tapBackButton();

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

    private boolean checkStamina() {
        logInfo("Navigating to Intel screen to check stamina.");
        ensureOnIntelScreen();
        sleepTask(2000);

        Integer staminaValue = readNumberValue(new DTOPoint(582, 23), new DTOPoint(672, 55));
        if (staminaValue == null) {
            logWarning("No stamina value found after OCR attempts.");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return false;
        }

        int minStaminaRequired = 30;
        if (staminaValue < minStaminaRequired) {
            logWarning("Not enough stamina to attack mercenary. Current: " + staminaValue + ", Required: "
                    + minStaminaRequired);
            long minutesToRegen = (minStaminaRequired - staminaValue) * 5L;
            LocalDateTime rescheduleTime = LocalDateTime.now().plusMinutes(minutesToRegen);
            reschedule(rescheduleTime);
            logInfo("Rescheduling for " + DateTimeFormatter.ofPattern("HH:mm:ss").format(rescheduleTime)
                    + " to regenerate stamina.");
            return false;
        }

        logInfo("Stamina is sufficient (" + staminaValue + ").");
        return true;

    }

    private void handleMercenaryEvent() {
        try {
            // Select mercenary event level if needed
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
        Integer level = readNumberValue(new DTOPoint(322, 867), new DTOPoint(454, 918));
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
            String textEasy = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(112, 919), new DTOPoint(179, 953));
            String textNormal = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(310, 919),
                    new DTOPoint(410, 953));
            String textHard = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(540, 919), new DTOPoint(609, 953));
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
            DTOImageSearchResult challengeCheck = emuManager.searchTemplate(EMULATOR_NUMBER,
                    EnumTemplates.MERCENARY_DIFFICULTY_CHALLENGE, 90);
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
            DTOImageSearchResult challengeCheck = emuManager.searchTemplate(EMULATOR_NUMBER,
                    EnumTemplates.MERCENARY_DIFFICULTY_CHALLENGE, 90);
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

        logInfo("Starting the Mercenary Event task.");

        // Search for the events button
        DTOImageSearchResult eventsResult = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.HOME_EVENTS_BUTTON, 90);
        if (!eventsResult.isFound()) {
            logWarning("The 'Events' button was not found.");
            return false;
        }

        tapPoint(eventsResult.getPoint());
        sleepTask(2000);

        // Close any windows that may be open
        tapRandomPoint(new DTOPoint(529, 27), new DTOPoint(635, 63), 5, 300);

        // Search for the mercenary within events
        DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.MERCENARY_EVENT_TAB, 90);

        if (result.isFound()) {
            tapPoint(result.getPoint());
            sleepTask(1000);
            logInfo("Successfully navigated to the Mercenary event.");
            return true;
        }

        // Swipe completely to the left
        logInfo("Mercenary event not immediately visible. Swiping left to locate it.");
        for (int i = 0; i < 3; i++) {
            emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(80, 120), new DTOPoint(578, 130));
            sleepTask(200);
        }

        int attempts = 0;
        while (attempts < 5) {
            result = emuManager.searchTemplate(EMULATOR_NUMBER,
                    EnumTemplates.MERCENARY_EVENT_TAB, 90);

            if (result.isFound()) {
                tapPoint(result.getPoint());
                sleepTask(1000);
                logInfo("Successfully navigated to the Mercenary event.");
                return true;
            }

            logInfo("Mercenary event not found. Swiping right and retrying...");
            emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(630, 143), new DTOPoint(500, 128));
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
        DTOImageSearchResult scoutButton = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.MERCENARY_SCOUT_BUTTON, 90);
        if (scoutButton.isFound()) {
            logInfo("Found scout button for mercenary event.");
            return scoutButton;
        }

        // If scout button not found, check for challenge button
        DTOImageSearchResult challengeButton = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.MERCENARY_CHALLENGE_BUTTON, 90);
        if (challengeButton.isFound()) {
            logInfo("Found challenge button for mercenary event.");
            return challengeButton;
        }

        logInfo("Neither scout nor challenge button found for mercenary event.");
        return null;
    }

    private void scoutAndAttack(DTOImageSearchResult eventButton, boolean sameLevelAsLastTime)
            throws IOException, TesseractException {
        logInfo("Starting scout/attack process for mercenary event.");

        if (eventButton != null) {
            // Click on the button (whether it's scout or challenge)
            tapPoint(eventButton.getPoint());
            sleepTask(4000); // Wait to travel to mercenary location on map

            DTOImageSearchResult attackOrRallyButton = null;
            boolean rally = false;
            if (attackAttempts > 3) {
                logWarning(
                        "Multiple consecutive attack attempts detected without level change. Rallying the mercenary instead of normal attack.");
                attackOrRallyButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.RALLY_BUTTON, 90);
                rally = true;
            } else {
                attackOrRallyButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.MERCENARY_ATTACK_BUTTON,
                        90);
            }

            if (attackOrRallyButton != null && attackOrRallyButton.isFound()) {
                logInfo("Attacking mercenary.");
                tapPoint(attackOrRallyButton.getPoint());
                sleepTask(1000);

                if (rally)
                    tapRandomPoint(new DTOPoint(275, 821), new DTOPoint(444, 856));
                sleepTask(500);

                // Check if the march screen is open before proceeding
                DTOImageSearchResult deployButton = emuManager.searchTemplate(EMULATOR_NUMBER,
                        EnumTemplates.DEPLOY_BUTTON, 90);
                if (!deployButton.isFound()) {
                    logError("March queue is full or another issue occurred. Cannot start a new march.");
                    reschedule(LocalDateTime.now().plusMinutes(10));
                    return;
                }

                // Check if we should use a specific flag
                boolean useFlag = profile.getConfig(EnumConfigurationKey.MERCENARY_USE_FLAG_BOOL, Boolean.class);
                if (useFlag && !sameLevelAsLastTime) {
                    // Select the specified flag
                    int flagToSelect = profile.getConfig(EnumConfigurationKey.MERCENARY_FLAG_INT, Integer.class);
                    selectMarchFlag(flagToSelect);
                    sleepTask(500); // Wait for flag selection
                }

                try {
                    String timeStr = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(521, 1141),
                            new DTOPoint(608, 1162));
                    long travelTimeSeconds = parseTimeToSeconds(timeStr);

                    if (travelTimeSeconds > 0) {
                        // Proceed to deploy troops
                        tapPoint(deployButton.getPoint());
                        sleepTask(1000); // Wait for march to start
                        long returnTimeSeconds = (travelTimeSeconds * 2) + 2;

                        LocalDateTime rescheduleTime = rally
                                ? LocalDateTime.now().plusSeconds(returnTimeSeconds).plusMinutes(5)
                                : LocalDateTime.now().plusSeconds(returnTimeSeconds);

                        reschedule(rescheduleTime);
                        logInfo("Mercenary march sent. Task will run again in "
                                + rescheduleTime.format(DateTimeFormatter.ofPattern("mm:ss")) + ".");
                    } else {
                        logError("Failed to parse march time. Aborting attack.");
                        tapBackButton(); // Go back from march screen
                    }
                } catch (IOException | TesseractException e) {
                    logError("Failed to read march time using OCR. Aborting attack. Error: " + e.getMessage(), e);
                    tapBackButton(); // Go back from march screen
                }
            } else {
                logWarning("Attack button not found after scouting/challenging.");
            }
        } else {
            logInfo("No scout or challenge button found, assuming event is completed. Rescheduling to reset.");
            reschedule(UtilTime.getGameReset());
        }
    }

    private long parseTimeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return 0;
        }
        Pattern pattern = Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})");
        Matcher matcher = pattern.matcher(timeStr.trim());
        if (matcher.find()) {
            try {
                int hours = Integer.parseInt(matcher.group(1));
                int minutes = Integer.parseInt(matcher.group(2));
                int seconds = Integer.parseInt(matcher.group(3));
                return (long) hours * 3600 + (long) minutes * 60 + seconds;
            } catch (NumberFormatException e) {
                logError("Failed to parse march time from OCR string: '" + timeStr + "'", e);
            }
        }
        logWarning("Could not parse march time from OCR string: '" + timeStr + "'.");
        return 0;
    }

    /**
     * Selects a flag for the march.
     * 
     * @param flagNumber The flag number to select (1-8)
     */
    private void selectMarchFlag(int flagNumber) {
        logInfo("Selecting march flag " + flagNumber + ".");
        DTOPoint flagPoint = null;
        switch (flagNumber) {
            case 1:
                flagPoint = new DTOPoint(70, 120);
                break;
            case 2:
                flagPoint = new DTOPoint(140, 120);
                break;
            case 3:
                flagPoint = new DTOPoint(210, 120);
                break;
            case 4:
                flagPoint = new DTOPoint(280, 120);
                break;
            case 5:
                flagPoint = new DTOPoint(350, 120);
                break;
            case 6:
                flagPoint = new DTOPoint(420, 120);
                break;
            case 7:
                flagPoint = new DTOPoint(490, 120);
                break;
            case 8:
                flagPoint = new DTOPoint(560, 120);
                break;
            default:
                logError("Invalid flag number: " + flagNumber + ". Defaulting to flag 1.");
                flagPoint = new DTOPoint(70, 120);
                break;
        }
        tapPoint(flagPoint);
    }

}
