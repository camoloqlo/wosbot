package cl.camodev.wosbot.serv.task.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.AllianceChampionshipHelper;
import cl.camodev.wosbot.serv.task.helper.NavigationHelper.EventMenu;
import cl.camodev.wosbot.serv.task.helper.TimeWindowHelper;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

import static cl.camodev.wosbot.console.enumerable.EnumTemplates.*;

/**
 * Task responsible for managing Alliance Championship participation.
 * 
 * <p>
 * This task handles automated troop deployment for the weekly Alliance
 * Championship event:
 * <ul>
 * <li>Verifies execution within valid time window (Monday 00:01 - Tuesday 22:55
 * UTC)</li>
 * <li>Navigates to Alliance Championship event screen</li>
 * <li>Deploys troops with configurable percentages (Infantry, Lancers,
 * Marksmen)</li>
 * <li>Can override existing deployments if configured</li>
 * <li>Supports three deployment positions (Left, Center, Right)</li>
 * </ul>
 * 
 * <p>
 * <b>Execution Window:</b>
 * <ul>
 * <li>Starts: Monday 00:01 UTC</li>
 * <li>Ends: Tuesday 22:55 UTC</li>
 * <li>Duration: ~47 hours per week</li>
 * <li>Repeats: Weekly</li>
 * </ul>
 * 
 * <p>
 * <b>Configuration:</b>
 * <ul>
 * <li>Override existing deployment (boolean)</li>
 * <li>Infantry percentage (default: 50%)</li>
 * <li>Lancers percentage (default: 20%)</li>
 * <li>Marksmen percentage (default: 30%)</li>
 * <li>Deployment position: LEFT, CENTER, RIGHT (default: CENTER)</li>
 * </ul>
 * 
 * <p>
 * <b>Rescheduling:</b>
 * <ul>
 * <li>Success: Reschedules to next window (next Monday)</li>
 * <li>Failure: Retries in 5 minutes</li>
 * <li>Outside window: Reschedules to next window start</li>
 * </ul>
 */
public class AllianceChampionshipTask extends DelayedTask {

    // ========== UI Navigation Constants ==========

    // ========== Troop Input Field Coordinates ==========
    private static final DTOPoint INFANTRY_INPUT_TL = new DTOPoint(583, 519);
    private static final DTOPoint INFANTRY_INPUT_BR = new DTOPoint(603, 531);
    private static final DTOPoint LANCERS_INPUT_TL = new DTOPoint(583, 666);
    private static final DTOPoint LANCERS_INPUT_BR = new DTOPoint(603, 685);
    private static final DTOPoint MARKSMEN_INPUT_TL = new DTOPoint(583, 815);
    private static final DTOPoint MARKSMEN_INPUT_BR = new DTOPoint(603, 829);

    // ========== Deployment UI Button Coordinates ==========
    private static final DTOPoint BALANCE_BUTTON_TL = new DTOPoint(308, 1170);
    private static final DTOPoint BALANCE_BUTTON_BR = new DTOPoint(336, 1209);
    private static final DTOPoint CONFIRM_TROOPS_BUTTON_TL = new DTOPoint(304, 965);
    private static final DTOPoint CONFIRM_TROOPS_BUTTON_BR = new DTOPoint(423, 996);
    private static final DTOPoint DEPLOY_BUTTON_TL = new DTOPoint(500, 1200);
    private static final DTOPoint DEPLOY_BUTTON_BR = new DTOPoint(600, 1230);

    // ========== Retry and Timing Constants ==========
    private static final int TEMPLATE_SEARCH_RETRIES = 3;
    private static final int TEXT_CLEAR_BACKSPACE_COUNT = 4;
    private static final int NAVIGATION_FAILURE_RETRY_MINUTES = 5;

    // ========== Default Configuration Values ==========
    private static final boolean DEFAULT_OVERRIDE_DEPLOY = false;
    private static final int DEFAULT_INFANTRY_PERCENTAGE = 50;
    private static final int DEFAULT_LANCERS_PERCENTAGE = 20;
    private static final int DEFAULT_MARKSMEN_PERCENTAGE = 30;
    private static final DeploymentPosition DEFAULT_POSITION = DeploymentPosition.CENTER;

