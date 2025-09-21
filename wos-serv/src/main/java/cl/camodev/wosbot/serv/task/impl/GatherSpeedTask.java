package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class GatherSpeedTask extends DelayedTask {

	// Constants for boost types
	private static final String BOOST_TYPE_8H = "8h";
	private static final String BOOST_TYPE_24H = "24h";
	
	public GatherSpeedTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	@Override
	protected void execute() {
		logInfo("Starting gather speed boost task.");
		
		// Get the configured boost type (8h or 24h)
		String boostTypeValue = profile.getConfig(EnumConfigurationKey.GATHER_SPEED_BOOST_TYPE_STRING, String.class);
		if (boostTypeValue == null || boostTypeValue.isEmpty()) {
			boostTypeValue = "8h (250 gems)"; // Default to 8h if not specified
		}
		
		// Extract just the "8h" or "24h" part from the ComboBox value
		String boostType = boostTypeValue.startsWith("8h") ? BOOST_TYPE_8H : BOOST_TYPE_24H;
		
		logInfo("Selected boost type: " + boostType);

		// Click the small icon under the profile picture
		emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(40, 118));
		sleepTask(1000);

		// Click growth tab
		emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(530, 122));
		sleepTask(1000);

		// Click gathering speed section
		emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(313, 406));
		sleepTask(1000);

		// Apply the appropriate boost based on the selected type
		if (BOOST_TYPE_8H.equals(boostType)) {
			// Click use button for 8h boost
			logInfo("Applying 8h boost (250 gems)");
			emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(578, 568));
			sleepTask(500);
			
			// Check if "Obtain" dialog appears
			checkAndHandleObtainDialog();
			
			// Check if boost replacement confirmation dialog appears
			checkAndHandleBoostReplaceDialog();
			
			// Reschedule task for 8 hours later
			rescheduleTask(8);
		} else if (BOOST_TYPE_24H.equals(boostType)) {
			// Click use button for 24h boost
			logInfo("Applying 24h boost (600 gems)");
			emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(578, 718));
			sleepTask(500);
			
			// Check if "Obtain" dialog appears
			checkAndHandleObtainDialog();
			
			// Check if boost replacement confirmation dialog appears
			checkAndHandleBoostReplaceDialog();
			
			// Reschedule task for 24 hours later
			rescheduleTask(24);
		} else {
			logWarning("Unknown boost type: " + boostType + ". Using default 8h boost.");
			emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(578, 568));
			sleepTask(500);
			
			// Check if "Obtain" dialog appears
			checkAndHandleObtainDialog();
			
			// Check if boost replacement confirmation dialog appears
			checkAndHandleBoostReplaceDialog();
			
			// Reschedule task for 8 hours later (default)
			rescheduleTask(8);
		}

		logInfo("Gather speed boost activated successfully.");

		// Go back to home
		tapBackButton();
	}
	
	/**
	 * Reschedules the task for the specified number of hours later
	 * @param hours Number of hours to wait before next execution
	 */
	private void rescheduleTask(int hours) {
		LocalDateTime nextSchedule = LocalDateTime.now().plusHours(hours);
		this.reschedule(nextSchedule);
		logInfo("Gather speed boost task completed. Rescheduled for " + hours + " hours later.");
	}

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}
	
	/**
	 * Checks for an "Obtain" dialog and handles it by clicking the buy button
	 * and confirming the purchase if necessary
	 */
	private void checkAndHandleObtainDialog() {
		try {
			// Check for "Obtain" text in the dialog
			String obtainText = emuManager.ocrRegionText(EMULATOR_NUMBER, 
					new DTOPoint(267, 126), new DTOPoint(370, 161));
			
			logDebug("OCR result for Obtain dialog: " + obtainText);
			
			if (obtainText != null && obtainText.toLowerCase().contains("obtain")) {
				logInfo("Obtain dialog detected. Clicking buy button.");
				// Click the buy button
				emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(580, 387));
				sleepTask(800);
				
				// Check for purchase confirmation dialog
				String purchaseText = emuManager.ocrRegionText(EMULATOR_NUMBER, 
						new DTOPoint(287, 427), new DTOPoint(433, 465));
				
				logDebug("OCR result for Purchase dialog: " + purchaseText);
				
				if (purchaseText != null && purchaseText.toLowerCase().contains("purchase")) {
					logInfo("Purchase confirmation dialog detected.");
					// Click "Don't show this confirmation again today" checkbox
					emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(210, 712));
					sleepTask(300);
					
					// Click confirm buy button
					emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(370, 790));
					sleepTask(800);
					logInfo("Purchase confirmed.");
				}
			}
		} catch (Exception e) {
			logWarning("Error processing OCR for dialogs: " + e.getMessage());
		}
	}
	
	/**
	 * Checks for a boost replacement confirmation dialog and handles it by clicking the confirm button.
	 * This dialog appears when a boost is already active and user tries to apply a new one.
	 */
	private void checkAndHandleBoostReplaceDialog() {
		try {
			// Check for "Activating another bonus" text in the dialog
			String confirmText = emuManager.ocrRegionText(EMULATOR_NUMBER, 
					new DTOPoint(235, 549), new DTOPoint(580, 584));
			
			logDebug("OCR result for boost replacement dialog: " + confirmText);
			
			if (confirmText != null && (confirmText.toLowerCase().contains("activating") || 
					confirmText.toLowerCase().contains("another") || 
					confirmText.toLowerCase().contains("bonus"))) {
				logInfo("Boost replacement confirmation dialog detected.");
				
				// Click confirm button to replace existing boost
				emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(505, 777));
				sleepTask(800);
				logInfo("Confirmed replacing existing boost.");
			}
		} catch (Exception e) {
			logWarning("Error processing OCR for boost replacement dialog: " + e.getMessage());
		}
	}
}
