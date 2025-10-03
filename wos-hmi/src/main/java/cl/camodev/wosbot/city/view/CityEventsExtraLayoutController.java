package cl.camodev.wosbot.city.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class CityEventsExtraLayoutController extends AbstractProfileController {

	// Daily tasks controls
	@FXML
	private CheckBox checkBoxDailyVipRewards;
	@FXML
	private CheckBox checkBoxBuyMonthlyVip;
	@FXML
	private CheckBox checkBoxStorehouseChest;
	@FXML
	private CheckBox checkBoxDailyLabyrinth;
	@FXML
	private CheckBox checkBoxHeroRecruitment;

	// Trek related controls
	@FXML
	private CheckBox checkBoxTrekSupplies;
	@FXML
	private CheckBox checkBoxTrekAutomation;

	// Arena related controls
	@FXML
	private CheckBox checkBoxArena;
	@FXML
	private CheckBox checkBoxArenaRefreshWithGems;
	@FXML
	private TextField textFieldArenaActivationHour;
	@FXML
	private ComboBox<Integer> comboBoxArenaExtraAttempts;

	@FXML
	private void initialize() {
		// Daily tasks bindings
		checkBoxMappings.put(checkBoxDailyVipRewards, EnumConfigurationKey.BOOL_VIP_POINTS);
		checkBoxMappings.put(checkBoxBuyMonthlyVip, EnumConfigurationKey.VIP_BUY_MONTHLY);
		checkBoxMappings.put(checkBoxStorehouseChest, EnumConfigurationKey.STOREHOUSE_CHEST_BOOL);
		checkBoxMappings.put(checkBoxDailyLabyrinth, EnumConfigurationKey.DAILY_LABYRINTH_BOOL);
		checkBoxMappings.put(checkBoxHeroRecruitment, EnumConfigurationKey.BOOL_HERO_RECRUITMENT);

		// Trek related bindings
		checkBoxMappings.put(checkBoxTrekSupplies, EnumConfigurationKey.TUNDRA_TREK_SUPPLIES_BOOL);
		checkBoxMappings.put(checkBoxTrekAutomation, EnumConfigurationKey.TUNDRA_TREK_AUTOMATION_BOOL);

		// Arena related bindings
		checkBoxMappings.put(checkBoxArena, EnumConfigurationKey.ARENA_TASK_BOOL);
		checkBoxMappings.put(checkBoxArenaRefreshWithGems, EnumConfigurationKey.ARENA_TASK_REFRESH_WITH_GEMS_BOOL);
		textFieldMappings.put(textFieldArenaActivationHour, EnumConfigurationKey.ARENA_TASK_ACTIVATION_HOUR_STRING);

		// Initialize combo box values (0 to 5 extra attempts)
		comboBoxArenaExtraAttempts.getItems().addAll(0, 1, 2, 3, 4, 5);
		comboBoxMappings.put(comboBoxArenaExtraAttempts, EnumConfigurationKey.ARENA_TASK_EXTRA_ATTEMPTS_INT);

		initializeChangeEvents();
	}
}
