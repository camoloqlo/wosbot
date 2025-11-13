package cl.camodev.wosbot.console.enumerable;

public enum TpDailyTaskEnum {
	// Daily task enumeration
	//@formatter:off
	HERO_RECRUITMENT(1, "Hero Recruitment",      	EnumConfigurationKey.BOOL_HERO_RECRUITMENT),
	NOMADIC_MERCHANT(2, "Nomadic Merchant",       	EnumConfigurationKey.BOOL_NOMADIC_MERCHANT),
	WAR_ACADEMY_TASK_BOOL(3, "War Academy Shards",  	EnumConfigurationKey.WAR_ACADEMY_TASK_BOOL),
	CRYSTAL_LABORATORY(4, "Crystal Laboratory",  	EnumConfigurationKey.BOOL_CRYSTAL_LAB_FC),
	VIP_POINTS(5, "VIP Points",                   	EnumConfigurationKey.BOOL_VIP_POINTS),
	PET_ADVENTURE(6, "Pet Adventure",             	EnumConfigurationKey.PET_PERSONAL_TREASURE_BOOL),
	EXPLORATION_CHEST(7, "Exploration Chest",       EnumConfigurationKey.BOOL_EXPLORATION_CHEST),
	TREK_SUPPLIES(8, "Trek Supplies",            	EnumConfigurationKey.TUNDRA_TREK_SUPPLIES_BOOL),
	LIFE_ESSENCE(9, "Life Essence",                	EnumConfigurationKey.LIFE_ESSENCE_BOOL),
	LIFE_ESSENCE_CARING(10, "Life Essence Caring",  EnumConfigurationKey.ALLIANCE_LIFE_ESSENCE_BOOL),
	LABYRINTH(11, "Labyrinth",                   	EnumConfigurationKey.DAILY_LABYRINTH_BOOL),
	TREK_AUTOMATION(12, "Tundra Trek Automation",  EnumConfigurationKey.TUNDRA_TREK_AUTOMATION_BOOL),
	BANK(14, "Bank",                              EnumConfigurationKey.BOOL_BANK),
	ARENA(15, "Arena",                             EnumConfigurationKey.ARENA_TASK_BOOL),

	MAIL_REWARDS(30, "Mail Rewards",               	EnumConfigurationKey.MAIL_REWARDS_BOOL),
	DAILY_MISSIONS(31, "Daily Missions",           	EnumConfigurationKey.DAILY_MISSION_BOOL),
	STOREHOUSE_CHEST(32, "Storehouse Chest",       	EnumConfigurationKey.STOREHOUSE_CHEST_BOOL),
	INTEL(33, "Intel",                             	EnumConfigurationKey.INTEL_BOOL),
	EXPERT_AGNES_INTEL(34, "Expert Agnes Intel", 	EnumConfigurationKey.EXPERT_AGNES_INTEL_BOOL),
	EXPERT_ROMULUS_TAG(35, "Expert Romulus Tag", 	EnumConfigurationKey.EXPERT_ROMULUS_TAG_BOOL),
	EXPERT_ROMULUS_TROOPS(36, "Expert Romulus Troops", EnumConfigurationKey.EXPERT_ROMULUS_TROOPS_BOOL),
	EXPERT_SKILL_TRAINING(37, "Expert Skill Training", EnumConfigurationKey.EXPERT_SKILL_TRAINING_ENABLED_BOOL),

