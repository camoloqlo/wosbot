package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

/**
 * Task responsible for activating gathering speed boost bonuses.
 * 
 * <p>
 * This task:
 * <ul>
 * <li>Opens the Growth menu</li>
 * <li>Navigates to Gathering Speed section</li>
 * <li>Applies configured boost type (8h or 24h)</li>
 * <li>Handles purchase dialogs if boost items are not in inventory</li>
 * <li>Handles boost replacement confirmations</li>
 * <li>Reschedules based on boost duration</li>
 * </ul>
 * 
 * <p>
 * <b>Boost Types:</b>
 * <ul>
 * <li>8h Boost: 250 gems, reschedules in 8 hours</li>
 * <li>24h Boost: 600 gems, reschedules in 24 hours</li>
 * </ul>
 * 
 * <p>
 * <b>Important:</b>
 * This task should run BEFORE GatherTask to ensure
 * gathering marches benefit from the speed boost.
 */
public class GatherSpeedTask extends DelayedTask {

	// ========== Navigation Coordinates ==========
	private static final DTOPoint PROFILE_MENU_BUTTON = new DTOPoint(40, 118);
	private static final DTOPoint GROWTH_TAB = new DTOPoint(530, 122);
	private static final DTOPoint GATHERING_SPEED_SECTION = new DTOPoint(313, 406);

	// ========== Boost Button Coordinates ==========
	private static final DTOPoint BOOST_8H_USE_BUTTON = new DTOPoint(578, 568);
	private static final DTOPoint BOOST_24H_USE_BUTTON = new DTOPoint(578, 718);

	// ========== Obtain Dialog Coordinates ==========
	private static final DTOPoint OBTAIN_DIALOG_TEXT_TOP_LEFT = new DTOPoint(267, 126);
	private static final DTOPoint OBTAIN_DIALOG_TEXT_BOTTOM_RIGHT = new DTOPoint(370, 161);
	private static final DTOPoint OBTAIN_BUY_BUTTON = new DTOPoint(580, 387);

	// ========== Purchase Confirmation Dialog ==========
	private static final DTOPoint PURCHASE_DIALOG_TEXT_TOP_LEFT = new DTOPoint(287, 427);
	private static final DTOPoint PURCHASE_DIALOG_TEXT_BOTTOM_RIGHT = new DTOPoint(433, 465);
	private static final DTOPoint PURCHASE_DONT_SHOW_CHECKBOX = new DTOPoint(210, 712);
	private static final DTOPoint PURCHASE_CONFIRM_BUTTON = new DTOPoint(370, 790);

	// ========== Boost Replacement Dialog ==========
	private static final DTOPoint BOOST_REPLACE_TEXT_TOP_LEFT = new DTOPoint(235, 549);
	private static final DTOPoint BOOST_REPLACE_TEXT_BOTTOM_RIGHT = new DTOPoint(580, 584);
	private static final DTOPoint BOOST_REPLACE_CONFIRM_BUTTON = new DTOPoint(505, 777);

	// ========== Constants ==========
	private static final int BOOST_24H_DURATION_HOURS = 24;
	private static final int BOOST_8H_DURATION_HOURS = 8;
	private static final int BOOST_24H_GEM_COST = 600;
	private static final int BOOST_8H_GEM_COST = 250;

	// ========== Configuration (loaded in loadConfiguration()) ==========
	private BoostType boostType;

	public GatherSpeedTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	/**
	 * Loads task configuration from profile.
	 */
	private void loadConfiguration() {
		String boostTypeValue = profile.getConfig(
				EnumConfigurationKey.GATHER_SPEED_BOOST_TYPE_STRING, String.class);

		// Parse boost type from ComboBox value (e.g., "8h (250 gems)" or "24h (600
		// gems)")
		if (boostTypeValue != null && boostTypeValue.startsWith("24h")) {
			this.boostType = BoostType.BOOST_24H;
		} else {
			this.boostType = BoostType.BOOST_8H; // Default
		}

		logDebug(String.format("Configuration loaded - Boost type: %s",
				boostType.getName()));
	}

