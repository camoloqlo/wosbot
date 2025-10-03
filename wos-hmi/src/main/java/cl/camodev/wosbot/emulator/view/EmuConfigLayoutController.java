package cl.camodev.wosbot.emulator.view;

import java.io.File;
import java.util.HashMap;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.emulator.EmulatorType;
import cl.camodev.wosbot.console.enumerable.GameVersion;
import cl.camodev.wosbot.console.enumerable.IdleBehavior;
import cl.camodev.wosbot.emulator.model.EmulatorAux;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

public class EmuConfigLayoutController {

	@FXML
	private TableView<EmulatorAux> tableviewEmulators;

	@FXML
	private TableColumn<EmulatorAux, Boolean> tableColumnActive;

	@FXML
	private TableColumn<EmulatorAux, String> tableColumnEmulatorName;

	@FXML
	private TableColumn<EmulatorAux, String> tableColumnEmulatorPath;

	@FXML
	private TableColumn<EmulatorAux, Void> tableColumnEmulatorAction;

	@FXML
	private TextField textfieldMaxConcurrentInstances;

	@FXML
	private TextField textfieldMaxIdleTime;

	@FXML
	private ComboBox<GameVersion> comboboxGameVersion;

	@FXML
	private ComboBox<IdleBehavior> comboboxIdleBehavior;

	private final FileChooser fileChooser = new FileChooser();

	// Fixed list of emulators derived from the enum
	private final ObservableList<EmulatorAux> emulatorList = FXCollections.observableArrayList();

