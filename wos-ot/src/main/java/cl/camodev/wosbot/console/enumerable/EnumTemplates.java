package cl.camodev.wosbot.console.enumerable;

/**
 * Enum containing all image template paths used for image recognition in the
 * game.
 * Templates are organized by functional categories for easier maintenance.
 */
public enum EnumTemplates {

	// @formatter:off
    // ========================================================================
    // HOME SCREEN AND NAVIGATION
    // ========================================================================
	GAME_HOME_FURNACE("/templates/city.png"),
	GAME_HOME_WORLD("/templates/world.png"),
	GAME_HOME_PETS("/templates/home/petsButton.png"),
	GAME_HOME_INTEL("/templates/home/intelButton.png"),
	GAME_HOME_INTEL_DONE("/templates/intel/intelDone.png"),
	GAME_HOME_RECONNECT("/templates/home/reconnectButton.png"),
	
	GAME_HOME_NEW_SURVIVORS("/templates/home/newSurvivors.png"),

	GAME_HOME_NEW_SURVIVORS_WELCOME_IN("/templates/home/newSurvivorsWelcome.png"),
	GAME_HOME_NEW_SURVIVORS_PLUS_BUTTON("/templates/home/newSurvivorsPlusButton.png"),

	GAME_HOME_BOTTOM_BAR_SHOP_BUTTON("/templates/home/bottombar/shopButton.png"),
	GAME_HOME_BOTTOM_BAR_BACKPACK_BUTTON("/templates/home/bottombar/backpack.png"),
	
	HOME_DEALS_BUTTON("/templates/home/dealsButton.png"),
	HOME_EVENTS_BUTTON("/templates/home/eventsButton.png"),
    
    EVENTS_SUNFIRE_TAB("/templates/events/sunFireTab.png"),
    SUNFIRE_MINISTRY_APPLY_BUTTON("/templates/events/sunFire/ministryApplyButton.png"),
	
	LEFT_MENU_CITY_TAB("/templates/leftmenu/cityTab.png"),
	LEFT_MENU_LABYRINTH_BUTTON("/templates/leftmenu/labyrinth.png"),
	LEFT_MENU_TUNDRA_TREK_BUTTON("/templates/leftmenu/tundraTrek.png"),
    LEFT_MENU_EXPERT_TRAINING_BUTTON("/templates/leftmenu/expertTraining.png"),

    // ========================================================================
    // SHORTCUTS AND BUILDING ACCESS
    // ========================================================================
	GAME_HOME_SHORTCUTS_INFANTRY("/templates/shortcuts/infantry.png"),
	GAME_HOME_SHORTCUTS_LANCER("/templates/shortcuts/lancer.png"),
	GAME_HOME_SHORTCUTS_MARKSMAN("/templates/shortcuts/marksman.png"),
	GAME_HOME_SHORTCUTS_RESEARCH_CENTER("/templates/shortcuts/researchCenter.png"),
	GAME_HOME_SHORTCUTS_HELP_REQUEST("/templates/shortcuts/helpRequest.png"),
    GAME_HOME_SHORTCUTS_HELP_REQUEST1("/templates/shortcuts/helpRequest1.png"),
	GAME_HOME_SHORTCUTS_HELP_REQUEST2("/templates/shortcuts/helpRequest2.png"),
	GAME_HOME_SHORTCUTS_UPGRADE("/templates/shortcuts/upgrade.png"),
	GAME_HOME_SHORTCUTS_OBTAIN("/templates/shortcuts/obtain.png"),
	
	GAME_HOME_CITY_STATUS_GO_BUTTON("/templates/home/city/status/goButton.png"),
	GAME_HOME_CITY_STATUS_COOKHOUSE("/templates/home/city/status/cookhouse.png"),
    GAME_HOME_WAR("/templates/home/war.png"),

    // ========================================================================
    // RESOURCES AND GATHERING
    // ========================================================================
	GAME_HOME_SHORTCUTS_MEAT("/templates/shortcuts/meat.png"),
	GAME_HOME_SHORTCUTS_WOOD("/templates/shortcuts/wood.png"),
	GAME_HOME_SHORTCUTS_COAL("/templates/shortcuts/coal.png"),
	GAME_HOME_SHORTCUTS_IRON("/templates/shortcuts/iron.png"),
	
