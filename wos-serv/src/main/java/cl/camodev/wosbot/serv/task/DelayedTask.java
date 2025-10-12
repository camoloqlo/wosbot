package cl.camodev.wosbot.serv.task;

import cl.camodev.utiles.UtilRally;
import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.ex.ProfileInReconnectStateException;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.serv.ocr.BotTextRecognitionProvider;
import cl.camodev.wosbot.serv.task.impl.ArenaTask;
import cl.camodev.wosbot.serv.task.impl.BearTrapTask;
import cl.camodev.wosbot.serv.task.impl.InitializeTask;
import java.awt.Color;
import java.util.List;

import cl.camodev.wosbot.almac.repo.ProfileRepository;
import net.sourceforge.tess4j.TesseractException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public abstract class DelayedTask implements Runnable, Delayed {

    protected volatile boolean recurring = true;
    protected LocalDateTime lastExecutionTime;
    protected LocalDateTime scheduledTime;
    protected String taskName;
    protected DTOProfiles profile;
    protected String EMULATOR_NUMBER;
    protected TpDailyTaskEnum tpTask;
    protected EmulatorManager emuManager = EmulatorManager.getInstance();
    protected ServScheduler servScheduler = ServScheduler.getServices();
    protected ServLogs servLogs = ServLogs.getServices();
    private ProfileLogger logger; // Will be initialized in the constructor
    protected BotTextRecognitionProvider provider;
    protected TextRecognitionRetrier<Integer> integerHelper;
    protected TextRecognitionRetrier<Duration> durationHelper;

    private static final int DEFAULT_RETRIES = 3;

    public DelayedTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        this.profile = profile;
        this.taskName = tpTask.getName();
        this.scheduledTime = LocalDateTime.now();
        this.EMULATOR_NUMBER = profile.getEmulatorNumber();
        this.tpTask = tpTask;
        this.logger = new ProfileLogger(this.getClass(), profile);
        this.provider = new BotTextRecognitionProvider(emuManager, EMULATOR_NUMBER);
        this.integerHelper = new TextRecognitionRetrier<>(provider);
        this.durationHelper = new TextRecognitionRetrier<>(provider);
    }

    protected Object getDistinctKey() {
        return null;
    }

    /**
     * Override this method to indicate from which screen the task should start.
     *
     * @return EnumStartLocation that indicates the required initial location
     */
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.ANY;
    }

    @Override
    public void run() {
        // Before executing, refresh the profile from the database to ensure current
        // configurations
        try {
            if (profile != null && profile.getId() != null) {
                DTOProfiles updated = ProfileRepository.getRepository().getProfileWithConfigsById(profile.getId());
                if (updated != null) {
                    this.profile = updated;
                }
            }
        } catch (Exception e) {
            // If profile refresh fails, continue with the existing instance
            logWarning("Could not refresh profile before execution: " + e.getMessage());
        }

        if (this instanceof InitializeTask) {
            execute();
            return;
        }

        if (!EmulatorManager.getInstance().isPackageRunning(EMULATOR_NUMBER, EmulatorManager.GAME.getPackageName())) {
            throw new HomeNotFoundException("Game is not running");
        }

        ensureCorrectScreenLocation(getRequiredStartLocation());
        // Validate stamina before executing the task
        if (consumesStamina()) {
            if (StaminaService.getServices().requiresUpdate(profile.getId())) {
                updateStaminaFromProfile();
            }

        }
        execute();
        ensureCorrectScreenLocation(EnumStartLocation.ANY);
    }

    protected abstract void execute();

    /**
     * Ensures the emulator is on the correct screen (Home or World) before
     * continuing.
     * It will attempt to navigate if it's on the wrong screen or press back if it's
     * lost.
     * 
     * @param requiredLocation The desired screen (HOME, WORLD or ANY).
     */
    protected void ensureCorrectScreenLocation(EnumStartLocation requiredLocation) {
        logDebug("Verifying screen location. Required: " + requiredLocation);

        for (int attempt = 1; attempt <= 10; attempt++) {
            DTOImageSearchResult home = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE, 90);
            DTOImageSearchResult world = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_WORLD, 90);
            DTOImageSearchResult reconnect = emuManager.searchTemplate(EMULATOR_NUMBER,
                    EnumTemplates.GAME_HOME_RECONNECT, 90);

            if (reconnect.isFound()) {
                throw new ProfileInReconnectStateException(
                        "Profile " + profile.getName() + " is in reconnect state, cannot execute task: " + taskName);
            }

            if (home.isFound() || world.isFound()) {
                // Found Home or World; check if we need to navigate to the correct location
                if (requiredLocation == EnumStartLocation.HOME && !home.isFound()) {
                    // We need HOME but we are in WORLD, navigate to HOME
                    logInfo("Navigating from WORLD to HOME...");
                    emuManager.tapAtPoint(EMULATOR_NUMBER, world.getPoint());
                    sleepTask(2000); // Wait for navigation

                    // Validate that we actually moved to HOME
                    DTOImageSearchResult homeAfterNav = emuManager.searchTemplate(EMULATOR_NUMBER,
                            EnumTemplates.GAME_HOME_FURNACE, 90);
                    if (!homeAfterNav.isFound()) {
                        logWarning("Failed to navigate to HOME, retrying...");
                        continue; // Try again
                    }
                    logInfo("Successfully navigated to HOME.");

                } else if (requiredLocation == EnumStartLocation.WORLD && !world.isFound()) {
                    // We need WORLD but we are in HOME, navigate to WORLD
                    logInfo("Navigating from HOME to WORLD...");
                    emuManager.tapAtPoint(EMULATOR_NUMBER, home.getPoint());
                    sleepTask(2000); // Wait for navigation

                    // Validate that we actually moved to WORLD
                    DTOImageSearchResult worldAfterNav = emuManager.searchTemplate(EMULATOR_NUMBER,
                            EnumTemplates.GAME_HOME_WORLD, 90);
                    if (!worldAfterNav.isFound()) {
                        logWarning("Failed to navigate to WORLD, retrying...");
                        continue; // Try again
                    }
                    logInfo("Successfully navigated to WORLD.");
                }
                // If requiredLocation is ANY, we can execute from either location
                return; // Success, correct screen is found
            } else {
                logWarning("Home/World screen not found. Tapping back button (Attempt " + attempt + "/10)");
                EmulatorManager.getInstance().tapBackButton(EMULATOR_NUMBER);
                sleepTask(100);
            }
        }

        logError("Failed to find Home/World screen after 10 attempts.");
        throw new HomeNotFoundException("Home not found after 10 attempts");
    }

    protected void ensureOnIntelScreen() {
        sleepTask(500);
        logInfo("Ensuring we are on the intel screen.");

        // First, check if we are already on the intel screen.
        if (isIntelScreenActive()) {
            logInfo("Already on the intel screen.");
            return;
        }
        logWarning("Not on intel screen. Attempting to navigate.");

        // If not on the intel screen, make sure we are on the world screen to find the
        // intel button.
        ensureCorrectScreenLocation(EnumStartLocation.WORLD);

        // Now, find and click the intel button.
        for (int i = 0; i < 3; i++) {
            DTOImageSearchResult intelButton = emuManager.searchTemplate(EMULATOR_NUMBER,
                    EnumTemplates.GAME_HOME_INTEL, 90);
            if (intelButton.isFound()) {
                logInfo("Intel button found. Tapping to open the intel screen.");
                emuManager.tapAtPoint(EMULATOR_NUMBER, intelButton.getPoint());
                sleepTask(1000); // Wait for screen transition

                // Check if successfully navigated to intel screen
                if (isIntelScreenActive()) {
                    logInfo("Successfully navigated to the intel screen.");
                    return;
                }

                logWarning("Tapped intel button, but still not on intel screen. Retrying...");
                tapBackButton();
                sleepTask(500);
            } else {
                logDebug("Intel button not found. Attempt " + (i + 1) + "/3. Retrying...");
                sleepTask(300);
            }
        }

        logError("Failed to find the intel button after 3 attempts.");
        throw new HomeNotFoundException("Failed to navigate to intel screen.");
    }

    /**
     * Helper method to check if we're currently on the intel screen.
     * Uses both image recognition and OCR for verification.
     * Makes two attempts before determining the screen state.
     *
     * @return true if we're on the intel screen, false otherwise
     */
    private boolean isIntelScreenActive() {
        // Make two attempts at detection
        for (int attempt = 0; attempt < 2; attempt++) {
            // Try image recognition first (faster)
            DTOImageSearchResult intelScreen1 = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_SCREEN_1,
                    90);
            DTOImageSearchResult intelScreen2 = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_SCREEN_2,
                    90);

            if (intelScreen1.isFound() || intelScreen2.isFound()) {
                logDebug("Intel screen confirmed via image template (attempt " + (attempt + 1) + ")");
                return true;
            }

            // Fallback to OCR check
            try {
                String intelText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(85, 15),
                        new DTOPoint(171, 62));
                if (intelText != null && intelText.toLowerCase().contains("intel")) {
                    logDebug("Intel screen confirmed via OCR (attempt " + (attempt + 1) + ")");
                    return true;
                }
            } catch (IOException | TesseractException e) {
                logWarning("Could not perform OCR to check for intel screen. Error: " + e.getMessage());
            }

            // If this is the first attempt and we didn't find the intel screen, wait
            // briefly before trying again
            if (attempt == 0) {
                sleepTask(300);
            }
        }

        // After two attempts, we still couldn't find the intel screen
        logDebug("Intel screen not detected after two attempts");
        return false;
    }

    protected int staminaRegenerationTime(int currentStamina, int targetStamina) {
        if (currentStamina >= targetStamina) {
            return 0;
        }
        int staminaNeeded = targetStamina - currentStamina;
        return staminaNeeded * 5; // 1 stamina every 5 minutes
    }

    protected void updateStaminaFromProfile() {
        // i need to update stamina on profile (maybe most reliable than intel screen)
        // go to profile
        tapRandomPoint(new DTOPoint(24, 24), new DTOPoint(61, 61), 1, 500);
        // go to stamina
        tapRandomPoint(new DTOPoint(223, 1101), new DTOPoint(244, 1123), 1, 500);

        DTOTesseractSettings settings = DTOTesseractSettings.builder()
                .setAllowedChars("0123456789/")
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255)) // White text
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                // .setReuseLastImage(true)
                .build();

        Integer stamina = integerHelper.execute(
                new DTOPoint(324, 255), new DTOPoint(477, 283), 5, 200L,
                settings,
                NumberValidators::isFractionFormat,
                NumberConverters::fractionToFirstInt);

        if (stamina != null) {
            logInfo("Stamina parsed and stored: " + stamina);
            StaminaService.getServices().setStamina(profile.getId(), stamina);
        }
        tapBackButton();
        tapBackButton();
    }

    protected Integer getSpentStamina() {
        DTOTesseractSettings settings = DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(254, 254, 254))
                .setDebug(true)
                .setAllowedChars("0123456789")
                .build();

        Integer spentStamina = readNumberValue(new DTOPoint(540, 1215), new DTOPoint(590, 1245), settings);

        return spentStamina;
    }

    protected void subtractStamina(Integer spentStamina, boolean rally) {
        if (spentStamina != null) {
            logInfo("Stamina decreased by " + spentStamina + ". Current stamina: "
                    + (getCurrentStamina() - spentStamina));
            StaminaService.getServices().subtractStamina(profile.getId(), spentStamina);
            return;
        }

        // If we couldn't parse stamina, use defaults
        int defaultStamina = rally ? 25 : 10;
        logWarning("No stamina value found on deployment. Default spending to " + defaultStamina + " stamina.");
        StaminaService.getServices().subtractStamina(profile.getId(), defaultStamina);
    }

    protected long parseTravelTime() {
        DTOTesseractSettings timeSettings = new DTOTesseractSettings.Builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(false)
                .setDebug(true)
                .setAllowedChars("0123456789:")
                .build();

        try {
            String timeStr = OCRWithRetries(new DTOPoint(521, 1141), new DTOPoint(608, 1162), 5, timeSettings);
            if (timeStr != null && !timeStr.isEmpty()) {
                long travelTimeSeconds = UtilTime.parseTimeToSeconds(timeStr);
                if (travelTimeSeconds > 0) {
                    logInfo("Successfully parsed travel time: " + timeStr + " (" + travelTimeSeconds + "s)");
                    return travelTimeSeconds * 2 + 2;
                }
            }
            logWarning("OCR returned null, empty, or invalid travel time");
        } catch (Exception e) {
            logError("Error parsing travel time: " + e.getMessage());
        }

        return 0; // Explicit failure - caller must check
    }

    protected int getCurrentStamina() {
        return StaminaService.getServices().getCurrentStamina(profile.getId());
    }

    protected boolean checkStaminaAndMarchesOrReschedule(int minStaminaLevel, int refreshStaminaLevel) {
        int currentStamina = getCurrentStamina();
        logInfo("Current stamina: " + currentStamina);

        if (currentStamina < minStaminaLevel) {
            LocalDateTime rescheduleTime = LocalDateTime.now()
                    .plusMinutes(staminaRegenerationTime(currentStamina, refreshStaminaLevel));
            reschedule(rescheduleTime);
            logWarning("Not enough stamina for expedition. (Current: " + currentStamina + "/" + minStaminaLevel
                    + "). Rescheduling task to run in "
                    + UtilTime.localDateTimeToDDHHMMSS(rescheduleTime));
            return false;
        }

        if (!checkMarchesAvailable()) {
            logWarning("No marches available, rescheduling for in 1 minute.");
            reschedule(LocalDateTime.now().plusMinutes(1));
            return false;
        }
        return true;
    }

    protected void selectFlag(Integer flagNumber) {
        tapPoint(UtilRally.getMarchFlagPoint(flagNumber));
        sleepTask(300);
        String str = OCRWithRetries("unlock", new DTOPoint(297, 126), new DTOPoint(424, 168));
        if (str != null && str.toLowerCase().equals("unlock")) {
            // Why would you select a flag that you don't have available..........
            logWarning("This flag is not unlocked, proceeding without selecting a flag.");
            tapBackButton();
        }
    }

    protected boolean checkMarchesAvailable() {
        // Open active marches panel
        tapPoint(new DTOPoint(2, 550));
        sleepTask(200);
        tapRandomPoint(new DTOPoint(340, 265), new DTOPoint(340, 265), 3, 100);

        // Define march slot coordinates
        DTOPoint[] marchTopLeft = {
                new DTOPoint(189, 740), // March 6
                new DTOPoint(189, 667), // March 5
                new DTOPoint(189, 594), // March 4
                new DTOPoint(189, 521), // March 3
                new DTOPoint(189, 448), // March 2
                new DTOPoint(189, 375), // March 1
        };
        DTOPoint[] marchBottomRight = {
                new DTOPoint(258, 768), // March 6
                new DTOPoint(258, 695), // March 5
                new DTOPoint(258, 622), // March 4
                new DTOPoint(258, 549), // March 3
                new DTOPoint(258, 476), // March 2
                new DTOPoint(258, 403), // March 1
        };

        // Check each march slot for "idle" status
        try {
            for (int marchSlot = 0; marchSlot < 6; marchSlot++) {
                for (int attempt = 0; attempt < 3; attempt++) {
                    String ocrResult = emuManager.ocrRegionText(EMULATOR_NUMBER,
                            marchTopLeft[marchSlot],
                            marchBottomRight[marchSlot]);

                    if (ocrResult.toLowerCase().contains("idle")) {
                        logInfo("Idle march detected in slot " + (6 - marchSlot));
                        closeLeftMenu();
                        return true;
                    }

                    if (attempt < 2) {
                        sleepTask(100);
                    }
                }
                logDebug("March slot " + (6 - marchSlot) + " is not idle");
            }
        } catch (IOException | TesseractException e) {
            logError("OCR attempt failed while checking marches: " + e.getMessage());
            closeLeftMenu();
            return false;
        }

        logInfo("No idle marches detected in any of the 6 slots.");
        closeLeftMenu();
        return false;
    }

    protected void closeLeftMenu() {
        tapPoint(new DTOPoint(110, 270));
        sleepTask(500);
        tapPoint(new DTOPoint(463, 548));
        sleepTask(500);
    }

    protected boolean disableAutoJoin() {
        ensureCorrectScreenLocation(EnumStartLocation.ANY);
        // Navigate to the alliance screen
        logDebug("Navigating to Alliance screen");

        // Tap on Alliance button at bottom of screen
        tapRandomPoint(new DTOPoint(493, 1187), new DTOPoint(561, 1240));
        sleepTask(3000);

        // Locate the Alliance War button
        DTOImageSearchResult menuResult = searchTemplateWithRetries(EnumTemplates.ALLIANCE_WAR_BUTTON);
        if (!menuResult.isFound()) {
            logError("Alliance War button not found");
            return false;
        }

        // Open the Alliance War menu
        logDebug("Opening Alliance War menu");
        tapPoint(menuResult.getPoint());
        sleepTask(1000);

        // Open the auto-join menu
        logDebug("Opening auto-join settings");
        tapRandomPoint(new DTOPoint(260, 1200), new DTOPoint(450, 1240));
        sleepTask(1500);

        // Disabling auto-join
        tapRandomPoint(new DTOPoint(120, 1069), new DTOPoint(249, 1122));
        sleepTask(300);

        // Return to home screen
        logDebug("Returning to home screen");
        tapBackButton();
        sleepTask(300);
        tapBackButton();
        sleepTask(300);
        tapBackButton();
        sleepTask(300);

        return true;
    }

    protected Integer readNumberValue(DTOPoint topLeft, DTOPoint bottomRight, DTOTesseractSettings settings) {
        Integer result = integerHelper.execute(topLeft,
                bottomRight,
                5,
                200L,
                settings,
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));
        logDebug("Number value read: " + (result != null ? result : "null"));
        return result;
    }

    protected DTOImageSearchResult searchTemplateWithRetries(EnumTemplates template) {
        return searchTemplateWithRetries(template, 90, DEFAULT_RETRIES);
    }

    protected DTOImageSearchResult searchTemplateWithRetries(EnumTemplates template, int maxRetries) {
        return searchTemplateWithRetries(template, 90, maxRetries, 200);
    }

    protected DTOImageSearchResult searchTemplateWithRetries(EnumTemplates template, int maxRetries, long delayMs) {
        return searchTemplateWithRetries(template, 90, maxRetries, delayMs);
    }

    protected DTOImageSearchResult searchTemplateWithRetries(EnumTemplates template, int threshold, int maxRetries) {
        return searchTemplateWithRetries(template, threshold, maxRetries, 200);
    }
    protected DTOImageSearchResult searchTemplateWithRetries(EnumTemplates template, int threshold, int maxRetries, long delayMs) {
        DTOImageSearchResult result = null;
        for (int i = 0; i < maxRetries && (result == null || !result.isFound()); i++) {
            logDebug("Searching template " + template + ", (attempt " + (i + 1) + "/" + maxRetries + ")");
            result = emuManager.searchTemplate(EMULATOR_NUMBER, template, threshold);
            sleepTask(delayMs);
        }
        logDebug(result.isFound() ? "Template " + template + " found." : "Template " + template + " not found.");
        return result;
    }

    protected String OCRWithRetries(String searchStringLower, DTOPoint p1, DTOPoint p2) {
        return OCRWithRetries(searchStringLower, p1, p2, DEFAULT_RETRIES);
    }

    protected DTOImageSearchResult searchTemplateRegionWithRetries(
            EnumTemplates template, DTOPoint topLeft, DTOPoint bottomRight) {
        return searchTemplateRegionWithRetries(
                template, topLeft, bottomRight,
                90, DEFAULT_RETRIES, 200L);
    }
    protected DTOImageSearchResult searchTemplateRegionWithRetries(
            EnumTemplates template, DTOPoint topLeft, DTOPoint bottomRight, int retries, long delayMs) {
        return searchTemplateRegionWithRetries(
                template, topLeft, bottomRight,
                90, retries, delayMs);
    }

        protected DTOImageSearchResult searchTemplateRegionWithRetries(
            EnumTemplates template, DTOPoint topLeft, DTOPoint bottomRight,
            int threshold, int maxRetries, long delayMs) {
        DTOImageSearchResult result = null;
        for (int i = 0; i < maxRetries && (result == null || !result.isFound()); i++) {
            logDebug("Searching template " + template + ", (attempt " + (i + 1) + "/" + maxRetries + ")");
            result = emuManager.searchTemplate(EMULATOR_NUMBER, template, topLeft, bottomRight, threshold);
            sleepTask(delayMs);
        }
        if (result == null) {
            throw new IllegalStateException("Template search did not return a result for template: " + template);
        }
        logDebug(result.isFound() ? "Template " + template + " found." : "Template " + template + " not found.");
        return result;
    }

    protected List<DTOImageSearchResult> searchTemplatesWithRetries(EnumTemplates template, int threshold,
            int maxRetries, int maxResults) {
        List<DTOImageSearchResult> result = null;
        for (int i = 0; i < maxRetries && (result == null || result.isEmpty()); i++) {
            logDebug("Searching template " + template + ", (attempt " + (i + 1) + "/" + maxRetries + ")");
            result = emuManager.searchTemplates(EMULATOR_NUMBER, template, threshold, maxResults);
            sleepTask(200);
        }
        logDebug(!result.isEmpty() ? "Template " + template + " found " + result.size() + " times."
                : "Template " + template + " not found.");
        return result;
    }

    protected List<DTOImageSearchResult> searchTemplatesWithRetries(EnumTemplates template, DTOPoint topLeft,
            DTOPoint bottomRight, int threshold, int maxRetries, int maxResults) {
        List<DTOImageSearchResult> result = null;
        for (int i = 0; i < maxRetries && (result == null || result.isEmpty()); i++) {
            logDebug("Searching template " + template + ", (attempt " + (i + 1) + "/" + maxRetries + ")");
            result = emuManager.searchTemplates(EMULATOR_NUMBER, template, topLeft, bottomRight, threshold, maxResults);
            sleepTask(200);
        }
        logDebug(!result.isEmpty() ? "Template " + template + " found " + result.size() + " times."
                : "Template " + template + " not found.");
        return result;
    }

    protected String OCRWithRetries(String searchString, DTOPoint p1, DTOPoint p2, int maxRetries) {
        String result = null;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            logDebug(
                    "Performing OCR to find '" + searchString + "' (attempt " + (attempt + 1) + "/" + maxRetries + ")");
            try {
                result = emuManager.ocrRegionText(EMULATOR_NUMBER, p1, p2);
                if (result != null && result.toLowerCase().contains(searchString.toLowerCase())) {
                    logDebug("OCRWithRetries result: " + result);
                    return result;
                }
            } catch (IOException | TesseractException e) {
                logWarning("OCR attempt " + (attempt + 1) + " threw an exception: " + e.getMessage());
            }
            sleepTask(200);
        }
        return null;
    }

    protected String OCRWithRetries(DTOPoint p1, DTOPoint p2) {
        return OCRWithRetries(p1, p2, 5);
    }

    protected String OCRWithRetries(DTOPoint p1, DTOPoint p2, int maxRetries) {
        String result = null;
        for (int attempt = 0; attempt < maxRetries && (result == null || result.isEmpty()); attempt++) {
            try {
                result = emuManager.ocrRegionText(EMULATOR_NUMBER, p1, p2);
            } catch (IOException | TesseractException e) {
                logWarning("OCR attempt " + attempt + " threw an exception: " + e.getMessage());
            }
            sleepTask(200);
        }
        logDebug("OCRWithRetries result: " + result);
        return result;
    }

    protected String OCRWithRetries(String searchString, DTOPoint p1, DTOPoint p2, int maxRetries,
            DTOTesseractSettings settings) {
        String result = null;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            logDebug(
                    "Performing OCR to find '" + searchString + "' (attempt " + (attempt + 1) + "/" + maxRetries + ")");
            try {
                result = emuManager.ocrRegionText(EMULATOR_NUMBER, p1, p2, settings);
                if (result != null && result.toLowerCase().contains(searchString.toLowerCase())) {
                    logDebug("OCRWithRetries result: " + result);
                    return result;
                }
            } catch (IOException | TesseractException e) {
                logWarning("OCR attempt " + (attempt + 1) + " threw an exception: " + e.getMessage());
            }
            sleepTask(200);
        }
        return null;
    }

    protected String OCRWithRetries(DTOPoint p1, DTOPoint p2, int maxRetries, DTOTesseractSettings settings) {
        String result = null;
        for (int attempt = 0; attempt < maxRetries && (result == null || result.isEmpty()); attempt++) {
            try {
                result = emuManager.ocrRegionText(EMULATOR_NUMBER, p1, p2, settings);
            } catch (IOException | TesseractException e) {
                logWarning("OCR attempt " + attempt + " threw an exception: " + e.getMessage());
            }
            sleepTask(200);
        }
        logDebug("OCRWithRetries result: " + result);
        return result;
    }

    public boolean isBearRunning() {
        DTOImageSearchResult result = searchTemplateWithRetries(EnumTemplates.BEAR_HUNT_IS_RUNNING,3,500L);
        return result.isFound();
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public LocalDateTime getLastExecutionTime() {
        return lastExecutionTime;
    }

    public void setLastExecutionTime(LocalDateTime lastExecutionTime) {
        this.lastExecutionTime = lastExecutionTime;
    }

    public Integer getTpDailyTaskId() {
        return tpTask.getId();
    }

    public TpDailyTaskEnum getTpTask() {
        return tpTask;
    }

    public void reschedule(LocalDateTime rescheduledTime) {
        Duration difference = Duration.between(LocalDateTime.now(), rescheduledTime);
        scheduledTime = LocalDateTime.now().plus(difference);
    }

    protected void sleepTask(long millis) {
        try {
            // long speedFactor = (long) (millis*1.3);
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task was interrupted during sleep", e);
        }
    }

    public String getTaskName() {
        return taskName;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = scheduledTime.toEpochSecond(ZoneOffset.UTC) - LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        return unit.convert(diff, TimeUnit.SECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (this == o)
            return 0;

        // Priority 1: InitializeTask has highest priority
        boolean thisInit = this instanceof InitializeTask;
        boolean otherInit = o instanceof InitializeTask;
        if (thisInit && !otherInit)
            return -1;
        if (!thisInit && otherInit)
            return 1;

        // Priority 2: BearTrapTask
        boolean thisBearTrap = this instanceof BearTrapTask;
        boolean otherBearTrap = o instanceof BearTrapTask;

        if (thisBearTrap && !otherBearTrap && this.getDelay(TimeUnit.NANOSECONDS) <= 0) {
            return -1;
        }

        if (!thisBearTrap && otherBearTrap && o.getDelay(TimeUnit.NANOSECONDS) <= 0) {
            return 1;
        }

        // Priority 3: ArenaTask
        boolean thisArena = this instanceof ArenaTask;
        boolean otherArena = o instanceof ArenaTask;

        if (thisArena && !otherArena && this.getDelay(TimeUnit.NANOSECONDS) <= 0)
            return -1;

        if (!thisArena && otherArena && o.getDelay(TimeUnit.NANOSECONDS) <= 0)
            return 1;

        // For tasks of same priority, compare by scheduled time
        long diff = this.getDelay(TimeUnit.NANOSECONDS)
                - o.getDelay(TimeUnit.NANOSECONDS);
        return Long.compare(diff, 0);
    }

    public LocalDateTime getScheduled() {
        return scheduledTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof DelayedTask))
            return false;
        if (getClass() != o.getClass())
            return false;

        DelayedTask that = (DelayedTask) o;

        if (tpTask != that.tpTask)
            return false;
        if (!Objects.equals(profile.getId(), that.profile.getId()))
            return false;

        Object keyThis = this.getDistinctKey();
        Object keyThat = that.getDistinctKey();
        if (keyThis != null || keyThat != null) {
            return Objects.equals(keyThis, keyThat);
        }

        return true;
    }

    @Override
    public int hashCode() {
        Object key = getDistinctKey();
        if (key != null) {
            return Objects.hash(getClass(), tpTask, profile.getId(), key);
        } else {
            return Objects.hash(getClass(), tpTask, profile.getId());
        }
    }

    public boolean provideDailyMissionProgress() {
        return false;
    }

    public boolean provideTriumphProgress() {
        return false;
    }

    protected boolean consumesStamina() {
        return false;
    }

    public void logInfo(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.info(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), message);
    }

    public void logWarning(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.warn(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), message);
    }

    public void logError(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.error(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), message);
    }

    public void logError(String message, Throwable t) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.error(prefixedMessage, t);
        servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), message);
    }

    public void logDebug(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.debug(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.DEBUG, taskName, profile.getName(), message);
    }

    /**
     * Taps at the specified point on the emulator screen.
     *
     * @param point The point to tap.
     */
    public void tapPoint(DTOPoint point) {
        emuManager.tapAtPoint(EMULATOR_NUMBER, point);
    }

    /**
     * Taps at a random point within the rectangle defined by two points on the
     * emulator screen.
     *
     * @param p1 The first corner of the rectangle.
     * @param p2 The opposite corner of the rectangle.
     */
    public void tapRandomPoint(DTOPoint p1, DTOPoint p2) {
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, p1, p2);
    }

    /**
     * Taps at random points within the rectangle defined by two points on the
     * emulator screen,
     * repeating the action a specified number of times with a delay between taps.
     *
     * @param p1    The first corner of the rectangle.
     * @param p2    The opposite corner of the rectangle.
     * @param count The number of taps to perform.
     * @param delay The delay in milliseconds between each tap.
     */
    public void tapRandomPoint(DTOPoint p1, DTOPoint p2, int count, int delay) {
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, p1, p2, count, delay);
    }

    /**
     * Performs a swipe action from a start point to an end point on the emulator
     * screen.
     *
     * @param start The starting point of the swipe.
     * @param end   The ending point of the swipe.
     */
    public void swipe(DTOPoint start, DTOPoint end) {
        emuManager.executeSwipe(EMULATOR_NUMBER, start, end);
    }

    /**
     * Taps the back button on the emulator.
     */
    public void tapBackButton() {
        emuManager.tapBackButton(EMULATOR_NUMBER);
    }

}