package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Task responsible for managing hero recruitment rewards.
 * 
 * <p>
 * This task handles two types of hero recruitment:
 * <ul>
 * <li><b>Advanced Recruitment:</b> Resets daily at game reset time</li>
 * <li><b>Epic Recruitment:</b> Resets after a certain duration from last
 * claim</li>
 * </ul>
 * 
 * <p>
 * <b>Task Flow:</b>
 * <ol>
 * <li>Navigate to hero recruitment interface</li>
 * <li>Check and claim advanced recruitment if available</li>
 * <li>Extract next advanced recruitment time (capped at game reset)</li>
 * <li>Check and claim epic recruitment if available</li>
 * <li>Extract next epic recruitment time</li>
 * <li>Reschedule to earliest of the two times</li>
 * </ol>
 * 
 * <p>
 * <b>Scheduling Strategy:</b>
 * Advanced recruitment is capped at game reset (won't schedule beyond daily
 * reset),
 * while epic recruitment can schedule beyond reset based on its own timer.
 * The task runs at whichever time comes first.
 */
public class HeroRecruitmentTask extends DelayedTask {

    // ===============================
    // CONSTANTS
    // ===============================

    /** Retry delay when OCR or navigation fails (minutes) */
    private static final int OCR_FAILURE_RETRY_MINUTES = 5;

    /** Maximum OCR retry attempts for time extraction */
    private static final int MAX_OCR_RETRIES = 3;

    /** Delay between OCR retry attempts (milliseconds) */
    private static final long OCR_RETRY_DELAY_MS = 200L;

    // Navigation coordinates
    private static final DTOPoint NAV_TAP_1_MIN = new DTOPoint(160, 1190);
    private static final DTOPoint NAV_TAP_1_MAX = new DTOPoint(217, 1250);
    private static final DTOPoint NAV_TAP_2_MIN = new DTOPoint(400, 1190);
    private static final DTOPoint NAV_TAP_2_MAX = new DTOPoint(660, 1250);

    // Advanced recruitment coordinates
    private static final DTOPoint ADVANCED_SEARCH_MIN = new DTOPoint(40, 800);
    private static final DTOPoint ADVANCED_SEARCH_MAX = new DTOPoint(340, 1100);
    private static final DTOPoint ADVANCED_CLAIM_MIN = new DTOPoint(80, 827);
    private static final DTOPoint ADVANCED_CLAIM_MAX = new DTOPoint(315, 875);
    private static final DTOPoint ADVANCED_OCR_TOP_LEFT = new DTOPoint(40, 770);
    private static final DTOPoint ADVANCED_OCR_BOTTOM_RIGHT = new DTOPoint(350, 810);

    // Epic recruitment coordinates
    private static final DTOPoint EPIC_SEARCH_MIN = new DTOPoint(40, 1160);
    private static final DTOPoint EPIC_SEARCH_MAX = new DTOPoint(340, 1255);
    private static final DTOPoint EPIC_CLAIM_MIN = new DTOPoint(70, 1180);
    private static final DTOPoint EPIC_CLAIM_MAX = new DTOPoint(315, 1230);
    private static final DTOPoint EPIC_OCR_TOP_LEFT = new DTOPoint(53, 1130);
    private static final DTOPoint EPIC_OCR_BOTTOM_RIGHT = new DTOPoint(330, 1160);

    // ===============================
    // FIELDS
    // ===============================

    /** Helper for flexible OCR-based time recognition */
    private final TextRecognitionRetrier<Duration> timeHelper;

    // ===============================
    // CONSTRUCTOR
    // ===============================