	GAME_HOME_SHORTCUTS_FARM_MEAT("/templates/shortcuts/farmMeat.png"),
	GAME_HOME_SHORTCUTS_FARM_WOOD("/templates/shortcuts/farmWood.png"),
	GAME_HOME_SHORTCUTS_FARM_COAL("/templates/shortcuts/farmCoal.png"),
	GAME_HOME_SHORTCUTS_FARM_IRON("/templates/shortcuts/farmIron.png"),
	
	GAME_HOME_SHORTCUTS_FARM_TICK("/templates/shortcuts/farmTick.png"),
	GAME_HOME_SHORTCUTS_FARM_GATHER("/templates/shortcuts/farmGather.png"),
	
	GATHER_DEPLOY_BUTTON("/templates/shortcuts/gatherDeploy.png"),
	GATHER_MEAT_HERO("/templates/gathering/meatHero.png"),
	GATHER_WOOD_HERO("/templates/gathering/woodHero.png"),
	GATHER_COAL_HERO("/templates/gathering/coalHero.png"),
	GATHER_IRON_HERO("/templates/gathering/ironHero.png"),

    // ========================================================================
    // BUILDING INTERACTIONS
    // ========================================================================
	BUILDING_BUTTON_TRAIN("/templates/building/trainButton.png"),
	BUILDING_BUTTON_SPEED("/templates/building/speedButton.png"),
	BUILDING_BUTTON_UPGRADE("/templates/building/upgradeButton.png"),
	BUILDING_BUTTON_DETAILS("/templates/building/detailsButton.png"),
	BUILDING_BUTTON_RESEARCH("/templates/building/researchButton.png"),
	BUILDING_BUTTON_LABYRINTH("/templates/building/labyrinthButton.png"),
    BUILDING_BUTTON_INFO("/templates/building/infoButton.png"),

	BUILDING_DETAILS_INFANTRY("/templates/building/detailsInfantry.png"),
	BUILDING_DETAILS_LANCER("/templates/building/detailsLancer.png"),
	BUILDING_DETAILS_MARKSMAN("/templates/building/detailsMarksman.png"),

    BUILDING_SURVIVOR_BUTTON_UPGRADE("/templates/building/survivorUpgradeButton.png"),
    BUILDING_SURVIVOR_BUTTON_FURNITURE_UPGRADE("/templates/building/survivorFurnitureUpgradeButton.png"),

    // ========================================================================
    // DAILY MISSIONS AND REWARDS
    // ========================================================================
	DAILY_MISSION_DAILY_TAB("/templates/dailymission/dailyMissionTab.png"),
	DAILY_MISSION_CLAIMALL_BUTTON("/templates/dailymission/claimAllButton.png"),
	DAILY_MISSION_CLAIM_BUTTON("/templates/dailymission/claimButton.png"),
	
	ALLIANCE_CHEST_CLAIM_BUTTON("/templates/dailymission/claimButton.png"),
	ALLIANCE_CHEST_CLAIM_ALL_BUTTON("/templates/alliance/lootClaimAllButton.png"),

    ALLIANCE_CHAMPIONSHIP_TAB("/templates/alliance/championshipTab.png"),
    ALLIANCE_CHAMPIONSHIP_TROOPS_BUTTON("/templates/alliance/championshipTroopsButton.png"),
    ALLIANCE_CHAMPIONSHIP_REGISTER_BUTTON("/templates/alliance/championshipRegisterButton.png"),
    ALLIANCE_CHAMPIONSHIP_SWITCH_LINE_BUTTON("/templates/alliance/championshipSwitchLineButton.png"),
    ALLIANCE_CHAMPIONSHIP_UPDATE_TROOPS_BUTTON("/templates/alliance/championshipUpdateTroopsButton.png"),
    ALLIANCE_CHAMPIONSHIP_DISPATCH_TROOPS_BUTTON("/templates/alliance/championshipDispatchTroopsButton.png"),
	
	STOREHOUSE_CHEST("/templates/storehouse/chest.png"),
	STOREHOUSE_CHEST_2("/templates/storehouse/chest2.png"),
	STOREHOUSE_STAMINA("/templates/storehouse/stamina.png"),

    MAIL_UNCLAIMED_REWARDS("/templates/mail/unclaimedRewards.png"),
    MAIL_MENU_OPEN("/templates/mail/inMailMenu.png"),
    MAIL_MENU("/templates/mail/mailMenu.png"),

