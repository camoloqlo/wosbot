package cl.camodev.wosbot.console.view;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import cl.camodev.wosbot.console.controller.ConsoleLogActionController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.model.LogMessageAux;
import cl.camodev.wosbot.ot.DTOLogMessage;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.IProfileDataChangeListener;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.text.Text;

public class ConsoleLogLayoutController implements IProfileDataChangeListener {

	@FXML
	private Button buttonClearLogs;
	
	@FXML
	private Button buttonOpenLogFolder;

	@FXML
	private CheckBox checkboxDebug;

	@FXML
	private ComboBox<String> comboBoxProfileFilter;

	@FXML
	private Button buttonResetProfileFilter;

	@FXML
	private TableView<LogMessageAux> tableviewLogMessages;

	@FXML
	private TableColumn<LogMessageAux, String> columnMessage;

	@FXML
	private TableColumn<LogMessageAux, String> columnTimeStamp;

	@FXML
	private TableColumn<LogMessageAux, String> columnProfile;

	@FXML
	private TableColumn<LogMessageAux, String> columnTask;

	@FXML
	private TableColumn<LogMessageAux, String> columnLevel;

	private ObservableList<LogMessageAux> logMessages;
	private FilteredList<LogMessageAux> filteredLogMessages;

	@FXML
	private void initialize() {
		new ConsoleLogActionController(this);
		logMessages = FXCollections.observableArrayList();
		filteredLogMessages = new FilteredList<>(logMessages);

        checkboxDebug.setSelected(Optional
                .ofNullable(ServConfig.getServices().getGlobalConfig())
                .map(cfg -> cfg.get(EnumConfigurationKey.BOOL_DEBUG.name()))
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(EnumConfigurationKey.BOOL_DEBUG.getDefaultValue())));

        checkboxDebug.setOnAction(e -> {
            ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.BOOL_DEBUG.name(), String.valueOf(checkboxDebug.isSelected()));
        });
		
		columnTimeStamp.setCellValueFactory(cellData -> cellData.getValue().timeStampProperty());
		columnMessage.setCellValueFactory(cellData -> cellData.getValue().messageProperty());
		columnLevel.setCellValueFactory(cellData -> cellData.getValue().severityProperty());
		columnTask.setCellValueFactory(cellData -> cellData.getValue().taskProperty());
		columnProfile.setCellValueFactory(cellData -> cellData.getValue().profileProperty());
		
		columnMessage.setCellFactory(column -> {
			return new TableCell<>() {
				private final Text text = new Text();

				{
					setGraphic(text);
					text.wrappingWidthProperty().bind(widthProperty());
					text.fillProperty().bind(textFillProperty()); // Inherit text color
					setPrefHeight(USE_COMPUTED_SIZE);
				}

				@Override
				protected void updateItem(String item, boolean empty) {
					super.updateItem(item, empty);
					if (empty || item == null) {
						text.setText(null);
					} else {
						text.setText(item);
					}
				}
			};
		});
		
		tableviewLogMessages.setItems(filteredLogMessages);

		// Botón para agregar datos (simula nuevos mensajes)
		tableviewLogMessages.setPlaceholder(new Label("NO LOGS"));
		
		// Initialize profile filter
		initializeProfileFilter();
		
		// Set up filter listeners
		setupFilterListeners();
		ServProfiles.getServices().addProfileDataChangeListener(this);
	}

	@FXML
	void handleButtonClearLogs(ActionEvent event) {
		Platform.runLater(() -> {
			logMessages.clear();
		});
	}

	@FXML
	void handleButtonResetProfileFilter(ActionEvent event) {
		comboBoxProfileFilter.getSelectionModel().selectFirst(); // Select "All profiles"
		updateLogFilter();
	}
	
	@FXML
	void handleButtonOpenLogFolder(ActionEvent event) {
		try {
			// Path to logs folder - application logs are stored in the "log" directory
			File logsDir = new File("log");
			if (!logsDir.exists()) {
				// Create logs directory if it doesn't exist
				logsDir.mkdirs();
			}
			
			// Open the logs directory
			Desktop.getDesktop().open(logsDir);
		} catch (IOException e) {
			System.err.println("Error opening logs folder: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void initializeProfileFilter() {
		// Load available profiles
		try {
			List<DTOProfiles> profiles = ServProfiles.getServices().getProfiles();
			if (profiles != null) {
				ObservableList<String> profileNames = FXCollections.observableArrayList();
				profileNames.add("All profiles");
				profiles.forEach(profile -> profileNames.add(profile.getName()));
				comboBoxProfileFilter.setItems(profileNames);

				// Keep the current selection if it's still valid, otherwise select "All profiles"
				String currentSelection = comboBoxProfileFilter.getSelectionModel().getSelectedItem();
				if (currentSelection != null && profileNames.contains(currentSelection)) {
					comboBoxProfileFilter.getSelectionModel().select(currentSelection);
				} else {
					comboBoxProfileFilter.getSelectionModel().selectFirst();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setupFilterListeners() {
		// Listen for profile filter changes
		comboBoxProfileFilter.valueProperty().addListener((obs, oldVal, newVal) -> {
			updateLogFilter();
		});
	}

	private void updateLogFilter() {
		filteredLogMessages.setPredicate(logMessage -> {
			// Profile filter
			String selectedProfile = comboBoxProfileFilter.getValue();
			if (selectedProfile != null && !selectedProfile.isEmpty() && !"All profiles".equals(selectedProfile)) {
				String messageProfile = logMessage.profileProperty().get();
				if (messageProfile == null || !messageProfile.equals(selectedProfile)) {
					return false;
				}
			}
			
			return true;
		});
	}

	public void appendMessage(DTOLogMessage dtoMessage) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
		String formattedDate = LocalDateTime.now().format(formatter);

		if (!checkboxDebug.isSelected() && dtoMessage.getSeverity() == EnumTpMessageSeverity.DEBUG) {
			return;
		}

		Platform.runLater(() -> {
			logMessages.add(0, new LogMessageAux(formattedDate, dtoMessage.getSeverity().toString(), dtoMessage.getMessage(), dtoMessage.getTask(), dtoMessage.getProfile()));

			if (logMessages.size() > 600) {
				logMessages.remove(logMessages.size() - 1);
			}
		});
	}

	@Override
	public void onProfileDataChanged(DTOProfiles profile) {
		Platform.runLater(() -> {
			initializeProfileFilter();
		});
	}

}