    /**
     * Constructs a new HeroRecruitmentTask.
     * 
     * @param profile     The game profile this task operates on
     * @param tpDailyTask The task type enum from the daily task registry
     */
    public HeroRecruitmentTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
        this.timeHelper = new TextRecognitionRetrier<>(provider);
    }

    // ===============================
    // MAIN EXECUTION
    // ===============================

    /**
     * Main execution method for the hero recruitment task.
     * 
     * <p>
     * <b>Execution Flow:</b>
     * <ol>
     * <li>Navigate to hero recruitment interface</li>
     * <li>Process advanced recruitment (claim + extract time)</li>
     * <li>Process epic recruitment (claim + extract time)</li>
     * <li>Calculate next execution time (earliest of both)</li>
     * <li>Reschedule task</li>
     * </ol>
     * 
     * <p>
     * OCR failures trigger retry in {@value #OCR_FAILURE_RETRY_MINUTES} minutes.
     * Navigation is handled automatically by DelayedTask framework after execution.
     */
    @Override
    protected void execute() {
        logInfo("Starting hero recruitment task");

        navigateToHeroRecruitment();

        LocalDateTime advancedTime = processAdvancedRecruitment();
        LocalDateTime epicTime = processEpicRecruitment();

        LocalDateTime nextExecution = calculateNextExecution(advancedTime, epicTime);

        logInfo("Next hero recruitment check scheduled for: " + nextExecution.format(DATETIME_FORMATTER));
        reschedule(nextExecution);
    }

    // ===============================
    // NAVIGATION
    // ===============================

    /**
     * Navigates to the hero recruitment interface.
     * 
     * <p>
     * Performs two sequential taps to open the hero recruitment menu.
     * The framework will handle returning to home screen after task completion.
     */
    private void navigateToHeroRecruitment() {
        logInfo("Navigating to hero recruitment interface");

        tapRandomPoint(NAV_TAP_1_MIN, NAV_TAP_1_MAX);
        sleepTask(500); // Wait for first menu to open

        tapRandomPoint(NAV_TAP_2_MIN, NAV_TAP_2_MAX);
        sleepTask(500); // Wait for hero recruitment interface to load
    }

    // ===============================
    // RECRUITMENT PROCESSING
    // ===============================

    /**
     * Processes advanced recruitment rewards and extracts next availability time.
     * 
     * <p>
     * Advanced recruitment resets daily at game reset time, so the extracted
     * time is capped at the next game reset to avoid scheduling beyond it.
     * 
     * @return Next advanced recruitment time (capped at game reset), or game reset
     *         if extraction fails
     */
    private LocalDateTime processAdvancedRecruitment() {
        logInfo("Processing advanced recruitment");

        RecruitmentResult result = processRecruitmentType(
                "Advanced",
                ADVANCED_SEARCH_MIN,
                ADVANCED_SEARCH_MAX,
                ADVANCED_CLAIM_MIN,
                ADVANCED_CLAIM_MAX,
                ADVANCED_OCR_TOP_LEFT,
                ADVANCED_OCR_BOTTOM_RIGHT);

        return capAtGameReset(result.nextTime());
    }

    /**
     * Processes epic recruitment rewards and extracts next availability time.
     * 
     * <p>
     * Epic recruitment resets after a certain duration from last claim,
     * so it's not capped at game reset and can schedule into the next day.
     * 
     * @return Next epic recruitment time, or retry time if extraction fails
     */
    private LocalDateTime processEpicRecruitment() {
        logInfo("Processing epic recruitment");

        RecruitmentResult result = processRecruitmentType(
                "Epic",
                EPIC_SEARCH_MIN,
                EPIC_SEARCH_MAX,
                EPIC_CLAIM_MIN,
                EPIC_CLAIM_MAX,
                EPIC_OCR_TOP_LEFT,
                EPIC_OCR_BOTTOM_RIGHT);

        return result.nextTime();
    }

    /**
     * Processes a recruitment type (generic method for both advanced and epic).
     * 
     * <p>
     * <b>Process Flow:</b>
     * <ol>
     * <li>Search for claim button in specified region</li>
     * <li>If found, claim the reward</li>
     * <li>Extract next availability time via OCR</li>
     * <li>Return result with next time or fallback</li>
     * </ol>
     * 
     * @param type           Recruitment type name for logging
     * @param searchMin      Top-left corner of claim button search region
     * @param searchMax      Bottom-right corner of claim button search region
     * @param claimMin       Top-left corner of claim button tap region
     * @param claimMax       Bottom-right corner of claim button tap region
     * @param ocrTopLeft     Top-left corner of time OCR region
     * @param ocrBottomRight Bottom-right corner of time OCR region
     * @return RecruitmentResult containing next time or fallback
     */
    private RecruitmentResult processRecruitmentType(
            String type,
            DTOPoint searchMin,
            DTOPoint searchMax,
            DTOPoint claimMin,
            DTOPoint claimMax,
            DTOPoint ocrTopLeft,
            DTOPoint ocrBottomRight) {

        checkAndClaimRecruitment(type, searchMin, searchMax, claimMin, claimMax);
        LocalDateTime nextTime = extractNextRecruitmentTime(type, ocrTopLeft, ocrBottomRight);

        if (nextTime != null) {
            return new RecruitmentResult(nextTime, true);
        } else {
            logWarning(type + " recruitment time extraction failed. Using fallback.");
            return new RecruitmentResult(getFallbackTime(), false);
        }
    }

    /**
     * Checks for and claims recruitment reward if available.
     * 
     * @param type      Recruitment type name for logging
     * @param searchMin Top-left corner of search region
     * @param searchMax Bottom-right corner of search region
     * @param claimMin  Top-left corner of claim tap region
     * @param claimMax  Bottom-right corner of claim tap region
     * @return true if reward was claimed, false if not available
     */
    private void checkAndClaimRecruitment(
            String type,
            DTOPoint searchMin,
            DTOPoint searchMax,
            DTOPoint claimMin,
            DTOPoint claimMax) {

        DTOImageSearchResult claimResult = templateSearchHelper.searchTemplate(
                EnumTemplates.HERO_RECRUIT_CLAIM,
                TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(1)
                        .withThreshold(95)
                        .withDelay(300L)
                        .withCoordinates(searchMin, searchMax)
                        .build());

        if (claimResult.isFound()) {
            logInfo(type + " recruitment reward available. Claiming now.");
            tapRandomPoint(claimMin, claimMax);
            sleepTask(1000); // Wait for claim animation
        } else {
            logInfo(type + " recruitment reward not available yet.");
        }
    }

    /**
     * Extracts the next recruitment time from the UI via OCR.
     * 
     * <p>
     * Uses {@link TextRecognitionRetrier} for robust OCR with validation
     * and retry logic. Attempts up to {@value #MAX_OCR_RETRIES} times.
     * 
     * @param type        Recruitment type name for logging
     * @param topLeft     Top-left corner of OCR region
     * @param bottomRight Bottom-right corner of OCR region
     * @return Next recruitment time, or null if extraction failed
     */
    private LocalDateTime extractNextRecruitmentTime(
            String type,
            DTOPoint topLeft,
            DTOPoint bottomRight) {

        logInfo("Extracting " + type + " recruitment next availability time");

        Duration nextTime = timeHelper.execute(
                topLeft,
                bottomRight,
                MAX_OCR_RETRIES,
                OCR_RETRY_DELAY_MS,
                DTOTesseractSettings.builder()
                        .setAllowedChars("0123456789d")
                        .build(),
                TimeValidators::isValidTime,
                TimeConverters::toDuration);

        if (nextTime != null) {
            LocalDateTime nextClaimTime = LocalDateTime.now().plus(nextTime);
            logInfo(type + " recruitment next time: " + nextClaimTime.format(DATETIME_FORMATTER));
            return nextClaimTime;
        } else {
            logWarning("Failed to extract " + type + " recruitment time after " +
                    MAX_OCR_RETRIES + " attempts");
            return null;
        }
    }

    // ===============================
    // SCHEDULING LOGIC
    // ===============================

    /**
     * Calculates the next execution time based on both recruitment times.
     * 
     * <p>
     * Returns the earliest of the two recruitment times to ensure
     * we don't miss either opportunity.
     * 
     * @param advancedTime Next advanced recruitment time
     * @param epicTime     Next epic recruitment time
     * @return Earliest time between the two
     */
    private LocalDateTime calculateNextExecution(LocalDateTime advancedTime, LocalDateTime epicTime) {
        return getEarliest(advancedTime, epicTime);
    }

    /**
     * Caps a time at the next game reset.
     * 
     * <p>
     * Used for advanced recruitment since it resets daily and shouldn't
     * be scheduled beyond the game reset time.
     * 
     * @param time Time to cap
     * @return Original time if before reset, otherwise game reset time
     */
    private LocalDateTime capAtGameReset(LocalDateTime time) {
        LocalDateTime gameReset = UtilTime.getGameReset();
        return getEarliest(time, gameReset);
    }

    /**
     * Returns the earliest of two LocalDateTime values, handling nulls safely.
     * 
     * <p>
     * Null handling:
     * <ul>
     * <li>If both null: returns fallback time (retry in 5 minutes)</li>
     * <li>If one null: returns the non-null value</li>
     * <li>If neither null: returns the earlier time</li>
     * </ul>
     * 
     * @param dt1 First datetime
     * @param dt2 Second datetime
     * @return Earliest datetime, or fallback if both null
     */
    private LocalDateTime getEarliest(LocalDateTime dt1, LocalDateTime dt2) {
        if (dt1 == null && dt2 == null) {
            return getFallbackTime();
        }
        if (dt1 == null) {
            return dt2;
        }
        if (dt2 == null) {
            return dt1;
        }
        return dt1.isBefore(dt2) ? dt1 : dt2;
    }

    /**
     * Gets the fallback time for when OCR extraction fails.
     * 
     * @return Current time plus {@value #OCR_FAILURE_RETRY_MINUTES} minutes
     */
    private LocalDateTime getFallbackTime() {
        return LocalDateTime.now().plusMinutes(OCR_FAILURE_RETRY_MINUTES);
    }

    // ===============================
    // TASK FRAMEWORK OVERRIDES
    // ===============================

    /**
     * Indicates that this task provides progress toward daily missions.
     * 
     * <p>
     * Hero recruitment typically counts toward "Recruit heroes" daily missions.
     * 
     * @return true to trigger daily mission progress checks
     */
    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }

    /**
     * Specifies the required starting screen location for this task.
     * 
     * <p>
     * This task can start from any screen as it performs its own navigation.
     * 
     * @return ANY as the required start location
     */
    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.ANY;
    }

    // ===============================
    // INNER CLASSES
    // ===============================

    /**
     * Record containing recruitment processing result.
     * 
     * @param nextTime Next availability time (or fallback if extraction failed)
     * @param success  Whether OCR extraction was successful
     */
    private record RecruitmentResult(LocalDateTime nextTime, boolean success) {
    }
}