	ARENA_CHALLENGE_BUTTON("/templates/arena/arenaChallengeButton.png"),
	ARENA_FREE_REFRESH_BUTTON("/templates/arena/arenaFreeRefreshButton.png"),
	ARENA_GEMS_REFRESH_BUTTON("/templates/arena/arenaGemsRefreshButton.png"),
	ARENA_GEMS_REFRESH_CONFIRM_BUTTON("/templates/arena/arenaGemsRefreshConfirmButton.png"),
	ARENA_GEMS_EXTRA_ATTEMPTS_BUTTON("/templates/arena/arenaGemsExtraAttemptsButton.png"),

    // ========================================================================
    // TRAINING CAMPS AND TROOPS
    // ========================================================================
	GAME_HOME_CAMP_TRAIN("/templates/home/camp/train.png"),
	TRAINING_TRAIN_BUTTON("/templates/home/camp/training.png"),
	TROOPS_ALREADY_MARCHING("/templates/rally/troopsAlreadyMarching.png"),
	RALLY_BUTTON("/templates/rally/rallyButton.png"),
    RALLY_REMOVE_HERO_BUTTON("/templates/rally/removeHeroButton.png"),
	RALLY_EQUALIZE_BUTTON("/templates/rally/equalizeButton.png"),
    RALLY_HOLD_BUTTON("/templates/rally/holdButton.png"),

	TRAINING_TROOP_PROMOTE("/templates/training/troopPromote.png"),
    TRAINING_TROOP_LOCKED("/templates/training/troopLocked.png"),

    MARCHES_AREA_RECALL_BUTTON("/templates/marches/recallButton.png"),
    MARCHES_AREA_SPEEDUP_BUTTON("/templates/marches/speedupButton.png"),
    MARCHES_AREA_VIEW_BUTTON("/templates/marches/viewButton.png"),

    // Infantry troop tiers
	TRAINING_INFANTRY_T11("/templates/training/infantry11.png"),
	TRAINING_INFANTRY_T10("/templates/training/infantry10.png"),
	TRAINING_INFANTRY_T9("/templates/training/infantry9.png"),
	TRAINING_INFANTRY_T8("/templates/training/infantry8.png"),
	TRAINING_INFANTRY_T7("/templates/training/infantry7.png"),
	TRAINING_INFANTRY_T6("/templates/training/infantry6.png"),
	TRAINING_INFANTRY_T5("/templates/training/infantry5.png"),
	TRAINING_INFANTRY_T4("/templates/training/infantry4.png"),
	TRAINING_INFANTRY_T3("/templates/training/infantry3.png"),
	TRAINING_INFANTRY_T2("/templates/training/infantry2.png"),
	TRAINING_INFANTRY_T1("/templates/training/infantry1.png"),

    // Lancer troop tiers
	TRAINING_LANCER_T11("/templates/training/lancer11.png"),
	TRAINING_LANCER_T10("/templates/training/lancer10.png"),
	TRAINING_LANCER_T9("/templates/training/lancer9.png"),
	TRAINING_LANCER_T8("/templates/training/lancer8.png"),
	TRAINING_LANCER_T7("/templates/training/lancer7.png"),
	TRAINING_LANCER_T6("/templates/training/lancer6.png"),
	TRAINING_LANCER_T5("/templates/training/lancer5.png"),
	TRAINING_LANCER_T4("/templates/training/lancer4.png"),
	TRAINING_LANCER_T3("/templates/training/lancer3.png"),
	TRAINING_LANCER_T2("/templates/training/lancer2.png"),
	TRAINING_LANCER_T1("/templates/training/lancer1.png"),

    // Marksman troop tiers
	TRAINING_MARKSMAN_T11("/templates/training/marksman11.png"),
	TRAINING_MARKSMAN_T10("/templates/training/marksman10.png"),
	TRAINING_MARKSMAN_T9("/templates/training/marksman9.png"),
	TRAINING_MARKSMAN_T8("/templates/training/marksman8.png"),
	TRAINING_MARKSMAN_T7("/templates/training/marksman7.png"),
	TRAINING_MARKSMAN_T6("/templates/training/marksman6.png"),
	TRAINING_MARKSMAN_T5("/templates/training/marksman5.png"),
	TRAINING_MARKSMAN_T4("/templates/training/marksman4.png"),
	TRAINING_MARKSMAN_T3("/templates/training/marksman3.png"),
	TRAINING_MARKSMAN_T2("/templates/training/marksman2.png"),
	TRAINING_MARKSMAN_T1("/templates/training/marksman1.png"),

