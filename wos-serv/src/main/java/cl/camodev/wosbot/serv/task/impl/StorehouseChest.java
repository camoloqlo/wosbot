package cl.camodev.wosbot.serv.task.impl;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.regex.Pattern;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

/**
 * Task responsible for claiming rewards from the Storehouse.
 * 
 * <p>
 * This task:
 * <ul>
 * <li>Navigates to the Storehouse via Research Center</li>
 * <li>Claims daily chest rewards (available every few hours)</li>
 * <li>Claims stamina rewards (available once per day at game reset)</li>
 * <li>Reads timers via OCR to determine next availability</li>
 * <li>Reschedules based on the nearest reward time</li>
 * </ul>
 * 
 * <p>
 * <b>Reward Types:</b>
 * <ul>
 * <li>Chest: General resources, multiple claims per day</li>
 * <li>Stamina: 120 base stamina + bonus from Agnes expert</li>
 * </ul>
 */
public class StorehouseChest extends DelayedTask {

    // ========== Navigation Coordinates ==========
    private static final DTOPoint STOREHOUSE_LOCATION_TOP_LEFT = new DTOPoint(30, 430);
    private static final DTOPoint STOREHOUSE_LOCATION_BOTTOM_RIGHT = new DTOPoint(50, 470);
    private static final DTOPoint STOREHOUSE_SCROLL_START = new DTOPoint(1, 636);
    private static final DTOPoint STOREHOUSE_SCROLL_END = new DTOPoint(2, 636);

    // ========== Chest Reward Coordinates ==========
    private static final DTOPoint CHEST_TIMER_TOP_LEFT = new DTOPoint(266, 1100);
    private static final DTOPoint CHEST_TIMER_BOTTOM_RIGHT = new DTOPoint(450, 1145);

    // ========== Stamina Reward Coordinates ==========
    private static final DTOPoint STAMINA_AMOUNT_TOP_LEFT = new DTOPoint(436, 632);
    private static final DTOPoint STAMINA_AMOUNT_BOTTOM_RIGHT = new DTOPoint(487, 657);
    private static final DTOPoint STAMINA_CLAIM_BUTTON_TOP_LEFT = new DTOPoint(250, 930);
    private static final DTOPoint STAMINA_CLAIM_BUTTON_BOTTOM_RIGHT = new DTOPoint(450, 950);

    // ========== Fallback Timer OCR ==========
    private static final DTOPoint FALLBACK_TIMER_TOP_LEFT = new DTOPoint(285, 642);
    private static final DTOPoint FALLBACK_TIMER_BOTTOM_RIGHT = new DTOPoint(430, 666);

    // ========== Constants ==========
    private static final int TIMER_OCR_MAX_ATTEMPTS = 3;
    private static final int MAX_TIMER_SECONDS = 7200; // 2 hours
    private static final int FALLBACK_RESCHEDULE_MINUTES = 5;
    private static final int BASE_STOREHOUSE_STAMINA = 120;
    private static final int SCROLL_ATTEMPT_COUNT = 2;
    private static final int SCROLL_REPEAT_DELAY = 300;

    // ========== OCR Settings ==========
    private static final DTOTesseractSettings STAMINA_OCR_SETTINGS = DTOTesseractSettings.builder()
            .setTextColor(new Color(248, 247, 234))
            .setRemoveBackground(true)
            .setAllowedChars("0123456789")
            .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
            .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
            .build();

    // ========== Configuration (loaded in loadConfiguration()) ==========
    private String storedStaminaTime;
    private TextRecognitionRetrier<LocalDateTime> textHelper;

    // ========== Execution State (reset each execution) ==========
    private LocalDateTime nextChestTime;
    private LocalDateTime nextStaminaTime;

    public StorehouseChest(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    /**
     * Loads task configuration from profile.
     */
    private void loadConfiguration() {
        // Check if we have a stored stamina claim time
        String storedStaminaTime = profile.getConfig(
                EnumConfigurationKey.STOREHOUSE_STAMINA_CLAIM_TIME_STRING, String.class);
        this.storedStaminaTime = storedStaminaTime;

        this.textHelper = new TextRecognitionRetrier<>(provider);

        logDebug(String.format("Configuration loaded - Stored stamina time: %s", storedStaminaTime));
    }

    /**
     * Resets execution-specific state.
     */
    private void resetExecutionState() {
        this.nextChestTime = null;
        this.nextStaminaTime = null;
        logDebug("Execution state reset");
    }

    @Override
    protected void execute() {
        loadConfiguration();
        resetExecutionState();

        logInfo("Starting Storehouse task.");

        if (!openStorehouse()) {
            logWarning("Failed to open Storehouse.");
            reschedule(LocalDateTime.now().plusMinutes(FALLBACK_RESCHEDULE_MINUTES));
            return;
        }

        processChestReward();

        if (isTimeToClaimStamina()) {
            processStaminaReward();
        }

        scheduleToNearestTime();

        logInfo("Storehouse task completed successfully.");
    }

    /**
     * Opens the Storehouse by navigating through Research Center.
     */
    private boolean openStorehouse() {
        logDebug("Navigating to Storehouse");

        marchHelper.openLeftMenuCitySection(true);

        DTOImageSearchResult researchCenter = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_SHORTCUTS_RESEARCH_CENTER,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!researchCenter.isFound()) {
            logError("Research Center shortcut not found.");
            return false;
        }

        logDebug("Tapping Research Center");
        tapPoint(researchCenter.getPoint());
        sleepTask(1000); // Wait for Research Center to open

        // Navigate to Storehouse
        logDebug("Tapping on Storehouse to navigate");
        tapRandomPoint(STOREHOUSE_LOCATION_TOP_LEFT, STOREHOUSE_LOCATION_BOTTOM_RIGHT);
        sleepTask(1000);

        tapBackButton();

        return true;
    }

