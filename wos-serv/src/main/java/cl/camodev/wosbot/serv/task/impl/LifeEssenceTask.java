package cl.camodev.wosbot.serv.task.impl;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

public class LifeEssenceTask extends DelayedTask {

	// ===================== CONSTANTS =====================
	// Navigation coordinates
	private static final DTOPoint SHOP_TAB_BUTTON = new DTOPoint(670, 195);
	private static final DTOPoint EXIT_BUTTON = new DTOPoint(40, 30);

	// Scroll coordinates
	private static final DTOPoint MENU_SCROLL_START = new DTOPoint(220, 845);
	private static final DTOPoint MENU_SCROLL_END = new DTOPoint(220, 94);

	// Search areas
	private static final DTOArea LIFE_ESSENCE_SEARCH_AREA = new DTOArea(
			new DTOPoint(0, 65),
			new DTOPoint(720, 1280));

	// Retry limits
	private static final int MAX_NAVIGATION_FAILURES = 5;
	private static final int MAX_CLAIM_SEARCH_ATTEMPTS = 5;
	private static final int MAX_CLAIM_RESULTS = 5;

	// Default configuration values
	private static final int DEFAULT_OFFSET_MINUTES = 60;
	private static final int BACKOFF_MULTIPLIER = 5;
	private static final int MAX_BACKOFF_MINUTES = 30;

	// Configuration (loaded fresh each execution)
	private int offsetMinutes;
	private boolean buyWeeklyScroll;

	// Execution state (reset each execution)
	private int consecutiveFailures = 0;

	public LifeEssenceTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	protected void execute() {
		logInfo("=== Starting Life Essence Task ===");

		// Load configuration
		loadConfiguration();

		// Check if we should stop trying after too many failures
		if (shouldStopRetrying()) {
			return;
		}

		// Navigate to Life Essence menu
		if (!navigateToLifeEssenceMenu()) {
			handleNavigationFailure();
			return;
		}

		// Claim available Life Essence
		int claimedCount = claimLifeEssence();

		// Buy weekly free scroll if enabled and available
		if (buyWeeklyScroll && shouldBuyWeeklyScroll()) {
			buyWeeklyFreeScroll();
		}

		likeIsland();

		// Exit and reschedule
		exitAndReschedule(claimedCount);
	}

	private void likeIsland() {

		DTOImageSearchResult likeButton = templateSearchHelper.searchTemplate(
				EnumTemplates.ISLAND_LIKE_BUTTON,
				SearchConfig.builder()
						.withArea(new DTOArea(new DTOPoint(634, 718), new DTOPoint(700, 774)))
						.withThreshold(95)
						.withMaxAttempts(3)
						.withDelay(100)
						.build());
		if (likeButton.isFound()) {
			logInfo("Liking the island");
			tapPoint(likeButton.getPoint());
			sleepTask(500); // Wait for like action
		}
	}

	/**
	 * Load configuration from profile after refresh
	 */
	private void loadConfiguration() {
		Integer configOffset = profile.getConfig(
				EnumConfigurationKey.LIFE_ESSENCE_OFFSET_INT,
				Integer.class);

		this.offsetMinutes = (configOffset != null && configOffset > 0)
				? configOffset
				: DEFAULT_OFFSET_MINUTES;

		this.buyWeeklyScroll = profile.getConfig(
				EnumConfigurationKey.LIFE_ESSENCE_BUY_WEEKLY_SCROLL_BOOL,
				Boolean.class);

		logDebug("Configuration loaded: offsetMinutes=" + offsetMinutes +
				", buyWeeklyScroll=" + buyWeeklyScroll);
	}

	/**
	 * Check if task should stop retrying after consecutive failures
	 */
	private boolean shouldStopRetrying() {
		// Get consecutive failure count from profile config (persisted)
		Integer failures = profile.getConfig(
				EnumConfigurationKey.LIFE_ESSENCE_CONSECUTIVE_FAILURES_INT,
				Integer.class);

		consecutiveFailures = (failures != null) ? failures : 0;

		if (consecutiveFailures >= MAX_NAVIGATION_FAILURES) {
			logWarning("Maximum consecutive failures (" + MAX_NAVIGATION_FAILURES +
					") reached. Disabling task. Re-enable in settings to retry.");
			setRecurring(false);
			return true;
		}

		return false;
	}

