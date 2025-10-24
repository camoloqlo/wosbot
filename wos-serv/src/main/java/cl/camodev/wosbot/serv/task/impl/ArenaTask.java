package cl.camodev.wosbot.serv.task.impl;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.awt.Color;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

/**
 * Task responsible for managing arena challenges.
 * 
 * <p>
 * This task:
 * <ul>
 * <li>Navigates to the arena via the Marksman Camp</li>
 * <li>Checks available challenge attempts</li>
 * <li>Optionally purchases extra attempts with gems</li>
 * <li>Challenges opponents with lower power (detected via text color)</li>
 * <li>Refreshes opponent list (free or with gems)</li>
 * <li>Runs at a configured activation time in UTC</li>
 * </ul>
 * 
 * <p>
 * <b>Power Comparison Strategy:</b>
 * Uses color detection on opponent power text instead of OCR:
 * <ul>
 * <li>Green text = opponent has lower power (challenge them)</li>
 * <li>Red text = opponent has higher power (skip them)</li>
 * </ul>
 */
public class ArenaTask extends DelayedTask {

    // ========== Configuration Keys ==========
    private static final String DEFAULT_ACTIVATION_TIME = "23:55"; // UTC
    private static final int DEFAULT_EXTRA_ATTEMPTS = 0;
    private static final boolean DEFAULT_REFRESH_WITH_GEMS = false;

    // ========== Arena Coordinates ==========
    // Arena screen
    private static final DTOPoint ARENA_ICON = new DTOPoint(702, 727);
    private static final DTOPoint ARENA_SCORE_TOP_LEFT = new DTOPoint(567, 1065);
    private static final DTOPoint ARENA_SCORE_BOTTOM_RIGHT = new DTOPoint(649, 1099);

    // Challenge list
    private static final DTOPoint CHALLENGES_LEFT_TOP_LEFT = new DTOPoint(405, 951);
    private static final DTOPoint CHALLENGES_LEFT_BOTTOM_RIGHT = new DTOPoint(439, 986);
    private static final DTOPoint EXTRA_ATTEMPTS_BUTTON = new DTOPoint(467, 965);

    // Opponent list (Y coordinates for 5 opponents)
    private static final int OPPONENT_BASE_Y_FIRST_RUN = 380;
    private static final int OPPONENT_BASE_Y_NORMAL = 354;
    private static final int OPPONENT_Y_SPACING = 128;
    private static final int OPPONENT_CHALLENGE_BUTTON_X = 624;

    // Power text color detection area (relative to opponent Y)
    private static final int POWER_TEXT_RELATIVE_X = 185;
    private static final int POWER_TEXT_WIDTH = 30; // 185 to 215
    private static final int POWER_TEXT_HEIGHT = 14;

    // Battle controls
    private static final DTOPoint BATTLE_START_BUTTON = new DTOPoint(530, 1200);
    private static final DTOPoint BATTLE_PAUSE_BUTTON = new DTOPoint(60, 962);
    private static final DTOPoint BATTLE_RETREAT_BUTTON = new DTOPoint(252, 635);

    // Battle result OCR regions
    private static final DTOPoint VICTORY_TEXT_TOP_LEFT = new DTOPoint(174, 387);
    private static final DTOPoint VICTORY_TEXT_BOTTOM_RIGHT = new DTOPoint(538, 503);

    // Extra attempts purchase
    private static final DTOPoint PURCHASE_PRICE_TOP_LEFT = new DTOPoint(328, 840);
    private static final DTOPoint PURCHASE_PRICE_BOTTOM_RIGHT = new DTOPoint(433, 883);
    private static final DTOPoint PURCHASE_COUNTER_SWIPE_START = new DTOPoint(420, 733);
    private static final DTOPoint PURCHASE_COUNTER_SWIPE_END = new DTOPoint(40, 733);
    private static final DTOPoint PURCHASE_CONFIRM_BUTTON = new DTOPoint(360, 860);
    private static final DTOPoint PURCHASE_COUNTER_INCREMENT_TOP_LEFT = new DTOPoint(457, 713);
    private static final DTOPoint PURCHASE_COUNTER_INCREMENT_BOTTOM_RIGHT = new DTOPoint(499, 752);
    private static final DTOPoint REFRESH_CONFIRM_BUTTON = new DTOPoint(210, 712);

