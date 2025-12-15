package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.serv.ocr.BotTextRecognitionProvider;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Alliance Mobilization Task - Manages automated participation in Alliance
 * Mobilization events.
 * 
 * <p>
 * This task handles:
 * <ul>
 * <li>Automatic navigation to Alliance Mobilization event screen</li>
 * <li>Detection and acceptance of tasks based on configurable filters:
 * <ul>
 * <li>Bonus percentage (200%, 120%, Both, Any)</li>
 * <li>Minimum points threshold (separate for 200% and 120%)</li>
 * <li>Task type enable/disable flags (14 different task types)</li>
 * </ul>
 * </li>
 * <li>Automatic task refresh when criteria not met</li>
 * <li>Collection of completed task rewards</li>
 * <li>Free mission bonus claims</li>
 * <li>Alliance Monuments interaction</li>
 * <li>Intelligent rescheduling based on:
 * <ul>
 * <li>Task refresh cooldowns</li>
 * <li>Task availability timers</li>
 * <li>Attempts counter (daily reset)</li>
 * <li>Event availability (weekly Monday reset)</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * <p>
 * <b>Task Selection Logic:</b>
 * <ol>
 * <li>Read remaining attempts counter</li>
 * <li>Check for completed tasks and collect rewards</li>
 * <li>Check for free mission bonus</li>
 * <li>For each available task:
 * <ul>
 * <li>Detect bonus percentage (200% or 120%)</li>
 * <li>Read points value via OCR</li>
 * <li>Detect task type via image recognition</li>
 * <li>Check if task type is enabled in config</li>
 * <li>Compare points against minimum threshold</li>
 * <li>Decision: Accept, Refresh, or Wait</li>
 * </ul>
 * </li>
 * <li>Check Alliance Monuments</li>
 * </ol>
 * 
 * <p>
 * <b>Rescheduling Strategy:</b>
 * <ul>
 * <li>If task refreshed: Reschedule for refresh cooldown + 5s buffer</li>
 * <li>If good task found but another running: Wait 1 hour</li>
 * <li>If no tasks available: Check availability timers, reschedule
 * accordingly</li>
 * <li>If no attempts remaining: Reschedule for daily reset</li>
 * <li>If event not active: Reschedule for next Monday</li>
 * <li>Default fallback: 5 minutes</li>
 * </ul>
 * 
 * <p>
 * <b>Configuration:</b>
 * <ul>
 * <li>Auto-accept enabled/disabled (user can accept manually if disabled)</li>
 * <li>Rewards percentage filter: "200%", "120%", "Both", "Any"</li>
 * <li>Minimum points for 200% tasks</li>
 * <li>Minimum points for 120% tasks</li>
 * <li>14 task type enable/disable flags</li>
 * </ul>
 * 
 * <p>
 * <b>Note:</b> Only one task can run at a time. If a task is already running,
 * other tasks will be refreshed to get better options, and the task will wait
 * for the running task to complete.
 */
public class AllianceMobilizationTask extends DelayedTask {

    // ========================================================================
    // CONSTANTS - OCR Patterns
    // ========================================================================

    /**
     * OCR-related patterns and configurations.
     */
    private static final class OCR {
        /** Pattern to match attempts counter format: "X / Y" or "X/Y" */
        static final Pattern ATTEMPTS_PATTERN = Pattern.compile("(\\d{1,2})\\s*/\\s*(\\d{0,3})");
    }

    // ========================================================================
    // CONSTANTS - Screen Coordinates
    // ========================================================================

    /**
     * Fixed screen coordinates for UI elements.
     * All coordinates are specific to the Alliance Mobilization screen layout.
     */
    private static final class Coords {
        // Attempts Counter (top of screen)
        static final DTOPoint ATTEMPTS_COUNTER_TOP_LEFT = new DTOPoint(168, 528);
        static final DTOPoint ATTEMPTS_COUNTER_BOTTOM_RIGHT = new DTOPoint(235, 565);

        // Task Interaction Buttons
        static final DTOPoint REFRESH_BUTTON = new DTOPoint(200, 805);
        static final DTOPoint REFRESH_CONFIRM_BUTTON = new DTOPoint(510, 790);
        static final DTOPoint ACCEPT_BUTTON = new DTOPoint(500, 805);
        static final DTOPoint FREE_MISSION_CONFIRM = new DTOPoint(340, 780);
        static final DTOPoint BACK_BUTTON = new DTOPoint(50, 50);

        // Alliance Monuments Click Sequence
        static final DTOPoint[] MONUMENT_CLICKS = {
                new DTOPoint(366, 1014),
                new DTOPoint(250, 870),
                new DTOPoint(366, 1014),
                new DTOPoint(154, 1002),
                new DTOPoint(245, 483)
        };

        // Free Mission Expected Location
        static final DTOPoint FREE_MISSION_EXPECTED = new DTOPoint(256, 527);

        // Task Availability Timers (when no tasks available)
        static final DTOPoint LEFT_TIMER_TOP_LEFT = new DTOPoint(162, 705);
        static final DTOPoint LEFT_TIMER_BOTTOM_RIGHT = new DTOPoint(280, 743);
        static final DTOPoint RIGHT_TIMER_TOP_LEFT = new DTOPoint(486, 705);
        static final DTOPoint RIGHT_TIMER_BOTTOM_RIGHT = new DTOPoint(595, 743);
    }

    // ========================================================================
    // CONSTANTS - Offsets for Detection
    // ========================================================================

    /**
     * Relative offsets for detecting UI elements near reference points.
     */
    private static final class Offsets {
        // Points Detection (relative to bonus indicator)
        static final int POINTS_X = 112;
        static final int POINTS_Y = 158;
        static final int POINTS_WIDTH = 75;
        static final int POINTS_HEIGHT = 34;

        // Task Type Detection (proximity to bonus)
        static final int TASK_TYPE_MAX_DELTA_X = 150;
        static final int TASK_TYPE_MAX_DELTA_Y = 100;

        // Running Task Detection (timer bar below bonus)
        static final int RUNNING_TASK_X = -50;
        static final int RUNNING_TASK_Y = 100;
        static final int RUNNING_TASK_WIDTH = 300;
        static final int RUNNING_TASK_HEIGHT = 150;

        // Free Mission Position Tolerance
        static final int FREE_MISSION_TOLERANCE = 50;
    }

    // ========================================================================
    // CONSTANTS - Limits and Retry Counts
    // ========================================================================

    /**
     * Maximum retry counts and search limits.
     */
    private static final class Limits {
        static final int MAX_NAVIGATION_ATTEMPTS = 3;
        static final int MAX_TEMPLATE_SEARCH_RESULTS_TASK_TYPE = 5;
        static final int MAX_TEMPLATE_SEARCH_RESULTS_120 = 2;
        static final int MONUMENT_BACK_CLICKS_COUNT = 2;
    }

    // ========================================================================
    // CONSTANTS - Timing and Delays
    // ========================================================================

    /**
     * Timing constants for rescheduling and delays.
     */
    private static final class Delays {
        static final int DEFAULT_COOLDOWN_SECONDS = 300; // 5 minutes
        static final int LONG_COOLDOWN_SECONDS = 1800; // 30 minutes
        static final int RESCHEDULE_BUFFER_SECONDS = 5;
        static final int RESCHEDULE_WAIT_HOURS = 1;
    }

    // ========================================================================
    // CONSTANTS - Template Match Thresholds
    // ========================================================================

    /**
     * Image matching thresholds (0-100).
     * Higher values require stricter matches.
     */
    private static final class Thresholds {
        static final int BONUS = 85;
        static final int TASK_TYPE = 85;
        static final int RUNNING = 85;
        static final int COMPLETED = 85;
        static final int FREE_MISSION = 90;
        static final int MONUMENTS = 94;
    }

    // ========================================================================
    // CONSTANTS - Default Configuration Values
    // ========================================================================

    /**
     * Default configuration values when user config is not set.
     */
    private static final class Defaults {
        static final boolean AUTO_ACCEPT = false;
        static final String REWARDS_PERCENTAGE = "Any";
        static final int MINIMUM_POINTS = 0;
        static final boolean TASK_TYPE_ENABLED = false;
    }

    // ========================================================================
    // Configuration Fields (loaded in loadConfiguration())
    // ========================================================================

    private boolean autoAcceptEnabled;
    private String rewardsPercentage;
    private int minimumPoints200;
    private int minimumPoints120;

    // Task type enable flags (14 types)
    private boolean buildSpeedupsEnabled;
    private boolean buyPackageEnabled;
    private boolean chiefGearCharmEnabled;
    private boolean chiefGearScoreEnabled;
    private boolean defeatBeastsEnabled;
    private boolean fireCrystalEnabled;
    private boolean gatherResourcesEnabled;
    private boolean heroGearStoneEnabled;
    private boolean mythicShardEnabled;
    private boolean rallyEnabled;
    private boolean trainTroopsEnabled;
    private boolean trainingSpeedupsEnabled;
    private boolean useGemsEnabled;
    private boolean useSpeedupsEnabled;

    // ========================================================================
    // OCR Helpers
    // ========================================================================

    private TextRecognitionRetrier<Duration> durationHelper;

    // ========================================================================
    // Mission Availability Tracking
    // ========================================================================

