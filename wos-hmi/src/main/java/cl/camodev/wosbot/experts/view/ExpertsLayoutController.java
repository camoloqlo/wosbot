package cl.camodev.wosbot.experts.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.common.view.PriorityListView;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.ExpertSkillItem;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;

public class ExpertsLayoutController extends AbstractProfileController {

    @FXML
    private CheckBox claimIntelCheckBox;

    @FXML
    private CheckBox claimLoyaltyTagCheckBox;

    @FXML
    private CheckBox claimTroopsCheckBox;

    @FXML
    private VBox troopOptionsVBox;

    @FXML
    private ComboBox<String> troopTypeComboBox;

    @FXML
    private CheckBox enableExpertSkillTrainingCheckBox;

    @FXML
    private VBox expertSkillTrainingVBox;

    @FXML
    private PriorityListView expertSkillPriorities;


    @FXML
    public void initialize() {
        setupMappings();
        setupVisibilityListeners();
        initializeChangeEvents();
    }

    private void setupMappings() {
        // Existing mappings
        troopTypeComboBox.getItems().addAll("Infantry", "Lancer", "Marksman");

        checkBoxMappings.put(claimIntelCheckBox, EnumConfigurationKey.EXPERT_AGNES_INTEL_BOOL);
        checkBoxMappings.put(claimLoyaltyTagCheckBox, EnumConfigurationKey.EXPERT_ROMULUS_TAG_BOOL);
        checkBoxMappings.put(claimTroopsCheckBox, EnumConfigurationKey.EXPERT_ROMULUS_TROOPS_BOOL);

        comboBoxMappings.put(troopTypeComboBox, EnumConfigurationKey.EXPERT_ROMULUS_TROOPS_TYPE_STRING);

        // New expert skill training mappings
        checkBoxMappings.put(enableExpertSkillTrainingCheckBox, EnumConfigurationKey.EXPERT_SKILL_TRAINING_ENABLED_BOOL);
        registerPriorityList(expertSkillPriorities, EnumConfigurationKey.EXPERT_SKILL_TRAINING_PRIORITIES_STRING, ExpertSkillItem.class);
    }

    private void setupVisibilityListeners() {
        // Existing listener for troop options
//        claimTroopsCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
//            troopOptionsVBox.setVisible(newValue);
//        });

        // New listener for expert skill training
        enableExpertSkillTrainingCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            expertSkillTrainingVBox.setVisible(newValue);
        });
    }
}
