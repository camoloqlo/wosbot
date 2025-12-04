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
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper;

import java.time.LocalDateTime;

/**
 * Task that claims daily loyalty tags from Expert Romulus.
 * 
 * <p>
 * <b>Game Mechanics:</b>
 * <ul>
 * <li>Romulus provides free loyalty tags daily</li>
 * <li>His icon appears above the Enlistment Office building</li>
 * <li>Enlistment Office is adjacent to the Infirmary</li>
 * <li>Camera must be moved to Infirmary area to see Romulus icon</li>
 * <li>Claim button may have animations affecting template matching</li>
 * <li>Resets daily at 00:00 UTC</li>
 * </ul>
 * 
 * <p>
 * <b>Navigation Path:</b>
 * <ol>
 * <li>Home screen</li>
 * <li>Left menu (City section)</li>
 * <li>Research Center shortcut</li>
 * <li>Tap Infirmary to move camera</li>
 * <li>Romulus icon becomes visible above Enlistment Office</li>
 * <li>Tap claim button</li>
 * </ol>
 * 
 * <p>
 * <b>Scheduling:</b>
 * <ul>
 * <li>Success: Reschedules to next game reset</li>
 * <li>Not found: Assumes already claimed, reschedules to next game reset</li>
 * <li>Navigation failure: Retries in 10 minutes</li>
 * </ul>
 * 
 * @author WoS Bot
 */
public class ExpertsRomulusTagTask extends DelayedTask {

    // ========================================================================
    // SEARCH CONFIGURATION CONSTANTS
    // ========================================================================

    /**
     * Maximum search attempts for Romulus claim button.
     * Multiple attempts needed due to:
     * - Button animations causing match percentage fluctuations
     * - Slow device loading times
     * - Camera movement completion delays
     */
    private static final int MAX_CLAIM_BUTTON_ATTEMPTS = 10;

    /**
     * Match threshold for Romulus claim button detection.
     * Lower than standard 90% due to button animations that cause
     * slight visual variations, similar to Agnes icon behavior.
     */
    private static final int CLAIM_BUTTON_THRESHOLD = 80;

    /**
     * Delay between search retry attempts in milliseconds.
     * Allows time for button animations to stabilize and camera movement to
     * complete.
     */
    private static final long SEARCH_RETRY_DELAY_MS = 300L;

    // ========================================================================
    // NAVIGATION CONSTANTS
    // ========================================================================

    /**
     * Tap area for the Infirmary building.
     * Tapping this area moves the game camera to the Infirmary,
     * which makes the nearby Enlistment Office (and Romulus icon) visible.
     */
    private static final DTOArea INFIRMARY_TAP_AREA = new DTOArea(
            new DTOPoint(488, 410),
            new DTOPoint(550, 450));

    /**
     * Retry delay in minutes when navigation fails (Research Center not found).
     */
    private static final int NAVIGATION_RETRY_MINUTES = 10;

    // ========================================================================
    // SEARCH CONFIGURATION
    // ========================================================================

    /**
     * Predefined search configuration for Romulus claim button detection.
     * Optimized for button animations and camera movement delays.
     */
    private static final TemplateSearchHelper.SearchConfig ROMULUS_CLAIM_CONFIG = TemplateSearchHelper.SearchConfig
            .builder()
            .withMaxAttempts(MAX_CLAIM_BUTTON_ATTEMPTS)
            .withThreshold(CLAIM_BUTTON_THRESHOLD)
            .withDelay(SEARCH_RETRY_DELAY_MS)
            .build();

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    /**
     * Constructs a new ExpertsRomulusTagTask.
     * 
     * @param profile The profile this task will execute for
     * @param tpTask  The task type enum
     */
    public ExpertsRomulusTagTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    // ========================================================================
    // TASK CONFIGURATION
    // ========================================================================

    /**
     * This task requires starting from the HOME screen to access left menu.
     * 
     * @return HOME - ensures reliable navigation to Research Center shortcut
     */
    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    // ========================================================================
    // MAIN EXECUTION
    // ========================================================================

    /**
     * Executes the Romulus loyalty tags claiming process.
     * 
     * <p>
     * <b>Process:</b>
     * <ol>
     * <li>Navigate to Romulus icon via camera movement</li>
     * <li>Search for claim button (handles retries and animations)</li>
     * <li>Claim tags if button found</li>
     * <li>Reschedule appropriately based on outcome</li>
     * </ol>
     * 
     * <p>
     * <b>Early Exit:</b> If navigation fails, task reschedules for retry
     * and exits immediately without attempting to claim.
     */
    @Override
    protected void execute() {
        logInfo("Attempting to claim loyalty tags from Romulus");

        if (!navigateToRomulusIcon()) {
            return; // Navigation failed, already rescheduled
        }

        boolean claimed = attemptToClaimRomulusTags();

        if (!claimed) {
            logInfo("Romulus claim button not found after " + MAX_CLAIM_BUTTON_ATTEMPTS +
                    " attempts. Assuming already claimed today");
        }

        rescheduleToGameReset();
        logInfo("Romulus tags task completed");
    }

