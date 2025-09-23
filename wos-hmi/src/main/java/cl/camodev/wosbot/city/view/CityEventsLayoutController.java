package cl.camodev.wosbot.city.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class CityEventsLayoutController extends AbstractProfileController {

	@FXML
	private CheckBox checkBoxCrystalLabFC, checkBoxDailyDiscountedRFC;

	@FXML
	private CheckBox checkBoxDailyVipRewards;

	@FXML
	private CheckBox checkBoxExplorationChest;

	@FXML
	private CheckBox checkBoxHeroRecruitment;

	@FXML
	private CheckBox checkBoxMailRewards;

	@FXML
	private CheckBox checkBoxLifeEssence;

	@FXML
	private CheckBox checkBoxDailyMission, checkBoxAutoScheduleDailyMission;

	@FXML
	private CheckBox checkBoxDailyLabyrinth;

	@FXML
	private CheckBox checkBoxTrekSupplies;

	@FXML
	private CheckBox checkBoxTrekAutomation;

	@FXML
	private CheckBox checkBoxStorehouseChest;

	@FXML
	private CheckBox checkBoxWarAcademyShards;

	@FXML
	private CheckBox checkBoxBuyMonthlyVip;

	@FXML
	private TextField textfieldExplorationOffset;

	@FXML
	private TextField textfieldMailOffset;

	@FXML
	private TextField textfieldLifeEssenceOffset;

	@FXML
	private TextField textfieldDailyMissionOffset;

    @FXML
    private ComboBox<Integer> comboBoxMondayRefinements;

	@FXML
	private void initialize() {

		checkBoxMappings.put(checkBoxCrystalLabFC, EnumConfigurationKey.BOOL_CRYSTAL_LAB_FC);
        checkBoxMappings.put(checkBoxDailyDiscountedRFC, EnumConfigurationKey.BOOL_CRYSTAL_LAB_DAILY_DISCOUNTED_RFC);
		checkBoxMappings.put(checkBoxExplorationChest, EnumConfigurationKey.BOOL_EXPLORATION_CHEST);
		checkBoxMappings.put(checkBoxHeroRecruitment, EnumConfigurationKey.BOOL_HERO_RECRUITMENT);
		checkBoxMappings.put(checkBoxWarAcademyShards, EnumConfigurationKey.BOOL_WAR_ACADEMY_SHARDS);
		checkBoxMappings.put(checkBoxDailyVipRewards, EnumConfigurationKey.BOOL_VIP_POINTS);
		checkBoxMappings.put(checkBoxMailRewards, EnumConfigurationKey.MAIL_REWARDS_BOOL);
		checkBoxMappings.put(checkBoxBuyMonthlyVip, EnumConfigurationKey.VIP_BUY_MONTHLY);
		checkBoxMappings.put(checkBoxLifeEssence, EnumConfigurationKey.LIFE_ESSENCE_BOOL);
		checkBoxMappings.put(checkBoxDailyMission, EnumConfigurationKey.DAILY_MISSION_BOOL);
		checkBoxMappings.put(checkBoxAutoScheduleDailyMission, EnumConfigurationKey.DAILY_MISSION_AUTO_SCHEDULE_BOOL);
		checkBoxMappings.put(checkBoxDailyLabyrinth, EnumConfigurationKey.DAILY_LABYRINTH_BOOL);
	checkBoxMappings.put(checkBoxTrekSupplies, EnumConfigurationKey.TUNDRA_TREK_SUPPLIES_BOOL);
	checkBoxMappings.put(checkBoxTrekAutomation, EnumConfigurationKey.TUNDRA_TREK_AUTOMATION_BOOL);
		checkBoxMappings.put(checkBoxStorehouseChest, EnumConfigurationKey.STOREHOUSE_CHEST_BOOL);

		textFieldMappings.put(textfieldExplorationOffset, EnumConfigurationKey.INT_EXPLORATION_CHEST_OFFSET);
		textFieldMappings.put(textfieldMailOffset, EnumConfigurationKey.MAIL_REWARDS_OFFSET_INT);
		textFieldMappings.put(textfieldLifeEssenceOffset, EnumConfigurationKey.LIFE_ESSENCE_OFFSET_INT);
		textFieldMappings.put(textfieldDailyMissionOffset, EnumConfigurationKey.DAILY_MISSION_OFFSET_INT);

        comboBoxMondayRefinements.getItems().addAll(0 ,20, 40, 60);
        comboBoxMappings.put(comboBoxMondayRefinements, EnumConfigurationKey.INT_WEEKLY_RFC);

		initializeChangeEvents();
	}
}
