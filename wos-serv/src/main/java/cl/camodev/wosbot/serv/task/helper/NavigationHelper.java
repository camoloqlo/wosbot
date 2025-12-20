package cl.camodev.wosbot.serv.task.helper;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.ex.ProfileInReconnectStateException;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.ButtonConstants;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

/**
 * Helper class for game navigation operations.
 * 
 * <p>
 * Provides navigation functionality including:
 * <ul>
 * <li>Navigating to alliance menus</li>
 * <li>Navigating to event menus</li>
 * <li>Ensuring correct screen location (Home/World)</li>
 * <li>Handling screen verification and recovery</li>
 * </ul>
 * 
 * @author WoS Bot
 */
public class NavigationHelper {

    private static final int MAX_SCREEN_LOCATION_ATTEMPTS = 10;
    private static final int MAX_EVENT_SWIPE_ATTEMPTS = 5;

    // Event navigation coordinates
    private static final DTOPoint EVENTS_TAB_CLEAR_TL = new DTOPoint(529, 27);
    private static final DTOPoint EVENTS_TAB_CLEAR_BR = new DTOPoint(635, 63);
    private static final int EVENTS_TAB_CLEAR_TAPS = 5;

    private static final DTOPoint EVENT_SWIPE_LEFT_START = new DTOPoint(80, 120);
    private static final DTOPoint EVENT_SWIPE_LEFT_END = new DTOPoint(578, 130);
    private static final DTOPoint EVENT_SWIPE_RIGHT_START = new DTOPoint(630, 143);
    private static final DTOPoint EVENT_SWIPE_RIGHT_END = new DTOPoint(400, 128);

    private final TemplateSearchHelper templateSearchHelper;
    private final EmulatorManager emuManager;
    private final String emulatorNumber;
    private final ProfileLogger logger;
    private final String profileName;
    private final ServLogs servLogs;
    private static final String HELPER_NAME = "NavigationHelper";

    /**
     * Constructs a new NavigationHelper.
     * 
     * @param emuManager     The emulator manager instance
     * @param emulatorNumber The identifier for the specific emulator
     * @param profile        The profile this helper operates on
     */
    public NavigationHelper(EmulatorManager emuManager, String emulatorNumber, DTOProfiles profile) {
        this.emuManager = emuManager;
        this.emulatorNumber = emulatorNumber;
        this.templateSearchHelper = new TemplateSearchHelper(emuManager, emulatorNumber, profile);
        this.logger = new ProfileLogger(NavigationHelper.class, profile);
        this.profileName = profile.getName();
        this.servLogs = ServLogs.getServices();
    }

    /**
     * Navigates to a specific alliance menu within the game.
     * 
     * @param menu The alliance menu to navigate to
     * @return true if navigation was successful, false otherwise
     */
    public boolean navigateToAllianceMenu(AllianceMenu menu) {
        emuManager.tapAtRandomPoint(
                emulatorNumber,
                ButtonConstants.BOTTOM_MENU_ALLIANCE_BUTTON.topLeft(),
                ButtonConstants.BOTTOM_MENU_ALLIANCE_BUTTON.bottomRight());

        EnumTemplates menuTemplate = switch (menu) {
            case WAR -> EnumTemplates.ALLIANCE_WAR_BUTTON;
            case CHESTS -> EnumTemplates.ALLIANCE_CHEST_BUTTON;
            case TERRITORY -> EnumTemplates.ALLIANCE_TERRITORY_BUTTON;
            case SHOP -> EnumTemplates.ALLIANCE_SHOP_BUTTON;
            case TECH -> EnumTemplates.ALLIANCE_TECH_BUTTON;
            case HELP -> EnumTemplates.ALLIANCE_HELP_BUTTON;
            case TRIUMPH -> EnumTemplates.ALLIANCE_TRIUMPH_BUTTON;
        };

        DTOImageSearchResult searchResult = templateSearchHelper.searchTemplate(
                menuTemplate,
                SearchConfigConstants.SINGLE_WITH_RETRIES);

        if (searchResult.isFound()) {
            emuManager.tapAtRandomPoint(
                    emulatorNumber,
                    searchResult.getPoint(),
                    searchResult.getPoint(),
                    1,
                    1000);
            return true;
        }

        return false;
    }

