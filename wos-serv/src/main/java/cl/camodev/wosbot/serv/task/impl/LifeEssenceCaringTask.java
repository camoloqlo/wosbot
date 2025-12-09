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
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

public class LifeEssenceCaringTask extends DelayedTask {

	// ===================== CONSTANTS =====================
	// Navigation coordinates
	private static final DTOPoint CARING_TAB_BUTTON = new DTOPoint(670, 100);
	private static final DTOPoint BACK_TO_MAP_BUTTON = new DTOPoint(42, 28);

	// Scroll coordinates
	private static final DTOPoint MENU_SCROLL_START = new DTOPoint(220, 845);
	private static final DTOPoint MENU_SCROLL_END = new DTOPoint(220, 94);
	private static final DTOPoint ISLAND_LIST_SCROLL_START = new DTOPoint(350, 1100);
	private static final DTOPoint ISLAND_LIST_SCROLL_END = new DTOPoint(350, 670);

	// Retry limits
	private static final int MAX_ISLAND_SCROLL_ATTEMPTS = 10;
	private static final int MAX_CARING_BUTTON_SEARCHES = 3;

	// Default configuration values
	private static final int DEFAULT_RETRY_OFFSET_MINUTES = 60;

	// Configuration (loaded fresh each execution)
	private int retryOffsetMinutes;

	public LifeEssenceCaringTask(DTOProfiles profile, TpDailyTaskEnum dailyMission) {
		super(profile, dailyMission);
	}

	@Override
	protected void execute() {
		logInfo("=== Starting Life Essence Caring Task ===");

		// Load configuration
		loadConfiguration();

		// Navigate to Life Essence menu
		if (!navigateToLifeEssenceMenu()) {
			logWarning("Failed to navigate to Life Essence menu. Retrying in " + retryOffsetMinutes + " minutes.");
			reschedule(LocalDateTime.now().plusMinutes(retryOffsetMinutes));
			return;
		}

		int islandsCared = 0;
		for (int i = 0; i < 4; i++) {
			// Check if daily attempts are available
			if (!checkDailyAttemptsAvailable()) {
				closeAllMenus();
				reschedule(UtilTime.getGameReset());
				return;
			}

			// Attempt to find and care for an island
			if (findAndCareForIsland()) {
				islandsCared++;
				logInfo("Life Essence caring completed successfully for " + islandsCared + " islands.");
				if (islandsCared >= 3) {
					logInfo("Life Essence caring completed successfully for all islands today. Rescheduling for next game reset.");
					closeAllMenus();
					reschedule(UtilTime.getGameReset());
					return;
				}
			}
		}

		// No island found - retry later
		logInfo("No island needing care found. Rescheduling in " + retryOffsetMinutes + " minutes.");
		closeAllMenus();
		reschedule(LocalDateTime.now().plusMinutes(retryOffsetMinutes));
	}

	/**
	 * Load configuration from profile after refresh
	 */
	private void loadConfiguration() {
		Integer configOffset = profile.getConfig(
				EnumConfigurationKey.ALLIANCE_LIFE_ESSENCE_OFFSET_INT,
				Integer.class);

		this.retryOffsetMinutes = (configOffset != null && configOffset > 0)
				? configOffset
				: DEFAULT_RETRY_OFFSET_MINUTES;

		logDebug("Configuration loaded: retryOffsetMinutes=" + retryOffsetMinutes);
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
		logDebug("Closing overview screen (2x back button)");
		tapBackButton();
		sleepTask(500); // Short delay between backs
		tapBackButton();
		sleepTask(1000); // Wait for UI to settle

		logInfo("Successfully navigated to Life Essence area");
		return true;
	}

	/**
	 * Check if daily caring attempts are available
	 * 
	 * UI Navigation:
	 * 1. Close the Life Essence overview (back button twice)
	 * 2. Open the Caring tab (top-right icon)
	 * 3. Search for "Daily Caring Available" indicator
	 * 
	 * @return true if attempts available, false if exhausted
	 */
	private boolean checkDailyAttemptsAvailable() {
		logInfo("Checking for daily caring attempts");

		// Open the Caring tab (alliance caring list)
		logDebug("Opening Caring tab");
		tapPoint(CARING_TAB_BUTTON);
		sleepTask(2000);

		// Search for daily attempt indicator
		DTOImageSearchResult dailyAttemptIndicator = templateSearchHelper.searchTemplate(
				EnumTemplates.LIFE_ESSENCE_DAILY_CARING_AVAILABLE,
				SearchConfig.builder().build());

		if (dailyAttemptIndicator.isFound()) {
			logInfo("Daily caring attempt is available");
			return true;
		}

		logInfo("No daily caring attempts remaining. Rescheduling for next game reset.");
		return false;
	}

