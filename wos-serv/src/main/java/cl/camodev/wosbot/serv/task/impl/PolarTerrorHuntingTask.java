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
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class PolarTerrorHuntingTask extends DelayedTask {
    private final int refreshStaminaLevel = 180;
    private final int minStaminaLevel = 100;
    private final IDailyTaskRepository iDailyTaskRepository = DailyTaskRepository.getRepository();
    private final ServTaskManager servTaskManager = ServTaskManager.getInstance();
    private Integer currentStamina = null;

    public PolarTerrorHuntingTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("=== Starting Polar Terror Hunting Task ===");

        if (isBearRunning()) {
            LocalDateTime rescheduleTo = LocalDateTime.now().plusMinutes(30);
            logInfo("Bear Hunt is running, rescheduling for " + rescheduleTo);
            reschedule(rescheduleTo);
            return;
        }
        logDebug("Bear Hunt is not running, continuing with Polar Terror Hunting Task");

        String flagString = profile.getConfig(EnumConfigurationKey.POLAR_TERROR_FLAG_STRING, String.class);
        int flagNumber = 0;
        int polarTerrorLevel = profile.getConfig(EnumConfigurationKey.POLAR_TERROR_LEVEL_INT, Integer.class);
        boolean limitedHunting = profile.getConfig(EnumConfigurationKey.POLAR_TERROR_MODE_STRING, String.class)
                .equals("Limited (10)");
        boolean useFlag = false;

        if (flagString != null) {
            try {
                flagNumber = Integer.parseInt(flagString);
                useFlag = true;
            } catch (NumberFormatException e) {
                useFlag = false;
            }
        }

        if (profile.getConfig(EnumConfigurationKey.INTEL_BOOL, Boolean.class)
                && useFlag
                && servTaskManager.getTaskState(profile.getId(), TpDailyTaskEnum.INTEL.getId()).isScheduled()) {
            // Make sure intel isn't about to run
            DailyTask intel = iDailyTaskRepository.findByProfileIdAndTaskName(profile.getId(), TpDailyTaskEnum.INTEL);
            if (ChronoUnit.MINUTES.between(LocalDateTime.now(), intel.getNextSchedule()) < 5) {
                reschedule(LocalDateTime.now().plusMinutes(35)); // Reschedule in 35 minutes, after intel has run
                logWarning("Intel task is scheduled to run soon. Rescheduling Polar Hunt to run 30min after intel.");
                return;
            }
        }

        logInfo(String.format("Configuration: Level %d | %s Mode | Flag: %s",
                polarTerrorLevel,
                limitedHunting ? "Limited (10 hunts)" : "Unlimited",
                useFlag ? "#" + flagString : "None"));

        // Verify if there's enough stamina to hunt, if not, reschedule the task
        currentStamina = getStaminaValueFromIntelScreen();
        if (currentStamina == null) {
            logWarning("No stamina value found after OCR attempts. Rescheduling task in 5 minutes to try again.");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }

        if (currentStamina < minStaminaLevel) {
            LocalDateTime rescheduleTime = LocalDateTime.now()
                    .plusMinutes(staminaRegenerationTime(currentStamina, refreshStaminaLevel));
            reschedule(rescheduleTime);
            logWarning("Not enough stamina to do polar (Current: " + currentStamina + "/" + minStaminaLevel
                    + "). Rescheduling task to run in "
                    + UtilTime.localDateTimeToDDHHMMSS(rescheduleTime));
            return;
        }

        if (!checkMarchesAvailable()) {
            logWarning("No marches available, rescheduling for in 5 minutes.");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }

        // Flag mode: Send single rally
        if (useFlag) {
            logInfo("Launching rally with flag #" + flagNumber);
            int result = launchSingleRally(polarTerrorLevel, useFlag, flagNumber);
            handleRallyResult(result, useFlag, limitedHunting, polarTerrorLevel);
            return;
        }

        // No-flag mode: Send multiple rallies
        logInfo("Starting rally loop (no-flag mode)");
        int ralliesDeployed = 0;
        while (true) {
            // Check marches before each rally
            if (!checkMarchesAvailable()) {
                logInfo("No marches available after " + ralliesDeployed + " rallies. Waiting for marches to return.");
                reschedule(LocalDateTime.now().plusMinutes(10));
                return;
            }

            // Check limited hunting limit
            if (limitedHunting && !polarsRemaining(polarTerrorLevel)) {
                return; // Already rescheduled in polarsRemaining
            }

            int result = launchSingleRally(polarTerrorLevel, false, 0);

            if (result == -1) {
                // OCR error - can't continue reliably
                logError("OCR error occurred. Rescheduling in 5 minutes.");
                reschedule(LocalDateTime.now().plusMinutes(5));
                return;
            }

            if (result == 0) {
                // Deployment failed - probably out of marches
                logInfo("Deployment failed after " + ralliesDeployed + " rallies. Rescheduling in 5 minutes.");
                reschedule(LocalDateTime.now().plusMinutes(5));
                return;
            }

            if (result == 3) {
                // Low stamina - already rescheduled in launchSingleRally
                logInfo("Stamina too low after " + ralliesDeployed + " rallies. Task rescheduled.");
                return;
            }

            // result == 1: success
            ralliesDeployed++;
            logInfo("Rally #" + ralliesDeployed + " deployed successfully. Current stamina: " + currentStamina);
            sleepTask(1500);
        }

    }

    /**
     * Launches a single polar rally without recursion
     * 
     * @return -1 if OCR error occurred
     * @return 0 if deployment failed
     * @return 1 if deployment successful (no-flag mode)
     * @return 2 if deployment successful (flag mode)
     * @return 3 if deployment successful but stamina too low to continue
     */
    private int launchSingleRally(int polarLevel, boolean useFlag, int flagNumber) {
        int maxRetries = 5;
        ensureCorrectScreenLocation(getRequiredStartLocation());

        // Open polars menu
        logInfo("Navigating to polars menu");
        boolean menuOpened = false;
        for (int i = 0; i < maxRetries; i++) {
            if (openPolarsMenu(polarLevel)) {
                menuOpened = true;
                break;
            }
        }
        if (!menuOpened) {
            logError("Failed to open polars menu after " + maxRetries + " attempts.");
            return 0;
        }

        // Open rally menu
        logInfo("Navigating to rally menu");
        boolean rallyOpened = false;
        for (int i = 0; i < maxRetries; i++) {
            if (openRallyMenu()) {
                rallyOpened = true;
                break;
            }
        }
        if (!rallyOpened) {
            logError("Failed to open rally menu after " + maxRetries + " attempts.");
            return 0;
        }

        // Tap "Hold a Rally" button
        tapRandomPoint(new DTOPoint(275, 821), new DTOPoint(444, 856), 1, 400);
        sleepTask(500);

        // Select flag if needed
        if (useFlag) {
            tapPoint(UtilRally.getMarchFlagPoint(flagNumber));
            sleepTask(300);
        }

        // Parse travel time
        long travelTimeSeconds = 0;
        try {
            String timeStr = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(521, 1141),
                    new DTOPoint(608, 1162));
            travelTimeSeconds = UtilTime.parseTimeToSeconds(timeStr) * 2 + 2;
        } catch (Exception e) {
            logError("Error parsing travel time: " + e.getMessage());
        }

        // Deploy march
        boolean deployed = clickDeployButton(maxRetries);
        if (!deployed) {
            return 0;
        }

        logInfo("March deployed successfully.");

        // Update stamina
        Integer previousStamina = currentStamina;
        currentStamina = getStaminaValueFromIntelScreen();
        if (currentStamina == null) {
            logWarning("No stamina value found after deployment.");
            return -1;
        }
        logInfo("Stamina decreased by " + (previousStamina - currentStamina) + ". Current stamina: " + currentStamina);

        // Flag mode: reschedule for march return
        if (useFlag) {
            if (travelTimeSeconds <= 0) {
                logError("Failed to parse travel time via OCR. Cannot accurately reschedule for march return.");
                return -1;
            }
            LocalDateTime rescheduleTime = LocalDateTime.now().plusSeconds(travelTimeSeconds).plusMinutes(5);
            reschedule(rescheduleTime);
            logInfo("Rally with flag scheduled to return in " + UtilTime.localDateTimeToDDHHMMSS(rescheduleTime));
            return 2;
        }

        // No-flag mode: check stamina for next rally
        if (currentStamina <= minStaminaLevel) {
            logInfo("Stamina is at or below minimum. Stopping deployment and rescheduling.");
            reschedule(LocalDateTime.now().plusMinutes(staminaRegenerationTime(currentStamina, refreshStaminaLevel)));
            return 3;
        }

        return 1;
    }

    private void handleRallyResult(int result, boolean useFlag, boolean limitedHunting, int polarLevel) {
        if (result == -1) {
            if (useFlag) {
                logWarning("March deployed with flag but travel time unknown. Using fallback reschedule.");
                reschedule(LocalDateTime.now().plusMinutes(10));
            } else {
                reschedule(LocalDateTime.now().plusMinutes(5));
            }
            return;
        }

        if (result == 0) {
            if (useFlag) {
                logError("Failed to deploy march. Trying again in 5 minutes.");
                reschedule(LocalDateTime.now().plusMinutes(5));
            }
            return;
        }

        // Results 2 and 3 already handle their own rescheduling
    }

    private boolean openRallyMenu() {
        // Search for rally button
        DTOImageSearchResult rallyButton = null;
        int rallyAttempts = 0;
        while (rallyAttempts < 4) {
            rallyButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.RALLY_BUTTON, 90);
            if (rallyButton.isFound()) {
                break;
            }
            rallyAttempts++;
            sleepTask(500);
        }

        if (!rallyButton.isFound()) {
            logDebug("Rally button not found.");
            sleepTask(500);
            return false;
        }

        tapPoint(rallyButton.getPoint());
        sleepTask(1000);
        return true;
    }

    private boolean clickDeployButton(int maxRetries) {
        // Search for deploy button and click it, retrying if needed
        for (int i = 0; i <= maxRetries; i++) {
            DTOImageSearchResult deploy = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.DEPLOY_BUTTON, 90);

            if (!deploy.isFound()) {
                continue;
            }

            tapPoint(deploy.getPoint());
            sleepTask(2000);
            deploy = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TROOPS_ALREADY_MARCHING, 90);

            if (!deploy.isFound()) {
                return true;
            }

            // Troops already marching pop-up detected, close it and retry (TODO: Should
            // find another polar terror to rally)
            sleepTask(500);
            tapBackButton();
            logDebug("Deploy still present, retry " + (i + 1) + " of " + maxRetries);
            sleepTask(1000);
        }
        logError("March deployment may have failed, deploy button still present.");
        return false;
    }

    private boolean isBearRunning() {
        DTOImageSearchResult result = searchTemplateWithRetries(EnumTemplates.BEAR_HUNT_IS_RUNNING);
        return result.isFound();
    }

    private boolean openPolarsMenu(int polarLevel) {
        // Navigate to the specified polar terror level
        // Open search (magnifying glass)
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(25, 850), new DTOPoint(67, 898));
        sleepTask(2000);

        // Swipe left to find polar terror icon
        emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(40, 913), new DTOPoint(678, 913));
        sleepTask(500);

        // Search the polar terror search icon
        DTOImageSearchResult polarTerror = null;
        int attempts = 0;
        while (attempts < 4) {
            polarTerror = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.POLAR_TERROR_SEARCH_ICON, 90);
            if (polarTerror.isFound()) {
                break;
            }
            attempts++;
            logDebug(String.format("Searching for Polar Terror icon (attempt %d/4)", attempts));
            emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(40, 913), new DTOPoint(678, 913));
            sleepTask(500);
        }

        if (!polarTerror.isFound()) {
            logWarning("Failed to find the polar terrors.");
            return false;
        }
        // Need to tap on the polar terror icon and check the current level selected
        tapPoint(polarTerror.getPoint());
        if (polarLevel != -1) {
            logInfo(String.format("Adjusting Polar Terror level to %d", polarLevel));
            emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(435, 1052), new DTOPoint(40, 1052)); // Swipe to level
                                                                                                       // 1
            sleepTask(300);
            if (polarLevel > 1) {
                emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(487, 1055), new DTOPoint(487, 1055),
                        (polarLevel - 1), 200);
            }

        }
        // tap on search button
        logDebug("Tapping on search button...");
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(301, 1200), new DTOPoint(412, 1229));
        sleepTask(4000);
        return true;
    }

    private boolean polarsRemaining(int polarLevel) {
        if (!openPolarsMenu(polarLevel)) {
            return false;
        }

        // Need to search for the magnifying glass icon to be sure we're on the search
        // screen
        DTOImageSearchResult magnifyingGlass = null;
        int attempts = 0;
        while (attempts < 4) {
            magnifyingGlass = emuManager.searchTemplate(EMULATOR_NUMBER,
                    EnumTemplates.POLAR_TERROR_TAB_MAGNIFYING_GLASS_ICON, 90);
            if (magnifyingGlass.isFound()) {
                break;
            }
            attempts++;
            logDebug(String.format("Searching for magnifying glass icon (attempt %d/4)", attempts));
            sleepTask(500);
        }
        if (!magnifyingGlass.isFound()) {
            return false;
        }
        // Need to scroll down a little bit and search for the remaining hunts "Special
        // Rewards (n left)"
        tapPoint(magnifyingGlass.getPoint());
        sleepTask(2000);
        DTOImageSearchResult specialRewards = null;
        attempts = 0;
        while (attempts < 5) {
            specialRewards = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.POLAR_TERROR_TAB_SPECIAL_REWARDS,
                    90);
            if (specialRewards.isFound()) {
                // Due to limited mode being enabled, and there's no special rewards found,
                // means there's no hunts left
                logWarning(
                        "No special rewards found, meaning there's no hunts left for today. Rescheduling task for reset");
                // Add 30 minutes to let intel and other tasks be processed
                reschedule(UtilTime.getGameReset().plusMinutes(30));
                return false;
            }
            attempts++;
            swipe(new DTOPoint(363, 1088), new DTOPoint(363, 1030));
            sleepTask(200);
        }
        // There are still rallies available, continue navigating to world screen
        tapBackButton();
        sleepTask(500);
        return true;
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.WORLD;
    }

}
