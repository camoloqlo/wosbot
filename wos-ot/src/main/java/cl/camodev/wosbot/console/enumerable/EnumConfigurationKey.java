package cl.camodev.wosbot.console.enumerable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Configuration keys for the application.
 * Keys are organized by functional categories for easier management.
 */
public enum EnumConfigurationKey {

	// @formatter:off
    // ========================================================================
    // SYSTEM AND EMULATOR SETTINGS
    // ========================================================================
	BOOL_DEBUG("false", Boolean.class),
	GAME_VERSION_STRING("GLOBAL", String.class),
	MAX_RUNNING_EMULATORS_INT("1", Integer.class),
	MAX_IDLE_TIME_INT("1", Integer.class),
	IDLE_BEHAVIOR_SEND_TO_BACKGROUND_BOOL("false", Boolean.class),
	MUMU_PATH_STRING("", String.class),
	MEMU_PATH_STRING("", String.class),
	LDPLAYER_PATH_STRING("", String.class),
	CURRENT_EMULATOR_STRING("", String.class),
	DISCORD_TOKEN_STRING("", String.class),
	
    // ========================================================================
    // CITY AND BUILDING MANAGEMENT
    // ========================================================================
	CITY_UPGRADE_FURNACE_BOOL("false", Boolean.class),
	CITY_ACCEPT_NEW_SURVIVORS_BOOL("false", Boolean.class),
	CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT("60", Integer.class),
	
    // ========================================================================
    // RESOURCE GATHERING AND MANAGEMENT
    // ========================================================================
	GATHER_SPEED_BOOL("false", Boolean.class),
	GATHER_SPEED_BOOST_TYPE_STRING("24h (600 gems)", String.class),
	GATHER_TASK_BOOL("false", Boolean.class),
	GATHER_COAL_BOOL("false", Boolean.class),
	GATHER_WOOD_BOOL("false", Boolean.class),
	GATHER_MEAT_BOOL("false", Boolean.class),
	GATHER_IRON_BOOL("false", Boolean.class),
	GATHER_COAL_LEVEL_INT("8", Integer.class),
	GATHER_WOOD_LEVEL_INT("8", Integer.class),
	GATHER_MEAT_LEVEL_INT("8", Integer.class),
	GATHER_IRON_LEVEL_INT("8", Integer.class),
	GATHER_ACTIVE_MARCH_QUEUE_INT("6", Integer.class),
	GATHER_REMOVE_HEROS_BOOL("true", Boolean.class),
	
    // ========================================================================
    // TROOP TRAINING AND MANAGEMENT
    // ========================================================================
	TRAIN_BOOL("false", Boolean.class),
    TRAIN_INFANTRY_BOOL("false", Boolean.class),
	TRAIN_MARKSMAN_BOOL("false", Boolean.class),
	TRAIN_LANCER_BOOL("false", Boolean.class),
	TRAIN_PRIORITIZE_PROMOTION_BOOL("false", Boolean.class),
    TRAIN_MINISTRY_APPOINTMENT_BOOL("false", Boolean.class),
    TRAIN_MINISTRY_APPOINTMENT_TIME_LONG("0", Long.class),
	BOOL_TRAINING_RESOURCES("false", Boolean.class),
	
    // ========================================================================
    // INTELLIGENCE FEATURES
    // ========================================================================
	INTEL_BOOL("false", Boolean.class),
	INTEL_FIRE_BEAST_BOOL("false", Boolean.class),
	INTEL_BEASTS_BOOL("false", Boolean.class),
	INTEL_CAMP_BOOL("false", Boolean.class),
	INTEL_EXPLORATION_BOOL("false", Boolean.class),
	INTEL_BEASTS_EVENT_BOOL("false", Boolean.class),
	INTEL_BEASTS_FLAG_INT("1", Integer.class),
	INTEL_USE_FLAG_BOOL("false", Boolean.class),
	INTEL_FC_ERA_BOOL("false", Boolean.class),
	INTEL_SMART_PROCESSING_BOOL("false", Boolean.class),
	INTEL_RECALL_GATHER_TROOPS_BOOL("false", Boolean.class),
	
