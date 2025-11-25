package cl.camodev.wosbot.serv.task.helper;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.ex.ProfileInReconnectStateException;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfiles;
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
 * <li>Ensuring correct screen location (Home/World)</li>
 * <li>Handling screen verification and recovery</li>
 * </ul>
 * 
 * @author WoS Bot
 */
public class NavigationHelper {

    private static final int MAX_SCREEN_LOCATION_ATTEMPTS = 10;

    private final TemplateSearchHelper templateSearchHelper;
    private final EmulatorManager emuManager;
    private final String emulatorNumber;
    private final ProfileLogger logger;
    private final String profileName;

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
        this.templateSearchHelper = new TemplateSearchHelper(emuManager, emulatorNumber);
        this.logger = new ProfileLogger(NavigationHelper.class, profile);
        this.profileName = profile.getName();
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
        logger.debug("Verifying screen location. Required: " + requiredLocation);

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
                logger.warn("Home/World screen not found. Tapping back button (Attempt " +
                        attempt + "/" + MAX_SCREEN_LOCATION_ATTEMPTS + ")");
                emuManager.tapBackButton(emulatorNumber);
                sleep(100);
            }
        }

        logger.error("Failed to find Home/World screen after " + MAX_SCREEN_LOCATION_ATTEMPTS + " attempts");
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
            logger.info("Navigating from WORLD to HOME...");
            return navigateToHome(attemptNumber);
        }

        // Navigate from HOME to WORLD
        if (requiredLocation == EnumStartLocation.WORLD && currentState == ScreenState.HOME) {
            logger.info("Navigating from HOME to WORLD...");
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
            logger.warn("World button not found during HOME navigation");
            return false;
        }

        emuManager.tapAtPoint(emulatorNumber, world.getPoint());
        sleep(2000); // Wait for navigation

        // Verify we moved to HOME
        DTOImageSearchResult home = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_FURNACE,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!home.isFound()) {
            logger.warn("Failed to navigate to HOME on attempt " + attemptNumber + ", retrying...");
            return false;
        }

        logger.info("Successfully navigated to HOME");
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
            logger.warn("Home button not found during WORLD navigation");
            return false;
        }

        emuManager.tapAtPoint(emulatorNumber, home.getPoint());
        sleep(2000); // Wait for navigation

        // Verify we moved to WORLD
        DTOImageSearchResult world = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_WORLD,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!world.isFound()) {
            logger.warn("Failed to navigate to WORLD on attempt " + attemptNumber + ", retrying...");
            return false;
        }

        logger.info("Successfully navigated to WORLD");
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
}
