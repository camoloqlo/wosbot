package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TundraTrekAutoTask extends DelayedTask {
    private static class WaitOutcome {
        boolean finished;   // reached 0/100
        boolean anyParsed;  // at least one OCR parse succeeded (any value)
        boolean stagnated;  // values did not decrease for OCR_MAX_WAIT
    }

    // =========================== CONSTANTS ===========================
    // Navigation points (to be filled using ADB-captured coordinates)
    private static final DTOPoint SIDE_MENU_AREA_START = new DTOPoint(3, 513);
    private static final DTOPoint SIDE_MENU_AREA_END = new DTOPoint(26, 588);
    private static final DTOPoint CITY_TAB_BUTTON = new DTOPoint(110, 270);
    private static final DTOPoint SCROLL_START_POINT = new DTOPoint(400, 800);
    private static final DTOPoint SCROLL_END_POINT = new DTOPoint(400, 100);
    private static final DTOPoint SKIP_BUTTON = new DTOPoint(71, 827);
    private static final DTOPoint RESULT_SKIP_BUTTON = new DTOPoint(640, 175);

    // OCR region for the trek counter (top-right indicator like "14/100").
    // NOTE: Calibrate via ADB/screenshot if needed.
    private static final DTOPoint TREK_COUNTER_TOP_LEFT = new DTOPoint(520, 18);
    private static final DTOPoint TREK_COUNTER_BOTTOM_RIGHT = new DTOPoint(655, 80);

    // Offsets to try around the expected OCR region (favor left/up as requested)
    private static final int[][] OCR_REGION_OFFSETS = new int[][]{
        // Favor left + much more up, then vary gradually
        {-200, -200}, {-160, -200}, {-120, -200},
        {-160, -80}, {-160, -120}, {-160, -160},
        {-120, -80}, {-80, -80}, {-40, -80}, {0, -80},
        // Previous near region attempts
        {0, 0}, {-40, -10}, {-80, -20}, {-120, -30}, {-160, -40}
    };

    // Polling parameters
    private static final long OCR_POLL_INTERVAL_MS = 2500;
    private static final Duration OCR_MAX_WAIT = Duration.ofMinutes(2);

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
                    logWarning("Timeout (2 min) with no valid OCR. Exiting with double back and rescheduling in 10 minutes.");
                    exitEventDoubleBack();
                } else if (outcome.stagnated) {
                    logWarning("Timeout (2 min) without decrease (values same or higher). Exiting with single back and rescheduling in 10 minutes.");
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
        // First try to click the Auto button
        DTOImageSearchResult autoBtn = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_AUTO_BUTTON, 85);
        if (autoBtn.isFound()) {
            tapPoint(autoBtn.getPoint());
            sleepTask(500);
        } else {
            logWarning("Auto button not found (autoTrek.png). Searching for Skip button as alternative...");

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
                DTOImageSearchResult autoRetry = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_AUTO_BUTTON, 85);
                if (autoRetry.isFound()) {
                    logInfo("Auto button now visible after skip - clicking it.");
                    tapPoint(autoRetry.getPoint());
                    sleepTask(500);
                }
            } else {
                logWarning("Neither Auto button nor Skip button found. Cannot start automation.");
                tapBackButton();
                sleepTask(500);
                return false;
            }
        }

        // Then click the Bag button
        DTOImageSearchResult bagBtn = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_BAG_BUTTON, 85);
        if (bagBtn.isFound()) {
            tapPoint(bagBtn.getPoint());
            sleepTask(500);
        } else {
            logWarning("Bag button not found (bagTrek.png). Searching for Skip button...");

            // If Bag button not found, try Skip button
            DTOImageSearchResult skipBtn = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.TUNDRA_TREK_SKIP_BUTTON, 85);
            if (skipBtn.isFound()) {
                logInfo("Skip button found - clicking to proceed.");
                tapPoint(skipBtn.getPoint());
                sleepTask(500);
                // Additional tab press after skip
                tapPoint(skipBtn.getPoint());
                sleepTask(500);
            } else {
                logWarning("Neither Bag button nor Skip button found. Using back button to exit.");
                tapBackButton();
                sleepTask(500);
                return false;
            }
        }
        return true;
    }

    private WaitOutcome waitUntilTrekCounterZero() {
    LocalDateTime noParseStart = LocalDateTime.now();
        Pattern fraction = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
        Pattern twoNumbersLoose = Pattern.compile("(\\d{1,3})\\D+(\\d{2,3})");
        int attempts = 0;
        WaitOutcome outcome = new WaitOutcome();
    Integer lastValue = null;
    LocalDateTime lastDecreaseAt = null;

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
                        if (remaining != null) {
                            outcome.anyParsed = true;
                            usedDx = dx; usedDy = dy;
                            break;
                        }
                    } catch (Exception inner) {
                        // Try next offset
                    }
                }

                if (remaining != null) {
                    logDebug("Trek counter OCR (dx=" + usedDx + ", dy=" + usedDy + "): '" + raw + "' => '" + norm + "' -> remaining=" + remaining + (lastValue != null ? (", lastValue=" + lastValue) : "") + (lastDecreaseAt != null ? (", sinceDecrease=" + Duration.between(lastDecreaseAt, LocalDateTime.now()).toSeconds() + "s") : ""));
                    if (remaining <= 0) {
                        outcome.finished = true;
                        return outcome;
                    }
                    LocalDateTime now = LocalDateTime.now();
                    if (lastValue == null) {
                        lastValue = remaining;
                        lastDecreaseAt = now;
                    } else {
                        if (remaining < lastValue) {
                            // Progress detected: update lastValue and reset stagnation timer
                            lastValue = remaining;
                            lastDecreaseAt = now;
                        } else {
                            // Same or higher: check stagnation window
                            if (lastDecreaseAt != null && Duration.between(lastDecreaseAt, now).compareTo(OCR_MAX_WAIT) >= 0) {
                                outcome.stagnated = true;
                                return outcome;
                            }
                        }
                    }
                } else {
                    logDebug("Trek counter OCR could not parse any offset on attempt " + attempts + ". Last raw='" + (raw == null ? "" : raw) + "'");
                    if (!outcome.anyParsed) {
                        if (Duration.between(noParseStart, LocalDateTime.now()).compareTo(OCR_MAX_WAIT) >= 0) {
                            return outcome;
                        }
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