	/**
	 * Navigate to the Life Essence menu
	 * 
	 * Navigation flow:
	 * 1. Open side menu shortcut
	 * 2. Switch to City tab
	 * 3. Scroll down to reveal Life Essence option
	 * 4. Tap Life Essence menu
	 * 5. Back out twice to close overview (needed for claim detection)
	 * 
	 * @return true if navigation successful, false otherwise
	 */
	private boolean navigateToLifeEssenceMenu() {
		logInfo("Navigating to Life Essence menu");

		// Open side menu
		marchHelper.openLeftMenuCitySection(true);

		// Scroll down to reveal Life Essence menu
		logDebug("Scrolling to reveal Life Essence menu");
		swipe(MENU_SCROLL_START, MENU_SCROLL_END);
		sleepTask(1000); // Wait for scroll to settle

		// Search for Life Essence menu option
		DTOImageSearchResult lifeEssenceMenu = templateSearchHelper.searchTemplate(
				EnumTemplates.LIFE_ESSENCE_MENU,
				SearchConfig.builder().build());

		// Try second swipe if not found
		if (!lifeEssenceMenu.isFound()) {
			logDebug("Life Essence menu not visible. Trying second swipe.");
			swipe(MENU_SCROLL_START, MENU_SCROLL_END);
			sleepTask(1000); // Wait for scroll

			lifeEssenceMenu = templateSearchHelper.searchTemplate(
					EnumTemplates.LIFE_ESSENCE_MENU,
					SearchConfig.builder().build());
		}

		if (!lifeEssenceMenu.isFound()) {
			logWarning("Life Essence menu not found after scrolling");
			return false;
		}

		// Open Life Essence menu
		logInfo("Life Essence menu found. Opening.");
		tapPoint(lifeEssenceMenu.getPoint());
		sleepTask(3000); // Wait for menu to fully load

		// Back out twice to close the overview screen
		// This is necessary because the overview blocks detection of claimable essence
		logDebug("Closing overview screen (2x back button)");
		tapBackButton();
		sleepTask(500); // Short delay between backs
		tapBackButton();
		sleepTask(1000); // Wait for UI to settle

		logInfo("Successfully navigated to Life Essence area");
		return true;
	}

	/**
	 * Claim all available Life Essence items
	 * 
	 * Strategy:
	 * - Search multiple times in case new essence appears after claiming
	 * - Stop early if no essence found on consecutive attempts
	 * 
	 * @return number of essence items claimed
	 */
	private int claimLifeEssence() {
		logInfo("Searching for claimable Life Essence");
		int totalClaimed = 0;
		int emptySearches = 0;

		for (int searchAttempt = 1; searchAttempt <= MAX_CLAIM_SEARCH_ATTEMPTS; searchAttempt++) {
			logDebug("Claim search attempt " + searchAttempt + "/" + MAX_CLAIM_SEARCH_ATTEMPTS);

			// Search for claimable essence in the defined area
			List<DTOImageSearchResult> essenceList = templateSearchHelper.searchTemplates(
					EnumTemplates.LIFE_ESSENCE_CLAIM,
					SearchConfig.builder()
							.withArea(new DTOArea(LIFE_ESSENCE_SEARCH_AREA.topLeft(),
									LIFE_ESSENCE_SEARCH_AREA.bottomRight()))
							.withThreshold(90)
							.withMaxAttempts(1)
							.withMaxResults(MAX_CLAIM_RESULTS)
							.build());

			if (essenceList.isEmpty()) {
				emptySearches++;
				logDebug("No claimable essence found on attempt " + searchAttempt);

				// If we've had 2 consecutive empty searches, likely done
				if (emptySearches >= 2) {
					logDebug("Two consecutive empty searches. Stopping claim attempts.");
					break;
				}

				// Wait a bit in case essence is still loading
				sleepTask(500);
				continue;
			}

			// Reset empty counter if we found something
			emptySearches = 0;

			// Claim each found essence
			logDebug("Found " + essenceList.size() + " claimable essence items");
			for (DTOImageSearchResult essence : essenceList) {
				tapPoint(essence.getPoint());
				sleepTask(500); // Wait for claim animation
				totalClaimed++;
			}

			// Wait for UI to update after claiming
			sleepTask(500);
		}

		logInfo("Claimed " + totalClaimed + " Life Essence items");
		return totalClaimed;
	}

	/**
	 * Check if weekly free scroll should be purchased
	 * 
	 * Checks profile config for next allowed purchase time.
	 * If not set or time has passed, returns true.
	 */
	private boolean shouldBuyWeeklyScroll() {
		String nextScrollTimeStr = profile.getConfig(
				EnumConfigurationKey.LIFE_ESSENCE_NEXT_SCROLL_TIME_STRING,
				String.class);

		if (nextScrollTimeStr == null || nextScrollTimeStr.isEmpty()) {
			logDebug("No scroll cooldown set. Attempting to buy.");
			return true;
		}

		try {
			LocalDateTime nextScrollTime = LocalDateTime.parse(nextScrollTimeStr);

			if (LocalDateTime.now().isAfter(nextScrollTime)) {
				logDebug("Scroll cooldown expired. Attempting to buy.");
				return true;
			}

			logInfo("Weekly scroll not yet available. Next purchase at: " +
					nextScrollTime);
			return false;

		} catch (Exception e) {
			logWarning("Failed to parse next scroll time: " + e.getMessage());
			return true; // Try anyway if parse fails
		}
	}

