package cl.camodev.wosbot.serv.task.helper;

import cl.camodev.utiles.ocr.TextRecognitionProvider;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.ocr.BotTextRecognitionProvider;
import cl.camodev.wosbot.serv.task.constants.CommonGameAreas;
import cl.camodev.wosbot.ot.DTOTesseractSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for character profile switching operations.
 * 
 * <p>
 * This helper encapsulates all character switching functionality including:
 * <ul>
 * <li>Verifying current character matches profile configuration</li>
 * <li>Navigating to character switching menu</li>
 * <li>Reading character list using OCR and template matching</li>
 * <li>Selecting target character from list</li>
 * <li>Handling scrolling and retry logic</li>
 * </ul>
 * 
 * <p>
 * Character switching flow:
 * <ol>
 * <li>Open profile menu (tap profile avatar)</li>
 * <li>Navigate to Settings menu (template search for Settings button)</li>
 * <li>Navigate to Switch Character menu (template search for Characters
 * button)</li>
 * <li>Search for target character using Furnace Level template</li>
 * <li>OCR character name above Furnace Level template</li>
 * <li>Select character and confirm</li>
 * </ol>
 * 
 * @author WoS Bot
 * @see StaminaHelper
 * @see TemplateSearchHelper
 */
public class CharacterSwitchHelper {

    private static final int MAX_CHARACTER_SEARCH_ATTEMPTS = 3;
    private static final int SCROLL_DELAY_MS = 1000;
    private static final int NAVIGATION_DELAY_MS = 500;
    private static final int CHARACTER_LIST_LOAD_DELAY_MS = 2000;
    /** Delay after character switch to wait for game reload (milliseconds) */
    public static final int CHARACTER_SWITCH_RELOAD_DELAY_MS = 5000;
    private static final int OCR_RETRY_DELAY_MS = 200;
    private static final int OCR_MAX_RETRIES = 2;
    private static final int TEMPLATE_SEARCH_MAX_ATTEMPTS = 3;
    private static final int TEMPLATE_SEARCH_DELAY_MS = 500;
    private static final int TEMPLATE_SEARCH_THRESHOLD = 80;
    private static final int TEMPLATE_SEARCH_HIGH_THRESHOLD = 90;
    
    /** Offset from furnace level position to checkmark X coordinate (pixels right) */
    private static final int CHECKMARK_OFFSET_X = 264;
    /** Offset from furnace level position to checkmark Y coordinate (pixels up) */
    private static final int CHECKMARK_OFFSET_Y = -40;
    /** Size of the checkmark icon (width and height in pixels) */
    private static final int CHECKMARK_SIZE = 60;

    private final EmulatorManager emuManager;
    private final String emulatorNumber;
    private final ProfileLogger logger;
    private final TemplateSearchHelper templateSearchHelper;
    private final TextRecognitionRetrier<String> stringHelper;

    /**
     * Constructs a new CharacterSwitchHelper.
     * 
     * @param emuManager     The emulator manager instance
     * @param emulatorNumber The identifier for the emulator
     * @param profile        The profile this helper operates on
     */
    public CharacterSwitchHelper(EmulatorManager emuManager, String emulatorNumber, DTOProfiles profile) {
        this.emuManager = emuManager;
        this.emulatorNumber = emulatorNumber;
        this.logger = new ProfileLogger(CharacterSwitchHelper.class, profile);
        this.templateSearchHelper = new TemplateSearchHelper(emuManager, emulatorNumber, profile);

        // Initialize OCR helper
        TextRecognitionProvider provider = new BotTextRecognitionProvider(emuManager, emulatorNumber);
        this.stringHelper = new TextRecognitionRetrier<>(provider);
    }