    /**
     * Navigates to a specific event within the Events menu.
     * 
     * <p>
     * This method handles the complete navigation flow to event screens:
     * <ol>
     * <li>Opens the Events menu from home screen</li>
     * <li>Clears any selected event tabs</li>
     * <li>Swipes left to reset carousel position</li>
     * <li>Searches for the event tab (with right swipes between attempts)</li>
     * <li>Taps the event tab when found</li>
     * </ol>
     * 
     * @param event The event to navigate to
     * @return true if navigation was successful, false otherwise
     */
    public boolean navigateToEventMenu(EventMenu event) {
        logInfo("Navigating to event: " + event.name());

        // Step 1: Open Events menu
        if (!openEventsMenu()) {
            logWarning("Failed to open Events menu");
            return false;
        }

        // Step 2: Clear any selected tabs
        clearEventTabSelection();

        // Step 3: Search for the event tab
        EnumTemplates eventTemplate = switch (event) {
            case HERO_MISSION -> EnumTemplates.HERO_MISSION_EVENT_TAB;
            case MERCENARY -> EnumTemplates.MERCENARY_EVENT_TAB;
            case ALLIANCE_CHAMPIONSHIP -> EnumTemplates.ALLIANCE_CHAMPIONSHIP_TAB;
            case ALLIANCE_MOBILIZATION -> EnumTemplates.ALLIANCE_MOBILIZATION_TAB;
            case TUNDRA_TRUCK -> EnumTemplates.TUNDRA_TRUCK_TAB;
        };

        DTOImageSearchResult eventTab = searchForEventTab(eventTemplate);

        // For Alliance Mobilization, also check for unselected tab if selected not
        // found
        if (!eventTab.isFound() && event == EventMenu.ALLIANCE_MOBILIZATION) {
            logDebug("Selected mobilization tab not found, searching for unselected tab");
            eventTab = searchForEventTab(EnumTemplates.ALLIANCE_MOBILIZATION_UNSELECTED_TAB);
        }

        if (!eventTab.isFound()) {
            logWarning("Event tab not found: " + event.name());
            return false;
        }

        // Step 4: Tap the event tab
        emuManager.tapAtPoint(emulatorNumber, eventTab.getPoint());
        sleep(1000);

        logInfo("Navigated to " + event.name());
        return true;
    }

    /**
     * Opens the Events menu from home screen.
     * 
     * @return true if Events menu opened successfully, false otherwise
     */
    private boolean openEventsMenu() {
        DTOImageSearchResult eventsButton = templateSearchHelper.searchTemplate(
                EnumTemplates.HOME_EVENTS_BUTTON,
                SearchConfigConstants.SINGLE_WITH_RETRIES);

        if (!eventsButton.isFound()) {
            logWarning("Events button not found on home screen");
            return false;
        }

        emuManager.tapAtPoint(emulatorNumber, eventsButton.getPoint());
        sleep(2000);

        logDebug("Events menu opened");
        return true;
    }

    /**
     * Clears any selected event tab to ensure clean navigation.
     * This taps multiple times in the upper-right area where tabs can be closed.
     */
    public void clearEventTabSelection() {
        logDebug("Clearing event tab selection");
        emuManager.tapAtRandomPoint(
                emulatorNumber,
                EVENTS_TAB_CLEAR_TL,
                EVENTS_TAB_CLEAR_BR,
                EVENTS_TAB_CLEAR_TAPS,
                300);
        sleep(300);
    }