	@Override
	protected void execute() {
		loadConfiguration();

		logInfo(String.format("Starting gather speed boost task (%s).", boostType.getName()));

		if (!navigateToGatheringSpeed()) {
			logWarning("Failed to navigate to gathering speed menu. Rescheduling in 5 minutes.");
			rescheduleDefault();
			return;
		}

		if (!activateBoost()) {
			logWarning("Failed to activate boost. Rescheduling in 5 minutes.");
			rescheduleDefault();
			return;
		}

		closeMenuAndReturn();

		logInfo("Gather speed boost activated successfully.");
		rescheduleForBoostDuration();
	}

	/**
	 * Navigates to the Gathering Speed section in the Growth menu.
	 * 
	 * <p>
	 * Navigation flow:
	 * <ol>
	 * <li>Opens profile menu</li>
	 * <li>Switches to Growth tab</li>
	 * <li>Opens Gathering Speed section</li>
	 * </ol>
	 * 
	 * @return true if navigation succeeded, false otherwise
	 */
	private boolean navigateToGatheringSpeed() {
		logDebug("Opening profile menu");
		tapPoint(PROFILE_MENU_BUTTON);
		sleepTask(1000); // Wait for profile menu to open

		logDebug("Switching to Growth tab");
		tapPoint(GROWTH_TAB);
		sleepTask(1000); // Wait for tab to load

		logDebug("Opening Gathering Speed section");
		tapPoint(GATHERING_SPEED_SECTION);
		sleepTask(1000); // Wait for section to open

		return true;
	}

	/**
	 * Activates the configured boost type and handles any dialogs.
	 * 
	 * @return true if boost was activated, false otherwise
	 */
	private boolean activateBoost() {
		DTOPoint useButton = (boostType == BoostType.BOOST_8H)
				? BOOST_8H_USE_BUTTON
				: BOOST_24H_USE_BUTTON;

		logInfo(String.format("Activating %s boost (%d gems)",
				boostType.getName(), boostType.getGemCost()));

		tapPoint(useButton);
		sleepTask(500); // Wait for dialog to appear

		// Handle potential "Obtain" dialog (if boost item not in inventory)
		if (!handleObtainDialog()) {
			logWarning("Failed to handle obtain dialog");
			return false;
		}

		// Handle potential boost replacement confirmation
		handleBoostReplacementDialog();

		return true;
	}

	/**
	 * Handles the "Obtain" dialog that appears when boost item is not in inventory.
	 * Purchases the boost item with gems if the dialog appears.
	 * 
	 * @return true if dialog was handled or didn't appear, false if error occurred
	 */
	private boolean handleObtainDialog() {
		logDebug("Checking for Obtain dialog");

		String obtainText = readStringValue(
				OBTAIN_DIALOG_TEXT_TOP_LEFT,
				OBTAIN_DIALOG_TEXT_BOTTOM_RIGHT,
				null);

		if (obtainText == null || obtainText.isEmpty()) {
			logDebug("No Obtain dialog detected");
			return true;
		}

		String cleanText = obtainText.toLowerCase().trim();
		logDebug("Obtain dialog OCR: '" + obtainText + "'");

		if (!cleanText.contains("obtain")) {
			logDebug("Obtain dialog not present");
			return true;
		}

		logInfo("Obtain dialog detected. Purchasing boost item.");

		// Click buy button
		tapPoint(OBTAIN_BUY_BUTTON);
		sleepTask(800); // Wait for purchase confirmation dialog

		// Handle purchase confirmation
		return handlePurchaseConfirmation();

	}

