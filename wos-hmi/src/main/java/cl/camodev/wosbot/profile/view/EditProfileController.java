package cl.camodev.wosbot.profile.view;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.profile.controller.ProfileManagerActionController;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.serv.impl.ServLogs;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.shape.Circle;

import java.net.URL;
import java.io.File;
import java.util.ResourceBundle;

public class EditProfileController implements Initializable {

    private static final double PROFILE_IMAGE_DIAMETER = 80.0;

    @FXML
    private TextField txtProfileName;

    @FXML
    private TextField txtEmulatorNumber;

    @FXML
    private CheckBox chkEnabled;

    @FXML
    private Button btnSave;

    @FXML
    private Button btnCancel;

    @FXML
    private ImageView imageProfile;

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

        imageProfile.setFitHeight(PROFILE_IMAGE_DIAMETER);
        imageProfile.setFitWidth(PROFILE_IMAGE_DIAMETER);
        imageProfile.setClip(new Circle(PROFILE_IMAGE_DIAMETER / 2, PROFILE_IMAGE_DIAMETER / 2, PROFILE_IMAGE_DIAMETER / 2));
        imageProfile.setImage(null);
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

            String photoPath = profileToEdit.getConfig(EnumConfigurationKey.PROFILE_IMAGE_PATH_STRING, String.class);
            if (photoPath != null && !photoPath.trim().isEmpty()) {
                File photoFile = new File(photoPath);
                if (photoFile.exists()) {
                    imageProfile.setImage(new Image(photoFile.toURI().toString()));
                }
            }
        }
    }

    @FXML
    private void handleSave() {
        if (validateInput()) {
            // Update the profile with new values
            profileToEdit.setName(txtProfileName.getText());
            profileToEdit.setEmulatorNumber(txtEmulatorNumber.getText());
            profileToEdit.setEnabled(chkEnabled.isSelected());

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

        if (errorMessage.length() > 0) {
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

    @FXML
    private void handleInsertPhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Photo");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Images", "*.png"));
        File selectedFile = fileChooser.showOpenDialog(dialogStage);
        if (selectedFile != null) {
            imageProfile.setImage(new Image(selectedFile.toURI().toString()));
            if (profileToEdit != null) {
                profileToEdit.setConfig(EnumConfigurationKey.PROFILE_IMAGE_PATH_STRING, selectedFile.getAbsolutePath());
            }
        }
    }
}
