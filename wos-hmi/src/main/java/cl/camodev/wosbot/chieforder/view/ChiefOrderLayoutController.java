
package cl.camodev.wosbot.chieforder.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;

public class ChiefOrderLayoutController extends AbstractProfileController {
    @FXML
    private CheckBox checkBoxRushJob;
    @FXML
    private CheckBox checkBoxUrgentMobilisation;
    @FXML
    private CheckBox checkBoxProductivityDay;

    @FXML
    private void initialize() {
        checkBoxMappings.put(checkBoxRushJob, EnumConfigurationKey.BOOL_CHIEF_ORDER_RUSH_JOB);
        checkBoxMappings.put(checkBoxUrgentMobilisation, EnumConfigurationKey.BOOL_CHIEF_ORDER_URGENT_MOBILISATION);
        checkBoxMappings.put(checkBoxProductivityDay, EnumConfigurationKey.BOOL_CHIEF_ORDER_PRODUCTIVITY_DAY);
        initializeChangeEvents();
    }

    public boolean isRushJobEnabled() {
        return checkBoxRushJob.isSelected();
    }
    public boolean isUrgentMobilisationEnabled() {
        return checkBoxUrgentMobilisation.isSelected();
    }
    public boolean isProductivityDayEnabled() {
        return checkBoxProductivityDay.isSelected();
    }
}