    // ========== Configuration (loaded in loadConfiguration()) ==========
    private boolean overrideDeploy;
    private int infantryPercentage;
    private int lancersPercentage;
    private int markmenPercentage;
    private DeploymentPosition position;

    /**
     * Constructs a new AllianceChampionshipTask.
     *
     * @param profile the profile this task belongs to
     * @param tpTask  the task type enum
     */
    public AllianceChampionshipTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    /**
     * Loads task configuration from the profile.
     * This must be called from execute() to ensure configuration is current.
     * 
     * <p>
     * Loads:
     * <ul>
     * <li>Override deploy flag (default: false)</li>
     * <li>Infantry percentage (default: 50%)</li>
     * <li>Lancers percentage (default: 20%)</li>
     * <li>Marksmen percentage (default: 30%)</li>
     * <li>Deployment position (default: CENTER)</li>
     * </ul>
     * 
     * <p>
     * All configuration values have sensible defaults if not set.
     */
    private void loadConfiguration() {
        this.overrideDeploy = getConfigBoolean(
                EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_OVERRIDE_DEPLOY_BOOL,
                DEFAULT_OVERRIDE_DEPLOY);

        this.infantryPercentage = getConfigInt(
                EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_INFANTRY_PERCENTAGE_INT,
                DEFAULT_INFANTRY_PERCENTAGE);

        this.lancersPercentage = getConfigInt(
                EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_LANCERS_PERCENTAGE_INT,
                DEFAULT_LANCERS_PERCENTAGE);

        this.markmenPercentage = getConfigInt(
                EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_MARKSMANS_PERCENTAGE_INT,
                DEFAULT_MARKSMEN_PERCENTAGE);

        String positionValue = getConfigString(
                EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_POSITION_STRING,
                DEFAULT_POSITION.getValue());
        this.position = DeploymentPosition.fromString(positionValue);

        logDebug(String.format(
                "Configuration loaded - Override: %s, Position: %s, Infantry: %d%%, Lancers: %d%%, Marksmen: %d%%",
                overrideDeploy, position, infantryPercentage, lancersPercentage, markmenPercentage));
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
        return (value != null) ? value : defaultValue;
    }

    /**
     * Main execution method for Alliance Championship deployment.
     * 
     * <p>
     * Flow:
     * <ol>
     * <li>Load current configuration</li>
     * <li>Verify execution within valid time window</li>
     * <li>Navigate to championship event</li>
     * <li>Check deployment status (new vs existing)</li>
     * <li>Deploy or update troops based on status</li>
     * <li>Reschedule for next window</li>
     * </ol>
     * 
     * <p>
     * If outside valid window, immediately reschedules for next window start.
     * If navigation fails, reschedules for retry in 5 minutes.
     */
    @Override
    protected void execute() {
        logInfo("Starting Alliance Championship task execution");

        if (!verifyExecutionWindow()) {
            // Outside window - reschedule for next window
            rescheduleNextWindow();
            return;
        }

        loadConfiguration();

        logWindowInformation();

        if (!navigateToChampionshipEvent()) {
            handleNavigationFailure("Failed to navigate to championship event");
            return;
        }

        DeploymentStatus status = checkDeploymentStatus();

        if (status == null) {
            handleNavigationFailure("Failed to determine deployment status");
            return;
        }

        handleDeploymentByStatus(status);
    }

    /**
     * Verifies that current time is within the valid execution window.
     * 
     * <p>
     * If outside window, logs warning and reschedules for next window.
     * 
     * @return true if inside valid window, false otherwise
     */
    private boolean verifyExecutionWindow() {
        if (!isInsideWindow()) {
            logWarning("Execute called OUTSIDE valid window. Rescheduling...");
            rescheduleNextWindow();
            return false;
        }

        logInfo("Confirmed: We are INSIDE a valid execution window");
        return true;
    }

