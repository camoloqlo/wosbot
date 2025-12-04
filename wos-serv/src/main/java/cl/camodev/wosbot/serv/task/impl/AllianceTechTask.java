package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.TaskQueue;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import cl.camodev.wosbot.serv.task.helper.NavigationHelper;

/**
 * Task responsible for donating to Alliance Technology research.
 * 
 * <p>
 * This task performs daily donations to alliance tech research and optionally
 * triggers the Alliance Shop task if sufficient coins are available after
 * donations.
 * 
 * <p>
 * <b>Execution Flow:</b>
 * <ol>
 * <li>Navigate to Alliance Tech menu</li>
 * <li>Locate and tap the thumbs-up (donate) button</li>
 * <li>Perform 25 donation taps (maximum per session)</li>
 * <li>Optionally check alliance coins and trigger shop task</li>
 * <li>Reschedule based on user-configured offset</li>
 * </ol>
 * 
 * <p>
 * <b>Configuration:</b>
 * <ul>
 * <li>Donation interval controlled by {@code ALLIANCE_TECH_OFFSET_INT} (min: 10
 * minutes)</li>
 * <li>Shop activation controlled by {@code ALLIANCE_SHOP_ENABLED_BOOL}</li>
 * <li>Shop trigger threshold controlled by
 * {@code ALLIANCE_SHOP_MIN_COINS_TO_ACTIVATE_INT}</li>
 * </ul>
 * 
 * <p>
 * <b>Scheduling:</b>
 * <ul>
 * <li>Success: Reschedules based on configured offset minutes</li>
 * <li>Failure: Retries after 10 minutes</li>
 * </ul>
 * 
 * @author WoS Bot
 * @see DelayedTask
 * @see AllianceShopTask
 */
public class AllianceTechTask extends DelayedTask {

	// ========================================================================
	// COORDINATE CONSTANTS
	// ========================================================================

	private static final DTOArea DONATION_BUTTON_AREA = new DTOArea(
			new DTOPoint(450, 1000),
			new DTOPoint(580, 1050));

	private static final DTOArea POPUP_CLOSE_AREA = new DTOArea(
			new DTOPoint(270, 30),
			new DTOPoint(280, 80));

	private static final DTOArea ALLIANCE_COINS_BUTTON_AREA = new DTOArea(
			new DTOPoint(580, 30),
			new DTOPoint(670, 50));

	private static final DTOArea COIN_COUNT_OCR_AREA = new DTOArea(
			new DTOPoint(272, 257),
			new DTOPoint(443, 285));

	// ========================================================================
	// BEHAVIOR CONSTANTS
	// ========================================================================

	private static final int DONATION_TAP_COUNT = 25;
	private static final int MIN_OFFSET_MINUTES = 10;
	private static final int DEFAULT_OFFSET_MINUTES = 60;
	private static final int ERROR_RETRY_MINUTES = 10;

	// ========================================================================
	// OCR SETTINGS
	// ========================================================================

	private static final DTOTesseractSettings COIN_COUNT_OCR_SETTINGS = DTOTesseractSettings.builder()
			.setAllowedChars("0123456789")
			.setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
			.build();

	// ========================================================================
	// INSTANCE FIELDS
	// ========================================================================

	private int offsetMinutes;
	private boolean donationsSuccessful;

	/**
	 * Constructs a new Alliance Tech donation task.
	 * 
	 * @param profile     The profile to execute this task for
	 * @param tpDailyTask The task type enum
	 */
	public AllianceTechTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	/**
	 * Loads and validates task configuration from the profile.
	 * 
	 * <p>
	 * Configuration key used:
	 * <ul>
	 * <li>{@link EnumConfigurationKey#ALLIANCE_TECH_OFFSET_INT} - Minutes between
	 * donations</li>
	 * </ul>
	 * 
	 * <p>
	 * Validates that offset is at least {@value MIN_OFFSET_MINUTES} minutes.
	 * Falls back to {@value DEFAULT_OFFSET_MINUTES} if invalid.
	 */
	private void loadConfiguration() {
		int rawOffset = profile.getConfig(
				EnumConfigurationKey.ALLIANCE_TECH_OFFSET_INT,
				Integer.class);

		if (rawOffset < MIN_OFFSET_MINUTES) {
			logWarning("Invalid offset configured: " + rawOffset +
					" minutes. Must be at least " + MIN_OFFSET_MINUTES +
					". Using default: " + DEFAULT_OFFSET_MINUTES);
			offsetMinutes = DEFAULT_OFFSET_MINUTES;
		} else {
			offsetMinutes = rawOffset;
		}

		logInfo("Configuration loaded - Offset: " + offsetMinutes + " minutes");
	}

