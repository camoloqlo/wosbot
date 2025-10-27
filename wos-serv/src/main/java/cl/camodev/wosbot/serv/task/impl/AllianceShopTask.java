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

/**
 * Task to purchase items from the Alliance Shop based on configured priorities.
 * This task is automatically triggered when Alliance Tech task detects
 * that the user has enough alliance coins (based on minimum threshold).
 */
public class AllianceShopTask extends DelayedTask {

    private Boolean expertUnlocked =false;

	public AllianceShopTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
        this.recurring=false;
	}

	@Override
	protected void execute() {
		logInfo("Starting Alliance Shop purchase task.");

		// 1. Navigate to Alliance Shop and check if available coins >= minimum configured
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(493, 1187), new DTOPoint(561, 1240));
        sleepTask(3000);
        DTOImageSearchResult shopButton = searchTemplateWithRetries(EnumTemplates.ALLIANCE_SHOP_BUTTON, 90, 5);

        if (!shopButton.isFound()) {
            logWarning("Could not find Alliance Shop button");
            setRecurring(false);
            return;
        }
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, shopButton.getPoint(), shopButton.getPoint(),1,1000);
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(580,30), new DTOPoint(670,50),1,1000);


        Integer currentCoins = integerHelper.execute(
                new DTOPoint(272,257),
                new DTOPoint(443,285),
                5,
                200L,
                DTOTesseractSettings.builder().setAllowedChars("0123456789").setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE).build(),
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*"))
        );

        if (currentCoins == null) {
            logWarning("Could not read current alliance coins.");
            setRecurring(false);
            return;
        }
        
        Integer minCoins = profile.getConfig(EnumConfigurationKey.ALLIANCE_SHOP_MIN_COINS_INT, Integer.class);
        logInfo("Current alliance coins: " + currentCoins + ". Minimum to save: " + minCoins);

        if (currentCoins < minCoins) {
            logInfo("Current alliance coins (" + currentCoins + ") are less than the minimum required (" + minCoins + "). Skipping purchases.");
            setRecurring(false);
            return;
        }

        // Tap on top to exit from coins details
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(270,30), new DTOPoint(280,80),3,200);

		// 2. Get purchase priorities from profile configuration using the utility
        List<DTOPriorityItem> enabledPriorities = PriorityItemUtil.getEnabledPriorities(
            profile,
            EnumConfigurationKey.ALLIANCE_SHOP_PRIORITIES_STRING
        );

        if (enabledPriorities.isEmpty()) {
            logWarning("No enabled purchase priorities configured. Please enable items in the Alliance Shop settings.");
            setRecurring(false);
            return;
        }

        logInfo("Found " + enabledPriorities.size() + " enabled purchase priorities:");

        // Log the ordered list of enabled priorities
        for (DTOPriorityItem priority : enabledPriorities) {
            logInfo(" Priority " + priority.getPriority() + ": " + priority.getName() + " (ID: " + priority.getIdentifier() + ")");
        }

        //navigate do week

        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(450,1233), new DTOPoint(590,1263),3,200);

        //verify if expert is unlocked by searching for the expert icon, if its found i must adjust the cards coords
        DTOImageSearchResult expertIcon = searchTemplateWithRetries(
                EnumTemplates.ALLIANCE_SHOP_EXPERT_ICON,
                90,
                3
        );

        if (expertIcon.isFound()) {
            expertUnlocked = true;
            logInfo("Expert unlocked detected in Alliance Shop. Adjusting item coordinates accordingly.");
        }

		// 3. Iterate through enabled items by priority order
        for (DTOPriorityItem priority : enabledPriorities) {
            logInfo("Attempting to purchase: " + priority.getName() + " (Priority: " + priority.getPriority() + ")");

            // Try to find the matching enum item
            AllianceShopItem shopItem = findShopItemByIdentifier(priority.getIdentifier());
            if (shopItem == null) {
                logWarning("Could not find shop item for identifier: " + priority.getIdentifier());
                continue;
            }
            // 4. Attempt to purchase each item
            //first i need if the item in the card is available or is already bought
            DTOArea cardCoords = cardsCoords(shopItem);
            DTOImageSearchResult soldOutResult = emuManager.searchTemplate(EMULATOR_NUMBER,EnumTemplates.ALLIANCE_SHOP_SOLD_OUT,cardCoords.topLeft(),cardCoords.bottomRight(),90);
            if (soldOutResult.isFound()){
                logInfo("Item "+shopItem.getDisplayName()+" is sold out, skipping.");
                continue;
            }
            Integer cardIndex = cardIndex(shopItem);

            DTOArea priceArea = getPriceArea(cardIndex);
            Integer itemPrice = integerHelper.execute(
                priceArea.topLeft(),
                priceArea.bottomRight(),
                5,
                1000L,
                DTOTesseractSettings.builder().setAllowedChars("0123456789").build(),
                    text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                    text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));
            if (itemPrice == null) {
                logWarning("Could not read price for item: " + shopItem.getDisplayName());
                continue;
            }

            // need to check how many items are available to buy and determine the max affordable quantity
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
                //if this scenario happens, assume quantity of 1
                logWarning("Could not read available quantity for item: " + shopItem.getDisplayName() + ". Assuming quantity of 1.");
                availableQuantity = 1;
            }

            int qty = computeBuyQty(currentCoins, minCoins, itemPrice, availableQuantity);
            if (qty <= 0) {
                logInfo("Cannot afford any more of item: " + shopItem.getDisplayName() + " or minimum coins threshold reached. Skipping.");
                continue;
            }

            //try to buy the item, if qty = availableQuantity, buy max, else buy qty
            logInfo("Attempting to buy " + qty + " of item: " + shopItem.getDisplayName() + " (Price: " + itemPrice + ", Available: " + availableQuantity + ", Current Coins: " + currentCoins + ")");

            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, priceArea.topLeft(), priceArea.bottomRight(),1,1500);
            if (qty == availableQuantity){
                //if qty = availableQuantity, tap on max button
                emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(596,690), new DTOPoint(626,717),1,300);
            }else{
                //click the plus button qty-1 times
                emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(397,691), new DTOPoint(425,716),qty-1,300);
            }
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(330,815), new DTOPoint(420,840),1,1000);
            currentCoins -= qty * itemPrice;
            logInfo("Successfully purchased " + qty + " of item: " + shopItem.getDisplayName() + ". Remaining coins: " + currentCoins);
            //tap on top a few times to exit from buy confirm and item details
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(270,30), new DTOPoint(280,80),3,200);
        }
        setRecurring(false);
		logInfo("Alliance Shop task completed.");
	}

    /**
     * Finds the AllianceShopItem enum by its identifier
     */
    private AllianceShopItem findShopItemByIdentifier(String identifier) {
        try {
            return AllianceShopItem.valueOf(identifier);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    public DTOArea cardsCoords(AllianceShopItem allianceShopTask) {
        // all squares have fixed coordinates, but some items can be in different squares
        // if fixed, return the fixed coordinates
        // if not, search for the item in the shop and return the card
        switch (allianceShopTask){
            case MYTHIC_HERO_SHARDS -> {
                return  getItemArea(1);
            }
            case PET_FOOD -> {
                return  getItemArea(2);
            }
            case PET_CHEST -> {
                return  getItemArea(3);
            }
            case TRANSFER_PASS ->  {
                return  getItemArea(4);
            }
        }
        return null;
    }

    public Integer cardIndex(AllianceShopItem allianceShopTask) {
        // all squares have fixed coordinates, but some items can be in different squares
        // if fixed, return the fixed coordinates
        // if not, search for the item in the shop and return the card
        switch (allianceShopTask){
            case MYTHIC_HERO_SHARDS -> {
                return  1;
            }
            case PET_FOOD -> {
                return  2;
            }
            case PET_CHEST -> {
                return  3;
            }
            case TRANSFER_PASS ->  {
                return  4;
            }
        }
        return null;
    }

    public DTOArea getItemArea(int cardNumber) {
        if (cardNumber < 1 || cardNumber > 9) {
            throw new IllegalArgumentException("El número de tarjeta debe estar entre 1 y 9");
        }

        final int adjustedCardNumber = expertUnlocked ? 121 : 0;

        final int startX = 27;
        final int startY = 192 + adjustedCardNumber;
        final int itemWidth = 215;
        final int itemHeight = 266;
        final int spacingX = 5;
        final int spacingY = 19;

        int row = (cardNumber - 1) / 3;
        int col = (cardNumber - 1) % 3;

        int x1 = startX + col * (itemWidth + spacingX);
        int y1 = startY + row * (itemHeight + spacingY);
        int x2 = x1 + itemWidth;
        int y2 = y1 + itemHeight;

        return new DTOArea(new DTOPoint(x1, y1), new DTOPoint(x2, y2));
    }
// in case we need to find a card by point
//    public int getCardIndexFromPoint(DTOPoint point) {
//
//        final int startX = 27;
//        final int startY = 192;
//        final int itemWidth = 215;
//        final int itemHeight = 266;
//        final int spacingX = 5;
//        final int spacingY = 19;
//
//        int x = point.getX();
//        int y = point.getY();
//
//        for (int row = 0; row < 3; row++) {
//            for (int col = 0; col < 3; col++) {
//                int x1 = startX + col * (itemWidth + spacingX);
//                int y1 = startY + row * (itemHeight + spacingY);
//                int x2 = x1 + itemWidth;
//                int y2 = y1 + itemHeight;
//
//                if (x >= x1 && x <= x2 && y >= y1 && y <= y2) {
//                    return row * 3 + col + 1;
//                }
//            }
//        }
//
//        return -1;
//    }

    public DTOArea getPriceArea(int cardNumber) {
        if (cardNumber < 1 || cardNumber > 9) {
            throw new IllegalArgumentException("El número de tarjeta debe estar entre 1 y 9");
        }
        final int adjustedCardNumber = expertUnlocked ? 121 : 0;

        final int startX = 27;
        final int startY = 192 + adjustedCardNumber;
        final int itemWidth = 215;
        final int itemHeight = 266;
        final int spacingX = 5;
        final int spacingY = 19;


        final int offsetX = 54;
        final int offsetY = 210;
        final int priceWidth = 158;
        final int priceHeight = 48;

        int row = (cardNumber - 1) / 3;
        int col = (cardNumber - 1) % 3;

        int cardX = startX + col * (itemWidth + spacingX);
        int cardY = startY + row * (itemHeight + spacingY);

        int x1 = cardX + offsetX;
        int y1 = cardY + offsetY;
        int x2 = x1 + priceWidth;
        int y2 = y1 + priceHeight;

        return new DTOArea(new DTOPoint(x1, y1), new DTOPoint(x2, y2));
    }

    public DTOArea getQuantityArea(int cardNumber) {
        if (cardNumber < 1 || cardNumber > 9) {
            throw new IllegalArgumentException("El número de tarjeta debe estar entre 1 y 9");
        }

        final int adjustedCardNumber = expertUnlocked ? 121 : 0;

        final int startX = 27;
        final int startY = 192 + adjustedCardNumber;
        final int itemWidth = 215;
        final int itemHeight = 266;
        final int spacingX = 5;
        final int spacingY = 19;

        final int offsetX = 65;
        final int offsetY = 165;
        final int width = 100;
        final int height = 35;

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

    private int computeBuyQty(int currentCoins, int minCoins, int itemPrice, int availableQuantity) {
        if (itemPrice <= 0) return 0;
        int affordable = (currentCoins - minCoins) / itemPrice;
        return Math.max(0, Math.min(availableQuantity, affordable));
    }


}