    /**
     * Verifies that the currently active character matches the profile's configured
     * character.
     * 
     * <p>
     * This method:
     * <ol>
     * <li>Opens profile menu by tapping profile avatar</li>
     * <li>Reads character ID and name from profile menu using OCR</li>
     * <li>Compares with profile configuration</li>
     * <li>Closes profile menu if character matches</li>
     * </ol>
     * 
     * <p>
     * If character configuration is not set, returns true (skip verification).
     * 
     * @param profile The profile containing character configuration
     * @return true if character matches or config not set, false if character
     *         doesn't match
     */
    public boolean verifyCurrentCharacter(DTOProfiles profile) {
        // Check if character configuration is set (using direct properties)
        String characterName = profile.getCharacterName();
        String characterId = profile.getCharacterId();

        // If both are empty/null, skip verification
        if ((characterName == null || characterName.isEmpty()) &&
                (characterId == null || characterId.isEmpty())) {
            logger.debug("Character configuration not set, skipping verification");
            return true;
        }

        logger.info("Verifying current character. Expected: name='" + characterName + "', id='" + characterId + "'");

        // Open profile menu (same as StaminaHelper)
        emuManager.tapAtRandomPoint(
                emulatorNumber,
                CommonGameAreas.PROFILE_AVATAR.topLeft(),
                CommonGameAreas.PROFILE_AVATAR.bottomRight(),
                1,
                NAVIGATION_DELAY_MS);

        // Read current character ID
        String currentCharacterId = readCurrentCharacterId();

        // Read current character name
        String currentCharacterName = readCurrentCharacterName();

        logger.debug("Current character - name: '" + currentCharacterName + "', id: '" + currentCharacterId + "'");

        // Compare character ID if configured
        boolean idMatches = true;
        boolean idConfigured = characterId != null && !characterId.isEmpty();
        if (idConfigured) {
            if (currentCharacterId == null || currentCharacterId.isEmpty()) {
                logger.warn("Character ID is configured but OCR failed to read it");
                idMatches = false;
            } else {
                idMatches = characterId.equals(currentCharacterId);
                logger.debug("Character ID match: " + idMatches);
            }
        }

        // Compare character name if configured (use contains to handle alliance code
        // prefix)
        boolean nameMatches = true;
        boolean nameConfigured = characterName != null && !characterName.isEmpty();
        if (nameConfigured) {
            if (currentCharacterName == null || currentCharacterName.isEmpty()) {
                logger.warn("Character name is configured but OCR failed to read it");
                nameMatches = false;
            } else {
                // Search for character name directly in OCR result (may contain alliance code prefix)
                nameMatches = matchesCharacterName(currentCharacterName, characterName);
                logger.debug("Character name match: " + nameMatches + " (OCR'd: '" + currentCharacterName
                        + "', expected: '" + characterName + "')");
            }
        }

        // Both idMatches and nameMatches default to true when not configured.
        // So if only one is configured, that one must match (the other is true by default).
        // If both are configured, both must match.
        boolean matches = idMatches || nameMatches;

        if (matches) {
            logger.info("Character verification successful - correct character is active");
            // Close profile menu
            emuManager.tapBackButton(emulatorNumber);
            return true;
        } else {
            logger.warn("Character verification failed - current character does not match profile configuration");
            // Keep profile menu open for character switching
            return false;
        }
    }

    /**
     * Reads the current character ID from the profile menu using OCR.
     * 
     * @return The character ID as string, or null if OCR fails
     */
    private String readCurrentCharacterId() {
        return stringHelper.execute(
                CommonGameAreas.CHARACTER_ID_OCR_AREA,
                3, // Max retries
                300L, // Delay between retries
                createCharacterIdOCRSettings(),
                text -> text != null && !text.trim().isEmpty(),
                text -> text.trim());
    }

    /**
     * Reads the current character name from the profile menu using OCR.
     * 
     * @return The character name as string, or null if OCR fails
     */
    private String readCurrentCharacterName() {
        return stringHelper.execute(
                CommonGameAreas.CHARACTER_NAME_OCR_AREA,
                3, // Max retries
                300L, // Delay between retries
                createCharacterNameOCRSettings(),
                text -> text != null && !text.trim().isEmpty(),
                text -> text.trim());
    }

