package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cl.camodev.wosbot.console.enumerable.EnumConfigurationKey.*;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.*;

/**
 * Task responsible for managing Crystal Laboratory operations.
 * 
 * <p>
 * This task handles:
 * <ul>
 * <li>Claiming available Fire Crystals (FC)</li>
 * <li>Purchasing discounted daily Refined Fire Crystals (RFC)</li>
 * <li>Weekly RFC refinement to reach configured targets (Mondays only)</li>
 * </ul>
 * 
 * <p>
 * <b>Weekly RFC Strategy:</b>
 * On Mondays, the task checks if enough FC is available to reach the weekly
 * RFC refinement target. If sufficient FC is available, it performs bulk
 * refinements.
 * If insufficient, it reschedules to check again in 2 hours.
 * 
 * <p>
 * <b>RFC Cost Tiers:</b>
 * RFC refinement costs increase with refinement level:
 * <ul>
 * <li>Refines 1-20: 20 FC each</li>
 * <li>Refines 21-40: 50 FC each</li>
 * <li>Refines 41-60: 100 FC each</li>
 * <li>Refines 61-80: 130 FC each</li>
 * <li>Refines 81-100: 160 FC each</li>
 * </ul>
 */
public class CrystalLaboratoryTask extends DelayedTask {

    // ===============================
    // CONSTANTS
    // ===============================

    private static final int MAX_CONSECUTIVE_FAILED_CLAIMS = 3;
    private static final int MAX_OCR_RETRIES = 5;
    private static final int NAVIGATION_RETRY_MINUTES = 5;
    private static final int INSUFFICIENT_FC_RETRY_HOURS = 2;

    // RFC tier costs (FC required per refinement at each tier)
    private static final int RFC_COST_TIER_1 = 20; // Refines 1-20
    private static final int RFC_COST_TIER_2 = 50; // Refines 21-40
    private static final int RFC_COST_TIER_3 = 100; // Refines 41-60
    private static final int RFC_COST_TIER_4 = 130; // Refines 61-80
    private static final int RFC_COST_TIER_5 = 160; // Refines 81-100

    // RFC tier boundaries
    private static final int RFC_TIER_1_MAX = 20;
    private static final int RFC_TIER_2_MAX = 40;
    private static final int RFC_TIER_3_MAX = 60;
    private static final int RFC_TIER_4_MAX = 80;
    private static final int RFC_TIER_5_MAX = 100;

    // OCR regions
    private static final DTOPoint CURRENT_FC_TOP_LEFT = new DTOPoint(590, 21);
    private static final DTOPoint CURRENT_FC_BOTTOM_RIGHT = new DTOPoint(700, 60);

    private static final DTOPoint CURRENT_RFC_TOP_LEFT = new DTOPoint(170, 1078);
    private static final DTOPoint CURRENT_RFC_BOTTOM_RIGHT = new DTOPoint(512, 1106);