    // ========================================================================
    // ALLIANCE FEATURES
    // ========================================================================
	ALLIANCE_CHEST_BUTTON("/templates/alliance/chestButton.png"),
	ALLIANCE_HONOR_CHEST("/templates/alliance/honorChest.png"),
	ALLIANCE_TECH_BUTTON("/templates/alliance/techButton.png"),
	ALLIANCE_TRIUMPH_BUTTON("/templates/alliance/triumphButton.png"),
	ALLIANCE_TRIUMPH_DAILY_CLAIMED("/templates/alliance/triumphDailyClaimed.png"),
	ALLIANCE_TRIUMPH_DAILY("/templates/alliance/triumphDaily.png"),
	ALLIANCE_TRIUMPH_WEEKLY("/templates/alliance/triumphWeekly.png"),
	ALLIANCE_TECH_THUMB_UP("/templates/alliance/techThumbUp.png"),
	ALLIANCE_WAR_BUTTON("/templates/alliance/warButton.png"),
    ALLIANCE_TERRITORY_BUTTON("/templates/alliance/territoryButton.png"),
    ALLIANCE_SHOP_BUTTON("/templates/alliance/shopButton.png"),
	ALLIANCE_HELP_BUTTON("/templates/alliance/helpButton.png"),
	ALLIANCE_HELP_REQUESTS("/templates/alliance/helpRequests.png"),

    ALLIANCE_SHOP_EXPERT_ICON("/templates/alliance/expertBadge.png"),
    ALLIANCE_SHOP_SOLD_OUT("/templates/alliance/shopSoldOut.png"),
    ALLIANCE_SHOP_100_VIP_XP("/templates/alliance/shop/vipXP100.png"),
    ALLIANCE_SHOP_10_VIP_XP("/templates/alliance/shop/vipXP10.png"),
    ALLIANCE_SHOP_RECALL_MARCH("/templates/alliance/shop/recallMarch.png"),
    ALLIANCE_SHOP_ADVANCED_TELEPORT("/templates/alliance/shop/advancedTeleport.png"),
    ALLIANCE_SHOP_TERRITORY_TELEPORT("/templates/alliance/shop/territoryTeleport.png"),

    // ========================================================================
    // ALLIANCE MOBILIZATION
    // ========================================================================
    ALLIANCE_MOBILIZATION_TAB("/templates/events/mobilization/allianzMobilizationTab.png"),
    ALLIANCE_MOBILIZATION_UNSELECTED_TAB("/templates/events/mobilization/allianzMobilizationUnselected.png"),
    AM_120_PERCENT("/templates/events/mobilization/AM_120%.png"),
    AM_200_PERCENT("/templates/events/mobilization/AM_200%.png"),
    AM_BAR_X("/templates/events/mobilization/AM_Bar_X.png"),
    AM_COMPLETED("/templates/events/mobilization/AM_Completed.png"),
    AM_PLUS_1_FREE_MISSION("/templates/events/mobilization/AM_+_1_Free_Mission.png"),
    AM_ALLIANCE_MONUMENTS("/templates/events/mobilization/AM_Alliance_Monuments.png"),
    AM_BUILD_SPEEDUPS("/templates/events/mobilization/AM_Build_Speedups.png"),
    AM_BUY_PACKAGE("/templates/events/mobilization/AM_Buy_Package.png"),
    AM_CHIEF_GEAR_CHARM("/templates/events/mobilization/AM_Chief_Gear_Charm.png"),
    AM_CHIEF_GEAR_SCORE("/templates/events/mobilization/AM_Chief_Gear_Score.png"),
    AM_DEFEAT_BEASTS("/templates/events/mobilization/AM_Defeat_Beasts.png"),
    AM_FIRE_CRYSTAL("/templates/events/mobilization/AM_Fire_Crystal.png"),
    AM_GATHER_RESOURCES("/templates/events/mobilization/AM_Gather_Resources.png"),
    AM_HERO_GEAR_STONE("/templates/events/mobilization/AM_Hero_Gear_Stone.png"),
    AM_MYTHIC_SHARD("/templates/events/mobilization/AM_Mythic_Shard.png"),
    AM_RALLY("/templates/events/mobilization/AM_Rally.png"),
    AM_TRAIN_TROOPS("/templates/events/mobilization/AM_Train_Troops.png"),
    AM_TRAINING_SPEEDUPS("/templates/events/mobilization/AM_Training_Speedups.png"),
    AM_USE_GEMS("/templates/events/mobilization/AM_Use_Gems.png"),
    AM_USE_SPEEDUPS("/templates/events/mobilization/AM_Use_Speedups.png"),


