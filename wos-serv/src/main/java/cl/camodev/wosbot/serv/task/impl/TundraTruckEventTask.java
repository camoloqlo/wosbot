package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

public class TundraTruckEventTask extends DelayedTask {

	// ===================== CONSTANTS =====================
	// UI Areas
	private static final DTOArea MY_TRUCKS_TAB = new DTOArea(
			new DTOPoint(120, 250),
			new DTOPoint(280, 270));
	private static final DTOArea REMAINING_TRUCKS_OCR = new DTOArea(
			new DTOPoint(477, 1151),
			new DTOPoint(527, 1179));
	private static final DTOArea COUNTDOWN_OCR = new DTOArea(
			new DTOPoint(194, 943),
			new DTOPoint(345, 976));
	private static final DTOArea CLOSE_WINDOW = new DTOArea(
			new DTOPoint(300, 1150),
			new DTOPoint(450, 1200));
	private static final DTOArea REFRESH_BUTTON = new DTOArea(
			new DTOPoint(588, 405),
			new DTOPoint(622, 436));
	private static final DTOArea CONFIRM_CHECKBOX = new DTOArea(
			new DTOPoint(200, 704),
			new DTOPoint(220, 722));
	private static final DTOArea CANCEL_POPUP = new DTOArea(
			new DTOPoint(626, 438),
			new DTOPoint(643, 454));
	private static final DTOArea CLOSE_DETAIL = new DTOArea(
			new DTOPoint(617, 770),
			new DTOPoint(650, 795));

	// Truck positions
	private static final DTOArea LEFT_TRUCK = new DTOArea(
			new DTOPoint(205, 643),
			new DTOPoint(265, 790));
	private static final DTOArea RIGHT_TRUCK = new DTOArea(
			new DTOPoint(450, 643),
			new DTOPoint(515, 790));

	private static final DTOArea LEFT_TRUCK_TIME = new DTOArea(
			new DTOPoint(185, 852),
			new DTOPoint(287, 875));
	private static final DTOArea RIGHT_TRUCK_TIME = new DTOArea(
			new DTOPoint(432, 852),
			new DTOPoint(535, 875));

	private static final DTOArea TIPS_POPUP_CHECKBOX = new DTOArea(
			new DTOPoint(198, 699),
			new DTOPoint(225, 726));

	// Retry limits
	private static final int MAX_NAVIGATION_ATTEMPTS = 2;
	private static final int MAX_REFRESH_ATTEMPTS = 10;

	// Configuration (loaded fresh each execution)
	private boolean useGems;
	private boolean truckSSR;
	private String activationTime; // Format: "HH:mm"
	private boolean useActivationTime;

	public TundraTruckEventTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	protected void execute() {
		logInfo("=== Starting Tundra Truck Event Task ===");

		// Load configuration
		loadConfiguration();

		// Schedule based on activation time if configured
		if (useActivationTime) {
			validateActivationTime();
			if (scheduledToActivationTime())
				return;
		}

		// Attempt navigation
		for (int attempt = 0; attempt < MAX_NAVIGATION_ATTEMPTS; attempt++) {
			TundraNavigationResult result = navigateToTundraEvent();

			switch (result) {
				case SUCCESS:
					logInfo("Successfully navigated to Tundra Truck event");
					handleTundraEvent();
					return;

				case COUNTDOWN:
					logInfo("Event in countdown. Waiting for next activation time.");
					return;

				case ENDED:
					logInfo("Event has ended. Task disabled.");
					return;

				case FAILURE:
					logDebug("Navigation failed (attempt " + (attempt + 1) + "/" + MAX_NAVIGATION_ATTEMPTS + ")");
					if (attempt < MAX_NAVIGATION_ATTEMPTS - 1) {
						sleepTask(300);
						tapBackButton();
					}
					break;
			}
		}

		// All navigation attempts failed
		logWarning("Could not find Tundra Truck event after " + MAX_NAVIGATION_ATTEMPTS +
				" attempts. Rescheduling for next activation time.");
		rescheduleWithActivationTime();
	}

