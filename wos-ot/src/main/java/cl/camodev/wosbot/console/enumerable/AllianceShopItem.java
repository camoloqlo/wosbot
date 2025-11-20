package cl.camodev.wosbot.console.enumerable;

/**
 * Enum defining the available items in the Alliance Shop.
 * Modify this enum to change the items available in the shop.
 */
public enum AllianceShopItem implements PrioritizableItem {
    MYTHIC_HERO_SHARDS( "Mythic Hero Shards",       500000,  ShopTab.WEEKLY),
    PET_FOOD(           "Pet Food",                 66650,   ShopTab.WEEKLY),
    PET_CHEST(          "Pet Chest",                100000,  ShopTab.WEEKLY),
    TRANSFER_PASS(      "Transfer Pass",            500000,  ShopTab.WEEKLY),
    VIP_XP_100(         "100 VIP XP",               6700,    ShopTab.BOTH),
    VIP_XP_10(          "10 VIP XP",                670,     ShopTab.BOTH),
    MARCH_RECALL(       "March Recall",             26000,   ShopTab.BOTH),
    ADVANCED_TELEPORT(  "Advanced Teleport",        130000,  ShopTab.BOTH),
    TERRITORY_TELEPORT( "Territory Teleport",       67000,   ShopTab.BOTH),;

    private final String displayName;
    private final int basePrice;
    private final ShopTab tab;

    AllianceShopItem(String displayName, int basePrice, ShopTab tab) {
        this.displayName = displayName;
        this.basePrice = basePrice;
        this.tab = tab;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public int getBasePrice() {
        return basePrice;
    }

    public ShopTab getTab() {
        return tab;
    }

    /**
     * Gets the enum name as identifier (used for matching with saved data)
     */
    @Override
    public String getIdentifier() {
        return this.name();
    }

    @Override
    public String toString() {
        return displayName;
    }

    public enum ShopTab {
        DAILY, WEEKLY, BOTH
    }
}