    // ========================================================================
    // ALLIANCE FEATURES
    // ========================================================================
	ALLIANCE_CHESTS_BOOL("false", Boolean.class),
	ALLIANCE_CHESTS_OFFSET_INT("60", Integer.class),
	ALLIANCE_HONOR_CHEST_BOOL("false", Boolean.class),
	ALLIANCE_TECH_BOOL("false", Boolean.class),
	ALLIANCE_TECH_OFFSET_INT("60", Integer.class),
	ALLIANCE_AUTOJOIN_BOOL("false", Boolean.class),
	ALLIANCE_AUTOJOIN_QUEUES_INT("1", Integer.class),
	ALLIANCE_AUTOJOIN_USE_ALL_TROOPS_BOOL("true", Boolean.class),
	ALLIANCE_AUTOJOIN_USE_PREDEFINED_FORMATION_BOOL("false", Boolean.class),
	ALLIANCE_PET_TREASURE_BOOL("false", Boolean.class),
	ALLIANCE_HELP_BOOL("false", Boolean.class),
	ALLIANCE_TRIUMPH_BOOL("false", Boolean.class),
	ALLIANCE_TRIUMPH_OFFSET_INT("60", Integer.class),
	ALLIANCE_LIFE_ESSENCE_BOOL("false", Boolean.class),
	ALLIANCE_LIFE_ESSENCE_OFFSET_INT("60", Integer.class),
	ALLIANCE_MOBILIZATION_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_REWARDS_PERCENTAGE_STRING("Any", String.class),
	ALLIANCE_MOBILIZATION_BUILD_SPEEDUPS_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_BUY_PACKAGE_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_CHIEF_GEAR_CHARM_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_CHIEF_GEAR_SCORE_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_DEFEAT_BEASTS_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_FIRE_CRYSTAL_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_GATHER_RESOURCES_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_HERO_GEAR_STONE_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_MYTHIC_SHARD_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_RALLY_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_TRAIN_TROOPS_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_TRAINING_SPEEDUPS_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_USE_GEMS_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_USE_SPEEDUPS_BOOL("false", Boolean.class),
	ALLIANCE_MOBILIZATION_MINIMUM_POINTS_200_INT("800", Integer.class),
	ALLIANCE_MOBILIZATION_MINIMUM_POINTS_120_INT("520", Integer.class),
	ALLIANCE_MOBILIZATION_AUTO_ACCEPT_BOOL("true", Boolean.class),
	ALLIANCE_MOBILIZATION_USE_GEMS_FOR_ACCEPT_BOOL("false", Boolean.class),
	ALLIANCE_CHAMPIONSHIP_BOOL("false", Boolean.class),
	ALLIANCE_CHAMPIONSHIP_OVERRIDE_DEPLOY_BOOL("false", Boolean.class),
	ALLIANCE_CHAMPIONSHIP_INFANTRY_PERCENTAGE_INT("50", Integer.class),
	ALLIANCE_CHAMPIONSHIP_LANCERS_PERCENTAGE_INT("20", Integer.class),
	ALLIANCE_CHAMPIONSHIP_MARKSMANS_PERCENTAGE_INT("30", Integer.class),
	ALLIANCE_CHAMPIONSHIP_POSITION_STRING("CENTER", String.class),

    // ========================================================================
    // LIFE ESSENCE AND PETS
    // ========================================================================
	LIFE_ESSENCE_BOOL("false", Boolean.class),
	LIFE_ESSENCE_OFFSET_INT("60", Integer.class),
	LIFE_ESSENCE_BUY_WEEKLY_SCROLL_BOOL("true", Boolean.class),
	LIFE_ESSENCE_CONSECUTIVE_FAILURES_INT("0", Integer.class),
	LIFE_ESSENCE_NEXT_SCROLL_TIME_STRING("", String.class),
	PET_SKILLS_BOOL("false", Boolean.class),
	PET_SKILL_STAMINA_BOOL("false", Boolean.class),
	PET_SKILL_FOOD_BOOL("false", Boolean.class),
	PET_SKILL_TREASURE_BOOL("false", Boolean.class),
	PET_SKILL_GATHERING_BOOL("false", Boolean.class),
	PET_SKILL_GATHERING_RESOURCE_STRING("MEAT", String.class),
	PET_PERSONAL_TREASURE_BOOL("false", Boolean.class),
	
