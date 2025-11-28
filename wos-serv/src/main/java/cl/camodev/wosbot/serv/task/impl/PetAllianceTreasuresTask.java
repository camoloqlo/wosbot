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

import java.time.LocalDateTime;

/**
 * Task that claims alliance treasures from the Beast Cage.
 * 
 * <p>
 * <b>Execution Flow:</b>
 * <ol>
 * <li>Navigate to Pets menu</li>
 * <li>Open Beast Cage</li>
 * <li>Open Alliance Treasure Map screen</li>
 * <li>Open Ally Treasure screen</li>
 * <li>Claim available treasures</li>
 * </ol>
 * 
 * <p>
 * <b>Scheduling:</b>
 * <ul>
 * <li>Success: Reschedules to next game reset</li>
 * <li>No treasure available: Reschedules to next game reset</li>
 * <li>Navigation failure: Retries in 5 minutes</li>
 * </ul>
 */
public class PetAllianceTreasuresTask extends DelayedTask {

	// ========================================================================
	// NAVIGATION CONSTANTS
	// ========================================================================

	/**
	 * Button area for opening the Alliance Treasure Map screen in Beast Cage.
	 */
	private static final DTOArea ALLIANCE_TREASURE_MAP_BUTTON = new DTOArea(
			new DTOPoint(547, 1150),
			new DTOPoint(650, 1210));

	/**
	 * Button area for opening the Ally Treasure screen from treasure map.
	 */
	private static final DTOArea ALLY_TREASURE_BUTTON = new DTOArea(
			new DTOPoint(612, 1184),
			new DTOPoint(653, 1211));

	/**
	 * Delay in minutes before retrying after navigation failure.
	 */
	private static final int RETRY_DELAY_MINUTES = 5;

	// ========================================================================
	// CONSTRUCTOR
	// ========================================================================

	/**
	 * Constructs a new PetAllianceTreasuresTask.
	 * 
	 * @param profile     The profile this task will execute for
	 * @param tpDailyTask The task type enum
	 */
	public PetAllianceTreasuresTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	// ========================================================================
	// TASK CONFIGURATION
	// ========================================================================

