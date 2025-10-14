package cl.camodev.wosbot.alliancechampionship.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class AllianceChampionshipLayoutController extends AbstractProfileController {

	@FXML
	private CheckBox checkBoxEnableChampionship, checkBoxOverrideCurrentDeploy;

	@FXML
	private TextField textfieldInfantryPercentage, textfieldLancersPercentage, textfieldMarksmansPercentage;

	@FXML
	private ComboBox<String> comboBoxPosition;

	@FXML
	private void initialize() {
		// Map checkboxes to their configuration keys
		checkBoxMappings.put(checkBoxEnableChampionship, EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_BOOL);
		checkBoxMappings.put(checkBoxOverrideCurrentDeploy, EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_OVERRIDE_DEPLOY_BOOL);

		// Map text fields to their configuration keys
		textFieldMappings.put(textfieldInfantryPercentage, EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_INFANTRY_PERCENTAGE_INT);
		textFieldMappings.put(textfieldLancersPercentage, EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_LANCERS_PERCENTAGE_INT);
		textFieldMappings.put(textfieldMarksmansPercentage, EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_MARKSMANS_PERCENTAGE_INT);

		// Initialize ComboBox with position options
		comboBoxPosition.getItems().addAll("LEFT", "CENTER", "RIGHT");

		// Map ComboBox to configuration key
		comboBoxMappings.put(comboBoxPosition, EnumConfigurationKey.ALLIANCE_CHAMPIONSHIP_POSITION_STRING);

		checkBoxOverrideCurrentDeploy.disableProperty().bind(checkBoxEnableChampionship.selectedProperty().not());
		textfieldInfantryPercentage.disableProperty().bind(checkBoxEnableChampionship.selectedProperty().not());
		textfieldLancersPercentage.disableProperty().bind(checkBoxEnableChampionship.selectedProperty().not());
		textfieldMarksmansPercentage.disableProperty().bind(checkBoxEnableChampionship.selectedProperty().not());
		comboBoxPosition.disableProperty().bind(checkBoxEnableChampionship.selectedProperty().not());

		initializeChangeEvents();
	}
}
