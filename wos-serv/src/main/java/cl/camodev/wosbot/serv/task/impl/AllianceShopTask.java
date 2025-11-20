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

import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;

import static cl.camodev.wosbot.console.enumerable.EnumTemplates.*;

/**
 * Task to purchase items from the Alliance Shop based on configured priorities.
 * This task is automatically triggered when Alliance Tech task detects
 * that the user has enough alliance coins (based on minimum threshold).
 *
 * Handles multi-tab shopping (Weekly/Daily) and ensures items are exhausted
 * across all applicable tabs before moving to the next priority.
 */
public class AllianceShopTask extends DelayedTask {

    private Boolean expertUnlocked = false;
    private Integer currentCoins;
    private Integer minCoins;
    private Integer minDiscountPercent;

    // Resultado especializado para los intentos de compra
    private enum PurchaseOutcome {
        PURCHASED,
        SOLD_OUT,
        INSUFFICIENT_DISCOUNT,
        CANT_AFFORD,
        ERROR
    }

    public AllianceShopTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
        this.recurring = false;
    }

    @Override
    protected void execute() {
        logInfo("Starting Alliance Shop purchase task.");

        if (!navigateToShopAndReadCoins()) {
            setRecurring(false);
            return;
        }

        if (!validateMinimumCoins()) {
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

    // ==================== Navigation & Setup ====================

    private boolean navigateToShopAndReadCoins() {
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(493, 1187), new DTOPoint(561, 1240));
        sleepTask(3000);

        DTOImageSearchResult shopButton = searchTemplateWithRetries(EnumTemplates.ALLIANCE_SHOP_BUTTON, 90, 5);
        if (!shopButton.isFound()) {
            logWarning("Could not find Alliance Shop button");
            return false;
        }

        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, shopButton.getPoint(), shopButton.getPoint(), 1, 1000);
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(580, 30), new DTOPoint(670, 50), 1, 1000);

        currentCoins = integerHelper.execute(
                new DTOPoint(272, 257),
                new DTOPoint(443, 285),
                5,
                200L,
                DTOTesseractSettings.builder()
                        .setAllowedChars("0123456789")
                        .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                        .build(),
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*"))
        );

        if (currentCoins == null) {
            logWarning("Could not read current alliance coins.");
            return false;
        }

        minCoins = profile.getConfig(EnumConfigurationKey.ALLIANCE_SHOP_MIN_COINS_INT, Integer.class);
        minDiscountPercent = profile.getConfig(EnumConfigurationKey.ALLIANCE_SHOP_MIN_PERCENTAGE_INT, Integer.class);
        logInfo("Current alliance coins: " + currentCoins + ". Minimum to save: " + minCoins);

        // Exit from coins details view
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(270, 30), new DTOPoint(280, 80), 3, 200);
        return true;
    }

    private boolean validateMinimumCoins() {
        if (currentCoins < minCoins) {
            logInfo("Current alliance coins (" + currentCoins + ") are less than the minimum required (" + minCoins + "). Skipping purchases.");
            return false;
        }
        return true;
    }

    private List<DTOPriorityItem> loadEnabledPriorities() {
        return PriorityItemUtil.getEnabledPriorities(
                profile,
                EnumConfigurationKey.ALLIANCE_SHOP_PRIORITIES_STRING
        );
    }

    private void logPriorities(List<DTOPriorityItem> priorities) {
        logInfo("Found " + priorities.size() + " enabled purchase priorities:");
        for (DTOPriorityItem priority : priorities) {
            logInfo(" Priority " + priority.getPriority() + ": " + priority.getName() + " (ID: " + priority.getIdentifier() + ")");
        }
    }

    private void detectExpertUnlock() {
        DTOImageSearchResult expertIcon = searchTemplateWithRetries(EnumTemplates.ALLIANCE_SHOP_EXPERT_ICON, 90, 3);
        if (expertIcon.isFound()) {
            expertUnlocked = true;
            logInfo("Expert unlocked detected in Alliance Shop. Adjusting item coordinates accordingly.");
        }
    }

    // ==================== Purchase Logic ====================

    private void processPurchases(List<DTOPriorityItem> enabledPriorities) {
        for (DTOPriorityItem priority : enabledPriorities) {
            if (currentCoins < minCoins) {
                logInfo("Reached minimum coins threshold. Stopping purchases.");
                return;
            }

            logInfo("Processing priority " + priority.getPriority() + ": " + priority.getName());

            AllianceShopItem shopItem = findShopItemByIdentifier(priority.getIdentifier());
            if (shopItem == null) {
                logWarning("Could not find shop item for identifier: " + priority.getIdentifier());
                continue;
            }

            // Process item across all applicable tabs
            List<AllianceShopItem.ShopTab> tabsToCheck = getTabsForItem(shopItem);
            boolean stopAll = false;
            for (AllianceShopItem.ShopTab tab : tabsToCheck) {
                if (currentCoins < minCoins) {
                    logInfo("Reached minimum coins threshold during multi-tab processing.");
                    stopAll = true;
                    break;
                }

                logInfo("Checking " + shopItem.getDisplayName() + " in " + tab + " tab");
                switchToTab(tab);

                PurchaseOutcome outcome = attemptPurchase(shopItem, tab);

                switch (outcome) {
                    case PURCHASED:
                        break;
                    case CANT_AFFORD:
                        logInfo("Cannot afford item " + shopItem.getDisplayName() + ". Stopping further purchases.");
                        stopAll = true;
                        break;
                    case SOLD_OUT:
                        logInfo("Item " + shopItem.getDisplayName() + " is sold out in " + tab + " tab. Continuing with next tab/priority.");
                        break;
                    case INSUFFICIENT_DISCOUNT:
                        logInfo("Item " + shopItem.getDisplayName() + " does not meet discount threshold in " + tab + " tab. Continuing.");
                        break;
                    case ERROR:
                    default:
                        logWarning("Unexpected error while attempting to purchase " + shopItem.getDisplayName() + " in " + tab + " tab. Continuing.");
                        break;
                }

                if (stopAll) {
                    break;
                }
            }

            if (stopAll) {
                return;
            }

            logInfo("Finished processing all tabs for: " + shopItem.getDisplayName());
        }
    }

    /**
     * Attempts to purchase an item from the current tab.
     * Handles sold-out detection, discount validation, quantity calculation, and purchase execution.
     *
     * @return PurchaseOutcome describing the result of the attempt
     */
    private PurchaseOutcome attemptPurchase(AllianceShopItem shopItem, AllianceShopItem.ShopTab currentTab) {
        Integer cardIndex = getCardIndex(shopItem);
        DTOArea cardCoords = cardIndex != null ? getItemArea(cardIndex) : null;

        if (cardCoords == null) {
            logWarning("Could not determine card coordinates for item: " + shopItem.getDisplayName());
            return PurchaseOutcome.ERROR;
        }

        // Check if sold out
        if (isSoldOut(cardCoords, shopItem)) {
            return PurchaseOutcome.SOLD_OUT;
        }

        // Read and validate price
        Integer itemPrice = readItemPrice(cardIndex, shopItem);
        if (itemPrice == null) {
            return PurchaseOutcome.ERROR;
        }

        // Validate discount threshold
        if (!meetsDiscountThreshold(shopItem, itemPrice)) {
            return PurchaseOutcome.INSUFFICIENT_DISCOUNT;
        }

        // Read available quantity
        Integer availableQuantity = readAvailableQuantity(cardIndex, shopItem);
        if (availableQuantity == null) {
            availableQuantity = 1;
        }

        // Calculate and execute purchase
        int qty = computeBuyQty(currentCoins, minCoins, itemPrice, availableQuantity);
        if (qty <= 0) {
            logInfo("Cannot afford any more of item: " + shopItem.getDisplayName());
            return PurchaseOutcome.CANT_AFFORD;
        }

        executePurchase(shopItem, itemPrice, availableQuantity, qty, cardIndex);
        return PurchaseOutcome.PURCHASED;
    }

    private boolean isSoldOut(DTOArea cardCoords, AllianceShopItem shopItem) {
        DTOImageSearchResult soldOutResult = emuManager.searchTemplate(
                EMULATOR_NUMBER,
                EnumTemplates.ALLIANCE_SHOP_SOLD_OUT,
                cardCoords.topLeft(),
                cardCoords.bottomRight(),
                90
        );

        if (soldOutResult.isFound()) {
            logInfo("Item " + shopItem.getDisplayName() + " is sold out");
            return true;
        }
        return false;
    }

    private Integer readItemPrice(int cardIndex, AllianceShopItem shopItem) {
        DTOArea priceArea = getPriceArea(cardIndex);
        Integer itemPrice = integerHelper.execute(
                priceArea.topLeft(),
                priceArea.bottomRight(),
                5,
                1000L,
                DTOTesseractSettings.builder().setAllowedChars("0123456789").build(),
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*"))
        );

        if (itemPrice == null) {
            logWarning("Could not read price for item: " + shopItem.getDisplayName());
        }
        return itemPrice;
    }

    private boolean meetsDiscountThreshold(AllianceShopItem shopItem, int itemPrice) {
        int basePrice = shopItem.getBasePrice();

        if (basePrice <= itemPrice) {
            // No discount or price increase - skip validation
            return false;
        }

        double discountPercent = ((basePrice - itemPrice) / (double) basePrice) * 100;
        double minDiscountThreshold = minDiscountPercent - 4; // minDiscountPercent - discountDelta

        logInfo("Item base price: " + basePrice +
                ", current price: " + itemPrice +
                ", discount: " + String.format("%.2f", discountPercent) + "%");

        if (discountPercent < minDiscountThreshold) {
            logInfo("Discount insufficient (required: " + minDiscountThreshold + "%), skipping purchase");
            return false;
        }

        return true;
    }

    private Integer readAvailableQuantity(int cardIndex, AllianceShopItem shopItem) {
        DTOArea quantityArea = getQuantityArea(cardIndex);
        Integer availableQuantity = integerHelper.execute(
                quantityArea.topLeft(),
                quantityArea.bottomRight(),
                5,
                1000L,
                DTOTesseractSettings.builder()
                        .setAllowedChars("0123456789")
                        .setTextColor(Color.white)
                        .setRemoveBackground(true)
                        .build(),
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*"))
        );

        if (availableQuantity == null) {
            logWarning("Could not read available quantity for item: " + shopItem.getDisplayName() + ". Assuming quantity of 1.");
        }

        return availableQuantity;
    }

    private void executePurchase(AllianceShopItem shopItem, int itemPrice, int availableQuantity, int qty, int cardIndex) {
        logInfo("Buying " + qty + " of " + shopItem.getDisplayName() +
                " (Price: " + itemPrice + ", Available: " + availableQuantity +
                ", Current Coins: " + currentCoins + ")");

        DTOArea priceArea = getPriceArea(cardIndex);
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, priceArea.topLeft(), priceArea.bottomRight(), 1, 1500);

        // Select quantity
        if (qty == availableQuantity) {
            // Tap MAX button
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(596, 690), new DTOPoint(626, 717), 1, 300);
        } else {
            // Tap + button (qty-1) times (starts at 1)
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(397, 691), new DTOPoint(425, 716), qty - 1, 300);
        }

        // Confirm purchase
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(330, 815), new DTOPoint(420, 840), 1, 1000);

        currentCoins -= qty * itemPrice;
        logInfo("Successfully purchased " + qty + " of " + shopItem.getDisplayName() + ". Remaining coins: " + currentCoins);

        // Close purchase dialog
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(270, 30), new DTOPoint(280, 80), 3, 200);
    }

    // ==================== Tab Management ====================

    /**
     * Determines which tabs need to be checked for a given item
     */
    private List<AllianceShopItem.ShopTab> getTabsForItem(AllianceShopItem item) {
        if (item.getTab() == AllianceShopItem.ShopTab.BOTH) {
            return List.of(AllianceShopItem.ShopTab.WEEKLY, AllianceShopItem.ShopTab.DAILY);
        }
        return List.of(item.getTab());
    }

    /**
     * Switches to the specified shop tab
     */
    private void switchToTab(AllianceShopItem.ShopTab tab) {
        switch (tab) {
            case WEEKLY:
                emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(450, 1233), new DTOPoint(590, 1263), 3, 200);
                break;
            case DAILY:
                emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(150, 1233), new DTOPoint(290, 1263), 3, 200);
                break;
        }
        sleepTask(1500); // Wait for tab content to load
    }

    // ==================== Item Location ====================

    private AllianceShopItem findShopItemByIdentifier(String identifier) {
        try {
            return AllianceShopItem.valueOf(identifier);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Gets the card index for items. Always searches for dynamic items to handle
     * potential position changes between tabs. Falls back to assumed positions
     * for items without templates.
     */
    private Integer getCardIndex(AllianceShopItem item) {
        // Try to find item using template search first
        EnumTemplates template = getItemTemplate(item);
        if (template != null) {
            Integer foundIndex = searchForItemCard(template);
            if (foundIndex != null) {
                return foundIndex;
            }
        }

        // Fallback to assumed fixed positions (primarily for weekly-only items)
        switch (item) {
            case MYTHIC_HERO_SHARDS: return 1;
            case PET_FOOD: return 2;
            case PET_CHEST: return 3;
            case TRANSFER_PASS: return 4;
            default: return null;
        }
    }

    /**
     * Maps shop items to their corresponding search templates
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
     * Searches for an item across all 9 card positions using the given template
     */
    private Integer searchForItemCard(EnumTemplates template) {
        for (int i = 1; i <= 9; i++) {
            DTOArea area = getItemArea(i);
            DTOImageSearchResult searchResult = emuManager.searchTemplate(
                    EMULATOR_NUMBER,
                    template,
                    area.topLeft(),
                    area.bottomRight(),
                    90
            );
            if (searchResult.isFound()) {
                return i;
            }
        }
        return null;
    }

    // ==================== Coordinate Calculation ====================

    public DTOArea getItemArea(int cardNumber) {
        return getCardArea(cardNumber, 0, 0, 215, 266);
    }

    public DTOArea getPriceArea(int cardNumber) {
        return getCardArea(cardNumber, 54, 210, 158, 48);
    }

    public DTOArea getQuantityArea(int cardNumber) {
        return getCardArea(cardNumber, 65, 165, 100, 35);
    }

    /**
     * Consolidated method to calculate card-based areas with offset and dimensions.
     * Handles Expert unlock Y-axis adjustment automatically.
     */
    private DTOArea getCardArea(int cardNumber, int offsetX, int offsetY, int width, int height) {
        if (cardNumber < 1 || cardNumber > 9) {
            throw new IllegalArgumentException("Card number must be between 1 and 9");
        }

        final int adjustedY = expertUnlocked ? 121 : 0;
        final int startX = 27;
        final int startY = 192 + adjustedY;
        final int itemWidth = 215;
        final int itemHeight = 266;
        final int spacingX = 5;
        final int spacingY = 19;

        int row = (cardNumber - 1) / 3;
        int col = (cardNumber - 1) % 3;

        int cardX = startX + col * (itemWidth + spacingX);
        int cardY = startY + row * (itemHeight + spacingY);

        int x1 = cardX + offsetX;
        int y1 = cardY + offsetY;
        int x2 = x1 + width;
        int y2 = y1 + height;

        return new DTOArea(new DTOPoint(x1, y1), new DTOPoint(x2, y2));
    }

    /**
     * Calculates the maximum quantity that can be purchased while respecting
     * the minimum coins threshold and available stock.
     */
    private int computeBuyQty(int currentCoins, int minCoins, int itemPrice, int availableQuantity) {
        if (itemPrice <= 0) return 0;
        int affordable = (currentCoins - minCoins) / itemPrice;
        return Math.max(0, Math.min(availableQuantity, affordable));
    }
}

