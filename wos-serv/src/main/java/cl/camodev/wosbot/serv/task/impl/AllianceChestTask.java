package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

public class AllianceChestTask extends DelayedTask {

	// Wait times
	private static final int TAB_CHANGE_WAIT_TIME = 500;
	private static final int CLAIM_WAIT_TIME = 1500;
	private static final int SHORT_WAIT_TIME = 300;

	public AllianceChestTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	protected void execute() {
		logInfo("Starting alliance chest collection task.");

		if (!navigateToAllianceScreen()) {
			rescheduleAndExit("Failed to navigate to alliance screen");
			return;
		}

		if (!openAllianceChestScreen()) {
			rescheduleAndExit("Failed to open alliance chest screen");
			return;
		}

		// Collect from all available sections
		collectLootChests();
		collectAllianceGifts();
		collectHonorChest();

		// Return to home screen
		returnToHomeScreen();

		// Reschedule task
		scheduleNextRun();
	}

	/**
	 * Navigates to the alliance screen from the home screen
	 * 
	 * @return true if navigation was successful, false otherwise
	 */
	private boolean navigateToAllianceScreen() {
		logInfo("Navigating to alliance screen");
		tapRandomPoint(new DTOPoint(493, 1187), new DTOPoint(561, 1240));
		sleepTask(3000);

		// Verify we're on the alliance screen by checking for the alliance chest button
		DTOImageSearchResult allianceVerification = templateSearchHelper.searchTemplate(
				EnumTemplates.ALLIANCE_CHEST_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);
		return allianceVerification.isFound();
	}

	/**
	 * Opens the alliance chest screen
	 * 
	 * @return true if successful, false otherwise
	 */
	private boolean openAllianceChestScreen() {
		DTOImageSearchResult allianceChestResult = templateSearchHelper.searchTemplate(
				EnumTemplates.ALLIANCE_CHEST_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);
		if (!allianceChestResult.isFound()) {
			logWarning("Alliance chest button not found.");
			return false;
		}

		tapPoint(allianceChestResult.getPoint());
		sleepTask(TAB_CHANGE_WAIT_TIME);
		return true;
	}

	/**
	 * Collects rewards from the loot chests section
	 */
	private void collectLootChests() {
		logInfo("Claiming loot chests.");
		tapRandomPoint(new DTOPoint(56, 375), new DTOPoint(320, 420));
		sleepTask(TAB_CHANGE_WAIT_TIME);

		// Tap "Claim All" button
		tapPoint(new DTOPoint(360, 1204));
		sleepTask(CLAIM_WAIT_TIME);

		// Close the result window if it appears
		closePopupIfPresent();
		sleepTask(SHORT_WAIT_TIME);
	}

	/**
	 * Collects rewards from the alliance gifts section
	 */
	private void collectAllianceGifts() {
		logInfo("Opening alliance gifts section.");
		tapRandomPoint(new DTOPoint(410, 375), new DTOPoint(626, 420));
		sleepTask(TAB_CHANGE_WAIT_TIME);

		// First try to use "Claim All" button
		DTOImageSearchResult claimAllButton = templateSearchHelper.searchTemplate(
				EnumTemplates.ALLIANCE_CHEST_CLAIM_ALL_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (claimAllButton.isFound()) {
			logInfo("'Claim All' button found. Claiming all gifts.");
			tapPoint(claimAllButton.getPoint());
			sleepTask(CLAIM_WAIT_TIME);

			// Close the result window
			closePopupIfPresent();
		} else {
			logInfo("No 'Claim All' button for gifts. Checking for individual gifts.");
			collectIndividualGifts();
		}
		sleepTask(SHORT_WAIT_TIME);
	}

	/**
	 * Collects individual gifts when "Claim All" button is not available.
	 * The game automatically scrolls to the next gift after claiming one.
	 */
	private void collectIndividualGifts() {
		int giftsClaimed = 0;
		int consecutiveFailures = 0;
		int maxConsecutiveFailures = 3; // Number of consecutive failures before giving up

		// Continue claiming gifts until no more are found
		while (consecutiveFailures < maxConsecutiveFailures) {
			DTOImageSearchResult claimButton = templateSearchHelper.searchTemplate(
					EnumTemplates.ALLIANCE_CHEST_CLAIM_BUTTON,
					SearchConfigConstants.DEFAULT_SINGLE);

			if (claimButton.isFound()) {
				logDebug("Claiming individual gift #" + (giftsClaimed + 1));
				tapPoint(claimButton.getPoint());
				sleepTask(CLAIM_WAIT_TIME);
				giftsClaimed++;
				consecutiveFailures = 0; // Reset failure counter on success

				// Close any popup that might appear
				closePopupIfPresent();
			} else {
				consecutiveFailures++;

				// If we've had multiple failures in a row, assume we're done
				if (consecutiveFailures >= maxConsecutiveFailures) {
					logDebug("No more individual gifts found.");
					break;
				}
			}
		}

		if (giftsClaimed > 0) {
			logInfo("Successfully claimed " + giftsClaimed + " individual gifts.");
		} else {
			logInfo("No individual gifts to claim.");
		}
	}

	/**
	 * Collects the honor chest if enabled
	 */
	private void collectHonorChest() {
		boolean honorChestEnabled = profile.getConfig(EnumConfigurationKey.ALLIANCE_HONOR_CHEST_BOOL, Boolean.class);

		if (honorChestEnabled) {
			logInfo("Claiming honor chest.");
			tapRandomPoint(new DTOPoint(320, 200), new DTOPoint(400, 250));
			sleepTask(TAB_CHANGE_WAIT_TIME);

			// Close the honor chest window
			closePopupIfPresent();
		} else {
			logInfo("Honor chest collection is disabled. Skipping.");
		}
	}

	/**
	 * Closes any popup window that might be present
	 */
	private void closePopupIfPresent() {
		// Tap the close button twice with a small delay between taps to handle multiple
		// windows
		tapRandomPoint(new DTOPoint(578, 1180), new DTOPoint(641, 1200), 2, 200);
		sleepTask(SHORT_WAIT_TIME);
	}

	/**
	 * Returns to the home screen by pressing back button twice
	 */
	private void returnToHomeScreen() {
		logInfo("Returning to home screen.");
		tapBackButton();
		sleepTask(SHORT_WAIT_TIME);
		tapBackButton();
		sleepTask(SHORT_WAIT_TIME);

		// Verify we're back at the home screen (could add additional verification here)
	}

	/**
	 * Reschedules the task and logs a message before exiting
	 */
	private void rescheduleAndExit(String reason) {
		logWarning(reason + ". Rescheduling task to run in 5 minutes.");
		LocalDateTime nextExecutionTime = LocalDateTime.now().plusMinutes(5);
		reschedule(nextExecutionTime);
	}

	/**
	 * Schedules the next execution of this task
	 */
	private void scheduleNextRun() {
		int offsetMinutes = profile.getConfig(EnumConfigurationKey.ALLIANCE_CHESTS_OFFSET_INT, Integer.class);
		LocalDateTime nextExecutionTime = LocalDateTime.now().plusMinutes(offsetMinutes);
		nextExecutionTime = nextExecutionTime.isAfter(UtilTime.getGameReset()) ? UtilTime.getGameReset()
				: nextExecutionTime;
		reschedule(nextExecutionTime);
		logInfo("Alliance chest task completed. Next run at: " + nextExecutionTime.format(DATETIME_FORMATTER));
	}
}