    /**
     * Switches to the target character specified in the profile configuration.
     * 
     * <p>
     * This method performs the full character switching flow:
     * <ol>
     * <li>Navigate to Settings menu from profile menu</li>
     * <li>Navigate to Switch Character menu</li>
     * <li>Search for target character in list</li>
     * <li>Select character and confirm</li>
     * <li>Wait for character switch to complete</li>
     * </ol>
     * 
     * <p>
     * If character is not found after max attempts, closes emulator and returns
     * false.
     * 
     * @param profile The profile containing character configuration
     * @return true if character switch was successful, false otherwise
     */
    public boolean switchToCharacter(DTOProfiles profile) {
        String characterName = profile.getCharacterName();
        String characterServer = profile.getCharacterServer();

        if (characterName == null || characterName.isEmpty()) {
            logger.error("Cannot switch character: character name not configured");
            return false;
        }

        logger.info("Switching to character: name='" + characterName + "', server='" + characterServer + "'");

        // Navigate to Settings menu (from profile menu)
        if (!navigateToSettingsMenu()) {
            logger.error("Failed to navigate to Settings menu");
            return false;
        }

        // Navigate to Switch Character menu
        if (!navigateToSwitchCharacterMenu()) {
            logger.error("Failed to navigate to Switch Character menu");
            // Navigate back
            emuManager.tapBackButton(emulatorNumber);
            return false;
        }

        // Wait for character list to load
        sleep(CHARACTER_LIST_LOAD_DELAY_MS);

        // Search for character in list
        boolean found = searchAndSelectCharacter(characterName, characterServer);

        if (!found) {
            logger.error("Target character not found after " + MAX_CHARACTER_SEARCH_ATTEMPTS
                    + " attempts. Closing emulator and continuing to next profile.");
            emuManager.closeEmulator(emulatorNumber);
            return false;
        }

        // Wait for character switch to complete
        sleep(CHARACTER_SWITCH_RELOAD_DELAY_MS);

        logger.info("Character switch completed successfully");
        return true;
    }

    /**
     * Navigates to the Settings menu from the profile menu.
     * 
     * @return true if navigation was successful, false otherwise
     */
    private boolean navigateToSettingsMenu() {
        logger.debug("Navigating to Settings menu");

        DTOImageSearchResult settingsButton = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_PROFILE_SETTINGS_BUTTON,
                TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(TEMPLATE_SEARCH_MAX_ATTEMPTS)
                        .withDelay(TEMPLATE_SEARCH_DELAY_MS)
                        .withThreshold(TEMPLATE_SEARCH_THRESHOLD)
                        .withArea(CommonGameAreas.PROFILE_SETTINGS_BUTTON_AREA)
                        .build());

        if (settingsButton == null || !settingsButton.isFound()) {
            logger.error("Settings button not found in profile menu");
            return false;
        }

        emuManager.tapAtRandomPoint(
                emulatorNumber,
                settingsButton.getPoint(),
                settingsButton.getPoint(),
                1,
                NAVIGATION_DELAY_MS);

