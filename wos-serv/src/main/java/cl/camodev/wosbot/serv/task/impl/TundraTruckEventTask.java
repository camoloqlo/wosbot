package cl.camodev.wosbot.serv.task.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import net.sourceforge.tess4j.TesseractException;

public class TundraTruckEventTask extends DelayedTask {

	private boolean useGems = profile.getConfig(EnumConfigurationKey.TUNDRA_TRUCK_USE_GEMS_BOOL, Boolean.class);
	private boolean truckSSR = profile.getConfig(EnumConfigurationKey.TUNDRA_TRUCK_SSR_BOOL, Boolean.class);
	private int activationHour = profile.getConfig(EnumConfigurationKey.TUNDRA_TRUCK_ACTIVATION_HOUR_INT, Integer.class);

	public TundraTruckEventTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
		
		// Schedule based on the configured activation hour
		if (activationHour >= 0 && activationHour <= 23) {
			scheduleActivationTime();
		}
	}
	
	/**
	 * Schedules the task based on the configured activation hour in UTC
	 */
	private void scheduleActivationTime() {
		// Get the current UTC time
		ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
		
		// Create a UTC time for today at the activation hour
		ZonedDateTime activationTimeUtc = nowUtc.toLocalDate().atTime(activationHour, 0).atZone(ZoneId.of("UTC"));
		
		// If the activation time has already passed today, schedule for tomorrow
		if (nowUtc.isAfter(activationTimeUtc)) {
			activationTimeUtc = activationTimeUtc.plusDays(1);
		}
		
		// Convert UTC time to system default time zone
		ZonedDateTime localActivationTime = activationTimeUtc.withZoneSameInstant(ZoneId.systemDefault());
		
		// Schedule the task
		logInfo("Scheduling Tundra Truck task for activation at " + activationHour + ":00 UTC (" + 
				localActivationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " local time)");
		reschedule(localActivationTime.toLocalDateTime());
	}

	/**
	 * Special reschedule method that respects the configured activation hour
	 * If activation hour is configured (0-23), it uses that time for the next day
	 * Otherwise, it uses the standard game reset time
	 */
	private void rescheduleWithActivationHour() {
		// If activation hour is configured to a valid hour (0-23)
		if (activationHour >= 0 && activationHour <= 23) {
			// Schedule based on the configured activation hour for the next day
			ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
			ZonedDateTime tomorrowActivationUtc = nowUtc.toLocalDate().plusDays(1)
				.atTime(activationHour, 0).atZone(ZoneId.of("UTC"));
			ZonedDateTime localActivationTime = tomorrowActivationUtc.withZoneSameInstant(ZoneId.systemDefault());
			
			logInfo("Rescheduling Tundra Truck task for next activation at " + activationHour + 
				":00 UTC tomorrow (" + localActivationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + 
				" local time)");
			
			reschedule(localActivationTime.toLocalDateTime());
		} else {
			// Use standard game reset time
			logInfo("Rescheduling Tundra Truck task for game reset time");
			reschedule(UtilTime.getGameReset());
		}
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.WORLD;
	}

	@Override
	protected void execute() {
		int attempt = 0;

		while (attempt < 2) {
			TundraNavigationResult result = navigateToTundraEvent();
			if (result == TundraNavigationResult.SUCCESS) {
				logInfo("Successfully navigated to Tundra Truck event.");
				handleTundraEvent();
				return;
			} else if (result == TundraNavigationResult.COUNTDOWN) {
				logInfo("Tundra Truck event has not started yet. Waiting for next activation time.");
				return;
			} else if (result == TundraNavigationResult.ENDED) {
				logInfo("Tundra Truck event has ended. Task rescheduled for next activation time.");
				return;
			}

			// Handle FAILURE case
			logDebug("Failed to navigate to Tundra Truck event. Attempt " + (attempt + 1) + "/2.");
			sleepTask(300);
			tapBackButton();
			attempt++;
		}

		// If menu is not found after 2 attempts, cancel the task
		if (attempt >= 2) {
			logWarning("Could not find the Tundra Truck event tab. Assuming event is unavailable. Task will be removed.");
			this.setRecurring(false);
		}

	}

	/**
	 * Navigates to the tundra truck event section in the game
	 *
	 * @return TundraNavigationResult indicating the outcome
	 */
	private TundraNavigationResult navigateToTundraEvent() {

		logInfo("Starting the Tundra Truck Event task.");

		// Search for the events button
		DTOImageSearchResult eventsResult = emuManager.searchTemplate(EMULATOR_NUMBER,
				EnumTemplates.HOME_EVENTS_BUTTON, 90);
		if (!eventsResult.isFound()) {
			logWarning("The 'Events' button was not found.");
			return TundraNavigationResult.FAILURE;
		}

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, eventsResult.getPoint(), eventsResult.getPoint());
		sleepTask(2000);
		// Close any windows that may be open
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(529, 27), new DTOPoint(635, 63), 5, 300);

		// Search for the tundra truck within events
		DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER,
				EnumTemplates.TUNDRA_TRUCK_TAB, 90);

		if (result.isFound()) {
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
			sleepTask(1000);
			logInfo("Successfully navigated to the Tundra Truck event.");

			// Check if the event is in countdown
			try {
				String countdownText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(194, 943), new DTOPoint(345, 976));
				if (countdownText != null && countdownText.toLowerCase().contains("countdown")) {
					rescheduleWithActivationHour();
					return TundraNavigationResult.COUNTDOWN; // Event not ready
				}
			} catch (IOException | TesseractException e) {
				logWarning("Could not perform OCR to check for event countdown. Proceeding with caution. Error: " + e.getMessage());
			}

			// Check if the event has ended
			if (eventHasEnded()) {
				return TundraNavigationResult.ENDED;
			}

			return TundraNavigationResult.SUCCESS;
		}

		// Swipe completely to the left
		logInfo("Tundra Truck event not immediately visible. Swiping left to locate it.");
		for (int i = 0; i < 3; i++) {
			emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(80, 120), new DTOPoint(578, 130));
			sleepTask(200);
		}

		int attempts = 0;
		while (attempts < 5) {
			result = emuManager.searchTemplate(EMULATOR_NUMBER,
					EnumTemplates.TUNDRA_TRUCK_TAB, 90);

			if (result.isFound()) {
				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
				sleepTask(1000);
				logInfo("Successfully navigated to the Tundra Truck event.");

				// Check if the event is in countdown
				try {
					String countdownText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(194, 943), new DTOPoint(345, 976));
					if (countdownText != null && countdownText.toLowerCase().contains("countdown")) {
						rescheduleWithActivationHour();
						return TundraNavigationResult.COUNTDOWN; // Event not ready
					}
				} catch (IOException | TesseractException e) {
					logWarning("Could not perform OCR to check for event countdown. Proceeding with caution. Error: " + e.getMessage());
				}

				// Check if the event has ended
				if (eventHasEnded()) {
					return TundraNavigationResult.ENDED;
				}
				return TundraNavigationResult.SUCCESS;
			}

			logInfo("Tundra Truck event not found. Swiping right and retrying...");
			emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(630, 143), new DTOPoint(500, 128));
			sleepTask(200);
			attempts++;
		}

		logWarning("Tundra Truck event not found after multiple attempts. Aborting the task.");
		this.setRecurring(false);
		return TundraNavigationResult.FAILURE;
	}

	private boolean eventHasEnded() {
		DTOImageSearchResult endedResult = emuManager.searchTemplate(EMULATOR_NUMBER,
				EnumTemplates.TUNDRA_TRUCK_ENDED, 90);
		if (endedResult.isFound()) {
			logInfo("The Tundra Truck event has ended. The task will be removed.");
			this.setRecurring(false);
			return true;
		}
		return false;
	}

	private void handleTundraEvent() {
		clickMyTrucksTab();
		collectArrivedTrucks();

		if (!checkAvailableTrucks()) {
			return; // No trucks remaining for today, scheduled for game reset
		}

		attemptSendTrucks();
	}

	/**
	 * Extract next training completion time and schedule the task accordingly
	 */
	private void scheduleNextTruckForBothSides() {
		logInfo("Extracting the next schedule time for both trucks.");

		Optional<LocalDateTime> leftTime = extractNextTime(0);
		Optional<LocalDateTime> rightTime = extractNextTime(1);

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime nextSchedule = now.plusHours(1); // fallback

		if (leftTime.isPresent() && rightTime.isPresent()) {
			nextSchedule = leftTime.get().isAfter(rightTime.get()) ? leftTime.get() : rightTime.get();
			logInfo("Both truck times were extracted. Next check is scheduled for: " + nextSchedule);
		} else if (leftTime.isPresent()) {
			nextSchedule = leftTime.get();
			logInfo("Only the left truck time was extracted. Next check is scheduled for: " + nextSchedule);
		} else if (rightTime.isPresent()) {
			nextSchedule = rightTime.get();
			logInfo("Only the right truck time was extracted. Next check is scheduled for: " + nextSchedule);
		} else {
			nextSchedule = now.plusMinutes(30);
			logInfo("Could not extract time for either truck. Rescheduling in 30 minutes as a fallback.");
		}

		reschedule(nextSchedule);
	}

	private void closeWindow() {
		sleepTask(300);
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER,
				new DTOPoint(300, 1150), new DTOPoint(450, 1200), 2, 300);
	}

	private boolean handleGemRefresh(DTOImageSearchResult popupGems) {
		logInfo("A gem refresh pop-up was detected.");
		if (useGems) {
			logInfo("Proceeding with gem refresh as 'useGems' is true.");
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(200, 704), new DTOPoint(220, 722));
			sleepTask(500);
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, popupGems.getPoint(), popupGems.getPoint());
			return true;
		} else {
			logInfo("Gem refresh was requested, but 'useGems' is set to false. Cancelling the refresh.");
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(626, 438), new DTOPoint(643, 454));
			closeWindow();
			return false;
		}
	}

	private boolean refreshTrucks() {
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(588, 405), new DTOPoint(622, 436));
		sleepTask(1000);

		DTOImageSearchResult popup = emuManager.searchTemplate(
				EMULATOR_NUMBER, EnumTemplates.TUNDRA_TRUCK_REFRESH, 90);
		DTOImageSearchResult popupGems = emuManager.searchTemplate(
				EMULATOR_NUMBER, EnumTemplates.TUNDRA_TRUCK_REFRESH_GEMS, 98);

		if (popup.isFound()) {
			logInfo("A valuable cargo refresh pop-up was detected.");
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(200, 704), new DTOPoint(220, 722));
			sleepTask(500);
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, popup.getPoint(), popup.getPoint());
			return true;
		} else if (popupGems.isFound()) {
			return handleGemRefresh(popupGems);
		}
		logInfo("Trucks refreshed successfully without requiring confirmation.");
		return true;
	}

	private boolean findSSRTruck() {
		final int MAX_REFRESH_ATTEMPTS = 10;
		int refreshAttempts = 0;

		DTOImageSearchResult truckRaritySSR = emuManager.searchTemplate(
				EMULATOR_NUMBER, EnumTemplates.TUNDRA_TRUCK_YELLOW, 90);

		while (!truckRaritySSR.isFound() && refreshAttempts < MAX_REFRESH_ATTEMPTS) {
			logInfo("SSR Truck not found. Refreshing trucks (Attempt " + (refreshAttempts + 1) + "/" + MAX_REFRESH_ATTEMPTS + ")...");

			if (!refreshTrucks()) {
				logWarning("Failed to refresh trucks, likely due to 'useGems' being false and no free refreshes available. Aborting SSR search.");
				return false;
			}

			truckRaritySSR = emuManager.searchTemplate(
					EMULATOR_NUMBER, EnumTemplates.TUNDRA_TRUCK_YELLOW, 90);
			refreshAttempts++;
		}

		if (!truckRaritySSR.isFound()) {
			logWarning("Could not find an SSR truck after " + MAX_REFRESH_ATTEMPTS + " refresh attempts.");
		}

		return truckRaritySSR.isFound();
	}

	private TruckStatus checkTruckStatus(int side) {
		int xStart = (side == 0) ? 205 : 450;
		int xEnd = (side == 0) ? 265 : 515;
		int yStart = 643, yEnd = 790;

		// Tap to open the truck details
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(xStart, yStart), new DTOPoint(xEnd, yEnd));
		sleepTask(500);

		// Check if the truck has already departed
		DTOImageSearchResult departedResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TRUCK_DEPARTED, 90);
		if (departedResult.isFound()) {
			logInfo("Truck on the " + (side == 0 ? "left" : "right") + " side has already departed.");
			// Close the detail window
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(617, 770), new DTOPoint(650, 795));
			sleepTask(300);
			closeWindow();
			return TruckStatus.DEPARTED;
		}

		// Check if the truck is available to be sent
		DTOImageSearchResult escortResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TRUCK_ESCORT, 90);
		if (escortResult.isFound()) {
			logInfo("Truck on the " + (side == 0 ? "left" : "right") + " side is available to send.");
			// Close the detail window without sending
			tapBackButton();
			sleepTask(300);
			closeWindow();
			return TruckStatus.AVAILABLE;
		}

		logWarning("Could not determine the status of the truck on the " + (side == 0 ? "left" : "right") + " side.");
		// Close any potentially open window
		tapBackButton();
		sleepTask(300);
		closeWindow();
		return TruckStatus.NOT_FOUND;
	}

	private boolean truckAlreadyDeparted(int side) {
		DTOImageSearchResult departedTruck = emuManager.searchTemplate(
				EMULATOR_NUMBER, EnumTemplates.TUNDRA_TRUCK_DEPARTED, 90);

		if (departedTruck.isFound()) {
			logInfo("The truck on the " + (side == 0 ? "left" : "right") + " side has already departed. Skipping send.");
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(617, 770), new DTOPoint(650, 795));
			closeWindow();
			return true;
		}
		return false;
	}

	private boolean trySendTruck(int side, int xStart, int xEnd, int yStart, int yEnd) {
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER,
				new DTOPoint(xStart, yStart), new DTOPoint(xEnd, yEnd));
		sleepTask(300);

		if (truckAlreadyDeparted(side)) {
			return false;
		}

		DTOImageSearchResult sendTruckResult = emuManager.searchTemplate(
				EMULATOR_NUMBER, EnumTemplates.TUNDRA_TRUCK_ESCORT, 90);
		sleepTask(500);

		if (sendTruckResult.isFound()) {
			if (!truckSSR || findSSRTruck()) {
				logInfo((truckSSR ? "An SSR Truck was found" : "Sending a non-SSR truck") + ". Proceeding to send.");
				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, sendTruckResult.getPoint(), sendTruckResult.getPoint());
				sleepTask(1000);
				return true;
			} else {
				logInfo("An SSR Truck was not found, and the user has not enabled gem usage or has reached the maximum number of attempts.");
			}
		} else {
			logInfo("No truck is available to send on the " + (side == 0 ? "left" : "right") + " side.");
		}
		return false;
	}

	private void attemptSendTrucks() {
		TruckStatus leftStatus = checkTruckStatus(0);
		TruckStatus rightStatus = checkTruckStatus(1);

		boolean leftSent = false;
		boolean rightSent = false;

		if (leftStatus == TruckStatus.DEPARTED && rightStatus == TruckStatus.DEPARTED) {
			logInfo("Both trucks have already been sent. Scheduling for next check.");
			scheduleNextTruckForBothSides();
			return;
		}


		if (leftStatus == TruckStatus.AVAILABLE) {
			leftSent = trySendTruck(0, 205, 265, 643, 790);
		}

		if (rightStatus == TruckStatus.AVAILABLE) {
			rightSent = trySendTruck(1, 450, 515, 643, 790);
		}

		if (leftSent || rightSent) {
			logInfo("At least one truck was sent. Scheduling for next check.");
			scheduleNextTruckForBothSides();
		} else {
			logInfo("No trucks were sent in this cycle. This might be because they already departed or were not available. Scheduling for next check.");
			scheduleNextTruckForBothSides();
		}
	}

	private boolean checkAvailableTrucks() {
		try {
			String text = emuManager.ocrRegionText(EMULATOR_NUMBER,
					new DTOPoint(477, 1151), new DTOPoint(527, 1179));
			logInfo("Remaining trucks OCR result: '" + text + "'");

			if (text != null && text.trim().matches("0\\s*/\\s*\\d+")) {
				logInfo("No trucks available to send (" + text.trim() + "). The task will be rescheduled.");
				rescheduleWithActivationHour();
				return false;
			}
		} catch (IOException | TesseractException e) {
			logError("An OCR error occurred while checking for available trucks.", e);
		} catch (Exception e) {
			logError("An unexpected error occurred while checking for available trucks.", e);
		}
		return true;
	}

	private void collectArrivedTrucks() {
		int attempts = 0;
		while (true) {
			DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER,
					EnumTemplates.TUNDRA_TRUCK_ARRIVED, 90);

			logInfo("Searching for arrived trucks (Attempt " + (attempts + 1) + "/3)...");

			if (result.isFound()) {
				logInfo("An arrived truck was found. Collecting rewards...");
				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
				sleepTask(1000);

				closeWindow();
			} else if (++attempts >= 3) {
				logInfo("No arrived trucks were found after 3 attempts. Moving on.");
				break;
			}
		}
		sleepTask(1000);
	}

	private void clickMyTrucksTab() {
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(120, 250), new DTOPoint(280, 270));
		sleepTask(1000);
	}

	/**
	 * Extract the next training completion time from the UI
	 *
	 * @return Optional containing the next training time, or empty if extraction failed
	 */
	private Optional<LocalDateTime> extractNextTime(int side) {
		try {
			// OCR region containing truck completion time
			String text;
			if (side == 0) {
				text = emuManager.ocrRegionText(EMULATOR_NUMBER,
						new DTOPoint(185, 852), new DTOPoint(287, 875));
			} else {
				text = emuManager.ocrRegionText(EMULATOR_NUMBER,
						new DTOPoint(432, 852), new DTOPoint(535, 875));
			}
			logInfo("OCR extracted the following text for side " + side + ": '" + text + "'");

			// Use UtilTime to parse the time
			LocalDateTime nextTime = UtilTime.parseTime(text);
			logInfo("Successfully extracted the truck's remaining time: " + nextTime);
			return Optional.of(nextTime);

		} catch (IOException | TesseractException e) {
			logError("An OCR error occurred while extracting the truck's remaining time.", e);
			return Optional.empty();
		} catch (Exception e) {
			logError("An unexpected error occurred while extracting the truck's remaining time: " + e.getMessage(), e);
			return Optional.empty();
		}
	}

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
}
