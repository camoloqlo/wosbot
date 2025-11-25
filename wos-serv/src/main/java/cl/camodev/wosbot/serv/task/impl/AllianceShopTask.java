package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.utiles.PriorityItemUtil;
import cl.camodev.wosbot.console.enumerable.AllianceShopItem;
import cl.camodev.wosbot.ot.*;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;

import static cl.camodev.wosbot.console.enumerable.EnumTemplates.*;

/**
 * Alliance Shop Task - Automatically purchases items from the Alliance Shop
 * based on configured priorities.
 * 
 * <p>
 * This task is triggered by {@link AllianceTechTask} when the profile has
 * enough alliance coins
 * (above the minimum threshold). It's a one-shot task that processes the entire
 * purchase list
 * and then exits without rescheduling.
 * 
 * <p>
 * <b>Key Features:</b>
 * <ul>
 * <li>Priority-based purchasing system (processes items in configured
 * order)</li>
 * <li>Multi-tab support (Weekly/Daily tabs, some items appear in both)</li>
 * <li>Discount threshold validation (only buys items with sufficient
 * discount)</li>
 * <li>Minimum coins protection (stops before depleting below threshold)</li>
 * <li>Sold-out detection per tab</li>
 * <li>Dynamic item position detection via template matching</li>
 * <li>Expert unlock detection (adjusts Y-axis coordinates automatically)</li>
 * </ul>
 * 
 * <p>
 * <b>Purchase Flow:</b>
 * <ol>
 * <li>Navigate to Alliance Shop and read current coins</li>
 * <li>Validate minimum coins threshold</li>
 * <li>Load enabled purchase priorities from config</li>
 * <li>Detect Expert unlock status (affects item layout)</li>
 * <li>For each priority (highest to lowest):
 * <ul>
 * <li>Search for item in applicable tab(s)</li>
 * <li>Check if sold out</li>
 * <li>Read and validate price (discount threshold)</li>
 * <li>Read available quantity</li>
 * <li>Calculate affordable quantity (respecting min coins)</li>
 * <li>Execute purchase</li>
 * <li>Update remaining coins</li>
 * </ul>
 * </li>
 * <li>Stop when minimum coins reached or all priorities processed</li>
 * </ol>
 * 
 * <p>
 * <b>Configuration:</b>
 * <ul>
 * <li>Minimum coins to keep (won't spend below this threshold)</li>
 * <li>Minimum discount percentage required for purchase</li>
 * <li>Priority list of items to purchase (ordered by priority)</li>
 * </ul>
 * 
 * <p>
 * <b>Important Notes:</b>
 * <ul>
 * <li>This task does NOT reschedule - it's a one-shot triggered task</li>
 * <li>Items are exhausted across all applicable tabs before moving to next
 * priority</li>
 * <li>Discount tolerance of 4% is applied (actual threshold = configured -
 * 4%)</li>
 * <li>If quantity OCR fails, assumes 1 item available</li>
 * <li>Expert unlock adds 121px Y-offset to all item coordinates</li>
 * </ul>
 */
public class AllianceShopTask extends DelayedTask {

    // ========================================================================
    // CONSTANTS - Navigation Coordinates
    // ========================================================================

    private static final DTOPoint ALLIANCE_BUTTON_TOP_LEFT = new DTOPoint(493, 1187);
    private static final DTOPoint ALLIANCE_BUTTON_BOTTOM_RIGHT = new DTOPoint(561, 1240);
    private static final DTOPoint SHOP_DETAILS_TOP_LEFT = new DTOPoint(580, 30);
    private static final DTOPoint SHOP_DETAILS_BOTTOM_RIGHT = new DTOPoint(670, 50);
    private static final DTOPoint COINS_TOP_LEFT = new DTOPoint(272, 257);
    private static final DTOPoint COINS_BOTTOM_RIGHT = new DTOPoint(443, 285);
    private static final DTOPoint CLOSE_TOP_LEFT = new DTOPoint(270, 30);
    private static final DTOPoint CLOSE_BOTTOM_RIGHT = new DTOPoint(280, 80);

    // ========================================================================
    // CONSTANTS - Purchase Dialog Coordinates
    // ========================================================================

    private static final DTOPoint MAX_BUTTON_TOP_LEFT = new DTOPoint(596, 690);
    private static final DTOPoint MAX_BUTTON_BOTTOM_RIGHT = new DTOPoint(626, 717);
    private static final DTOPoint PLUS_BUTTON_TOP_LEFT = new DTOPoint(397, 691);
    private static final DTOPoint PLUS_BUTTON_BOTTOM_RIGHT = new DTOPoint(425, 716);
    private static final DTOPoint CONFIRM_BUTTON_TOP_LEFT = new DTOPoint(330, 815);
    private static final DTOPoint CONFIRM_BUTTON_BOTTOM_RIGHT = new DTOPoint(420, 840);

    // ========================================================================
    // CONSTANTS - Tab Coordinates
    // ========================================================================

    private static final DTOPoint WEEKLY_TAB_TOP_LEFT = new DTOPoint(450, 1233);
    private static final DTOPoint WEEKLY_TAB_BOTTOM_RIGHT = new DTOPoint(590, 1263);
    private static final DTOPoint DAILY_TAB_TOP_LEFT = new DTOPoint(150, 1233);
    private static final DTOPoint DAILY_TAB_BOTTOM_RIGHT = new DTOPoint(290, 1263);

    // ========================================================================
    // CONSTANTS - Grid Layout
    // ========================================================================

    private static final int GRID_START_X = 27;
    private static final int GRID_START_Y = 192;
    private static final int ITEM_WIDTH = 215;
    private static final int ITEM_HEIGHT = 266;
    private static final int SPACING_X = 5;
    private static final int SPACING_Y = 19;
    private static final int EXPERT_Y_OFFSET = 121;
    private static final int CARDS_PER_ROW = 3;
    private static final int MAX_CARD_POSITIONS = 9;

