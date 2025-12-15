package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TundraTrekAutoTask extends DelayedTask {

    // ===================== IMMUTABLE RESULT CLASS =====================
    private static class WaitOutcome {
        final boolean finished; // Reached 0/100
        final boolean anyParsed; // At least one OCR parse succeeded
        final boolean stagnated; // Value parsed but didn't decrease within timeout

        WaitOutcome(boolean finished, boolean anyParsed, boolean stagnated) {
            this.finished = finished;
            this.anyParsed = anyParsed;
            this.stagnated = stagnated;
        }
    }

    // ===================== CONSTANTS =====================
    // Navigation points
    private static final DTOPoint SCROLL_START_POINT = new DTOPoint(400, 800);
    private static final DTOPoint SCROLL_END_POINT = new DTOPoint(400, 100);
    private static final DTOPoint UPPER_SCREEN_CLICK = new DTOPoint(360, 200);

    // OCR region for trek counter (e.g., "14/100")
    private static final DTOPoint TREK_COUNTER_TOP_LEFT = new DTOPoint(516, 22);
    private static final DTOPoint TREK_COUNTER_BOTTOM_RIGHT = new DTOPoint(610, 60);

    // OCR region offsets for fallback attempts
    private static final int[][] OCR_REGION_OFFSETS = {
            { 0, 0 }, // Primary position
            { -2, 1 }, { 2, -1 }, // Minor adjustments
            { 3, 0 }, { -2, -3 } // Edge case fallbacks
    };

    // Timeout durations
    private static final Duration STAGNATION_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration NO_PARSE_TIMEOUT = Duration.ofMinutes(3);

    public TundraTrekAutoTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("=== Starting Tundra Trek Auto Task ===");

        try {
            // Navigate to tundra menu
            if (!navigateToTundraMenu()) {
                rescheduleWithDelay(Duration.ofHours(1), "Failed to navigate to Tundra menu");
                return;
            }

            // Pre-check: exit immediately if counter already at 0
            if (checkIfAlreadyComplete()) {
                return;
            }

            // Start automation sequence
            if (!startAutomationSequence()) {
                rescheduleWithDelay(Duration.ofHours(1), "Failed to start automation sequence");
                return;
            }

            // Wait for completion
            handleAutomationCompletion();

        } catch (Exception e) {
            logError("Unexpected error during TundraTrekAuto task: " + e.getMessage(), e);
            rescheduleWithDelay(Duration.ofHours(1), "Unexpected error");
        }
    }

    /**
     * Navigate to the Tundra Trek menu
     */
    private boolean navigateToTundraMenu() {
        logInfo("Navigating to Tundra menu");

        // Open side menu
        marchHelper.openLeftMenuCitySection(true);

        // Scroll to bring Tundra menu into view
        swipe(SCROLL_START_POINT, SCROLL_END_POINT);
        sleepTask(1300);

        // Search for Tundra Trek icon with retries
        DTOImageSearchResult tundraIcon = templateSearchHelper.searchTemplate(
                EnumTemplates.LEFT_MENU_TUNDRA_TREK_BUTTON,
                SearchConfigConstants.SINGLE_WITH_RETRIES);

        if (!tundraIcon.isFound()) {
            logWarning("Tundra Trek icon not found. Verify template exists: templates/leftmenu/tundraTrek.png");
            return false;
        }

        // Tap icon and verify navigation
        tapPoint(tundraIcon.getPoint());
        sleepTask(1500);

        logInfo("Successfully entered Tundra Trek event");
        return true;
    }

    /**
     * Check if trek counter is already at 0/100
     */
    private boolean checkIfAlreadyComplete() {
        Integer remaining = readTrekCounterOnce();

        if (remaining != null) {
            logInfo("Pre-check: Trek counter at " + remaining + "/100");

            if (remaining <= 0) {
                logInfo("Trek already complete (0/100). Exiting event.");
                tapBackButton();
                sleepTask(500);
                reschedule(UtilTime.getGameReset());
                return true;
            }
        } else {
            logDebug("Pre-check: Could not read counter. Proceeding with automation.");
        }

        return false;
    }

    /**
     * Start the automation sequence (Auto button + Bag button)
     * 
     * UI Behavior Notes:
     * - Auto button may be visible but grayed out (not clickable)
     * - Clicking a grayed Auto button doesn't open the checkbox UI
     * - Blue button unlocks/activates the Auto button
     * - Skip button can advance through UI states
     * - Upper screen clicks can refresh/reveal hidden buttons
     * - Checkbox visibility confirms Auto button successfully opened
     */
    private boolean startAutomationSequence() {
        logInfo("Starting automation sequence");

        // Try to activate Auto button
        if (!activateAutoButton()) {
            logWarning("Failed to activate Auto button");
            return false;
        }

        // Try to click Bag button (optional - not critical for automation)
        if (!clickBagButton()) {
            logInfo("Bag button sequence skipped (proceeding without it)");
        }

        logInfo("Automation sequence started successfully");
        return true;
    }

    /**
     * Attempt to activate the Auto button through various methods.
     * 
     * Auto button is verified by checking if checkbox becomes visible after
     * clicking.
     * If checkbox doesn't appear, the Auto button is likely grayed out and needs
     * to be unlocked via Blue button or Skip button.
     */
    private boolean activateAutoButton() {
        // Attempt 1: Try direct Auto button click
        if (tryDirectAutoButton()) {
            return true;
        }

        // Attempt 2: Upper screen click to reveal buttons, then try Auto
        logInfo("Auto button not visible. Trying upper screen click to reveal buttons");
        tapPoint(UPPER_SCREEN_CLICK);
        sleepTask(3500 / 2);

        if (tryDirectAutoButton()) {
            return true;
        }

        // Attempt 3: Try Blue button to unlock Auto
        if (tryBlueButtonSequence()) {
            return true;
        }

        // Attempt 4: Try Skip button as last resort
        if (trySkipButtonSequence()) {
            return true;
        }

        logWarning("All button activation methods failed. Cannot start automation.");
        tapBackButton();
        sleepTask(500);
        return false;
    }

    /**
     * Try to click Auto button directly and verify it opened via checkbox
     */
    private boolean tryDirectAutoButton() {
        DTOImageSearchResult autoBtn = templateSearchHelper.searchTemplate(
                EnumTemplates.TUNDRA_TREK_AUTO_BUTTON,
                SearchConfigConstants.SINGLE_WITH_2_RETRIES);

        if (!autoBtn.isFound()) {
            return false;
        }

        logInfo("Auto button found - clicking it");
        tapPoint(autoBtn.getPoint());
        sleepTask(500);

        // Verify Auto opened by checking for checkbox
        if (isCheckboxVisible()) {
            logInfo("Auto button successfully opened (checkbox visible)");
            return true;
        }

        logDebug("Auto button clicked but checkbox not visible - Auto may be disabled/grayed out");
        return false;
    }

    /**
     * Try Blue button sequence: Click Blue → Upper screen click → Try Auto
     */
    private boolean tryBlueButtonSequence() {
        DTOImageSearchResult blueBtn = templateSearchHelper.searchTemplate(
                EnumTemplates.TUNDRA_TREK_BLUE_BUTTON,
                SearchConfigConstants.SINGLE_WITH_2_RETRIES);

        if (!blueBtn.isFound()) {
            logDebug("Blue button not found");
            return false;
        }

        logInfo("Blue button found - clicking to potentially unlock Auto");
        tapPoint(blueBtn.getPoint());
        sleepTask(3500);

        // After Blue button, try upper screen click
        logInfo("After Blue button: performing upper screen click");
        tapPoint(UPPER_SCREEN_CLICK);
        sleepTask(1000);

        // Now try Auto button
        if (tryDirectAutoButton()) {
            logInfo("Auto button activated successfully after Blue button sequence");
            return true;
        }

        logDebug("Blue button sequence did not result in active Auto button");
        return false;
    }

    /**
     * Try Skip button sequence: Double-tap Skip → Try Auto
     */
    private boolean trySkipButtonSequence() {
        DTOImageSearchResult skipBtn = templateSearchHelper.searchTemplate(
                EnumTemplates.TUNDRA_TREK_SKIP_BUTTON,
                SearchConfigConstants.SINGLE_WITH_2_RETRIES);

        if (!skipBtn.isFound()) {
            logDebug("Skip button not found");
            return false;
        }

        logInfo("Skip button found - using as Auto alternative");

        // Double-tap skip for UI rebuild
        tapPoint(skipBtn.getPoint());
        sleepTask(500);
        tapPoint(skipBtn.getPoint());
        sleepTask(3500);

        // After skip, try Auto button
        if (tryDirectAutoButton()) {
            logInfo("Auto button activated successfully after Skip button");
            return true;
        }

        // Skip worked but Auto still not active - may still be valid state
        logInfo("Skip button clicked but Auto not verified. Proceeding anyway.");
        return true;
    }

    /**
     * Check if checkbox is visible (either active or inactive)
     * This confirms the Auto button menu actually opened
     */
    private boolean isCheckboxVisible() {
        // Check for active checkbox
        DTOImageSearchResult activeCheck = templateSearchHelper.searchTemplate(
                EnumTemplates.TUNDRA_TREK_CHECK_ACTIVE,
                SearchConfigConstants.HIGH_SENSITIVITY);

        if (activeCheck.isFound()) {
            logDebug("Active checkbox found");
            return true;
        }

        // Check for inactive checkbox
        DTOImageSearchResult inactiveCheck = templateSearchHelper.searchTemplate(
                EnumTemplates.TUNDRA_TREK_CHECK_INACTIVE,
                SearchConfigConstants.HIGH_SENSITIVITY);

        if (inactiveCheck.isFound()) {
            logDebug("Inactive checkbox found");
            return true;
        }

        logDebug("No checkbox found (neither active nor inactive)");
        return false;
    }

    /**
     * Attempt to click the Bag button
     */
    private boolean clickBagButton() {
        DTOImageSearchResult bagBtn = templateSearchHelper.searchTemplate(
                EnumTemplates.TUNDRA_TREK_BAG_BUTTON,
                SearchConfigConstants.HIGH_SENSITIVITY);

        if (!bagBtn.isFound()) {
            logDebug("Bag button not found (optional button)");
            return false;
        }

        // Ensure checkbox is active before clicking bag
        if (!ensureCheckboxActive()) {
            logWarning("Could not activate checkbox. Skipping bag button.");
            return false;
        }

        logInfo("Clicking Bag button");
        tapPoint(bagBtn.getPoint());
        sleepTask(500);
        return true;
    }

    /**
     * Ensure the checkbox is in active state
     */
    private boolean ensureCheckboxActive() {
        // Check if already active
        DTOImageSearchResult activeCheck = templateSearchHelper.searchTemplate(
                EnumTemplates.TUNDRA_TREK_CHECK_ACTIVE,
                SearchConfigConstants.SINGLE_WITH_2_RETRIES);

        if (activeCheck.isFound()) {
            logDebug("Checkbox already active");
            return true;
        }

        // Find and click inactive checkbox
        DTOImageSearchResult inactiveCheck = templateSearchHelper.searchTemplate(
                EnumTemplates.TUNDRA_TREK_CHECK_INACTIVE,
                SearchConfigConstants.SINGLE_WITH_2_RETRIES);

        if (!inactiveCheck.isFound()) {
            logWarning("Checkbox not found (neither active nor inactive)");
            return false;
        }

        logInfo("Activating checkbox");
        tapPoint(inactiveCheck.getPoint());
        sleepTask(500);

        // Verify activation
        activeCheck = templateSearchHelper.searchTemplate(
                EnumTemplates.TUNDRA_TREK_CHECK_ACTIVE,
                SearchConfigConstants.SINGLE_WITH_2_RETRIES);

        if (activeCheck.isFound()) {
            logDebug("Checkbox successfully activated");
            return true;
        }

        logWarning("Checkbox activation failed");
        return false;
    }

    /**
     * Handle the completion of automation
     */
    private void handleAutomationCompletion() {
        WaitOutcome outcome = waitUntilTrekCounterZero();

        if (outcome.finished) {
            logInfo("Trek completed successfully (0/100). Exiting event.");
            sleepTask(2000);
            tapBackButton();
            sleepTask(500);
            reschedule(UtilTime.getGameReset());
            return;
        }

        // Handle timeout scenarios
        if (!outcome.anyParsed) {
            logWarning("Timeout: No valid OCR readings for " + NO_PARSE_TIMEOUT.toMinutes() + " minutes");
            exitWithDoubleBack();
        } else if (outcome.stagnated) {
            logWarning("Timeout: Counter stagnated for " + STAGNATION_TIMEOUT.toMinutes() + " minute");
            tapBackButton();
        } else {
            logInfo("Exiting normally after timeout");
            tapBackButton();
        }

        sleepTask(500);
        rescheduleWithDelay(Duration.ofMinutes(10), "Timeout during automation");
    }

    /**
     * Wait until trek counter reaches 0/100
     */
    private WaitOutcome waitUntilTrekCounterZero() {
        Pattern fractionPattern = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
        Pattern twoNumbersPattern = Pattern.compile("(\\d{1,3})\\D+(\\d{2,3})");

        int attempts = 0;
        Integer lastValue = null;
        LocalDateTime lastDecreaseAt = null;
        LocalDateTime noParseStart = LocalDateTime.now();
        boolean anyParsed = false;

        while (true) {
            Integer remaining = tryReadTrekCounter(fractionPattern, twoNumbersPattern, attempts);
            LocalDateTime now = LocalDateTime.now();

            if (remaining != null) {
                // Successfully parsed a value
                anyParsed = true;

                if (remaining <= 0) {
                    return new WaitOutcome(true, anyParsed, false);
                }

                // Track decreases
                if (lastValue == null) {
                    lastValue = remaining;
                    lastDecreaseAt = now;
                    logDebug("Initial trek counter: " + remaining);
                } else if (remaining < lastValue) {
                    lastValue = remaining;
                    lastDecreaseAt = now;
                    logDebug("Trek counter decreased to: " + remaining);
                } else if (lastDecreaseAt != null) {
                    // Value hasn't decreased - check for stagnation
                    Duration sinceDecrease = Duration.between(lastDecreaseAt, now);
                    if (sinceDecrease.compareTo(STAGNATION_TIMEOUT) >= 0) {
                        logWarning("Trek counter stagnated at " + remaining + " for " +
                                sinceDecrease.toSeconds() + "s");
                        return new WaitOutcome(false, anyParsed, true);
                    }
                }

                // Reset no-parse timer since we got a value
                noParseStart = now;

            } else {
                // Failed to parse - check no-parse timeout
                logDebug("Trek counter OCR failed (attempt " + attempts + ")");

                Duration sinceNoParse = Duration.between(noParseStart, now);
                if (sinceNoParse.compareTo(NO_PARSE_TIMEOUT) >= 0) {
                    logWarning("No valid OCR for " + sinceNoParse.toMinutes() + " minutes");
                    return new WaitOutcome(false, anyParsed, false);
                }
            }

            attempts++;
            sleepTask(2500);
        }
    }

    /**
     * Attempt to read trek counter using multiple OCR strategies
     */
    private Integer tryReadTrekCounter(Pattern fractionPattern, Pattern twoNumbersPattern, int attempt) {
        for (int[] offset : OCR_REGION_OFFSETS) {
            int dx = offset[0];
            int dy = offset[1];

            DTOPoint p1 = new DTOPoint(
                    TREK_COUNTER_TOP_LEFT.getX() + dx,
                    TREK_COUNTER_TOP_LEFT.getY() + dy);
            DTOPoint p2 = new DTOPoint(
                    TREK_COUNTER_BOTTOM_RIGHT.getX() + dx,
                    TREK_COUNTER_BOTTOM_RIGHT.getY() + dy);

            try {
                String raw = emuManager.ocrRegionText(EMULATOR_NUMBER, p1, p2);
                String normalized = normalizeOcrText(raw);
                Integer remaining = parseRemaining(raw, normalized, fractionPattern, twoNumbersPattern);

                if (remaining != null) {
                    if (attempt < 5 || attempt % 10 == 0) {
                        logDebug("OCR success (dx=" + dx + ", dy=" + dy + "): '" + raw +
                                "' => " + remaining);
                    }
                    return remaining;
                }
            } catch (Exception e) {
                if (attempt < 3) {
                    logDebug("OCR exception at offset (" + dx + "," + dy + "): " + e.getMessage());
                }
            }
        }

        return null;
    }

    /**
     * Read trek counter once (for pre-check)
     */
    private Integer readTrekCounterOnce() {
        Pattern fractionPattern = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
        Pattern twoNumbersPattern = Pattern.compile("(\\d{1,3})\\D+(\\d{2,3})");

        for (int[] offset : OCR_REGION_OFFSETS) {
            int dx = offset[0];
            int dy = offset[1];

            DTOPoint p1 = new DTOPoint(
                    TREK_COUNTER_TOP_LEFT.getX() + dx,
                    TREK_COUNTER_TOP_LEFT.getY() + dy);
            DTOPoint p2 = new DTOPoint(
                    TREK_COUNTER_BOTTOM_RIGHT.getX() + dx,
                    TREK_COUNTER_BOTTOM_RIGHT.getY() + dy);

            try {
                String raw = emuManager.ocrRegionText(EMULATOR_NUMBER, p1, p2);
                String normalized = normalizeOcrText(raw);
                Integer remaining = parseRemaining(raw, normalized, fractionPattern, twoNumbersPattern);

                if (remaining != null) {
                    return remaining;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    /**
     * Normalize OCR text for better parsing
     */
    private String normalizeOcrText(String text) {
        if (text == null)
            return "";

        return text
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('O', '0')
                .replace('o', '0')
                .replace('I', '1')
                .replace('l', '1')
                .replaceAll("\\s+", "")
                .trim();
    }

    /**
     * Parse remaining count from OCR text
     */
    private Integer parseRemaining(String raw, String normalized,
            Pattern fractionPattern, Pattern twoNumbersPattern) {
        // Try fraction pattern on raw
        Integer result = extractBestFraction(raw, fractionPattern);
        if (result != null)
            return result;

        // Try with alternate separators
        if (raw != null) {
            String altRaw = raw
                    .replace(':', '/')
                    .replace(';', '/')
                    .replace('-', '/')
                    .replace('|', '/')
                    .replace('\\', '/');
            result = extractBestFraction(altRaw, fractionPattern);
            if (result != null)
                return result;
        }

        // Try fraction on normalized
        result = extractBestFraction(normalized, fractionPattern);
        if (result != null)
            return result;

        // Try loose two-number pattern
        result = tryTwoNumbersPattern(raw, twoNumbersPattern);
        if (result != null)
            return result;

        result = tryTwoNumbersPattern(normalized, twoNumbersPattern);
        if (result != null)
            return result;

        // Heuristic for "0100" variations
        if (normalized != null &&
                (normalized.matches("^0+/?1?0?0+$") || normalized.matches("^0+100$"))) {
            return 0;
        }

        return null;
    }

    /**
     * Extract best (smallest) numerator from fraction pattern matches
     */
    private Integer extractBestFraction(String text, Pattern fractionPattern) {
        if (text == null)
            return null;

        Matcher matcher = fractionPattern.matcher(text);
        Integer best = null;

        while (matcher.find()) {
            try {
                int numerator = Integer.parseInt(matcher.group(1));
                int denominator = Integer.parseInt(matcher.group(2));

                // Denominator should be around 100 (50-150 range)
                if (denominator < 50 || denominator > 150) {
                    continue;
                }

                if (best == null || numerator < best) {
                    best = numerator;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return best;
    }

    /**
     * Try loose two-numbers pattern
     */
    private Integer tryTwoNumbersPattern(String text, Pattern pattern) {
        if (text == null)
            return null;

        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                int numerator = Integer.parseInt(matcher.group(1));
                int denominator = Integer.parseInt(matcher.group(2));

                if (denominator >= 50 && denominator <= 150) {
                    return numerator;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return null;
    }

    /**
     * Exit with double back button press
     */
    private void exitWithDoubleBack() {
        tapBackButton();
        sleepTask(300);
        tapBackButton();
        sleepTask(300);
    }

    /**
     * Reschedule task with delay and reason
     */
    private void rescheduleWithDelay(Duration delay, String reason) {
        LocalDateTime nextExecution = LocalDateTime.now().plus(delay);
        logWarning(reason + ". Rescheduling for " + nextExecution);
        reschedule(nextExecution);
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.ANY;
    }
}