    /**
     * Searches for an event tab with intelligent swiping.
     * 
     * <p>
     * Algorithm:
     * <ol>
     * <li>First checks if tab is immediately visible</li>
     * <li>Swipes completely left (3 times) to reset position</li>
     * <li>Searches while swiping right up to MAX_EVENT_SWIPE_ATTEMPTS times</li>
     * </ol>
     * 
     * @param eventTemplate The template to search for
     * @return DTOImageSearchResult containing tab location if found
     */
    private DTOImageSearchResult searchForEventTab(EnumTemplates eventTemplate) {
        // First attempt: Check if already visible
        DTOImageSearchResult result = templateSearchHelper.searchTemplate(
                eventTemplate,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (result.isFound()) {
            logDebug("Event tab found immediately");
            return result;
        }

        // Swipe completely left to reset carousel
        logDebug("Event not visible, swiping left to reset position");
        for (int i = 0; i < 3; i++) {
            emuManager.executeSwipe(
                    emulatorNumber,
                    EVENT_SWIPE_LEFT_START,
                    EVENT_SWIPE_LEFT_END);
            sleep(300);
        }

        // Search while swiping right
        logDebug("Searching for event tab while swiping right");
        for (int attempt = 0; attempt < MAX_EVENT_SWIPE_ATTEMPTS; attempt++) {
            result = templateSearchHelper.searchTemplate(
                    eventTemplate,
                    SearchConfigConstants.DEFAULT_SINGLE);

            if (result.isFound()) {
                logDebug("Event tab found after " + attempt + " swipe(s)");
                return result;
            }

            // Swipe right to see more events
            emuManager.executeSwipe(
                    emulatorNumber,
                    EVENT_SWIPE_RIGHT_START,
                    EVENT_SWIPE_RIGHT_END);
            sleep(300);
        }

        logDebug("Event tab not found after " + MAX_EVENT_SWIPE_ATTEMPTS + " swipe attempts");
        return result; // Return not found result
    }

    /**
     * Ensures the emulator is on the correct screen (Home or World) before
     * continuing.
     * 
     * <p>
     * This method will:
     * <ul>
     * <li>Verify current screen location</li>
     * <li>Navigate between Home/World if needed</li>
     * <li>Press back button if lost</li>
     * <li>Throw exception if unable to locate after max attempts</li>
     * </ul>
     * 
     * @param requiredLocation The desired screen (HOME, WORLD, or ANY)
     * @throws HomeNotFoundException            if Home/World screen cannot be found
     *                                          after max attempts
     * @throws ProfileInReconnectStateException if profile is in reconnect state
     */
    public void ensureCorrectScreenLocation(EnumStartLocation requiredLocation) {
        logDebug("Verifying screen location. Required: " + requiredLocation);

        for (int attempt = 1; attempt <= MAX_SCREEN_LOCATION_ATTEMPTS; attempt++) {
            ScreenState state = detectCurrentScreen();

            if (state == ScreenState.RECONNECT) {
                throw new ProfileInReconnectStateException(
                        "Profile " + profileName + " is in reconnect state");
            }

            if (handleScreenNavigation(state, requiredLocation, attempt)) {
                return; // Successfully on correct screen
            }

            // If we're lost, tap back and try again
            if (state == ScreenState.UNKNOWN) {
                logDebug("Home/World screen not found. Tapping back button (Attempt " +
                        attempt + "/" + MAX_SCREEN_LOCATION_ATTEMPTS + ")");
                emuManager.tapBackButton(emulatorNumber);
                sleep(300);
            }
        }

        logDebug("Failed to find Home/World screen after " + MAX_SCREEN_LOCATION_ATTEMPTS + " attempts");
        throw new HomeNotFoundException("Home not found after " + MAX_SCREEN_LOCATION_ATTEMPTS + " attempts");
    }

    /**
     * Detects the current screen state.
     * 
     * @return The current screen state
     */
    private ScreenState detectCurrentScreen() {
        DTOImageSearchResult home = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_FURNACE,
                SearchConfigConstants.DEFAULT_SINGLE);

        DTOImageSearchResult world = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_WORLD,
                SearchConfigConstants.DEFAULT_SINGLE);

        DTOImageSearchResult reconnect = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_RECONNECT,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (reconnect.isFound()) {
            return ScreenState.RECONNECT;
        }

        if (home.isFound()) {
            return ScreenState.HOME;
        }

        if (world.isFound()) {
            return ScreenState.WORLD;
        }

