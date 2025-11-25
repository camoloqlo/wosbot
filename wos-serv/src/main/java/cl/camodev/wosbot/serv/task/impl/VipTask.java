package cl.camodev.wosbot.serv.task.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

/**
 * Task responsible for managing VIP benefits and purchases.
 * 
 * <p>
 * This task performs the following actions:
 * <ul>
 * <li>Opens the VIP menu</li>
 * <li>Optionally purchases monthly VIP pass if not active and cooldown
 * expired</li>
 * <li>Claims daily VIP chest rewards (7 chests)</li>
 * <li>Claims VIP point rewards (7 reward tiers)</li>
 * </ul>
 * 
 * <p>
 * The task runs once per day at game reset time.
 * 
 * <p>
 * <b>Monthly VIP Cooldown Logic:</b>
 * After purchasing monthly VIP, the task reads the expiration time from the
 * screen
 * (format: "10d 19:23:10") and stores it in profile config. On subsequent runs,
 * the task checks if the cooldown has expired before attempting to purchase
 * again.
 */
public class VipTask extends DelayedTask {

	// ========== Configuration Keys ==========
	private static final boolean DEFAULT_BUY_MONTHLY_VIP = false;

	// ========== VIP Menu Coordinates ==========
	private static final DTOPoint VIP_MENU_BUTTON_TOP_LEFT = new DTOPoint(430, 48);
	private static final DTOPoint VIP_MENU_BUTTON_BOTTOM_RIGHT = new DTOPoint(530, 85);

	// ========== VIP Purchase Coordinates ==========
	private static final DTOPoint PURCHASE_CONFIRM_TOP_LEFT = new DTOPoint(520, 810);
	private static final DTOPoint PURCHASE_CONFIRM_BOTTOM_RIGHT = new DTOPoint(650, 850);
	private static final DTOPoint PURCHASE_FINAL_CONFIRM_TOP_LEFT = new DTOPoint(250, 770);
	private static final DTOPoint PURCHASE_FINAL_CONFIRM_BOTTOM_RIGHT = new DTOPoint(480, 800);

	// ========== VIP Expiration Time OCR ==========
	private static final DTOPoint VIP_EXPIRATION_TIME_TOP_LEFT = new DTOPoint(273, 1170);
	private static final DTOPoint VIP_EXPIRATION_TIME_BOTTOM_RIGHT = new DTOPoint(461, 1213);

	// ========== VIP Rewards Coordinates ==========
	// Daily VIP chest rewards (7 chests to claim)
	private static final DTOPoint DAILY_CHEST_REWARDS_TOP_LEFT = new DTOPoint(540, 813);
	private static final DTOPoint DAILY_CHEST_REWARDS_BOTTOM_RIGHT = new DTOPoint(624, 835);

	// VIP point rewards (7 reward tiers)
	private static final DTOPoint VIP_POINT_REWARDS_TOP_LEFT = new DTOPoint(602, 263);
	private static final DTOPoint VIP_POINT_REWARDS_BOTTOM_RIGHT = new DTOPoint(650, 293);

	// ========== Configuration (loaded in loadConfiguration()) ==========
	private boolean buyMonthlyVip;
	private LocalDateTime nextMonthlyVipBuyTime;

	public VipTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	/**
	 * Loads task configuration from profile.
	 * Must be called at the start of execute() after profile refresh.
	 */
	private void loadConfiguration() {
		Boolean configuredBuyVip = profile.getConfig(
				EnumConfigurationKey.VIP_MONTHLY_BUY_BOOL, Boolean.class);
		this.buyMonthlyVip = (configuredBuyVip != null) ? configuredBuyVip : DEFAULT_BUY_MONTHLY_VIP;

		// Load next monthly VIP buy time (stored as ISO string)
		String nextBuyTimeStr = profile.getConfig(
				EnumConfigurationKey.VIP_NEXT_MONTHLY_BUY_TIME_STRING, String.class);

		if (nextBuyTimeStr != null && !nextBuyTimeStr.isEmpty()) {
			try {
				this.nextMonthlyVipBuyTime = LocalDateTime.parse(nextBuyTimeStr);
			} catch (Exception e) {
				logWarning("Failed to parse stored next monthly VIP buy time: " + e.getMessage());
				this.nextMonthlyVipBuyTime = null;
			}
		} else {
			this.nextMonthlyVipBuyTime = null;
		}

		logDebug(String.format("Configuration loaded - Buy monthly VIP: %s, Next buy: %s",
				buyMonthlyVip,
				(nextMonthlyVipBuyTime != null)
						? nextMonthlyVipBuyTime.format(DATETIME_FORMATTER)
						: "not set"));
	}

