package cl.camodev.wosbot.serv.task.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import cl.camodev.utiles.AllianceChampionshipHelper;
import cl.camodev.utiles.TimeWindowHelper;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

import static cl.camodev.wosbot.console.enumerable.EnumTemplates.*;

/**
 * Task responsible for managing Alliance Championship participation.
 * Handles troop deployment with configurable percentages for Infantry, Lancers, and Marksmans.
 * Can override current deployment if configured.
 *
 * Execution Window:
 * - Starts: Monday 00:01 UTC
 * - Ends: Wednesday 22:55 UTC
 * - Duration: ~71 hours per week
 * - Repeats weekly
 */
public class AllianceChampionshipTask extends DelayedTask {


    boolean overrideDeploy;
    int infantryPercentage;
    int lancersPercentage;
    int marksmansPercentage;
    String positionValue;
    DeploymentPosition position;

	public AllianceChampionshipTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
        updateConfiguration();
		// Calculate initial execution time based on window state
		TimeWindowHelper.WindowResult result = AllianceChampionshipHelper.calculateWindow();
		Instant nextExecutionInstant;

		switch (result.getState()) {
			case BEFORE:
				// If we're before the window, execute at window start
				nextExecutionInstant = result.getCurrentWindowStart();
				logInfo("Current time is BEFORE window. Next execution (UTC): " +
					LocalDateTime.ofInstant(nextExecutionInstant, ZoneId.of("UTC")));
				break;

			case INSIDE:
				// If we're inside the window, execute NOW
				nextExecutionInstant = Instant.now();
				logInfo("Current time is INSIDE window. Executing NOW");
				break;

			case AFTER:
				// If we're after the window, use next window start
				nextExecutionInstant = result.getNextWindowStart();
				logInfo("Current time is AFTER window. Next execution (UTC): " +
					LocalDateTime.ofInstant(nextExecutionInstant, ZoneId.of("UTC")));
				break;

			default:
				throw new IllegalStateException("Unrecognized window state");
		}

		// Convert from UTC Instant to local system time for scheduling
		LocalDateTime nextExecutionLocal = LocalDateTime.ofInstant(
			nextExecutionInstant,
			ZoneId.systemDefault()
		);

