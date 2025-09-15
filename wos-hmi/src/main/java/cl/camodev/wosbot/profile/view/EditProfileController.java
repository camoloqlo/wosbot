package cl.camodev.wosbot.profile.view;

import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.profile.controller.ProfileManagerActionController;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.serv.impl.ServLogs;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class EditProfileController implements Initializable {

    @FXML
    private TextField txtProfileName;

    @FXML
    private TextField txtEmulatorNumber;

    @FXML
    private CheckBox chkEnabled;

    @FXML
    private Slider sliderPriority;

    @FXML
    private Label lblPriorityValue;

    @FXML
    private Button btnSave;

    @FXML
    private Button btnCancel;

    @FXML
    private TextField txtReconnectionTime;

    private ProfileAux profileToEdit;
    private ProfileManagerActionController actionController;
    private Stage dialogStage;
    private boolean saveClicked = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Add input validation to emulator number field - only allow numbers
        txtEmulatorNumber.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                txtEmulatorNumber.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });

        // Optional: Limit the length to a reasonable number (e.g., 3 digits)
        txtEmulatorNumber.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 3) {
                txtEmulatorNumber.setText(oldValue);
            }
        });

        // Add input validation to reconnection time field - only allow numbers
        txtReconnectionTime.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                txtReconnectionTime.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });

        // Configure the priority slider
        sliderPriority.valueProperty().addListener((observable, oldValue, newValue) -> {
            int priorityValue = newValue.intValue();
            lblPriorityValue.setText(String.valueOf(priorityValue));
        });
    }

    public void setProfileToEdit(ProfileAux profile) {
        this.profileToEdit = profile;
        populateFields();
    }

    public void setActionController(ProfileManagerActionController controller) {
        this.actionController = controller;
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    private void populateFields() {
        if (profileToEdit != null) {
            txtProfileName.setText(profileToEdit.getName());
            txtEmulatorNumber.setText(profileToEdit.getEmulatorNumber());
            chkEnabled.setSelected(profileToEdit.isEnabled());
            sliderPriority.setValue(profileToEdit.getPriority().doubleValue());
            lblPriorityValue.setText(String.valueOf(profileToEdit.getPriority()));
            txtReconnectionTime.setText(String.valueOf(profileToEdit.getReconnectionTime()));
        }
    }

    @FXML
    private void handleSave() {
        if (validateInput()) {
            // Update the profile with new values
            profileToEdit.setName(txtProfileName.getText());
            profileToEdit.setEmulatorNumber(txtEmulatorNumber.getText());
            profileToEdit.setEnabled(chkEnabled.isSelected());
            profileToEdit.setPriority((long) sliderPriority.getValue());

            // Update reconnection time
            long reconnectionTime = Long.parseLong(txtReconnectionTime.getText().isEmpty() ? "0" : txtReconnectionTime.getText());
            profileToEdit.setReconnectionTime(reconnectionTime);

            // Save to database
            boolean success = actionController.saveProfile(profileToEdit);

            if (success) {
                saveClicked = true;

                // Show success message
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Success");
                alert.setHeaderText(null);
                alert.setContentText("Profile updated successfully.");
                alert.showAndWait();

                // Close dialog
                dialogStage.close();

                ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "Profile Editor", "-",
                    "Profile '" + profileToEdit.getName() + "' updated successfully");
            } else {
                // Show error message
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("Failed to update profile. Please try again.");
                alert.showAndWait();

                ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "Profile Editor", "-",
                    "Failed to update profile '" + profileToEdit.getName() + "'");
            }
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private boolean validateInput() {
        StringBuilder errorMessage = new StringBuilder();

        // Validate profile name
        if (txtProfileName.getText() == null || txtProfileName.getText().trim().isEmpty()) {
            errorMessage.append("Profile name cannot be empty.\n");
        }

        // Validate emulator number
        if (txtEmulatorNumber.getText() == null || txtEmulatorNumber.getText().trim().isEmpty()) {
            errorMessage.append("Emulator number cannot be empty.\n");
        } else {
            String emulatorText = txtEmulatorNumber.getText().trim();
            // Additional validation: check if it's a valid non-negative integer (>= 0)
            try {
                int emulatorNumber = Integer.parseInt(emulatorText);
                if (emulatorNumber < 0) {
                    errorMessage.append("Emulator number must be a non-negative integer (0 or greater).\n");
                }
            } catch (NumberFormatException e) {
                errorMessage.append("Emulator number must be a valid integer.\n");
            }
        }

        // Validate reconnection time
        if (txtReconnectionTime.getText() == null || txtReconnectionTime.getText().trim().isEmpty()) {
            errorMessage.append("Reconnection time cannot be empty.\n");
        } else {
            String reconnectionTimeText = txtReconnectionTime.getText().trim();
            try {
                long reconnectionTime = Long.parseLong(reconnectionTimeText);
                if (reconnectionTime < 0) {
                    errorMessage.append("Reconnection time must be a non-negative number (0 or greater).\n");
                }
            } catch (NumberFormatException e) {
                errorMessage.append("Reconnection time must be a valid number.\n");
            }
        }

        if (!errorMessage.isEmpty()) {
            // Show validation error
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Invalid Input");
            alert.setHeaderText("Please correct the following errors:");
            alert.setContentText(errorMessage.toString());
            alert.showAndWait();
            return false;
        }

        return true;
    }
}