    // ========================================================================
    // CONSTANTS - Card Sub-Areas (Offsets and Dimensions)
    // ========================================================================

    private static final int PRICE_OFFSET_X = 54;
    private static final int PRICE_OFFSET_Y = 210;
    private static final int PRICE_WIDTH = 158;
    private static final int PRICE_HEIGHT = 48;
    private static final int QUANTITY_OFFSET_X = 65;
    private static final int QUANTITY_OFFSET_Y = 165;
    private static final int QUANTITY_WIDTH = 100;
    private static final int QUANTITY_HEIGHT = 35;

    // ========================================================================
    // CONSTANTS - Thresholds and Defaults
    // ========================================================================

    private static final int THRESHOLD_SHOP_BUTTON = 90;
    private static final int DISCOUNT_TOLERANCE = 4;
    private static final int DEFAULT_QUANTITY = 1;

    // ========================================================================
    // CONSTANTS - Retry Counts
    // ========================================================================

    private static final int RETRIES_SHOP_BUTTON = 5;
    private static final int RETRIES_SOLD_OUT = 1;
    private static final int RETRIES_ITEM_SEARCH = 1;
    private static final int RETRIES_OCR = 5;

    // ========================================================================
    // Configuration (loaded in loadConfiguration())
    // ========================================================================

    private Integer currentCoins;
    private Integer minCoins;
    private Integer minDiscountPercent;
    private boolean expertUnlocked;