    // ========== Arena Constants ==========
    private static final int INITIAL_ARENA_SCORE = 1000;
    private static final int MAX_GEM_REFRESHES = 5;
    private static final int[] ATTEMPT_PRICES = { 100, 200, 400, 600, 800 };
    private static final int MIN_COLORED_PIXELS_THRESHOLD = 10;
    private static final double GREEN_DOMINANCE_RATIO = 1.5;
    private static final int COLOR_ANALYSIS_STEP_SIZE = 2;
    private static final int MAX_OPPONENTS = 5;

    // ========== Configuration (loaded in loadConfiguration()) ==========
    private String activationTime;
    private int extraAttempts;
    private boolean refreshWithGems;

    // ========== Execution State (reset each execution) ==========
    private int attempts;
    private boolean firstRun;
    private int gemRefreshCount;

    public ArenaTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    /**
     * Loads task configuration from profile.
     * Must be called at the start of execute() after profile refresh.
     */
    private void loadConfiguration() {
        String configuredTime = profile.getConfig(
                EnumConfigurationKey.ARENA_TASK_ACTIVATION_TIME_STRING, String.class);
        this.activationTime = (configuredTime != null) ? configuredTime : DEFAULT_ACTIVATION_TIME;

        Integer configuredAttempts = profile.getConfig(
                EnumConfigurationKey.ARENA_TASK_EXTRA_ATTEMPTS_INT, Integer.class);
        this.extraAttempts = (configuredAttempts != null) ? configuredAttempts : DEFAULT_EXTRA_ATTEMPTS;

        Boolean configuredRefresh = profile.getConfig(
                EnumConfigurationKey.ARENA_TASK_REFRESH_WITH_GEMS_BOOL, Boolean.class);
        this.refreshWithGems = (configuredRefresh != null) ? configuredRefresh : DEFAULT_REFRESH_WITH_GEMS;

        logDebug(String.format("Configuration loaded - Time: %s, Extra attempts: %d, Refresh with gems: %s",
                activationTime, extraAttempts, refreshWithGems));
    }

    /**
     * Resets execution-specific state variables.
     * Must be called at the start of each execute() run.
     */
    private void resetExecutionState() {
        this.attempts = 0;
        this.firstRun = false;
        this.gemRefreshCount = 0;
        logDebug("Execution state reset");
    }

    @Override
    protected void execute() {
        loadConfiguration();
        resetExecutionState();

        // Validate activation time format
        if (!isValidTimeFormat(activationTime)) {
            logWarning("Invalid activation time format: " + activationTime +
                    ". Scheduling to 5 min before reset.");
            reschedule(UtilTime.getGameReset().minusMinutes(5));
            return;
        }

        // Validate timing window
        if (!isWithinExecutionWindow()) {
            return; // Reschedule handled inside validation
        }

        logInfo("Starting arena task.");

        if (!navigateToArena()) {
            logWarning("Failed to navigate to arena.");
            rescheduleWithActivationHour();
            return;
        }

        firstRun = detectFirstRun();

        if (!openChallengeList()) {
            logWarning("Failed to open challenge list.");
            rescheduleWithActivationHour();
            return;
        }

        if (!readInitialAttempts()) {
            logWarning("Failed to read initial attempts.");
            rescheduleWithActivationHour();
            return;
        }

        purchaseExtraAttemptsIfConfigured();

        processChallenges();

        logInfo("Arena task completed successfully.");
        rescheduleWithActivationHour();
    }