    // ========================================================================
    // CRYSTAL LAB AND UPGRADES
    // ========================================================================
	CRYSTAL_LAB_FC_BUTTON("/templates/crystallab/fc.png"),
    CRYSTAL_LAB_REFINE_BUTTON("/templates/crystallab/fcRefine.png"),
    CRYSTAL_LAB_DAILY_DISCOUNTED_RFC("/templates/crystallab/dailyDiscountedRFC.png"),
    CRYSTAL_LAB_RFC_REFINE_BUTTON("/templates/crystallab/rfcRefineButton.png"),
	
	VIP_UNLOCK_BUTTON("/templates/vip/vipUnlockButton.png"),
	VIP_MENU("/templates/vip/vipMenu.png"),

    // ========================================================================
    // INTELLIGENCE AND MISSIONS
    // ========================================================================
	INTEL_COMPLETED("/templates/intel/completed.png"),
	INTEL_VIEW("/templates/intel/beastView.png"),
	INTEL_ATTACK("/templates/intel/beastAttack.png"),
	INTEL_RESCUE("/templates/intel/survivorRescue.png"),
	INTEL_EXPLORE("/templates/intel/journeyExplore.png"),
	INTEL_FIRE_BEAST("/templates/intel/beastFire.png"),
	INTEL_SCREEN_1("/templates/intel/intelScreen1.png"),
	INTEL_SCREEN_2("/templates/intel/intelScreen2.png"),
	INTEL_AGNES("/templates/experts/intelAgnes.png"),
	
	DEPLOY_BUTTON("/templates/intel/deploy.png"),
	DEPLOY_CONFIRMATION_DIALOG("/templates/intel/deployConfirmationDialog.png"),
	
	// Grayscale templates for B&W matching
	INTEL_BEAST_GRAYSCALE("/templates/intel/beastGrayscale.png"),
	INTEL_BEAST_GRAYSCALE_FC("/templates/intel/beastGrayscaleFC.png"),
	INTEL_BEAST_GRAYSCALE_FC1("/templates/intel/beastGrayscaleFC1.png"),
	INTEL_SURVIVOR_GRAYSCALE("/templates/intel/survivorGrayscale.png"),
	INTEL_SURVIVOR_GRAYSCALE_FC("/templates/intel/survivorGrayscaleFC.png"),
	INTEL_JOURNEY_GRAYSCALE("/templates/intel/journeyGrayscale.png"),
	INTEL_JOURNEY_GRAYSCALE_FC("/templates/intel/journeyGrayscaleFC.png"),
	
	INTEL_MASTER_BOUNTY("/templates/intel/masterBounty.png"),
	
    // ========================================================================
    // PETS AND BEAST CAGES
    // ========================================================================
	PETS_BEAST_CAGE("/templates/pets/beastCage.png"),
	PETS_BEAST_ALLIANCE_CLAIM("/templates/pets/claimButton.png"),
	PETS_INFO_SKILLS("/templates/pets/infoSkill.png"),
	PETS_SKILL_USE("/templates/pets/useSkill.png"),
	PETS_UNLOCK_TEXT("/templates/pets/unlockSkillText.png"),
	
	PETS_CHEST_COMPLETED("/templates/pets/chestCompleted.png"),
	PETS_CHEST_SELECT("/templates/pets/chestSelect.png"),
	PETS_CHEST_START("/templates/pets/chestStart.png"),
	PETS_CHEST_ATTEMPT("/templates/pets/chestAttempt.png"),
	PETS_CHEST_SHARE("/templates/pets/chestShare.png"),
	PETS_CHEST_RED("/templates/pets/chestRed.png"),
	PETS_CHEST_PURPLE("/templates/pets/chestPurple.png"),
	PETS_CHEST_BLUE("/templates/pets/chestBlue.png"),
	