	@Override
	protected void execute() {
		loadConfiguration();

		logInfo("Starting VIP task.");

		if (!openVipMenu()) {
			logWarning("Failed to open VIP menu.");
			scheduleNextExecution();
			return;
		}

		if (buyMonthlyVip) {
			handleMonthlyVipPurchase();
		}

		claimDailyChestRewards();

		claimVipPointRewards();

		closeVipMenu();

		scheduleNextExecution();
		logInfo("VIP task completed successfully.");
	}

	/**
	 * Schedules the next execution of the VIP task.
	 * 
	 * <p>
	 * The task is scheduled to run at the earliest of two possible times:
	 * <ul>
	 * <li>Next game reset - For claiming daily VIP rewards</li>
	 * <li>Next monthly VIP buy time - If VIP buying is enabled and a buy time is
	 * set</li>
	 * </ul>
	 */
	private void scheduleNextExecution() {
		LocalDateTime nextGameReset = UtilTime.getGameReset();
		LocalDateTime nextExecutionTime = nextGameReset;

		if (buyMonthlyVip && nextMonthlyVipBuyTime != null) {
			// If monthly VIP buy is enabled and we have a next buy time
			if (nextMonthlyVipBuyTime.isBefore(nextGameReset)) {
				nextExecutionTime = nextMonthlyVipBuyTime;
				logInfo("Next execution scheduled for monthly VIP buy at: " +
						nextMonthlyVipBuyTime.format(DATETIME_FORMATTER));
			} else {
				logInfo("Next execution scheduled for game reset at: " +
						nextGameReset.format(DATETIME_FORMATTER));
			}
		} else {
			logInfo("Next execution scheduled for game reset at: " +
					nextGameReset.format(DATETIME_FORMATTER));
		}

		reschedule(nextExecutionTime);
	}