	/**
	 * Attempt to purchase the weekly free scroll
	 * 
	 * Process:
	 * 1. Navigate to shop tab
	 * 2. Search for weekly free scroll offer
	 * 3. Click scroll to open purchase dialog
	 * 4. Click buy button to confirm
	 * 5. Update next available time to next Monday 00:00 UTC
	 */
	private void buyWeeklyFreeScroll() {
		logInfo("Attempting to buy weekly free scroll");

		// Navigate to shop tab
		logDebug("Opening shop tab");
		tapPoint(SHOP_TAB_BUTTON);
		sleepTask(1000); // Wait for tab transition

		// Search for weekly free scroll offer
		DTOImageSearchResult scrollOffer = templateSearchHelper.searchTemplate(
				EnumTemplates.ISLAND_WEEKLY_FREE_SCROLL,
				SearchConfig.builder().build());

		if (!scrollOffer.isFound()) {
			logInfo("Weekly free scroll not available (already purchased this week)");

			// Set next available time even though we didn't buy
			// This prevents repeatedly checking for an already-purchased scroll
			LocalDateTime nextScrollTime = calculateNextMondayReset();
			profile.setConfig(EnumConfigurationKey.LIFE_ESSENCE_NEXT_SCROLL_TIME_STRING,
					nextScrollTime.toString());
			setShouldUpdateConfig(true);
			logInfo("Next scroll purchase check scheduled for: " + nextScrollTime);

			tapPoint(EXIT_BUTTON);
			return;
		}

		// Click scroll to open purchase dialog
		logInfo("Weekly free scroll found. Opening purchase dialog.");
		tapPoint(scrollOffer.getPoint());
		sleepTask(500); // Wait for dialog

		// Search for buy button
		DTOImageSearchResult buyButton = templateSearchHelper.searchTemplate(
				EnumTemplates.ISLAND_WEEKLY_FREE_SCROLL_BUY_BUTTON,
				SearchConfig.builder().build());

		if (!buyButton.isFound()) {
			logWarning("Buy button not found. Purchase may have failed.");
			tapBackButton(); // Close dialog
			sleepTask(500);
			tapPoint(EXIT_BUTTON); // Exit shop
			sleepTask(500);
			return;
		}

		// Confirm purchase
		tapPoint(buyButton.getPoint());
		sleepTask(500); // Wait for purchase to complete

		logInfo("Weekly free scroll purchased successfully");

		LocalDateTime nextScrollTime = calculateNextMondayReset();
		profile.setConfig(EnumConfigurationKey.LIFE_ESSENCE_NEXT_SCROLL_TIME_STRING,
				nextScrollTime.toString());
		logInfo("Next scroll purchase available at: " + nextScrollTime);

		// Exit shop
		tapPoint(EXIT_BUTTON);
		sleepTask(500);
	}

	/**
	 * Handle navigation failure by incrementing failure count and rescheduling
	 */
	private void handleNavigationFailure() {
		consecutiveFailures++;

		profile.setConfig(EnumConfigurationKey.LIFE_ESSENCE_CONSECUTIVE_FAILURES_INT, consecutiveFailures);

		logWarning("Navigation failed. Consecutive failures: " + consecutiveFailures +
				"/" + MAX_NAVIGATION_FAILURES);

		// Calculate backoff time: 5, 10, 15, 20, 25 minutes (max 30)
		int backoffMinutes = Math.min(BACKOFF_MULTIPLIER * consecutiveFailures, MAX_BACKOFF_MINUTES);
		LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes(backoffMinutes);

		reschedule(nextAttempt);

		logInfo("Rescheduling with " + backoffMinutes + " minute backoff. Next attempt: " +
				UtilTime.localDateTimeToDDHHMMSS(nextAttempt));
	}

	/**
	 * Calculates and returns next Monday game reset time
	 */
	private LocalDateTime calculateNextMondayReset() {
		ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);

		// Get next Monday at 00:00 UTC (or current Monday if before reset)
		ZonedDateTime nextMonday = nowUtc
				.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
				.truncatedTo(ChronoUnit.DAYS);

		// If we're past the reset time on Monday, move to next week
		if (!nextMonday.isAfter(nowUtc)) {
			nextMonday = nextMonday.plusWeeks(1);
		}

		return nextMonday.toLocalDateTime();
	}

	/**
	 * Exit Life Essence interface and reschedule task
	 * 
	 * @param claimedCount number of essence items claimed
	 */
	private void exitAndReschedule(int claimedCount) {
		// Exit Life Essence interface
		logDebug("Exiting Life Essence interface");
		tapPoint(EXIT_BUTTON);
		sleepTask(1000); // Wait for menu close

		// Reset failure count on successful execution
		if (consecutiveFailures > 0) {
			consecutiveFailures = 0;
			profile.setConfig(EnumConfigurationKey.LIFE_ESSENCE_CONSECUTIVE_FAILURES_INT, 0);
			logInfo("Consecutive failure count reset after successful execution");
		}

		// Calculate next schedule time
		int scheduleOffset = offsetMinutes;

		LocalDateTime nextSchedule = LocalDateTime.now().plusMinutes(scheduleOffset);
		reschedule(nextSchedule);

		logInfo("Life Essence task completed. Claimed: " + claimedCount +
				". Next run in: " + UtilTime.localDateTimeToDDHHMMSS(nextSchedule));
	}

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.ANY;
	}

}