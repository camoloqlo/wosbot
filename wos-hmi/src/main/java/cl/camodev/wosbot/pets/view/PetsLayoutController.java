package cl.camodev.wosbot.pets.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;

public class PetsLayoutController extends AbstractProfileController {
	@FXML
	private CheckBox checkBoxPetAllianceTreasure;

	@FXML
	private CheckBox checkBoxPetPersonalTreasure;

	@FXML
	private CheckBox checkboxFoodSkill;

	@FXML
	private CheckBox checkboxGatheringSkill;

	@FXML
	private CheckBox checkboxStaminaSkill;

	@FXML
	private CheckBox checkboxTreasureSkill;

	@FXML
	private void initialize() {
		checkBoxMappings.put(checkBoxPetAllianceTreasure, EnumConfigurationKey.ALLIANCE_PET_TREASURE_BOOL);
		checkBoxMappings.put(checkBoxPetPersonalTreasure, EnumConfigurationKey.PET_PERSONAL_TREASURE_BOOL);
		checkBoxMappings.put(checkboxFoodSkill, EnumConfigurationKey.PET_SKILL_FOOD_BOOL);
		checkBoxMappings.put(checkboxGatheringSkill, EnumConfigurationKey.PET_SKILL_GATHERING_BOOL);
		checkBoxMappings.put(checkboxStaminaSkill, EnumConfigurationKey.PET_SKILL_STAMINA_BOOL);
		checkBoxMappings.put(checkboxTreasureSkill, EnumConfigurationKey.PET_SKILL_TRESURE_BOOL);

		initializeChangeEvents();
		checkboxGatheringSkill.setDisable(false);

	}

}