    /** Pattern for extracting numbers with optional thousand separators */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d{1,3}(?:[.,]\\d{3})*|\\d+)");

    // ===============================
    // FIELDS
    // ===============================

    private boolean useDiscountedDailyRFC;
    private int weeklyRFCTarget;

    // ===============================
    // CONSTRUCTOR
    // ===============================

    /**
     * Constructs a new CrystalLaboratoryTask.
     * 
     * @param profile     The game profile this task operates on
     * @param tpDailyTask The task type enum from the daily task registry
     */
    public CrystalLaboratoryTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    // ===============================
    // CONFIGURATION
    // ===============================

    /**
     * Loads task configuration from the profile.
     * 
     * <p>
     * Configuration loaded:
     * <ul>
     * <li><b>BOOL_CRYSTAL_LAB_DAILY_DISCOUNTED_RFC:</b> Whether to purchase
     * discounted daily RFC</li>
     * <li><b>INT_WEEKLY_RFC:</b> Target number of RFC refinements per week</li>
     * </ul>
     */
    protected void loadConfiguration() {
        this.useDiscountedDailyRFC = profile.getConfig(
                BOOL_CRYSTAL_LAB_DAILY_DISCOUNTED_RFC,
                Boolean.class);

        this.weeklyRFCTarget = profile.getConfig(INT_WEEKLY_RFC, Integer.class);

        logInfo(String.format("Configuration loaded - Discounted RFC: %s, Weekly target: %d",
                useDiscountedDailyRFC, weeklyRFCTarget));
    }

    // ===============================
    // MAIN EXECUTION
    // ===============================

    /**
     * Main execution method for the Crystal Laboratory task.
     * 
     * <p>
     * <b>Execution Flow:</b>
     * <ol>
     * <li>Navigate to Crystal Laboratory</li>
     * <li>Claim available Fire Crystals</li>
     * <li>Purchase discounted daily RFC if enabled</li>
     * <li>Process weekly RFC refinements (Mondays only)</li>
     * <li>Reschedule to next game reset</li>
     * </ol>
     * 
     * <p>
     * Navigation or OCR failures trigger retry in
     * {@value #NAVIGATION_RETRY_MINUTES} minutes.
     * Insufficient FC for weekly target triggers retry in
     * {@value #INSUFFICIENT_FC_RETRY_HOURS} hours.
     */
    @Override
    protected void execute() {
        logInfo("Starting Crystal Laboratory task");
        loadConfiguration();

        if (!navigateToCrystalLaboratory()) {
            reschedule(LocalDateTime.now().plusMinutes(NAVIGATION_RETRY_MINUTES));
            return;
        }

        claimAllCrystals();

        if (useDiscountedDailyRFC) {
            purchaseDiscountedRFC();

            if (isMonday()) {
                WeeklyRFCResult result = processWeeklyRFC();

                if (result == WeeklyRFCResult.INSUFFICIENT_FC) {
                    // Override default reschedule - check again in 2 hours
                    reschedule(LocalDateTime.now().plusHours(INSUFFICIENT_FC_RETRY_HOURS));
                    return;
                }
            }
        }

        // Default reschedule: next game reset (daily execution)
        reschedule(UtilTime.getGameReset());
    }

    // ===============================
    // NAVIGATION
    // ===============================

    /**
     * Navigates to the Crystal Laboratory interface.
     * 
     * <p>
     * <b>Navigation Strategy:</b>
     * <ol>
     * <li>Open left menu city section</li>
     * <li>Find and tap troops button (Lancer shortcut)</li>
     * <li>Swipe to reveal Crystal Lab</li>
     * <li>Search for Crystal Lab FC button</li>
     * <li>If not found, use fallback coordinate</li>
     * <li>Validate arrival with UI template</li>
     * </ol>
     * 
     * @return true if navigation successful, false otherwise
     */
    private boolean navigateToCrystalLaboratory() {
        logInfo("Navigating to Crystal Laboratory");

        marchHelper.openLeftMenuCitySection(true);

        if (!findAndTapTroopsButton()) {
            return false;
        }

        return openCrystalLabInterface();
    }

    /**
     * Finds and taps the troops button in the left menu.
     * 
     * @return true if button found and tapped, false otherwise
     */
    private boolean findAndTapTroopsButton() {
        DTOImageSearchResult troopsResult = templateSearchHelper.searchTemplate(
                GAME_HOME_SHORTCUTS_LANCER,
                SearchConfig.builder().build());

        if (!troopsResult.isFound()) {
            logWarning("Could not locate troops button. Navigation failed.");
            return false;
        }

        tapPoint(troopsResult.getPoint());
        sleepTask(1000); // Wait for troops menu to open
        return true;
    }

    /**
     * Opens the Crystal Lab interface with fallback navigation.
     * 
     * <p>
     * Primary method: Search for FC button template.
     * Fallback method: Tap known coordinate and validate UI.
     * 
     * @return true if interface opened successfully, false otherwise
     */
    private boolean openCrystalLabInterface() {
        tapRandomPoint(new DTOPoint(637, 903), new DTOPoint(692, 914), 1, 500);

        DTOImageSearchResult validationResult = templateSearchHelper.searchTemplate(
                VALIDATION_CRYSTAL_LAB_UI,
                SearchConfig.builder().build());

        if (!validationResult.isFound()) {
            logWarning("Crystal Lab UI not found. Retrying in 5min.");
            return false;
        }
        logInfo("Successfully navigated to Crystal Laboratory");
        return true;
    }

    // ===============================
    // CRYSTAL CLAIMING
    // ===============================

    /**
     * Claims all available Fire Crystals.
     * 
     * <p>
     * Continuously searches for and taps the refine/claim button until
     * either no more claims are available or we hit the consecutive failure limit.
     * 
     * <p>
     * The consecutive failure threshold handles:
     * <ul>
     * <li>OCR instability during claim animations</li>
     * <li>Temporary button disappearance during UI updates</li>
     * <li>Flicker during rapid claiming</li>
     * </ul>
     */
    private void claimAllCrystals() {
        DTOImageSearchResult claimResult = templateSearchHelper.searchTemplate(
                CRYSTAL_LAB_REFINE_BUTTON,
                SearchConfig.builder().build());

        if (!claimResult.isFound()) {
            logInfo("No crystals available to claim.");
            return;
        }

        logInfo("Starting crystal claiming process");
        executeCrystalClaimLoop(claimResult);
    }

    /**
     * Executes the crystal claiming loop with failure tolerance.
     * 
     * @param initialClaimResult Initial search result for the claim button
     */
    private void executeCrystalClaimLoop(DTOImageSearchResult initialClaimResult) {
        DTOImageSearchResult claimResult = initialClaimResult;
        int consecutiveFailures = 0;

        while (claimResult.isFound() && consecutiveFailures < MAX_CONSECUTIVE_FAILED_CLAIMS) {
            logDebug("Claiming crystal...");

            tapRandomPoint(claimResult.getPoint(), claimResult.getPoint());
            sleepTask(100); // Brief pause for claim animation

            claimResult = templateSearchHelper.searchTemplate(
                    CRYSTAL_LAB_REFINE_BUTTON,
                    SearchConfig.builder().build());

            if (!claimResult.isFound()) {
                consecutiveFailures++;
                logDebug("Claim button not found. Consecutive failures: " +
                        consecutiveFailures + "/" + MAX_CONSECUTIVE_FAILED_CLAIMS);
            } else {
                consecutiveFailures = 0; // Reset on success
            }
        }

        logClaimLoopResult(consecutiveFailures);
    }

    /**
     * Logs the result of the crystal claiming loop.
     * 
     * @param consecutiveFailures Number of consecutive failures at loop exit
     */
    private void logClaimLoopResult(int consecutiveFailures) {
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILED_CLAIMS) {
            logInfo("Crystal claiming completed - no more claims available.");
        } else {
            logInfo("Crystal claiming process finished successfully.");
        }
    }

    // ===============================
    // DISCOUNTED RFC
    // ===============================

    /**
     * Purchases the discounted daily Refined Fire Crystal if available.
     * 
     * <p>
     * The game offers a 50% discounted RFC once per day. This method
     * checks for availability and purchases it automatically.
     */
    private void purchaseDiscountedRFC() {
        DTOImageSearchResult discountedResult = templateSearchHelper.searchTemplate(
                CRYSTAL_LAB_DAILY_DISCOUNTED_RFC,
                SearchConfig.builder().build());

        if (!discountedResult.isFound()) {
            logInfo("No discounted RFC available today.");
            return;
        }

        logInfo("50% discounted RFC available. Attempting to purchase.");
        executeDiscountedRFCPurchase();
    }

    /**
     * Executes the discounted RFC purchase by tapping the refine button.
     */
    private void executeDiscountedRFCPurchase() {
        DTOImageSearchResult refineResult = templateSearchHelper.searchTemplate(
                CRYSTAL_LAB_RFC_REFINE_BUTTON,
                SearchConfig.builder().build());

        if (refineResult.isFound()) {
            tapPoint(refineResult.getPoint());
            sleepTask(500); // Wait for purchase to process
            logInfo("Discounted RFC purchased successfully.");
        } else {
            logWarning("Could not find RFC refine button for discounted purchase.");
        }
    }

    // ===============================
    // WEEKLY RFC PROCESSING
    // ===============================

    /**
     * Processes weekly RFC refinements to reach the configured target.
     * 
     * <p>
     * This method:
     * <ol>
     * <li>Extracts current FC and RFC counts via OCR</li>
     * <li>Calculates FC needed to reach weekly target</li>
     * <li>Checks if sufficient FC is available</li>
     * <li>Performs bulk refinements if possible</li>
     * </ol>
     * 
     * @return Result indicating whether refinements were done, target reached,
     *         insufficient FC, or OCR failed
     */
    private WeeklyRFCResult processWeeklyRFC() {
        logInfo("Processing weekly RFC refinements (Monday check)");

        Integer currentFC = extractCurrentFC();
        if (currentFC == null) {
            return WeeklyRFCResult.OCR_FAILED;
        }

        Integer currentRFC = extractCurrentRFC();
        if (currentRFC == null) {
            return WeeklyRFCResult.OCR_FAILED;
        }

        return processRFCRefinements(currentFC, currentRFC);
    }

    /**
     * Extracts the current FC count via OCR.
     * 
     * @return Current FC count, or null if extraction failed
     */
    private Integer extractCurrentFC() {
        int fc = extractNumberWithOCR(
                CURRENT_FC_TOP_LEFT,
                CURRENT_FC_BOTTOM_RIGHT,
                "current FC");

        return fc == -1 ? null : fc;
    }

    /**
     * Extracts the current RFC refinement count via OCR.
     * 
     * @return Current RFC count, or null if extraction failed
     */
    private Integer extractCurrentRFC() {
        int rfc = extractNumberWithOCR(
                CURRENT_RFC_TOP_LEFT,
                CURRENT_RFC_BOTTOM_RIGHT,
                "current refined FC");

        return rfc == -1 ? null : rfc;
    }

    /**
     * Processes RFC refinements based on current resources and target.
     * 
     * @param currentFC  Available Fire Crystals
     * @param currentRFC Current RFC refinement level
     * @return Result of the refinement process
     */
    private WeeklyRFCResult processRFCRefinements(int currentFC, int currentRFC) {
        if (currentRFC >= weeklyRFCTarget) {
            logInfo(String.format("Weekly target (%d) already reached. Current: %d",
                    weeklyRFCTarget, currentRFC));
            return WeeklyRFCResult.TARGET_REACHED;
        }

        int neededFC = calculateFCNeeded(currentRFC, weeklyRFCTarget);

        logInfo(String.format("FC Analysis - Available: %d, Current RFC: %d, Target: %d, Needed: %d",
                currentFC, currentRFC, weeklyRFCTarget, neededFC));

        if (neededFC > currentFC) {
            logInfo(String.format("Insufficient FC. Need %d more FC to reach target.",
                    neededFC - currentFC));
            return WeeklyRFCResult.INSUFFICIENT_FC;
        }

        performBulkRefinements(currentRFC);
        return WeeklyRFCResult.REFINEMENTS_DONE;
    }

    /**
     * Performs bulk RFC refinements to reach the weekly target.
     * 
     * @param currentRFC Current RFC refinement level
     */
    private void performBulkRefinements(int currentRFC) {
        int refinesToDo = weeklyRFCTarget - currentRFC;

        logInfo(String.format("Sufficient FC available. Performing %d refinements.", refinesToDo));

        DTOImageSearchResult refineResult = templateSearchHelper.searchTemplate(
                CRYSTAL_LAB_RFC_REFINE_BUTTON,
                SearchConfig.builder().build());

        if (refineResult.isFound()) {
            tapRandomPoint(refineResult.getPoint(), refineResult.getPoint(), refinesToDo, 500);
            logInfo("Bulk refinements completed.");
        } else {
            logWarning("Could not find RFC refine button for bulk refinements.");
        }
    }

    // ===============================
    // OCR EXTRACTION
    // ===============================

    /**
     * Extracts a number from a screen region using OCR with retry logic.
     * 
     * <p>
     * This method handles numbers with optional thousand separators
     * (commas or periods) and normalizes them before parsing.
     * 
     * <p>
     * Example formats handled:
     * <ul>
     * <li>"1,234" → 1234</li>
     * <li>"1.234" → 1234</li>
     * <li>"1234" → 1234</li>
     * </ul>
     * 
     * @param topLeft     Top-left corner of OCR region
     * @param bottomRight Bottom-right corner of OCR region
     * @param description Description for logging purposes
     * @return Extracted number, or -1 if extraction failed
     */
    private int extractNumberWithOCR(DTOPoint topLeft, DTOPoint bottomRight, String description) {
        for (int attempt = 1; attempt <= MAX_OCR_RETRIES; attempt++) {
            logDebug("Extracting " + description + " via OCR (attempt " +
                    attempt + "/" + MAX_OCR_RETRIES + ")");

            try {
                String ocrResult = stringHelper.execute(
                        topLeft,
                        bottomRight,
                        1,
                        300L,
                        null,
                        s -> !s.isEmpty(),
                        s -> s);
                Integer number = parseNumberFromOCR(ocrResult);

                if (number != null) {
                    logInfo(description + ": " + number);
                    return number;
                }

            } catch (Exception e) {
                logWarning("OCR attempt " + attempt + " threw exception: " + e.getMessage());
            }

            if (attempt < MAX_OCR_RETRIES) {
                sleepTask(1000); // Wait before retry
            }
        }

        logWarning("Failed to extract " + description + " after " + MAX_OCR_RETRIES + " attempts");
        return -1;
    }

    /**
     * Parses a number from OCR text, handling thousand separators.
     * 
     * @param ocrText Raw OCR text
     * @return Parsed integer, or null if parsing failed
     */
    private Integer parseNumberFromOCR(String ocrText) {
        Matcher matcher = NUMBER_PATTERN.matcher(ocrText);

        if (!matcher.find()) {
            return null;
        }

        try {
            String normalized = matcher.group(1).replaceAll("[.,]", "");
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            logWarning("Failed to parse number from: " + ocrText);
            return null;
        }
    }

    // ===============================
    // RFC COST CALCULATION
    // ===============================

    /**
     * Calculates the total FC needed to refine from current level to target level.
     * 
     * <p>
     * RFC refinement costs increase with level according to tier boundaries:
     * <ul>
     * <li>Refines 1-20: {@value #RFC_COST_TIER_1} FC each</li>
     * <li>Refines 21-40: {@value #RFC_COST_TIER_2} FC each</li>
     * <li>Refines 41-60: {@value #RFC_COST_TIER_3} FC each</li>
     * <li>Refines 61-80: {@value #RFC_COST_TIER_4} FC each</li>
     * <li>Refines 81-100: {@value #RFC_COST_TIER_5} FC each</li>
     * </ul>
     * 
     * @param currentLevel Current RFC refinement level
     * @param targetLevel  Target RFC refinement level
     * @return Total FC needed to reach target from current level
     */
    private int calculateFCNeeded(int currentLevel, int targetLevel) {
        int totalFC = 0;

        for (int refine = currentLevel + 1; refine <= targetLevel; refine++) {
            totalFC += getRefinementCost(refine);
        }

        return totalFC;
    }

    /**
     * Gets the FC cost for a specific refinement level.
     * 
     * @param refineLevel The refinement level to check
     * @return FC cost for that refinement
     */
    private int getRefinementCost(int refineLevel) {
        if (refineLevel <= RFC_TIER_1_MAX) {
            return RFC_COST_TIER_1;
        } else if (refineLevel <= RFC_TIER_2_MAX) {
            return RFC_COST_TIER_2;
        } else if (refineLevel <= RFC_TIER_3_MAX) {
            return RFC_COST_TIER_3;
        } else if (refineLevel <= RFC_TIER_4_MAX) {
            return RFC_COST_TIER_4;
        } else if (refineLevel <= RFC_TIER_5_MAX) {
            return RFC_COST_TIER_5;
        }

        // For levels beyond 100, use highest tier cost
        return RFC_COST_TIER_5;
    }

    // ===============================
    // UTILITY METHODS
    // ===============================

    /**
     * Checks if today is Monday (UTC time).
     * 
     * @return true if today is Monday, false otherwise
     */
    private boolean isMonday() {
        return LocalDateTime.now(Clock.systemUTC()).getDayOfWeek() == DayOfWeek.MONDAY;
    }

    // ===============================
    // TASK FRAMEWORK OVERRIDES
    // ===============================

    /**
     * Specifies the required starting screen location for this task.
     * 
     * @return HOME as the required start location
     */
    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    // ===============================
    // INNER CLASSES
    // ===============================

    /**
     * Enumeration of possible weekly RFC processing results.
     */
    private enum WeeklyRFCResult {
        REFINEMENTS_DONE,
        TARGET_REACHED,
        INSUFFICIENT_FC,
        OCR_FAILED
    }
}