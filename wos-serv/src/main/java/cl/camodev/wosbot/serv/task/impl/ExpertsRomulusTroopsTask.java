package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
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
 * Task that claims daily troops from Expert Romulus.
 * 
 * <p>
 * <b>Game Mechanics:</b>
 * <ul>
 * <li>Romulus provides free troops daily from one troop building</li>
 * <li>User configures which troop type to claim (Infantry/Lancer/Marksman)</li>
 * <li>Claim button appears above the selected troop building</li>
 * <li>Three troop buildings exist on map, each with potential claim button</li>
 * <li>Search area restricted to prevent claiming from wrong building</li>
 * <li>Navigation overlay must be cleared before claim button is visible</li>
 * <li>Resets daily at 00:00 UTC</li>
 * </ul>
 * 
 * <p>
 * <b>Configuration:</b>
 * User must set {@link EnumConfigurationKey#EXPERT_ROMULUS_TROOPS_TYPE_STRING}
 * to one of: "Infantry", "Lancer", or "Marksman"
 * 
 * <p>
 * <b>Navigation Path:</b>
 * <ol>
 * <li>Home screen</li>
 * <li>Left menu (City section)</li>
 * <li>Tap selected troop building shortcut</li>
 * <li>Wait for camera to move to building</li>
 * <li>Tap back button to clear navigation overlay</li>
 * <li>Romulus claim button becomes visible above building</li>
 * <li>Tap claim button (within restricted search area)</li>
 * </ol>
 * 
 * <p>
 * <b>Scheduling:</b>
 * <ul>
 * <li>Success: Reschedules to next game reset</li>
 * <li>Not found: Assumes already claimed, reschedules to next game reset</li>
 * <li>Navigation failure: Retries in 10 minutes</li>
 * <li>Invalid config: Retries in 1 hour (gives user time to fix)</li>
 * </ul>
 * 
 * @author WoS Bot
 */
public class ExpertsRomulusTroopsTask extends DelayedTask {

    // ========================================================================
    // SEARCH CONFIGURATION CONSTANTS
    // ========================================================================

    /**
     * Maximum search attempts for Romulus claim button.
     * Multiple attempts needed due to:
     * - Button animations causing match percentage fluctuations
     * - Overlay clearing delays
     * - Camera movement completion time
     */
    private static final int MAX_CLAIM_BUTTON_ATTEMPTS = 10;

    /**
     * Match threshold for Romulus claim button detection.
     * Lower than standard 90% due to button animations,
     * consistent with other Romulus tasks.
     */
    private static final int CLAIM_BUTTON_THRESHOLD = 80;

    /**
     * Delay between search retry attempts in milliseconds.
     */
    private static final long SEARCH_RETRY_DELAY_MS = 300L;

    // ========================================================================
    // SEARCH AREA CONSTANTS
    // ========================================================================

    /**
     * Search area for Romulus claim button above troop buildings.
     * 
     * <p>
     * <b>Why restrict search area?</b>
     * Three troop buildings exist on the map (Infantry, Lancer, Marksman),
     * and each can have a Romulus claim button above it. Without restricting
     * the search area, the template matching might find and click the wrong
     * building's claim button. This area ensures we only search the region
     * where the user's selected troop building is located.
     */
    private static final DTOArea CLAIM_BUTTON_SEARCH_AREA = new DTOArea(
            new DTOPoint(180, 351),
            new DTOPoint(443, 600));

    // ========================================================================
    // RETRY TIMING CONSTANTS
    // ========================================================================

    private static final int NAVIGATION_RETRY_MINUTES = 10;
    private static final int INVALID_CONFIG_RETRY_HOURS = 1;

    // ========================================================================
    // SEARCH CONFIGURATION
    // ========================================================================

    /**
     * Predefined search configuration for Romulus troops claim button.
     * Uses restricted search area to prevent clicking wrong troop building.
     */
    private static final TemplateSearchHelper.SearchConfig ROMULUS_TROOPS_CLAIM_CONFIG = TemplateSearchHelper.SearchConfig
            .builder()
            .withMaxAttempts(MAX_CLAIM_BUTTON_ATTEMPTS)
            .withThreshold(CLAIM_BUTTON_THRESHOLD)
            .withDelay(SEARCH_RETRY_DELAY_MS)
            .withCoordinates(
                    CLAIM_BUTTON_SEARCH_AREA.topLeft(),
                    CLAIM_BUTTON_SEARCH_AREA.bottomRight())
            .build();

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    /**
     * Constructs a new ExpertsRomulusTroopsTask.
     * 
     * @param profile The profile this task will execute for
     * @param tpTask  The task type enum
     */
    public ExpertsRomulusTroopsTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    // ========================================================================
    // TASK CONFIGURATION
    // ========================================================================

    /**
     * This task requires starting from the HOME screen to access left menu.
     * 
     * @return HOME - ensures reliable navigation to troop building shortcuts
     */
    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    // ========================================================================
    // MAIN EXECUTION
    // ========================================================================

    /**
     * Executes the Romulus troops claiming process.
     * 
     * <p>
     * <b>Process:</b>
     * <ol>
     * <li>Load and validate troop type configuration</li>
     * <li>Navigate to selected troop building</li>
     * <li>Clear navigation overlay</li>
     * <li>Search for and claim Romulus troops</li>
     * <li>Reschedule appropriately based on outcome</li>
     * </ol>
     * 
     * <p>
     * <b>Early Exit:</b> Task exits immediately if:
     * <ul>
     * <li>Troop type configuration is invalid</li>
     * <li>Navigation to troop building fails</li>
     * </ul>
     */
    @Override
    protected void execute() {
        logInfo("Attempting to claim troops from Romulus");

        // Load and validate troop type configuration
        String troopType = loadTroopTypeConfiguration();
        if (troopType == null) {
            return; // Invalid config, already rescheduled
        }

        EnumTemplates troopTemplate = getTroopTemplate(troopType);
        if (troopTemplate == null) {
            return; // Invalid troop type, already rescheduled
        }

        // Navigate to troop building
        if (!navigateToTroopBuilding(troopTemplate, troopType)) {
            return; // Navigation failed, already rescheduled
        }

        // Clear overlay and claim troops
        clearNavigationOverlay();

        boolean claimed = attemptToClaimRomulusTroops(troopType);

        if (!claimed) {
            logInfo("Romulus claim button not found after " + MAX_CLAIM_BUTTON_ATTEMPTS +
                    " attempts. Assuming already claimed today");
        }

        rescheduleToGameReset();
        logInfo("Romulus troops task completed");
    }

    // ========================================================================
    // CONFIGURATION LOADING
    // ========================================================================

    /**
     * Loads the troop type configuration from user profile.
     * 
     * @return The configured troop type string, or null if config missing
     */
    private String loadTroopTypeConfiguration() {
        String troopType = profile.getConfig(
                EnumConfigurationKey.EXPERT_ROMULUS_TROOPS_TYPE_STRING,
                String.class);

        if (troopType == null || troopType.trim().isEmpty()) {
            logError("Troop type configuration is missing. Please set " +
                    EnumConfigurationKey.EXPERT_ROMULUS_TROOPS_TYPE_STRING.name());
            rescheduleForInvalidConfig();
            return null;
        }

        logDebug("Configured troop type: " + troopType);
        return troopType;
    }

    /**
     * Converts troop type string to corresponding template enum.
     * 
     * <p>
     * Valid troop types: Infantry, Lancer, Marksman
     * 
     * @param troopType The troop type string from configuration
     * @return The template enum for the troop building, or null if invalid
     */
    private EnumTemplates getTroopTemplate(String troopType) {
        EnumTemplates template;

        switch (troopType) {
            case "Infantry":
                template = EnumTemplates.GAME_HOME_SHORTCUTS_INFANTRY;
                break;
            case "Lancer":
                template = EnumTemplates.GAME_HOME_SHORTCUTS_LANCER;
                break;
            case "Marksman":
                template = EnumTemplates.GAME_HOME_SHORTCUTS_MARKSMAN;
                break;
            default:
                logError("Invalid troop type in configuration: '" + troopType + "'. " +
                        "Valid options: Infantry, Lancer, Marksman. " +
                        "Please check your profile settings.");
                rescheduleForInvalidConfig();
                return null;
        }

        return template;
    }

    // ========================================================================
    // NAVIGATION METHODS
    // ========================================================================

    /**
     * Navigates to the selected troop building via left menu shortcut.
     * 
     * <p>
     * <b>Navigation Steps:</b>
     * <ol>
     * <li>Open left menu (City section)</li>
     * <li>Search for troop building shortcut</li>
     * <li>Tap shortcut to move camera to building</li>
     * <li>Wait for camera movement to complete</li>
     * </ol>
     * 
     * <p>
     * After this navigation, a small overlay appears that must be cleared
     * with the back button before the claim button becomes visible.
     * 
     * @param troopTemplate The template for the troop building to navigate to
     * @param troopType     The troop type name (for logging)
     * @return true if navigation succeeded, false if shortcut not found
     */
    private boolean navigateToTroopBuilding(EnumTemplates troopTemplate, String troopType) {
        logDebug("Navigating to " + troopType + " building");

        // Open left menu on city section
        marchHelper.openLeftMenuCitySection(true);

        // Search for troop building shortcut
        DTOImageSearchResult troopChoice = templateSearchHelper.searchTemplate(
                troopTemplate,
                TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(3)
                        .withThreshold(90)
                        .withDelay(300L)
                        .build());

        if (!troopChoice.isFound()) {
            logWarning(troopType + " building shortcut not found in left menu");
            rescheduleForNavigationRetry();
            marchHelper.closeLeftMenu();
            return false;
        }

        logDebug("Opening " + troopType + " building view");
        tapPoint(troopChoice.getPoint());
        sleepTask(1000); // Wait for camera to move to building

        return true;
    }

    /**
     * Clears the navigation overlay that appears after moving to a building.
     * 
     * <p>
     * When tapping a building shortcut, the game displays a small overlay
     * that obscures the Romulus claim button. Tapping the back button removes
     * this overlay without changing the camera position, making the claim
     * button visible and interactable.
     */
    private void clearNavigationOverlay() {
        logDebug("Clearing navigation overlay");
        tapBackButton();
        sleepTask(500); // Wait for overlay to clear
    }

    // ========================================================================
    // CLAIMING LOGIC
    // ========================================================================

    /**
     * Attempts to find and claim troops from Romulus claim button.
     * 
     * <p>
     * Uses predefined search configuration that:
     * <ul>
     * <li>Restricts search to specific screen area (avoids wrong building)</li>
     * <li>Handles multiple retry attempts (10 total)</li>
     * <li>Uses lower match threshold (80%) for animation tolerance</li>
     * <li>Includes delays between attempts for UI stabilization</li>
     * </ul>
     * 
     * <p>
     * <b>Why restrict search area?</b>
     * The game has three troop buildings on screen, and each can have a
     * Romulus claim button above it. Without restricting the search area,
     * template matching might find and click the claim button for a different
     * troop building than the user configured.
     * 
     * @param troopType The troop type name (for logging)
     * @return true if claim button was found and tapped, false otherwise
     */
    private boolean attemptToClaimRomulusTroops(String troopType) {
        logDebug("Searching for Romulus claim button for " + troopType);

        DTOImageSearchResult claimButton = templateSearchHelper.searchTemplate(
                EnumTemplates.ROMULUS_CLAIM_TROOPS_BUTTON,
                ROMULUS_TROOPS_CLAIM_CONFIG);

        if (!claimButton.isFound()) {
            return false;
        }

        logInfo("Romulus claim button found - claiming " + troopType + " troops");
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
     * <li>Success: Troops claimed, wait for next daily reset</li>
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
     * Used when troop building shortcut is not found in left menu.
     * This typically indicates a temporary UI issue.
     */
    private void rescheduleForNavigationRetry() {
        LocalDateTime retryTime = LocalDateTime.now().plusMinutes(NAVIGATION_RETRY_MINUTES);
        reschedule(retryTime);
        logWarning("Navigation failed. Retrying at " + retryTime.format(TIME_FORMATTER));
    }

    /**
     * Reschedules the task for invalid configuration retry (1 hour).
     * 
     * <p>
     * Used when troop type configuration is missing or invalid.
     * The longer delay gives the user time to notice the error and
     * update their profile configuration.
     */
    private void rescheduleForInvalidConfig() {
        LocalDateTime retryTime = LocalDateTime.now().plusHours(INVALID_CONFIG_RETRY_HOURS);
        reschedule(retryTime);
        logWarning("Invalid configuration. Retrying at " + retryTime.format(TIME_FORMATTER));
    }
}