    // ========================================================================
    // ISLAND
    // ========================================================================
	LIFE_ESSENCE_MENU("/templates/island/threeMenu.png"),
	LIFE_ESSENCE_CLAIM("/templates/island/claim.png"),
	LIFE_ESSENCE_DAILY_CARING_AVAILABLE("/templates/island/dailyCaringAvailable.png"),
	LIFE_ESSENCE_DAILY_CARING_GOTO_ISLAND("/templates/island/dailyCaringGotoIsland.png"),
	LIFE_ESSENCE_DAILY_CARING_BUTTON("/templates/island/dailyCaringButton.png"),
	ISLAND_WEEKLY_FREE_SCROLL("/templates/island/weeklyFreeScroll.png"),
	ISLAND_WEEKLY_FREE_SCROLL_BUY_BUTTON("/templates/island/weeklyFreeScrollBuyButton.png"),
    ISLAND_LIKE_BUTTON("/templates/island/likeButton.png"),
	
    // ========================================================================
    // MERCHANTS AND SHOPS
    // ========================================================================
	NOMADIC_MERCHANT_COAL("/templates/nomadicmerchant/coal.png"), 
	NOMADIC_MERCHANT_WOOD("/templates/nomadicmerchant/wood.png"), 
	NOMADIC_MERCHANT_MEAT("/templates/nomadicmerchant/meat.png"), 
	NOMADIC_MERCHANT_STONE("/templates/nomadicmerchant/stone.png"),
	NOMADIC_MERCHANT_VIP("/templates/nomadicmerchant/vip.png"),
	NOMADIC_MERCHANT_REFRESH("/templates/nomadicmerchant/refresh.png"),
	
	SHOP_MYSTERY_BUTTON("/templates/shop/mysteryShopButton.png"),
	MYSTERY_SHOP_FREE_REWARD("/templates/shop/mysteryshop/freeReward.png"),
	MYSTERY_SHOP_DAILY_REFRESH("/templates/shop/mysteryshop/dailyRefresh.png"),
	MYSTERY_SHOP_250_BADGES_BUTTON("/templates/shop/mysteryshop/250BadgesButton.png"),
	MYSTERY_SHOP_MYTHIC_SHARDS_BUTTON("/templates/shop/mysteryshop/mysteryShopMythicShard.png"),
	
    // ========================================================================
    // BANK AND EVENTS
    // ========================================================================
	EVENTS_DEALS_BANK("/templates/events/deals/bank.png"),
	EVENTS_DEALS_BANK_INDEPOSIT("/templates/events/deals/bankInDeposit.png"),
	EVENTS_DEALS_BANK_DEPOSIT("/templates/events/deals/bankDeposit.png"),
	EVENTS_DEALS_BANK_WITHDRAW("/templates/events/deals/bankWithdraw.png"),

	EXPLORATION_CLAIM("/templates/exploration/claim.png"),
	HERO_RECRUIT_CLAIM("/templates/herorecruitment/freebutton.png"),
	
    // ========================================================================
    // LABYRINTH
    // ========================================================================
	LABYRINTH_DUNGEON_1("/templates/labyrinth/dungeon1.png"),
	LABYRINTH_DUNGEON_2("/templates/labyrinth/dungeon2.png"),
	LABYRINTH_DUNGEON_3("/templates/labyrinth/dungeon3.png"),
	LABYRINTH_DUNGEON_4("/templates/labyrinth/dungeon4.png"),
	LABYRINTH_DUNGEON_5("/templates/labyrinth/dungeon5.png"),
	LABYRINTH_DUNGEON_6("/templates/labyrinth/dungeon6.png"),
	
	LABYRINTH_QUICK_CHALLENGE("/templates/labyrinth/quickChallenge.png"),
	LABYRINTH_NORMAL_CHALLENGE("/templates/labyrinth/normalChallenge.png"),
	LABYRINTH_RAID_CHALLENGE("/templates/labyrinth/raidChallenge.png"),
	LABYRINTH_QUICK_DEPLOY("/templates/labyrinth/quickDeploy.png"),
	LABYRINTH_DEPLOY("/templates/labyrinth/deploy.png"),
	
    // ========================================================================
    // VALIDATION TEMPLATES
    // ========================================================================
	VALIDATION_WAR_ACADEMY_UI("/templates/validation/warAcademy.png"),
    VALIDATION_CRYSTAL_LAB_UI("/templates/validation/crystalLab.png"),
	