	/**
	 * Executes the Alliance Tech donation task.
	 * 
	 * <p>
	 * <b>Execution Flow:</b>
	 * <ol>
	 * <li>Load and validate configuration</li>
	 * <li>Navigate to Alliance Tech menu</li>
	 * <li>Locate thumbs-up donation button</li>
	 * <li>Perform donations</li>
	 * <li>Optionally trigger Alliance Shop task</li>
	 * <li>Schedule next execution</li>
	 * </ol>
	 * 
	 * <p>
	 * On any failure, the task reschedules for retry after 10 minutes.
	 */
	@Override
	protected void execute() {
		logInfo("Starting Alliance Tech donation task");

		donationsSuccessful = false;
		loadConfiguration();

		if (!navigateToTechMenu()) {
			handleTaskFailure("Failed to navigate to Alliance Tech menu");
			return;
		}

		if (!findDonationButton()) {
			handleTaskFailure("Thumbs-up donation button not found");
			return;
		}

		performDonations();
		donationsSuccessful = true;

		checkAndTriggerAllianceShop();

		scheduleNextRun();
	}

	/**
	 * Navigates to the Alliance Tech menu using the navigation helper.
	 * 
	 * @return true if navigation was successful, false otherwise
	 */
	private boolean navigateToTechMenu() {
		logDebug("Navigating to Alliance Tech menu");

		boolean success = navigationHelper.navigateToAllianceMenu(NavigationHelper.AllianceMenu.TECH);

		if (success) {
			logDebug("Successfully navigated to Alliance Tech menu");
		} else {
			logError("Failed to navigate to Alliance Tech menu");
		}

		return success;
	}

	/**
	 * Searches for the thumbs-up donation button in the Alliance Tech screen.
	 * 
	 * <p>
	 * Uses template matching with 3 retry attempts to locate the button.
	 * 
	 * @return true if the donation button was found, false otherwise
	 */
	private boolean findDonationButton() {
		logDebug("Searching for thumbs-up donation button");

		DTOImageSearchResult thumbsUpResult = templateSearchHelper.searchTemplate(
				EnumTemplates.ALLIANCE_TECH_THUMB_UP,
				SearchConfigConstants.SINGLE_WITH_RETRIES);

		if (thumbsUpResult.isFound()) {
			logInfo("Thumbs-up donation button found. Proceeding with donations.");
			tapPoint(thumbsUpResult.getPoint());
			sleepTask(500); // Wait for donation popup to appear
			return true;
		}

		logWarning("Thumbs-up donation button not found after retries");
		return false;
	}

	/**
	 * Performs the maximum number of donations to Alliance Tech.
	 * 
	 * <p>
	 * Donations are performed by rapidly tapping the donation button
	 * {@value DONATION_TAP_COUNT} times, which is the maximum allowed per session.
	 * 
	 * <p>
	 * The taps are spaced 150ms apart to ensure reliable registration.
	 */
	private void performDonations() {
		logInfo("Performing " + DONATION_TAP_COUNT + " donations to Alliance Tech");

		tapRandomPoint(
				DONATION_BUTTON_AREA.topLeft(),
				DONATION_BUTTON_AREA.bottomRight(),
				DONATION_TAP_COUNT,
				150 // Delay between donation taps in milliseconds
		);

		logInfo("Donations completed successfully");
	}

