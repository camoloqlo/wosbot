package cl.camodev.wosbot.events.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;

public class EventsLayoutController extends AbstractProfileController {
    @FXML
    private CheckBox checkBoxTundraEvent, checkBoxTundraUseGems, checkBoxTundraSSR, checkBoxHeroMission, checkBoxMercenaryPrestige;

    @FXML
    private void initialize() {
    checkBoxMappings.put(checkBoxTundraEvent, EnumConfigurationKey.TUNDRA_TRUCK_EVENT_BOOL);
    checkBoxMappings.put(checkBoxTundraUseGems, EnumConfigurationKey.TUNDRA_TRUCK_USE_GEMS_BOOL);
    checkBoxMappings.put(checkBoxTundraSSR, EnumConfigurationKey.TUNDRA_TRUCK_SSR_BOOL);
    checkBoxMappings.put(checkBoxHeroMission, EnumConfigurationKey.HERO_MISSION_EVENT_BOOL);
    checkBoxMappings.put(checkBoxMercenaryPrestige, EnumConfigurationKey.MERCENARY_EVENT_BOOL);

        // Hide tundra event options initially
        checkBoxTundraUseGems.setVisible(checkBoxTundraEvent.isSelected());
        checkBoxTundraSSR.setVisible(checkBoxTundraEvent.isSelected());

        // Show/hide tundra event options based on main checkbox
        checkBoxTundraEvent.selectedProperty().addListener((obs, oldVal, newVal) -> {
            checkBoxTundraUseGems.setVisible(newVal);
            checkBoxTundraSSR.setVisible(newVal);
        });

        initializeChangeEvents();
    checkBoxHeroMission.setDisable(true);
    checkBoxMercenaryPrestige.setDisable(false);
    }
}