    // ========================================================================
    // TUNDRA TRUCK AND TREK EVENTS
    // ========================================================================
	TUNDRA_TRUCK_TAB("/templates/tundratruck/tundraTruckTab.png"),
	TUNDRA_TRUCK_ARRIVED("/templates/tundratruck/tundraTruckArrived.png"),
	TUNDRA_TRUCK_YELLOW("/templates/tundratruck/tundraTruckLegendary.png"),
	TUNDRA_TRUCK_PURPLE("/templates/tundratruck/tundraTruckEpic.png"),
	TUNDRA_TRUCK_BLUE("/templates/tundratruck/tundraTruckRare.png"),
	TUNDRA_TRUCK_GREEN("/templates/tundratruck/tundraTruckNormal.png"),
	TUNDRA_TRUCK_REFRESH("/templates/tundratruck/tundraTruckRefresh.png"),
	TUNDRA_TRUCK_REFRESH_GEMS("/templates/tundratruck/tundraTruckRefreshGems.png"),
	TUNDRA_TRUCK_TIPS_POPUP("/templates/tundratruck/tundraTruckTipsPopup.png"),
	TUNDRA_TRUCK_YELLOW_RAID("/templates/tundratruck/tundraTruckLegendaryRaid.png"),
	TUNDRA_TRUCK_ESCORT("/templates/tundratruck/tundraTruckEscort.png"),
	TUNDRA_TRUCK_DEPARTED("/templates/tundratruck/tundraTruckDeparted.png"),
	TUNDRA_TRUCK_ENDED("/templates/tundratruck/tundraTruckEnded.png"),
	
	TUNDRA_TREK_SUPPLIES("/templates/tundratrek/trekSupplies.png"),
	TUNDRA_TREK_CLAIM_BUTTON("/templates/tundratrek/trekClaimButton.png"),
	TUNDRA_TREK_AUTO_BUTTON("/templates/tundratrek/autoTrek.png"),
	TUNDRA_TREK_BAG_BUTTON("/templates/tundratrek/bagTrek.png"),
	TUNDRA_TREK_SKIP_BUTTON("/templates/tundratrek/skipTrek.png"),
	TUNDRA_TREK_BLUE_BUTTON("/templates/tundratrek/bluebuttonTrek.png"),
	TUNDRA_TREK_CHECK_ACTIVE("/templates/tundratrek/checkactiveTrek.png"),
	TUNDRA_TREK_CHECK_INACTIVE("/templates/tundratrek/checkinactiveTrek.png"),
	
    // ========================================================================
    // TUNDRA TRUCK AND TREK EVENTS
    // ========================================================================
    JOURNEY_OF_LIGHT_TAB("/templates/events/journeyoflight/journeyOfLightTab.png"),
    JOURNEY_OF_LIGHT_UNSELECTED_TAB("/templates/events/journeyoflight/journeyOfLightUnselected.png"),
    JOURNEY_OF_LIGHT_FREE_WATCHES("/templates/events/journeyoflight/freeWatch.png"),
    JOURNEY_OF_LIGHT_CLAIM_WATCHES("/templates/events/journeyoflight/freeWatchAvailable.png"),

    // ========================================================================
    // EXPERTS AND MERCENARY EVENT
    // ========================================================================
	ROMULUS_CLAIM_TROOPS_BUTTON("/templates/experts/romulusClaimTroopsButton.png"),
	ROMULUS_CLAIM_TAG_BUTTON("/templates/experts/romulusClaimTagButton.png"),
	AGNES_CLAIM_INTEL("/templates/experts/intelAgnes.png"),

    EXPERT_TRAINING_SPEEDUP_ICON("/templates/experts/expertTrainingSpeedupIcon.png"),
    EXPERT_TRAINING_CYRILLE_BADGE("/templates/experts/cyrilleBadge.png"),
    EXPERT_TRAINING_AGNES_BADGE("/templates/experts/agnesBadge.png"),
    EXPERT_TRAINING_ROMULUS_BADGE("/templates/experts/romulusBadge.png"),
    EXPERT_TRAINING_HOLGER_BADGE("/templates/experts/holgerBadge.png"),
    EXPERT_TRAINING_BALDUR_BADGE("/templates/experts/baldurBadge.png"),
    EXPERT_TRAINING_FABIAN_BADGE("/templates/experts/fabianBadge.png"),
    EXPERT_TRAINING_LEARN_BUTTON("/templates/experts/learnButton.png"),

	
	MERCENARY_EVENT_TAB("/templates/mercenary/mercenaryEventTab.png"),
	MERCENARY_SCOUT_BUTTON("/templates/mercenary/mercenaryEventScout.png"),
	MERCENARY_CHALLENGE_BUTTON("/templates/mercenary/mercenaryEventChallenge.png"),
	MERCENARY_ATTACK_BUTTON("/templates/mercenary/mercenaryEventAttack.png"),
	MERCENARY_DEPLOY_BUTTON("/templates/mercenary/mercenaryEventDeploy.png"),
	MERCENARY_DIFFICULTY_CHALLENGE("/templates/mercenary/mercenaryEventDifficultyChallenge.png"),
	MERCENARY_EPIC_INITIATION_SELECTED("/templates/mercenary/mercenaryEventEpicInitiationSelected.png"),
	MERCENARY_EPIC_INITIATION_UNSELECTED("/templates/mercenary/mercenaryEventEpicInitiationUnselected.png"),
	MERCENARY_CHAMPIONS_INITIATION_SELECTED("/templates/mercenary/mercenaryEventChampionsInitiationSelected.png"),
	MERCENARY_CHAMPIONS_INITIATION_UNSELECTED("/templates/mercenary/mercenaryEventChampionsInitiationUnselected.png"),
	MERCENARY_LEGENDS_INITIATION_SELECTED("/templates/mercenary/mercenaryEventLegendsInitiationSelected.png"),
	MERCENARY_LEGENDS_INITIATION_UNSELECTED("/templates/mercenary/mercenaryEventLegendsInitiationUnselected.png"),

