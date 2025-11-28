package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper;

import java.time.LocalDateTime;

/**
 * Task that claims extra daily intel from Expert Agnes on the Intel screen.
 * 
 * <p>
 * <b>Game Mechanics:</b>
 * <ul>
 * <li>Agnes provides one free intel claim per day</li>
 * <li>Her icon appears on the Intel screen after reset</li>
 * <li>Icon has small animations that affect template matching</li>
 * <li>After claiming, icon disappears (no confirmation available)</li>
 * <li>Resets daily at 00:00 UTC</li>
 * </ul>
 * 
 * <p>
 * <b>Execution Flow:</b>
 * <ol>
 * <li>Navigate to Intel screen</li>
 * <li>Search for Agnes icon (with retries for animations/slow devices)</li>
 * <li>Tap icon to claim intel</li>
 * <li>Reschedule to next game reset</li>
 * </ol>
 * 
 * <p>
 * <b>Scheduling:</b>
 * <ul>
 * <li>Success: Reschedules to next game reset</li>
 * <li>Not found: Assumes already claimed, reschedules to next game reset</li>
 * </ul>
 * 
 * @author WoS Bot
 */
public class ExpertsAgnesIntelTask extends DelayedTask {

    // ========================================================================
    // SEARCH CONFIGURATION CONSTANTS
    // ========================================================================

    /**
     * Maximum search attempts for Agnes icon.
     * Multiple attempts needed due to:
     * - Icon animations causing match percentage fluctuations
     * - Slow device loading times (1-2 seconds for Intel screen)
     */
    private static final int MAX_AGNES_SEARCH_ATTEMPTS = 10;

    /**
     * Match threshold for Agnes icon detection.
     * Lower than standard 90% due to small icon animations that cause
     * slight visual variations. Any match above 70% is typically correct,
     * but 80% provides good balance between reliability and tolerance.
     */
    private static final int AGNES_ICON_THRESHOLD = 80;

    /**
     * Delay between search retry attempts in milliseconds.
     * Allows time for icon animations to stabilize and UI to fully load.
     */
    private static final long SEARCH_RETRY_DELAY_MS = 300L;

    // ========================================================================
    // SEARCH AREA CONSTANTS
    // ========================================================================

    /**
     * Search area for Agnes icon on the Intel screen.
     * Agnes appears in a fixed location, restricting search improves performance.
     */
    private static final DTOArea AGNES_ICON_AREA = new DTOArea(
            new DTOPoint(0, 80),
            new DTOPoint(168, 470));

    // ========================================================================
    // SEARCH CONFIGURATION
    // ========================================================================

    /**
     * Predefined search configuration for Agnes icon detection.
     * Optimized for icon animations and slow device compatibility.
     */
    private static final TemplateSearchHelper.SearchConfig AGNES_SEARCH_CONFIG = TemplateSearchHelper.SearchConfig
            .builder()
            .withMaxAttempts(MAX_AGNES_SEARCH_ATTEMPTS)
            .withThreshold(AGNES_ICON_THRESHOLD)
            .withDelay(SEARCH_RETRY_DELAY_MS)
            .withArea(AGNES_ICON_AREA)
            .build();

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    /**
     * Constructs a new ExpertsAgnesIntelTask.
     * 
     * @param profile The profile this task will execute for
     * @param tpTask  The task type enum
     */
    public ExpertsAgnesIntelTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    // ========================================================================
    // TASK CONFIGURATION
    // ========================================================================

    /**
     * This task requires starting from the HOME screen to access Intel menu.
     * 
     * @return HOME - ensures reliable navigation to Intel screen
     */
    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    // ========================================================================
    // MAIN EXECUTION
    // ========================================================================

    /**
     * Executes the Agnes intel claiming process.
     * 
     * <p>
     * <b>Process:</b>
     * <ol>
     * <li>Navigate to Intel screen via helper</li>
     * <li>Search for Agnes icon (handles retries and animations)</li>
     * <li>Claim intel if icon found</li>
     * <li>Reschedule to next game reset regardless of outcome</li>
     * </ol>
     * 
     * <p>
     * <b>Note:</b> If Agnes icon is not found, assumes intel was already
     * claimed today, as there's no way to verify past claims after the icon
     * disappears.
     */
    @Override
    protected void execute() {
        logInfo("Attempting to claim extra intel from Agnes");

        intelScreenHelper.ensureOnIntelScreen();

        boolean claimed = attemptToClaimAgnesIntel();

        if (!claimed) {
            logInfo("Agnes icon not found after " + MAX_AGNES_SEARCH_ATTEMPTS +
                    " attempts. Assuming already claimed today");
        }

        rescheduleToGameReset();
        logInfo("Agnes intel task completed");
    }

    // ========================================================================
    // CLAIMING LOGIC
    // ========================================================================

    /**
     * Attempts to find and claim intel from Agnes icon.
     * 
     * <p>
     * Uses predefined search configuration that handles:
     * <ul>
     * <li>Multiple retry attempts (10 total)</li>
     * <li>Lower match threshold (80%) for animation tolerance</li>
     * <li>Delay between attempts for UI stabilization</li>
     * <li>Restricted search area for better performance</li>
     * </ul>
     * 
     * <p>
     * The search configuration automatically handles retries and delays,
     * eliminating the need for manual loops.
     * 
     * @return true if Agnes icon was found and claimed, false otherwise
     */
    private boolean attemptToClaimAgnesIntel() {
        logDebug("Searching for Agnes icon with " + MAX_AGNES_SEARCH_ATTEMPTS +
                " attempts");

        DTOImageSearchResult agnesIcon = templateSearchHelper.searchTemplate(
                EnumTemplates.AGNES_CLAIM_INTEL,
                AGNES_SEARCH_CONFIG);

        if (!agnesIcon.isFound()) {
            return false;
        }

        logInfo("Agnes icon found - claiming intel");
        tapPoint(agnesIcon.getPoint());
        sleepTask(1000); // Wait for claim animation to complete

        return true;
    }

    // ========================================================================
    // RESCHEDULING METHODS
    // ========================================================================

    /**
     * Reschedules the task to the next game reset (00:00 UTC).
     * 
     * <p>
     * Used for both success and failure cases:
     * <ul>
     * <li>Success: Intel claimed, wait for next daily reset</li>
     * <li>Failure: Assumes already claimed, wait for next daily reset</li>
     * </ul>
     */
    private void rescheduleToGameReset() {
        LocalDateTime nextReset = UtilTime.getGameReset();
        reschedule(nextReset);
        logInfo("Rescheduled to next game reset: " +
                nextReset.format(DATETIME_FORMATTER));
    }
}
