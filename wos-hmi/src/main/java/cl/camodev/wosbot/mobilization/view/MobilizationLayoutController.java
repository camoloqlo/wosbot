package cl.camodev.wosbot.mobilization.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class MobilizationLayoutController extends AbstractProfileController {

    @FXML
    private CheckBox checkBoxAllianceMobilization;

    @FXML
    private ComboBox<String> rewardsPercentageCombo;

    @FXML
    private CheckBox buildSpeedupsCheck;
    @FXML
    private CheckBox buyPackageCheck;
    @FXML
    private CheckBox chiefGearCharmCheck;
    @FXML
    private CheckBox chiefGearScoreCheck;
    @FXML
    private CheckBox defeatBeastsCheck;
    @FXML
    private CheckBox fireCrystalCheck;
    @FXML
    private CheckBox gatherResourcesCheck;
    @FXML
    private CheckBox heroGearStoneCheck;
    @FXML
    private CheckBox mythicShardCheck;
    @FXML
    private CheckBox rallyCheck;
    @FXML
    private CheckBox trainTroopsCheck;
    @FXML
    private CheckBox trainingSpeedupsCheck;
    @FXML
    private CheckBox useGemsCheck;
    @FXML
    private CheckBox useSpeedupsCheck;

    @FXML
    private TextField minimumPoints200Field;
    @FXML
    private TextField minimumPoints120Field;
    @FXML
    private CheckBox autoAcceptCheck;
    @FXML
    private CheckBox useGemsBottomCheck;

    @FXML
    private void initialize() {
        // Map the main activation checkbox
        checkBoxMappings.put(checkBoxAllianceMobilization, EnumConfigurationKey.ALLIANCE_MOBILIZATION_BOOL);

        // Map task type checkboxes
        checkBoxMappings.put(buildSpeedupsCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_BUILD_SPEEDUPS_BOOL);
        checkBoxMappings.put(buyPackageCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_BUY_PACKAGE_BOOL);
        checkBoxMappings.put(chiefGearCharmCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_CHIEF_GEAR_CHARM_BOOL);
        checkBoxMappings.put(chiefGearScoreCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_CHIEF_GEAR_SCORE_BOOL);
        checkBoxMappings.put(defeatBeastsCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_DEFEAT_BEASTS_BOOL);
        checkBoxMappings.put(fireCrystalCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_FIRE_CRYSTAL_BOOL);
        checkBoxMappings.put(gatherResourcesCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_GATHER_RESOURCES_BOOL);
        checkBoxMappings.put(heroGearStoneCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_HERO_GEAR_STONE_BOOL);
        checkBoxMappings.put(mythicShardCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_MYTHIC_SHARD_BOOL);
        checkBoxMappings.put(rallyCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_RALLY_BOOL);
        checkBoxMappings.put(trainTroopsCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_TRAIN_TROOPS_BOOL);
        checkBoxMappings.put(trainingSpeedupsCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_TRAINING_SPEEDUPS_BOOL);
        checkBoxMappings.put(useGemsCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_USE_GEMS_BOOL);
        checkBoxMappings.put(useSpeedupsCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_USE_SPEEDUPS_BOOL);
        checkBoxMappings.put(autoAcceptCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_AUTO_ACCEPT_BOOL);
        checkBoxMappings.put(useGemsBottomCheck, EnumConfigurationKey.ALLIANCE_MOBILIZATION_USE_GEMS_FOR_ACCEPT_BOOL);

        // Map text fields
    textFieldMappings.put(minimumPoints200Field, EnumConfigurationKey.ALLIANCE_MOBILIZATION_MINIMUM_POINTS_200_INT);
    textFieldMappings.put(minimumPoints120Field, EnumConfigurationKey.ALLIANCE_MOBILIZATION_MINIMUM_POINTS_120_INT);

        // Map combo box
        comboBoxMappings.put(rewardsPercentageCombo, EnumConfigurationKey.ALLIANCE_MOBILIZATION_REWARDS_PERCENTAGE_STRING);

        // Initialize change events for all checkboxes
        initializeChangeEvents();
    }
}
