package cl.camodev.wosbot.intel.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;

public class IntelLayoutController extends AbstractProfileController {

	@FXML
	private CheckBox checkBoxBeast;

	@FXML
	private CheckBox checkBoxFireBeast;

	@FXML
	private CheckBox checkBoxIntel;

	@FXML
	private CheckBox checkBoxJourney;

	@FXML
	private CheckBox checkBoxSurvivors;

	@FXML
	private CheckBox checkBoxFireCrystalEra;

    @FXML
    private CheckBox checkBoxSmartIntel;

	@FXML
	private CheckBox checkBoxRecallGatherTroops;

	@FXML
	private CheckBox checkBoxUseFlag;

	@FXML
	private HBox hboxFlagSelection;

	@FXML
    private ComboBox<Integer> comboBoxBeastFlag;

	@FXML
	private void initialize() {
		hboxFlagSelection.visibleProperty().bind(checkBoxUseFlag.selectedProperty());
		
		comboBoxBeastFlag.getItems().addAll(1, 2, 3, 4, 5, 6, 7, 8);
        comboBoxMappings.put(comboBoxBeastFlag, EnumConfigurationKey.INTEL_BEASTS_FLAG_INT);
		checkBoxMappings.put(checkBoxIntel, EnumConfigurationKey.INTEL_BOOL);
		checkBoxMappings.put(checkBoxFireBeast, EnumConfigurationKey.INTEL_FIRE_BEAST_BOOL);
		checkBoxMappings.put(checkBoxBeast, EnumConfigurationKey.INTEL_BEASTS_BOOL);
		checkBoxMappings.put(checkBoxJourney, EnumConfigurationKey.INTEL_EXPLORATION_BOOL);
		checkBoxMappings.put(checkBoxSurvivors, EnumConfigurationKey.INTEL_CAMP_BOOL);
		checkBoxMappings.put(checkBoxFireCrystalEra, EnumConfigurationKey.INTEL_FC_ERA_BOOL);
		checkBoxMappings.put(checkBoxUseFlag, EnumConfigurationKey.INTEL_USE_FLAG_BOOL);
        checkBoxMappings.put(checkBoxSmartIntel, EnumConfigurationKey.INTEL_SMART_PROCESSING_BOOL);
		checkBoxMappings.put(checkBoxRecallGatherTroops, EnumConfigurationKey.INTEL_RECALL_GATHER_TROOPS_BOOL);
		
		// Make Smart Intel Processing and Recall Gather Troops mutually exclusive
		checkBoxSmartIntel.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
			if (isSelected && checkBoxRecallGatherTroops.isSelected()) {
				checkBoxRecallGatherTroops.setSelected(false);
			}
		});
		
		checkBoxRecallGatherTroops.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
			if (isSelected && checkBoxSmartIntel.isSelected()) {
				checkBoxSmartIntel.setSelected(false);
			}
		});
		
		initializeChangeEvents();

	}

}
