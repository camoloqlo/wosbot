package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

import java.awt.Color;

public class HeroMissionEventTask extends DelayedTask {
    private final int refreshStaminaLevel = 180;
    private final int minStaminaLevel = 100;
    private final IDailyTaskRepository iDailyTaskRepository = DailyTaskRepository.getRepository();
    private final ServTaskManager servTaskManager = ServTaskManager.getInstance();
    private int flagNumber = 0;
    private boolean useFlag = false;

    public HeroMissionEventTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("=== Starting Hero's Mission ===");

        flagNumber = profile.getConfig(EnumConfigurationKey.HERO_MISSION_FLAG_INT, Integer.class);
        useFlag = flagNumber > 0;

        if (isBearRunning()) {
            LocalDateTime rescheduleTo = LocalDateTime.now().plusMinutes(30);
            logInfo("Bear Hunt is running, rescheduling for " + rescheduleTo);
            reschedule(rescheduleTo);
            return;
        }
        logDebug("Bear Hunt is not running, continuing with Hero's Mission");

        if (profile.getConfig(EnumConfigurationKey.INTEL_BOOL, Boolean.class)
                && useFlag
                && servTaskManager.getTaskState(profile.getId(), TpDailyTaskEnum.INTEL.getId()).isScheduled()) {
            // Make sure intel isn't about to run
            DailyTask intel = iDailyTaskRepository.findByProfileIdAndTaskName(profile.getId(), TpDailyTaskEnum.INTEL);
            if (ChronoUnit.MINUTES.between(LocalDateTime.now(), intel.getNextSchedule()) < 5) {
                reschedule(LocalDateTime.now().plusMinutes(35)); // Reschedule in 35 minutes, after intel has run
                logWarning(
                        "Intel task is scheduled to run soon. Rescheduling Hero's Mission to run 30min after intel.");
                return;
            }
        }

        // Verify if there's enough stamina to hunt, if not, reschedule the task
        if (!checkStaminaAndMarchesOrReschedule(minStaminaLevel, refreshStaminaLevel))
            return;

        int attempt = 0;
        while (attempt < 2) {
            boolean result = navigateToEventScreen();
            if (result) {
                logInfo("Successfully navigated to Hero's Mission event.");
                sleepTask(500);
                handleHeroMissionEvent();
                return;
            }

            logDebug("Failed to navigate to Hero's Mission event. Attempt " + (attempt + 1) + "/2.");
            sleepTask(300);
            tapBackButton();
            attempt++;
        }

        // If menu is not found after 2 attempts, cancel the task
        if (attempt >= 2) {
            logWarning(
                    "Could not find the Hero's Mission event tab. Assuming event is unavailable. Rescheduling for next reset.");
            reschedule(UtilTime.getGameReset());
        }
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

        // Search for the Hero's Mission event tab
        DTOImageSearchResult result = searchTemplateWithRetries(
                EnumTemplates.HERO_MISSION_EVENT_TAB, 90, 3);

        if (result.isFound()) {
            sleepTask(500);
            tapPoint(result.getPoint());
            return true;
        }

        // Swipe completely to the left
        logInfo("Hero's Mission event not immediately visible. Swiping left to locate it.");
        for (int i = 0; i < 3; i++) {
            swipe(new DTOPoint(80, 120), new DTOPoint(578, 130));
            sleepTask(300);
        }

        for (int i = 0; i < 5; i++) {
            result = searchTemplateWithRetries(
                    EnumTemplates.HERO_MISSION_EVENT_TAB, 90, 1);
            if (result.isFound()) {
                sleepTask(500);
                tapPoint(result.getPoint());
                return true;
            }
            logInfo("Hero's Mission event not found. Swiping right and retrying...");
            swipe(new DTOPoint(630, 143), new DTOPoint(400, 128));
            sleepTask(300);
        }