    // ========================================================================
    // DAILY TASKS AND MISSIONS
    // ========================================================================
	MAIL_REWARDS_BOOL("false", Boolean.class),
	MAIL_REWARDS_OFFSET_INT("60", Integer.class),
	DAILY_MISSION_BOOL("false", Boolean.class),
	DAILY_MISSION_OFFSET_INT("60", Integer.class),
	DAILY_MISSION_AUTO_SCHEDULE_BOOL("false", Boolean.class),
	STOREHOUSE_CHEST_BOOL("false", Boolean.class),
	STOREHOUSE_STAMINA_CLAIM_TIME_STRING("", String.class),
	DAILY_LABYRINTH_BOOL("false", Boolean.class),
	ARENA_TASK_ACTIVATION_TIME_STRING("23:50", String.class),
	ARENA_TASK_BOOL("false", Boolean.class),
	ARENA_TASK_EXTRA_ATTEMPTS_INT("0", Integer.class),
	ARENA_TASK_REFRESH_WITH_GEMS_BOOL("false", Boolean.class),
	ARENA_TASK_PLAYER_STATE_INT("0", Integer.class),
	
    // ========================================================================
    // SHOPS AND MERCHANTS
    // ========================================================================
	BOOL_NOMADIC_MERCHANT("false", Boolean.class), 
	BOOL_NOMADIC_MERCHANT_VIP_POINTS("false", Boolean.class), 
	WAR_ACADEMY_TASK_BOOL("false", Boolean.class),
	BOOL_CRYSTAL_LAB_FC("false", Boolean.class),
	BOOL_CRYSTAL_LAB_DAILY_DISCOUNTED_RFC("false", Boolean.class),
	INT_WEEKLY_RFC("0", Integer.class),
	BOOL_EXPLORATION_CHEST("false", Boolean.class),
	INT_EXPLORATION_CHEST_OFFSET("60", Integer.class),
	BOOL_HERO_RECRUITMENT("false", Boolean.class),
	BOOL_VIP_POINTS("false", Boolean.class),
	VIP_MONTHLY_BUY_BOOL("false", Boolean.class),
	VIP_NEXT_MONTHLY_BUY_TIME_STRING("", String.class),
	BOOL_MYSTERY_SHOP("false", Boolean.class),
	BOOL_MYSTERY_SHOP_250_HERO_WIDGET("false", Boolean.class),

    // ========================================================================
    // BANK FEATURES
    // ========================================================================
	BOOL_BANK("false", Boolean.class),
	INT_BANK_DELAY("1", Integer.class),
	
    // ========================================================================
    // EVENTS AND SPECIAL FEATURES
    // ========================================================================

	// Chief Order feature
	BOOL_CHIEF_ORDER_RUSH_JOB("false", Boolean.class),
	BOOL_CHIEF_ORDER_URGENT_MOBILISATION("false", Boolean.class),
	BOOL_CHIEF_ORDER_PRODUCTIVITY_DAY("false", Boolean.class),
	
	// Tundra events
	TUNDRA_TRUCK_EVENT_BOOL("false", Boolean.class),
	TUNDRA_TRUCK_ACTIVATION_TIME_BOOL("false", Boolean.class),
	TUNDRA_TRUCK_USE_GEMS_BOOL("false", Boolean.class),
	TUNDRA_TRUCK_SSR_BOOL("false", Boolean.class),
	TUNDRA_TRUCK_ACTIVATION_TIME_STRING("14:00", String.class),
	TUNDRA_TREK_SUPPLIES_BOOL("false", Boolean.class),
	TUNDRA_TREK_AUTOMATION_BOOL("false", Boolean.class),
	