	/**
	 * Load configuration from profile after refresh
	 */
	private void loadConfiguration() {
		this.useGems = profile.getConfig(EnumConfigurationKey.TUNDRA_TRUCK_USE_GEMS_BOOL, Boolean.class);
		this.truckSSR = profile.getConfig(EnumConfigurationKey.TUNDRA_TRUCK_SSR_BOOL, Boolean.class);
		this.activationTime = profile.getConfig(EnumConfigurationKey.TUNDRA_TRUCK_ACTIVATION_TIME_STRING, String.class);
		this.useActivationTime = profile.getConfig(EnumConfigurationKey.TUNDRA_TRUCK_ACTIVATION_TIME_BOOL,
				Boolean.class);

		logDebug("Configuration loaded: " + "useGems=" + useGems +
				", truckSSR=" + truckSSR + ", useActivationTime=" + useActivationTime +
				", activationTime=" + activationTime);
	}

	/**
	 * Check if activation time is in valid HH:mm format
	 */
	private void validateActivationTime() {
		if (activationTime == null || activationTime.trim().isEmpty()) {
			logWarning("Invalid activation time format: '" + activationTime
					+ "'. Expected HH:mm (e.g., '14:30'). Changing to default time: 14:00");
			activationTime = "14:00";
		}

		try {
			LocalTime.parse(activationTime.trim(), TIME_FORMATTER);
		} catch (DateTimeParseException e) {
			logWarning("Invalid activation time format: '" + activationTime
					+ "'. Expected HH:mm (e.g., '14:30'). Changing to default time: 14:00");
			activationTime = "14:00";
		}
	}

	/**
	 * Schedule task based on configured activation time in UTC.
	 * If activation time has already passed today, schedule immediately instead of
	 * tomorrow.
	 * 
	 * @return true if scheduled to activation time, false if running now
	 */
	private boolean scheduledToActivationTime() {
		try {
			LocalTime targetTime = LocalTime.parse(activationTime.trim(), TIME_FORMATTER);
			ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));

			// Create UTC time for today at the activation time
			ZonedDateTime activationTimeUtc = nowUtc.toLocalDate()
					.atTime(targetTime)
					.atZone(ZoneId.of("UTC"));

			// Convert to local time for scheduling
			ZonedDateTime localActivationTime = activationTimeUtc.withZoneSameInstant(ZoneId.systemDefault());