    /**
     * Constructs a new AllianceShopTask.
     * 
     * <p>
     * This task is non-recurring and is triggered by AllianceTechTask
     * when the profile has sufficient alliance coins for shopping.
     *
     * @param profile     the profile this task belongs to
     * @param tpDailyTask the task type enum
     */
    public AllianceShopTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
        this.recurring = false;
    }

    /**
     * Loads task configuration from the profile.
     * 
     * <p>
     * Loads:
     * <ul>
     * <li>Minimum coins threshold (won't spend below this)</li>
     * <li>Minimum discount percentage for purchases</li>
     * <li>Resets Expert unlock flag (will be detected during execution)</li>
     * </ul>
     * 
     * <p>
     * <b>Note:</b> Priority list is loaded separately during execution
     * to ensure fresh data when processing purchases.
     */
    private void loadConfiguration() {
        this.minCoins = profile.getConfig(
                EnumConfigurationKey.ALLIANCE_SHOP_MIN_COINS_INT,
                Integer.class);

        this.minDiscountPercent = profile.getConfig(
                EnumConfigurationKey.ALLIANCE_SHOP_MIN_PERCENTAGE_INT,
                Integer.class);

        // Reset Expert unlock flag - will be detected during navigation
        this.expertUnlocked = false;

        logDebug(String.format(
                "Configuration loaded - Min coins: %d, Min discount: %d%%",
                minCoins, minDiscountPercent));
    }

    /**
     * Main execution method for Alliance Shop task.
     * 
     * <p>
     * <b>Execution Flow:</b>
     * <ol>
     * <li>Load configuration</li>
     * <li>Navigate to shop and read current coins</li>
     * <li>Validate minimum coins threshold</li>
     * <li>Load enabled purchase priorities</li>
     * <li>Detect Expert unlock status</li>
     * <li>Process purchases for each priority</li>
     * <li>Task completes (no reschedule - one-shot task)</li>
     * </ol>
     * 
     * <p>
     * <b>Important:</b> This task does NOT reschedule. It's triggered
     * by AllianceTechTask and runs once per trigger.
     */
    @Override
    protected void execute() {
        loadConfiguration();

        logInfo("Starting Alliance Shop purchase task.");

        if (!navigateToShopAndReadCoins()) {
            logWarning("Failed to navigate to shop or read coins. Task ending.");
            setRecurring(false);
            return;
        }

        if (!validateMinimumCoins()) {
            logInfo("Current coins below minimum threshold. Task ending.");
            setRecurring(false);
            return;
        }

        List<DTOPriorityItem> enabledPriorities = loadEnabledPriorities();
        if (enabledPriorities.isEmpty()) {
            logWarning("No enabled purchase priorities configured. Please enable items in the Alliance Shop settings.");
            setRecurring(false);
            return;
        }

        logPriorities(enabledPriorities);
        detectExpertUnlock();
        processPurchases(enabledPriorities);

        setRecurring(false);
        logInfo("Alliance Shop task completed.");
    }

    // ========================================================================
    // NAVIGATION AND SETUP
    // ========================================================================

    /**
     * Navigates to the Alliance Shop and reads the current coin balance.
     * 
     * <p>
     * <b>Navigation Steps:</b>
     * <ol>
     * <li>Tap Alliance button on home screen</li>
     * <li>Wait for Alliance menu to open</li>
     * <li>Search for and tap Shop button</li>
     * <li>Tap shop details button (top right)</li>
     * <li>Read current coins via OCR</li>
     * <li>Close details view</li>
     * </ol>
     * 
     * <p>
     * Stores the current coins in {@link #currentCoins} for use throughout
     * the purchase process.
     * 
     * @return true if navigation successful and coins read, false otherwise
     */
    private boolean navigateToShopAndReadCoins() {
        logDebug("Navigating to Alliance Shop...");

        tapRandomPoint(ALLIANCE_BUTTON_TOP_LEFT, ALLIANCE_BUTTON_BOTTOM_RIGHT);
        sleepTask(3000); // Wait for Alliance menu to open

        DTOImageSearchResult shopButton = templateSearchHelper.searchTemplate(
                EnumTemplates.ALLIANCE_SHOP_BUTTON,
                SearchConfig.builder()
                        .withThreshold(THRESHOLD_SHOP_BUTTON)
                        .withMaxAttempts(RETRIES_SHOP_BUTTON)
                        .build());

        if (!shopButton.isFound()) {
            logWarning("Could not find Alliance Shop button");
            return false;
        }

        logDebug("Shop button found at: " + shopButton.getPoint());
        tapRandomPoint(shopButton.getPoint(), shopButton.getPoint(), 1, 1000);

        logDebug("Opening shop details to read coins...");
        tapRandomPoint(SHOP_DETAILS_TOP_LEFT, SHOP_DETAILS_BOTTOM_RIGHT, 1, 1000);

        currentCoins = readCurrentCoins();

        if (currentCoins == null) {
            logWarning("Could not read current alliance coins.");
            return false;
        }

        logInfo("Current alliance coins: " + currentCoins + ". Minimum to save: " + minCoins);

        // Exit from coins details view
        tapRandomPoint(CLOSE_TOP_LEFT, CLOSE_BOTTOM_RIGHT, 3, 200);

        return true;
    }

    /**
     * Reads the current alliance coins from the shop details screen using OCR.
     * 
     * <p>
     * Uses the integerHelper with robust retry logic to handle OCR inaccuracies.
     * 
     * @return current coins as Integer, or null if OCR fails
     */
    private Integer readCurrentCoins() {
        return integerHelper.execute(
                COINS_TOP_LEFT,
                COINS_BOTTOM_RIGHT,
                RETRIES_OCR,
                200L, // 200ms delay between retries
                DTOTesseractSettings.builder()
                        .setAllowedChars("0123456789")
                        .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                        .build(),
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));
    }

    /**
     * Validates that current coins exceed the minimum threshold.
     * 
     * <p>
     * If current coins are below the minimum, we skip all purchases
     * to protect the user's coin balance.
     * 
     * @return true if coins are sufficient, false otherwise
     */
    private boolean validateMinimumCoins() {
        if (currentCoins < minCoins) {
            logInfo("Current alliance coins (" + currentCoins +
                    ") are less than the minimum required (" + minCoins + "). Skipping purchases.");
            return false;
        }
        return true;
    }

    /**
     * Loads enabled purchase priorities from the profile configuration.
     * 
     * <p>
     * Priorities are stored as a comma-separated string in the format:
     * "priority:identifier,priority:identifier,..."
     * 
     * <p>
     * Example: "1:VIP_XP_100,2:MARCH_RECALL,3:ADVANCED_TELEPORT"
     * 
     * @return list of enabled priorities sorted by priority (highest first)
     */
    private List<DTOPriorityItem> loadEnabledPriorities() {
        return PriorityItemUtil.getEnabledPriorities(
                profile,
                EnumConfigurationKey.ALLIANCE_SHOP_PRIORITIES_STRING);
    }

    /**
     * Logs the loaded purchase priorities for visibility.
     * 
     * @param priorities the list of enabled priorities to log
     */
    private void logPriorities(List<DTOPriorityItem> priorities) {
        logInfo("Found " + priorities.size() + " enabled purchase priorities:");
        for (DTOPriorityItem priority : priorities) {
            logInfo(" Priority " + priority.getPriority() + ": " +
                    priority.getName() + " (ID: " + priority.getIdentifier() + ")");
        }
    }

    /**
     * Detects if Expert is unlocked in the Alliance Shop.
     * 
     * <p>
     * When Expert is unlocked, an additional icon appears at the top
     * of the shop, which pushes all item cards down by 121 pixels.
     * 
     * <p>
     * Sets {@link #expertUnlocked} flag which is used in coordinate
     * calculations throughout the task.
     */
    private void detectExpertUnlock() {
        DTOImageSearchResult expertIcon = templateSearchHelper.searchTemplate(
                EnumTemplates.ALLIANCE_SHOP_EXPERT_ICON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(3)
                        .build());

        if (expertIcon.isFound()) {
            expertUnlocked = true;
            logInfo("Expert unlocked detected in Alliance Shop. Adjusting item coordinates accordingly.");
        } else {
            logDebug("Expert not unlocked. Using standard coordinates.");
        }
    }

    // ========================================================================
    // PURCHASE PROCESSING - MAIN ORCHESTRATOR
    // ========================================================================

    /**
     * Processes purchases for all enabled priorities.
     * 
     * <p>
     * <b>Processing Flow:</b>
     * For each priority (highest to lowest):
     * <ol>
     * <li>Check if minimum coins threshold reached (stop if yes)</li>
     * <li>Find shop item by identifier</li>
     * <li>Process item across all applicable tabs (Weekly/Daily/Both)</li>
     * <li>Handle purchase outcomes (purchased, sold out, can't afford, etc.)</li>
     * <li>Stop if can't afford any more items</li>
     * </ol>
     * 
     * <p>
     * <b>Important:</b> Items are exhausted across all applicable tabs
     * before moving to the next priority. This ensures we maximize purchases
     * of higher-priority items before spending coins on lower-priority ones.
     * 
     * @param enabledPriorities list of priorities sorted highest to lowest
     */
    private void processPurchases(List<DTOPriorityItem> enabledPriorities) {
        for (DTOPriorityItem priority : enabledPriorities) {
            if (hasReachedMinimumCoins()) {
                logInfo("Reached minimum coins threshold. Stopping purchases.");
                return;
            }

            logInfo("Processing priority " + priority.getPriority() + ": " + priority.getName());

            AllianceShopItem shopItem = findShopItemByIdentifier(priority.getIdentifier());
            if (shopItem == null) {
                logWarning("Could not find shop item for identifier: " + priority.getIdentifier());
                continue;
            }

            if (!processPriorityItem(shopItem)) {
                // Can't afford anything more - stop all purchases
                return;
            }

            logInfo("Finished processing all tabs for: " + shopItem.getDisplayName());
        }
    }

    /**
     * Processes a single priority item across all applicable tabs.
     * 
     * <p>
     * Some items appear in multiple tabs (Weekly/Daily). This method
     * ensures we exhaust the item in all tabs before moving to next priority.
     * 
     * @param shopItem the item to process
     * @return true if processing should continue, false if we can't afford anything
     *         more
     */
    private boolean processPriorityItem(AllianceShopItem shopItem) {
        List<AllianceShopItem.ShopTab> tabsToCheck = getTabsForItem(shopItem);

        for (AllianceShopItem.ShopTab tab : tabsToCheck) {
            if (hasReachedMinimumCoins()) {
                logInfo("Reached minimum coins threshold during multi-tab processing.");
                return true; // Continue to next priority
            }

            logInfo("Checking " + shopItem.getDisplayName() + " in " + tab + " tab");
            switchToTab(tab);

            PurchaseOutcome outcome = processItemInTab(shopItem, tab);

            if (!handlePurchaseOutcome(outcome, shopItem, tab)) {
                // Can't afford - stop all purchases
                return false;
            }
        }

        return true;
    }

    /**
     * Processes a single item in a specific tab.
     * 
     * <p>
     * <b>Processing Steps:</b>
     * <ol>
     * <li>Find item card position (via template search or fallback)</li>
     * <li>Check if sold out</li>
     * <li>Validate item price and discount</li>
     * <li>Read available quantity</li>
     * <li>Calculate affordable quantity</li>
     * <li>Execute purchase</li>
     * </ol>
     * 
     * @param shopItem   the item to purchase
     * @param currentTab the tab we're currently in
     * @return PurchaseOutcome describing the result
     */
    private PurchaseOutcome processItemInTab(AllianceShopItem shopItem, AllianceShopItem.ShopTab currentTab) {
        Integer cardIndex = getCardIndex(shopItem);
        DTOArea cardCoords = cardIndex != null ? getItemArea(cardIndex) : null;

        if (cardCoords == null) {
            logWarning("Could not determine card coordinates for item: " + shopItem.getDisplayName());
            return PurchaseOutcome.ERROR;
        }

        if (isSoldOut(cardCoords, shopItem)) {
            return PurchaseOutcome.SOLD_OUT;
        }

        PriceValidationResult priceValidation = validateItemPrice(cardIndex, shopItem);
        if (!priceValidation.isValid()) {
            return priceValidation.getOutcome();
        }

        int itemPrice = priceValidation.getPrice();
        int quantity = calculatePurchaseQuantity(cardIndex, shopItem, itemPrice);

        if (quantity <= 0) {
            logInfo("Cannot afford any more of item: " + shopItem.getDisplayName());
            return PurchaseOutcome.CANT_AFFORD;
        }

        executePurchase(shopItem, itemPrice, priceValidation.getAvailableQuantity(), quantity, cardIndex);
        return PurchaseOutcome.PURCHASED;
    }

    /**
     * Handles the outcome of a purchase attempt.
     * 
     * <p>
     * <b>Outcome Handling:</b>
     * <ul>
     * <li>PURCHASED: Continue to next tab/priority</li>
     * <li>SOLD_OUT: Continue to next tab/priority</li>
     * <li>INSUFFICIENT_DISCOUNT: Continue to next tab/priority</li>
     * <li>CANT_AFFORD: Stop all purchases (return false)</li>
     * <li>ERROR: Continue to next tab/priority</li>
     * </ul>
     * 
     * @param outcome  the purchase outcome
     * @param shopItem the item that was processed
     * @param tab      the tab that was processed
     * @return true to continue processing, false to stop all purchases
     */
    private boolean handlePurchaseOutcome(
            PurchaseOutcome outcome,
            AllianceShopItem shopItem,
            AllianceShopItem.ShopTab tab) {

        switch (outcome) {
            case PURCHASED:
                logDebug("Successfully purchased item");
                break;
            case CANT_AFFORD:
                logInfo("Cannot afford item " + shopItem.getDisplayName() +
                        ". Stopping further purchases.");
                return false; // Stop all purchases
            case SOLD_OUT:
                logInfo("Item " + shopItem.getDisplayName() + " is sold out in " + tab +
                        " tab. Continuing with next tab/priority.");
                break;
            case INSUFFICIENT_DISCOUNT:
                logInfo("Item " + shopItem.getDisplayName() +
                        " does not meet discount threshold in " + tab +
                        " tab. Continuing.");
                break;
            case ERROR:
            default:
                logWarning("Unexpected error while attempting to purchase " +
                        shopItem.getDisplayName() + " in " + tab +
                        " tab. Continuing.");
                break;
        }

        return true; // Continue processing
    }

    /**
     * Checks if we've reached the minimum coins threshold.
     * 
     * @return true if current coins are at or below minimum, false otherwise
     */
    private boolean hasReachedMinimumCoins() {
        return currentCoins < minCoins;
    }

    // ========================================================================
    // PURCHASE VALIDATION
    // ========================================================================

    /**
     * Checks if an item is sold out by searching for the sold-out indicator.
     * 
     * <p>
     * The sold-out indicator is a gray overlay on the item card.
     * 
     * @param cardCoords the coordinates of the item card
     * @param shopItem   the item being checked
     * @return true if sold out, false if available
     */
    private boolean isSoldOut(DTOArea cardCoords, AllianceShopItem shopItem) {
        DTOImageSearchResult soldOutResult = templateSearchHelper.searchTemplate(
                EnumTemplates.ALLIANCE_SHOP_SOLD_OUT,
                SearchConfig.builder()
                        .withMaxAttempts(RETRIES_SOLD_OUT)
                        .withDelay(100L)
                        .withArea(cardCoords)
                        .build());

        if (soldOutResult.isFound()) {
            logInfo("Item " + shopItem.getDisplayName() + " is sold out");
            return true;
        }
        return false;
    }

    /**
     * Validates item price and discount threshold.
     * 
     * <p>
     * <b>Validation Steps:</b>
     * <ol>
     * <li>Read item price via OCR</li>
     * <li>Compare against base price to calculate discount</li>
     * <li>Check if discount meets minimum threshold (with tolerance)</li>
     * </ol>
     * 
     * <p>
     * <b>Discount Calculation:</b>
     * Actual threshold = configured minimum - {@link #DISCOUNT_TOLERANCE}
     * 
     * <p>
     * This tolerance accounts for OCR inaccuracies and minor price variations.
     * 
     * @param cardIndex the card position (1-9)
     * @param shopItem  the item being validated
     * @return PriceValidationResult with validation outcome and price
     */
    private PriceValidationResult validateItemPrice(int cardIndex, AllianceShopItem shopItem) {
        Integer itemPrice = readItemPrice(cardIndex, shopItem);

        if (itemPrice == null) {
            return new PriceValidationResult(PurchaseOutcome.ERROR, 0, 0);
        }

        if (!meetsDiscountThreshold(shopItem, itemPrice)) {
            return new PriceValidationResult(PurchaseOutcome.INSUFFICIENT_DISCOUNT, itemPrice, 0);
        }

        Integer availableQty = readAvailableQuantity(cardIndex, shopItem);
        if (availableQty == null) {
            availableQty = DEFAULT_QUANTITY;
        }

        return new PriceValidationResult(PurchaseOutcome.PURCHASED, itemPrice, availableQty);
    }

    /**
     * Reads the item price from the specified card using OCR.
     * 
     * @param cardIndex the card position (1-9)
     * @param shopItem  the item being read
     * @return price as Integer, or null if OCR fails
     */
    private Integer readItemPrice(int cardIndex, AllianceShopItem shopItem) {
        DTOArea priceArea = getPriceArea(cardIndex);

        Integer itemPrice = integerHelper.execute(
                priceArea.topLeft(),
                priceArea.bottomRight(),
                RETRIES_OCR,
                1000L, // 1000ms delay for price reading
                DTOTesseractSettings.builder()
                        .setAllowedChars("0123456789")
                        .build(),
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));

        if (itemPrice == null) {
            logWarning("Could not read price for item: " + shopItem.getDisplayName());
        }

        return itemPrice;
    }

    /**
     * Checks if the item price meets the minimum discount threshold.
     * 
     * <p>
     * <b>Discount Calculation:</b>
     * 
     * <pre>
     * discount% = ((basePrice - currentPrice) / basePrice) × 100
     * threshold = minDiscountPercent - DISCOUNT_TOLERANCE
     * </pre>
     * 
     * <p>
     * <b>Why Tolerance?</b>
     * The {@link #DISCOUNT_TOLERANCE} (4%) is subtracted from the configured
     * minimum to account for OCR inaccuracies and minor price variations.
     * 
     * @param shopItem  the item being checked
     * @param itemPrice the current price read from screen
     * @return true if discount meets threshold, false otherwise
     */
    private boolean meetsDiscountThreshold(AllianceShopItem shopItem, int itemPrice) {
        int basePrice = shopItem.getBasePrice();
        if (basePrice <= itemPrice) {
            // No discount or price increase - skip item
            logInfo("Item " + shopItem.getDisplayName() +
                    " has no discount (base: " + basePrice + ", current: " + itemPrice + ")");
            return false;
        }

        double discountPercent = ((basePrice - itemPrice) / (double) basePrice) * 100;
        double minDiscountThreshold = minDiscountPercent - DISCOUNT_TOLERANCE;

        logInfo("Item base price: " + basePrice +
                ", current price: " + itemPrice +
                ", discount: " + String.format("%.2f", discountPercent) + "%");

        if (discountPercent < minDiscountThreshold) {
            logInfo("Discount insufficient (required: " + minDiscountThreshold + "%), skipping purchase");
            return false;
        }

        return true;
    }

    /**
     * Reads the available quantity for an item using OCR.
     * 
     * <p>
     * The quantity is displayed in white text over the item icon.
     * If OCR fails, returns null (caller should use {@link #DEFAULT_QUANTITY}).
     * 
     * @param cardIndex the card position (1-9)
     * @param shopItem  the item being read
     * @return available quantity as Integer, or null if OCR fails
     */
    private Integer readAvailableQuantity(int cardIndex, AllianceShopItem shopItem) {
        DTOArea quantityArea = getQuantityArea(cardIndex);

        Integer availableQuantity = integerHelper.execute(
                quantityArea.topLeft(),
                quantityArea.bottomRight(),
                RETRIES_OCR,
                1000L, // 1000ms delay for quantity reading
                DTOTesseractSettings.builder()
                        .setAllowedChars("0123456789")
                        .setTextColor(Color.white)
                        .setRemoveBackground(true)
                        .build(),
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));

        if (availableQuantity == null) {
            logWarning("Could not read available quantity for item: " +
                    shopItem.getDisplayName() + ". Assuming quantity of " + DEFAULT_QUANTITY + ".");
        }

        return availableQuantity;
    }

    /**
     * Calculates the maximum quantity that can be purchased.
     * 
     * <p>
     * <b>Calculation:</b>
     * 
     * <pre>
     * affordable = (currentCoins - minCoins) / itemPrice
     * quantity = min(affordable, availableQuantity)
     * </pre>
     * 
     * <p>
     * This ensures we:
     * <ul>
     * <li>Don't spend below minimum coins threshold</li>
     * <li>Don't exceed available stock</li>
     * </ul>
     * 
     * @param cardIndex the card position (1-9) - not used currently but kept for
     *                  future enhancements
     * @param shopItem  the item being purchased - not used currently but kept for
     *                  future enhancements
     * @param itemPrice the price per item
     * @return maximum quantity that can be purchased (0 if can't afford any)
     */
    private int calculatePurchaseQuantity(int cardIndex, AllianceShopItem shopItem, int itemPrice) {
        Integer availableQuantity = readAvailableQuantity(cardIndex, shopItem);
        if (availableQuantity == null) {
            availableQuantity = DEFAULT_QUANTITY;
        }

        return computeBuyQty(currentCoins, minCoins, itemPrice, availableQuantity);
    }

    /**
     * Computes the maximum quantity that can be purchased while respecting
     * the minimum coins threshold and available stock.
     * 
     * <p>
     * This is the core calculation method that ensures we never
     * spend below the minimum coins threshold.
     * 
     * @param currentCoins      current coins available
     * @param minCoins          minimum coins to keep
     * @param itemPrice         price per item
     * @param availableQuantity stock available
     * @return maximum quantity to purchase (0 if can't afford any)
     */
    private int computeBuyQty(int currentCoins, int minCoins, int itemPrice, int availableQuantity) {
        if (itemPrice <= 0) {
            return 0;
        }

        int affordable = (currentCoins - minCoins) / itemPrice;
        return Math.max(0, Math.min(availableQuantity, affordable));
    }

    // ========================================================================
    // PURCHASE EXECUTION
    // ========================================================================

    /**
     * Executes the purchase of an item.
     * 
     * <p>
     * <b>Purchase Flow:</b>
     * <ol>
     * <li>Tap item card to open purchase dialog</li>
     * <li>Select quantity:
     * <ul>
     * <li>If buying all available: Tap MAX button</li>
     * <li>Otherwise: Tap + button (qty-1) times</li>
     * </ul>
     * </li>
     * <li>Tap confirm button</li>
     * <li>Update remaining coins</li>
     * <li>Close purchase dialog</li>
     * </ol>
     * 
     * @param shopItem          the item being purchased
     * @param itemPrice         the price per item
     * @param availableQuantity the total available quantity
     * @param qty               the quantity to purchase
     * @param cardIndex         the card position (1-9)
     */
    private void executePurchase(
            AllianceShopItem shopItem,
            int itemPrice,
            int availableQuantity,
            int qty,
            int cardIndex) {

        logInfo("Buying " + qty + " of " + shopItem.getDisplayName() +
                " (Price: " + itemPrice + ", Available: " + availableQuantity +
                ", Current Coins: " + currentCoins + ")");

        openPurchaseDialog(cardIndex);
        selectQuantity(qty, availableQuantity);
        confirmPurchase();
        updateRemainingCoins(qty, itemPrice);
        closePurchaseDialog();

        logInfo("Successfully purchased " + qty + " of " + shopItem.getDisplayName() +
                ". Remaining coins: " + currentCoins);
    }

    /**
     * Opens the purchase dialog by tapping the item's price area.
     * 
     * @param cardIndex the card position (1-9)
     */
    private void openPurchaseDialog(int cardIndex) {
        DTOArea priceArea = getPriceArea(cardIndex);
        tapRandomPoint(priceArea.topLeft(), priceArea.bottomRight(), 1, 1500);
    }

    /**
     * Selects the desired quantity in the purchase dialog.
     * 
     * <p>
     * If buying all available, taps MAX button for efficiency.
     * Otherwise, taps + button the appropriate number of times.
     * 
     * @param qty               quantity to purchase
     * @param availableQuantity total available quantity
     */
    private void selectQuantity(int qty, int availableQuantity) {
        if (qty == availableQuantity) {
            // Tap MAX button - more efficient than multiple + taps
            tapRandomPoint(MAX_BUTTON_TOP_LEFT, MAX_BUTTON_BOTTOM_RIGHT, 1, 300);
        } else {
            // Tap + button (qty-1) times (quantity starts at 1)
            tapRandomPoint(PLUS_BUTTON_TOP_LEFT, PLUS_BUTTON_BOTTOM_RIGHT, qty - 1, 300);
        }
    }

    /**
     * Confirms the purchase by tapping the confirm button.
     */
    private void confirmPurchase() {
        tapRandomPoint(CONFIRM_BUTTON_TOP_LEFT, CONFIRM_BUTTON_BOTTOM_RIGHT, 1, 1000);
    }

    /**
     * Updates the remaining coins after a purchase.
     * 
     * <p>
     * This tracks coins locally to avoid repeated OCR reads.
     * 
     * @param qty       quantity purchased
     * @param itemPrice price per item
     */
    private void updateRemainingCoins(int qty, int itemPrice) {
        currentCoins -= qty * itemPrice;
    }

    /**
     * Closes the purchase dialog by tapping the close button.
     */
    private void closePurchaseDialog() {
        tapRandomPoint(CLOSE_TOP_LEFT, CLOSE_BOTTOM_RIGHT, 3, 200);
    }

    // ========================================================================
    // TAB MANAGEMENT
    // ========================================================================

    /**
     * Determines which tabs need to be checked for a given item.
     * 
     * <p>
     * Some items appear in both Weekly and Daily tabs (e.g., VIP XP).
     * Others only appear in one tab.
     * 
     * @param item the item to check
     * @return list of tabs to search (Weekly, Daily, or both)
     */
    private List<AllianceShopItem.ShopTab> getTabsForItem(AllianceShopItem item) {
        if (item.getTab() == AllianceShopItem.ShopTab.BOTH) {
            return List.of(AllianceShopItem.ShopTab.WEEKLY, AllianceShopItem.ShopTab.DAILY);
        }
        return List.of(item.getTab());
    }

    /**
     * Switches to the specified shop tab.
     * 
     * <p>
     * Uses modern switch expression for cleaner code.
     * Waits for tab content to load after switching.
     * 
     * @param tab the tab to switch to (WEEKLY or DAILY)
     */
    private void switchToTab(AllianceShopItem.ShopTab tab) {
        switch (tab) {
            case WEEKLY -> tapRandomPoint(WEEKLY_TAB_TOP_LEFT, WEEKLY_TAB_BOTTOM_RIGHT, 3, 200);
            case DAILY -> tapRandomPoint(DAILY_TAB_TOP_LEFT, DAILY_TAB_BOTTOM_RIGHT, 3, 200);
            case BOTH -> {
                // Items in BOTH tabs are available on both, no need to switch
                logDebug("Item available in both tabs");
            }
        }
        sleepTask(1500); // Wait for tab content to load
    }

    // ========================================================================
    // ITEM LOCATION AND SEARCH
    // ========================================================================

    /**
     * Finds a shop item enum by its string identifier.
     * 
     * @param identifier the identifier string (e.g., "VIP_XP_100")
     * @return AllianceShopItem enum, or null if not found
     */
    private AllianceShopItem findShopItemByIdentifier(String identifier) {
        try {
            return AllianceShopItem.valueOf(identifier);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Gets the card index (1-9) for an item.
     * 
     * <p>
     * <b>Search Strategy:</b>
     * <ol>
     * <li>For items with templates: Search all 9 positions using template
     * matching</li>
     * <li>For items without templates: Use fallback fixed positions</li>
     * </ol>
     * 
     * <p>
     * <b>Why Search?</b>
     * Items can change positions between tabs (Weekly vs Daily) and when
     * new items are added. Template search ensures we always find the
     * correct position dynamically.
     * 
     * <p>
     * <b>Fallback Positions:</b>
     * Weekly-only items without templates use assumed fixed positions:
     * <ul>
     * <li>Mythic Hero Shards: Position 1</li>
     * <li>Pet Food: Position 2</li>
     * <li>Pet Chest: Position 3</li>
     * <li>Transfer Pass: Position 4</li>
     * </ul>
     * 
     * @param item the item to find
     * @return card index (1-9), or null if not found
     */
    private Integer getCardIndex(AllianceShopItem item) {
        EnumTemplates template = getItemTemplate(item);

        if (template != null) {
            Integer foundIndex = searchForItemCard(template);
            if (foundIndex != null) {
                return foundIndex;
            }
        }

        // Fallback to assumed fixed positions (primarily for weekly-only items)
        return switch (item) {
            case MYTHIC_HERO_SHARDS -> 1;
            case PET_FOOD -> 2;
            case PET_CHEST -> 3;
            case TRANSFER_PASS -> 4;
            default -> null;
        };
    }

    /**
     * Maps shop items to their corresponding search templates.
     * 
     * <p>
     * Not all items have templates (only dynamic items that can move positions).
     * 
     * @param item the shop item
     * @return corresponding EnumTemplates, or null if no template exists
     */
    private EnumTemplates getItemTemplate(AllianceShopItem item) {
        return switch (item) {
            case VIP_XP_100 -> ALLIANCE_SHOP_100_VIP_XP;
            case VIP_XP_10 -> ALLIANCE_SHOP_10_VIP_XP;
            case MARCH_RECALL -> ALLIANCE_SHOP_RECALL_MARCH;
            case ADVANCED_TELEPORT -> ALLIANCE_SHOP_ADVANCED_TELEPORT;
            case TERRITORY_TELEPORT -> ALLIANCE_SHOP_TERRITORY_TELEPORT;
            default -> null;
        };
    }

    /**
     * Searches for an item across all 9 card positions using template matching.
     * 
     * <p>
     * Iterates through positions 1-9 and searches each card's area
     * for the specified template.
     * 
     * @param template the template to search for
     * @return card index (1-9) if found, null otherwise
     */
    private Integer searchForItemCard(EnumTemplates template) {
        for (int i = 1; i <= MAX_CARD_POSITIONS; i++) {
            DTOArea area = getItemArea(i);

            DTOImageSearchResult searchResult = templateSearchHelper.searchTemplate(
                    template,
                    SearchConfig.builder()
                            .withMaxAttempts(RETRIES_ITEM_SEARCH)
                            .withDelay(200L)
                            .withArea(area)
                            .build());

            if (searchResult.isFound()) {
                logDebug("Found " + template.name() + " at card position " + i);
                return i;
            }
        }

        logDebug("Template " + template.name() + " not found in any card position");
        return null;
    }

    // ========================================================================
    // COORDINATE CALCULATION
    // ========================================================================

    /**
     * Gets the full area for an item card.
     * 
     * <p>
     * The item card includes the icon, name, price, and quantity.
     * 
     * @param cardNumber card position (1-9)
     * @return DTOArea covering the entire card
     */
    private DTOArea getItemArea(int cardNumber) {
        return getCardArea(cardNumber, 0, 0, ITEM_WIDTH, ITEM_HEIGHT);
    }

    /**
     * Gets the area where the price is displayed on a card.
     * 
     * <p>
     * The price is shown at the bottom of the card in white text.
     * 
     * @param cardNumber card position (1-9)
     * @return DTOArea for the price region
     */
    private DTOArea getPriceArea(int cardNumber) {
        return getCardArea(cardNumber, PRICE_OFFSET_X, PRICE_OFFSET_Y, PRICE_WIDTH, PRICE_HEIGHT);
    }

    /**
     * Gets the area where the quantity is displayed on a card.
     * 
     * <p>
     * The quantity is shown as white text overlaid on the item icon.
     * 
     * @param cardNumber card position (1-9)
     * @return DTOArea for the quantity region
     */
    private DTOArea getQuantityArea(int cardNumber) {
        return getCardArea(cardNumber, QUANTITY_OFFSET_X, QUANTITY_OFFSET_Y, QUANTITY_WIDTH, QUANTITY_HEIGHT);
    }

    /**
     * Consolidated method to calculate card-based areas with offset and dimensions.
     * 
     * <p>
     * <b>Grid Layout:</b>
     * Cards are arranged in a 3x3 grid with fixed spacing.
     * When Expert is unlocked, all cards shift down by {@link #EXPERT_Y_OFFSET}
     * pixels.
     * 
     * <p>
     * <b>Coordinate Calculation:</b>
     * 
     * <pre>
     * row = (cardNumber - 1) / 3
     * col = (cardNumber - 1) % 3
     * 
     * cardX = GRID_START_X + col × (ITEM_WIDTH + SPACING_X)
     * cardY = GRID_START_Y + expertOffset + row × (ITEM_HEIGHT + SPACING_Y)
     * 
     * x1 = cardX + offsetX
     * y1 = cardY + offsetY
     * x2 = x1 + width
     * y2 = y1 + height
     * </pre>
     * 
     * <p>
     * <b>Expert Unlock Effect:</b>
     * When Expert is unlocked, {@link #expertUnlocked} is true, and
     * {@link #EXPERT_Y_OFFSET} (121px) is added to the Y-coordinate.
     * 
     * @param cardNumber card position (1-9)
     * @param offsetX    X offset from card origin
     * @param offsetY    Y offset from card origin
     * @param width      width of the region
     * @param height     height of the region
     * @return DTOArea for the specified region
     * @throws IllegalArgumentException if cardNumber is not between 1 and 9
     */
    private DTOArea getCardArea(int cardNumber, int offsetX, int offsetY, int width, int height) {
        if (cardNumber < 1 || cardNumber > MAX_CARD_POSITIONS) {
            throw new IllegalArgumentException("Card number must be between 1 and " + MAX_CARD_POSITIONS);
        }

        int expertOffset = expertUnlocked ? EXPERT_Y_OFFSET : 0;

        int row = (cardNumber - 1) / CARDS_PER_ROW;
        int col = (cardNumber - 1) % CARDS_PER_ROW;

        int cardX = GRID_START_X + col * (ITEM_WIDTH + SPACING_X);
        int cardY = GRID_START_Y + expertOffset + row * (ITEM_HEIGHT + SPACING_Y);

        int x1 = cardX + offsetX;
        int y1 = cardY + offsetY;
        int x2 = x1 + width;
        int y2 = y1 + height;

        return new DTOArea(new DTOPoint(x1, y1), new DTOPoint(x2, y2));
    }

    // ========================================================================
    // OVERRIDES
    // ========================================================================

    /**
     * This task does NOT provide daily mission progress.
     * 
     * <p>
     * Alliance Shop purchases don't count toward daily mission objectives.
     * 
     * @return false
     */
    @Override
    public boolean provideDailyMissionProgress() {
        return false;
    }

    /**
     * This task does NOT consume stamina.
     * 
     * <p>
     * Alliance Shop purchases use alliance coins, not stamina.
     * 
     * @return false
     */
    @Override
    protected boolean consumesStamina() {
        return false;
    }

    // ========================================================================
    // DATA CLASSES
    // ========================================================================

    /**
     * Represents the possible outcomes of a purchase attempt.
     * 
     * <p>
     * <b>Outcomes:</b>
     * <ul>
     * <li>PURCHASED - Item successfully purchased</li>
     * <li>SOLD_OUT - Item is sold out in current tab</li>
     * <li>INSUFFICIENT_DISCOUNT - Discount doesn't meet minimum threshold</li>
     * <li>CANT_AFFORD - Not enough coins (respecting minimum threshold)</li>
     * <li>ERROR - Unexpected error during processing</li>
     * </ul>
     */
    private enum PurchaseOutcome {
        /** Item was successfully purchased */
        PURCHASED,

        /** Item is sold out in the current tab */
        SOLD_OUT,

        /** Item's discount doesn't meet the minimum threshold */
        INSUFFICIENT_DISCOUNT,

        /** Cannot afford the item while respecting minimum coins */
        CANT_AFFORD,

        /** An error occurred during processing */
        ERROR
    }

    /**
     * Represents the result of price validation.
     * 
     * <p>
     * Contains:
     * <ul>
     * <li>Validation outcome (valid/invalid)</li>
     * <li>Item price (if successfully read)</li>
     * <li>Available quantity (if successfully read)</li>
     * </ul>
     * 
     * <p>
     * This consolidates price reading, discount validation, and quantity
     * reading into a single result object for cleaner code flow.
     */
    private static class PriceValidationResult {
        private final PurchaseOutcome outcome;
        private final int price;
        private final int availableQuantity;

        /**
         * Constructs a price validation result.
         * 
         * @param outcome           the validation outcome
         * @param price             the item price (0 if not read)
         * @param availableQuantity the available quantity (0 if not read)
         */
        PriceValidationResult(PurchaseOutcome outcome, int price, int availableQuantity) {
            this.outcome = outcome;
            this.price = price;
            this.availableQuantity = availableQuantity;
        }

        /**
         * Checks if validation was successful.
         * 
         * @return true if outcome is PURCHASED, false otherwise
         */
        boolean isValid() {
            return outcome == PurchaseOutcome.PURCHASED;
        }

        /**
         * Gets the validation outcome.
         * 
         * @return the PurchaseOutcome
         */
        PurchaseOutcome getOutcome() {
            return outcome;
        }

        /**
         * Gets the validated price.
         * 
         * @return the price (0 if validation failed)
         */
        int getPrice() {
            return price;
        }

        /**
         * Gets the available quantity.
         * 
         * @return the available quantity (0 if not read)
         */
        int getAvailableQuantity() {
            return availableQuantity;
        }
    }
}