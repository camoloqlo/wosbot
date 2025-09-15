package cl.camodev.wosbot.profile.view;

import java.util.function.UnaryOperator;

import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.profile.controller.ProfileManagerActionController;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.converter.IntegerStringConverter;

public class NewProfileLayoutController {

	private ProfileManagerActionController profileManagerActionController;

	@FXML
	private Button buttonSaveProfile;

	@FXML
	private TextField textfieldEmulatorNumber;

	@FXML
	private TextField textfieldProfileName;

	@FXML
	private CheckBox checkboxEnabled;

	@FXML
	private Slider sliderPriority;

	@FXML
	private Label labelPriorityValue;

	@FXML
	private TextField textfieldReconnectionTime;

	public NewProfileLayoutController(ProfileManagerActionController profileManagerActionController) {
		this.profileManagerActionController = profileManagerActionController;
	}

	@FXML
	private void initialize() {

		// Filter for emulator number - only numbers
		UnaryOperator<TextFormatter.Change> emulatorFilter = change -> {
			String newText = change.getControlNewText();
			if (newText.matches("\\d*")) {
				return change;
			}
			return null;
		};

		// Filter for reconnection time - only non-negative numbers
		UnaryOperator<TextFormatter.Change> reconnectionTimeFilter = change -> {
			String newText = change.getControlNewText();
			if (newText.matches("\\d*")) {
				return change;
			}
			return null;
		};

		TextFormatter<Integer> emulatorFormatter = new TextFormatter<>(new IntegerStringConverter(), 0, emulatorFilter);
		textfieldEmulatorNumber.setTextFormatter(emulatorFormatter);

		TextFormatter<Integer> reconnectionTimeFormatter = new TextFormatter<>(new IntegerStringConverter(), 0, reconnectionTimeFilter);
		textfieldReconnectionTime.setTextFormatter(reconnectionTimeFormatter);

		labelPriorityValue.setText(String.valueOf((int) sliderPriority.getValue()));

		sliderPriority.valueProperty().addListener((observable, oldValue, newValue) -> {
		int priorityValue = newValue.intValue();
		labelPriorityValue.setText(String.valueOf(priorityValue));
		});


		ChangeListener<String> fieldListener = (ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
			buttonSaveProfile.setDisable(!validateFields());
		};

		textfieldEmulatorNumber.textProperty().addListener(fieldListener);
		textfieldProfileName.textProperty().addListener(fieldListener);
		textfieldReconnectionTime.textProperty().addListener(fieldListener);


		buttonSaveProfile.setDisable(true);
	}

	@FXML
	private void handleSaveProfileButton(ActionEvent event) {
		long priority = (long) sliderPriority.getValue();
		boolean enabled = checkboxEnabled.isSelected();
		long reconnectionTime = Long.parseLong(textfieldReconnectionTime.getText().isEmpty() ? "0" : textfieldReconnectionTime.getText());

		DTOProfiles newProfile = new DTOProfiles(-1L, textfieldProfileName.getText(), textfieldEmulatorNumber.getText(), enabled, priority, reconnectionTime);
		profileManagerActionController.addProfile(newProfile);
		profileManagerActionController.closeNewProfileDialog();
	}

	private boolean validateFields() {
		String emulatorText = textfieldEmulatorNumber.getText();
		String profileText = textfieldProfileName.getText();
		String reconnectionTimeText = textfieldReconnectionTime.getText();

		if (emulatorText.isEmpty() || profileText.isEmpty()) {
			return false;
		}

		try {
			int emulatorNumber = Integer.parseInt(emulatorText);
			if (emulatorNumber < 0) {
				return false;
			}

			// Validar reconnection time
			if (!reconnectionTimeText.isEmpty()) {
				int reconnectionTime = Integer.parseInt(reconnectionTimeText);
				if (reconnectionTime < 0) {
					return false;
				}
			}

			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