    /**
     * Validates if the current time is within the configured execution window.
     * Window runs from activation time until 23:55 UTC.
     * 
     * @return true if within window, false otherwise (task will be rescheduled)
     */
    private boolean isWithinExecutionWindow() {
        if (!isValidTimeFormat(activationTime)) {
            return true; // No timing restriction if format is invalid
        }

        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
        String[] timeParts = activationTime.split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        ZonedDateTime scheduledTimeUtc = nowUtc.toLocalDate()
                .atTime(hour, minute)
                .atZone(ZoneId.of("UTC"));
        ZonedDateTime cutoffTimeUtc = nowUtc.toLocalDate()
                .atTime(23, 56)
                .atZone(ZoneId.of("UTC"));

        if (nowUtc.isBefore(scheduledTimeUtc)) {
            logDebug(String.format("Task triggered too early (current: %s UTC, scheduled: %s UTC). " +
                    "Rescheduling for scheduled time.",
                    nowUtc.format(DateTimeFormatter.ofPattern("HH:mm")), activationTime));
            rescheduleForToday();
            return false;
        }

        if (nowUtc.isAfter(cutoffTimeUtc)) {
            logDebug(String.format("Task triggered too late (current: %s UTC, cutoff: 23:55 UTC). " +
                    "Scheduling for tomorrow.",
                    nowUtc.format(DateTimeFormatter.ofPattern("HH:mm"))));
            rescheduleWithActivationHour();
            return false;
        }

        logDebug(String.format("Task running within window (current: %s UTC, window: %s - 23:55 UTC)",
                nowUtc.format(DateTimeFormatter.ofPattern("HH:mm")), activationTime));
        return true;
    }

    /**
     * Navigates to the arena screen via Marksman Camp.
     * 
     * <p>
     * Navigation flow:
     * <ol>
     * <li>Opens event list from left sidebar</li>
     * <li>Selects event category</li>
     * <li>Finds and taps Marksman Camp shortcut</li>
     * <li>Taps arena icon to enter</li>
     * </ol>
     * 
     * @return true if navigation succeeded, false otherwise
     */
    private boolean navigateToArena() {
        logDebug("Opening event list");

        // Open left menu on city section
        openLeftMenuCitySection(true);

        logDebug("Searching for Marksman Camp shortcut");
        DTOImageSearchResult marksmanResult = searchTemplateWithRetries(
                EnumTemplates.GAME_HOME_SHORTCUTS_MARKSMAN);

        if (!marksmanResult.isFound()) {
            logError("Marksman camp shortcut not found.");
            return false;
        }

        logDebug("Opening Marksman Camp");
        tapPoint(marksmanResult.getPoint());
        sleepTask(1000); // Wait for Marksman Camp to load

        logDebug("Entering arena");
        tapPoint(ARENA_ICON);
        sleepTask(1000); // Wait for arena screen to load

        return true;
    }