    /**
     * Processes the chest reward.
     * Searches for chest, claims it, and reads the next availability timer.
     */
    private void processChestReward() {
        logInfo("Searching for Storehouse chest reward.");

        DTOImageSearchResult chest = searchForChest();

        if (chest.isFound()) {
            logInfo("Chest found. Claiming reward.");
            tapPoint(chest.getPoint());
            sleepTask(500); // Wait for reward screen

            nextChestTime = readChestTimer();

            if (nextChestTime == null) {
                nextChestTime = LocalDateTime.now().plusMinutes(FALLBACK_RESCHEDULE_MINUTES);
                logWarning("Failed to read chest timer, using fallback.");
            }

            // Close reward screen
            tapRandomPoint(STOREHOUSE_SCROLL_START, STOREHOUSE_SCROLL_END, SCROLL_ATTEMPT_COUNT, SCROLL_REPEAT_DELAY);
            return;
        }

        logWarning("Chest not found after maximum attempts. Trying fallback timer reading.");
        nextChestTime = readFallbackTimer();

        if (nextChestTime == null) {
            nextChestTime = LocalDateTime.now().plusMinutes(FALLBACK_RESCHEDULE_MINUTES);
        }
    }

    /**
     * Searches for chest templates with retries.
     */
    private DTOImageSearchResult searchForChest() {
        DTOImageSearchResult chest = templateSearchHelper.searchTemplate(
                EnumTemplates.STOREHOUSE_CHEST,
                SearchConfigConstants.SINGLE_WITH_RETRIES);

        if (chest.isFound()) {
            logDebug("Storehouse chest found");
            return chest;
        }

        // Try alternative chest template
        return templateSearchHelper.searchTemplate(
                EnumTemplates.STOREHOUSE_CHEST_2,
                SearchConfigConstants.SINGLE_WITH_RETRIES);
    }