	/**
	 * Handles the purchase confirmation dialog.
	 * Checks "Don't show again today" and confirms purchase.
	 * 
	 * @return true if dialog was handled or didn't appear, false if error occurred
	 */
	private boolean handlePurchaseConfirmation() {
		logDebug("Checking for purchase confirmation dialog");

		String purchaseText = readStringValue(
				PURCHASE_DIALOG_TEXT_TOP_LEFT,
				PURCHASE_DIALOG_TEXT_BOTTOM_RIGHT,
				null);

		if (purchaseText == null || purchaseText.isEmpty()) {
			logDebug("No purchase confirmation dialog");
			return true;
		}

		String cleanText = purchaseText.toLowerCase().trim();
		logDebug("Purchase confirmation OCR: '" + purchaseText + "'");

		if (!cleanText.contains("purchase")) {
			logDebug("Purchase confirmation not present");
			return true;
		}

		logInfo("Purchase confirmation dialog detected.");

		// Click "Don't show this confirmation again today" checkbox
		logDebug("Checking 'Don't show again' checkbox");
		tapPoint(PURCHASE_DONT_SHOW_CHECKBOX);
		sleepTask(300); // Wait for checkbox animation

		// Click confirm button
		logDebug("Confirming purchase");
		tapPoint(PURCHASE_CONFIRM_BUTTON);
		sleepTask(800); // Wait for purchase to complete

		logInfo("Purchase confirmed.");
		return true;

	}

	/**
	 * Handles the boost replacement confirmation dialog.
	 * This appears when a boost is already active and user tries to apply a new
	 * one.
	 */
	private void handleBoostReplacementDialog() {
		logDebug("Checking for boost replacement dialog");

		String confirmText = readStringValue(
				BOOST_REPLACE_TEXT_TOP_LEFT,
				BOOST_REPLACE_TEXT_BOTTOM_RIGHT,
				null);

		if (confirmText == null || confirmText.isEmpty()) {
			logDebug("No boost replacement dialog");
			return;
		}

		String cleanText = confirmText.toLowerCase().trim();
		logDebug("Boost replacement OCR: '" + confirmText + "'");

		// Check for key phrases
		boolean hasActivating = cleanText.contains("activating");
		boolean hasAnother = cleanText.contains("another");
		boolean hasBonus = cleanText.contains("bonus");

		if (hasActivating || hasAnother || hasBonus) {
			logInfo("Boost replacement dialog detected. Confirming replacement.");

			tapPoint(BOOST_REPLACE_CONFIRM_BUTTON);
			sleepTask(800); // Wait for confirmation

			logInfo("Confirmed replacing existing boost.");
		} else {
			logDebug("Boost replacement dialog not present");
		}

	}

	/**
	 * Closes the Growth menu and returns to home.
	 */
	private void closeMenuAndReturn() {
		logDebug("Closing Growth menu");
		tapBackButton();
		sleepTask(500); // Wait for menu to close
	}

	/**
	 * Reschedules the task based on the boost duration.
	 */
	private void rescheduleForBoostDuration() {
		int hours = boostType.getDurationHours();
		LocalDateTime nextSchedule = LocalDateTime.now().plusHours(hours);

		reschedule(nextSchedule);

		logInfo(String.format("Rescheduled for %d hours later at: %s",
				hours, nextSchedule.format(DATETIME_FORMATTER)));
	}

	/**
	 * Reschedules the task with default timing (5 minutes) in case of errors.
	 */
	private void rescheduleDefault() {
		LocalDateTime nextSchedule = LocalDateTime.now().plusMinutes(5);
		reschedule(nextSchedule);
	}

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

	@Override
	public boolean provideDailyMissionProgress() {
		return false;
	}

	/**
	 * Enum representing the two boost types available.
	 */
	private enum BoostType {
		BOOST_24H("24h", BOOST_24H_DURATION_HOURS, BOOST_24H_GEM_COST),
		BOOST_8H("8h", BOOST_8H_DURATION_HOURS, BOOST_8H_GEM_COST);

		private final String name;
		private final int durationHours;
		private final int gemCost;

		BoostType(String name, int durationHours, int gemCost) {
			this.name = name;
			this.durationHours = durationHours;
			this.gemCost = gemCost;
		}

		public String getName() {
			return name;
		}

		public int getDurationHours() {
			return durationHours;
		}

		public int getGemCost() {
			return gemCost;
		}
	}
}