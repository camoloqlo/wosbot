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
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper;

public class MysteryShopTask extends DelayedTask {

	public MysteryShopTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	@Override
	protected void execute() {
		int attempt = 0;

		while (attempt < 5) {
			if (navigateToShop()) {
				handleMysteryShopOperations();
				return;
			} else {
				logWarning("Navigate to shop failed, retrying...");
				sleepTask(2000);
			}
			attempt++;
		}

		// If navigation fails after 5 attempts, reschedule for 1 hour
		if (attempt >= 5) {
			logWarning("Shop navigation failed after multiple attempts, rescheduling task for 1 hour");
			LocalDateTime nextAttempt = LocalDateTime.now().plusHours(1);
			this.reschedule(nextAttempt);
		}
	}

	/**
	 * Navigates to the shop section in the game
	 *
	 * @return true if navigation was successful, false otherwise
	 */
	private boolean navigateToShop() {
		logInfo("Navigating to the Mystery Shop.");
		// STEP 1: Search for the bottom bar shop button
		DTOImageSearchResult shopButtonResult = templateSearchHelper.searchTemplate(
				EnumTemplates.GAME_HOME_BOTTOM_BAR_SHOP_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (!shopButtonResult.isFound()) {
			logWarning("Shop button on the main screen not found. Rescheduling for 1 hour.");
			LocalDateTime nextAttempt = LocalDateTime.now().plusHours(1);
			this.reschedule(nextAttempt);
			return false;
		}

		// Tap on shop button
		tapRandomPoint(shopButtonResult.getPoint(), shopButtonResult.getPoint());
		sleepTask(1000);

		// STEP 2: Search for mystery shop within the shop menu
		DTOImageSearchResult mysteryShopResult = templateSearchHelper.searchTemplate(
				EnumTemplates.SHOP_MYSTERY_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (!mysteryShopResult.isFound()) {
			logWarning("Mystery Shop button not found inside the shop. Rescheduling for 1 hour.");
			tapBackButton();
			LocalDateTime nextAttempt = LocalDateTime.now().plusHours(1);
			this.reschedule(nextAttempt);
			return false;
		}

		// Tap on mystery shop
		tapRandomPoint(mysteryShopResult.getPoint(), mysteryShopResult.getPoint());
		sleepTask(1000);
		logInfo("Successfully navigated to the Mystery Shop.");
		return true;
	}

	/**
	 * Handles all mystery shop operations: scroll, claim free rewards, make
	 * configured purchases, use daily refresh
	 */
	private void handleMysteryShopOperations() {
		logInfo("Starting Mystery Shop operations: claiming free items, making configured purchases and using daily refresh.");
		// STEP 3: Scroll down in specific area to reveal all items
		DTOPoint scrollStart = new DTOPoint(350, 1100);
		DTOPoint scrollEnd = new DTOPoint(350, 650);
		emuManager.executeSwipe(EMULATOR_NUMBER, scrollStart, scrollEnd);
		sleepTask(500);

		// STEP 4: Process free rewards, configured buys and daily refresh in a loop
		boolean foundFreeRewards = true;
		boolean foundConfiguredPurchases = true;
		int dailyRefreshUsedCount = 0;
		final int maxDailyRefreshes = 10; // configurable limit to avoid abusing refresh
		int maxIterations = 5; // Prevent infinite loops
		int iteration = 0;
		boolean totalClaimedAny = false;
		boolean totalPurchasedAny = false;

		while ((foundFreeRewards || foundConfiguredPurchases || dailyRefreshUsedCount < maxDailyRefreshes)
				&& iteration < maxIterations) {
			iteration++;

			// First, try to claim all free rewards
			foundFreeRewards = claimAllFreeRewards();
			totalClaimedAny = totalClaimedAny || foundFreeRewards;

			// Second, try to make configured purchases
			foundConfiguredPurchases = makeConfiguredPurchases();
			totalPurchasedAny = totalPurchasedAny || foundConfiguredPurchases;

			// If no free rewards or purchases found, try to use daily refresh one or more
			// times (up to remaining limit)
			if (!foundFreeRewards && !foundConfiguredPurchases && dailyRefreshUsedCount < maxDailyRefreshes) {
				// Try using daily refresh repeatedly until no more refresh is available or we
				// reach the limit
				while (dailyRefreshUsedCount < maxDailyRefreshes) {
					boolean used = tryUseDailyRefresh();
					if (!used)
						break; // no refresh available now
					dailyRefreshUsedCount++;

					// After using a refresh, give UI a moment to update and scroll again to reveal
					// new items
					sleepTask(1000);
					emuManager.executeSwipe(EMULATOR_NUMBER, scrollStart, scrollEnd);
					sleepTask(1000);

					// After refresh, attempt to claim rewards and make purchases again
					foundFreeRewards = claimAllFreeRewards();
					totalClaimedAny = totalClaimedAny || foundFreeRewards;

					foundConfiguredPurchases = makeConfiguredPurchases();
					totalPurchasedAny = totalPurchasedAny || foundConfiguredPurchases;

					// If we found rewards or purchases after this refresh, break inner refresh loop
					// and continue outer loop
					if (foundFreeRewards || foundConfiguredPurchases)
						break;
					// otherwise continue trying another refresh (if any left)
				}
			}
		}

		// Navigate back
		tapBackButton();
		sleepTask(1000);
		tapBackButton();

		// If no more actions possible, reschedule to game reset time
		if (!foundFreeRewards && !foundConfiguredPurchases) {
			LocalDateTime nextReset = UtilTime.getGameReset();
			this.reschedule(nextReset);
			if (totalClaimedAny) {
				logInfo("Free rewards claimed");
			}
			if (totalPurchasedAny) {
				logInfo("Configured purchases made");
			}
			if (dailyRefreshUsedCount > 0 && !totalClaimedAny && !totalPurchasedAny) {
				logInfo("Daily refresh used but no rewards or purchases found");
			}
			if (!totalClaimedAny && !totalPurchasedAny && dailyRefreshUsedCount == 0) {
				logInfo("No free rewards, purchases or daily refresh available");
			}
		}
	}

	/**
	 * Claims all available free rewards
	 *
	 * @return true if at least one free reward was found and claimed, false
	 *         otherwise
	 */
	private boolean claimAllFreeRewards() {
		boolean foundAnyReward = false;
		boolean foundRewardInThisIteration = true;
		int maxRewardAttempts = 5;
		int rewardAttempt = 0;

		// Keep looking for free rewards until none are found
		while (foundRewardInThisIteration && rewardAttempt < maxRewardAttempts) {
			rewardAttempt++;
			foundRewardInThisIteration = false;

			// Search for free reward button on screen (one at a time)
			DTOImageSearchResult freeRewardResult = templateSearchHelper.searchTemplate(
					EnumTemplates.MYSTERY_SHOP_FREE_REWARD,
					SearchConfigConstants.DEFAULT_SINGLE);

			// If found, claim the reward
			if (freeRewardResult.isFound()) {
				// Tap on the free reward
				tapRandomPoint(freeRewardResult.getPoint(), freeRewardResult.getPoint());
				sleepTask(400);

				// Confirm the claim (tap on confirm button or area)
				tapPoint(new DTOPoint(360, 830));
				sleepTask(300);

				logInfo("A free reward has been claimed.");
				foundAnyReward = true;
				foundRewardInThisIteration = true;

				// Wait a bit before searching for the next reward
				sleepTask(1000);
			}
		}

		return foundAnyReward;
	}

	/**
	 * Tries to use the daily refresh if available
	 *
	 * @return true if daily refresh was used, false otherwise
	 */
	private boolean tryUseDailyRefresh() {
		DTOImageSearchResult dailyRefreshResult = templateSearchHelper.searchTemplate(
				EnumTemplates.MYSTERY_SHOP_DAILY_REFRESH,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (dailyRefreshResult.isFound()) {
			// Tap on daily refresh
			tapRandomPoint(dailyRefreshResult.getPoint(), dailyRefreshResult.getPoint());
			sleepTask(1000);

			logInfo("Daily refresh used successfully");
			return true;
		}

		return false;
	}

	/**
	 * Makes all configured purchases based on profile settings
	 *
	 *
	 * @return true if at least one purchase was made, false otherwise
	 */
	private boolean makeConfiguredPurchases() {
		boolean foundAnyPurchase = false;

		// Handle 250 Hero Widget purchases
		if (profile.getConfig(EnumConfigurationKey.BOOL_MYSTERY_SHOP_250_HERO_WIDGET, Boolean.class)) {
			foundAnyPurchase = buyHeroWidget() || foundAnyPurchase;
		}

		// Add more purchase types here as needed
		// Example:
		// if (buyOtherItem) {
		// foundAnyPurchase = buyItems(EnumTemplates.MYSTERY_SHOP_OTHER_ITEM_BUTTON,
		// "Other Item") || foundAnyPurchase;
		// }

		return foundAnyPurchase;
	}

	private boolean buyHeroWidget() {
		boolean foundAnyWidget = false;
		boolean foundWidgetInThisIteration = true;
		int maxPurchaseAttempts = 5;
		int purchaseAttempt = 0;

		// List to store coordinates of found mythic shards to avoid checking them
		// repeatedly
		java.util.List<DTOPoint> blacklistedCoordinates = new java.util.ArrayList<>();

		// Keep looking for Hero Widgets to buy until none are found
		while (foundWidgetInThisIteration && purchaseAttempt < maxPurchaseAttempts) {
			logDebug("Searching for 250 badges Hero Widget to purchase. Attempt " + (purchaseAttempt + 1));

			purchaseAttempt++;
			foundWidgetInThisIteration = false;

			// Search for the 250 Hero Widget buy button
			DTOImageSearchResult heroWidgetResult = templateSearchHelper.searchTemplate(
					EnumTemplates.MYSTERY_SHOP_250_BADGES_BUTTON,
					TemplateSearchHelper.SearchConfig.builder()
							.withMaxAttempts(1)
							.withThreshold(95)
							.withDelay(300L)
							.build());

			if (heroWidgetResult.isFound()) {
				// Check if this position is already blacklisted
				boolean isBlacklisted = blacklistedCoordinates.stream()
						.anyMatch(point -> Math.abs(point.getX() - heroWidgetResult.getPoint().getX()) < 40 &&
								Math.abs(point.getY() - heroWidgetResult.getPoint().getY()) < 40);

				if (isBlacklisted) {
					logDebug("Skipping already identified mythic shard location.");
					continue;
				}

				// Check if it is not mythic shards to avoid wrong purchase
				// Search in a specific area based on heroWidgetResult position
				DTOImageSearchResult mythicShardResult = templateSearchHelper.searchTemplate(
						EnumTemplates.MYSTERY_SHOP_MYTHIC_SHARDS_BUTTON,
						TemplateSearchHelper.SearchConfig.builder()
								.withMaxAttempts(1)
								.withThreshold(95)
								.withDelay(300L)
								.withCoordinates(
										new DTOPoint(heroWidgetResult.getPoint().getX() - 51,
												heroWidgetResult.getPoint().getY() - 177),
										new DTOPoint(heroWidgetResult.getPoint().getX() + 45,
												heroWidgetResult.getPoint().getY() - 82))
								.build());

				if (mythicShardResult.isFound()) {
					// Add this location to the blacklist
					blacklistedCoordinates.add(heroWidgetResult.getPoint());
					logInfo("Mythic shards found instead of 250 Hero Widget. Skipping purchase.");
					continue;
				}

				// Tap on the hero widget buy button
				tapPoint(heroWidgetResult.getPoint());
				sleepTask(600);

				// Confirm the purchase (tap on confirm button or area)
				tapPoint(new DTOPoint(360, 830));
				sleepTask(600);

				logInfo("250 Hero Widget found and purchased on attempt " + purchaseAttempt + ".");
				foundAnyWidget = true;
				foundWidgetInThisIteration = true;

				// Wait a bit before searching for the next widget
				sleepTask(2000);
			}
		}

		return foundAnyWidget;
	}

	/**
	 * Attempts to buy specific items from the mystery shop
	 *
	 * @param template the template to search for the buy button
	 * @param itemName the name of the item for logging purposes
	 * @return true if at least one item was purchased, false otherwise
	 */
	@SuppressWarnings("unused")
	private boolean buyItems(EnumTemplates template, String itemName) {
		boolean foundAnyItem = false;
		boolean foundItemInThisIteration = true;
		int maxPurchaseAttempts = 5;
		int purchaseAttempt = 0;

		// Keep looking for items to buy until none are found
		while (foundItemInThisIteration && purchaseAttempt < maxPurchaseAttempts) {
			purchaseAttempt++;
			foundItemInThisIteration = false;

			// Search for the buy button on screen (one at a time)
			DTOImageSearchResult buyButtonResult = templateSearchHelper.searchTemplate(
					template,
					TemplateSearchHelper.SearchConfig.builder()
							.withMaxAttempts(1)
							.withThreshold(95)
							.withDelay(300L)
							.build());

			// If found, purchase the item
			if (buyButtonResult.isFound()) {
				// Tap on the buy button
				tapRandomPoint(buyButtonResult.getPoint(), buyButtonResult.getPoint());
				sleepTask(600);

				// Confirm the purchase (tap on confirm button or area)
				tapPoint(new DTOPoint(360, 830));
				sleepTask(600);

				logInfo(itemName + " has been purchased.");
				foundAnyItem = true;
				foundItemInThisIteration = true;

				// Wait a bit before searching for the next item
				sleepTask(2000);
			}
		}

		return foundAnyItem;
	}
}