			// If activation time has already passed today, run immediately
			if (nowUtc.isAfter(activationTimeUtc)) {
				logInfo("Activation time " + activationTime + " UTC has already passed today. Running immediately.");
				return false;
			} else {
				logInfo("Scheduling Tundra Truck task for " + activationTime + " UTC (" +
						localActivationTime.format(DATETIME_FORMATTER) + " local time)");
				reschedule(localActivationTime.toLocalDateTime());
				return true;
			}
		} catch (DateTimeParseException e) {
			logError("Failed to parse activation time '" + activationTime + "': " + e.getMessage());
			// Fallback to game reset
			reschedule(UtilTime.getGameReset());
		}
		return true;
	}

	/**
	 * Reschedule with activation time or game reset
	 */
	private void rescheduleWithActivationTime() {
		if (useActivationTime) {
			validateActivationTime();
			try {
				LocalTime targetTime = LocalTime.parse(activationTime.trim(), TIME_FORMATTER);
				ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));

				// Schedule for tomorrow at activation time
				ZonedDateTime tomorrowActivationUtc = nowUtc.toLocalDate().plusDays(1)
						.atTime(targetTime)
						.atZone(ZoneId.of("UTC"));

				ZonedDateTime localActivationTime = tomorrowActivationUtc.withZoneSameInstant(ZoneId.systemDefault());

				logInfo("Rescheduling for next activation at " + activationTime + " UTC tomorrow (" +
						localActivationTime.format(DATETIME_FORMATTER) + " local time)");

				reschedule(localActivationTime.toLocalDateTime());
			} catch (DateTimeParseException e) {
				logError("Failed to parse activation time: " + e.getMessage());
				reschedule(UtilTime.getGameReset());
			}
		} else {
			logInfo("Rescheduling for game reset time");
			reschedule(UtilTime.getGameReset());
		}
	}

	/**
	 * Navigate to Tundra Truck event section
	 * 
	 * <p>
	 * Uses the generic NavigationHelper.navigateToEventMenu() method.
	 */
	private TundraNavigationResult navigateToTundraEvent() {
		logInfo("Navigating to Tundra Truck event");

		boolean success = navigationHelper.navigateToEventMenu(
				cl.camodev.wosbot.serv.task.helper.NavigationHelper.EventMenu.TUNDRA_TRUCK);

		if (!success) {
			logWarning("Failed to navigate to Tundra Truck event");
			return TundraNavigationResult.FAILURE;
		}

		sleepTask(2000);

		// Check if in countdown
		String countdownText = stringHelper.execute(
				COUNTDOWN_OCR.topLeft(),
				COUNTDOWN_OCR.bottomRight(),
				1,
				300L,
				null,
				s -> !s.isEmpty(),
				s -> s);
		if (countdownText != null && countdownText.toLowerCase().contains("countdown")) {
			rescheduleWithActivationTime();
			return TundraNavigationResult.COUNTDOWN;
		}

		// Check if ended
		if (isEventEnded()) {
			return TundraNavigationResult.ENDED;
		}

		return TundraNavigationResult.SUCCESS;
	}

	/**
	 * Check if event has ended
	 */
	private boolean isEventEnded() {
		DTOImageSearchResult endedResult = templateSearchHelper.searchTemplate(
				EnumTemplates.TUNDRA_TRUCK_ENDED,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (endedResult.isFound()) {
			logInfo("Tundra Truck event has ended. Rescheduling for next reset.");
			reschedule(UtilTime.getGameReset());
			return true;
		}

		return false;
	}

	/**
	 * Main event handling logic
	 */
	private void handleTundraEvent() {
		clickMyTrucksTab();
		collectArrivedTrucks();

		if (!checkAvailableTrucks()) {
			return; // No trucks remaining, already rescheduled
		}

		attemptSendTrucks();
	}

	/**
	 * Click the "My Trucks" tab
	 */
	private void clickMyTrucksTab() {
		tapRandomPoint(MY_TRUCKS_TAB.topLeft(), MY_TRUCKS_TAB.bottomRight());
		sleepTask(1000);
	}

	/**
	 * Collect any arrived trucks
	 */
	private void collectArrivedTrucks() {
		List<DTOImageSearchResult> arrivedsTruck = templateSearchHelper.searchTemplates(
				EnumTemplates.TUNDRA_TRUCK_ARRIVED,
				SearchConfig.builder()
						.withThreshold(80)
						.withMaxAttempts(2)
						.withMaxResults(3)
						.build());

		logDebug("Searching for arrived trucks");

		if (arrivedsTruck.isEmpty())
			logInfo("No arrived trucks found.");
		else
			logInfo(arrivedsTruck.size() + " arrived trucks found. Collecting rewards now.");

		for (DTOImageSearchResult result : arrivedsTruck) {
			if (result.isFound()) {
				tapPoint(result.getPoint());
				sleepTask(500);
				closeWindow();
			}
		}

		sleepTask(1000);
	}

	/**
	 * Check if trucks are available to send
	 * Also checks if trucks are in transit before rescheduling to reset
	 */
	private boolean checkAvailableTrucks() {
		try {
			String text = stringHelper.execute(
					REMAINING_TRUCKS_OCR.topLeft(),
					REMAINING_TRUCKS_OCR.bottomRight(),
					1,
					300L,
					null,
					s -> !s.isEmpty(),
					s -> s);
			logInfo("Remaining trucks OCR: '" + text + "'");

			if (text != null && text.trim().matches("0\\s*/\\s*\\d+")) {
				logInfo("No trucks available to send (0/4)");

				// Check if any trucks are still in transit
				if (hasInTransitTrucks()) {
					logInfo("Trucks are in transit. Scheduling next check for truck return time.");
					scheduleNextTruckCheck();
					return false;
				}

				logInfo("No trucks available and none in transit. Rescheduling for next activation time.");
				rescheduleWithActivationTime();
				return false;
			}

			return true;
		} catch (Exception e) {
			logError("Error checking available trucks: " + e.getMessage(), e);
			return true; // Proceed anyway
		}
	}

	/**
	 * Check if any trucks are currently in transit (have return times)
	 */
	private boolean hasInTransitTrucks() {
		logDebug("Checking if any trucks are in transit...");

		Optional<LocalDateTime> leftTime = extractTruckTime(TruckSide.LEFT);
		Optional<LocalDateTime> rightTime = extractTruckTime(TruckSide.RIGHT);

		boolean leftInTransit = leftTime.isPresent() && leftTime.get().isAfter(LocalDateTime.now());
		boolean rightInTransit = rightTime.isPresent() && rightTime.get().isAfter(LocalDateTime.now());

		if (leftInTransit) {
			logInfo("Left truck is in transit, returns at: " + leftTime.get());
		}
		if (rightInTransit) {
			logInfo("Right truck is in transit, returns at: " + rightTime.get());
		}

		return leftInTransit || rightInTransit;
	}

	/**
	 * Attempt to send both trucks
	 */
	private void attemptSendTrucks() {
		TruckStatus leftStatus = checkTruckStatus(TruckSide.LEFT);
		TruckStatus rightStatus = checkTruckStatus(TruckSide.RIGHT);

		// If both already departed, just schedule next check
		if (leftStatus == TruckStatus.DEPARTED && rightStatus == TruckStatus.DEPARTED) {
			logInfo("Both trucks already departed. Scheduling next check.");
			scheduleNextTruckCheck();
			return;
		}

		boolean leftSent = false;
		boolean rightSent = false;

		if (leftStatus == TruckStatus.AVAILABLE) {
			logInfo("Attempting to send left truck.");
			leftSent = trySendTruck(TruckSide.LEFT);
		}

		if (rightStatus == TruckStatus.AVAILABLE) {
			logInfo("Attempting to send right truck.");
			rightSent = trySendTruck(TruckSide.RIGHT);
		}

		logInfo((leftSent || rightSent ? "Truck(s) sent" : "No trucks sent") + ". Scheduling next check.");
		scheduleNextTruckCheck();
	}

	/**
	 * Check status of a specific truck
	 */
	private TruckStatus checkTruckStatus(TruckSide side) {
		DTOPoint start = side == TruckSide.LEFT ? LEFT_TRUCK.topLeft() : RIGHT_TRUCK.topLeft();
		DTOPoint end = side == TruckSide.LEFT ? LEFT_TRUCK.bottomRight() : RIGHT_TRUCK.bottomRight();

		// Tap to open truck details
		tapRandomPoint(start, end);
		sleepTask(500);

		// Check if already departed
		DTOImageSearchResult departedResult = templateSearchHelper.searchTemplate(
				EnumTemplates.TUNDRA_TRUCK_DEPARTED,
				SearchConfigConstants.DEFAULT_SINGLE);
		if (departedResult.isFound()) {
			logInfo(side + " truck has already departed");
			tapRandomPoint(CLOSE_DETAIL.topLeft(), CLOSE_DETAIL.bottomRight());
			sleepTask(300);
			closeWindow();
			return TruckStatus.DEPARTED;
		}

		// Check if available to send
		DTOImageSearchResult escortResult = templateSearchHelper.searchTemplate(
				EnumTemplates.TUNDRA_TRUCK_ESCORT,
				SearchConfigConstants.DEFAULT_SINGLE);
		if (escortResult.isFound()) {
			logInfo(side + " truck is available to send");
			tapBackButton();
			sleepTask(300);
			closeWindow();
			return TruckStatus.AVAILABLE;
		}

		logWarning("Could not determine " + side + " truck status");
		tapBackButton();
		sleepTask(300);
		closeWindow();
		return TruckStatus.NOT_FOUND;
	}

	/**
	 * Try to send a specific truck
	 */
	private boolean trySendTruck(TruckSide side) {
		DTOPoint start = side == TruckSide.LEFT ? LEFT_TRUCK.topLeft() : RIGHT_TRUCK.topLeft();
		DTOPoint end = side == TruckSide.LEFT ? LEFT_TRUCK.bottomRight() : RIGHT_TRUCK.bottomRight();

		tapRandomPoint(start, end);
		sleepTask(300);

		// Check if already departed
		if (isTruckDeparted(side)) {
			return false;
		}

		// Check if escort button available
		DTOImageSearchResult escortButton = templateSearchHelper.searchTemplate(
				EnumTemplates.TUNDRA_TRUCK_ESCORT,
				SearchConfigConstants.DEFAULT_SINGLE);
		sleepTask(500);

		if (!escortButton.isFound()) {
			logInfo("No " + side + " truck available to send");
			return false;
		}

		// If SSR required, find one
		if (truckSSR && !findSSRTruck()) {
			logInfo("SSR truck not found and required. Skipping " + side + " truck.");
			return false;
		}

		logInfo("Sending " + side + " truck" + (truckSSR ? " (SSR)" : ""));
		tapPoint(escortButton.getPoint());
		sleepTask(1000);

		// Check for "higher-level trucks" pop-up
		DTOImageSearchResult tipsPopup = templateSearchHelper.searchTemplate(
				EnumTemplates.TUNDRA_TRUCK_TIPS_POPUP,
				SearchConfigConstants.SINGLE_WITH_2_RETRIES);
		if (tipsPopup.isFound()) {
			tapRandomPoint(TIPS_POPUP_CHECKBOX.topLeft(), TIPS_POPUP_CHECKBOX.bottomRight(), 1, 300);
			tapRandomPoint(CONFIRM_CHECKBOX.topLeft(), CONFIRM_CHECKBOX.bottomRight(), 1, 300);
		}

		return true;
	}

	/**
	 * Check if truck already departed
	 */
	private boolean isTruckDeparted(TruckSide side) {
		DTOImageSearchResult departedResult = templateSearchHelper.searchTemplate(
				EnumTemplates.TUNDRA_TRUCK_DEPARTED,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (departedResult.isFound()) {
			logInfo(side + " truck already departed. Skipping.");
			tapRandomPoint(CLOSE_DETAIL.topLeft(), CLOSE_DETAIL.bottomRight());
			closeWindow();
			return true;
		}

		return false;
	}

	/**
	 * Find SSR truck through refreshes
	 */
	private boolean findSSRTruck() {
		DTOImageSearchResult ssrTruck = templateSearchHelper.searchTemplate(
				EnumTemplates.TUNDRA_TRUCK_YELLOW,
				SearchConfigConstants.DEFAULT_SINGLE);

		for (int attempt = 0; attempt < MAX_REFRESH_ATTEMPTS && !ssrTruck.isFound(); attempt++) {
			logInfo("SSR truck not found. Refreshing (attempt " + (attempt + 1) + "/" + MAX_REFRESH_ATTEMPTS + ")");

			if (!refreshTrucks()) {
				logWarning("Refresh failed (likely no gems/free refreshes). Aborting SSR search.");
				return false;
			}

			ssrTruck = templateSearchHelper.searchTemplate(
					EnumTemplates.TUNDRA_TRUCK_YELLOW,
					SearchConfigConstants.DEFAULT_SINGLE);
		}

		if (!ssrTruck.isFound()) {
			logWarning("SSR truck not found after " + MAX_REFRESH_ATTEMPTS + " refresh attempts");
		}

		return ssrTruck.isFound();
	}

	/**
	 * Refresh available trucks
	 */
	private boolean refreshTrucks() {
		tapRandomPoint(REFRESH_BUTTON.topLeft(), REFRESH_BUTTON.bottomRight());
		sleepTask(1000);

		DTOImageSearchResult freeRefresh = templateSearchHelper.searchTemplate(
				EnumTemplates.TUNDRA_TRUCK_REFRESH,
				SearchConfigConstants.SINGLE_WITH_RETRIES);

		DTOImageSearchResult gemRefresh = templateSearchHelper.searchTemplate(
				EnumTemplates.TUNDRA_TRUCK_REFRESH_GEMS,
				SearchConfigConstants.STRICT_MATCHING);

		if (freeRefresh.isFound()) {
			logInfo("Free refresh available - confirming");
			tapRandomPoint(CONFIRM_CHECKBOX.topLeft(), CONFIRM_CHECKBOX.bottomRight());
			sleepTask(500);
			tapPoint(freeRefresh.getPoint());
			return true;
		}

		if (gemRefresh.isFound()) {
			return handleGemRefresh(gemRefresh);
		}

		logDebug("Trucks refreshed without confirmation popup");
		return true;
	}

	/**
	 * Handle gem refresh popup
	 */
	private boolean handleGemRefresh(DTOImageSearchResult gemButton) {
		logInfo("Gem refresh popup detected");

		if (useGems) {
			logInfo("Using gems for refresh (useGems=true)");
			tapRandomPoint(CONFIRM_CHECKBOX.topLeft(), CONFIRM_CHECKBOX.bottomRight());
			sleepTask(500);
			tapPoint(gemButton.getPoint());
			return true;
		}

		logInfo("Declining gem refresh (useGems=false)");
		tapRandomPoint(CANCEL_POPUP.topLeft(), CANCEL_POPUP.bottomRight());
		closeWindow();
		return false;
	}

	/**
	 * Schedule next truck check based on soonest return time
	 */
	private void scheduleNextTruckCheck() {
		logInfo("Extracting next truck return times");

		Optional<LocalDateTime> leftTime = extractTruckTime(TruckSide.LEFT);
		Optional<LocalDateTime> rightTime = extractTruckTime(TruckSide.RIGHT);

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime nextSchedule;

		if (leftTime.isPresent() && rightTime.isPresent()) {
			// Use the EARLIER time (soonest truck return)
			nextSchedule = leftTime.get().isBefore(rightTime.get()) ? leftTime.get() : rightTime.get();
			logInfo("Both truck times extracted. Next check: " + nextSchedule.format(DATETIME_FORMATTER)
					+ " (soonest return)");
		} else if (leftTime.isPresent()) {
			nextSchedule = leftTime.get();
			logInfo("Only left truck time extracted. Next check: " + nextSchedule.format(DATETIME_FORMATTER));
		} else if (rightTime.isPresent()) {
			nextSchedule = rightTime.get();
			logInfo("Only right truck time extracted. Next check: " + nextSchedule.format(DATETIME_FORMATTER));
		} else {
			// Fallback: 30 minutes
			nextSchedule = now.plusMinutes(30);
			logInfo("Could not extract truck times. Fallback: next check in 30 minutes");
		}

		reschedule(nextSchedule);
	}

	/**
	 * Extract truck return time from UI
	 */
	private Optional<LocalDateTime> extractTruckTime(TruckSide side) {
		try {
			DTOPoint start = side == TruckSide.LEFT ? LEFT_TRUCK_TIME.topLeft() : RIGHT_TRUCK_TIME.topLeft();
			DTOPoint end = side == TruckSide.LEFT ? LEFT_TRUCK_TIME.bottomRight() : RIGHT_TRUCK_TIME.bottomRight();

			String text = stringHelper.execute(
					start,
					end,
					1,
					300L,
					null,
					s -> !s.isEmpty(),
					s -> s);

			if (text == null || text.trim().isEmpty()) {
				logDebug("OCR returned empty for " + side + " truck time");
				return Optional.empty();
			}

			logDebug(side + " truck time OCR: '" + text + "'");

			// Use UtilTime to parse
			LocalDateTime returnTime = TimeConverters.toLocalDateTime(text);
			logInfo(side + " truck returns at: " + returnTime);
			return Optional.of(returnTime);

		} catch (Exception e) {
			logError("Error extracting " + side + " truck time: " + e.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Close confirmation window
	 */
	private void closeWindow() {
		sleepTask(300);
		tapRandomPoint(CLOSE_WINDOW.topLeft(), CLOSE_WINDOW.bottomRight(), 2, 600);
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.WORLD;
	}

	// ===================== ENUMS =====================

	private enum TundraNavigationResult {
		SUCCESS,
		FAILURE,
		COUNTDOWN,
		ENDED
	}

	private enum TruckStatus {
		AVAILABLE,
		DEPARTED,
		NOT_FOUND
	}

	private enum TruckSide {
		LEFT("Left"),
		RIGHT("Right");

		private final String displayName;

		TruckSide(String displayName) {
			this.displayName = displayName;
		}

		@Override
		public String toString() {
			return displayName;
		}
	}
}