	/**
	 * Search through the alliance island list to find one that needs caring
	 * 
	 * Strategy:
	 * 1. Scroll through island list (up to MAX_ISLAND_SCROLL_ATTEMPTS times)
	 * 2. Look for "Go to Island" button on each screen
	 * 3. If found, navigate to island and perform caring
	 * 
	 * @return true if island found and cared for, false if none found
	 */
	private boolean findAndCareForIsland() {
		logInfo("Searching for island that needs caring");

		for (int scrollAttempt = 0; scrollAttempt < MAX_ISLAND_SCROLL_ATTEMPTS; scrollAttempt++) {
			logDebug("Searching islands on screen (scroll attempt " + (scrollAttempt + 1) +
					"/" + MAX_ISLAND_SCROLL_ATTEMPTS + ")");

			// Search for "Go to Island" button indicating island needs care
			DTOImageSearchResult gotoIslandButton = templateSearchHelper.searchTemplate(
					EnumTemplates.LIFE_ESSENCE_DAILY_CARING_GOTO_ISLAND,
					SearchConfig.builder().build());

			if (gotoIslandButton.isFound()) {
				logInfo("Found island needing care (after " + (scrollAttempt + 1) + " scroll(s))");
				return performCaringOnIsland(gotoIslandButton);
			}

			// Not found on this screen - scroll down to see more islands
			if (scrollAttempt < MAX_ISLAND_SCROLL_ATTEMPTS - 1) {
				logDebug("No island found on this screen. Scrolling to see more islands.");
				swipe(ISLAND_LIST_SCROLL_START, ISLAND_LIST_SCROLL_END);
				sleepTask(2000);
			}
		}

		logInfo("No island needing care found after " + MAX_ISLAND_SCROLL_ATTEMPTS + " scroll attempts");
		return false;
	}

	/**
	 * Navigate to an island and perform the caring action
	 * 
	 * Process:
	 * 1. Click "Go to Island" button to navigate to island on map
	 * 2. Wait for map navigation to complete
	 * 3. Search for "Caring" button on the island
	 * 4. Click caring button to complete the action
	 * 5. Return to world map
	 * 
	 * @param gotoButton The button to navigate to the island
	 * @return true if caring performed successfully, false if button not found
	 */
	private boolean performCaringOnIsland(DTOImageSearchResult gotoButton) {
		logInfo("Navigating to island location");

		// Click "Go to Island" to navigate on world map
		tapPoint(gotoButton.getPoint());
		sleepTask(5000);

		// Search for the caring button multiple times
		// (May take a moment for island UI to fully load after navigation)
		for (int attempt = 0; attempt < MAX_CARING_BUTTON_SEARCHES; attempt++) {
			logDebug("Searching for caring button (attempt " + (attempt + 1) +
					"/" + MAX_CARING_BUTTON_SEARCHES + ")");

			DTOImageSearchResult caringButton = templateSearchHelper.searchTemplate(
					EnumTemplates.LIFE_ESSENCE_DAILY_CARING_BUTTON,
					SearchConfig.builder().build());

			if (caringButton.isFound()) {
				logInfo("Caring button found. Performing caring action.");

				// Click the caring button
				tapPoint(caringButton.getPoint());
				sleepTask(5000);

				// Go back to our island view we're back on main map
				tapPoint(BACK_TO_MAP_BUTTON);
				sleepTask(3000);

				logInfo("Caring action completed successfully");
				return true;
			}

			// Not found yet - wait a bit longer for UI to load
			if (attempt < MAX_CARING_BUTTON_SEARCHES - 1) {
				sleepTask(1000);
			}
		}

		logWarning("Caring button not found after " + MAX_CARING_BUTTON_SEARCHES +
				" attempts. Island may not need care or UI didn't load properly.");

		// Return to world map even if caring failed
		tapPoint(BACK_TO_MAP_BUTTON);
		sleepTask(1000);

		return false;
	}

	/**
	 * Close all menus and return to world map
	 * 
	 * Uses multiple taps of the back-to-map button to ensure
	 * all overlays and menus are closed
	 */
	private void closeAllMenus() {
		logDebug("Closing all menus and returning to world map");
		sleepTask(300);
		tapRandomPoint(BACK_TO_MAP_BUTTON, BACK_TO_MAP_BUTTON, 2, 1000);
	}

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.ANY;
	}

}