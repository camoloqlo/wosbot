package cl.camodev.wosbot.serv.task.helper;

import cl.camodev.utiles.UtilRally;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.constants.CommonGameAreas;
import net.sourceforge.tess4j.TesseractException;

import java.io.IOException;
import java.util.Objects;

/**
 * Helper class for managing march-related operations in the game.
 * 
 * <p>This helper encapsulates all march management functionality including:
 * <ul>
 *   <li>Checking march slot availability</li>
 *   <li>Selecting rally flags</li>
 *   <li>Opening and closing the left menu</li>
 *   <li>Navigating between city and wilderness tabs</li>
 * </ul>
 * 
 * <p>March checking is performed via OCR on the left menu wilderness tab,
 * which displays the status of all 6 march slots.
 * 
 * @author WoS Bot
 * @see CommonGameAreas
 */
public class MarchHelper {

    private static final int TOTAL_MARCH_SLOTS = 6;
    private static final int OCR_RETRY_ATTEMPTS = 3;

    private final EmulatorManager emuManager;
    private final String emulatorNumber;
    private final TextRecognitionRetrier<String> stringHelper;
    private final ProfileLogger logger;

    /**
     * Constructs a new MarchHelper.
     * 
     * @param emuManager The emulator manager instance
     * @param emulatorNumber The identifier for the emulator
     * @param stringHelper OCR helper for reading text values
     * @param profile The profile this helper operates on
     */
    public MarchHelper(
            EmulatorManager emuManager,
            String emulatorNumber,
            TextRecognitionRetrier<String> stringHelper,
            DTOProfiles profile) {
        this.emuManager = emuManager;
        this.emulatorNumber = emulatorNumber;
        this.stringHelper = stringHelper;
        this.logger = new ProfileLogger(MarchHelper.class, profile);
    }

    /**
     * Checks if any march slots are currently idle and available for deployment.
     * 
     * <p><b>Process:</b>
     * <ol>
     *   <li>Opens the left menu wilderness section</li>
     *   <li>Checks each of the 6 march slots via OCR</li>
     *   <li>Returns true if any slot shows "idle" status</li>
     *   <li>Closes the left menu before returning</li>
     * </ol>
     * 
     * <p>March slots are checked from top to bottom (slot 6 → slot 1).
     * Each slot is checked up to 3 times to handle OCR variability.
     * 
     * @return true if at least one march is idle, false if all are busy or on error
     */
    public boolean checkMarchesAvailable() {
        openLeftMenuCitySection(false); // Open wilderness tab
        
        try {
            for (int marchSlot = 0; marchSlot < TOTAL_MARCH_SLOTS; marchSlot++) {
                if (isMarchSlotIdle(marchSlot)) {
                    int slotNumber = TOTAL_MARCH_SLOTS - marchSlot;
                    logger.info("Idle march detected in slot " + slotNumber);
                    closeLeftMenu();
                    return true;
                }
                
                int slotNumber = TOTAL_MARCH_SLOTS - marchSlot;
                logger.debug("March slot " + slotNumber + " is not idle");
            }
        } catch (Exception e) {
            logger.error("Error while checking marches: " + e.getMessage());
            closeLeftMenu();
            return false;
        }
        
        logger.info("No idle marches detected in any of the " + TOTAL_MARCH_SLOTS + " slots");
        closeLeftMenu();
        return false;
    }