        return false;
    }

    private void handleHeroMissionEvent() {
        ReaperAvailabilityResult reaperStatus = reapersAvailable();

        if (reaperStatus.isOcrError()) {
            logWarning("OCR error while checking reaper availability. Retrying in 5 minutes.");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }

        if (!reaperStatus.isAvailable()) {
            logInfo("No reapers available. Rescheduling task for next reset.");
            reschedule(UtilTime.getGameReset());
            return;
        }

        claimAllRewards();
        if (!rallyReaper()) {
            reschedule(LocalDateTime.now().plusMinutes(5));
        }
    }

    private boolean rallyReaper() {
        DTOImageSearchResult button = searchTemplateWithRetries(EnumTemplates.HERO_MISSION_EVENT_TRACE_BUTTON, 90,
                3);
        if (!button.isFound()) {
            button = searchTemplateWithRetries(EnumTemplates.HERO_MISSION_EVENT_CAPTURE_BUTTON, 90, 3);
            if (!button.isFound()) {
                logWarning(
                        "Could not find 'Trace' or 'Capture' button to rally reapers. Rescheduling to try again in 5 minutes.");
                return false;
            }
        }
        tapPoint(button.getPoint());
        sleepTask(3000);
        tapPoint(new DTOPoint(360, 584)); // Tap on the center of the screen to select the reaper
        sleepTask(300);

        // Search for rally button
        DTOImageSearchResult rallyButton = searchTemplateWithRetries(EnumTemplates.RALLY_BUTTON, 90, 3);

        if (!rallyButton.isFound()) {
            logDebug("Rally button not found. Rescheduling to try again in 5 minutes.");
            return false;
        }

        tapPoint(rallyButton.getPoint());
        sleepTask(1000);

        // Tap "Hold a Rally" button
        tapRandomPoint(new DTOPoint(275, 821), new DTOPoint(444, 856), 1, 400);
        sleepTask(500);

        // Select flag if needed
        if (useFlag) {
            selectFlag(flagNumber);
        }

        // Parse travel time
        long travelTimeSeconds = 0;
        DTOTesseractSettings settings = new DTOTesseractSettings.Builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setAllowedChars("0123456789:") // Only allow digits and ':'
                .build();

        try {
            String timeStr = OCRWithRetries(new DTOPoint(521, 1141), new DTOPoint(608, 1162), 5, settings);
            travelTimeSeconds = UtilTime.parseTimeToSeconds(timeStr) * 2 + 2;

            logInfo("Success parsing travel time: " + timeStr);
        } catch (Exception e) {
            logError("Error parsing travel time: " + e.getMessage());
        }

        Integer spentStamina = getSpentStamina();

        // Deploy march
        DTOImageSearchResult deploy = searchTemplateWithRetries(EnumTemplates.DEPLOY_BUTTON, 90, 3);

        if (!deploy.isFound()) {
            logDebug("Deploy button not found. Rescheduling to try again in 5 minutes.");
            return false;
        }

        tapPoint(deploy.getPoint());
        sleepTask(2000);

        deploy = searchTemplateWithRetries(EnumTemplates.DEPLOY_BUTTON, 90, 3);
        if (deploy.isFound()) {
            // Probably march got taken by auto-join or something
            logInfo("Deploy button still found after trying to deploy march. Rescheduling to try again in 5 minutes.");
            return false;
        }

        logInfo("March deployed successfully.");

        // Update stamina
        subtractStamina(spentStamina, true);

        if (travelTimeSeconds <= 0) {
            logError("Failed to parse travel time via OCR. Rescheduling in 10 minutes as fallback.");
            LocalDateTime rescheduleTime = LocalDateTime.now().plusMinutes(10);
            reschedule(rescheduleTime);
            logInfo("Reaper rally scheduled to return in "
                    + UtilTime.localDateTimeToDDHHMMSS(rescheduleTime));
            return true;
        }

        LocalDateTime rescheduleTime = LocalDateTime.now().plusSeconds(travelTimeSeconds).plusMinutes(5);
        reschedule(rescheduleTime);
        logInfo("Reaper rally scheduled to return in " + UtilTime.localDateTimeToDDHHMMSS(rescheduleTime));
        return true;
    }

    private void claimAllRewards() {
        List<DTOImageSearchResult> chests = searchTemplatesWithRetries(EnumTemplates.HERO_MISSION_EVENT_CHEST,
                new DTOPoint(116, 950), new DTOPoint(671, 1018), 90, 5, 5);

        if (!chests.isEmpty()) {
            logInfo("Found " + chests.size() + " chests to be claimed.");
        } else {
            logInfo("Didn't find any chests to be claimed.");
            return;
        }

        for (DTOImageSearchResult chest : chests) {
            if (chest.isFound()) {
                tapPoint(chest.getPoint());
                sleepTask(300);
                tapBackButton();
            }
        }

    }

    private ReaperAvailabilityResult reapersAvailable() {
        DTOTesseractSettings settingsRallied = new DTOTesseractSettings.Builder()
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(254, 254, 254)) // White text
                .setAllowedChars("0123456789") // Only allow digits
                .setDebug(true)
                .build();

        // Limited mode: Check how many reapers have been rallied
        Integer reapersRallied = readNumberValue(
                new DTOPoint(68, 1062),
                new DTOPoint(125, 1093),
                settingsRallied);

        if (reapersRallied == null) {
            logWarning("Failed to parse reapers rallied count via OCR: '" + reapersRallied + "'");
            sleepTask(500);
            return ReaperAvailabilityResult.OCR_ERROR_RALLIED_COUNT;
        }

        logInfo("Reapers rallied until now: " + reapersRallied);
        sleepTask(500);

        if (reapersRallied < 10) {
            return ReaperAvailabilityResult.AVAILABLE;
        } else {
            return ReaperAvailabilityResult.UNAVAILABLE;
        }
    }

    @Override
    public EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.WORLD;
    }

    @Override
    protected boolean consumesStamina() {
        return true;
    }

    /**
     * Represents the result of checking reaper availability
     */
    public enum ReaperAvailabilityResult {
        /**
         * Reapers are available (< 10 rallied)
         */
        AVAILABLE,

        /**
         * No reapers available (>= 10 rallied)
         */
        UNAVAILABLE,

        /**
         * Failed to read the OCR value for reapers rallied count
         */
        OCR_ERROR_RALLIED_COUNT;

        /**
         * Convenience method to check if reapers are available
         */
        public boolean isAvailable() {
            return this == AVAILABLE;
        }

        /**
         * Convenience method to check if result is an OCR error
         */
        public boolean isOcrError() {
            return this == OCR_ERROR_RALLIED_COUNT;
        }
    }

}
