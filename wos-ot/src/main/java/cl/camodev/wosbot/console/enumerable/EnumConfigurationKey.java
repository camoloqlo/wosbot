package cl.camodev.wosbot.console.enumerable;

public enum EnumConfigurationKey {

    //@formatter:off
	BOOL_DEBUG("false", Boolean.class ),
	DISCORD_TOKEN_STRING("", String.class),
	MUMU_PATH_STRING("", String.class),
	MEMU_PATH_STRING("", String.class),
	LDPLAYER_PATH_STRING("", String.class),
	CURRENT_EMULATOR_STRING("", String.class),

    GAME_VERSION_STRING("GLOBAL", String.class),
	MAX_RUNNING_EMULATORS_INT("1", Integer.class),
	MAX_IDLE_TIME_INT("1", Integer.class),
	
	BOOL_NOMADIC_MERCHANT("false", Boolean.class), 
	BOOL_NOMADIC_MERCHANT_VIP_POINTS("false", Boolean.class), 
	BOOL_WAR_ACADEMY_SHARDS("false", Boolean.class),
	BOOL_CRYSTAL_LAB_FC("false",Boolean.class),
    BOOL_CRYSTAL_LAB_DAILY_DISCOUNTED_RFC("false",Boolean.class),
	BOOL_EXPLORATION_CHEST("false",Boolean.class),
	INT_EXPLORATION_CHEST_OFFSET("60",Integer.class),
	BOOL_HERO_RECRUITMENT("false",Boolean.class),
	BOOL_VIP_POINTS("false",Boolean.class),
	VIP_BUY_MONTHLY("false",Boolean.class),
	
	TRAIN_INFANTRY_BOOL("false",Boolean.class),
	TRAIN_MARKSMAN_BOOL("false",Boolean.class),
	TRAIN_LANCER_BOOL("false",Boolean.class),

	TRAIN_PRIORITIZE_PROMOTION_BOOL("false",Boolean.class),
	
	BOOL_TRAINING_RESOURCES("false",Boolean.class),
	
	CITY_UPGRADE_FURNACE_BOOL("false",Boolean.class),
	CITY_ACCEPT_NEW_SURVIVORS_BOOL("false",Boolean.class),
	CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT("60",Integer.class),

	ALLIANCE_CHESTS_BOOL("false",Boolean.class),
	ALLIANCE_CHESTS_OFFSET_INT("60",Integer.class),
	ALLIANCE_HONOR_CHEST_BOOL("false",Boolean.class),
	ALLIANCE_TECH_BOOL("false",Boolean.class),
	ALLIANCE_TECH_OFFSET_INT("60",Integer.class),
	ALLIANCE_AUTOJOIN_BOOL("false",Boolean.class),
	ALLIANCE_AUTOJOIN_QUEUES_INT("1",Integer.class),
	ALLIANCE_PET_TREASURE_BOOL("false",Boolean.class),
	ALLIANCE_HELP_BOOL("false",Boolean.class),
	ALLIANCE_TRIUMPH_BOOL("false",Boolean.class),
	ALLIANCE_TRIUMPH_OFFSET_INT("60",Integer.class),
	
	GATHER_SPEED_BOOL("false",Boolean.class),
	GATHER_COAL_BOOL("false",Boolean.class),
	GATHER_WOOD_BOOL("false",Boolean.class),
	GATHER_MEAT_BOOL("false",Boolean.class),
	GATHER_IRON_BOOL("false",Boolean.class),
	GATHER_COAL_LEVEL_INT("1",Integer.class),
	GATHER_WOOD_LEVEL_INT("1",Integer.class),
	GATHER_MEAT_LEVEL_INT("1",Integer.class),
	GATHER_IRON_LEVEL_INT("1",Integer.class),
	GATHER_ACTIVE_MARCH_QUEUE_INT("6",Integer.class),
    GATHER_REMOVE_HEROS_BOOL("false",Boolean.class),
	
