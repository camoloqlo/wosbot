package cl.camodev.wosbot.training.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import java.util.Arrays;
import java.util.List;

public class TrainingLayoutController extends AbstractProfileController {

	@FXML
	private CheckBox checkBoxEnableTraining;

	@FXML
	private CheckBox checkBoxAppointMinister;

	@FXML
	private CheckBox checkBoxTrainPrioritizePromotion;

	@FXML
	private CheckBox checkBoxTrainInfantry;

	@FXML
	private CheckBox checkBoxTrainLancers;

	@FXML
	private CheckBox checkBoxTrainMarksman;

	@FXML
	private CheckBox checkBoxUseResources;

	// Lista de todos los checkboxes hijos
	private List<CheckBox> childCheckboxes;

	@FXML
	private void initialize() {
		checkBoxMappings.put(checkBoxEnableTraining, EnumConfigurationKey.TRAIN_BOOL);
		checkBoxMappings.put(checkBoxTrainInfantry, EnumConfigurationKey.TRAIN_INFANTRY_BOOL);
		checkBoxMappings.put(checkBoxTrainLancers, EnumConfigurationKey.TRAIN_LANCER_BOOL);
		checkBoxMappings.put(checkBoxTrainMarksman, EnumConfigurationKey.TRAIN_MARKSMAN_BOOL);
		checkBoxMappings.put(checkBoxTrainPrioritizePromotion, EnumConfigurationKey.TRAIN_PRIORITIZE_PROMOTION_BOOL);
		checkBoxMappings.put(checkBoxAppointMinister, EnumConfigurationKey.TRAIN_MINISTRY_APPOINTMENT_BOOL);
		checkBoxMappings.put(checkBoxUseResources, EnumConfigurationKey.BOOL_TRAINING_RESOURCES);

		initializeChangeEvents();

		// Inicializar la lista de checkboxes hijos
		childCheckboxes = Arrays.asList(
				checkBoxTrainInfantry,
				checkBoxTrainLancers,
				checkBoxTrainMarksman,
				checkBoxAppointMinister,
				checkBoxUseResources,
				checkBoxTrainPrioritizePromotion);

		// Configurar el listener para el checkbox principal
		checkBoxEnableTraining.selectedProperty().addListener((observable, oldValue, newValue) -> {
			updateChildrenState(newValue);
		});

		// Establecer estado inicial basado en el estado actual del checkbox principal
		updateChildrenState(checkBoxEnableTraining.isSelected());
	}

	/**
	 * Actualiza el estado de habilitación de todos los checkboxes hijos
	 * 
	 * @param enabled estado de habilitación a aplicar
	 */
	private void updateChildrenState(boolean enabled) {
		for (CheckBox child : childCheckboxes) {
			child.setDisable(!enabled);
		}
	}
}