	// Polar Terror Hunting
	POLAR_TERROR_ENABLED_BOOL("false", Boolean.class),
	POLAR_TERROR_LEVEL_INT("1", Integer.class),
	POLAR_TERROR_FLAG_STRING("No Flag", String.class),
	POLAR_TERROR_MODE_STRING("Limited (10)", String.class),

	// Mercenary event
	MERCENARY_EVENT_BOOL("false", Boolean.class),
	MERCENARY_FLAG_INT("0", Integer.class),
	
	// Hero mission event
	HERO_MISSION_EVENT_BOOL("false", Boolean.class),
	HERO_MISSION_FLAG_INT("0", Integer.class),

    // Journey of Light event
    JOURNEY_OF_LIGHT_BOOL("false", Boolean.class),

    // Myriad Bazaar event
    MYRIAD_BAZAAR_EVENT_BOOL("false", Boolean.class),

    // Bear trap event
    BEAR_TRAP_EVENT_BOOL("false", Boolean.class),
    BEAR_TRAP_RALLY_FLAG_INT("1", Integer.class),
    BEAR_TRAP_SCHEDULE_DATETIME_STRING("", LocalDateTime.class),
    BEAR_TRAP_PREPARATION_TIME_INT("5", Integer.class),
    BEAR_TRAP_ACTIVE_PETS_BOOL("false", Boolean.class),
    BEAR_TRAP_NUMBER_INT("1", Integer.class),
    BEAR_TRAP_RECALL_TROOPS_BOOL("false", Boolean.class),
    BEAR_TRAP_CALL_RALLY_BOOL("false", Boolean.class),
    BEAR_TRAP_JOIN_RALLY_BOOL("false", Boolean.class),
    BEAR_TRAP_JOIN_FLAG_INT("1", String.class),  // Comma-separated flags: "1,3,4"

    // ========================================================================
    // EXPERT SETTINGS
    // ========================================================================
	EXPERT_AGNES_INTEL_BOOL("false", Boolean.class),
	EXPERT_ROMULUS_TAG_BOOL("false", Boolean.class),
	EXPERT_ROMULUS_TROOPS_BOOL("false", Boolean.class),
	EXPERT_ROMULUS_TROOPS_TYPE_STRING("Infantry", String.class),
	EXPERT_SKILL_TRAINING_ENABLED_BOOL("false", Boolean.class),
	EXPERT_SKILL_TRAINING_PRIORITIES_STRING("", String.class),

    // ========================================================================
    // PRIORITIZED LIST SETTINGS
    // ========================================================================
	// Stored format: "name:priority:enabled|name:priority:enabled|..."
	// Example: "Fire Crystals:1:true|VIP Points:2:true|Hero Shards:3:false|Speedups:4:true"
    ALLIANCE_SHOP_ENABLED_BOOL("false", Boolean.class),
	ALLIANCE_SHOP_PRIORITIES_STRING("", String.class),
    ALLIANCE_SHOP_MIN_PERCENTAGE_INT("50", Integer.class),
	ALLIANCE_SHOP_MIN_COINS_TO_ACTIVATE_INT("0", Integer.class),
	ALLIANCE_SHOP_MIN_COINS_INT("0", Integer.class);

	// @formatter:on
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
        } else if (type.equals(Long.class)) {
            return (T) Long.valueOf(value);
        } else if (type.equals(Double.class)) {
            return (T) Double.valueOf(value);
        } else if (type.equals(String.class)) {
            return (T) value;
        } else if (type.equals(LocalDateTime.class)) {
            if (value == null || value.isBlank()) {
                LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
                return (T) nowUtc.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            }
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
                return (T) LocalDateTime.parse(value, formatter);
            } catch (Exception e) {
                // Si el formato no coincide o hay error, devuelve la siguiente hora UTC exacta
                LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
                return (T) nowUtc.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            }
        }

        // Add other if/else according to the supported types
        throw new UnsupportedOperationException("Type " + type.getSimpleName() + " not supported for casting.");
    }
}
