package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

/**
 * Task responsible for enabling auto-join for Alliance Wars.
 * Configures the number of queues and troop selection based on user preferences.
 * Auto-join must be renewed approximately every 8 hours.
 */
public class AllianceAutojoinTask extends DelayedTask {

	public AllianceAutojoinTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}
	
	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

	@Override
	protected void execute() {
		logInfo("Starting Alliance auto-join task");

		// Navigate to the alliance screen
		if (!navigateToAllianceScreen()) {
			rescheduleAndExit("Failed to navigate to Alliance screen");
			return;
		}

		// Locate the Alliance War button
		DTOImageSearchResult menuResult = emuManager.searchTemplate(EMULATOR_NUMBER,
				EnumTemplates.ALLIANCE_WAR_BUTTON, 90);
		if (!menuResult.isFound()) {
			logError("Alliance War button not found");
			rescheduleAndExit("Failed to locate Alliance War button");
			return;
		}

		// Open the Alliance War menu
		logDebug("Opening Alliance War menu");
		emuManager.tapAtPoint(EMULATOR_NUMBER, menuResult.getPoint());
		sleepTask(1000);

		// Open the auto-join menu
		logDebug("Opening auto-join settings");
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(260, 1200), new DTOPoint(450, 1240));
		sleepTask(1500); // Increased delay to ensure menu fully loads
		
		// Select troop configuration based on user preference
		boolean useAllTroops = profile.getConfig(EnumConfigurationKey.ALLIANCE_AUTOJOIN_USE_ALL_TROOPS_BOOL, Boolean.class);
		if (useAllTroops) {
			logInfo("Configuring auto-join with all troops option");
			emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(98, 376));
		} else {
			logInfo("Configuring auto-join with specific formation");
			emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(98, 442));
		}
		sleepTask(700); // Increased delay to ensure selection is registered

		// Reset the queue counter to zero first
		logDebug("Resetting queue counter");
		emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(430, 600), new DTOPoint(40, 600));
		sleepTask(300);

		// Set the queue limit based on user configuration
		int queueCount = profile.getConfig(EnumConfigurationKey.ALLIANCE_AUTOJOIN_QUEUES_INT, Integer.class);
		logInfo("Setting auto-join queue count to " + queueCount);
		// Tap (queue count - 1) times to set the desired number of queues
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(460, 590), new DTOPoint(497, 610), 
				(queueCount - 1), 400);
        sleepTask(300);

		// Enable the auto-join feature
		logInfo("Enabling auto-join");
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(380, 1070), new DTOPoint(640, 1120));
        sleepTask(500); // Increased delay to ensure activation registers
		
		// Return to home screen
		logDebug("Returning to home screen");
		tapBackButton();
		sleepTask(300);
		tapBackButton();
		sleepTask(300);
		tapBackButton();
		
		// Schedule next run based on successful completion
		scheduleNextRun();
	}

	/**
	 * Navigates to the Alliance screen from the home screen
	 * @return true if navigation was successful, false otherwise
	 */
	private boolean navigateToAllianceScreen() {
		logDebug("Navigating to Alliance screen");
		// Tap on Alliance button at bottom of screen
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(493, 1187), new DTOPoint(561, 1240));
		sleepTask(3000);
		
		// Verify we're on the Alliance screen by checking for the Alliance chest button
		DTOImageSearchResult allianceVerification = emuManager.searchTemplate(EMULATOR_NUMBER, 
				EnumTemplates.ALLIANCE_CHEST_BUTTON, 90);
				
		if (!allianceVerification.isFound()) {
			logError("Failed to verify Alliance screen - Alliance chest button not found");
			return false;
		}
		
		logDebug("Successfully navigated to Alliance screen");
		return true;
	}

	/**
	 * Handles task failure by logging a warning and rescheduling with a short retry interval
	 * @param reason The reason for the failure
	 */
	private void rescheduleAndExit(String reason) {
		logWarning(reason);
		
		// Attempt to return to home screen in case we're in a menu
		try {
			tapBackButton();
			sleepTask(300);
			tapBackButton();
			sleepTask(300);
		} catch (Exception e) {
			logError("Error while trying to return to home screen: " + e.getMessage());
		}
		
		// Short retry interval for failures
		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(15);
		this.reschedule(retryTime);
		logInfo("Task failed - rescheduled to retry in 15 minutes");
	}
	
	/**
	 * Schedules the next execution of this task after successful completion
	 * Alliance auto-join expires after 8 hours, so we run slightly before that
	 */
	private void scheduleNextRun() {
		// Schedule for 7 hours and 50 minutes later (10 minutes before expiration)
		LocalDateTime nextExecutionTime = LocalDateTime.now().plusHours(7).plusMinutes(50);
		this.reschedule(nextExecutionTime);
		logInfo("Alliance auto-join task completed successfully. Next execution in 7h 50m.");
	}

}
