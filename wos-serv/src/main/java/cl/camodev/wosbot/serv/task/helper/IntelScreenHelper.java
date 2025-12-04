package cl.camodev.wosbot.serv.task.helper;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import net.sourceforge.tess4j.TesseractException;

import java.io.IOException;

/**
 * Helper class for navigating to and verifying the Intel screen.
 * 
 * <p>The Intel screen is where players manage reconnaissance missions,
 * beast hunting, survivor rescue, and exploration tasks. This helper
 * provides reliable navigation and verification methods.
 * 
 * <p><b>Verification Methods:</b>
 * <ul>
 *   <li>Image template matching (primary)</li>
 *   <li>OCR text verification (fallback)</li>
 * </ul>
 * 
 * @author WoS Bot
 */
public class IntelScreenHelper {

    private static final int MAX_NAVIGATION_ATTEMPTS = 3;
    private static final int MAX_VERIFICATION_ATTEMPTS = 2;

    private final EmulatorManager emuManager;
    private final String emulatorNumber;
    private final TemplateSearchHelper templateSearchHelper;
    private final NavigationHelper navigationHelper;
    private final ProfileLogger logger;

    /**
     * Constructs a new IntelScreenHelper.
     * 
     * @param emuManager The emulator manager instance
     * @param emulatorNumber The identifier for the emulator
     * @param templateSearchHelper Helper for template matching
     * @param screenNavHelper Helper for screen navigation
     * @param profile The profile this helper operates on
     */
    public IntelScreenHelper(
            EmulatorManager emuManager,
            String emulatorNumber,
            TemplateSearchHelper templateSearchHelper,
            NavigationHelper navigationHelper,
            DTOProfiles profile) {
        this.emuManager = emuManager;
        this.emulatorNumber = emulatorNumber;
        this.templateSearchHelper = templateSearchHelper;
        this.navigationHelper = navigationHelper;
        this.logger = new ProfileLogger(IntelScreenHelper.class, profile);
    }

    /**
     * Ensures the game is currently displaying the Intel screen.
     * 
     * <p>If already on the Intel screen, returns immediately.
     * Otherwise, navigates from the world screen by locating and
     * tapping the Intel button.
     * 
     * <p><b>Navigation Flow:</b>
     * <ol>
     *   <li>Check if already on Intel screen (fast path)</li>
     *   <li>Navigate to world screen if needed</li>
     *   <li>Locate and tap Intel button</li>
     *   <li>Verify Intel screen opened successfully</li>
     * </ol>
     * 
     * @throws HomeNotFoundException if Intel button cannot be found or
     *         navigation to Intel screen fails after maximum attempts
     */
    public void ensureOnIntelScreen() {
        try {
            Thread.sleep(500); // Brief stabilization delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        logger.info("Ensuring we are on the Intel screen");
        
        // Fast path: already on Intel screen
        if (isIntelScreenActive()) {
            logger.info("Already on the Intel screen");
            return;
        }
        
        logger.warn("Not on Intel screen. Attempting to navigate");
        
        // Ensure we're on the world screen to access Intel button
        navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.WORLD);
        
        // Attempt to find and tap Intel button
        for (int attempt = 0; attempt < MAX_NAVIGATION_ATTEMPTS; attempt++) {
            if (attemptIntelNavigation(attempt + 1)) {
                logger.info("Successfully navigated to the Intel screen");
                return;
            }
        }
        
        logger.error("Failed to navigate to Intel screen after " + MAX_NAVIGATION_ATTEMPTS + " attempts");
        throw new HomeNotFoundException("Failed to navigate to Intel screen");
    }

    /**
     * Attempts a single navigation sequence to the Intel screen.
     * 
     * @param attemptNumber The current attempt number (for logging)
     * @return true if navigation succeeded, false otherwise
     */
    private boolean attemptIntelNavigation(int attemptNumber) {
        logger.debug("Intel navigation attempt " + attemptNumber + "/" + MAX_NAVIGATION_ATTEMPTS);
        
        DTOImageSearchResult intelButton = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_INTEL,
                SearchConfigConstants.DEFAULT_SINGLE
        );
        
        if (!intelButton.isFound()) {
            logger.debug("Intel button not found on attempt " + attemptNumber);
            sleep(300); // Wait before retry
            return false;
        }
        
        logger.info("Intel button found. Tapping to open Intel screen");
        emuManager.tapAtPoint(emulatorNumber, intelButton.getPoint());
        sleep(1000); // Wait for screen transition
        
        // Verify navigation succeeded
        if (isIntelScreenActive()) {
            return true;
        }
        
        logger.warn("Tapped Intel button, but Intel screen did not open. Retrying...");
        emuManager.tapBackButton(emulatorNumber);
        sleep(500); // Wait before next attempt
        
        return false;
    }

    /**
     * Checks if the Intel screen is currently active.
     * 
     * <p>Uses a two-pronged verification approach:
     * <ol>
     *   <li><b>Template matching</b> - Searches for Intel screen UI elements</li>
     *   <li><b>OCR verification</b> - Reads "Intel" text from screen title (fallback)</li>
     * </ol>
     * 
     * <p>Makes two verification attempts with a brief delay between them
     * to handle UI rendering delays.
     * 
     * @return true if Intel screen is active, false otherwise
     */
    public boolean isIntelScreenActive() {
        for (int attempt = 0; attempt < MAX_VERIFICATION_ATTEMPTS; attempt++) {
            if (verifyIntelScreenViaTemplate()) {
                logger.debug("Intel screen confirmed via template (attempt " + (attempt + 1) + ")");
                return true;
            }
            
            if (verifyIntelScreenViaOCR()) {
                logger.debug("Intel screen confirmed via OCR (attempt " + (attempt + 1) + ")");
                return true;
            }
            
            // Wait briefly before second attempt
            if (attempt == 0) {
                sleep(300);
            }
        }
        
        logger.debug("Intel screen not detected after " + MAX_VERIFICATION_ATTEMPTS + " attempts");
        return false;
    }

    /**
     * Verifies Intel screen using template matching.
     * 
     * @return true if Intel screen templates are found, false otherwise
     */
    private boolean verifyIntelScreenViaTemplate() {
        DTOImageSearchResult intelScreen1 = templateSearchHelper.searchTemplate(
                EnumTemplates.INTEL_SCREEN_1,
                SearchConfigConstants.DEFAULT_SINGLE
        );
        
        DTOImageSearchResult intelScreen2 = templateSearchHelper.searchTemplate(
                EnumTemplates.INTEL_SCREEN_2,
                SearchConfigConstants.DEFAULT_SINGLE
        );
        
        return intelScreen1.isFound() || intelScreen2.isFound();
    }

    /**
     * Verifies Intel screen using OCR text recognition.
     * 
     * <p>Reads the screen title area and checks for "intel" text.
     * 
     * @return true if "intel" text is found in title, false otherwise
     */
    private boolean verifyIntelScreenViaOCR() {
        try {
            String intelText = emuManager.ocrRegionText(
                    emulatorNumber,
                    new DTOPoint(85, 15),
                    new DTOPoint(171, 62)
            );
            
            return intelText != null && intelText.toLowerCase().contains("intel");
            
        } catch (IOException | TesseractException e) {
            logger.warn("Could not perform OCR to check for Intel screen: " + e.getMessage());
            return false;
        }
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