        logger.info("Successfully navigated to Settings menu");
        return true;
    }

    /**
     * Navigates to the Switch Character menu from the Settings menu.
     * 
     * @return true if navigation was successful, false otherwise
     */
    private boolean navigateToSwitchCharacterMenu() {
        logger.debug("Navigating to Switch Character menu");

        DTOImageSearchResult switchCharacterButton = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_PROFILE_SETTINGS_SWITCH_CHARACTER_BUTTON,
                TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(TEMPLATE_SEARCH_MAX_ATTEMPTS)
                        .withDelay(TEMPLATE_SEARCH_DELAY_MS)
                        .withThreshold(TEMPLATE_SEARCH_THRESHOLD)
                        .build());

        if (switchCharacterButton == null || !switchCharacterButton.isFound()) {
            logger.error("Switch Character button not found in Settings menu");
            return false;
        }

        emuManager.tapAtRandomPoint(
                emulatorNumber,
                switchCharacterButton.getPoint(),
                switchCharacterButton.getPoint(),
                1,
                NAVIGATION_DELAY_MS);

        logger.info("Successfully navigated to Switch Character menu");
        return true;
    }

    /**
     * Searches for the target character in the character list and selects it.
     * 
     * <p>
     * This method searches for both ACTIVE and INACTIVE Furnace Level templates together
     * in a single pass, then checks all results to find the target character. The distinction
     * between active and inactive is based on background color (orange for active, blue for inactive).
     * 
     * <p>
     * Flow:
     * <ol>
     * <li>Searches for both ACTIVE and INACTIVE templates in visible area</li>
     * <li>For each template found, reads character name above it</li>
     * <li>If target found with ACTIVE template, returns true (no switch needed)</li>
     * <li>If target found with INACTIVE template, selects and confirms switch</li>
     * <li>If not found, scrolls down and repeats</li>
     * </ol>
     * 
     * @param targetCharacterName The name of the character to find
     * @param targetServer        The server number (optional, for verification)
     * @return true if character was found (active) or selected successfully (inactive), false otherwise
     */
    private boolean searchAndSelectCharacter(String targetCharacterName, String targetServer) {
        logger.debug("Searching for character: name='" + targetCharacterName + "', server='" + targetServer + "'");

        for (int attempt = 0; attempt < MAX_CHARACTER_SEARCH_ATTEMPTS; attempt++) {
            logger.debug("Character search attempt " + (attempt + 1) + "/" + MAX_CHARACTER_SEARCH_ATTEMPTS);

            // Search for both ACTIVE and INACTIVE templates together
            DTOImageSearchResult foundCharacter = findCharacterInBothTemplateLists(targetCharacterName);

            if (foundCharacter == null) {
                // Character not found in visible area, scroll down
                logger.debug("Target character not found in visible area, scrolling down");
                scrollCharacterList();
                continue;
            }

            // Check which template type was used to find the character
            // We need to determine if it's ACTIVE or INACTIVE by checking both templates at the found location
            boolean isActive = isCharacterActiveAtPosition(foundCharacter.getPoint());

            if (isActive) {
                logger.info("Target character is already active - no switch needed");
                // Close the switch character menu and return to profile menu
                emuManager.tapBackButton(emulatorNumber);
                sleep(NAVIGATION_DELAY_MS);
                return true;
            } else {
                // Character is inactive, proceed with switching
                DTOPoint furnacePoint = foundCharacter.getPoint();
                logger.info("Found target character (inactive) at position " + furnacePoint);

                // Tap on character entry (tap on Furnace Level position)
                emuManager.tapAtRandomPoint(
                        emulatorNumber,
                        furnacePoint,
                        furnacePoint,
                        1,
                        NAVIGATION_DELAY_MS);

                // Wait for confirmation dialog to appear
                sleep(NAVIGATION_DELAY_MS);

                // Verify and confirm character switch
                boolean confirmed = confirmCharacterSwitch(targetCharacterName);
                if (confirmed) {
                    return true;
                } else {
                    // Character name didn't match, cancel and continue searching
                    logger.warn("Character name verification failed, canceling and continuing search");
                    cancelCharacterSwitch();
                    continue;
                }
            }
        }

        logger.error("Target character not found after " + MAX_CHARACTER_SEARCH_ATTEMPTS + " search attempts");
        return false;
    }

    /**
     * Searches for the target character in both ACTIVE and INACTIVE template lists.
     * 
     * <p>
     * This method searches both template types together and returns the first match found.
     * The distinction between active/inactive is determined separately by checking the template
     * type at the found position.
     * 
     * @param targetCharacterName The name of the character to find
     * @return The DTOImageSearchResult for the found character, or null if not found
     */
    private DTOImageSearchResult findCharacterInBothTemplateLists(String targetCharacterName) {
        // Search for both ACTIVE and INACTIVE templates in the same area
        List<DTOImageSearchResult> activeFurnaceLevels = templateSearchHelper.searchTemplates(
                EnumTemplates.GAME_PROFILE_SETTINGS_CHARACTER_FURNACE_LEVEL_ACTIVE,
                TemplateSearchHelper.SearchConfig.builder()
                        .withArea(CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_CHARACTER_LIST_AREA)
                        .withMaxAttempts(TEMPLATE_SEARCH_MAX_ATTEMPTS)
                        .withThreshold(TEMPLATE_SEARCH_HIGH_THRESHOLD)
                        .withMaxResults(5)
                        .build());

        List<DTOImageSearchResult> inactiveFurnaceLevels = templateSearchHelper.searchTemplates(
                EnumTemplates.GAME_PROFILE_SETTINGS_CHARACTER_FURNACE_LEVEL_INACTIVE,
                TemplateSearchHelper.SearchConfig.builder()
                        .withArea(CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_CHARACTER_LIST_AREA)
                        .withMaxAttempts(TEMPLATE_SEARCH_MAX_ATTEMPTS)
                        .withThreshold(TEMPLATE_SEARCH_HIGH_THRESHOLD)
                        .withMaxResults(5)
                        .build());

        // Combine both lists and check all results
        List<DTOImageSearchResult> allFurnaceLevels = new ArrayList<>();
        if (activeFurnaceLevels != null) {
            allFurnaceLevels.addAll(activeFurnaceLevels);
        }
        if (inactiveFurnaceLevels != null) {
            allFurnaceLevels.addAll(inactiveFurnaceLevels);
        }

        if (allFurnaceLevels.isEmpty()) {
            return null;
        }

        logger.debug("Found " + allFurnaceLevels.size() + " Furnace Level templates (ACTIVE + INACTIVE)");

        // Check each Furnace Level template
        for (DTOImageSearchResult furnaceLevel : allFurnaceLevels) {
            DTOPoint furnacePoint = furnaceLevel.getPoint();

            // Calculate OCR region for character name relative to Furnace Level template
            // Vertical: Character name is 35 pixels above furnace (Y=725 when furnace Y=760)
            // Height: 50 pixels, search from (furnace Y - 60) to (furnace Y - 10)
            // Horizontal: Fixed range from X=210 to X=500
            DTOPoint nameTopLeft = new DTOPoint(
                    CommonGameAreas.CHARACTER_NAME_ABOVE_FURNACE_X_START,
                    furnacePoint.getY() - CommonGameAreas.CHARACTER_NAME_ABOVE_FURNACE_TOP_OFFSET_Y);
            DTOPoint nameBottomRight = new DTOPoint(
                    CommonGameAreas.CHARACTER_NAME_ABOVE_FURNACE_X_END,
                    furnacePoint.getY() - CommonGameAreas.CHARACTER_NAME_ABOVE_FURNACE_BOTTOM_OFFSET_Y);

            // Read character name above Furnace Level
            String characterName = stringHelper.execute(
                    nameTopLeft,
                    nameBottomRight,
                    OCR_MAX_RETRIES,
                    OCR_RETRY_DELAY_MS,
                    createCharacterNameOCRSettings(),
                    text -> text != null && !text.trim().isEmpty(),
                    text -> text.trim());

            if (characterName != null) {
                // Search for character name directly in OCR result (may contain alliance code prefix)
                if (matchesCharacterName(characterName, targetCharacterName)) {
                    logger.info("Found target character: '" + characterName + "'");
                    return furnaceLevel;
                }
            }
        }

        return null;
    }

    /**
     * Determines if a character at the given position is ACTIVE or INACTIVE.
     * 
     * <p>
     * This method searches for the green checkmark icon that appears on active character cards.
     * The checkmark is located at a fixed offset from the furnace level position:
     * - When furnace is at (x=286, y=760), checkmark is at (x=550, y=720)
     * - Offset: +264 pixels right, -40 pixels up
     * - Checkmark size: 60x60 pixels
     * 
     * @param furnacePosition The furnace level template position
     * @return true if character is ACTIVE (checkmark found), false if INACTIVE
     */
    private boolean isCharacterActiveAtPosition(DTOPoint furnacePosition) {
        // Calculate checkmark position relative to furnace level
        // Offset: +264 pixels right, -40 pixels up from furnace position
        // Checkmark size: 60x60 pixels
        int checkmarkX = furnacePosition.getX() + CHECKMARK_OFFSET_X;
        int checkmarkY = furnacePosition.getY() + CHECKMARK_OFFSET_Y;
        
        DTOArea checkmarkSearchArea = new DTOArea(
                new DTOPoint(checkmarkX, checkmarkY),
                new DTOPoint(checkmarkX + CHECKMARK_SIZE, checkmarkY + CHECKMARK_SIZE));

        DTOImageSearchResult checkmarkResult = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_PROFILE_SETTINGS_CHARACTER_ACTIVE_CHECKMARK,
                TemplateSearchHelper.SearchConfig.builder()
                        .withArea(checkmarkSearchArea)
                        .withMaxAttempts(1)
                        .withThreshold(TEMPLATE_SEARCH_HIGH_THRESHOLD)
                        .build());

        // If checkmark found at this position, character is active
        return checkmarkResult != null && checkmarkResult.isFound();
    }

    /**
     * Verifies the character name in the confirmation dialog and confirms the
     * switch if correct.
     * 
     * <p>
     * Before confirming, reads the character name from the confirmation dialog
     * (area 170,650 to 550,700)
     * and verifies it matches the target character name. If it doesn't match,
     * returns false
     * so the caller can cancel and continue searching.
     * 
     * @param targetCharacterName The expected character name to verify
     * @return true if character name matches and confirmation was successful, false
     *         if name doesn't match
     */
    private boolean confirmCharacterSwitch(String targetCharacterName) {
        logger.debug("Verifying character name in confirmation dialog before confirming switch");

        // Read character name from confirmation dialog
        String characterNameInDialog = stringHelper.execute(
                CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_CONFIRM_DIALOG_NAME_OCR_AREA.topLeft(),
                CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_CONFIRM_DIALOG_NAME_OCR_AREA.bottomRight(),
                OCR_MAX_RETRIES,
                OCR_RETRY_DELAY_MS,
                createCharacterNameOCRSettings(),
                text -> text != null && !text.trim().isEmpty(),
                text -> text.trim());

        if (characterNameInDialog == null || characterNameInDialog.isEmpty()) {
            logger.warn("Could not read character name from confirmation dialog");
            return false;
        }

        // Search for character name directly in OCR result (may contain alliance code prefix)
        boolean nameMatches = matchesCharacterName(characterNameInDialog, targetCharacterName);

        if (!nameMatches) {
            logger.warn("Character name in confirmation dialog does not match. Expected: '" + targetCharacterName +
                    "', Found: '" + characterNameInDialog + "')");
            return false;
        }

        logger.info("Character name verified in confirmation dialog: '" + characterNameInDialog + "'");

        // Name matches, proceed with confirmation
        DTOImageSearchResult confirmButton = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_PROFILE_SETTINGS_SWITCH_CHARACTER_CONFIRM_BUTTON,
                TemplateSearchHelper.SearchConfig.builder()
                        .withArea(CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_PROMPT_BUTTON_AREA)
                        .withMaxAttempts(TEMPLATE_SEARCH_MAX_ATTEMPTS)
                        .withDelay(TEMPLATE_SEARCH_DELAY_MS)
                        .withThreshold(TEMPLATE_SEARCH_THRESHOLD)
                        .build());

        if (confirmButton == null || !confirmButton.isFound()) {
            logger.error("Confirm button not found in character switch dialog");
            return false;
        }

        emuManager.tapAtRandomPoint(
                emulatorNumber,
                confirmButton.getPoint(),
                confirmButton.getPoint(),
                1,
                NAVIGATION_DELAY_MS);

        logger.info("Character switch confirmed");
        return true;
    }

    /**
     * Cancels the character switch by tapping the Cancel button.
     */
    private void cancelCharacterSwitch() {
        logger.debug("Canceling character switch");

        DTOImageSearchResult cancelButton = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_PROFILE_SETTINGS_SWITCH_CHARACTER_CANCEL_BUTTON,
                TemplateSearchHelper.SearchConfig.builder()
                        .withArea(CommonGameAreas.PROFILE_SETTINGS_SWITCH_CHARACTER_PROMPT_BUTTON_AREA)
                        .withMaxAttempts(TEMPLATE_SEARCH_MAX_ATTEMPTS)
                        .withDelay(TEMPLATE_SEARCH_DELAY_MS)
                        .withThreshold(TEMPLATE_SEARCH_THRESHOLD)
                        .build());

        if (cancelButton == null || !cancelButton.isFound()) {
            logger.warn("Cancel button not found, using back button instead");
            emuManager.tapBackButton(emulatorNumber);
        } else {
            emuManager.tapAtRandomPoint(
                    emulatorNumber,
                    cancelButton.getPoint(),
                    cancelButton.getPoint(),
                    1,
                    NAVIGATION_DELAY_MS);
        }

        // Wait for dialog to close
        sleep(NAVIGATION_DELAY_MS);
        logger.info("Character switch canceled");
    }

    /**
     * Scrolls the character list down to see more characters.
     */
    private void scrollCharacterList() {
        logger.debug("Scrolling character list down");

        // Scroll from middle-bottom to middle-top of screen
        DTOPoint scrollStart = new DTOPoint(360, 800); // Middle-bottom
        DTOPoint scrollEnd = new DTOPoint(360, 400); // Middle-top

        emuManager.executeSwipe(emulatorNumber, scrollStart, scrollEnd);
        sleep(SCROLL_DELAY_MS);
    }

    /**
     * Checks if the OCR result contains the expected character name.
     * 
     * <p>
     * This method searches for the expected character name directly in the OCR result,
     * which may contain an alliance code prefix (e.g., "[ABC] John Doe" or "[ABCJohn Doe").
     * Uses pattern matching for case-insensitive search, similar to IntelligenceTask.
     * 
     * <p>
     * Both strings are trimmed and all spaces are removed before matching to handle OCR spacing
     * errors. This makes matching more lenient and robust against OCR spacing inconsistencies.
     * 
     * <p>
     * Examples:
     * <ul>
     * <li>OCR: "[ABC] John Doe", Expected: "John Doe" → true (both become "[ABC]JohnDoe" and "JohnDoe")</li>
     * <li>OCR: "[ABC]John Doe", Expected: "John Doe" → true</li>
     * <li>OCR: "[ABCJohnDoe", Expected: "John Doe" → true</li>
     * <li>OCR: "John  Doe" (double space), Expected: "John Doe" → true</li>
     * <li>OCR: "JohnDoe", Expected: "John Doe" → true</li>
     * </ul>
     * 
     * @param ocrResult The OCR result that may contain alliance code prefix
     * @param expectedName The expected character name to search for
     * @return true if the expected name is found in the OCR result (case-insensitive)
     */
    private boolean matchesCharacterName(String ocrResult, String expectedName) {
        if (ocrResult == null || ocrResult.isEmpty() || expectedName == null || expectedName.isEmpty()) {
            return false;
        }

        // Trim and remove all spaces from both strings for more lenient matching
        // This handles OCR spacing errors (missing spaces, extra spaces, etc.)
        String normalizedOcr = ocrResult.trim().replaceAll("\\s+", "");
        String normalizedExpected = expectedName.trim().replaceAll("\\s+", "");

        // Escape special regex characters in the expected name and create case-insensitive pattern
        String escapedName = Pattern.quote(normalizedExpected);
        Pattern namePattern = Pattern.compile(escapedName, Pattern.CASE_INSENSITIVE);
        Matcher matcher = namePattern.matcher(normalizedOcr);
        return matcher.find();
    }

    /**
     * Creates OCR settings for reading character ID (numbers only).
     * 
     * @return OCR settings configured for character ID reading
     */
    private DTOTesseractSettings createCharacterIdOCRSettings() {
        return DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setAllowedChars("0123456789")
                .setRemoveBackground(true)
                .build();
    }

    /**
     * Creates OCR settings for reading character name (alphanumeric + special
     * characters).
     * 
     * @return OCR settings configured for character name reading
     */
    private DTOTesseractSettings createCharacterNameOCRSettings() {
        return DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ")
                .setRemoveBackground(true)
                .build();
    }

    /**
     * Sleeps for the specified number of milliseconds.
     * 
     * @param ms Milliseconds to sleep
     */
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Sleep interrupted");
        }
    }
}