	/**
	 * Opens the VIP menu by tapping the VIP button at the top of the screen.
	 * 
	 * @return true if VIP menu opened successfully, false otherwise
	 */
	private boolean openVipMenu() {
		logDebug("Opening VIP menu");

		tapRandomPoint(VIP_MENU_BUTTON_TOP_LEFT, VIP_MENU_BUTTON_BOTTOM_RIGHT);
		sleepTask(1000); // Wait for VIP menu to load

		DTOImageSearchResult vipMenu = templateSearchHelper.searchTemplate(
				EnumTemplates.VIP_MENU,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (!vipMenu.isFound()) {
			return false;
		}

		return true;
	}

	/**
	 * Handles the monthly VIP purchase flow with cooldown checking.
	 * 
	 * <p>
	 * Logic flow:
	 * <ol>
	 * <li>Checks if cooldown has expired (if set)</li>
	 * <li>If cooldown active, skips purchase and logs remaining time</li>
	 * <li>If cooldown expired or not set, checks for unlock button</li>
	 * <li>If unlock button found, purchases VIP and reads new expiration time</li>
	 * <li>If unlock button not found (already active), reads expiration time</li>
	 * <li>Stores next buy time in profile config</li>
	 * </ol>
	 */
	private void handleMonthlyVipPurchase() {
		logDebug("Handling monthly VIP purchase check");

		// Check if cooldown is active
		if (nextMonthlyVipBuyTime != null && LocalDateTime.now().isBefore(nextMonthlyVipBuyTime)) {
			logInfo(String.format("Monthly VIP purchase on cooldown. Next purchase available at: %s (in %s)",
					nextMonthlyVipBuyTime.format(DATETIME_FORMATTER),
					UtilTime.localDateTimeToDDHHMMSS(nextMonthlyVipBuyTime)));
			return;
		}

		logDebug("Cooldown expired or not set. Checking VIP status.");

		// Check if VIP needs to be purchased
		DTOImageSearchResult unlockButton = templateSearchHelper.searchTemplate(
				EnumTemplates.VIP_UNLOCK_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (unlockButton.isFound()) {
			// VIP is not active, purchase it
			logInfo("Monthly VIP is not active. Initiating purchase.");
			purchaseMonthlyVip(unlockButton.getPoint());
		} else {
			// VIP is already active
			logInfo("Monthly VIP is already active.");
		}

		// Read and store the VIP expiration time (whether we just bought it or it was
		// already active)
		readAndStoreVipExpirationTime();
	}

	/**
	 * Executes the VIP purchase flow by confirming through two dialogs.
	 * 
	 * @param unlockButtonPoint the point where the unlock button was found
	 */
	private void purchaseMonthlyVip(DTOPoint unlockButtonPoint) {
		// Tap unlock button to start purchase flow
		tapPoint(unlockButtonPoint);
		sleepTask(1000); // Wait for purchase dialog

		// First confirmation (purchase amount dialog)
		logDebug("Confirming VIP purchase (step 1/2)");
		tapRandomPoint(PURCHASE_CONFIRM_TOP_LEFT, PURCHASE_CONFIRM_BOTTOM_RIGHT);
		sleepTask(500); // Wait for second confirmation dialog

		// Second confirmation (final purchase confirmation)
		logDebug("Confirming VIP purchase (step 2/2)");
		tapRandomPoint(PURCHASE_FINAL_CONFIRM_TOP_LEFT, PURCHASE_FINAL_CONFIRM_BOTTOM_RIGHT);
		sleepTask(500); // Wait for purchase to complete

		// Close any success popup
		tapBackButton();
		sleepTask(500); // Wait for popup to close

		logInfo("Monthly VIP purchase completed.");
	}

	/**
	 * Reads the VIP expiration time from the screen and stores it in profile
	 * config.
	 * 
	 * <p>
	 * The time is displayed in format "24d 00:33:59" (days, hours:minutes:seconds).
	 * We parse this manually to ensure accuracy.
	 */
	private void readAndStoreVipExpirationTime() {
		logDebug("Reading VIP expiration time from screen");

		DTOTesseractSettings timeSettings = DTOTesseractSettings.builder()
				.setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
				.setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
				.setAllowedChars("0123456789d")
				.build();

		Duration expirationTime = durationHelper.execute(
				VIP_EXPIRATION_TIME_TOP_LEFT,
				VIP_EXPIRATION_TIME_BOTTOM_RIGHT,
				3,
				200L,
				timeSettings,
				TimeValidators::isValidTime,
				TimeConverters::toDuration);

		if (expirationTime == null) {
			logWarning("Failed to read VIP expiration time from screen");
			return;
		}

		LocalDateTime calculatedExpirationTime = LocalDateTime.now().plus(expirationTime);
		logDebug("OCR result: '" + UtilTime.localDateTimeToDDHHMMSS(calculatedExpirationTime) + "'");
		nextMonthlyVipBuyTime = calculatedExpirationTime;

		// Store the expiration time in ISO format
		profile.setConfig(
				EnumConfigurationKey.VIP_NEXT_MONTHLY_BUY_TIME_STRING,
				calculatedExpirationTime.toString());
		setShouldUpdateConfig(true);

		logInfo(String.format("VIP expiration time stored: %s (expires in %s)",
				calculatedExpirationTime.format(DATETIME_FORMATTER),
				UtilTime.localDateTimeToDDHHMMSS(calculatedExpirationTime)));

	}

	/**
	 * Claims all daily VIP chest rewards.
	 * 
	 * <p>
	 * Taps the chest reward area 7 times to claim all available chests.
	 * Each chest provides daily rewards for VIP members.
	 */
	private void claimDailyChestRewards() {
		logInfo("Claiming daily VIP chest rewards");

		tapRandomPoint(
				DAILY_CHEST_REWARDS_TOP_LEFT,
				DAILY_CHEST_REWARDS_BOTTOM_RIGHT,
				3,
				300);

		sleepTask(500); // Wait for rewards to be claimed

		logDebug("Daily chest rewards claimed");
	}

	/**
	 * Claims all VIP point rewards.
	 * 
	 * <p>
	 * Taps the VIP point reward area 7 times to claim all available reward tiers.
	 * VIP points are earned through various activities and unlock progressive
	 * rewards.
	 */
	private void claimVipPointRewards() {
		logInfo("Claiming VIP point rewards");

		tapRandomPoint(
				VIP_POINT_REWARDS_TOP_LEFT,
				VIP_POINT_REWARDS_BOTTOM_RIGHT,
				3,
				300);

		sleepTask(500); // Wait for rewards to be claimed

		logDebug("VIP point rewards claimed");
	}

	/**
	 * Closes the VIP menu by tapping the back button.
	 */
	private void closeVipMenu() {
		logDebug("Closing VIP menu");
		tapBackButton();
		sleepTask(500); // Wait for menu to close
	}

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.ANY;
	}

}