package cl.camodev.wosbot.console.view;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import cl.camodev.wosbot.console.controller.ConsoleLogActionController;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.model.LogMessageAux;
import cl.camodev.wosbot.ot.DTOLogMessage;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServProfiles;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class ConsoleLogLayoutController {

	private ConsoleLogActionController actionController;

	@FXML
	private Button buttonClearLogs;

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
		actionController = new ConsoleLogActionController(this);
		logMessages = FXCollections.observableArrayList();
		filteredLogMessages = new FilteredList<>(logMessages);
		
		columnTimeStamp.setCellValueFactory(cellData -> cellData.getValue().timeStampProperty());
		columnMessage.setCellValueFactory(cellData -> cellData.getValue().messageProperty());
		columnLevel.setCellValueFactory(cellData -> cellData.getValue().severityProperty());
		columnTask.setCellValueFactory(cellData -> cellData.getValue().taskProperty());
		columnProfile.setCellValueFactory(cellData -> cellData.getValue().profileProperty());
		tableviewLogMessages.setItems(filteredLogMessages);

		// Botón para agregar datos (simula nuevos mensajes)
		tableviewLogMessages.setPlaceholder(new Label("NO LOGS"));
		
		// Initialize profile filter
		initializeProfileFilter();
		
		// Set up filter listeners
		setupFilterListeners();
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

	private void initializeProfileFilter() {
		// Load available profiles
		try {
			List<DTOProfiles> profiles = ServProfiles.getServices().getProfiles();
			if (profiles != null) {
				ObservableList<String> profileNames = FXCollections.observableArrayList();
				profileNames.add("All profiles");
				profiles.forEach(profile -> profileNames.add(profile.getName()));
				comboBoxProfileFilter.setItems(profileNames);
				comboBoxProfileFilter.getSelectionModel().selectFirst(); // Select "All profiles" by default
			}
                } catch (Exception e) {
                        System.err.println("Failed to load profiles: " + e.getMessage());
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

}
