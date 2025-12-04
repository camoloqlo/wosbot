package cl.camodev.wosbot.city.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class CityEventsLayoutController extends AbstractProfileController {

	@FXML
	private CheckBox checkBoxCrystalLabFC, checkBoxDailyDiscountedRFC, checkBoxExplorationChest, checkBoxMailRewards,
			checkBoxLifeEssence, checkBoxWeeklyScroll, checkBoxDailyMission, checkBoxAutoScheduleDailyMission,
			checkBoxWarAcademyShards;

	@FXML
	private TextField textfieldExplorationOffset, textfieldMailOffset, textfieldLifeEssenceOffset,
			textfieldDailyMissionOffset;

	@FXML
	private ComboBox<Integer> comboBoxMondayRefinements;

	@FXML
	private void initialize() {

		checkBoxMappings.put(checkBoxCrystalLabFC, EnumConfigurationKey.BOOL_CRYSTAL_LAB_FC);
		checkBoxMappings.put(checkBoxDailyDiscountedRFC, EnumConfigurationKey.BOOL_CRYSTAL_LAB_DAILY_DISCOUNTED_RFC);
		checkBoxMappings.put(checkBoxExplorationChest, EnumConfigurationKey.BOOL_EXPLORATION_CHEST);
		checkBoxMappings.put(checkBoxWarAcademyShards, EnumConfigurationKey.WAR_ACADEMY_TASK_BOOL);
		checkBoxMappings.put(checkBoxMailRewards, EnumConfigurationKey.MAIL_REWARDS_BOOL);
		checkBoxMappings.put(checkBoxLifeEssence, EnumConfigurationKey.LIFE_ESSENCE_BOOL);
		checkBoxMappings.put(checkBoxWeeklyScroll, EnumConfigurationKey.LIFE_ESSENCE_BUY_WEEKLY_SCROLL_BOOL);
		checkBoxMappings.put(checkBoxDailyMission, EnumConfigurationKey.DAILY_MISSION_BOOL);
		checkBoxMappings.put(checkBoxAutoScheduleDailyMission, EnumConfigurationKey.DAILY_MISSION_AUTO_SCHEDULE_BOOL);

		textFieldMappings.put(textfieldExplorationOffset, EnumConfigurationKey.INT_EXPLORATION_CHEST_OFFSET);
		textFieldMappings.put(textfieldMailOffset, EnumConfigurationKey.MAIL_REWARDS_OFFSET_INT);
		textFieldMappings.put(textfieldLifeEssenceOffset, EnumConfigurationKey.LIFE_ESSENCE_OFFSET_INT);
		textFieldMappings.put(textfieldDailyMissionOffset, EnumConfigurationKey.DAILY_MISSION_OFFSET_INT);

		comboBoxMondayRefinements.getItems().addAll(0,14,20,34,40,54,60,74,80,94);
		comboBoxMappings.put(comboBoxMondayRefinements, EnumConfigurationKey.INT_WEEKLY_RFC);

		initializeChangeEvents();
	}
}