		logInfo("Alliance Championship scheduled for (Local): " + nextExecutionLocal);
		this.reschedule(nextExecutionLocal);
	}

	@Override
	protected void execute() {
		logInfo("Starting Alliance Championship task execution");
        updateConfiguration();
		// Verify we're inside a valid window
		if (!isInsideWindow()) {
			logWarning("Execute called OUTSIDE valid window. Rescheduling...");
			rescheduleNextWindow();
			return;
		}

		logInfo("Confirmed: We are INSIDE a valid execution window");

		// Get window information
		TimeWindowHelper.WindowResult window = getWindowState();
		LocalDateTime windowStart = LocalDateTime.ofInstant(
			window.getCurrentWindowStart(),
			ZoneId.of("UTC")
		);
		LocalDateTime windowEnd = LocalDateTime.ofInstant(
			window.getCurrentWindowEnd(),
			ZoneId.of("UTC")
		);

		logInfo("Championship window: " + windowStart + " to " + windowEnd + " (UTC)");

		logInfo(String.format("Configuration - Override: %s, Position: %s, Infantry: %d%%, Lancers: %d%%, Marksmans: %d%%",
			overrideDeploy, position, infantryPercentage, lancersPercentage, marksmansPercentage));

		// navigate to Alliance Championship screen
		DTOImageSearchResult dealsButton = searchTemplateWithRetries(HOME_EVENTS_BUTTON, 90,5,200);

		if (!dealsButton.isFound()){
			logInfo("Deals button not found");
			return;
		}
		tapPoint(dealsButton.getPoint());
		sleepTask(1000);
		tapRandomPoint(new DTOPoint(300,30), new DTOPoint(400,50), 5,500);
		swipe(new DTOPoint(130,140), new DTOPoint(640,140));

		//search for event
		DTOImageSearchResult result=null;
		int attempts = 0;
		while (attempts < 5) {
			result = searchTemplateWithRetries(ALLIANCE_CHAMPIONSHIP_TAB, 90, 1);

			if (result.isFound()) {
				tapPoint(result.getPoint());
				sleepTask(1000);
				logInfo("Successfully navigated to Alliance Championship event");
				break;
			}

			logInfo("Alliance Championship tab not found, retrying...");
			swipe(new DTOPoint(630, 143), new DTOPoint(500, 128));
			sleepTask(200);
			attempts++;
		}

		if (!result.isFound()){
			logWarning("Failed to find Alliance Championship tab after multiple attempts. Exiting task.");
			return;
		}

		//enter event
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER,result.getPoint(),result.getPoint(),5,300);

		//check if there's a active deployment searching the register button
		DTOImageSearchResult troopsButton = searchTemplateWithRetries(ALLIANCE_CHAMPIONSHIP_TROOPS_BUTTON, 90,5,200);
        DTOImageSearchResult registerButton = searchTemplateWithRetries(ALLIANCE_CHAMPIONSHIP_REGISTER_BUTTON, 90,5,200);


        //if register is found i should do a regular deployment
        //if troops is found i should check if i need to override

        if (registerButton.isFound()){
        	logInfo("No active deployment found, proceeding with new deployment");
        	tapPoint(registerButton.getPoint());
        	sleepTask(1000);
            handleNewDeployment();
            rescheduleNextWindow();
            return;
        } else if (troopsButton.isFound() && overrideDeploy){
        	logInfo("Active deployment found, but override is enabled. Proceeding to redeploy troops");
        	tapPoint(troopsButton.getPoint());
        	sleepTask(1000);
            handleUpdateDeployment();
            rescheduleNextWindow();
            return;
        } else if (troopsButton.isFound() && !overrideDeploy){
        	logInfo("Active deployment found and override is disabled. Skipping deployment.");
        	rescheduleNextWindow();
        	return;
        } else {
        	logWarning("Neither Register nor Troops button found. Cannot proceed with deployment. Exiting task.");
        	return;
        }

	}

    private void handleUpdateDeployment() {
        tapRandomPoint(new DTOPoint(300,30), new DTOPoint(400,50), 5,500);
        // 1.- check if im alredy in the desired position
        DTOArea deploymentArea = getDeploymentArea(position);
        tapRandomPoint(deploymentArea.topLeft(), deploymentArea.bottomRight(), 1,500);

        // search the switch line icon, if found in other position i need to change after modify the deployment

        DTOImageSearchResult switchLine = searchTemplateWithRetries(ALLIANCE_CHAMPIONSHIP_SWITCH_LINE_BUTTON, 90,5,100);
        if (switchLine.isFound()){
            logInfo("Current deployment position does not match desired. Switching position.");
            tapPoint(switchLine.getPoint());
            sleepTask(1000);
            tapRandomPoint(deploymentArea.topLeft(), deploymentArea.bottomRight(), 1,500);
        }else{
            logInfo("Current deployment position matches desired. No position change needed.");
        }
        // 2.- if im in the desired position search the deployment button, and update the % of troops

        DTOImageSearchResult updateButton = searchTemplateWithRetries(ALLIANCE_CHAMPIONSHIP_UPDATE_TROOPS_BUTTON, 90,5,100);

        if (!updateButton.isFound()){
            logWarning("Update button not found. Cannot proceed with deployment update. Exiting task.");
            return;
        }

        tapPoint(updateButton.getPoint());
        sleepTask(200);

        tapRandomPoint(new DTOPoint(308,1170), new DTOPoint(336,1209), 1,500); // balance button maybe i need to switch it o template?

        //Davi, do ur IA things here please, this sucks

        //reset all to 0
        tapRandomPoint(new DTOPoint(583,519), new DTOPoint(603,531), 1,200); //infantry
        emuManager.clearText(EMULATOR_NUMBER,4);

        tapRandomPoint(new DTOPoint(583,666), new DTOPoint(603,685), 1,200); //lancers
        emuManager.clearText(EMULATOR_NUMBER,4);

        tapRandomPoint(new DTOPoint(583,815), new DTOPoint(603,829), 1,200); //marksmans
        emuManager.clearText(EMULATOR_NUMBER,4);

        //set new values

        tapRandomPoint(new DTOPoint(583,519), new DTOPoint(603,531), 1,400); //infantry
        emuManager.writeText(EMULATOR_NUMBER, String.valueOf(infantryPercentage));
        sleepTask(200);
        tapRandomPoint(new DTOPoint(583,666), new DTOPoint(603,685), 1,400); //lancers
        emuManager.writeText(EMULATOR_NUMBER, String.valueOf(lancersPercentage));
        sleepTask(200);
        tapRandomPoint(new DTOPoint(583,815), new DTOPoint(603,829), 1,400); //marksmans
        emuManager.writeText(EMULATOR_NUMBER, String.valueOf(marksmansPercentage));
        sleepTask(200);
        //confirm troops %
        tapRandomPoint(new DTOPoint(304,965), new DTOPoint(423,996), 1,500); //confirm button

        //3.- confirm the deployment
        tapRandomPoint(new DTOPoint(500,1200), new DTOPoint(600,1230), 1,500); // deploy button

        logInfo("Troops deployment updated successfully. Task will be scheduled for the next window.");
    }

    private void handleNewDeployment() {
        tapRandomPoint(new DTOPoint(300,30), new DTOPoint(400,50), 5,500);
        // 1.- check if im alredy in the desired position
        DTOArea deploymentArea = getDeploymentArea(position);
        tapRandomPoint(deploymentArea.topLeft(), deploymentArea.bottomRight(), 1,500);

        DTOImageSearchResult dispatchButton = searchTemplateWithRetries(ALLIANCE_CHAMPIONSHIP_DISPATCH_TROOPS_BUTTON, 90,5,100);

        if (!dispatchButton.isFound()){
            logWarning("Dispatch button not found. Cannot proceed with new deployment. Exiting task.");
            return;
        }

        tapPoint(dispatchButton.getPoint());
        sleepTask(200);

        tapRandomPoint(new DTOPoint(308,1170), new DTOPoint(336,1209), 1,500); // balance button maybe i need to switch it o template?

        //reset all to 0
        tapRandomPoint(new DTOPoint(583,519), new DTOPoint(603,531), 1,200); //infantry
        emuManager.clearText(EMULATOR_NUMBER,4);
        sleepTask(200);
        tapRandomPoint(new DTOPoint(583,666), new DTOPoint(603,685), 1,200); //lancers
        emuManager.clearText(EMULATOR_NUMBER,4);
        sleepTask(200);
        tapRandomPoint(new DTOPoint(583,815), new DTOPoint(603,829), 1,200); //marksmans
        emuManager.clearText(EMULATOR_NUMBER,4);
        sleepTask(200);
        //set new values

        tapRandomPoint(new DTOPoint(583,519), new DTOPoint(603,531), 1,400); //infantry
        emuManager.writeText(EMULATOR_NUMBER, String.valueOf(infantryPercentage));

        tapRandomPoint(new DTOPoint(583,666), new DTOPoint(603,685), 1,400); //lancers
        emuManager.writeText(EMULATOR_NUMBER, String.valueOf(lancersPercentage));

        tapRandomPoint(new DTOPoint(583,815), new DTOPoint(603,829), 1,400); //marksmans
        emuManager.writeText(EMULATOR_NUMBER, String.valueOf(marksmansPercentage));

        //confirm troops %
        tapRandomPoint(new DTOPoint(304,965), new DTOPoint(423,996), 1,500); //confirm button

        //3.- confirm the deployment
        tapRandomPoint(new DTOPoint(500,1200), new DTOPoint(600,1230), 1,500); // deploy button

        logInfo("Troops deployed successfully. Task will be scheduled for the next window.");

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
	 * If currently INSIDE window, reschedules for next Monday.
	 * If BEFORE or AFTER, reschedules for upcoming window start.
	 */
	private void rescheduleNextWindow() {
		TimeWindowHelper.WindowResult result = getWindowState();

		Instant nextExecutionInstant;
		if (result.getState() == TimeWindowHelper.WindowState.INSIDE) {
			// If still inside, schedule for next week's window
			nextExecutionInstant = result.getNextWindowStart();
		} else {
			// If outside, schedule for next window start
			nextExecutionInstant = result.getNextWindowStart();
		}

		// Convert from UTC Instant to local system time
		LocalDateTime nextExecutionLocal = LocalDateTime.ofInstant(
			nextExecutionInstant,
			ZoneId.systemDefault()
		);

		LocalDateTime nextExecutionUtc = LocalDateTime.ofInstant(
			nextExecutionInstant,
			ZoneId.of("UTC")
		);

		logInfo("Rescheduling Alliance Championship for (UTC): " + nextExecutionUtc);
		logInfo("Rescheduling Alliance Championship for (Local): " + nextExecutionLocal);

		this.reschedule(nextExecutionLocal);
	}

    private void updateConfiguration() {
        overrideDeploy = profile.getConfig(EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_OVERRIDE_DEPLOY_BOOL, Boolean.class);
        infantryPercentage = profile.getConfig(EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_INFANTRY_PERCENTAGE_INT, Integer.class);
        lancersPercentage = profile.getConfig(EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_LANCERS_PERCENTAGE_INT, Integer.class);
        marksmansPercentage = profile.getConfig(EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_MARKSMANS_PERCENTAGE_INT, Integer.class);
        positionValue = profile.getConfig(EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_POSITION_STRING, String.class);
        position = DeploymentPosition.fromString(positionValue);
    }

    public DTOArea getDeploymentArea(DeploymentPosition pos) {
        return switch (pos) {
            case LEFT -> new DTOArea(new DTOPoint(40, 900), new DTOPoint(220, 1000));
            case CENTER -> new DTOArea(new DTOPoint(290, 900), new DTOPoint(450, 1000));
            case RIGHT -> new DTOArea(new DTOPoint(510, 900), new DTOPoint(680, 1000));
        };
    }

	/**
	 * Enum representing the deployment position in Alliance Championship.
	 */
	public enum DeploymentPosition {
		LEFT("LEFT"),
		CENTER("CENTER"),
		RIGHT("RIGHT");

		private final String value;

		DeploymentPosition(String value) {
			this.value = value;
		}

		/**
		 * Parse a string value to DeploymentPosition enum.
		 * Returns CENTER as default if value is null, empty, or invalid.
		 *
		 * @param value String value from configuration
		 * @return Corresponding DeploymentPosition enum
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

			// Default to CENTER if invalid value
			return CENTER;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return value;
		}
	}

}

