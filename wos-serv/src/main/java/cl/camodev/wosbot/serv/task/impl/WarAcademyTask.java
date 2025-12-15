package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

/**
 * Task responsible for redeeming War Academy shards.
 * 
 * <p>
 * This task:
 * <ul>
 * <li>Navigates to War Academy via Research Center</li>
 * <li>Opens the Redeem section</li>
 * <li>Reads remaining shards count via OCR</li>
 * <li>Redeems maximum available shards</li>
 * <li>Checks if more shards are available</li>
 * <li>Reschedules based on shard availability</li>
 * </ul>
 * 
 * <p>
 * The task runs daily and reschedules to game reset when all shards are
 * redeemed.
 */
public class WarAcademyTask extends DelayedTask {

    // ========== Navigation Coordinates ==========
    private static final DTOPoint LEFT_MENU_SWIPE_START = new DTOPoint(255, 477);
    private static final DTOPoint LEFT_MENU_SWIPE_END = new DTOPoint(255, 425);
    private static final DTOPoint BUILDING_TAP_CENTER = new DTOPoint(360, 790);

    // ========== War Academy UI Coordinates ==========
    private static final DTOPoint REDEEM_TAB_BUTTON = new DTOPoint(642, 164);
    private static final DTOPoint SHARDS_OCR_TOP_LEFT = new DTOPoint(466, 456);
    private static final DTOPoint SHARDS_OCR_BOTTOM_RIGHT = new DTOPoint(624, 484);

    // ========== Redeem Action Coordinates ==========
    private static final DTOPoint REDEEM_BUTTON = new DTOPoint(545, 520);
    private static final DTOPoint MAX_SHARDS_BUTTON = new DTOPoint(614, 705);
    private static final DTOPoint CONFIRM_BUTTON = new DTOPoint(358, 828);

    // ========== Constants ==========
    private static final int MIN_RESEARCH_CENTERS_REQUIRED = 2;
    private static final int BUILDING_TAP_COUNT = 5;
    private static final int BUILDING_TAP_DELAY = 100;
    private static final int RETRY_DELAY_MINUTES = 5;
    private static final int ADDITIONAL_SHARDS_DELAY_HOURS = 2;

    // ========== OCR Settings ==========
    private static final DTOTesseractSettings SHARDS_OCR_SETTINGS = DTOTesseractSettings.builder()
            .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
            .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
            .setAllowedChars("0123456789")
            .build();

    public WarAcademyTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    protected void execute() {

        logInfo("Starting War Academy task.");

        if (!navigateToWarAcademy()) {
            logWarning("Failed to navigate to War Academy.");
            reschedule(LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES));
            return;
        }