    /**
     * Checks if a specific march slot is idle via OCR.
     * 
     * <p>Performs multiple OCR attempts (up to 3) on the slot to handle
     * text rendering variability.
     * 
     * @param slotIndex The slot index (0-5, where 0 is slot 6, 5 is slot 1)
     * @return true if the slot shows "idle" status, false otherwise
     */
    private boolean isMarchSlotIdle(int slotIndex) {
        DTOPoint topLeft = CommonGameAreas.MARCH_SLOTS_TOP_LEFT[slotIndex];
        DTOPoint bottomRight = CommonGameAreas.MARCH_SLOTS_BOTTOM_RIGHT[slotIndex];
        
        for (int attempt = 0; attempt < OCR_RETRY_ATTEMPTS; attempt++) {
            try {
                String ocrResult = emuManager.ocrRegionText(
                        emulatorNumber,
                        topLeft,
                        bottomRight
                );
                
                if (ocrResult != null && ocrResult.toLowerCase().contains("idle")) {
                    return true;
                }
                
                if (attempt < OCR_RETRY_ATTEMPTS - 1) {
                    Thread.sleep(100); // Brief delay before retry
                }
            } catch (IOException | TesseractException e) {
                logger.debug("OCR attempt " + (attempt + 1) + " failed for slot " + 
                           slotIndex + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while checking march slot");
                return false;
            }
        }
        
        return false;
    }

    /**
     * Selects a rally flag for march deployment.
     * 
     * <p>This method taps on the specified flag and verifies it's unlocked.
     * If the flag shows "Unlock" text (meaning it's locked), it taps back
     * and proceeds without flag selection.
     * 
     * @param flagNumber The flag number to select (1-8)
     */
    public void selectFlag(Integer flagNumber) {
        if (flagNumber == null) {
            logger.debug("No flag number specified, skipping flag selection");
            return;
        }
        
        logger.debug("Selecting rally flag " + flagNumber);
        
        DTOPoint flagPoint = UtilRally.getMarchFlagPoint(flagNumber);
        emuManager.tapAtPoint(emulatorNumber, flagPoint);
        
        try {
            Thread.sleep(300); // Wait for flag selection UI
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Check if flag is locked
        String flagStatus = stringHelper.execute(
                CommonGameAreas.FLAG_UNLOCK_TEXT_OCR.topLeft(),
                CommonGameAreas.FLAG_UNLOCK_TEXT_OCR.bottomRight(),
                3,      // Max retry attempts
                200L,   // Delay between retries
                null,   // Use default OCR settings
                Objects::nonNull,
                text -> text
        );
        
        if (flagStatus != null && flagStatus.toLowerCase().contains("unlock")) {
            logger.warn("Flag " + flagNumber + " is locked. Proceeding without flag selection.");
            emuManager.tapBackButton(emulatorNumber);
        } else {
            logger.debug("Flag " + flagNumber + " selected successfully");
        }
    }

    /**
     * Opens the left menu and navigates to the specified tab.
     * 
     * <p>The left menu contains two tabs:
     * <ul>
     *   <li><b>City tab</b>: Shows city buildings and upgrades</li>
     *   <li><b>Wilderness tab</b>: Shows march slots and resource info</li>
     * </ul>
     * 
     * @param cityTab true to open city tab, false to open wilderness tab
     */
    public void openLeftMenuCitySection(boolean cityTab) {
        logger.debug("Opening left menu - " + (cityTab ? "City" : "Wilderness") + " tab");
        
        // Trigger left menu (tap multiple times for reliability)
        emuManager.tapAtRandomPoint(
                emulatorNumber,
                CommonGameAreas.LEFT_MENU_TRIGGER.topLeft(),
                CommonGameAreas.LEFT_MENU_TRIGGER.bottomRight(),
                3,      // Multiple taps
                400     // Delay between taps
        );
        
        // Select appropriate tab
        if (cityTab) {
            emuManager.tapAtRandomPoint(
                    emulatorNumber,
                    CommonGameAreas.LEFT_MENU_CITY_TAB.topLeft(),
                    CommonGameAreas.LEFT_MENU_CITY_TAB.bottomRight(),
                    3,      // Multiple taps for reliability
                    100     // Short delay between taps
            );
        } else {
            emuManager.tapAtRandomPoint(
                    emulatorNumber,
                    CommonGameAreas.LEFT_MENU_WILDERNESS_TAB.topLeft(),
                    CommonGameAreas.LEFT_MENU_WILDERNESS_TAB.bottomRight(),
                    3,      // Multiple taps for reliability
                    100     // Short delay between taps
            );
        }
    }

    /**
     * Closes the left menu by tapping on designated close points.
     * 
     * <p>Uses a two-step close sequence:
     * <ol>
     *   <li>Tap city tab area to deselect current tab</li>
     *   <li>Tap outside menu area to fully close menu</li>
     * </ol>
     */
    public void closeLeftMenu() {
        logger.debug("Closing left menu");
        
        // First tap: deselect tab
        emuManager.tapAtPoint(emulatorNumber, CommonGameAreas.LEFT_MENU_CLOSE_CITY);
        
        try {
            Thread.sleep(500); // Wait for menu transition
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Second tap: close menu completely
        emuManager.tapAtPoint(emulatorNumber, CommonGameAreas.LEFT_MENU_CLOSE_OUTSIDE);
        
        try {
            Thread.sleep(500); // Wait for menu to close
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