    /**
     * Reads the chest timer via OCR.
     */
    private LocalDateTime readChestTimer() {
        logDebug("Reading chest timer via OCR");

        DTOTesseractSettings settings = DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255))
                .setAllowedChars("0123456789:")
                .build();

        LocalDateTime cooldown = textHelper.execute(
                CHEST_TIMER_TOP_LEFT,
                CHEST_TIMER_BOTTOM_RIGHT,
                TIMER_OCR_MAX_ATTEMPTS,
                200L,
                settings,
                TimeValidators::isValidTime,
                TimeConverters::toLocalDateTime);

        if (cooldown == null) {
            logWarning("OCR returned empty time text");
            return null;
        }

        logDebug("Time OCR result: '" + UtilTime.localDateTimeToDDHHMMSS(cooldown) + "'");

        return cooldown;
    }

    /**
     * Checks if it's time to claim the stamina reward.
     * Stamina is claimed once per day at game reset.
     */
    private boolean isTimeToClaimStamina() {

        if (storedStaminaTime != null && !storedStaminaTime.isEmpty()) {
            try {
                LocalDateTime nextClaimTime = LocalDateTime.parse(storedStaminaTime);
                boolean timeToClaimAgain = LocalDateTime.now().isAfter(nextClaimTime);

                if (!timeToClaimAgain) {
                    logDebug("Stamina already claimed. Next claim at: " + nextClaimTime.format(DATETIME_FORMATTER));
                }

                nextStaminaTime = nextClaimTime;

                return timeToClaimAgain;
            } catch (Exception e) {
                logWarning("Failed to parse stored stamina claim time: " + e.getMessage());
            }
        }

        // First run or invalid stored time - allow claiming
        return true;
    }

    /**
     * Processes the stamina reward.
     * Searches for stamina, claims it, and reads the bonus amount.
     */
    private void processStaminaReward() {
        logInfo("Searching for Storehouse stamina reward.");

        DTOImageSearchResult stamina = templateSearchHelper.searchTemplate(
                EnumTemplates.STOREHOUSE_STAMINA,
                SearchConfigConstants.SINGLE_WITH_RETRIES);

        if (stamina.isFound()) {
            logInfo("Stamina reward found. Claiming.");
            tapPoint(stamina.getPoint());
            sleepTask(2000); // Wait for reward details screen

            claimStaminaReward();
            nextStaminaTime = UtilTime.getNextReset();
        } else {
            logWarning("Stamina reward not found after maximum attempts. Will retry in 1 hour as fallback.");
            nextStaminaTime = LocalDateTime.now().plusHours(1);
        }

        // Store the next claim time
        profile.setConfig(
                EnumConfigurationKey.STOREHOUSE_STAMINA_CLAIM_TIME_STRING,
                nextStaminaTime.toString());
        setShouldUpdateConfig(true);
    }

    /**
     * Claims the stamina reward and updates stamina service.
     */
    private void claimStaminaReward() {
        // Read Agnes bonus stamina amount
        Integer agnesStamina = integerHelper.execute(
                STAMINA_AMOUNT_TOP_LEFT,
                STAMINA_AMOUNT_BOTTOM_RIGHT,
                TIMER_OCR_MAX_ATTEMPTS,
                200L,
                STAMINA_OCR_SETTINGS,
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));

        // Claim button
        tapRandomPoint(STAMINA_CLAIM_BUTTON_TOP_LEFT, STAMINA_CLAIM_BUTTON_BOTTOM_RIGHT);
        sleepTask(4000); // Wait for claim animation

        // Update stamina service
        StaminaService.getServices().addStamina(profile.getId(), BASE_STOREHOUSE_STAMINA);

        if (agnesStamina != null && agnesStamina > 0) {
            StaminaService.getServices().addStamina(profile.getId(), agnesStamina);
            logInfo(String.format("Claimed %d base stamina + %d from Agnes bonus.",
                    BASE_STOREHOUSE_STAMINA, agnesStamina));
        } else {
            logInfo("Claimed " + BASE_STOREHOUSE_STAMINA + " base stamina.");
        }
    }

    /**
     * Reads timer using fallback OCR region.
     * Used when chest is not found but UI is still visible.
     */
    private LocalDateTime readFallbackTimer() {
        logDebug("Attempting fallback timer reading.");

        DTOTesseractSettings settings = DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255))
                .setAllowedChars("0123456789:")
                .build();

        LocalDateTime cooldown = textHelper.execute(
                FALLBACK_TIMER_TOP_LEFT,
                FALLBACK_TIMER_BOTTOM_RIGHT,
                TIMER_OCR_MAX_ATTEMPTS,
                200L,
                settings,
                TimeValidators::isValidTime,
                TimeConverters::toLocalDateTime);

        if (cooldown == null) {
            logWarning("OCR returned empty time text");
            return null;
        }

        logDebug("Time OCR result: '" + UtilTime.localDateTimeToDDHHMMSS(cooldown) + "'");

        // Validate timer is reasonable
        long secondsDiff = Duration.between(LocalDateTime.now(), cooldown).getSeconds();

        if (secondsDiff > MAX_TIMER_SECONDS) {
            logWarning(String.format("Timer exceeds 2 hours (%d min), using 1 hour fallback.", secondsDiff / 60));
            return LocalDateTime.now().plusHours(1);
        }

        return cooldown;
    }

    /**
     * Schedules the task to the nearest reward time.
     * Chest claims are checked more frequently than stamina (once per reset).
     */
    private void scheduleToNearestTime() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReset = UtilTime.getGameReset();

        // Validate chest time
        if (nextChestTime != null && nextChestTime.isBefore(now)) {
            logDebug("Chest time is in the past, treating as invalid.");
            nextChestTime = null;
        }

        // Cap chest time at reset to avoid missing stamina
        if (nextChestTime != null && nextChestTime.isAfter(nextReset)) {
            logInfo("Chest time exceeds reset, capping at reset time.");
            nextChestTime = nextReset;
        }

        // Validate stamina time
        if (nextStaminaTime != null && nextStaminaTime.isBefore(now)) {
            logDebug("Stamina time is in the past, treating as invalid.");
            nextStaminaTime = null;
        }

        // Determine which time is nearest and valid
        LocalDateTime scheduledTime;
        String reason;

        if (nextChestTime == null && nextStaminaTime == null) {
            scheduledTime = LocalDateTime.now().plusMinutes(FALLBACK_RESCHEDULE_MINUTES);
            reason = "No valid times (fallback)";
        } else if (nextChestTime == null) {
            scheduledTime = nextStaminaTime;
            reason = "stamina claim";
        } else if (nextStaminaTime == null) {
            scheduledTime = nextChestTime;
            reason = "chest claim";
        } else {
            // Both times valid - pick nearest
            if (nextChestTime.isBefore(nextStaminaTime)) {
                scheduledTime = nextChestTime;
                reason = "chest claim (nearest)";
            } else {
                scheduledTime = nextStaminaTime;
                reason = "stamina claim (nearest)";
            }
        }

        logInfo(String.format("Rescheduling for %s at: %s",
                reason, scheduledTime.format(DATETIME_FORMATTER)));

        if (!reason.contains("fallback")) {
            logDebug(String.format("Chest: %s, Stamina: %s",
                    (nextChestTime != null) ? nextChestTime.format(DATETIME_FORMATTER) : "null",
                    (nextStaminaTime != null) ? nextStaminaTime.format(DATETIME_FORMATTER) : "null"));
        }

        reschedule(scheduledTime);
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }
}