        return ScreenState.UNKNOWN;
    }

    /**
     * Handles navigation between screens based on current and required location.
     * 
     * @param currentState     The current screen state
     * @param requiredLocation The required screen location
     * @param attemptNumber    The current attempt number
     * @return true if on correct screen, false if navigation needed
     */
    private boolean handleScreenNavigation(
            ScreenState currentState,
            EnumStartLocation requiredLocation,
            int attemptNumber) {

        // If we're on any valid screen and ANY is acceptable, we're done
        if (requiredLocation == EnumStartLocation.ANY &&
                (currentState == ScreenState.HOME || currentState == ScreenState.WORLD)) {
            return true;
        }

        // Navigate from WORLD to HOME
        if (requiredLocation == EnumStartLocation.HOME && currentState == ScreenState.WORLD) {
            logDebug("Navigating from WORLD to HOME...");
            return navigateToHome(attemptNumber);
        }

        // Navigate from HOME to WORLD
        if (requiredLocation == EnumStartLocation.WORLD && currentState == ScreenState.HOME) {
            logDebug("Navigating from HOME to WORLD...");
            return navigateToWorld(attemptNumber);
        }

        // We're already on the correct screen
        if ((requiredLocation == EnumStartLocation.HOME && currentState == ScreenState.HOME) ||
                (requiredLocation == EnumStartLocation.WORLD && currentState == ScreenState.WORLD)) {
            return true;
        }

        return false;
    }

    /**
     * Navigates from WORLD screen to HOME screen.
     * 
     * @param attemptNumber The current attempt number
     * @return true if navigation succeeded, false otherwise
     */
    private boolean navigateToHome(int attemptNumber) {
        DTOImageSearchResult world = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_WORLD,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!world.isFound()) {
            logWarning("World button not found during HOME navigation");
            return false;
        }

        emuManager.tapAtPoint(emulatorNumber, world.getPoint());
        sleep(2000); // Wait for navigation

        // Verify we moved to HOME
        DTOImageSearchResult home = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_FURNACE,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!home.isFound()) {
            logWarning("Failed to navigate to HOME on attempt " + attemptNumber + ", retrying...");
            return false;
        }

        logDebug("Successfully navigated to HOME");
        return true;
    }

    /**
     * Navigates from HOME screen to WORLD screen.
     * 
     * @param attemptNumber The current attempt number
     * @return true if navigation succeeded, false otherwise
     */
    private boolean navigateToWorld(int attemptNumber) {
        DTOImageSearchResult home = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_FURNACE,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!home.isFound()) {
            logWarning("Home button not found during WORLD navigation");
            return false;
        }

        emuManager.tapAtPoint(emulatorNumber, home.getPoint());
        sleep(2000); // Wait for navigation

        // Verify we moved to WORLD
        DTOImageSearchResult world = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_WORLD,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!world.isFound()) {
            logWarning("Failed to navigate to WORLD on attempt " + attemptNumber + ", retrying...");
            return false;
        }

        logDebug("Successfully navigated to WORLD");
        return true;
    }

    /**
     * Sleeps for the specified duration, handling interruption.
     * 
     * @param millis Duration to sleep in milliseconds
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Enum representing possible screen states.
     */
    private enum ScreenState {
        HOME,
        WORLD,
        RECONNECT,
        UNKNOWN
    }

    /**
     * Enum representing alliance menu options.
     */
    public enum AllianceMenu {
        WAR, CHESTS, TERRITORY, SHOP, TECH, HELP, TRIUMPH
    }

    /**
     * Enum representing event menu options.
     */
    public enum EventMenu {
        HERO_MISSION,
        MERCENARY,
        ALLIANCE_CHAMPIONSHIP,
        ALLIANCE_MOBILIZATION,
        TUNDRA_TRUCK
    }

    // ========================================================================
    // LOGGING METHODS
    // ========================================================================

    private void logInfo(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.info(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.INFO, HELPER_NAME, profileName, message);
    }

    private void logWarning(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.warn(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.WARNING, HELPER_NAME, profileName, message);
    }

    @SuppressWarnings("unused")
    private void logError(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.error(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.ERROR, HELPER_NAME, profileName, message);
    }

    private void logDebug(String message) {
        String prefixedMessage = profileName + " - " + message;
        logger.debug(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.DEBUG, HELPER_NAME, profileName, message);
    }
}
