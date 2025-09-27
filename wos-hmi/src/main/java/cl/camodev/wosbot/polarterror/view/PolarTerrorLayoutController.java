package cl.camodev.wosbot.polarterror.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;

public class PolarTerrorLayoutController extends AbstractProfileController {

    @FXML
    private CheckBox checkBoxEnablePolarTerror;

    @FXML
    private ComboBox<Integer> comboBoxPolarTerrorLevel;

    @FXML
    private ComboBox<String> comboBoxPolarTerrorFlag;

    @FXML
    private ComboBox<String> comboBoxPolarTerrorMode;

    @FXML
    private void initialize() {
        // Set up CheckBox mappings
        checkBoxMappings.put(checkBoxEnablePolarTerror, EnumConfigurationKey.POLAR_TERROR_ENABLED_BOOL);

        // Initialize ComboBox values


        // Inicializa el combobox de Flag con "No Flag" (0) y las opciones numéricas
        comboBoxPolarTerrorFlag.getItems().add("No Flag");
        for (int i = 1; i <= 8; i++) {
            comboBoxPolarTerrorFlag.getItems().add(String.valueOf(i));
            comboBoxPolarTerrorLevel.getItems().addAll(i);
        }

        comboBoxPolarTerrorMode.getItems().addAll("Limited (10)", "Unlimited");

        // Set up ComboBox mappings
        comboBoxMappings.put(comboBoxPolarTerrorLevel, EnumConfigurationKey.POLAR_TERROR_LEVEL_INT);
        comboBoxMappings.put(comboBoxPolarTerrorFlag, EnumConfigurationKey.POLAR_TERROR_FLAG_STRING);
        comboBoxMappings.put(comboBoxPolarTerrorMode, EnumConfigurationKey.POLAR_TERROR_MODE_STRING);

        // Initialize change events for automatically saving when values change
        initializeChangeEvents();
    }
}