	public void initialize() {

		HashMap<String, String> globalConfig = ServConfig.getServices().getGlobalConfig();

		String currentEmulator = globalConfig.get(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name());

		for (EmulatorType type : EmulatorType.values()) {
			String defaultPath = globalConfig.getOrDefault(type.getConfigKey(), "");
			EmulatorAux emulator = new EmulatorAux(type, defaultPath);
			emulator.setActive(type.name().equals(currentEmulator));
			emulatorList.add(emulator);
		}

		// Configure name column (read-only)
		tableColumnEmulatorName.setCellValueFactory(new PropertyValueFactory<>("name"));
		// Configure column that displays the path
		tableColumnEmulatorPath.setCellValueFactory(new PropertyValueFactory<>("path"));

		// Configure the selection column with a RadioButton to choose the active emulator
		tableColumnActive.setCellValueFactory(cellData -> cellData.getValue().activeProperty());

		final ToggleGroup toggleGroup = new ToggleGroup();
		tableColumnActive.setCellFactory(column -> new TableCell<EmulatorAux, Boolean>() {
			private final RadioButton radioButton = new RadioButton();
			{
				radioButton.setToggleGroup(toggleGroup);
				radioButton.setOnAction(event -> {
					EmulatorAux selected = getTableView().getItems().get(getIndex());
					// Deactivates the active flag on all
					for (EmulatorAux e : emulatorList) {
						e.setActive(false);
					}
					selected.setActive(true);
					tableviewEmulators.refresh();
				});
			}

			@Override
			protected void updateItem(Boolean item, boolean empty) {
				super.updateItem(item, empty);
				if (empty) {
					setGraphic(null);
				} else {
					radioButton.setSelected(item != null && item);
					setGraphic(radioButton);
				}
			}
		});

		// Configure the action column to update the path
		tableColumnEmulatorAction.setCellFactory(col -> new TableCell<EmulatorAux, Void>() {
			private final Button btn = new Button("...");

			{
				btn.setOnAction(event -> {
					EmulatorAux emulator = getTableView().getItems().get(getIndex());
					// The executableName can be used to filter or validate the file
					File selectedFile = openFileChooser("Select" + emulator.getEmulatorType().getExecutableName());
					if (selectedFile != null) {
						// Verifies that the selected file matches the expected one
						if (!selectedFile.getName().equalsIgnoreCase(emulator.getEmulatorType().getExecutableName())) {
							showError("File not valid, please select: " + emulator.getEmulatorType().getExecutableName());
							return;
						}
						emulator.setPath(selectedFile.getParent());
						tableviewEmulators.refresh();
					}
				});
			}

			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);
				setGraphic(empty ? null : btn);
			}
		});

		// Assign the fixed list to the TableView
		tableviewEmulators.setItems(emulatorList);

		textfieldMaxConcurrentInstances.setText(globalConfig.getOrDefault(EnumConfigurationKey.MAX_RUNNING_EMULATORS_INT.name(), "1"));
		textfieldMaxIdleTime.setText(globalConfig.getOrDefault(EnumConfigurationKey.MAX_IDLE_TIME_INT.name(), "15"));

		comboboxGameVersion.setItems(FXCollections.observableArrayList(GameVersion.values()));
		String gameVersionName = globalConfig.getOrDefault(EnumConfigurationKey.GAME_VERSION_STRING.name(), GameVersion.GLOBAL.name());
		comboboxGameVersion.setValue(GameVersion.valueOf(gameVersionName));

		// Initialize the idle behavior combobox
		comboboxIdleBehavior.setItems(FXCollections.observableArrayList(IdleBehavior.values()));
		boolean idleBehaviorSendToBackground = Boolean.parseBoolean(globalConfig.getOrDefault(EnumConfigurationKey.IDLE_BEHAVIOR_SEND_TO_BACKGROUND_BOOL.name(), "false"));
		comboboxIdleBehavior.setValue(IdleBehavior.fromBoolean(idleBehaviorSendToBackground));
		
		// Add listener to show warning when "Close Game" is selected
		comboboxIdleBehavior.setOnAction(event -> {
			IdleBehavior selectedBehavior = comboboxIdleBehavior.getValue();
			ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.IDLE_BEHAVIOR_SEND_TO_BACKGROUND_BOOL.name(), selectedBehavior.shouldSendToBackground() ? "true" : "false");
			if (selectedBehavior != null && selectedBehavior.shouldSendToBackground()) {
				showConcurrentInstanceWarning();
			}
		});
	}

	// Saves the configuration, iterating through the list to extract the path and determine the active emulator
	@FXML
	private void handleSaveConfiguration() {
		String activeEmulatorName = null;
		for (EmulatorAux emulator : emulatorList) {
			if (emulator.isActive()) {
				activeEmulatorName = emulator.getEmulatorType().name();
				break;
			}
		}
		if (activeEmulatorName == null) {
			showError("Missing active emulator. Please select one.");
			return;
		}

		// Saves the maximum number of instances
		String maxInstances = textfieldMaxConcurrentInstances.getText();
		if (maxInstances.isEmpty()) {
			showError("Max instances cannot be empty.");
			return;
		}

		// Saves the maximum idle time
		String maxIdleTime = textfieldMaxIdleTime.getText();
		if (maxIdleTime.isEmpty()) {
			showError("Max idle time cannot be empty.");
			return;
		}
		// Saves the configuration using the key defined in each enum value
		for (EmulatorAux emulator : emulatorList) {
			ServScheduler.getServices().saveEmulatorPath(emulator.getEmulatorType().getConfigKey(), emulator.getPath());
		}

		GameVersion selectedGameVersion = comboboxGameVersion.getValue();
		if (selectedGameVersion != null) {
			ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.GAME_VERSION_STRING.name(), selectedGameVersion.name());
		}

		ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.MAX_IDLE_TIME_INT.name(), maxIdleTime);
		ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.MAX_RUNNING_EMULATORS_INT.name(), maxInstances);
		ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name(), activeEmulatorName);
			showInfo("Config saved successfully");
	}

	private File openFileChooser(String title) {
		fileChooser.setTitle(title);
		fileChooser.getExtensionFilters().clear();
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Executable Files", "*.exe"));
		return fileChooser.showOpenDialog(null);
	}

	private void showConcurrentInstanceWarning() {
		// Get current max concurrent instances value
		String maxInstancesText = textfieldMaxConcurrentInstances.getText();
		int maxInstances = 1;
		try {
			maxInstances = Integer.parseInt(maxInstancesText);
		} catch (NumberFormatException e) {
			// Use default value if parsing fails
		}
		
		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle("Important: Concurrent Instance Requirement");
		alert.setHeaderText("Close Game Option Selected");
		alert.setContentText(
			"You have selected 'Close Game' behavior which keeps emulators running during idle periods.\n\n" +
			"IMPORTANT: Make sure you have enough concurrent emulator instances (" + maxInstances + ") " +
			"to handle all your active profiles simultaneously. If you have more profiles than concurrent " +
			"instances, some profiles won't be able to run.\n\n" +
			"Consider:\n" +
			"• Increasing 'Max Concurrent Instances' if needed\n" +
			"• Using 'Close Emulator' if you have limited system resources"
		);
		alert.showAndWait();
	}

	private void showError(String message) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle("Error");
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
	}

	private void showInfo(String message) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Success");
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
	}
}