    // ========================================================================
    // NAVIGATION METHODS
    // ========================================================================

    /**
     * Navigates camera to show Romulus icon above Enlistment Office.
     * 
     * <p>
     * <b>Navigation Steps:</b>
     * <ol>
     * <li>Open left menu (City section)</li>
     * <li>Search for Research Center shortcut</li>
     * <li>Tap Research Center to open city view</li>
     * <li>Tap Infirmary to move camera</li>
     * <li>Romulus icon becomes visible above nearby Enlistment Office</li>
     * </ol>
     * 
     * <p>
     * <b>Camera Movement:</b> The Enlistment Office is not directly visible
     * from the Research Center view. Tapping the Infirmary building moves the
     * game camera to that area, which brings the Enlistment Office (and Romulus
     * icon above it) into view.
     * 
     * @return true if navigation succeeded, false if Research Center not found
     */
    private boolean navigateToRomulusIcon() {
        logDebug("Navigating camera to Romulus icon via Infirmary");

        // Open left menu on city section
        marchHelper.openLeftMenuCitySection(true);

        // Search for Research Center shortcut
        DTOImageSearchResult researchCenter = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_SHORTCUTS_RESEARCH_CENTER,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!researchCenter.isFound()) {
            logWarning("Research Center shortcut not found in left menu");
            rescheduleForNavigationRetry();
            marchHelper.closeLeftMenu();
            return false;
        }

        logDebug("Opening Research Center view");
        tapPoint(researchCenter.getPoint());
        sleepTask(500); // Wait for city view to load

        logDebug("Tapping Infirmary to move camera");
        tapRandomPoint(
                INFIRMARY_TAP_AREA.topLeft(),
                INFIRMARY_TAP_AREA.bottomRight());
        sleepTask(500); // Wait for camera movement to complete

        return true;
    }

    // ========================================================================
    // CLAIMING LOGIC
    // ========================================================================

    /**
     * Attempts to find and claim loyalty tags from Romulus claim button.
     * 
     * <p>
     * Uses predefined search configuration that handles:
     * <ul>
     * <li>Multiple retry attempts (10 total)</li>
     * <li>Lower match threshold (80%) for animation tolerance</li>
     * <li>Delay between attempts for UI stabilization</li>
     * </ul>
     * 
     * <p>
     * The search configuration automatically handles retries and delays,
     * eliminating the need for manual loops.
     * 
     * <p>
     * <b>Note:</b> After tapping the claim button, the icon disappears
     * immediately with no confirmation screen, similar to Agnes behavior.
     * 
     * @return true if claim button was found and tapped, false otherwise
     */
    private boolean attemptToClaimRomulusTags() {
        logDebug("Searching for Romulus claim button with " +
                MAX_CLAIM_BUTTON_ATTEMPTS + " attempts");

        DTOImageSearchResult claimButton = templateSearchHelper.searchTemplate(
                EnumTemplates.ROMULUS_CLAIM_TAG_BUTTON,
                ROMULUS_CLAIM_CONFIG);

        if (!claimButton.isFound()) {
            return false;
        }

        logInfo("Romulus claim button found - claiming loyalty tags");
        tapPoint(claimButton.getPoint());
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
     * <li>Success: Tags claimed, wait for next daily reset</li>
     * <li>Failure: Assumes already claimed, wait for next daily reset</li>
     * </ul>
     */
    private void rescheduleToGameReset() {
        LocalDateTime nextReset = UtilTime.getGameReset();
        reschedule(nextReset);
        logInfo("Rescheduled to next game reset: " +
                nextReset.format(DATETIME_FORMATTER));
    }

    /**
     * Reschedules the task for navigation retry (10 minutes).
     * 
     * <p>
     * Used when Research Center shortcut is not found in left menu.
     * This typically indicates a temporary UI issue or left menu not loaded yet.
     */
    private void rescheduleForNavigationRetry() {
        LocalDateTime retryTime = LocalDateTime.now().plusMinutes(NAVIGATION_RETRY_MINUTES);
        reschedule(retryTime);
        logWarning("Navigation failed. Retrying at " + retryTime.format(TIME_FORMATTER));
    }
}