    /**
     * Logs detailed information about the current execution window.
     */
    private void logWindowInformation() {
        TimeWindowHelper.WindowResult window = getWindowState();
        LocalDateTime windowStart = LocalDateTime.ofInstant(
                window.getCurrentWindowStart(),
                ZoneId.of("UTC"));
        LocalDateTime windowEnd = LocalDateTime.ofInstant(
                window.getCurrentWindowEnd(),
                ZoneId.of("UTC"));

        logInfo("Championship window: " + windowStart.format(DATETIME_FORMATTER) + " to "
                + windowEnd.format(DATETIME_FORMATTER) + " (UTC)");
    }

    /**
     * Navigates to the Alliance Championship event screen.
     * 
     * <p>
     * Uses the generic NavigationHelper.navigateToEventMenu() method.
     * 
     * @return true if navigation successful, false otherwise
     */
    private boolean navigateToChampionshipEvent() {
        logInfo("Navigating to Alliance Championship event...");

        boolean success = navigationHelper.navigateToEventMenu(EventMenu.ALLIANCE_CHAMPIONSHIP);

        if (!success) {
            logWarning("Failed to navigate to Alliance Championship event");
            return false;
        }

        sleepTask(2000);
        return true;
    }

    /**
     * Checks the current deployment status in the championship.
     * 
     * <p>
     * Possible statuses:
     * <ul>
     * <li>NEW_DEPLOYMENT: Register button found (no active deployment)</li>
     * <li>EXISTING_DEPLOYMENT: Troops button found (deployment already active)</li>
     * <li>null: Neither button found (error state)</li>
     * </ul>
     * 
     * @return DeploymentStatus enum or null if status cannot be determined
     */
    private DeploymentStatus checkDeploymentStatus() {
        DTOImageSearchResult troopsButton = templateSearchHelper.searchTemplate(
                ALLIANCE_CHAMPIONSHIP_TROOPS_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES)
                        .withDelay(200L)
                        .build());

        if (troopsButton.isFound()) {
            logInfo("Active deployment found");
            if (overrideDeploy) {
                tapRandomPoint(troopsButton.getPoint(), troopsButton.getPoint(), 3, 100);
                sleepTask(1000);
            }
            return DeploymentStatus.EXISTING_DEPLOYMENT;
        }