	INTEL_BOOL("false",Boolean.class),
	INTEL_FIRE_BEAST_BOOL("false",Boolean.class),
	INTEL_BEASTS_BOOL("false",Boolean.class),
	INTEL_CAMP_BOOL("false",Boolean.class),
	INTEL_EXPLORATION_BOOL("false",Boolean.class),
	INTEL_BEASTS_EVENT_BOOL("false",Boolean.class),
	INTEL_BEASTS_FLAG_INT("1", Integer.class),
	INTEL_USE_FLAG_BOOL("false", Boolean.class),
	INTEL_FC_ERA_BOOL("false",Boolean.class),
	
	LIFE_ESSENCE_BOOL("false",Boolean.class),
	LIFE_ESSENCE_OFFSET_INT("60",Integer.class),
	ALLIANCE_LIFE_ESSENCE_BOOL("false",Boolean.class),
	ALLIANCE_LIFE_ESSENCE_OFFSET_INT("60",Integer.class),
	
	MAIL_REWARDS_BOOL("false",Boolean.class),
	MAIL_REWARDS_OFFSET_INT("60",Integer.class),
	
	DAILY_MISSION_BOOL("false",Boolean.class),
	DAILY_MISSION_OFFSET_INT("60",Integer.class),
	DAILY_MISSION_AUTO_SCHEDULE_BOOL("false",Boolean.class),
	
	STOREHOUSE_CHEST_BOOL("false",Boolean.class),

	PET_SKILL_STAMINA_BOOL("false",Boolean.class),
	PET_SKILL_FOOD_BOOL("false",Boolean.class),
	PET_SKILL_TRESURE_BOOL("false",Boolean.class),
	PET_SKILL_GATHERING_BOOL("false",Boolean.class),
	PET_PERSONAL_TREASURE_BOOL("false",Boolean.class),

	DAILY_LABYRINTH_BOOL("false",Boolean.class),
	
	BOOL_BANK("false",Boolean.class),
	INT_BANK_DELAY("1",Integer.class),
	BOOL_MYSTERY_SHOP("false",Boolean.class),
    BOOL_MYSTERY_SHOP_50D_GEAR("false",Boolean.class),

	// Chief Order feature
	BOOL_CHIEF_ORDER_RUSH_JOB("false", Boolean.class),
	BOOL_CHIEF_ORDER_URGENT_MOBILISATION("false", Boolean.class),
	BOOL_CHIEF_ORDER_PRODUCTIVITY_DAY("false", Boolean.class),

	TUNDRA_TRUCK_EVENT_BOOL("false",Boolean.class),
	TUNDRA_TRUCK_USE_GEMS_BOOL("false",Boolean.class),
	TUNDRA_TRUCK_SSR_BOOL("false",Boolean.class),
	MERCENARY_EVENT_BOOL("false",Boolean.class),
	HERO_MISSION_EVENT_BOOL("false",Boolean.class),

	TUNDRA_TREK_SUPPLIES_BOOL("false",Boolean.class),

	EXPERT_AGNES_INTEL_BOOL("false", Boolean.class),
	EXPERT_ROMULUS_TAG_BOOL("false", Boolean.class),
	EXPERT_ROMULUS_TROOPS_BOOL("false", Boolean.class),
	EXPERT_ROMULUS_TROOPS_TYPE_STRING("Infantry", String.class),
	;
	//@formatter:on
    private final String defaultValue;
    private final Class<?> type;

    EnumConfigurationKey(String defaultValue, Class<?> type) {
        this.defaultValue = defaultValue;
        this.type = type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public Class<?> getType() {
        return type;
    }

    /**
     * Method that converts a String to the type defined in 'type'. Add conversions according to the types you need..
     */
    @SuppressWarnings("unchecked")
    public <T> T castValue(String value) {
        if (type.equals(Boolean.class)) {
            return (T) Boolean.valueOf(value);
        } else if (type.equals(Integer.class)) {
            return (T) Integer.valueOf(value);
        } else if (type.equals(Double.class)) {
            return (T) Double.valueOf(value);
        } else if (type.equals(String.class)) {
            return (T) value;
        }
        // Add other if/else according to the supported types
        throw new UnsupportedOperationException("Type " + type.getSimpleName() + " not supported for casting.");
    }
}