	// ========================================================================
    // HERO'S MISSION EVENT
    // ========================================================================
	HERO_MISSION_EVENT_TAB("/templates/events/heromission/heroMissionEventTab.png"),
	HERO_MISSION_EVENT_TRACE_BUTTON("/templates/events/heromission/heroMissionEventTraceButton.png"),
	HERO_MISSION_EVENT_CAPTURE_BUTTON("/templates/events/heromission/heroMissionEventCaptureButton.png"),
	HERO_MISSION_EVENT_CHEST("/templates/events/heromission/heroMissionEventChest.png"),

    // ========================================================================
    // POLAR TERROR HUNTING
    // ========================================================================
    POLAR_TERROR_SEARCH_ICON("/templates/polarterror/polarTerror.png"),
    POLAR_TERROR_LEVEL_SLIDER("/templates/polarterror/slider.png"),
    POLAR_TERROR_TAB_MAGNIFYING_GLASS_ICON("/templates/polarterror/polarTerrorTabMagnifyingGlass.png"),
    POLAR_TERROR_TAB_SPECIAL_REWARDS("/templates/polarterror/polarTerrorTabSpecialRewards.png"),
    POLAR_TERROR_LEVEL_SELECTOR("/templates/polarterror/polarTerrorLevelSelector.png"),
    POLAR_TERROR_FLAG_SELECTOR("/templates/polarterror/polarTerrorFlagSelector.png"),
    POLAR_TERROR_MODE_SELECTOR("/templates/polarterror/polarTerrorModeSelector.png"),
    POLAR_TERROR_DEPLOY_BUTTON("/templates/polarterror/polarTerrorDeploy.png"),

    // ========================================================================
    // BEAR HUNT EVENT
    // ========================================================================
    BEAR_HUNT_IS_RUNNING("/templates/events/bearhunt/bearIsRunning.png"),
    BEAR_RALLY_BUTTON("/templates/events/bearhunt/bearRallyButton.png"),
    BEAR_DEPLOY_BUTTON("/templates/events/bearhunt/bearDeployButton.png"),
    BEAR_JOIN_PLUS_ICON("/templates/events/bearhunt/bearJoinPlusIcon.png"),
    // ========================================================================
    // MYRIAD BAZAAR
    // ========================================================================
    EVENTS_MYRIAD_BAZAAR_ICON("/templates/myriadbazaar/myriadBazaarIcon.png"),
	
    // ========================================================================
    // CHIEF ORDER
    // ========================================================================
	CHIEF_ORDER_MENU_BUTTON("/templates/chieforder/chiefOrderMenuButton.png"),
	CHIEF_ORDER_RUSH_JOB("/templates/chieforder/chiefOrderRushJob.png"),
	CHIEF_ORDER_URGENT_MOBILISATION("/templates/chieforder/chiefOrderUrgentMobilisation.png"),
	CHIEF_ORDER_PRODUCTIVITY_DAY("/templates/chieforder/chiefOrderProductivityDay.png"),
	CHIEF_ORDER_ENACT_BUTTON("/templates/chieforder/chiefOrderEnactButton.png");

	// @formatter:on
	private final String template;

	EnumTemplates(String template) {
		this.template = template;
	}

	public String getTemplate() {
		return template;
	}
}
