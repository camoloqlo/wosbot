package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class StorehouseChest extends DelayedTask {

    private LocalDateTime nextStaminaClaim = LocalDateTime.now();

    public StorehouseChest(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    public EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    @Override
    protected void execute() {
        logInfo("Navigating to the Storehouse.");

        // Track both times during execution
        LocalDateTime nextChestTime = null;
        LocalDateTime nextStaminaTime = nextStaminaClaim;

        tapRandomPoint(new DTOPoint(3, 513), new DTOPoint(26, 588));
        sleepTask(500);

        tapPoint(new DTOPoint(110, 270));
        sleepTask(700);

        tapRandomPoint(new DTOPoint(20, 250), new DTOPoint(200, 280));
        sleepTask(700);

        DTOImageSearchResult researchCenter = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.GAME_HOME_SHORTCUTS_RESEARCH_CENTER, 90);

        if (researchCenter.isFound()) {
            tapPoint(researchCenter.getPoint());
            sleepTask(1000);
            tapRandomPoint(new DTOPoint(30, 430), new DTOPoint(50, 470));
            sleepTask(1000);
            tapRandomPoint(new DTOPoint(1, 636), new DTOPoint(2, 636), 2, 300);

            logInfo("Searching for the storehouse chest.");
            boolean chestFound = false;

            for (int i = 0; i < 5; i++) {
                DTOImageSearchResult chest = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.STOREHOUSE_CHEST,
                        90);
                DTOImageSearchResult chest2 = emuManager.searchTemplate(EMULATOR_NUMBER,
                        EnumTemplates.STOREHOUSE_CHEST_2, 90);

                logDebug("Searching for storehouse chest (Attempt " + (i + 1) + "/5).");
                if (chest.isFound() || chest2.isFound()) {
                    if (!chest.isFound()) {
                        chest = chest2;
                    }

                    chestFound = true;
                    logInfo("Storehouse chest found. Tapping to claim.");
                    tapPoint(chest.getPoint());
                    sleepTask(500);

                    // OCR for next chest time
                    for (int ocrAttempt = 0; ocrAttempt < 5; ocrAttempt++) {
                        try {
                            String chestWaitText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(266, 1100),
                                    new DTOPoint(450, 1145));
                            if (chestWaitText != null && !chestWaitText.trim().isEmpty()) {
                                nextChestTime = UtilTime.parseTime(chestWaitText);
                                logInfo("Next chest available at: " + nextChestTime + " (OCR: " + chestWaitText + ")");
                                break;
                            }
                            logDebug("Chest OCR attempt " + (ocrAttempt + 1) + " failed to read time, retrying...");
                            sleepTask(300);
                        } catch (Exception e) {
                            logDebug("Chest OCR error on attempt " + (ocrAttempt + 1) + ": " + e.getMessage());
                            if (ocrAttempt < 4)
                                sleepTask(300);
                        }
                    }

                    if (nextChestTime == null) {
                        logWarning("Failed to read chest timer after multiple attempts.");
                        nextChestTime = LocalDateTime.now().plusMinutes(5);
                    } else {
                        nextChestTime = nextChestTime.minusSeconds(3);
                    }

                    tapRandomPoint(new DTOPoint(1, 636), new DTOPoint(2, 636), 5, 300);
                    break;
                } else {
                    tapRandomPoint(new DTOPoint(1, 636), new DTOPoint(2, 636), 2, 300);
                }
                sleepTask(300);
            }

            // Search for stamina if it's time
            if (!LocalDateTime.now().isBefore(nextStaminaClaim)) {
                logInfo("Searching for stamina rewards.");
                for (int j = 0; j < 5; j++) {
                    DTOImageSearchResult stamina = emuManager.searchTemplate(EMULATOR_NUMBER,
                            EnumTemplates.STOREHOUSE_STAMINA, 90);

                    logDebug("Searching for storehouse stamina (Attempt " + (j + 1) + "/5).");
                    if (stamina.isFound()) {
                        logInfo("Stamina reward found. Claiming it.");
                        tapPoint(stamina.getPoint());
                        sleepTask(500);
                        tapRandomPoint(new DTOPoint(250, 930), new DTOPoint(450, 950));
                        sleepTask(4000);

                        try {
                            nextStaminaClaim = UtilTime.getNextReset();
                            nextStaminaTime = nextStaminaClaim;
                            logInfo("Next stamina claim scheduled at " + nextStaminaClaim);
                        } catch (Exception e) {
                            logDebug("Error obtaining next reset for stamina claim; keeping previous schedule.");
                        }
                        break;
                    } else {
                        tapRandomPoint(new DTOPoint(1, 636), new DTOPoint(2, 636), 2,
                                300);
                    }
                }
            } else {
                logInfo("Skipping stamina search until " + nextStaminaClaim);
            }

            // If chest wasn't found, try alternative OCR
            if (!chestFound) {
                try {
                    boolean timeFound = false;
                    for (int attempt = 0; attempt < 5 && !timeFound; attempt++) {
                        String nextRewardTime = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(285, 642),
                                new DTOPoint(430, 666));
                        if (nextRewardTime != null && !nextRewardTime.trim().isEmpty()) {
                            if (nextRewardTime.contains("d")) {
                                logWarning(
                                        "OCR time contains days ('" + nextRewardTime + "'), setting timer to 1 hour.");
                                nextChestTime = LocalDateTime.now().plusHours(1);
                                timeFound = true;
                                break;
                            }

                            LocalDateTime parsedTime = UtilTime.parseTime(nextRewardTime);
                            long secondsDiff = java.time.Duration.between(LocalDateTime.now(), parsedTime).getSeconds();

                            if (secondsDiff > 7200) {
                                logWarning("OCR time over 2 hours detected (" + secondsDiff / 60
                                        + " min). Setting 1 hour timer instead.");
                                nextChestTime = LocalDateTime.now().plusHours(1);
                            } else {
                                nextChestTime = parsedTime.minusSeconds(3);
                            }
                            timeFound = true;
                        } else if (attempt < 4) {
                            logDebug("OCR attempt " + (attempt + 1) + " failed to read time, retrying...");
                            sleepTask(300);
                        }
                    }

                    if (!timeFound) {
                        logWarning("Failed to read next reward time after multiple attempts, using 5 minute fallback.");
                        nextChestTime = LocalDateTime.now().plusMinutes(5);
                    }
                } catch (Exception e) {
                    logError("Error during OCR: " + e.getMessage());
                    nextChestTime = LocalDateTime.now().plusMinutes(5);
                }
            }

            // Schedule to the nearest time
            scheduleToNearestTime(nextChestTime, nextStaminaTime);

        } else {
            logWarning("Research Center shortcut not found. Rescheduling for 5 minutes.");
            reschedule(LocalDateTime.now().plusMinutes(5));
        }

        tapBackButton();
    }

    /**
     * Schedules the task to whichever time is nearest (chest or stamina)
     */
    private void scheduleToNearestTime(LocalDateTime nextChestTime, LocalDateTime nextStaminaTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReset = UtilTime.getNextReset();

        // If chest time would exceed next reset, cap it at reset to avoid missing
        // stamina
        if (nextChestTime != null && nextChestTime.isAfter(nextReset)) {
            logInfo("Next chest time (" + nextChestTime + ") exceeds reset, capping at reset time.");
            nextChestTime = nextReset;
        }

        // Validate stamina time - if in the past, it's invalid
        if (nextStaminaTime != null && nextStaminaTime.isBefore(now)) {
            logDebug("Stamina time is in the past, treating as invalid.");
            nextStaminaTime = null;
        }

        // Determine which time is nearest
        LocalDateTime scheduledTime;
        String reason;

        if (nextChestTime == null && nextStaminaTime == null) {
            scheduledTime = LocalDateTime.now().plusMinutes(5);
            reason = "No valid times available, using 5 minute fallback";
        } else if (nextChestTime == null) {
            scheduledTime = nextStaminaTime;
            reason = "stamina claim";
        } else if (nextStaminaTime == null) {
            scheduledTime = nextChestTime;
            reason = "chest claim";
        } else {
            // Both times are valid, pick the nearest one
            if (nextChestTime.isBefore(nextStaminaTime)) {
                scheduledTime = nextChestTime;
                reason = "chest claim (nearest)";
            } else {
                scheduledTime = nextStaminaTime;
                reason = "stamina claim (nearest)";
            }
        }

        logInfo("Scheduling next check at " + scheduledTime + " for " + reason);
        if (!reason.contains("fallback")) {
            logDebug("Next chest time: " + nextChestTime + ", Next stamina time: " + nextStaminaTime);
        }

        reschedule(scheduledTime);
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }
}