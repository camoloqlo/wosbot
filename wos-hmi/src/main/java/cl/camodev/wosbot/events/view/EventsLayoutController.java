package cl.camodev.wosbot.events.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class EventsLayoutController extends AbstractProfileController {
    @FXML
    private CheckBox checkBoxTundraEvent, checkBoxTundraUseGems, checkBoxTundraSSR, checkBoxHeroMission, 
                     checkBoxMercenaryEvent, checkBoxMercenaryUseFlag, checkBoxJourneyofLight;
    
    @FXML
    private TextField textfieldTundraActivationHour;
    
    @FXML
    private Label labelTundraActivationHour;

    @FXML
    private HBox hboxMercenaryFlagSelection;
    
    @FXML
    private ComboBox<Integer> comboBoxMercenaryFlag;

    @FXML
    private void initialize() {
        // Bind use flag visibility to checkbox
        checkBoxMercenaryUseFlag.visibleProperty().bind(checkBoxMercenaryEvent.selectedProperty());

        // Bind flag selection visibility to checkbox
        hboxMercenaryFlagSelection.visibleProperty().bind(checkBoxMercenaryUseFlag.selectedProperty());
        
        // Set up flag combobox with integer values
        comboBoxMercenaryFlag.getItems().addAll(1, 2, 3, 4, 5, 6, 7, 8);
        
        // Map UI elements to configuration keys
        comboBoxMappings.put(comboBoxMercenaryFlag, EnumConfigurationKey.MERCENARY_FLAG_INT);
        checkBoxMappings.put(checkBoxTundraEvent, EnumConfigurationKey.TUNDRA_TRUCK_EVENT_BOOL);
        checkBoxMappings.put(checkBoxTundraUseGems, EnumConfigurationKey.TUNDRA_TRUCK_USE_GEMS_BOOL);
        checkBoxMappings.put(checkBoxTundraSSR, EnumConfigurationKey.TUNDRA_TRUCK_SSR_BOOL);
        checkBoxMappings.put(checkBoxHeroMission, EnumConfigurationKey.HERO_MISSION_EVENT_BOOL);
        checkBoxMappings.put(checkBoxMercenaryEvent, EnumConfigurationKey.MERCENARY_EVENT_BOOL);
        checkBoxMappings.put(checkBoxMercenaryUseFlag, EnumConfigurationKey.MERCENARY_USE_FLAG_BOOL);
        checkBoxMappings.put(checkBoxJourneyofLight, EnumConfigurationKey.JOURNEY_OF_LIGHT_BOOL);

        // Map the activation hour text field
        textFieldMappings.put(textfieldTundraActivationHour, EnumConfigurationKey.TUNDRA_TRUCK_ACTIVATION_HOUR_INT);
        
        // Set default value (0-23)
        textfieldTundraActivationHour.setText("0");

        // Hide tundra event options initially
        boolean tundraTruckEnabled = checkBoxTundraEvent.isSelected();
        checkBoxTundraUseGems.setVisible(tundraTruckEnabled);
        checkBoxTundraSSR.setVisible(tundraTruckEnabled);
        textfieldTundraActivationHour.setVisible(tundraTruckEnabled);
        labelTundraActivationHour.setVisible(tundraTruckEnabled);

        // Show/hide tundra event options based on main checkbox
        checkBoxTundraEvent.selectedProperty().addListener((obs, oldVal, newVal) -> {
            checkBoxTundraUseGems.setVisible(newVal);
            checkBoxTundraSSR.setVisible(newVal);
            textfieldTundraActivationHour.setVisible(newVal);
            labelTundraActivationHour.setVisible(newVal);
        });

        initializeChangeEvents();
    checkBoxHeroMission.setDisable(true);
    // checkBoxMercenaryPrestige.setDisable(false); // Mercenary deferred
    }
}