        if (!openRedeemSection()) {
            logWarning("Failed to open Redeem section.");
            reschedule(LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES));
            return;
        }

        Integer remainingShards = readRemainingShards();

        if (remainingShards == null) {
            logError("Failed to read remaining shards count.");
            reschedule(LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES));
            return;
        }

        if (remainingShards <= 0) {
            logInfo("No shards available to redeem.");
            reschedule(UtilTime.getGameReset());
            return;
        }

        logInfo(String.format("Found %d shards to redeem.", remainingShards));

        if (!redeemMaximumShards()) {
            logWarning("Failed to redeem shards.");
            reschedule(LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES));
            return;
        }

        handlePostRedemptionCheck();
    }

    /**
     * Navigates to War Academy via Research Center.
     * 
     * <p>
     * Navigation flow:
     * <ol>
     * <li>Opens left city menu</li>
     * <li>Swipes to bring Research Centers into view</li>
     * <li>Finds at least 2 Research Center icons</li>
     * <li>Taps the bottommost one (War Academy)</li>
     * <li>Taps building center to enter</li>
     * <li>Opens Research button</li>
     * <li>Verifies War Academy UI is visible</li>
     * </ol>
     * 
     * @return true if navigation succeeded, false otherwise
     */
    private boolean navigateToWarAcademy() {
        logInfo("Navigating to War Academy.");

        marchHelper.openLeftMenuCitySection(true);

        // Swipe to bring Research Centers into view
        logDebug("Swiping to reveal Research Centers");
        swipe(LEFT_MENU_SWIPE_START, LEFT_MENU_SWIPE_END);
        sleepTask(500); // Wait for swipe animation

        // Search for Research Centers
        List<DTOImageSearchResult> researchCenters = templateSearchHelper.searchTemplates(
                EnumTemplates.GAME_HOME_SHORTCUTS_RESEARCH_CENTER,
                SearchConfigConstants.MULTIPLE_RESULTS);

        if (researchCenters.size() < MIN_RESEARCH_CENTERS_REQUIRED) {
            logError(String.format("Only found %d Research Centers, need at least %d.",
                    researchCenters.size(), MIN_RESEARCH_CENTERS_REQUIRED));
            return false;
        }

        logInfo(String.format("Found %d Research Centers.", researchCenters.size()));

        // Tap the bottommost Research Center (highest Y coordinate = War Academy)
        DTOImageSearchResult warAcademyCenter = researchCenters.stream()
                .max(Comparator.comparingInt(r -> r.getPoint().getY()))
                .orElseThrow(() -> new RuntimeException("No valid Research Center found"));

        logDebug("Tapping War Academy (bottommost Research Center)");
        tapPoint(warAcademyCenter.getPoint());
        sleepTask(1000); // Wait for building zoom

        // Tap building center to enter
        logDebug("Tapping building center to enter");
        tapRandomPoint(BUILDING_TAP_CENTER, BUILDING_TAP_CENTER, BUILDING_TAP_COUNT, BUILDING_TAP_DELAY);
        sleepTask(1000); // Wait for building to open

        // Open Research section
        DTOImageSearchResult researchButton = templateSearchHelper.searchTemplate(
                EnumTemplates.BUILDING_BUTTON_RESEARCH,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!researchButton.isFound()) {
            logError("Research button not found.");
            return false;
        }

        logDebug("Opening Research section");
        tapPoint(researchButton.getPoint());
        sleepTask(500); // Wait for Research screen

        // Verify War Academy UI
        DTOImageSearchResult warAcademyUI = templateSearchHelper.searchTemplate(
                EnumTemplates.VALIDATION_WAR_ACADEMY_UI,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!warAcademyUI.isFound()) {
            logError("War Academy UI validation failed.");
            return false;
        }

        logInfo("Successfully navigated to War Academy.");
        return true;
    }

    /**
     * Opens the Redeem section in War Academy.
     * 
     * @return true if section opened successfully, false otherwise
     */
    private boolean openRedeemSection() {
        logDebug("Opening Redeem section");

        tapPoint(REDEEM_TAB_BUTTON);
        sleepTask(500); // Wait for tab to load

        return true;
    }

    /**
     * Reads the remaining shards count via OCR using integerHelper.
     * 
     * @return number of remaining shards, or null if reading failed
     */
    private Integer readRemainingShards() {
        logInfo("Reading remaining shards count via OCR.");

        Integer shards = integerHelper.execute(
                SHARDS_OCR_TOP_LEFT,
                SHARDS_OCR_BOTTOM_RIGHT,
                5,
                200L,
                SHARDS_OCR_SETTINGS,
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));

        if (shards != null) {
            logInfo("Successfully read shards count: " + shards);
        } else {
            logWarning("Failed to read shards count via OCR.");
        }

        return shards;
    }

    /**
     * Redeems the maximum number of available shards.
     * 
     * <p>
     * Redemption flow:
     * <ol>
     * <li>Taps Redeem button</li>
     * <li>Selects maximum shards</li>
     * <li>Confirms redemption</li>
     * </ol>
     * 
     * @return true if redemption succeeded, false otherwise
     */
    private boolean redeemMaximumShards() {
        logInfo("Redeeming maximum available shards.");

        // Tap Redeem button
        logDebug("Tapping Redeem button");
        tapPoint(REDEEM_BUTTON);
        sleepTask(500); // Wait for redemption dialog

        // Select maximum shards
        logDebug("Selecting maximum shards");
        tapPoint(MAX_SHARDS_BUTTON);
        sleepTask(100); // Wait for selection

        // Confirm redemption
        logDebug("Confirming redemption");
        tapPoint(CONFIRM_BUTTON);
        sleepTask(1000); // Wait for redemption to process

        logInfo("Shards redeemed successfully.");
        return true;
    }

    /**
     * Checks if additional shards are available after redemption
     * and reschedules accordingly.
     */
    private void handlePostRedemptionCheck() {
        logInfo("Checking for additional shards after redemption.");

        Integer finalShards = readRemainingShards();

        if (finalShards == null) {
            logError("Failed to read final shards count.");
            reschedule(LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES));
            return;
        }

        if (finalShards > 0) {
            logInfo(String.format("Additional shards found: %d. Rescheduling in %d hours.",
                    finalShards, ADDITIONAL_SHARDS_DELAY_HOURS));
            reschedule(LocalDateTime.now().plusHours(ADDITIONAL_SHARDS_DELAY_HOURS));
        } else {
            logInfo("No additional shards found. Rescheduling for game reset.");
            reschedule(UtilTime.getGameReset());
        }
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return false;
    }
}