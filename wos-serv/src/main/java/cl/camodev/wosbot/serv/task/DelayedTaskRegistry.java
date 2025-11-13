package cl.camodev.wosbot.serv.task;

import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.impl.*;
import cl.camodev.wosbot.almac.repo.ProfileRepository;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public class DelayedTaskRegistry {
    private static final Map<TpDailyTaskEnum, Function<DTOProfiles, DelayedTask>> registry = new EnumMap<>(TpDailyTaskEnum.class);

    static {
        registry.put(TpDailyTaskEnum.HERO_RECRUITMENT, profile -> new HeroRecruitmentTask(profile, TpDailyTaskEnum.HERO_RECRUITMENT));
        registry.put(TpDailyTaskEnum.NOMADIC_MERCHANT, profile -> new NomadicMerchantTask(profile, TpDailyTaskEnum.NOMADIC_MERCHANT));
        registry.put(TpDailyTaskEnum.WAR_ACADEMY_TASK_BOOL, profile -> new WarAcademyTask(profile, TpDailyTaskEnum.WAR_ACADEMY_TASK_BOOL));
        registry.put(TpDailyTaskEnum.CRYSTAL_LABORATORY, profile -> new CrystalLaboratoryTask(profile, TpDailyTaskEnum.CRYSTAL_LABORATORY));
        registry.put(TpDailyTaskEnum.VIP_POINTS, profile -> new VipTask(profile, TpDailyTaskEnum.VIP_POINTS));
        registry.put(TpDailyTaskEnum.PET_ADVENTURE, profile -> new PetAdventureChestTask(profile, TpDailyTaskEnum.PET_ADVENTURE));
        registry.put(TpDailyTaskEnum.EXPLORATION_CHEST, profile -> new ExplorationTask(profile, TpDailyTaskEnum.EXPLORATION_CHEST));
        registry.put(TpDailyTaskEnum.LIFE_ESSENCE, profile -> new LifeEssenceTask(profile, TpDailyTaskEnum.LIFE_ESSENCE));
        registry.put(TpDailyTaskEnum.LIFE_ESSENCE_CARING, profile -> new LifeEssenceCaringTask(profile, TpDailyTaskEnum.LIFE_ESSENCE_CARING));
        registry.put(TpDailyTaskEnum.LABYRINTH, profile -> new DailyLabyrinthTask(profile, TpDailyTaskEnum.LABYRINTH));
        registry.put(TpDailyTaskEnum.TREK_SUPPLIES, profile -> new TundraTrekTask(profile, TpDailyTaskEnum.TREK_SUPPLIES));
        registry.put(TpDailyTaskEnum.TREK_AUTOMATION, profile -> new TundraTrekAutoTask(profile, TpDailyTaskEnum.TREK_AUTOMATION));
        registry.put(TpDailyTaskEnum.BANK, profile -> new BankTask(profile, TpDailyTaskEnum.BANK));
        registry.put(TpDailyTaskEnum.SHOP_MYSTERY, profile -> new MysteryShopTask(profile, TpDailyTaskEnum.SHOP_MYSTERY));
        //registry.put(TpDailyTaskEnum.BEAST_SLAY, profile -> new BeastSlayTask(profile, TpDailyTaskEnum.BEAST_SLAY));

        // Gathering task
        registry.put(TpDailyTaskEnum.GATHER_RESOURCES, profile -> new GatherTask(profile, TpDailyTaskEnum.GATHER_RESOURCES));
        registry.put(TpDailyTaskEnum.GATHER_BOOST, profile -> new GatherSpeedTask(profile, TpDailyTaskEnum.GATHER_BOOST));
        

        // Daily rewards
        registry.put(TpDailyTaskEnum.MAIL_REWARDS, profile -> new MailRewardsTask(profile, TpDailyTaskEnum.MAIL_REWARDS));
        registry.put(TpDailyTaskEnum.DAILY_MISSIONS, profile -> new DailyMissionTask(profile, TpDailyTaskEnum.DAILY_MISSIONS));
        registry.put(TpDailyTaskEnum.STOREHOUSE_CHEST, profile -> new StorehouseChest(profile, TpDailyTaskEnum.STOREHOUSE_CHEST));
        registry.put(TpDailyTaskEnum.INTEL, profile -> new IntelligenceTask(profile, TpDailyTaskEnum.INTEL));
        registry.put(TpDailyTaskEnum.EXPERT_AGNES_INTEL, profile -> new ExpertsAgnesIntelTask(profile, TpDailyTaskEnum.EXPERT_AGNES_INTEL));
        registry.put(TpDailyTaskEnum.EXPERT_ROMULUS_TAG, profile -> new ExpertsRomulusTagTask(profile, TpDailyTaskEnum.EXPERT_ROMULUS_TAG));
        registry.put(TpDailyTaskEnum.EXPERT_ROMULUS_TROOPS, profile -> new ExpertsRomulusTroopsTask(profile, TpDailyTaskEnum.EXPERT_ROMULUS_TROOPS));
        registry.put(TpDailyTaskEnum.EXPERT_SKILL_TRAINING, profile -> new ExpertSkillTrainingTask(profile, TpDailyTaskEnum.EXPERT_SKILL_TRAINING));

        // Alliance tasks
        registry.put(TpDailyTaskEnum.ALLIANCE_AUTOJOIN, profile -> new AllianceAutojoinTask(profile, TpDailyTaskEnum.ALLIANCE_AUTOJOIN));
        registry.put(TpDailyTaskEnum.ALLIANCE_TECH, profile -> new AllianceTechTask(profile, TpDailyTaskEnum.ALLIANCE_TECH));
        registry.put(TpDailyTaskEnum.ALLIANCE_SHOP, profile -> new AllianceShopTask(profile, TpDailyTaskEnum.ALLIANCE_SHOP));
        registry.put(TpDailyTaskEnum.ALLIANCE_PET_TREASURE, profile -> new PetAllianceTreasuresTask(profile, TpDailyTaskEnum.ALLIANCE_PET_TREASURE));
        registry.put(TpDailyTaskEnum.ALLIANCE_CHESTS, profile -> new AllianceChestTask(profile, TpDailyTaskEnum.ALLIANCE_CHESTS));
        registry.put(TpDailyTaskEnum.ALLIANCE_TRIUMPH, profile -> new TriumphTask(profile, TpDailyTaskEnum.ALLIANCE_TRIUMPH));
        registry.put(TpDailyTaskEnum.ALLIANCE_MOBILIZATION, profile -> new AllianceMobilizationTask(profile, TpDailyTaskEnum.ALLIANCE_MOBILIZATION));
        registry.put(TpDailyTaskEnum.ALLIANCE_CHAMPIONSHIP, profile -> new AllianceChampionshipTask(profile, TpDailyTaskEnum.ALLIANCE_CHAMPIONSHIP));

        // Pet skills tasks (unified)
        registry.put(TpDailyTaskEnum.PET_SKILLS,   profile -> new PetSkillsTask(profile, TpDailyTaskEnum.PET_SKILLS));

        // Training troops tasks
        registry.put(TpDailyTaskEnum.TRAINING_TROOPS, profile -> new TrainingTask(profile,TpDailyTaskEnum.TRAINING_TROOPS));

        // Chief Order tasks
        registry.put(TpDailyTaskEnum.CHIEF_ORDER_RUSH_JOB, profile -> new ChiefOrderTask(profile, TpDailyTaskEnum.CHIEF_ORDER_RUSH_JOB, cl.camodev.wosbot.serv.task.impl.ChiefOrderTask.ChiefOrderType.RUSH_JOB));
        registry.put(TpDailyTaskEnum.CHIEF_ORDER_URGENT_MOBILIZATION, profile -> new ChiefOrderTask(profile, TpDailyTaskEnum.CHIEF_ORDER_URGENT_MOBILIZATION, cl.camodev.wosbot.serv.task.impl.ChiefOrderTask.ChiefOrderType.URGENT_MOBILIZATION));
        registry.put(TpDailyTaskEnum.CHIEF_ORDER_PRODUCTIVITY_DAY, profile -> new ChiefOrderTask(profile, TpDailyTaskEnum.CHIEF_ORDER_PRODUCTIVITY_DAY, cl.camodev.wosbot.serv.task.impl.ChiefOrderTask.ChiefOrderType.PRODUCTIVITY_DAY));

        // City upgrade
        registry.put(TpDailyTaskEnum.CITY_UPGRADE_FURNACE, profile -> new UpgradeBuildingsTask(profile, TpDailyTaskEnum.CITY_UPGRADE_FURNACE));
		registry.put(TpDailyTaskEnum.ARENA, profile -> new ArenaTask(profile, TpDailyTaskEnum.ARENA));
        registry.put(TpDailyTaskEnum.CITY_SURVIVORS, profile -> new NewSurvivorsTask(profile, TpDailyTaskEnum.CITY_SURVIVORS));
        
        // Events
        registry.put(TpDailyTaskEnum.EVENT_TUNDRA_TRUCK, profile -> new TundraTruckEventTask(profile, TpDailyTaskEnum.EVENT_TUNDRA_TRUCK));
        registry.put(TpDailyTaskEnum.EVENT_HERO_MISSION, profile -> new HeroMissionEventTask(profile, TpDailyTaskEnum.EVENT_HERO_MISSION));
        registry.put(TpDailyTaskEnum.MERCENARY_EVENT, profile -> new MercenaryEventTask(profile, TpDailyTaskEnum.MERCENARY_EVENT));
        registry.put(TpDailyTaskEnum.EVENT_JOURNEY_OF_LIGHT, profile -> new JourneyofLightTask(profile, TpDailyTaskEnum.EVENT_JOURNEY_OF_LIGHT));
        registry.put(TpDailyTaskEnum.EVENT_POLAR_TERROR, profile -> new PolarTerrorHuntingTask(profile, TpDailyTaskEnum.EVENT_POLAR_TERROR));
        registry.put(TpDailyTaskEnum.EVENT_MYRIAD_BAZAAR, profile -> new MyriadBazaarEventTask(profile, TpDailyTaskEnum.EVENT_MYRIAD_BAZAAR));
        registry.put(TpDailyTaskEnum.BEAR_TRAP, profile -> new BearTrapTask(profile, TpDailyTaskEnum.BEAR_TRAP));

        // Initialize
        registry.put(TpDailyTaskEnum.INITIALIZE, profile -> new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
    }

    /**
     * Creates a new instance of {@link DelayedTask} based on the provided task type and profile.
     * Updates the profile from the database to get the latest configurations.
     */
    public static DelayedTask create(TpDailyTaskEnum type, DTOProfiles profile) {
        // Update profile from database to get latest configurations
        DTOProfiles updatedProfile = profile;
        if (profile != null && profile.getId() != null) {
            DTOProfiles refreshed = ProfileRepository.getRepository().getProfileWithConfigsById(profile.getId());
            if (refreshed != null) {
                updatedProfile = refreshed;
            }
        }

        Function<DTOProfiles, DelayedTask> factory = registry.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("No factory registered for task type: " + type);
        }
        return factory.apply(updatedProfile);
    }
}