	/**
	 * Checks if Alliance Shop should be triggered and executes it if conditions are
	 * met.
	 * 
	 * <p>
	 * <b>Trigger Conditions:</b>
	 * <ul>
	 * <li>Alliance Shop feature is enabled in configuration</li>
	 * <li>Current alliance coins exceed the configured minimum threshold</li>
	 * </ul>
	 * 
	 * <p>
	 * If conditions are met, triggers the Alliance Shop task to run immediately
	 * via {@link TaskQueue#executeTaskNow(TpDailyTaskEnum, boolean)}.
	 */
	private void checkAndTriggerAllianceShop() {
		boolean shopEnabled = profile.getConfig(
				EnumConfigurationKey.ALLIANCE_SHOP_ENABLED_BOOL,
				Boolean.class);

		if (!shopEnabled) {
			logDebug("Alliance Shop is disabled. Skipping shop check.");
			return;
		}

		logInfo("Alliance Shop enabled. Checking current coins.");

		navigateToCoinsDisplay();

		Integer currentCoins = readNumberValue(COIN_COUNT_OCR_AREA.topLeft(), COIN_COUNT_OCR_AREA.bottomRight(),
				COIN_COUNT_OCR_SETTINGS);

		if (currentCoins == null) {
			logWarning("Could not read current alliance coins. Skipping shop trigger.");
			return;
		}

		Integer minCoins = profile.getConfig(
				EnumConfigurationKey.ALLIANCE_SHOP_MIN_COINS_TO_ACTIVATE_INT,
				Integer.class);

		logInfo("Current alliance coins: " + currentCoins + ", Minimum required: " + minCoins);

		if (currentCoins > minCoins) {
			triggerAllianceShopTask();
		} else {
			logInfo("Insufficient coins to trigger Alliance Shop task");
		}
	}

	/**
	 * Navigates to the alliance coins display popup.
	 * 
	 * <p>
	 * <b>Navigation Steps:</b>
	 * <ol>
	 * <li>Close donation popup by tapping the close area (3 times for
	 * reliability)</li>
	 * <li>Tap alliance coins button to open coins popup</li>
	 * </ol>
	 */
	private void navigateToCoinsDisplay() {
		logDebug("Closing donation popup");
		tapRandomPoint(
				POPUP_CLOSE_AREA.topLeft(),
				POPUP_CLOSE_AREA.bottomRight(),
				3, // Multiple taps to ensure popup closes
				200 // Delay between taps in milliseconds
		);

		logDebug("Opening alliance coins popup");
		tapRandomPoint(
				ALLIANCE_COINS_BUTTON_AREA.topLeft(),
				ALLIANCE_COINS_BUTTON_AREA.bottomRight(),
				1,
				1000 // Wait for popup to fully load
		);
	}

	/**
	 * Triggers the Alliance Shop task to execute immediately.
	 * 
	 * <p>
	 * The shop task will wait for this task to complete before executing,
	 * as per the TaskQueue execution model.
	 */
	private void triggerAllianceShopTask() {
		TaskQueue queue = servScheduler.getQueueManager().getQueue(profile.getId());

		if (queue == null) {
			logError("Could not retrieve task queue for profile. Cannot trigger shop task.");
			return;
		}

		logInfo("Triggering Alliance Shop task to execute immediately");
		queue.executeTaskNow(TpDailyTaskEnum.ALLIANCE_SHOP, true);
	}

	/**
	 * Schedules the next execution of this task after successful completion.
	 * 
	 * <p>
	 * The task reschedules itself based on the user-configured offset minutes.
	 */
	private void scheduleNextRun() {
		LocalDateTime nextExecutionTime = LocalDateTime.now().plusMinutes(offsetMinutes);
		reschedule(nextExecutionTime);

		logInfo("Alliance Tech task completed successfully. Next execution in " +
				offsetMinutes + " minutes");
	}

	/**
	 * Handles task failure by logging the reason and scheduling a retry.
	 * 
	 * <p>
	 * On failure, the task reschedules to retry after {@value ERROR_RETRY_MINUTES}
	 * minutes
	 * to avoid spamming retries while allowing timely recovery.
	 * 
	 * @param reason A descriptive reason for the task failure
	 */
	private void handleTaskFailure(String reason) {
		logWarning("Task failed: " + reason);

		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(ERROR_RETRY_MINUTES);
		reschedule(retryTime);

		logInfo("Task rescheduled to retry in " + ERROR_RETRY_MINUTES + " minutes");
	}

	/**
	 * Indicates whether this task contributed to daily mission progress.
	 * 
	 * <p>
	 * Returns true only if donations were successfully performed, as donations
	 * contribute to the "Donate to Alliance Tech" daily mission.
	 * 
	 * @return true if donations were successful, false otherwise
	 */
	@Override
	public boolean provideDailyMissionProgress() {
		return donationsSuccessful;
	}
}