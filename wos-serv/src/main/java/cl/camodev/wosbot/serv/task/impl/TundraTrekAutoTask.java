package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TundraTrekAutoTask extends DelayedTask {
    private static class WaitOutcome {
        boolean finished;   // Reached 0/100
        boolean anyParsed;  // At least one OCR parse succeeded (some numeric value detected)
        boolean stagnated;  // A value was parsed but did not decrease within STAGNATION_TIMEOUT (1 min)
    }

    // =========================== CONSTANTS ===========================
    // Navigation points (to be filled using ADB-captured coordinates)
    private static final DTOPoint SIDE_MENU_AREA_START = new DTOPoint(3, 513);
    private static final DTOPoint SIDE_MENU_AREA_END = new DTOPoint(26, 588);
    private static final DTOPoint CITY_TAB_BUTTON = new DTOPoint(110, 270);
    private static final DTOPoint SCROLL_START_POINT = new DTOPoint(400, 800);
    private static final DTOPoint SCROLL_END_POINT = new DTOPoint(400, 100);
    
    // Fallback click point in upper screen half when Auto button not visible
    private static final DTOPoint UPPER_SCREEN_CLICK = new DTOPoint(360, 200);

    // OCR region for the trek counter (top-right indicator like "14/100").
    // Optimized coordinates based on debug analysis: Average offset {12, -2}
    private static final DTOPoint TREK_COUNTER_TOP_LEFT = new DTOPoint(516, 22);
    private static final DTOPoint TREK_COUNTER_BOTTOM_RIGHT = new DTOPoint(610, 60);

    // Minimal offsets - primary position should now work with {0, 0}
    private static final int[][] OCR_REGION_OFFSETS = new int[][]{
        // Primary position (should work perfectly now)
        {0, 0},
        // Minor fallbacks for edge cases
        {-2, 1}, {2, -1}, {3, 0}, {-2, -3}
    };

    // Polling parameters
    private static final long OCR_POLL_INTERVAL_MS = 2500; // Interval between OCR polling attempts
    // NOTE: Timeouts handled inside waitUntilTrekCounterZero():
    //  - STAGNATION_TIMEOUT (1 minute) when values are parsed but not decreasing
    //  - NO_PARSE_TIMEOUT (3 minutes) when no valid OCR value is parsed at all

    public TundraTrekAutoTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }

    @Override
    public EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    @Override
    protected void execute() {
        logInfo("Starting TundraTrekAuto task for profile: " + profile.getName());

        // Check if auto manage events is enabled
        boolean autoManageEnabled = profile.getConfig(EnumConfigurationKey.TUNDRA_TREK_AUTOMATION_BOOL, Boolean.class);
        if (!autoManageEnabled) {
            logInfo("Tundra Trek automation is disabled for this profile. Skipping task.");
            this.setRecurring(false);
            return;
        }

        try {
            if (!navigateToTundraMenu()) {
                rescheduleOneHourLater("Failed to navigate to the Tundra menu");
                return;
            }

            // Pre-check: if counter is already 0/100, exit immediately without pressing Auto
            Integer preRemaining = readTrekCounterOnce();
            if (preRemaining != null) {
                logInfo("Pre-check trek counter remaining=" + preRemaining);
                if (preRemaining <= 0) {
                    logInfo("Trek counter already 0/100 on entry. Exiting event.");
                    tapBackButton();
                    reschedule(LocalDateTime.now().plusHours(12));
                    return;
                }
            } else {
                logDebug("Pre-check OCR could not read counter. Proceeding with Auto.");
            }

            // Press Auto button then Bag button
            if (!clickAutoThenBag()) {
                rescheduleOneHourLater("Failed to press Auto or Bag");
                return;
            }

            // Wait until the trek counter reaches 0/100, then exit
            WaitOutcome outcome = waitUntilTrekCounterZero();
            if (outcome.finished) {
                logInfo("Tundra trek counter reached 0/100. Exiting event.");
                tapBackButton();
                reschedule(LocalDateTime.now().plusHours(12));
            } else {
                if (!outcome.anyParsed) {
                    logWarning("Timeout (3 min) with no valid OCR. Exiting with double back and rescheduling in 10 minutes.");
                    exitEventDoubleBack();
                } else if (outcome.stagnated) {
                    logWarning("Timeout (1 min) without decrease (values same or higher). Exiting with single back and rescheduling in 10 minutes.");
                    tapBackButton();
                } else {
                    logInfo("Exiting with single back and rescheduling in 10 minutes.");
                    tapBackButton();
                }
                this.reschedule(LocalDateTime.now().plusMinutes(10));
            }

        } catch (Exception e) {
            logError("An error occurred during the TundraTrekAuto task: " + e.getMessage());
            rescheduleOneHourLater("Unexpected error during execution: " + e.getMessage());
        }
    }

    private boolean navigateToTundraMenu() {
        logInfo("Navigating to the Tundra menu...");
        // Open side menu
        tapRandomPoint(SIDE_MENU_AREA_START, SIDE_MENU_AREA_END);
        sleepTask(1000);

        // Switch to city tab
        tapPoint(CITY_TAB_BUTTON);
        sleepTask(500);

        // Scroll down to bring Tundra menu item into view
        swipe(SCROLL_START_POINT, SCROLL_END_POINT);
        sleepTask(1300);

        // Use only the dedicated Tundra Trek icon (no fallback)
        DTOImageSearchResult tundraIcon = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LEFT_MENU_TUNDRA_TREK_BUTTON, 90);
        if (tundraIcon.isFound()) {
            tapPoint(tundraIcon.getPoint());
            sleepTask(1500);
            logInfo("Entered event section via tundra trek icon.");
            return true;
        }

        logWarning("Could not find Tundra Trek icon in left menu. Ensure templates/leftmenu/tundraTrek.png exists and matches.");
        return false;
    }

    private boolean clickAutoThenBag() {
        boolean autoButtonSuccess = false;

        // First try to click the Auto button
        DTOImageSearchResult autoBtn = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_AUTO_BUTTON, 85);
        if (autoBtn.isFound()) {
            tapPoint(autoBtn.getPoint());
            sleepTask(500);
            autoButtonSuccess = true;
        } else {
            logWarning("Auto button not found (autoTrek.png). Trying upper screen click fallback...");
            
            // Additional fallback: click in upper screen half before Skip button
            logInfo("Clicking in upper screen half to potentially reveal Auto button.");
            tapPoint(UPPER_SCREEN_CLICK);
            sleepTask(2000);
            
            // Search for Auto button first, then Blue button as fallback
            DTOImageSearchResult autoButtonCheck = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_AUTO_BUTTON, 85);
            DTOImageSearchResult specialSection = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_BLUE_BUTTON, 85);

            if (autoButtonCheck.isFound()) {
                logInfo("Auto button now visible after upper screen click - clicking it directly.");
                tapPoint(autoButtonCheck.getPoint());
                sleepTask(500);
                autoButtonSuccess = true;
            } else if (specialSection.isFound()) {
                logInfo("Blue button found - clicking it.");
                tapPoint(specialSection.getPoint());
                sleepTask(3500);
            }

            // If neither Auto nor Blue button found, try Skip button as alternative
            if (!autoButtonCheck.isFound() && !specialSection.isFound()) {
                logWarning("Auto button still not found after upper screen click. Searching for Skip button as alternative...");

                // If Auto button not found, try Skip button as alternative
                DTOImageSearchResult skipBtn = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_SKIP_BUTTON, 85);
                if (skipBtn.isFound()) {
                    logInfo("Skip button found - clicking as Auto alternative.");
                    tapPoint(skipBtn.getPoint());
                    sleepTask(500);
                    // Additional tab press after skip
                    tapPoint(skipBtn.getPoint());
                    sleepTask(3000); // Give UI time to rebuild after skip clicks

                    // Check if Auto button is now visible after skip clicks
                    DTOImageSearchResult autoRetryAfterSkip = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_AUTO_BUTTON, 85);
                    if (autoRetryAfterSkip.isFound()) {
                        logInfo("Auto button now visible after skip - clicking it.");
                        tapPoint(autoRetryAfterSkip.getPoint());
                        sleepTask(500);
                        autoButtonSuccess = true;
                    }
                } else {
                    logWarning("Neither Auto button nor Skip button found. Cannot start automation.");
                    tapBackButton();
                    sleepTask(500);
                    return false;
                }
            }
        }

        // Only proceed to Bag button if Auto button was successfully clicked
        if (!autoButtonSuccess) {
            logWarning("Auto button was not successfully activated. Skipping bag button sequence.");
            return false;
        }

        logInfo("Auto button was successful. Proceeding to Bag button sequence.");

        // Then click the Bag button - but first check and handle checkbox state
        DTOImageSearchResult bagBtn = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_BAG_BUTTON, 85);
        if (bagBtn.isFound()) {
            if (ensureCheckboxActive()) {
                tapPoint(bagBtn.getPoint());
                sleepTask(500);
            } else {
                logWarning("Could not ensure checkbox is active. Aborting bag button click.");
                tapBackButton();
                sleepTask(500);
                return false;
            }
        } else {
            logWarning("Bag button not found (bagTrek.png). Trying Blue Button fallback...");
            
            // Try Blue Button fallback - click upper screen then search for buttons
            logInfo("Clicking in upper screen half to potentially reveal buttons.");
            tapPoint(UPPER_SCREEN_CLICK);
            sleepTask(2000);

            // First check if Auto button appeared after upper screen click
            DTOImageSearchResult autoCheckAfterClick = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_AUTO_BUTTON, 85);
            if (autoCheckAfterClick.isFound()) {
                logInfo("Auto button appeared after upper screen click - clicking it.");
                tapPoint(autoCheckAfterClick.getPoint());
                sleepTask(500);
                autoButtonSuccess = true;

                // After Auto button click, check if Blue button is still available (Auto might be grayed out)
                DTOImageSearchResult blueBtnCheck = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_BLUE_BUTTON, 85);
                if (blueBtnCheck.isFound()) {
                    logInfo("Blue button still available after Auto click - clicking to activate Auto button.");
                    tapPoint(blueBtnCheck.getPoint());
                    sleepTask(2000);

                    // After Blue button click, search for Auto button again
                    DTOImageSearchResult autoAfterBlue = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_AUTO_BUTTON, 85);
                    if (autoAfterBlue.isFound()) {
                        logInfo("Auto button now active after Blue button - clicking it.");
                        tapPoint(autoAfterBlue.getPoint());
                        sleepTask(500);
                    } else {
                        logWarning("Auto button not found after Blue button click.");
                    }
                }

                // Now try to find and click Bag button
                DTOImageSearchResult bagAfterAuto = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_BAG_BUTTON, 85);
                if (bagAfterAuto.isFound()) {
                    logInfo("Bag button found after Auto button - checking checkbox state.");
                    if (ensureCheckboxActive()) {
                        tapPoint(bagAfterAuto.getPoint());
                        sleepTask(500);
                    }
                } else {
                    logWarning("Bag button not found after Auto button in fallback.");
                }
            } else {
                // If Auto button not found, search for Blue button as fallback
                DTOImageSearchResult blueBtn = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_BLUE_BUTTON, 85);

                if (blueBtn.isFound()) {
                    logInfo("Blue button found - clicking it.");
                    tapPoint(blueBtn.getPoint());
                    sleepTask(2000);

                    // After blue button: upper screen click, short pause, then search Auto button again
                    logInfo("After blue button: performing another upper screen click.");
                    tapPoint(UPPER_SCREEN_CLICK);
                    sleepTask(1000);
                    DTOImageSearchResult autoAfterBlue = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_AUTO_BUTTON, 85);
                    if (autoAfterBlue.isFound()) {
                        logInfo("Auto button found after blue button - clicking it.");
                        tapPoint(autoAfterBlue.getPoint());
                        sleepTask(500);
                        autoButtonSuccess = true;
                    } else {
                        logInfo("Auto button not found after blue button.");
                    }

                    // After blue button sequence, check if Bag button is now visible
                    DTOImageSearchResult bagRetry = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_BAG_BUTTON, 85);
                    if (bagRetry.isFound()) {
                        logInfo("Bag button now visible after blue button click - checking checkbox state.");
                        if (ensureCheckboxActive()) {
                            tapPoint(bagRetry.getPoint());
                            sleepTask(500);
                        } else {
                            logInfo("Checkbox could not be activated. Proceeding anyway.");
                        }
                    } else {
                        logInfo("Bag button still not found after blue button. Proceeding anyway.");
                    }
                } else {
                    logWarning("Blue button not found. Searching for Skip button as final fallback...");

                    // If neither button found, try Skip button
                    DTOImageSearchResult skipBtn = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_SKIP_BUTTON, 85);
                    if (skipBtn.isFound()) {
                        logInfo("Skip button found - clicking to proceed.");
                        tapPoint(skipBtn.getPoint());
                        sleepTask(500);
                        // Additional tab press after skip
                        tapPoint(skipBtn.getPoint());
                        sleepTask(500);
                    } else {
                        logWarning("Skip button not found. Using back button to exit.");
                        tapBackButton();
                        sleepTask(500);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private WaitOutcome waitUntilTrekCounterZero() {
        Pattern fraction = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
        Pattern twoNumbersLoose = Pattern.compile("(\\d{1,3})\\D+(\\d{2,3})");
        int attempts = 0;
        WaitOutcome outcome = new WaitOutcome();
        Integer lastValue = null;
        LocalDateTime lastDecreaseAt = null;
    final Duration STAGNATION_TIMEOUT = Duration.ofMinutes(1); // 1 minute when valid values are parsed but not decreasing
    final Duration NO_PARSE_TIMEOUT = Duration.ofMinutes(3);   // 3 minutes when no valid value can be parsed at all
        LocalDateTime noParseStart = LocalDateTime.now();

        while (true) {
            try {
                String raw = null;
                String norm = null;
                Integer remaining = null;
                int usedDx = 0, usedDy = 0;

                for (int i = 0; i < OCR_REGION_OFFSETS.length; i++) {
                    int dx = OCR_REGION_OFFSETS[i][0];
                    int dy = OCR_REGION_OFFSETS[i][1];
                    DTOPoint p1 = new DTOPoint(TREK_COUNTER_TOP_LEFT.getX() + dx, TREK_COUNTER_TOP_LEFT.getY() + dy);
                    DTOPoint p2 = new DTOPoint(TREK_COUNTER_BOTTOM_RIGHT.getX() + dx, TREK_COUNTER_BOTTOM_RIGHT.getY() + dy);
                    try {
                        raw = emuManager.ocrRegionText(EMULATOR_NUMBER, p1, p2);
                        norm = normalizeOcrText(raw);
                        remaining = parseRemaining(raw, norm, fraction, twoNumbersLoose);
                        if (attempts < 5 || attempts % 10 == 0) {
                            logDebug("OCR attempt " + attempts + ", offset " + i + " (dx=" + dx + ", dy=" + dy + "): region[" + p1.getX() + "," + p1.getY() + " to " + p2.getX() + "," + p2.getY() + "] raw='" + (raw != null ? raw.replace('\n', '\\') : "null") + "' norm='" + (norm != null ? norm : "null") + "' parsed=" + remaining);
                        }
                        if (remaining != null) {
                            outcome.anyParsed = true;
                            usedDx = dx; usedDy = dy;
                            break;
                        }
                    } catch (Exception inner) {
                        if (attempts < 5) {
                            logDebug("OCR exception at offset " + i + " (dx=" + dx + ", dy=" + dy + "): " + inner.getMessage());
                        }
                    }
                }

                LocalDateTime now = LocalDateTime.now();
                if (remaining != null) {
                    logDebug("Trek counter OCR (dx=" + usedDx + ", dy=" + usedDy + "): '" + raw + "' => '" + norm + "' -> remaining=" + remaining + (lastValue != null ? (", lastValue=" + lastValue) : "") + (lastDecreaseAt != null ? (", sinceDecrease=" + Duration.between(lastDecreaseAt, now).toSeconds() + "s") : ""));
                    if (remaining <= 0) {
                        outcome.finished = true;
                        return outcome;
                    }
                    if (lastValue == null) {
                        lastValue = remaining;
                        lastDecreaseAt = now;
                    } else {
                        if (remaining < lastValue) {
                            lastValue = remaining;
                            lastDecreaseAt = now;
                        } else {
                            if (lastDecreaseAt != null && Duration.between(lastDecreaseAt, now).compareTo(STAGNATION_TIMEOUT) >= 0) {
                                outcome.stagnated = true;
                                return outcome;
                            }
                        }
                    }
                } else {
                    logDebug("Trek counter OCR could not parse any number on attempt " + attempts + ". Last raw='" + (raw == null ? "" : raw) + "'");
                    if (Duration.between(noParseStart, now).compareTo(NO_PARSE_TIMEOUT) >= 0) {
                        logWarning("OCR timeout: 3 minutes without any number parsed. Aborting.");
                        return outcome;
                    }
                }
            } catch (Exception e) {
                logDebug("OCR error while reading trek counter: " + e.getMessage());
            }

            attempts++;
            sleepTask(OCR_POLL_INTERVAL_MS);
        }
    }

    private Integer readTrekCounterOnce() {
        Pattern fraction = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
        Pattern twoNumbersLoose = Pattern.compile("(\\d{1,3})\\D+(\\d{2,3})");
        String raw = null;
        String norm = null;
        for (int[] off : OCR_REGION_OFFSETS) {
            int dx = off[0];
            int dy = off[1];
            DTOPoint p1 = new DTOPoint(TREK_COUNTER_TOP_LEFT.getX() + dx, TREK_COUNTER_TOP_LEFT.getY() + dy);
            DTOPoint p2 = new DTOPoint(TREK_COUNTER_BOTTOM_RIGHT.getX() + dx, TREK_COUNTER_BOTTOM_RIGHT.getY() + dy);
            try {
                raw = emuManager.ocrRegionText(EMULATOR_NUMBER, p1, p2);
                norm = normalizeOcrText(raw);
                Integer remaining = parseRemaining(raw, norm, fraction, twoNumbersLoose);
                if (remaining != null) {
                    logDebug("Pre-check OCR (dx=" + dx + ", dy=" + dy + "): '" + raw + "' => '" + norm + "' -> remaining=" + remaining);
                    return remaining;
                }
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private String normalizeOcrText(String text) {
        if (text == null) return "";
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



    private Integer parseRemaining(String raw, String norm, Pattern fractionPattern, Pattern twoNumbersLoose) {
        // Helper to select the smallest plausible numerator among matches
        Integer best = extractBestFraction(raw, fractionPattern);
        if (best != null) return best;

        // Try alternate separators on raw
        String altRaw = raw == null ? null : raw.replace(':', '/').replace(';', '/').replace('-', '/').replace('|', '/').replace('\\', '/');
        best = extractBestFraction(altRaw, fractionPattern);
        if (best != null) return best;

        // Try normalized text
        best = extractBestFraction(norm, fractionPattern);
        if (best != null) return best;

        // Loose two-number pattern on raw
        if (raw != null) {
            Matcher m = twoNumbersLoose.matcher(raw);
            if (m.find()) {
                try {
                    int num = Integer.parseInt(m.group(1));
                    int den = Integer.parseInt(m.group(2));
                    if (den >= 50 && den <= 150) return num;
                } catch (NumberFormatException ignore) {}
            }
        }

        // Loose two-number pattern on norm
        if (norm != null) {
            Matcher m = twoNumbersLoose.matcher(norm);
            if (m.find()) {
                try {
                    int num = Integer.parseInt(m.group(1));
                    int den = Integer.parseInt(m.group(2));
                    if (den >= 50 && den <= 150) return num;
                } catch (NumberFormatException ignore) {}
            }
        }

        // Heuristic for 0100-like
        if (norm != null && (norm.matches("^0+/?1?0?0+$") || norm.matches("^0+100$"))) {
            return 0;
        }
        return null;
    }

    private Integer extractBestFraction(String text, Pattern fractionPattern) {
        if (text == null) return null;
        Matcher m = fractionPattern.matcher(text);
        Integer best = null;
        while (m.find()) {
            try {
                int num = Integer.parseInt(m.group(1));
                int den = Integer.parseInt(m.group(2));
                boolean denOk = den >= 50 && den <= 150;
                if (!denOk) continue;
                if (best == null || num < best) {
                    best = num;
                }
            } catch (NumberFormatException ignore) {
            }
        }
        return best;
    }

    private boolean ensureCheckboxActive() {
        logInfo("Checking checkbox state before proceeding with bag button.");

        // First check if active checkbox is already visible
        logDebug("Searching for active checkbox template");
        DTOImageSearchResult activeCheck = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_CHECK_ACTIVE, 85);
        logDebug("Active checkbox search result: found=" + activeCheck.isFound());
        if (activeCheck.isFound()) {
            logInfo("Checkbox is already active. Proceeding.");
            return true;
        }

        // Check if inactive checkbox is visible
        logDebug("Searching for inactive checkbox template");
        DTOImageSearchResult inactiveCheck = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_CHECK_INACTIVE, 85);
        logDebug("Inactive checkbox search result: found=" + inactiveCheck.isFound());
        if (inactiveCheck.isFound()) {
            logInfo("Checkbox is inactive. Clicking to activate it at position: " + inactiveCheck.getPoint());
            tapPoint(inactiveCheck.getPoint());
            sleepTask(500);

            // Verify that checkbox is now active
            logDebug("Verifying checkbox activation...");
            DTOImageSearchResult activeCheckRetry = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_CHECK_ACTIVE, 85);
            logDebug("Post-click active checkbox search: found=" + activeCheckRetry.isFound());
            if (activeCheckRetry.isFound()) {
                logInfo("Checkbox successfully activated.");
                return true;
            } else {
                logWarning("Checkbox click did not activate it properly.");
                return false;
            }
        }

        logWarning("Neither active nor inactive checkbox found. Template files may be missing or checkbox not visible on screen.");
        return false;
    }

    private void rescheduleOneHourLater(String reason) {
        LocalDateTime nextExecution = LocalDateTime.now().plusHours(1);
        logWarning(reason + ". Rescheduling task for one hour later.");
        this.reschedule(nextExecution);
    }

    private void exitEventDoubleBack() {
        try {
            tapBackButton();
            sleepTask(400);
            tapBackButton();
            sleepTask(400);
        } catch (Exception ignore) {
        }
    }
}