        DTOImageSearchResult registerButton = templateSearchHelper.searchTemplate(
                ALLIANCE_CHAMPIONSHIP_REGISTER_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES)
                        .withDelay(200L)
                        .build());

        if (registerButton.isFound()) {
            logInfo("No active deployment found");
            tapRandomPoint(registerButton.getPoint(), registerButton.getPoint(), 3, 100);
            sleepTask(1000);
            return DeploymentStatus.NEW_DEPLOYMENT;
        } else {
            logWarning("Neither Register nor Troops button found");
            return null;
        }

    }

    /**
     * Handles deployment based on current status and configuration.
     * 
     * <p>
     * Logic:
     * <ul>
     * <li>NEW_DEPLOYMENT: Always deploy new troops</li>
     * <li>EXISTING_DEPLOYMENT + override enabled: Update deployment</li>
     * <li>EXISTING_DEPLOYMENT + override disabled: Skip (already deployed)</li>
     * </ul>
     * 
     * @param status the current deployment status
     */
    private void handleDeploymentByStatus(DeploymentStatus status) {
        switch (status) {
            case NEW_DEPLOYMENT:
                logInfo("Proceeding with new deployment");
                if (deployTroops(false)) {
                    logInfo("Troops deployed successfully");
                    rescheduleNextWindow();
                } else {
                    handleNavigationFailure("Failed to deploy troops");
                }
                break;

            case EXISTING_DEPLOYMENT:
                if (overrideDeploy) {
                    logInfo("Override is enabled. Proceeding to update deployment");
                    if (deployTroops(true)) {
                        logInfo("Troops deployment updated successfully");
                        rescheduleNextWindow();
                    } else {
                        handleNavigationFailure("Failed to update deployment");
                    }
                } else {
                    logInfo("Override is disabled. Skipping deployment.");
                    rescheduleNextWindow();
                }
                break;
        }
    }

    /**
     * Deploys or updates troops with configured percentages.
     * 
     * <p>
     * Process:
     * <ol>
     * <li>Clear event tab selection</li>
     * <li>Select deployment position (Left/Center/Right)</li>
     * <li>Handle position switching if needed (update only)</li>
     * <li>Open deployment configuration</li>
     * <li>Configure troop percentages</li>
     * <li>Confirm and deploy</li>
     * </ol>
     * 
     * @param isUpdate true for updating existing deployment, false for new
     *                 deployment
     * @return true if deployment successful, false otherwise
     */
    private boolean deployTroops(boolean isUpdate) {
        navigationHelper.clearEventTabSelection();

        DTOArea deploymentArea = getDeploymentArea(position);
        tapRandomPoint(deploymentArea.topLeft(), deploymentArea.bottomRight(), 1, 500);
        sleepTask(500); // Wait for position selection

        if (isUpdate) {
            if (!handlePositionSwitching(deploymentArea)) {
                return false;
            }
        }

        if (!openDeploymentConfiguration(isUpdate)) {
            return false;
        }

        configureTroopPercentages();
        confirmDeployment();

        return true;
    }

    /**
     * Handles position switching for deployment updates.
     * 
     * <p>
     * If the "Switch Line" button is visible, it means we're in the wrong
     * position. Taps the button to switch, then re-selects the correct position.
     * 
     * @param deploymentArea the target deployment area coordinates
     * @return true if position handling successful, false otherwise
     */
    private boolean handlePositionSwitching(DTOArea deploymentArea) {
        DTOImageSearchResult switchLine = templateSearchHelper.searchTemplate(
                ALLIANCE_CHAMPIONSHIP_SWITCH_LINE_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES)
                        .withDelay(100L)
                        .build());

        if (switchLine.isFound()) {
            logInfo("Current deployment position does not match desired. Switching position.");
            tapPoint(switchLine.getPoint());
            sleepTask(1000); // Wait for position switch
            tapRandomPoint(deploymentArea.topLeft(), deploymentArea.bottomRight(), 1, 500);
            sleepTask(500); // Wait for position reselection
        } else {
            logInfo("Current deployment position matches desired. No position change needed.");
        }

        return true;
    }

    /**
     * Opens the deployment configuration screen.
     * 
     * @param isUpdate true if updating existing deployment, false for new
     *                 deployment
     * @return true if configuration screen opened, false otherwise
     */
    private boolean openDeploymentConfiguration(boolean isUpdate) {
        EnumTemplates buttonTemplate = isUpdate
                ? ALLIANCE_CHAMPIONSHIP_UPDATE_TROOPS_BUTTON
                : ALLIANCE_CHAMPIONSHIP_DISPATCH_TROOPS_BUTTON;

        String buttonName = isUpdate ? "Update" : "Dispatch";

        DTOImageSearchResult configButton = templateSearchHelper.searchTemplate(
                buttonTemplate,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES)
                        .withDelay(100L)
                        .build());

        if (!configButton.isFound()) {
            logWarning(buttonName + " button not found. Cannot proceed with deployment.");
            return false;
        }

        tapPoint(configButton.getPoint());
        sleepTask(200); // Wait for configuration screen

        tapBalanceButton();

        return true;
    }

    /**
     * Taps the balance button to enable manual troop percentage input.
     */
    private void tapBalanceButton() {
        tapRandomPoint(BALANCE_BUTTON_TL, BALANCE_BUTTON_BR, 1, 500);
        sleepTask(300); // Wait for balance mode to activate
    }

    /**
     * Configures troop percentages for all three troop types.
     * 
     * <p>
     * Process for each troop type:
     * <ol>
     * <li>Tap input field to focus</li>
     * <li>Clear existing value (backspace 4 times)</li>
     * <li>Enter new percentage value</li>
     * </ol>
     */
    private void configureTroopPercentages() {
        resetTroopInputs();
        setTroopPercentages();
    }

    /**
     * Resets all troop input fields to 0 by clearing them.
     */
    private void resetTroopInputs() {
        clearTroopInput(INFANTRY_INPUT_TL, INFANTRY_INPUT_BR, "Infantry");
        clearTroopInput(LANCERS_INPUT_TL, LANCERS_INPUT_BR, "Lancers");
        clearTroopInput(MARKSMEN_INPUT_TL, MARKSMEN_INPUT_BR, "Marksmen");
    }

    /**
     * Clears a single troop input field.
     * 
     * @param topLeft     top-left coordinate of input field
     * @param bottomRight bottom-right coordinate of input field
     * @param troopType   name of troop type for logging
     */
    private void clearTroopInput(DTOPoint topLeft, DTOPoint bottomRight, String troopType) {
        tapRandomPoint(topLeft, bottomRight, 1, 200);
        sleepTask(100); // Wait for field focus
        emuManager.clearText(EMULATOR_NUMBER, TEXT_CLEAR_BACKSPACE_COUNT);
        sleepTask(200); // Wait for clear to complete
        logDebug(troopType + " input cleared");
    }

    /**
     * Sets the configured percentage values for all troop types.
     */
    private void setTroopPercentages() {
        setTroopPercentage(INFANTRY_INPUT_TL, INFANTRY_INPUT_BR, infantryPercentage, "Infantry");
        setTroopPercentage(LANCERS_INPUT_TL, LANCERS_INPUT_BR, lancersPercentage, "Lancers");
        setTroopPercentage(MARKSMEN_INPUT_TL, MARKSMEN_INPUT_BR, markmenPercentage, "Marksmen");
    }

    /**
     * Sets the percentage value for a single troop type.
     * 
     * @param topLeft     top-left coordinate of input field
     * @param bottomRight bottom-right coordinate of input field
     * @param percentage  the percentage value to set
     * @param troopType   name of troop type for logging
     */
    private void setTroopPercentage(DTOPoint topLeft, DTOPoint bottomRight, int percentage, String troopType) {
        tapRandomPoint(topLeft, bottomRight, 1, 400);
        sleepTask(100); // Wait for field focus
        emuManager.writeText(EMULATOR_NUMBER, String.valueOf(percentage));
        sleepTask(200); // Wait for text input to complete
        logDebug(troopType + " percentage set to " + percentage + "%");
    }

    /**
     * Confirms the troop configuration and deploys.
     * 
     * <p>
     * Taps:
     * <ol>
     * <li>Confirm button (validates percentages)</li>
     * <li>Deploy button (finalizes deployment)</li>
     * </ol>
     */
    private void confirmDeployment() {
        tapRandomPoint(CONFIRM_TROOPS_BUTTON_TL, CONFIRM_TROOPS_BUTTON_BR, 1, 500);
        sleepTask(500); // Wait for confirmation

        tapRandomPoint(DEPLOY_BUTTON_TL, DEPLOY_BUTTON_BR, 1, 500);
        sleepTask(1000); // Wait for deployment to complete
    }

    /**
     * Handles navigation or deployment failures by rescheduling for retry.
     * 
     * @param context description of the failure for logging
     */
    private void handleNavigationFailure(String context) {
        logWarning(context + ". Retrying in " + NAVIGATION_FAILURE_RETRY_MINUTES + " minutes.");
        reschedule(LocalDateTime.now().plusMinutes(NAVIGATION_FAILURE_RETRY_MINUTES));
    }

    /**
     * Verifies if we're currently inside an execution window.
     *
     * @return true if inside valid window, false otherwise
     */
    private boolean isInsideWindow() {
        TimeWindowHelper.WindowResult result = AllianceChampionshipHelper.calculateWindow();
        return result.getState() == TimeWindowHelper.WindowState.INSIDE;
    }

    /**
     * Gets detailed information about the current window.
     *
     * @return Window state with start, end, and next window times
     */
    private TimeWindowHelper.WindowResult getWindowState() {
        return AllianceChampionshipHelper.calculateWindow();
    }

    /**
     * Reschedules the task for the next execution window.
     * 
     * <p>
     * Always schedules for the next window start (next Monday 00:01 UTC),
     * regardless of whether currently inside or outside the window.
     */
    private void rescheduleNextWindow() {
        TimeWindowHelper.WindowResult result = getWindowState();
        Instant nextExecutionInstant = result.getNextWindowStart();

        LocalDateTime nextExecutionLocal = LocalDateTime.ofInstant(
                nextExecutionInstant,
                ZoneId.systemDefault());

        LocalDateTime nextExecutionUtc = LocalDateTime.ofInstant(
                nextExecutionInstant,
                ZoneId.of("UTC"));

        logInfo("Rescheduling Alliance Championship for (UTC): " + nextExecutionUtc);
        logInfo("Rescheduling Alliance Championship for (Local): " + nextExecutionLocal);

        reschedule(nextExecutionLocal);
    }

    /**
     * Gets the screen coordinates for a deployment position.
     * 
     * @param pos the deployment position (LEFT, CENTER, RIGHT)
     * @return DTOArea containing the tap region for that position
     */
    private DTOArea getDeploymentArea(DeploymentPosition pos) {
        return switch (pos) {
            case LEFT -> new DTOArea(new DTOPoint(40, 900), new DTOPoint(220, 1000));
            case CENTER -> new DTOArea(new DTOPoint(290, 900), new DTOPoint(450, 1000));
            case RIGHT -> new DTOArea(new DTOPoint(510, 900), new DTOPoint(680, 1000));
        };
    }

    /**
     * Specifies that this task can start from any screen location.
     * The task will handle navigation to the championship event internally.
     * 
     * @return EnumStartLocation.ANY
     */
    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.ANY;
    }

    /**
     * Indicates that this task does not provide daily mission progress.
     * 
     * @return false
     */
    @Override
    public boolean provideDailyMissionProgress() {
        return false;
    }

    /**
     * Indicates that this task does not consume stamina.
     * 
     * @return false
     */
    @Override
    protected boolean consumesStamina() {
        return false;
    }

    /**
     * Enum representing the current deployment status in Alliance Championship.
     */
    private enum DeploymentStatus {
        /** No active deployment (Register button visible) */
        NEW_DEPLOYMENT,

        /** Existing deployment active (Troops button visible) */
        EXISTING_DEPLOYMENT
    }

    /**
     * Enum representing the deployment position in Alliance Championship.
     * 
     * <p>
     * Three positions are available on the battlefield:
     * <ul>
     * <li>LEFT: Left flank position</li>
     * <li>CENTER: Center position (default)</li>
     * <li>RIGHT: Right flank position</li>
     * </ul>
     */
    public enum DeploymentPosition {
        /** Left flank deployment position */
        LEFT("LEFT"),

        /** Center deployment position (default) */
        CENTER("CENTER"),

        /** Right flank deployment position */
        RIGHT("RIGHT");

        private final String value;

        /**
         * Constructs a DeploymentPosition with a string value.
         * 
         * @param value the string representation of the position
         */
        DeploymentPosition(String value) {
            this.value = value;
        }

        /**
         * Parses a string value to DeploymentPosition enum.
         * 
         * <p>
         * Returns CENTER as default if value is null, empty, or invalid.
         * Case-insensitive matching is used.
         * 
         * @param value String value from configuration
         * @return Corresponding DeploymentPosition enum, or CENTER if invalid
         */
        public static DeploymentPosition fromString(String value) {
            if (value == null || value.trim().isEmpty()) {
                return CENTER;
            }

            for (DeploymentPosition position : DeploymentPosition.values()) {
                if (position.value.equalsIgnoreCase(value.trim())) {
                    return position;
                }
            }

            return CENTER;
        }

        /**
         * Gets the string value of this position.
         * 
         * @return the string representation
         */
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}