    /**
     * Tracks consecutive runs where no missions were found.
     * When this counter reaches 3, it indicates that both mission slots
     * have completed the maximum number of times for today, and the task
     * should be rescheduled for the next game reset (00:00 UTC).
     */
    private int consecutiveNoMissionsCount = 0;

    /**
     * Tracks consecutive runs where only one mission was found and it's running.
     * When this counter reaches 3, reschedules for 30 minutes to wait for
     * the running mission to complete and free up a slot.
     */
    private int consecutiveOnlyRunningMissionCount = 0;

    /**
     * Constructs a new AllianceMobilizationTask.
     *
     * @param profile the profile this task belongs to
     * @param tpTask  the task type enum
     */
    public AllianceMobilizationTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    /**
     * Initializes OCR helpers after EMULATOR_NUMBER is available.
     * Called at the start of execute() to ensure proper initialization.
     */
    private void initializeOCRHelpers() {
        BotTextRecognitionProvider provider = new BotTextRecognitionProvider(emuManager, EMULATOR_NUMBER);
        this.durationHelper = new TextRecognitionRetrier<>(provider);
    }

    /**
     * Loads task configuration from the profile.
     * This must be called from execute() to ensure configuration is current.
     * 
     * <p>
     * Loads:
     * <ul>
     * <li>Auto-accept flag (default: false)</li>
     * <li>Rewards percentage filter (default: "Any")</li>
     * <li>Minimum points for 200% tasks</li>
     * <li>Minimum points for 120% tasks</li>
     * <li>14 task type enable/disable flags (all default: false)</li>
     * </ul>
     */
    private void loadConfiguration() {
        this.autoAcceptEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_AUTO_ACCEPT_BOOL,
                Defaults.AUTO_ACCEPT);

        this.rewardsPercentage = getConfigString(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_REWARDS_PERCENTAGE_STRING,
                Defaults.REWARDS_PERCENTAGE);

        // Load minimum points with legacy migration
        this.minimumPoints200 = getConfigInt(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_MINIMUM_POINTS_200_INT,
                Defaults.MINIMUM_POINTS);

        this.minimumPoints120 = getConfigInt(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_MINIMUM_POINTS_120_INT,
                Defaults.MINIMUM_POINTS);

        // Load all task type flags
        loadTaskTypeFlags();