	/**
	 * This task can start from any screen location.
	 * 
	 * @return ANY - works from both home and world screens
	 */
	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.ANY;
	}

	// ========================================================================
	// MAIN EXECUTION
	// ========================================================================

	/**
	 * Executes the alliance treasure claiming process.
	 * 
	 * <p>
	 * Navigates through Pets → Beast Cage → Alliance Treasures and attempts
	 * to claim available rewards. Reschedules appropriately based on outcome.
	 */
	@Override
	protected void execute() {
		logInfo("Starting alliance treasure claim process");

		if (!navigateToBeastCage()) {
			rescheduleForRetry();
			return;
		}

		openAllianceTreasureScreens();
		claimTreasureIfAvailable();
	}

	// ========================================================================
	// NAVIGATION METHODS
	// ========================================================================

	/**
	 * Navigates from current screen to the Beast Cage menu.
	 * 
	 * <p>
	 * <b>Steps:</b>
	 * <ol>
	 * <li>Search for Pets button (with retries for reliability)</li>
	 * <li>Tap to open Pets menu</li>
	 * <li>Search for Beast Cage button</li>
	 * <li>Tap to open Beast Cage</li>
	 * </ol>
	 * 
	 * @return true if navigation succeeded, false if any step failed
	 */
	private boolean navigateToBeastCage() {
		// Search for Pets button with retries for reliability
		DTOImageSearchResult petsResult = templateSearchHelper.searchTemplate(
				EnumTemplates.GAME_HOME_PETS,
				SearchConfigConstants.SINGLE_WITH_RETRIES);

		if (!petsResult.isFound()) {
			logWarning("Pets button not found on home screen. Will retry in " +
					RETRY_DELAY_MINUTES + " minutes");
			return false;
		}

		logDebug("Opening Pets menu");
		tapRandomPoint(petsResult.getPoint(), petsResult.getPoint());
		sleepTask(3000); // Wait for Pets menu to fully load

		// Search for Beast Cage button
		DTOImageSearchResult beastCageResult = templateSearchHelper.searchTemplate(
				EnumTemplates.PETS_BEAST_CAGE,
				SearchConfigConstants.SINGLE_WITH_RETRIES);

		if (!beastCageResult.isFound()) {
			logWarning("Beast Cage button not found in Pets menu. Will retry in " +
					RETRY_DELAY_MINUTES + " minutes");
			tapBackButton(); // Exit Pets menu
			return false;
		}

		logDebug("Opening Beast Cage");
		tapRandomPoint(beastCageResult.getPoint(), beastCageResult.getPoint());
		sleepTask(500); // Wait for Beast Cage to open

		return true;
	}

	/**
	 * Opens the alliance treasure screens within Beast Cage.
	 * 
	 * <p>
	 * <b>Navigation sequence:</b>
	 * <ol>
	 * <li>Tap Alliance Treasure Map button (opens treasure map view)</li>
	 * <li>Tap Ally Treasure button (opens claim screen)</li>
	 * </ol>
	 * 
	 * <p>
	 * This method assumes we're already in the Beast Cage menu.
	 */
	private void openAllianceTreasureScreens() {
		logDebug("Opening Alliance Treasure Map screen");
		tapRandomPoint(
				ALLIANCE_TREASURE_MAP_BUTTON.topLeft(),
				ALLIANCE_TREASURE_MAP_BUTTON.bottomRight());
		sleepTask(500); // Wait for treasure map screen to load

		logDebug("Opening Ally Treasure screen");
		tapRandomPoint(
				ALLY_TREASURE_BUTTON.topLeft(),
				ALLY_TREASURE_BUTTON.bottomRight());
		sleepTask(500); // Wait for ally treasure screen to load
	}

	// ========================================================================
	// CLAIM LOGIC
	// ========================================================================

	/**
	 * Attempts to claim alliance treasure if the claim button is available.
	 * 
	 * <p>
	 * <b>Behavior:</b>
	 * <ul>
	 * <li>If claim button found: Claims treasure, reschedules to next game
	 * reset</li>
	 * <li>If claim button not found: Already claimed, reschedules to next game
	 * reset</li>
	 * </ul>
	 * 
	 * <p>
	 * Always navigates back to previous screen after checking, regardless of
	 * outcome.
	 */
	private void claimTreasureIfAvailable() {
		DTOImageSearchResult claimButton = templateSearchHelper.searchTemplate(
				EnumTemplates.PETS_BEAST_ALLIANCE_CLAIM,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (claimButton.isFound()) {
			logInfo("Claim button found - claiming alliance treasure");
			tapRandomPoint(claimButton.getPoint(), claimButton.getPoint());
			sleepTask(500); // Wait for claim animation
			rescheduleToGameReset("Alliance treasure claimed successfully");
		} else {
			logInfo("No claimable treasure found - already claimed today");
			rescheduleToGameReset("Treasure already claimed");
		}
	}

	// ========================================================================
	// RESCHEDULING METHODS
	// ========================================================================

	/**
	 * Reschedules the task to the next game reset (00:00 UTC).
	 * 
	 * <p>
	 * Used when treasure is successfully claimed or when no treasure
	 * is available (indicating it was already claimed today).
	 * 
	 * @param reason Descriptive reason for logging purposes
	 */
	private void rescheduleToGameReset(String reason) {
		LocalDateTime nextReset = UtilTime.getGameReset();
		reschedule(nextReset);
		logInfo(reason + ". Rescheduling to next game reset: " +
				nextReset.format(DATETIME_FORMATTER));
	}

	/**
	 * Reschedules the task to retry in 5 minutes.
	 * 
	 * <p>
	 * Used when navigation fails (Pets button or Beast Cage not found).
	 * This handles temporary UI issues or feature unavailability.
	 */
	private void rescheduleForRetry() {
		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES);
		reschedule(retryTime);
		logWarning("Navigation failed. Retrying at " + retryTime.format(TIME_FORMATTER));
	}
}