    /**
     * Detects if this is the first arena run by checking if score is 1000.
     * First runs have different opponent list positioning.
     * 
     * @return true if first run detected, false otherwise
     */
    private boolean detectFirstRun() {
        logDebug("Checking if this is first arena run");

        DTOTesseractSettings settings = DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255))
                .setAllowedChars("0123456789")
                .build();

        Integer arenaScore = readNumberValue(
                ARENA_SCORE_TOP_LEFT,
                ARENA_SCORE_BOTTOM_RIGHT,
                settings);

        if (arenaScore == null) {
            logWarning("Failed to read arena score, assuming not first run");
            return false;
        }

        logInfo("Arena score: " + arenaScore);
        boolean isFirstRun = (arenaScore == INITIAL_ARENA_SCORE);

        if (isFirstRun) {
            logInfo("First run detected (score = 1000)");
        }

        return isFirstRun;
    }

    /**
     * Opens the challenge list by tapping the Challenge button.
     * 
     * @return true if challenge list opened successfully, false otherwise
     */
    private boolean openChallengeList() {
        logDebug("Opening challenge list");

        DTOImageSearchResult challengeResult = searchTemplateWithRetries(
                EnumTemplates.ARENA_CHALLENGE_BUTTON);

        if (!challengeResult.isFound()) {
            logError("Challenge button not found.");
            return false;
        }

        tapPoint(challengeResult.getPoint());
        sleepTask(1000); // Wait for challenge list to load

        return true;
    }

    /**
     * Reads the initial number of available challenge attempts via OCR.
     * 
     * @return true if attempts were successfully read, false otherwise
     */
    private boolean readInitialAttempts() {
        logDebug("Reading initial challenge attempts");

        DTOTesseractSettings settings = DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(91, 112, 147))
                .setAllowedChars("0123456789")
                .build();

        Integer attemptsRead = readNumberValue(
                CHALLENGES_LEFT_TOP_LEFT,
                CHALLENGES_LEFT_BOTTOM_RIGHT,
                settings);

        if (attemptsRead == null) {
            logError("Failed to read initial attempts via OCR");
            return false;
        }

        this.attempts = attemptsRead;
        logInfo("Initial attempts available: " + attempts);
        return true;
    }

    /**
     * Purchases extra attempts if configured to do so.
     * Updates the attempts counter with the number of attempts bought.
     */
    private void purchaseExtraAttemptsIfConfigured() {
        if (extraAttempts <= 0) {
            logDebug("No extra attempts configured to purchase");
            return;
        }

        logInfo("Extra attempts configured: " + extraAttempts);
        int attemptsBought = buyExtraAttempts();

        if (attemptsBought > 0) {
            attempts += attemptsBought;
            logInfo(String.format("Purchased %d extra attempts. Total attempts: %d",
                    attemptsBought, attempts));
        }
    }

    /**
     * Purchases extra arena attempts using gems.
     * 
     * <p>
     * Purchase flow:
     * <ol>
     * <li>Opens purchase dialog via "+" button</li>
     * <li>Resets counter to zero via swipe</li>
     * <li>Reads current price to determine position in sequence</li>
     * <li>Calculates how many more attempts to buy</li>
     * <li>Increments counter and confirms purchase</li>
     * </ol>
     * 
     * @return number of attempts successfully purchased (0 if failed or none
     *         available)
     */
    private int buyExtraAttempts() {
        logDebug("Opening extra attempts purchase dialog");
        tapPoint(EXTRA_ATTEMPTS_BUTTON);
        sleepTask(1000); // Wait for purchase dialog to open

        DTOImageSearchResult confirmResult = searchTemplateWithRetries(
                EnumTemplates.ARENA_GEMS_EXTRA_ATTEMPTS_BUTTON);

        if (!confirmResult.isFound()) {
            logInfo("No more extra attempts available for purchase");
            return 0;
        }

        // Reset counter to zero
        logDebug("Resetting purchase counter to zero");
        swipe(PURCHASE_COUNTER_SWIPE_START, PURCHASE_COUNTER_SWIPE_END);
        sleepTask(300); // Wait for swipe animation

        // Determine current position in price sequence
        int previousAttempts = detectCurrentAttemptPosition();
        if (previousAttempts < 0) {
            tapBackButton();
            return 0;
        }

        // Calculate how many more attempts we can/want to buy
        int remainingAttempts = calculateRemainingAttemptsToBuy(previousAttempts);
        if (remainingAttempts <= 0) {
            tapBackButton();
            return 0;
        }

        // Calculate expected total price
        int expectedPrice = calculateTotalPrice(previousAttempts, remainingAttempts);
        logInfo(String.format("Buying %d attempts for %d gems (already have %d)",
                remainingAttempts, expectedPrice, previousAttempts));

        // Increment counter to desired amount (counter starts at 1, so increment n-1
        // times)
        if (remainingAttempts > 1) {
            tapRandomPoint(
                    PURCHASE_COUNTER_INCREMENT_TOP_LEFT,
                    PURCHASE_COUNTER_INCREMENT_BOTTOM_RIGHT,
                    remainingAttempts - 1,
                    400);
            sleepTask(300); // Wait for counter animation
        }

        // Confirm purchase
        tapPoint(PURCHASE_CONFIRM_BUTTON);
        sleepTask(500); // Wait for purchase confirmation

        return remainingAttempts;
    }

    /**
     * Detects the current position in the attempt purchase sequence by reading
     * the displayed price and matching it against known prices.
     * 
     * @return attempt position (0-4), or -1 if detection failed
     */
    private int detectCurrentAttemptPosition() {
        logDebug("Detecting current attempt position via price");

        DTOTesseractSettings settings = DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255))
                .setAllowedChars("0123456789")
                .build();

        Integer singleAttemptPrice = readNumberValue(
                PURCHASE_PRICE_TOP_LEFT,
                PURCHASE_PRICE_BOTTOM_RIGHT,
                settings);

        if (singleAttemptPrice == null) {
            logWarning("Failed to read single attempt price");
            return -1;
        }

        // Find matching price in sequence
        for (int i = 0; i < ATTEMPT_PRICES.length; i++) {
            if (ATTEMPT_PRICES[i] == singleAttemptPrice) {
                logDebug(String.format("Detected position %d (price: %d gems)", i, singleAttemptPrice));
                return i;
            }
        }

        logWarning(String.format("Unexpected attempt price: %d gems", singleAttemptPrice));
        return -1;
    }

    /**
     * Calculates how many more attempts should be purchased based on
     * current position and configured limit.
     * 
     * @param previousAttempts how many attempts have already been bought
     * @return number of attempts to buy (0 if none should be bought)
     */
    private int calculateRemainingAttemptsToBuy(int previousAttempts) {
        if (previousAttempts >= extraAttempts) {
            logInfo(String.format("Already have %d attempts (wanted %d), no need to buy more",
                    previousAttempts, extraAttempts));
            return 0;
        }

        int canBuy = ATTEMPT_PRICES.length - previousAttempts;
        int wantToBuy = extraAttempts - previousAttempts;
        int toBuy = Math.min(canBuy, wantToBuy);

        if (toBuy <= 0) {
            logWarning("Cannot purchase any more attempts (max limit reached)");
            return 0;
        }

        return toBuy;
    }

    /**
     * Calculates the total gem cost for purchasing a range of attempts.
     * 
     * @param startPosition starting position in price sequence
     * @param count         how many attempts to buy
     * @return total gem cost
     */
    private int calculateTotalPrice(int startPosition, int count) {
        int totalPrice = 0;
        for (int i = startPosition; i < startPosition + count; i++) {
            totalPrice += ATTEMPT_PRICES[i];
        }
        return totalPrice;
    }

    /**
     * Processes all available challenge attempts.
     * 
     * <p>
     * For each attempt:
     * <ol>
     * <li>Scans opponents from top to bottom</li>
     * <li>Analyzes power text color (green = weaker, red = stronger)</li>
     * <li>Challenges first weaker opponent found</li>
     * <li>If no weaker opponents, tries to refresh list</li>
     * <li>If no refreshes available, challenges first opponent anyway</li>
     * </ol>
     */
    private void processChallenges() {
        logInfo(String.format("Processing %d challenge attempts", attempts));

        while (attempts > 0) {
            boolean foundWeakerOpponent = scanAndChallengeWeakerOpponent();

            if (!foundWeakerOpponent) {
                if (!tryRefreshOpponentList()) {
                    // No refresh available, challenge first opponent to avoid wasting attempts
                    challengeFirstOpponent();
                }
            }
        }

        logInfo("All challenge attempts used.");
    }

    /**
     * Scans the opponent list from top to bottom and challenges the first
     * opponent with predominantly green power text (indicating lower power).
     * 
     * @return true if a weaker opponent was found and challenged, false otherwise
     */
    private boolean scanAndChallengeWeakerOpponent() {
        int baseY = firstRun ? OPPONENT_BASE_Y_FIRST_RUN : OPPONENT_BASE_Y_NORMAL;

        for (int i = 0; i < MAX_OPPONENTS; i++) {
            if (attempts <= 0) {
                break;
            }

            int opponentY = baseY + (i * OPPONENT_Y_SPACING);

            if (isOpponentWeaker(opponentY, i + 1)) {
                challengeOpponent(opponentY, i + 1);
                attempts--;

                boolean victory = checkBattleResult();
                if (victory) {
                    firstRun = false; // After first victory, UI changes
                }

                sleepTask(1000); // Wait before scanning next opponent
                return true;
            }
        }

        return false;
    }

    /**
     * Analyzes the power text color for a specific opponent to determine
     * if they are weaker (green text) or stronger (red text).
     * 
     * @param opponentY      Y-coordinate of the opponent row
     * @param opponentNumber opponent number for logging (1-5)
     * @return true if opponent is weaker (green text), false otherwise
     */
    private boolean isOpponentWeaker(int opponentY, int opponentNumber) {
        logDebug(String.format("Analyzing opponent %d power (y=%d)", opponentNumber, opponentY));

        DTOPoint topLeft = new DTOPoint(POWER_TEXT_RELATIVE_X, opponentY);
        DTOPoint bottomRight = new DTOPoint(
                POWER_TEXT_RELATIVE_X + POWER_TEXT_WIDTH,
                opponentY + POWER_TEXT_HEIGHT);

        int[] colorCounts = emuManager.analyzeRegionColors(
                EMULATOR_NUMBER,
                topLeft,
                bottomRight,
                COLOR_ANALYSIS_STEP_SIZE);

        int backgroundPixels = colorCounts[0];
        int greenPixels = colorCounts[1];
        int redPixels = colorCounts[2];
        int totalColoredPixels = greenPixels + redPixels;

        logDebug(String.format("Color counts - Background: %d, Green: %d, Red: %d",
                backgroundPixels, greenPixels, redPixels));

        boolean isWeaker = (totalColoredPixels > MIN_COLORED_PIXELS_THRESHOLD) &&
                (greenPixels > redPixels * GREEN_DOMINANCE_RATIO);

        if (isWeaker) {
            logInfo(String.format("Opponent %d has lower power (green text dominant)", opponentNumber));
        } else {
            logDebug(String.format("Opponent %d has higher power (red text), skipping", opponentNumber));
        }

        return isWeaker;
    }

    /**
     * Challenges a specific opponent and handles the battle sequence.
     * 
     * @param opponentY      Y-coordinate of the opponent row
     * @param opponentNumber opponent number for logging
     */
    private void challengeOpponent(int opponentY, int opponentNumber) {
        logInfo(String.format("Challenging opponent %d", opponentNumber));

        tapPoint(new DTOPoint(OPPONENT_CHALLENGE_BUTTON_X, opponentY));
        sleepTask(2000); // Wait for challenge confirmation screen

        executeBattleSequence();
    }

    /**
     * Challenges the first opponent in the list regardless of power level.
     * Used when no refreshes are available and we want to use remaining attempts.
     */
    private void challengeFirstOpponent() {
        logInfo("No suitable opponents found. Challenging first opponent to use remaining attempts.");

        int baseY = firstRun ? OPPONENT_BASE_Y_FIRST_RUN : OPPONENT_BASE_Y_NORMAL;

        tapPoint(new DTOPoint(OPPONENT_CHALLENGE_BUTTON_X, baseY));
        sleepTask(2000); // Wait for challenge confirmation screen

        executeBattleSequence();
        attempts--;
        checkBattleResult(); // Don't care about result here
        firstRun = false;
        sleepTask(1000); // Wait before next action
    }

    /**
     * Executes the battle sequence by starting the battle, then immediately
     * pausing and retreating to skip the animation.
     */
    private void executeBattleSequence() {
        logDebug("Executing battle sequence (with animation skip)");

        tapPoint(BATTLE_START_BUTTON);
        sleepTask(3000); // Wait for battle to start

        tapPoint(BATTLE_PAUSE_BUTTON);
        sleepTask(500); // Wait for pause menu

        tapPoint(BATTLE_RETREAT_BUTTON);
        sleepTask(1000); // Wait for battle result screen
    }

    /**
     * Checks the battle result screen via OCR to determine victory or defeat.
     * 
     * @return true if victory, false if defeat or unknown
     */
    private boolean checkBattleResult() {
        TextRecognitionRetrier<String> textHelper = new TextRecognitionRetrier<>(provider);

        DTOTesseractSettings textSettings = DTOTesseractSettings.builder()
                //.setDebug(true)
                .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
                .build();

        String resultText = textHelper.execute(
                VICTORY_TEXT_TOP_LEFT,
                VICTORY_TEXT_BOTTOM_RIGHT,
                3,
                200L,
                textSettings,
                text -> text.matches("^[a-zA-Z]*$"),
                text -> text.toLowerCase());

        logDebug("OCR for result region: " + resultText);

        if (resultText.contains("victory")) {
            logInfo("Battle result: Victory");
            sleepTask(1000); // Wait before dismissing
            tapBackButton();
            return true;
        }

        logInfo("Battle result: Defeat");
        sleepTask(1000);
        tapBackButton();
        return false;
    }

    /**
     * Attempts to refresh the opponent list.
     * 
     * <p>
     * Refresh priority:
     * <ol>
     * <li>Free refresh (if available)</li>
     * <li>Gem refresh (if enabled and within limit)</li>
     * </ol>
     * 
     * @return true if refresh succeeded, false if no refresh available
     */
    private boolean tryRefreshOpponentList() {
        // Try free refresh first
        DTOImageSearchResult freeRefreshResult = searchTemplateWithRetries(
                EnumTemplates.ARENA_FREE_REFRESH_BUTTON);

        if (freeRefreshResult.isFound()) {
            logInfo("Using free refresh");
            tapPoint(freeRefreshResult.getPoint());
            sleepTask(1000); // Wait for list to refresh
            return true;
        }

        // Try gem refresh if enabled and within limit
        if (!refreshWithGems) {
            logDebug("Gem refresh disabled in configuration");
            return false;
        }

        if (gemRefreshCount >= MAX_GEM_REFRESHES) {
            logInfo(String.format("Gem refresh limit reached (%d/%d)",
                    gemRefreshCount, MAX_GEM_REFRESHES));
            return false;
        }

        DTOImageSearchResult gemsRefreshResult = searchTemplateWithRetries(
                EnumTemplates.ARENA_GEMS_REFRESH_BUTTON);

        if (!gemsRefreshResult.isFound()) {
            logDebug("Gem refresh button not found");
            return false;
        }

        gemRefreshCount++;
        logInfo(String.format("Using gem refresh (%d/%d)", gemRefreshCount, MAX_GEM_REFRESHES));

        tapPoint(gemsRefreshResult.getPoint());
        sleepTask(500); // Wait for confirmation popup

        // Confirm gem refresh
        DTOImageSearchResult confirmResult = searchTemplateWithRetries(
                EnumTemplates.ARENA_GEMS_REFRESH_CONFIRM_BUTTON);

        if (confirmResult.isFound()) {
            tapPoint(REFRESH_CONFIRM_BUTTON); // Tap checkbox if needed
            sleepTask(300); // Wait for checkbox animation
            tapPoint(confirmResult.getPoint());
            sleepTask(1000); // Wait for list to refresh
            return true;
        }

        logWarning("Gem refresh confirmation button not found");
        return false;
    }

    /**
     * Validates if a time string is in valid HH:mm format (24-hour clock).
     * 
     * @param time time string to validate
     * @return true if valid format, false otherwise
     */
    private boolean isValidTimeFormat(String time) {
        if (time == null || time.trim().isEmpty()) {
            return false;
        }

        try {
            String[] parts = time.split(":");
            if (parts.length != 2) {
                return false;
            }

            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);

            return hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 55;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Reschedules the task based on configured activation time.
     * If activation time is invalid, falls back to game reset time.
     */
    private void rescheduleWithActivationHour() {
        if (!isValidTimeFormat(activationTime)) {
            logInfo("Rescheduling Arena task for 5 min before game reset time (invalid activation time)");
            reschedule(UtilTime.getGameReset().minusMinutes(5));
            return;
        }

        try {
            String[] timeParts = activationTime.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
            ZonedDateTime tomorrowActivationUtc = nowUtc.toLocalDate()
                    .plusDays(1)
                    .atTime(hour, minute)
                    .atZone(ZoneId.of("UTC"));

            ZonedDateTime localActivationTime = tomorrowActivationUtc.withZoneSameInstant(
                    ZoneId.systemDefault());

            logInfo(String.format("Rescheduling Arena task for %s UTC tomorrow (%s local)",
                    activationTime,
                    localActivationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

            reschedule(localActivationTime.toLocalDateTime());

        } catch (Exception e) {
            logError("Failed to reschedule with activation time: " + e.getMessage());
            logInfo("Falling back to 5 min before game reset time");
            reschedule(UtilTime.getGameReset().minusMinutes(5));
        }
    }

    /**
     * Reschedules the task for today's activation time.
     * Used when task is triggered too early.
     */
    private void rescheduleForToday() {
        if (!isValidTimeFormat(activationTime)) {
            logInfo("Rescheduling Arena task for 5 min before game reset time (invalid activation time)");
            reschedule(UtilTime.getGameReset().minusMinutes(5));
            return;
        }

        try {
            String[] timeParts = activationTime.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
            ZonedDateTime todayActivationUtc = nowUtc.toLocalDate()
                    .atTime(hour, minute)
                    .atZone(ZoneId.of("UTC"));

            ZonedDateTime localActivationTime = todayActivationUtc.withZoneSameInstant(
                    ZoneId.systemDefault());

            logInfo(String.format("Rescheduling Arena task for %s UTC today (%s local)",
                    activationTime,
                    localActivationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

            reschedule(localActivationTime.toLocalDateTime());

        } catch (Exception e) {
            logError("Failed to reschedule for today's activation time: " + e.getMessage());
            logInfo("Falling back to 5 min before game reset time");
            reschedule(UtilTime.getGameReset().minusMinutes(5));
        }
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