        logDebug(String.format(
                "Configuration loaded - Auto-accept: %s, Filter: %s, Min 200%%: %d, Min 120%%: %d",
                autoAcceptEnabled, rewardsPercentage, minimumPoints200, minimumPoints120));
    }

    /**
     * Loads all 14 task type enable/disable flags from configuration.
     */
    private void loadTaskTypeFlags() {
        this.buildSpeedupsEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_BUILD_SPEEDUPS_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.buyPackageEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_BUY_PACKAGE_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.chiefGearCharmEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_CHIEF_GEAR_CHARM_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.chiefGearScoreEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_CHIEF_GEAR_SCORE_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.defeatBeastsEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_DEFEAT_BEASTS_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.fireCrystalEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_FIRE_CRYSTAL_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.gatherResourcesEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_GATHER_RESOURCES_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.heroGearStoneEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_HERO_GEAR_STONE_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.mythicShardEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_MYTHIC_SHARD_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.rallyEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_RALLY_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.trainTroopsEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_TRAIN_TROOPS_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.trainingSpeedupsEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_TRAINING_SPEEDUPS_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.useGemsEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_USE_GEMS_BOOL,
                Defaults.TASK_TYPE_ENABLED);

        this.useSpeedupsEnabled = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_MOBILIZATION_USE_SPEEDUPS_BOOL,
                Defaults.TASK_TYPE_ENABLED);
    }

    /**
     * Helper method to safely retrieve boolean configuration values.
     * 
     * @param key          the configuration key to retrieve
     * @param defaultValue the default value if configuration is not set
     * @return the configured boolean value or default if not set
     */
    private boolean getConfigBoolean(EnumConfigurationKey key, boolean defaultValue) {
        Boolean value = profile.getConfig(key, Boolean.class);
        return (value != null) ? value : defaultValue;
    }

    /**
     * Helper method to safely retrieve integer configuration values.
     * 
     * @param key          the configuration key to retrieve
     * @param defaultValue the default value if configuration is not set
     * @return the configured integer value or default if not set
     */
    private int getConfigInt(EnumConfigurationKey key, int defaultValue) {
        Integer value = profile.getConfig(key, Integer.class);
        return (value != null) ? value : defaultValue;
    }

    /**
     * Helper method to safely retrieve string configuration values.
     * 
     * @param key          the configuration key to retrieve
     * @param defaultValue the default value if configuration is not set
     * @return the configured string value or default if not set
     */
    private String getConfigString(EnumConfigurationKey key, String defaultValue) {
        String value = profile.getConfig(key, String.class);
        return (value != null && !value.trim().isEmpty()) ? value : defaultValue;
    }

    /**
     * Main execution method for Alliance Mobilization task.
     * 
     * <p>
     * <b>Execution Flow:</b>
     * <ol>
     * <li>Initialize OCR helpers</li>
     * <li>Load configuration</li>
     * <li>Navigate to Alliance Mobilization event (with retry)</li>
     * <li>Read attempts counter</li>
     * <li>Validate attempts remaining</li>
     * <li>Search and process tasks based on filters</li>
     * <li>Fallback reschedule if no specific reschedule was set</li>
     * </ol>
     * 
     * <p>
     * <b>Fallback Reschedule Pattern:</b>
     * If no specific reschedule time was determined during task processing
     * (e.g., no tasks found, no cooldowns read), the task will automatically
     * reschedule for 5 minutes as a safety mechanism to keep checking.
     * This ensures the task always reschedules even if all detection fails.
     */
    @Override
    protected void execute() {
        initializeOCRHelpers();
        loadConfiguration();

        logInfo("Starting Alliance Mobilization task execution");

        if (!navigateWithRetry()) {
            handleNavigationFailure();
            return;
        }

        AttemptStatus attemptStatus = readAttemptsCounter();

        if (!validateAttemptsRemaining(attemptStatus)) {
            return;
        }

        boolean rescheduleWasSet = searchAndProcessAllTasks();

        if (!rescheduleWasSet) {
            applyFallbackReschedule();
        }

        logInfo("Alliance Mobilization - Task completed");
    }

    /**
     * Handles navigation failure by rescheduling for next Monday.
     * Event may not be active, so wait for next weekly reset.
     * 
     * <p>
     * Alliance Mobilization is a weekly event that typically starts
     * on Monday. If navigation fails after all retries, we assume the
     * event is not currently active.
     */
    private void handleNavigationFailure() {
        LocalDateTime nextMonday = UtilTime.getNextMondayUtc();
        logInfo("Failed to navigate after " + Limits.MAX_NAVIGATION_ATTEMPTS +
                " attempts. Event may not be active. Retrying on next Monday at "
                + nextMonday.format(DATETIME_FORMATTER) + ".");
        reschedule(nextMonday);
    }

    /**
     * Validates that attempts remain for task processing.
     * 
     * <p>
     * If no attempts remain, reschedules for daily game reset (00:00 UTC).
     * If attempts counter couldn't be read, proceeds with default processing
     * to avoid blocking the task unnecessarily.
     * 
     * @param attemptStatus the attempt status read from screen, or null if OCR
     *                      failed
     * @return true if attempts remain or status unknown, false if no attempts
     *         remain
     */
    private boolean validateAttemptsRemaining(AttemptStatus attemptStatus) {
        if (attemptStatus != null) {
            String totalDisplay = attemptStatus.total() != null && attemptStatus.total() > 0
                    ? attemptStatus.total().toString()
                    : "?";
            logInfo("Detected attempts counter: " + attemptStatus.remaining() + "/" + totalDisplay);

            if (attemptStatus.remaining() <= 0) {
                LocalDateTime nextReset = UtilTime.getGameReset();
                logInfo("No attempts remaining. Rescheduling for next UTC reset at "
                        + nextReset.format(DATETIME_FORMATTER) + ".");
                reschedule(nextReset);
                return false;
            }
        } else {
            logWarning("Unable to detect attempts counter. Proceeding with default processing.");
        }
        return true;
    }

    /**
     * Applies fallback reschedule when no specific reschedule was determined.
     * 
     * <p>
     * This is the safety mechanism that ensures the task always reschedules
     * even if no tasks were found, no cooldowns were read, or no specific
     * timing could be determined. Without this, the task could become "dead"
     * and never execute again.
     */
    private void applyFallbackReschedule() {
        logInfo("No tasks processed. Checking again in " + (Delays.DEFAULT_COOLDOWN_SECONDS / 60) + " minutes.");
        reschedule(LocalDateTime.now().plusSeconds(Delays.DEFAULT_COOLDOWN_SECONDS));
    }

    // ========================================================================
    // NAVIGATION METHODS
    // ========================================================================

    /**
     * Navigates to Alliance Mobilization with retry logic.
     * 
     * <p>
     * Attempts navigation up to MAX_NAVIGATION_ATTEMPTS times.
     * Returns to home screen between attempts to reset UI state.
     * 
     * @return true if navigation successful, false after max retries exhausted
     */
    private boolean navigateWithRetry() {
        for (int attempt = 1; attempt <= Limits.MAX_NAVIGATION_ATTEMPTS; attempt++) {
            logInfo("Navigation attempt " + attempt + "/" + Limits.MAX_NAVIGATION_ATTEMPTS);

            if (navigateToAllianceMobilization()) {
                return true;
            }

            if (attempt < Limits.MAX_NAVIGATION_ATTEMPTS) {
                logWarning("Navigation failed. Returning to home screen and retrying...");
                returnToHomeScreen();
                sleepTask(2000); // Wait for UI to stabilize before retry
            }
        }
        return false;
    }

    /**
     * Returns to home screen for navigation retry.
     * Uses exception handling as home navigation may fail if already home.
     * 
     * <p>
     * This method is intentionally fault-tolerant. If returning to home
     * fails (e.g., already on home screen), we log and continue rather than
     * aborting the entire task.
     */
    private void returnToHomeScreen() {
        try {
            navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.HOME);
        } catch (Exception e) {
            logWarning("Failed to return to home screen: " + e.getMessage());
        }
    }

    /**
     * Navigates to the Alliance Mobilization event screen.
     * 
     * <p>
     * Uses the generic NavigationHelper.navigateToEventMenu() method.
     * 
     * @return true if successfully navigated to event screen, false otherwise
     */
    private boolean navigateToAllianceMobilization() {
        logInfo("Navigating to Alliance Mobilization...");

        boolean success = navigationHelper.navigateToEventMenu(
                cl.camodev.wosbot.serv.task.helper.NavigationHelper.EventMenu.ALLIANCE_MOBILIZATION);

        if (!success) {
            logWarning("Failed to navigate to Alliance Mobilization event");
            return false;
        }

        sleepTask(2000);
        return true;
    }

    // ========================================================================
    // ATTEMPTS COUNTER METHODS
    // ========================================================================

    /**
     * Reads the attempts counter from the screen using OCR.
     * 
     * <p>
     * The counter shows remaining attempts in format "X / Y" or "X/Y".
     * For example: "5 / 10" means 5 remaining out of 10 total.
     * 
     * <p>
     * <b>OCR Corrections Applied:</b>
     * <ul>
     * <li>O/o → 0 (common OCR misread)</li>
     * <li>I/l/| → 1 (common OCR misread)</li>
     * <li>Removes non-numeric characters except /</li>
     * <li>Handles spacing variations</li>
     * </ul>
     * 
     * @return AttemptStatus with remaining and total attempts, or null if OCR fails
     */
    private AttemptStatus readAttemptsCounter() {
        String ocrResult = stringHelper.execute(
                Coords.ATTEMPTS_COUNTER_TOP_LEFT,
                Coords.ATTEMPTS_COUNTER_BOTTOM_RIGHT,
                1,
                300L,
                null,
                s -> !s.isEmpty(),
                s -> s);

        logDebug("Attempts counter OCR result: '" + ocrResult + "'");

        return parseAttemptsFromOCR(ocrResult);
    }

    /**
     * Parses attempts counter from OCR text.
     * 
     * <p>
     * <b>Handles formats:</b>
     * <ul>
     * <li>"5 / 10" or "5/10" → remaining=5, total=10</li>
     * <li>"0 / 10" or "0/10" → remaining=0, total=10</li>
     * <li>"5 /" or "5/" → remaining=5, total=null (unknown total)</li>
     * </ul>
     * 
     * <p>
     * <b>Validation:</b>
     * <ul>
     * <li>Returns null if OCR result is empty or invalid</li>
     * <li>Returns null if total=0 (clearly wrong, retry)</li>
     * <li>Allows null total (meaning unknown but remaining is valid)</li>
     * </ul>
     * 
     * @param ocrResult the raw OCR text from screen
     * @return AttemptStatus or null if parsing fails
     */
    private AttemptStatus parseAttemptsFromOCR(String ocrResult) {
        if (ocrResult == null || ocrResult.trim().isEmpty()) {
            return null;
        }

        String normalized = normalizeOCRText(ocrResult);

        Matcher matcher = OCR.ATTEMPTS_PATTERN.matcher(normalized);
        if (matcher.find()) {
            int remaining = Integer.parseInt(matcher.group(1));
            Integer total = parseTotalAttempts(matcher.group(2));

            // Reject clearly wrong totals (0 is impossible)
            if (total != null && total == 0) {
                logDebug("OCR attempts counter returned total=0, treating as invalid");
                return null;
            }
            return new AttemptStatus(remaining, total);
        }

        return parseZeroAttemptsFormat(normalized);
    }

    /**
     * Normalizes OCR text by fixing common OCR errors.
     * 
     * <p>
     * Common OCR errors with numbers:
     * <ul>
     * <li>Letter O confused with zero</li>
     * <li>Letter I, l, or pipe confused with one</li>
     * <li>Extra spacing or punctuation</li>
     * </ul>
     * 
     * @param text the raw OCR text
     * @return normalized text with common corrections applied
     */
    private String normalizeOCRText(String text) {
        return text
                .replace('O', '0')
                .replace('o', '0')
                .replace('I', '1')
                .replace('l', '1')
                .replace('|', '1')
                .replaceAll("[^0-9/]+", " ")
                .trim();
    }

    /**
     * Parses total attempts from the matched regex group.
     * 
     * @param totalGroup the regex group containing total attempts (may be empty)
     * @return total attempts as Integer, or null if empty
     */
    private Integer parseTotalAttempts(String totalGroup) {
        if (totalGroup != null && !totalGroup.isEmpty()) {
            return Integer.parseInt(totalGroup);
        }
        return null;
    }

    /**
     * Attempts to parse zero attempts format ("0/X").
     * 
     * <p>
     * This handles the special case where remaining is zero,
     * which is important for determining when to reschedule.
     * 
     * @param normalized the normalized OCR text
     * @return AttemptStatus with 0 remaining, or null if format doesn't match
     */
    private AttemptStatus parseZeroAttemptsFormat(String normalized) {
        String condensed = normalized.replaceAll("\\s+", "");
        if (condensed.startsWith("0/")) {
            Integer total = null;
            String afterSlash = condensed.substring(2).replaceAll("[^0-9]", "");
            if (!afterSlash.isEmpty()) {
                total = Integer.parseInt(afterSlash);
            }
            return new AttemptStatus(0, total);
        }
        return null;
    }

    // ========================================================================
    // TASK SEARCH AND PROCESSING - MAIN ORCHESTRATOR
    // ========================================================================

    /**
     * Searches for and processes all available tasks based on configuration.
     * 
     * <p>
     * This is the main orchestrator that coordinates the entire task
     * selection and processing workflow.
     * 
     * <p>
     * <b>Orchestration Flow:</b>
     * <ol>
     * <li>Check and collect completed tasks (rewards)</li>
     * <li>Check and use free mission bonus (+1 attempt)</li>
     * <li>Determine which bonus levels to search based on filter</li>
     * <li>Check if any task is already running (only 1 allowed)</li>
     * <li>Process 200% tasks if applicable</li>
     * <li>Process 120% tasks if applicable</li>
     * <li>Check if no missions found for 3 consecutive runs (reschedule for
     * reset)</li>
     * <li>Check task availability timers if no tasks found</li>
     * <li>Check and use Alliance Monuments (bonus rewards)</li>
     * </ol>
     * 
     * <p>
     * <b>Important Constraint:</b> Only ONE task can run at a time.
     * If a task is running, other tasks will be refreshed but not accepted,
     * and this method will reschedule to check again later.
     * 
     * <p>
     * <b>No Missions Found Handling:</b>
     * If no missions are found on any run, the consecutive counter increments.
     * When this counter reaches 3 runs in a row with no missions, it indicates
     * that both mission slots have been completed the maximum number of times
     * for today, and the task reschedules for game reset (00:00 UTC).
     * 
     * @return true if a specific reschedule time was set, false for fallback
     *         reschedule
     */
    private boolean searchAndProcessAllTasks() {
        logInfo("Searching for tasks (Filter: " + rewardsPercentage +
                ", Min points 200%: " + minimumPoints200 +
                ", Min points 120%: " + minimumPoints120 +
                ", Auto-accept: " + autoAcceptEnabled + ")");

        checkAndCollectCompletedTasks();
        checkAndUseFreeMission();

        TaskSearchFilters filters = determineTaskSearchFilters();
        boolean anyTaskRunning = checkForRunningTasks(filters);

        if (anyTaskRunning) {
            logInfo("A task is already running - only one task can run at a time");
            logInfo("Other tasks will be refreshed to get better options");
        }

        int shortestCooldownSeconds = Integer.MAX_VALUE;
        boolean rescheduleWasSet = false;
        boolean anyMissionFound = false; // True if ANY mission detected (running or available)
        boolean onlyRunningMissions = false; // True if only running missions were found

        if (filters.search200) {
            TaskProcessingResult result = process200PercentTask(
                    filters.accept200,
                    anyTaskRunning,
                    shortestCooldownSeconds);

            shortestCooldownSeconds = result.shortestCooldown;
            if (result.missionFound) {
                anyMissionFound = true;
                onlyRunningMissions = result.onlyRunningMission;
            }
            if (result.shouldStopProcessing) {
                return true; // Specific reschedule already set (e.g., waiting for running task)
            }
        }

        if (filters.search120 && !anyMissionFound) {
            TaskProcessingResult result = process120PercentTasks(
                    filters.accept120,
                    anyTaskRunning,
                    shortestCooldownSeconds);

            shortestCooldownSeconds = result.shortestCooldown;
            if (result.missionFound) {
                anyMissionFound = true;
                onlyRunningMissions = result.onlyRunningMission;
            }
            if (result.shouldStopProcessing) {
                return true; // Specific reschedule already set
            }
        }

        // Check if no missions found for 3 consecutive runs
        // A mission is considered "found" if it exists and is detected, whether running
        // or not
        if (!anyMissionFound) {
            consecutiveNoMissionsCount++;
            logInfo("No missions detected. Consecutive count: " + consecutiveNoMissionsCount + "/3");

            if (consecutiveNoMissionsCount >= 3) {
                logInfo("No missions found for 3 consecutive runs - both mission slots completed for today");
                rescheduleForGameReset();
                return true;
            }
        } else {
            if (consecutiveNoMissionsCount > 0) {
                logDebug("Missions detected again - resetting consecutive no-mission counter");
                consecutiveNoMissionsCount = 0;
            }
        }

        // Check if only running missions found for 3 consecutive runs
        if (onlyRunningMissions) {
            consecutiveOnlyRunningMissionCount++;
            logInfo("Only running mission(s) found. Consecutive count: " + consecutiveOnlyRunningMissionCount + "/3");

            if (consecutiveOnlyRunningMissionCount >= 3) {
                logInfo("Only running missions found for 3 consecutive runs - rescheduling for 30 minutes");
                reschedule(LocalDateTime.now().plusSeconds(Delays.LONG_COOLDOWN_SECONDS));
                return true;
            }
        } else {
            if (consecutiveOnlyRunningMissionCount > 0) {
                logDebug("Available mission(s) found again - resetting only-running-mission counter");
                consecutiveOnlyRunningMissionCount = 0;
            }
        }

        if (shortestCooldownSeconds < Integer.MAX_VALUE) {
            rescheduleForShortestCooldown(shortestCooldownSeconds);
            rescheduleWasSet = true;
        }

        if (!rescheduleWasSet) {
            rescheduleWasSet = checkTaskAvailabilityTimersAndReschedule();
        }

        checkAndUseAllianceMonuments();

        return rescheduleWasSet;
    }

    /**
     * Determines which tasks to search and accept based on rewards percentage
     * filter.
     * 
     * <p>
     * <b>Filter Logic:</b>
     * <ul>
     * <li>"200%" → Accept 200%, search and refresh 120%</li>
     * <li>"120%" → Accept 120%, search and refresh 200%</li>
     * <li>"Both" → Accept both 200% and 120%</li>
     * <li>"Any" → Accept whichever is found first</li>
     * </ul>
     * 
     * <p>
     * <b>Why search non-accepted types?</b>
     * Even if we won't accept a certain bonus level, we still search for it
     * so we can refresh those tasks to cycle toward better options.
     * 
     * @return TaskSearchFilters indicating which tasks to search and which to
     *         accept
     */
    private TaskSearchFilters determineTaskSearchFilters() {
        boolean accept200 = rewardsPercentage.equals("200%") ||
                rewardsPercentage.equals("Both") ||
                rewardsPercentage.equals("Any");

        boolean accept120 = rewardsPercentage.equals("120%") ||
                rewardsPercentage.equals("Both") ||
                rewardsPercentage.equals("Any");

        // Always search both levels to enable refreshing
        boolean search200 = accept200 || rewardsPercentage.equals("120%");
        boolean search120 = accept120 || rewardsPercentage.equals("200%");

        return new TaskSearchFilters(search200, search120, accept200, accept120);
    }

    /**
     * Checks if any task is currently running.
     * 
     * <p>
     * Searches for timer bars below bonus indicators to detect running tasks.
     * The game only allows one mobilization task to run at a time.
     * 
     * <p>
     * This check is performed early to avoid unnecessary processing
     * if we already know we can't accept any tasks.
     * 
     * @param filters which tasks to check
     * @return true if any task is running, false if all slots are available
     */
    private boolean checkForRunningTasks(TaskSearchFilters filters) {
        if (filters.search200) {
            DTOImageSearchResult result200 = templateSearchHelper.searchTemplate(
                    EnumTemplates.AM_200_PERCENT,
                    SearchConfig.builder()
                            .withThreshold(Thresholds.BONUS)
                            .withMaxAttempts(1)
                            .build());

            if (result200.isFound() && isTaskAlreadyRunning(result200.getPoint())) {
                logInfo("Task at 200% is already running");
                return true;
            }
        }

        if (filters.search120) {
            List<DTOImageSearchResult> results120 = templateSearchHelper.searchTemplates(
                    EnumTemplates.AM_120_PERCENT,
                    SearchConfig.builder()
                            .withThreshold(Thresholds.BONUS)
                            .withMaxAttempts(3)
                            .withMaxResults(Limits.MAX_TEMPLATE_SEARCH_RESULTS_120)
                            .build());

            if (results120 != null && !results120.isEmpty()) {
                for (DTOImageSearchResult result120 : results120) {
                    if (isTaskAlreadyRunning(result120.getPoint())) {
                        logInfo("Task at 120% (" + result120.getPoint() + ") is already running");
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Reschedules the task for the next game reset (00:00 UTC).
     * 
     * <p>
     * Called when no missions are found for 3 consecutive runs in a row,
     * indicating that both mission slots have completed the maximum number
     * of times for today. The task will resume at the next game reset.
     */
    private void rescheduleForGameReset() {
        LocalDateTime nextReset = UtilTime.getGameReset();
        logInfo("Rescheduling for game reset at " + nextReset.format(DATETIME_FORMATTER) + " (next day 00:00 UTC)");
        reschedule(nextReset);
    }

    /**
     * Reschedules based on the shortest cooldown found from refreshed tasks.
     * 
     * <p>
     * When tasks are refreshed, the game shows a cooldown timer indicating
     * when they can be refreshed again. We reschedule for the shortest cooldown
     * plus a small buffer to ensure the cooldown has expired.
     * 
     * @param shortestCooldownSeconds the shortest cooldown in seconds from all
     *                                refreshed tasks
     */
    private void rescheduleForShortestCooldown(int shortestCooldownSeconds) {
        LocalDateTime nextRun = LocalDateTime.now()
                .plusSeconds(shortestCooldownSeconds + Delays.RESCHEDULE_BUFFER_SECONDS);

        reschedule(nextRun);
        logInfo("Rescheduling based on shortest cooldown: " + shortestCooldownSeconds + " seconds -> "
                + nextRun.format(DATETIME_FORMATTER));
    }

    /**
     * Checks task availability timers and reschedules if found.
     * 
     * <p>
     * When no tasks are available, the game shows timers indicating
     * when new tasks will appear. This allows us to reschedule precisely
     * instead of using the default 5-minute fallback.
     * 
     * @return true if timer found and rescheduled, false if no timers detected
     */
    private boolean checkTaskAvailabilityTimersAndReschedule() {
        int timerSeconds = readTaskAvailabilityTimers();

        if (timerSeconds > 0) {
            LocalDateTime nextRun = LocalDateTime.now().plusSeconds(timerSeconds + 10); // +10s buffer
            reschedule(nextRun);
            logInfo("Next check in " + (timerSeconds / 60) + "min (task availability timer)");
            return true;
        }

        return false;
    }

    // ========================================================================
    // TASK PROCESSING - 200% TASKS
    // ========================================================================

    /**
     * Processes 200% bonus task if found.
     * 
     * <p>
     * Only one 200% task can exist at a time. This is the highest
     * bonus tier and is generally preferred if points meet minimum.
     * 
     * @param shouldAccept            whether to accept this task type based on
     *                                filter
     * @param anyTaskRunning          whether another task is already running
     * @param currentShortestCooldown current shortest cooldown tracker
     * @return TaskProcessingResult with updated cooldown and stop flag
     */
    private TaskProcessingResult process200PercentTask(
            boolean shouldAccept,
            boolean anyTaskRunning,
            int currentShortestCooldown) {

        logDebug("Searching for 200% bonus task...");

        DTOImageSearchResult result200 = templateSearchHelper.searchTemplate(
                EnumTemplates.AM_200_PERCENT,
                SearchConfig.builder()
                        .withThreshold(Thresholds.BONUS)
                        .withMaxAttempts(1)
                        .build());

        if (!result200.isFound()) {
            logDebug("No 200% bonus task found");
            return new TaskProcessingResult(false, currentShortestCooldown, false, false);
        }

        if (isTaskAlreadyRunning(result200.getPoint())) {
            logInfo("Task at 200% is already running - skipping this one");
            return new TaskProcessingResult(false, currentShortestCooldown, true, true); // Mission found but running
                                                                                         // (only running mission)
        }

        return processIndividualTask(
                result200.getPoint(),
                shouldAccept,
                anyTaskRunning,
                minimumPoints200,
                currentShortestCooldown,
                "200%");
    }

    // ========================================================================
    // TASK PROCESSING - 120% TASKS
    // ========================================================================

    /**
     * Processes up to 2 available 120% bonus tasks.
     * 
     * <p>
     * The game can show up to 2 tasks with 120% bonus simultaneously.
     * We process each one independently based on points and task type.
     * 
     * @param shouldAccept            whether to accept this task type based on
     *                                filter
     * @param anyTaskRunning          whether another task is already running
     * @param currentShortestCooldown current shortest cooldown tracker
     * @return TaskProcessingResult with updated cooldown and stop flag
     */
    private TaskProcessingResult process120PercentTasks(
            boolean shouldAccept,
            boolean anyTaskRunning,
            int currentShortestCooldown) {

        logDebug("Searching for 120% bonus tasks (max 2 positions)...");

        List<DTOImageSearchResult> results120 = templateSearchHelper.searchTemplates(
                EnumTemplates.AM_120_PERCENT,
                SearchConfig.builder()
                        .withThreshold(Thresholds.BONUS)
                        .withMaxAttempts(3)
                        .withMaxResults(Limits.MAX_TEMPLATE_SEARCH_RESULTS_120)
                        .build());

        if (results120 == null || results120.isEmpty()) {
            logDebug("No 120% bonus tasks found");
            return new TaskProcessingResult(false, currentShortestCooldown, false, false);
        }

        logInfo("Found " + results120.size() + " x 120% bonus task(s)");

        int shortestCooldown = currentShortestCooldown;
        boolean anyMissionFound = false;
        boolean anyAvailableMissionFound = false;

        for (DTOImageSearchResult result120 : results120) {
            if (isTaskAlreadyRunning(result120.getPoint())) {
                logInfo("Task at 120% (" + result120.getPoint() + ") is already running - skipping this one");
                anyMissionFound = true; // Mission was detected even if running
                continue; // Check next 120% task
            }

            anyAvailableMissionFound = true; // At least one available mission found

            TaskProcessingResult result = processIndividualTask(
                    result120.getPoint(),
                    shouldAccept,
                    anyTaskRunning,
                    minimumPoints120,
                    shortestCooldown,
                    "120%");

            shortestCooldown = result.shortestCooldown;
            anyMissionFound = anyMissionFound || result.missionFound;

            if (result.shouldStopProcessing) {
                return new TaskProcessingResult(result.shouldStopProcessing, result.shortestCooldown, true, false); // Mission
                                                                                                                    // was
                                                                                                                    // found,
                                                                                                                    // not
                                                                                                                    // only
                                                                                                                    // running
            }
        }

        // onlyRunningMission = true only if we found missions but all are running (no
        // available ones)
        boolean onlyRunningMission = anyMissionFound && !anyAvailableMissionFound;
        return new TaskProcessingResult(false, shortestCooldown, anyMissionFound, onlyRunningMission);
    }

    // ========================================================================
    // TASK PROCESSING - INDIVIDUAL TASK
    // ========================================================================

    /**
     * Processes a single task: reads points, detects type, makes decision.
     * 
     * <p>
     * <b>Processing Flow:</b>
     * <ol>
     * <li>Read points via OCR from overview screen</li>
     * <li>Skip if OCR fails (continue to next task)</li>
     * <li>Detect task type via image recognition</li>
     * <li>Skip if detection fails</li>
     * <li>Check if task type is enabled in config</li>
     * <li>Make decision: Accept, Refresh, or Wait</li>
     * </ol>
     * 
     * @param bonusLocation           location of the bonus indicator on screen
     * @param shouldAcceptBonusLevel  whether this bonus level should be accepted
     *                                (from filter)
     * @param anyTaskRunning          whether another task is running
     * @param minimumPoints           minimum points threshold for this bonus level
     * @param currentShortestCooldown current shortest cooldown tracker
     * @param bonusPercentage         bonus percentage string for logging (e.g.,
     *                                "200%")
     * @return TaskProcessingResult with updated cooldown and stop flag
     */
    private TaskProcessingResult processIndividualTask(
            DTOPoint bonusLocation,
            boolean shouldAcceptBonusLevel,
            boolean anyTaskRunning,
            int minimumPoints,
            int currentShortestCooldown,
            String bonusPercentage) {

        int detectedPoints = readPointsNearBonus(bonusLocation);

        if (detectedPoints < 0) {
            logWarning("Could not read points for " + bonusPercentage + " task - skipping");
            return new TaskProcessingResult(false, currentShortestCooldown, true, false); // Mission was found, just OCR
                                                                                          // failed
        }

        EnumTemplates taskType = detectTaskTypeNearBonus(bonusLocation);

        if (taskType == null) {
            logInfo("Task type not detected at " + bonusLocation);
            return new TaskProcessingResult(false, currentShortestCooldown, true, false); // Mission was found, just
                                                                                          // type detection failed
        }

        logInfo("Task type detected: " + taskType.name());
        boolean isEnabled = isTaskTypeEnabled(taskType);

        return makeTaskDecision(
                bonusLocation,
                taskType,
                isEnabled,
                detectedPoints,
                minimumPoints,
                shouldAcceptBonusLevel,
                anyTaskRunning,
                currentShortestCooldown,
                bonusPercentage);
    }

    // ========================================================================
    // TASK DECISION LOGIC
    // ========================================================================

    /**
     * Makes the final decision on what to do with a task based on all criteria.
     * 
     * <p>
     * <b>Decision Matrix:</b>
     * <table border="1">
     * <tr>
     * <th>Condition</th>
     * <th>Action</th>
     * <th>Reason</th>
     * </tr>
     * <tr>
     * <td>Wrong bonus level</td>
     * <td>Refresh</td>
     * <td>Filter mismatch (e.g., want 200% but got 120%)</td>
     * </tr>
     * <tr>
     * <td>Task type disabled</td>
     * <td>Refresh</td>
     * <td>User doesn't want this task type</td>
     * </tr>
     * <tr>
     * <td>Points below minimum</td>
     * <td>Refresh</td>
     * <td>Rewards too low</td>
     * </tr>
     * <tr>
     * <td>Good task + another running</td>
     * <td>Wait 1 hour</td>
     * <td>Can't accept (only 1 task at a time)</td>
     * </tr>
     * <tr>
     * <td>Good task + auto-accept on</td>
     * <td>Accept</td>
     * <td>All criteria met, auto-accept enabled</td>
     * </tr>
     * <tr>
     * <td>Good task + auto-accept off</td>
     * <td>Skip</td>
     * <td>Wait for manual acceptance</td>
     * </tr>
     * </table>
     * 
     * <p>
     * <b>Important:</b> When auto-accept is disabled and a good task is found,
     * the task does NOT reschedule specifically for this. It falls through to
     * normal rescheduling logic (availability timers or 5-minute fallback).
     * This allows the user time to manually accept the task.
     * 
     * @return TaskProcessingResult indicating whether to stop processing and any
     *         cooldown
     */
    private TaskProcessingResult makeTaskDecision(
            DTOPoint bonusLocation,
            EnumTemplates taskType,
            boolean isTaskTypeEnabled,
            int detectedPoints,
            int minimumPoints,
            boolean shouldAcceptBonusLevel,
            boolean anyTaskRunning,
            int currentShortestCooldown,
            String bonusPercentage) {

        logInfo("Found: " + taskType.name() + " (" + detectedPoints + "pts, " + bonusPercentage +
                ", enabled: " + isTaskTypeEnabled + ")");

        // Decision 1: Wrong bonus level (filter mismatch)
        if (!shouldAcceptBonusLevel) {
            logInfo("Refreshing (bonus level " + bonusPercentage + " not selected in filter)");
            int cooldown = clickAndRefreshTask(bonusLocation);
            int newShortest = Math.min(cooldown, currentShortestCooldown);
            return new TaskProcessingResult(false, newShortest, true, false); // Mission found and refreshed
        }

        // Decision 2: Task type disabled
        if (!isTaskTypeEnabled) {
            logInfo("Refreshing (mission disabled)");
            int cooldown = clickAndRefreshTask(bonusLocation);
            int newShortest = Math.min(cooldown, currentShortestCooldown);
            return new TaskProcessingResult(false, newShortest, true, false); // Mission found and refreshed
        }

        // Decision 3: Points below minimum
        if (detectedPoints < minimumPoints) {
            logInfo("Refreshing (low points: " + detectedPoints + " < " + minimumPoints + ")");
            int cooldown = clickAndRefreshTask(bonusLocation);
            int newShortest = Math.min(cooldown, currentShortestCooldown);
            return new TaskProcessingResult(false, newShortest, true, false); // Mission found and refreshed
        }

        // Decision 4: Good task but another task is running
        if (anyTaskRunning) {
            logInfo("Waiting 1h (task good but another task running)");
            LocalDateTime nextRun = LocalDateTime.now().plusHours(Delays.RESCHEDULE_WAIT_HOURS);
            reschedule(nextRun);
            return new TaskProcessingResult(true, 0, true, false); // Stop processing, reschedule already set, mission
                                                                   // found
        }

        // Decision 5: Good task and auto-accept enabled
        if (autoAcceptEnabled) {
            logInfo("Accepting task");
            clickAndAcceptTask(bonusLocation);
            return new TaskProcessingResult(false, currentShortestCooldown, true, false); // Mission found and accepted
        }

        // Decision 6: Good task but auto-accept disabled (user will accept manually)
        logInfo("Skipping (auto-accept disabled - user will accept manually)");
        return new TaskProcessingResult(false, currentShortestCooldown, true, false); // Mission found but awaiting
                                                                                      // manual acceptance
    }

    // ========================================================================
    // TASK ACTIONS - ACCEPT AND REFRESH
    // ========================================================================

    /**
     * Clicks a task and refreshes it to get a new random task.
     * 
     * <p>
     * <b>Refresh Flow:</b>
     * <ol>
     * <li>Click task to open details screen</li>
     * <li>Click refresh button</li>
     * <li>Confirm refresh in popup</li>
     * <li>Read cooldown from the mission area after popup closes</li>
     * <li>Return cooldown for rescheduling</li>
     * </ol>
     * 
     * <p>
     * The cooldown is read from the appropriate mission area (left or right)
     * based on the bonus location x-coordinate. The left mission uses coordinates
     * (75, 712) to (299, 798), and the right mission uses (410, 712) to (627, 798).
     * 
     * @param bonusLocation location of the task's bonus indicator
     * @return cooldown in seconds until task can be refreshed again
     */
    private int clickAndRefreshTask(DTOPoint bonusLocation) {
        tapPoint(bonusLocation);
        sleepTask(2000); // Wait for task details screen to load

        tapPoint(Coords.REFRESH_BUTTON);
        sleepTask(1500); // Wait for cooldown confirmation popup

        tapPoint(Coords.REFRESH_CONFIRM_BUTTON);
        sleepTask(1500); // Wait for refresh to complete and return to overview

        int cooldownSeconds = readRefreshCooldownFromMission(bonusLocation);

        logInfo("Cooldown: " + cooldownSeconds + "s");
        return cooldownSeconds;
    }

    /**
     * Clicks a task and accepts it to start the task.
     * 
     * <p>
     * <b>Accept Flow:</b>
     * <ol>
     * <li>Click task to open details screen</li>
     * <li>Click accept button</li>
     * <li>Task starts running (timer bar appears)</li>
     * </ol>
     * 
     * <p>
     * After acceptance, the task will show a timer bar and count toward
     * the daily attempts counter. Only one task can run at a time.
     * 
     * @param bonusLocation location of the task's bonus indicator
     */
    private void clickAndAcceptTask(DTOPoint bonusLocation) {
        tapPoint(bonusLocation);
        sleepTask(2000); // Wait for task details screen to load

        tapPoint(Coords.ACCEPT_BUTTON);
        sleepTask(1500); // Wait for acceptance to complete
    }

    // ========================================================================
    // TASK DETECTION - POINTS, TYPE, RUNNING STATUS
    // ========================================================================

    /**
     * Reads points value near a bonus indicator using OCR.
     * 
     * <p>
     * Points are displayed as a number (e.g., "150") in a fixed offset
     * from the bonus indicator. The points value determines the rewards
     * and is a key criterion for task acceptance.
     * 
     * <p>
     * OCR area is calculated relative to the bonus location using
     * predefined offsets for consistent detection.
     * 
     * @param bonusLocation location of the bonus indicator on screen
     * @return points value as integer, or -1 if OCR fails
     */
    private int readPointsNearBonus(DTOPoint bonusLocation) {
        logDebug("Reading points near bonus location: " + bonusLocation);

        DTOPoint topLeft = new DTOPoint(
                bonusLocation.getX() + Offsets.POINTS_X,
                bonusLocation.getY() + Offsets.POINTS_Y);

        DTOPoint bottomRight = new DTOPoint(
                topLeft.getX() + Offsets.POINTS_WIDTH,
                topLeft.getY() + Offsets.POINTS_HEIGHT);

        logDebug("OCR area for points: " + topLeft + " to " + bottomRight);

        String ocrResult = stringHelper.execute(
                topLeft,
                bottomRight,
                1,
                300L,
                null,
                s -> !s.isEmpty(),
                s -> s);

        if (ocrResult != null && !ocrResult.trim().isEmpty()) {
            String numericValue = ocrResult.replaceAll("[^0-9]", "");

            if (!numericValue.isEmpty()) {
                int points = Integer.parseInt(numericValue);
                logInfo("Detected points on overview: " + points);
                return points;
            }
        }

        logWarning("Could not read points near bonus after OCR attempts");
        return -1;
    }

    /**
     * Detects task type near a bonus indicator via image recognition.
     * 
     * <p>
     * Searches for all 14 task type icons and finds the one closest
     * to the bonus indicator (within proximity thresholds). Task type
     * icons are displayed to the left of the bonus indicator.
     * 
     * <p>
     * <b>Task Types (14 total):</b>
     * <ul>
     * <li>Build Speedups - Complete building upgrades</li>
     * <li>Buy Package - Purchase items from shop</li>
     * <li>Chief Gear Charm - Upgrade chief gear charms</li>
     * <li>Chief Gear Score - Improve chief gear score</li>
     * <li>Defeat Beasts - Kill wild beasts</li>
     * <li>Fire Crystal - Collect/use fire crystals</li>
     * <li>Gather Resources - Gather from wilderness</li>
     * <li>Hero Gear Stone - Upgrade hero gear</li>
     * <li>Mythic Shard - Collect mythic shards</li>
     * <li>Rally - Participate in rallies</li>
     * <li>Train Troops - Train new troops</li>
     * <li>Training Speedups - Use training speedups</li>
     * <li>Use Gems - Spend gems</li>
     * <li>Use Speedups - Use speedup items</li>
     * </ul>
     * 
     * <p>
     * <b>Detection Strategy:</b>
     * For each task type template, search for up to 5 matches on screen.
     * For each match, calculate distance from bonus indicator. The match
     * within proximity thresholds is the task type.
     * 
     * @param bonusLocation location of the bonus indicator on screen
     * @return EnumTemplates for the detected task type, or null if not found
     */
    private EnumTemplates detectTaskTypeNearBonus(DTOPoint bonusLocation) {
        logDebug("Detecting task type near bonus location: " + bonusLocation);

        EnumTemplates[] taskTypeTemplates = {
                EnumTemplates.AM_BUILD_SPEEDUPS,
                EnumTemplates.AM_BUY_PACKAGE,
                EnumTemplates.AM_CHIEF_GEAR_CHARM,
                EnumTemplates.AM_CHIEF_GEAR_SCORE,
                EnumTemplates.AM_DEFEAT_BEASTS,
                EnumTemplates.AM_FIRE_CRYSTAL,
                EnumTemplates.AM_GATHER_RESOURCES,
                EnumTemplates.AM_HERO_GEAR_STONE,
                EnumTemplates.AM_MYTHIC_SHARD,
                EnumTemplates.AM_RALLY,
                EnumTemplates.AM_TRAIN_TROOPS,
                EnumTemplates.AM_TRAINING_SPEEDUPS,
                EnumTemplates.AM_USE_GEMS,
                EnumTemplates.AM_USE_SPEEDUPS
        };

        for (EnumTemplates template : taskTypeTemplates) {
            List<DTOImageSearchResult> results = templateSearchHelper.searchTemplates(
                    template,
                    SearchConfig.builder()
                            .withThreshold(Thresholds.TASK_TYPE)
                            .withMaxAttempts(3)
                            .withMaxResults(Limits.MAX_TEMPLATE_SEARCH_RESULTS_TASK_TYPE)
                            .build());

            if (results != null && !results.isEmpty()) {
                for (DTOImageSearchResult result : results) {
                    int deltaX = Math.abs(result.getPoint().getX() - bonusLocation.getX());
                    int deltaY = Math.abs(result.getPoint().getY() - bonusLocation.getY());

                    logDebug(template.name() + " found at (" + result.getPoint().getX() + "," +
                            result.getPoint().getY() + ") - Distance from bonus: deltaX=" + deltaX + "px, deltaY="
                            + deltaY + "px");

                    // Check if this match is close enough to the bonus indicator
                    if (deltaX < Offsets.TASK_TYPE_MAX_DELTA_X && deltaY < Offsets.TASK_TYPE_MAX_DELTA_Y) {
                        logInfo("Detected task type: " + template.name() + " at " + result.getPoint() +
                                " (deltaX=" + deltaX + "px, deltaY=" + deltaY + "px)");
                        return template;
                    } else {
                        logDebug("Too far from bonus (max: deltaX=" + Offsets.TASK_TYPE_MAX_DELTA_X +
                                "px, deltaY=" + Offsets.TASK_TYPE_MAX_DELTA_Y + "px)");
                    }
                }
            }
        }

        logWarning("No task type detected near bonus location");
        return null;
    }

    /**
     * Checks if a task is already running by searching for its timer bar.
     * 
     * <p>
     * Running tasks display a progress timer bar below the bonus indicator.
     * This timer shows how much time remains until the task completes.
     * 
     * <p>
     * Detection is performed by searching for the AM_BAR_X template
     * (which matches only the fixed frame parts of the timer, not the
     * variable progress bar itself).
     * 
     * @param bonusLocation location of the bonus indicator on screen
     * @return true if timer bar found (task is running), false otherwise (task
     *         available)
     */
    private boolean isTaskAlreadyRunning(DTOPoint bonusLocation) {
        logDebug("Checking if task is already running near: " + bonusLocation);

        DTOPoint searchTopLeft = new DTOPoint(
                bonusLocation.getX() + Offsets.RUNNING_TASK_X,
                bonusLocation.getY() + Offsets.RUNNING_TASK_Y);

        DTOPoint searchBottomRight = new DTOPoint(
                searchTopLeft.getX() + Offsets.RUNNING_TASK_WIDTH,
                searchTopLeft.getY() + Offsets.RUNNING_TASK_HEIGHT);

        DTOImageSearchResult barResult = templateSearchHelper.searchTemplate(
                EnumTemplates.AM_BAR_X,
                SearchConfig.builder()
                        .withThreshold(Thresholds.RUNNING)
                        .withMaxAttempts(1)
                        .withDelay(100L)
                        .withArea(new DTOArea(searchTopLeft, searchBottomRight))
                        .build());

        if (barResult.isFound()) {
            logInfo("Timer bar detected at " + barResult.getPoint() + " - task is already running");
            return true;
        }

        logDebug("No timer bar detected - task is available");
        return false;
    }

    /**
     * Checks if a task type is enabled based on configuration.
     * 
     * <p>
     * Maps the task type template to its corresponding configuration flag.
     * Each of the 14 task types has its own enable/disable flag in the profile.
     * 
     * @param taskType the task type to check
     * @return true if task type is enabled, false if disabled or unknown
     */
    private boolean isTaskTypeEnabled(EnumTemplates taskType) {
        logDebug("Checking if task type is enabled: " + taskType.name());

        switch (taskType) {
            case AM_BUILD_SPEEDUPS:
                return buildSpeedupsEnabled;
            case AM_BUY_PACKAGE:
                return buyPackageEnabled;
            case AM_CHIEF_GEAR_CHARM:
                return chiefGearCharmEnabled;
            case AM_CHIEF_GEAR_SCORE:
                return chiefGearScoreEnabled;
            case AM_DEFEAT_BEASTS:
                return defeatBeastsEnabled;
            case AM_FIRE_CRYSTAL:
                return fireCrystalEnabled;
            case AM_GATHER_RESOURCES:
                return gatherResourcesEnabled;
            case AM_HERO_GEAR_STONE:
                return heroGearStoneEnabled;
            case AM_MYTHIC_SHARD:
                return mythicShardEnabled;
            case AM_RALLY:
                return rallyEnabled;
            case AM_TRAIN_TROOPS:
                return trainTroopsEnabled;
            case AM_TRAINING_SPEEDUPS:
                return trainingSpeedupsEnabled;
            case AM_USE_GEMS:
                return useGemsEnabled;
            case AM_USE_SPEEDUPS:
                return useSpeedupsEnabled;
            default:
                logWarning("Unknown task type: " + taskType.name());
                return false;
        }
    }

    // ========================================================================
    // OCR AND TIMER READING METHODS
    // ========================================================================

    /**
     * Reads refresh cooldown from the mission area after popup is closed.
     * 
     * <p>
     * After confirming a refresh, the cooldown timer appears in the mission area.
     * This method determines which mission area (left or right) based on the
     * bonus location x-coordinate and reads the cooldown from that area.
     * 
     * <p>
     * <b>Mission Area Division:</b>
     * <ul>
     * <li>Left mission: x < 360, coordinates (75, 712) to (299, 798)</li>
     * <li>Right mission: x >= 360, coordinates (410, 712) to (627, 798)</li>
     * </ul>
     * 
     * <p>
     * Cooldown format can be:
     * /**
     * Reads refresh cooldown from the mission area after popup is closed.
     * 
     * <p>
     * After confirming a refresh, the cooldown timer appears in the mission area.
     * This method determines which mission area (left or right) based on the
     * bonus location x-coordinate and reads the cooldown from that area.
     * 
     * <p>
     * <b>Mission Area Division:</b>
     * <ul>
     * <li>Left mission: x < 360, coordinates (75, 712) to (299, 798)</li>
     * <li>Right mission: x >= 360, coordinates (410, 712) to (627, 798)</li>
     * </ul>
     * 
     * <p>
     * Cooldown format can be:
     * <ul>
     * <li>MM:SS (e.g., "05:30")</li>
     * <li>Xd HH:mm:ss (e.g., "1d 05:30:00")</li>
     * </ul>
     * 
     * @param bonusLocation location of the bonus indicator to determine left/right
     *                      mission
     * @return cooldown in seconds, or DEFAULT_COOLDOWN_SECONDS if OCR fails
     */
    private int readRefreshCooldownFromMission(DTOPoint bonusLocation) {
        // Determine which mission area (left or right) based on bonus location
        DTOPoint topLeft;
        DTOPoint bottomRight;
        String missionSide;

        if (bonusLocation.getX() < 360) {
            // Left mission
            topLeft = new DTOPoint(75, 712);
            bottomRight = new DTOPoint(299, 798);
            missionSide = "Left";
        } else {
            // Right mission
            topLeft = new DTOPoint(410, 712);
            bottomRight = new DTOPoint(627, 798);
            missionSide = "Right";
        }

        logDebug("Reading refresh cooldown from " + missionSide + " mission area: " + topLeft + " to " + bottomRight);
        sleepTask(500); // Wait for popup to close and cooldown to appear

        DTOTesseractSettings timeSettings = DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(158, 14, 14))
                .setAllowedChars("0123456789d:")
                .build();

        Duration cooldownTime = durationHelper.execute(
                topLeft,
                bottomRight,
                3, // 3 retries
                200L, // 200ms delay between retries
                timeSettings,
                TimeValidators::isValidTime,
                TimeConverters::toDuration);

        if (cooldownTime == null) {
            logWarning("Failed to read cooldown time from " + missionSide + " mission area");
            return Delays.DEFAULT_COOLDOWN_SECONDS;
        }

        int totalSeconds = (int) cooldownTime.getSeconds();
        if (totalSeconds > 0) {
            logInfo("Cooldown from " + missionSide + " mission: " + totalSeconds + " seconds");
            return totalSeconds;
        }

        logWarning("Could not read cooldown from " + missionSide + " mission area, using default 5 minutes");
        return Delays.DEFAULT_COOLDOWN_SECONDS;
    }

    /**
     * Reads task availability timers from the screen.
     * 
     * <p>
     * When no tasks are available, the game shows timers on empty task slots
     * indicating when new tasks will appear. There can be up to 2 timers
     * (left and right positions).
     * 
     * <p>
     * This method reads both timers and returns the shortest one for
     * optimal rescheduling.
     * 
     * @return shortest timer in seconds, or 0 if no timers found
     */
    private int readTaskAvailabilityTimers() {
        logDebug("Reading task availability timers from empty task slots...");

        int leftTimerSeconds = readTimerFromRegion(
                Coords.LEFT_TIMER_TOP_LEFT,
                Coords.LEFT_TIMER_BOTTOM_RIGHT,
                "Left");

        int rightTimerSeconds = readTimerFromRegion(
                Coords.RIGHT_TIMER_TOP_LEFT,
                Coords.RIGHT_TIMER_BOTTOM_RIGHT,
                "Right");

        if (leftTimerSeconds > 0 && rightTimerSeconds > 0) {
            int shortestTimer = Math.min(leftTimerSeconds, rightTimerSeconds);
            logInfo("Found task availability timers - using shortest: " + shortestTimer + " seconds");
            return shortestTimer;
        }

        if (leftTimerSeconds > 0) {
            logInfo("Found left timer: " + leftTimerSeconds + " seconds");
            return leftTimerSeconds;
        }

        if (rightTimerSeconds > 0) {
            logInfo("Found right timer: " + rightTimerSeconds + " seconds");
            return rightTimerSeconds;
        }

        logDebug("No task availability timers found");
        return 0;
    }

    /**
     * Reads a single timer from a specific region using OCR.
     * 
     * <p>
     * Timer format can be:
     * <ul>
     * <li>HH:mm:ss (e.g., "01:30:00")</li>
     * <li>Xd HH:mm:ss (e.g., "1d 05:30:00")</li>
     * </ul>
     * 
     * <p>
     * Uses the durationHelper with robust time parsing to handle
     * various OCR inaccuracies.
     * 
     * @param topLeft     top-left corner of timer region
     * @param bottomRight bottom-right corner of timer region
     * @param timerName   descriptive name for logging (e.g., "Left", "Right")
     * @return timer value in seconds, or 0 if OCR fails
     */
    private int readTimerFromRegion(DTOPoint topLeft, DTOPoint bottomRight, String timerName) {
        DTOTesseractSettings timeSettings = DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setAllowedChars("0123456789d:")
                .build();

        Duration cooldownTime = durationHelper.execute(
                topLeft,
                bottomRight,
                3, // 3 retries
                200L, // 200ms delay between retries
                timeSettings,
                TimeValidators::isValidTime,
                TimeConverters::toDuration);

        if (cooldownTime == null) {
            logDebug("Failed to read " + timerName + " timer from screen");
            return 0;
        }

        int totalSeconds = (int) cooldownTime.getSeconds();
        if (totalSeconds > 0) {
            logInfo("Cooldown from " + timerName + " timer: " + totalSeconds + " seconds");
            return totalSeconds;
        }

        return 0;
    }

    // ========================================================================
    // COMPLETED TASKS
    // ========================================================================

    /**
     * Checks for and collects rewards from completed tasks.
     * 
     * <p>
     * When a task completes, a "Completed" indicator appears on the task.
     * Clicking it collects the rewards and clears the task slot.
     * 
     * <p>
     * This is checked at the start of task processing to ensure we
     * collect rewards and free up slots before searching for new tasks.
     */
    private void checkAndCollectCompletedTasks() {
        logDebug("Checking for completed tasks to collect rewards...");

        DTOImageSearchResult completedResult = templateSearchHelper.searchTemplate(
                EnumTemplates.AM_COMPLETED,
                SearchConfig.builder()
                        .withThreshold(Thresholds.COMPLETED)
                        .withMaxAttempts(1)
                        .build());

        if (completedResult.isFound()) {
            logInfo("Completed task found at " + completedResult.getPoint() + " - collecting rewards");
            tapPoint(completedResult.getPoint());
            sleepTask(1500); // Wait for reward collection animation
            logInfo("Rewards collected from completed task");
        } else {
            logDebug("No completed tasks found");
        }
    }

    // ========================================================================
    // SPECIAL FEATURES (FREE MISSION & MONUMENTS)
    // ========================================================================

    /**
     * Checks for and uses the free mission bonus if available.
     * 
     * <p>
     * The free mission button grants +1 extra attempt without consuming
     * the daily attempt counter. It appears near the expected position
     * but we verify proximity before clicking.
     * 
     * <p>
     * This is a valuable bonus that should be used whenever available.
     */
    private void checkAndUseFreeMission() {
        logDebug("Checking for free mission button...");

        DTOImageSearchResult freeMissionResult = templateSearchHelper.searchTemplate(
                EnumTemplates.AM_PLUS_1_FREE_MISSION,
                SearchConfig.builder()
                        .withThreshold(Thresholds.FREE_MISSION)
                        .withMaxAttempts(1)
                        .build());

        if (!freeMissionResult.isFound()) {
            logDebug("No free mission button found");
            return;
        }

        DTOPoint location = freeMissionResult.getPoint();
        int deltaX = Math.abs(location.getX() - Coords.FREE_MISSION_EXPECTED.getX());
        int deltaY = Math.abs(location.getY() - Coords.FREE_MISSION_EXPECTED.getY());

        if (deltaX <= Offsets.FREE_MISSION_TOLERANCE && deltaY <= Offsets.FREE_MISSION_TOLERANCE) {
            logInfo("Free mission button found at " + location + " (near expected position) - using it");
            tapPoint(location);
            sleepTask(1500); // Wait for confirmation popup

            logInfo("Clicking confirm at: " + Coords.FREE_MISSION_CONFIRM);
            tapPoint(Coords.FREE_MISSION_CONFIRM);
            sleepTask(1500); // Wait for confirmation to complete

            logInfo("Free mission used successfully");
        } else {
            logDebug("Free mission button found at " + location +
                    " but too far from expected position " + Coords.FREE_MISSION_EXPECTED + ", skipping");
        }
    }

    /**
     * Checks for and uses Alliance Monuments if available.
     * 
     * <p>
     * Alliance Monuments provide bonus rewards and are accessed through
     * a special button on the Alliance Mobilization screen. The interaction
     * involves:
     * <ol>
     * <li>Click Alliance Monuments button</li>
     * <li>Perform image recognition for special chests (future feature)</li>
     * <li>Click through 5 fixed monument positions</li>
     * <li>Click back button twice to return</li>
     * </ol>
     * 
     * <p>
     * <b>Future Enhancement:</b> Monument image recognition is prepared
     * but not yet implemented (templates need to be added to EnumTemplates).
     */
    private void checkAndUseAllianceMonuments() {
        logDebug("Checking for Alliance Monuments button...");

        DTOImageSearchResult monumentsResult = templateSearchHelper.searchTemplate(
                EnumTemplates.AM_ALLIANCE_MONUMENTS,
                SearchConfig.builder()
                        .withThreshold(Thresholds.MONUMENTS)
                        .withMaxAttempts(1)
                        .build());

        if (!monumentsResult.isFound()) {
            logDebug("No Alliance Monuments button found");
            return;
        }

        DTOPoint location = monumentsResult.getPoint();
        logInfo("Alliance Monuments button found at " + location + " - using it");

        tapPoint(location);
        sleepTask(1500); // Wait for monuments screen to load

        // Image recognition loop for special chests (prepared for future use)
        processMonumentImageRecognition();

        // Click through the 5 fixed monument positions
        for (int i = 0; i < Coords.MONUMENT_CLICKS.length; i++) {
            logInfo("Clicking monument position " + (i + 1) + "/" + Coords.MONUMENT_CLICKS.length + " at: " +
                    Coords.MONUMENT_CLICKS[i]);
            tapPoint(Coords.MONUMENT_CLICKS[i]);
            sleepTask(i < 2 ? 1000 : 500); // Longer delay for first 2 clicks
        }

        // Click back button twice to close monuments screen
        for (int i = 1; i <= Limits.MONUMENT_BACK_CLICKS_COUNT; i++) {
            logInfo("Clicking back button (" + i + "/" + Limits.MONUMENT_BACK_CLICKS_COUNT + ") at: "
                    + Coords.BACK_BUTTON);
            tapPoint(Coords.BACK_BUTTON);
            sleepTask(500); // Wait for screen transition
        }

        logInfo("Alliance Monuments used successfully");
    }

    /**
     * Processes monument image recognition for special chests.
     * 
     * <p>
     * <b>Future Feature:</b> This method is prepared for detecting and
     * clicking special monument chests using image recognition. The feature
     * is currently inactive because the chest image templates are not yet
     * added to EnumTemplates.
     * 
     * <p>
     * <b>Planned Behavior:</b>
     * Search for chest images repeatedly and double-click each one found
     * until no more chests are detected. Then proceed with fixed clicks.
     * 
     * <p>
     * <b>To Enable:</b> Add the following templates to EnumTemplates:
     * <ul>
     * <li>AM_BALDUR_CHEST_1</li>
     * <li>AM_BALDUR_CHEST_2</li>
     * <li>AM_BALDUR_CHEST_3</li>
     * </ul>
     */
    private void processMonumentImageRecognition() {
        // Future feature: Monument chest detection
        // Templates need to be added to EnumTemplates before enabling this
        EnumTemplates[] monumentImages = {
                // EnumTemplates.AM_BALDUR_CHEST_1,
                // EnumTemplates.AM_BALDUR_CHEST_2,
                // EnumTemplates.AM_BALDUR_CHEST_3
        };

        // Skip if no images configured
        if (monumentImages.length == 0) {
            return;
        }

        logInfo("Starting monument image recognition loop...");
        int clickCount = 0;
        boolean imageFound;

        do {
            imageFound = false;

            // Search for each of the 3 chest images
            for (EnumTemplates imageTemplate : monumentImages) {
                DTOImageSearchResult imageResult = templateSearchHelper.searchTemplate(
                        imageTemplate,
                        SearchConfig.builder()
                                .withThreshold(85)
                                .withMaxAttempts(1)
                                .build());

                if (imageResult.isFound()) {
                    imageFound = true;
                    clickCount++;
                    DTOPoint imageLocation = imageResult.getPoint();

                    logInfo("Monument image found (" + imageTemplate.name() + ") at " + imageLocation +
                            " - clicking (click #" + clickCount + ")");

                    // First click
                    tapPoint(imageLocation);
                    sleepTask(500);

                    // Second click on same position
                    logInfo("Second click on same position");
                    tapPoint(imageLocation);
                    sleepTask(500);

                    // Break inner loop to re-search all images from start
                    break;
                }
            }

            if (!imageFound) {
                logInfo("No more monument images found. Proceeding with fixed clicks. Total recognition clicks: " +
                        clickCount);
            }

        } while (imageFound);
    }

    // ========================================================================
    // OVERRIDES
    // ========================================================================

    /**
     * Returns the required start location for this task.
     * 
     * <p>
     * Alliance Mobilization can be accessed from any location (home or world)
     * since we navigate through the Events button which is accessible from both.
     * 
     * @return EnumStartLocation.ANY since navigation works from anywhere
     */
    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.ANY;
    }

    /**
     * Indicates whether this task provides daily mission progress.
     * 
     * <p>
     * Alliance Mobilization does NOT contribute to daily mission objectives.
     * 
     * @return false
     */
    @Override
    public boolean provideDailyMissionProgress() {
        return false;
    }

    /**
     * Indicates whether this task consumes stamina.
     * 
     * <p>
     * Alliance Mobilization does NOT consume stamina. It has its own
     * separate attempts counter that resets daily.
     * 
     * @return false
     */
    @Override
    protected boolean consumesStamina() {
        return false;
    }

    // ========================================================================
    // DATA CLASSES
    // ========================================================================

    /**
     * Represents the attempts counter status.
     * 
     * @param remaining number of attempts remaining
     * @param total     total attempts available (may be null if unknown)
     */
    private record AttemptStatus(int remaining, Integer total) {
    }

    /**
     * Represents the result of task processing.
     * 
     * @param shouldStopProcessing true if processing should stop (reschedule
     *                             already set)
     * @param shortestCooldown     shortest cooldown in seconds from processed tasks
     * @param missionFound         true if any mission was detected (running or
     *                             available)
     * @param onlyRunningMission   true if only a running mission was found (no
     *                             available missions)
     */
    private record TaskProcessingResult(boolean shouldStopProcessing, int shortestCooldown, boolean missionFound,
            boolean onlyRunningMission) {
    }

    /**
     * Represents task search filters based on rewards percentage configuration.
     * 
     * @param search200 whether to search for 200% tasks
     * @param search120 whether to search for 120% tasks
     * @param accept200 whether to accept 200% tasks (vs refresh)
     * @param accept120 whether to accept 120% tasks (vs refresh)
     */
    private record TaskSearchFilters(
            boolean search200,
            boolean search120,
            boolean accept200,
            boolean accept120) {
    }
}