	ALLIANCE_AUTOJOIN(40, "Alliance Autojoin",     	EnumConfigurationKey.ALLIANCE_AUTOJOIN_BOOL),
	ALLIANCE_TECH(42, "Alliance Tech",             	EnumConfigurationKey.ALLIANCE_TECH_BOOL),
	ALLIANCE_PET_TREASURE(43, "Alliance Pet Treasure", EnumConfigurationKey.ALLIANCE_PET_TREASURE_BOOL),
	ALLIANCE_CHESTS(44, "Alliance Chests",         EnumConfigurationKey.ALLIANCE_CHESTS_BOOL),
	ALLIANCE_TRIUMPH(45, "Alliance Triumph",       EnumConfigurationKey.ALLIANCE_TRIUMPH_BOOL),
	ALLIANCE_MOBILIZATION(46, "Alliance Mobilization", EnumConfigurationKey.ALLIANCE_MOBILIZATION_BOOL),
    ALLIANCE_SHOP(47, "Alliance Shop",             	null),
    ALLIANCE_CHAMPIONSHIP(49, "Alliance Championship", EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_BOOL),
    BEAR_TRAP(48, "Bear Trap Event",          EnumConfigurationKey.BEAR_TRAP_EVENT_BOOL),

	PET_SKILLS(51, "Pet Skills",     EnumConfigurationKey.PET_SKILLS_BOOL),

	TRAINING_TROOPS(60, "Training",     EnumConfigurationKey.TRAIN_BOOL),


	CITY_UPGRADE_FURNACE(70, "City Upgrade Furnace", EnumConfigurationKey.CITY_UPGRADE_FURNACE_BOOL),
	CITY_SURVIVORS(71, "City Survivors", EnumConfigurationKey.CITY_ACCEPT_NEW_SURVIVORS_BOOL),

	SHOP_MYSTERY(80, "Shop Mystery",              EnumConfigurationKey.BOOL_MYSTERY_SHOP),

	CHIEF_ORDER_RUSH_JOB(300, "Chief Order: Rush Job", EnumConfigurationKey.BOOL_CHIEF_ORDER_RUSH_JOB),
	CHIEF_ORDER_URGENT_MOBILIZATION(301, "Chief Order: Urgent Mobilization", EnumConfigurationKey.BOOL_CHIEF_ORDER_URGENT_MOBILISATION),
	CHIEF_ORDER_PRODUCTIVITY_DAY(302, "Chief Order: Productivity Day", EnumConfigurationKey.BOOL_CHIEF_ORDER_PRODUCTIVITY_DAY),

	INITIALIZE(100, "Initialize",                  null),

	GATHER_BOOST(101, "Gather Speed Boost",       EnumConfigurationKey.GATHER_SPEED_BOOL),
	GATHER_RESOURCES(102, "Gather Resources",               EnumConfigurationKey.GATHER_TASK_BOOL),

	EVENT_TUNDRA_TRUCK(200, "Tundra Truck Event",    EnumConfigurationKey.TUNDRA_TRUCK_EVENT_BOOL),
	EVENT_HERO_MISSION(201, "Hero Mission Event",    EnumConfigurationKey.HERO_MISSION_EVENT_BOOL),
	MERCENARY_EVENT(202, "Mercenary Event",    EnumConfigurationKey.MERCENARY_EVENT_BOOL),
    EVENT_JOURNEY_OF_LIGHT(203, "Journey of Light Event",    EnumConfigurationKey.JOURNEY_OF_LIGHT_BOOL),
    EVENT_POLAR_TERROR(204, "Polar Terror Hunting",    EnumConfigurationKey.POLAR_TERROR_ENABLED_BOOL),
    EVENT_MYRIAD_BAZAAR(205, "Myriad Bazaar Event",    EnumConfigurationKey.MYRIAD_BAZAAR_EVENT_BOOL);;


    private final int id;
    private final String name;
    private final EnumConfigurationKey configKey;

    TpDailyTaskEnum(int id, String name, EnumConfigurationKey configKey) {
        this.id = id;
        this.name = name;
        this.configKey = configKey;
    }

    public static TpDailyTaskEnum fromId(int id) {
        for (TpDailyTaskEnum t : values()) {
            if (t.id == id) {
                return t;
            }
        }
        throw new IllegalArgumentException("No TpDailyTaskEnum exists with id " + id);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Daily task enumeration */
    public EnumConfigurationKey getConfigKey() {
        return configKey;
    }
}
