package cl.camodev.wosbot.serv.task.helper;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.CommonGameAreas;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

/**
 * Helper class for Alliance-related operations.
 * 
 * <p>This helper encapsulates Alliance-specific functionality that doesn't
 * fit into the general NavigationHelper, including:
 * <ul>
 *   <li>Disabling alliance war auto-join</li>
 *   <li>Managing alliance war rally settings</li>
 * </ul>
 * 
 * @author WoS Bot
 * @see CommonGameAreas
 */
public class AllianceHelper {

    private final EmulatorManager emuManager;
    private final String emulatorNumber;
    private final TemplateSearchHelper templateSearchHelper;
    private final NavigationHelper navigationHelper;
    private final ProfileLogger logger;

    /**
     * Constructs a new AllianceHelper.
     * 
     * @param emuManager The emulator manager instance
     * @param emulatorNumber The identifier for the emulator
     * @param templateSearchHelper Helper for template matching
     * @param navigationHelper Helper for screen navigation
     * @param profile The profile this helper operates on
     */
    public AllianceHelper(
            EmulatorManager emuManager,
            String emulatorNumber,
            TemplateSearchHelper templateSearchHelper,
            NavigationHelper navigationHelper,
            DTOProfiles profile) {
        this.emuManager = emuManager;
        this.emulatorNumber = emulatorNumber;
        this.templateSearchHelper = templateSearchHelper;
        this.navigationHelper = navigationHelper;
        this.logger = new ProfileLogger(AllianceHelper.class, profile);
    }

    /**
     * Disables alliance war auto-join functionality.
     * 
     * <p><b>Navigation Flow:</b>
     * <ol>
     *   <li>Ensure on correct screen (home or world)</li>
     *   <li>Navigate to Alliance screen</li>
     *   <li>Open Alliance War menu</li>
     *   <li>Navigate to Rally section</li>
     *   <li>Open auto-join settings</li>
     *   <li>Disable auto-join</li>
     *   <li>Return to previous screen</li>
     * </ol>
     * 
     * <p><b>Note:</b> This method may be called mid-task, so it uses manual
     * back button navigation instead of relying on automatic screen cleanup.
     * 
     * @return true if auto-join was successfully disabled, false if navigation failed
     */
    public boolean disableAutoJoin() {
     navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.ANY);
        
        logger.debug("Navigating to Alliance screen to disable auto-join");
        
        if (!navigateToAllianceWarRally()) {
            return false;
        }
        
        if (!openAutoJoinSettings()) {
            return false;
        }
        
        disableAutoJoinToggle();
        returnToHomeScreen();
        
        logger.info("Successfully disabled alliance auto-join");
        return true;
    }

    /**
     * Navigates to the Alliance War Rally section.
     * 
     * @return true if navigation succeeded, false otherwise
     */
    private boolean navigateToAllianceWarRally() {
        // Tap Alliance button
        logger.debug("Opening Alliance menu");
        emuManager.tapAtRandomPoint(
                emulatorNumber,
                CommonGameAreas.BOTTOM_MENU_ALLIANCE_BUTTON.topLeft(),
                CommonGameAreas.BOTTOM_MENU_ALLIANCE_BUTTON.bottomRight()
        );
        sleep(3000); // Wait for Alliance screen to load
        
        // Locate Alliance War button
        DTOImageSearchResult warButton = templateSearchHelper.searchTemplate(
                EnumTemplates.ALLIANCE_WAR_BUTTON,
                SearchConfigConstants.SINGLE_WITH_RETRIES
        );
        
        if (!warButton.isFound()) {
            logger.error("Alliance War button not found");
            return false;
        }
        
        // Open Alliance War menu
        logger.debug("Opening Alliance War menu");
        emuManager.tapAtPoint(emulatorNumber, warButton.getPoint());
        sleep(500); // Wait for War menu to open
        
        // Navigate to Rally section
        logger.debug("Opening Rally section");
        emuManager.tapAtRandomPoint(
                emulatorNumber,
                CommonGameAreas.ALLIANCE_WAR_RALLY_TAB.topLeft(),
                CommonGameAreas.ALLIANCE_WAR_RALLY_TAB.bottomRight()
        );
        sleep(500); // Wait for Rally section to load
        
        return true;
    }

    /**
     * Opens the auto-join settings popup.
     * 
     * @return true if settings popup opened, false otherwise
     */
    private boolean openAutoJoinSettings() {
        logger.debug("Opening auto-join settings");
        
        emuManager.tapAtRandomPoint(
                emulatorNumber,
                CommonGameAreas.ALLIANCE_AUTOJOIN_MENU_BUTTON.topLeft(),
                CommonGameAreas.ALLIANCE_AUTOJOIN_MENU_BUTTON.bottomRight()
        );
        sleep(1000); // Wait for popup to appear
        
        return true;
    }

    /**
     * Taps the disable button in the auto-join settings popup.
     */
    private void disableAutoJoinToggle() {
        logger.debug("Disabling auto-join");
        
        emuManager.tapAtRandomPoint(
                emulatorNumber,
                CommonGameAreas.ALLIANCE_AUTOJOIN_DISABLE_BUTTON.topLeft(),
                CommonGameAreas.ALLIANCE_AUTOJOIN_DISABLE_BUTTON.bottomRight()
        );
        sleep(300); // Wait for disable action to register
    }

    /**
     * Returns to home screen by tapping back button multiple times.
     * 
     * <p>Uses manual navigation because this method may be called mid-task
     * and cannot rely on DelayedTask's automatic screen cleanup.
     */
    private void returnToHomeScreen() {
        logger.debug("Returning to home screen");
        
        emuManager.tapBackButton(emulatorNumber);
        sleep(300);
        
        emuManager.tapBackButton(emulatorNumber);
        sleep(300);
        
        emuManager.tapBackButton(emulatorNumber);
        sleep(300);
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
}
