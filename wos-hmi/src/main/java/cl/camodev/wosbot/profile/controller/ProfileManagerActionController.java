package cl.camodev.wosbot.profile.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.launcher.view.ILauncherConstants;
import cl.camodev.wosbot.ot.DTOConfig;
import cl.camodev.wosbot.ot.DTOProfileStatus;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.profile.model.IProfileModel;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.profile.model.impl.ProfileCallback;
import cl.camodev.wosbot.profile.model.impl.ProfileModel;
import cl.camodev.wosbot.profile.view.BulkUpdateDialogController;
import cl.camodev.wosbot.profile.view.EditProfileController;
import cl.camodev.wosbot.profile.view.NewProfileLayoutController;
import cl.camodev.wosbot.profile.view.ProfileManagerLayoutController;
import cl.camodev.wosbot.serv.IProfileStatusChangeListener;
import cl.camodev.wosbot.serv.impl.ServLogs;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ProfileManagerActionController implements IProfileStatusChangeListener {

	private final ProfileManagerLayoutController profileManagerLayoutController;

	private Stage newProfileStage;

	private IProfileModel iModel;

	public ProfileManagerActionController(ProfileManagerLayoutController profileManagerLayoutController) {
		this.profileManagerLayoutController = profileManagerLayoutController;
		this.iModel = new ProfileModel();
		this.iModel.addProfileStatusChangeListerner(this);

	}

	public void loadProfiles(ProfileCallback callback) {
		CompletableFuture.supplyAsync(() -> {
			List<DTOProfiles> profiles = iModel.getProfiles();
			return profiles;
		}).thenAccept(profiles -> {

			if (callback != null) {
				callback.onProfilesLoaded(profiles);
			}

		}).exceptionally(ex -> {
			ex.printStackTrace();
			return null;
		});
	}

	public boolean deleteProfile(DTOProfiles profile) {
		return iModel.deleteProfile(profile);
	}

	public boolean addProfile(DTOProfiles profile) {
		return iModel.addProfile(profile);
	}

	public boolean saveProfile(ProfileAux currentProfile) {

		DTOProfiles dtoprofile = new DTOProfiles(currentProfile.getId(), currentProfile.getName(), currentProfile.getEmulatorNumber(), currentProfile.isEnabled(), currentProfile.getPriority(), currentProfile.getReconnectionTime());
		currentProfile.getConfigs().forEach(cfgAux -> {
			DTOConfig dtoConfig = new DTOConfig(currentProfile.getId(), cfgAux.getName(), cfgAux.getValue());
			dtoprofile.getConfigs().add(dtoConfig);
		});
		return iModel.saveProfile(dtoprofile);
	}


	/**
	 * Updates only the selected profiles with settings from the template profile.
	 * This method allows for selective bulk updates instead of updating all profiles.
	 */
	public boolean bulkUpdateSelectedProfiles(ProfileAux templateProfile, List<ProfileAux> selectedProfiles) {
		if (templateProfile == null || selectedProfiles == null || selectedProfiles.isEmpty()) {
			return false;
		}

		try {
			boolean allUpdatesSuccessful = true;

			for (ProfileAux targetProfile : selectedProfiles) {
				// Copy all configuration values from template to target profile
				for (int i = 0; i < templateProfile.getConfigs().size(); i++) {
					String configName = templateProfile.getConfigs().get(i).getName();
					String configValue = templateProfile.getConfigs().get(i).getValue();

					try {
						// Convert String config name to EnumConfigurationKey
						EnumConfigurationKey configKey = EnumConfigurationKey.valueOf(configName);

						// Update the target profile's configurations
						targetProfile.setConfig(configKey, configValue);
					} catch (IllegalArgumentException e) {
						// If the config name is not a valid enum value, skip it
						ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, "Profile Manager", "-",
							"Skipping unknown configuration: " + configName + " for profile: " + targetProfile.getName());
					}
				}

				// Save the updated target profile
				boolean saveResult = saveProfile(targetProfile);
				if (!saveResult) {
					allUpdatesSuccessful = false;
					ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "Profile Manager", "-",
						"Failed to update profile: " + targetProfile.getName());
				} else {
					ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "Profile Manager", "-",
						"Successfully updated profile: " + targetProfile.getName() + " with template from: " + templateProfile.getName());
				}
			}

			return allUpdatesSuccessful;

		} catch (Exception e) {
			e.printStackTrace();
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "Profile Manager", "-",
				"Error during bulk update of selected profiles: " + e.getMessage());
			return false;
		}
	}

	@Override
	public void onProfileStatusChange(DTOProfileStatus status) {
		if (status != null) {
			profileManagerLayoutController.handleProfileStatusChange(status);

		}

	}

	public void showNewProfileDialog() {
		try {
			FXMLLoader loader = new FXMLLoader(NewProfileLayoutController.class.getResource("NewProfileLayout.fxml"));
			NewProfileLayoutController controller = new NewProfileLayoutController(this);
			loader.setController(controller);

			Parent root = loader.load();
			Scene scene = new Scene(root);
			scene.getStylesheets().add(ILauncherConstants.getCssPath());

			newProfileStage = new Stage();
			newProfileStage.setTitle("New Profile");
			newProfileStage.setScene(scene);
			newProfileStage.initModality(Modality.APPLICATION_MODAL);
			newProfileStage.setResizable(false);

			newProfileStage.setOnCloseRequest(event -> closeNewProfileDialog());

			newProfileStage.showAndWait();
		} catch (IOException e) {
			e.printStackTrace();
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "Profile Manager", "-", "Error loading FXML " + e.getMessage());
		}
	}

	public void closeNewProfileDialog() {
		if (newProfileStage != null) {
			newProfileStage.close();
			newProfileStage = null;
		}
		profileManagerLayoutController.loadProfiles();
	}

	/**
	 * Shows the bulk update dialog and handles the entire workflow
	 */
	public void showBulkUpdateDialog(Long loadedProfileId, List<ProfileAux> profiles, javafx.scene.Node ownerNode) {
		if (loadedProfileId == null) {
			showAlert(javafx.scene.control.Alert.AlertType.WARNING, "WARNING",
				"Please load a profile first to use as template for bulk update.");
			return;
		}

		// Find the currently loaded profile to use as template
		ProfileAux templateProfile = profiles.stream()
			.filter(p -> p.getId().equals(loadedProfileId))
			.findFirst()
			.orElse(null);

		if (templateProfile == null) {
			showAlert(AlertType.ERROR, "ERROR",
				"Could not find the loaded profile to use as template.");
			return;
		}

		// Check if there are other profiles to update
		if (profiles.size() <= 1) {
			showAlert(AlertType.WARNING, "WARNING",
				"No other profiles available to update. You need at least 2 profiles for bulk update.");
			return;
		}

		try {
			// Load the bulk update dialog
			FXMLLoader loader = new FXMLLoader(
				getClass().getResource("/cl/camodev/wosbot/profile/view/BulkUpdateDialog.fxml")
			);
			Parent root = loader.load();

			// Get the controller and setup the dialog
			BulkUpdateDialogController dialogController = loader.getController();

			// Create a new stage for the dialog
			Stage dialogStage = new Stage();
			dialogStage.setTitle("Bulk Update Profiles");

			// Create scene and apply CSS
			Scene scene = new Scene(root);
			scene.getStylesheets().add(ILauncherConstants.getCssPath());

			dialogStage.setScene(scene);
			dialogStage.initModality(Modality.WINDOW_MODAL);
			dialogStage.initOwner(ownerNode.getScene().getWindow());
			dialogStage.setResizable(false);

			// Setup the dialog with current data
			dialogController.setupDialog(templateProfile, new ArrayList<>(profiles), dialogStage);

			// Show dialog and wait for user response
			dialogStage.showAndWait();

			// Process the result if user confirmed
			if (dialogController.isUpdateConfirmed()) {
				List<ProfileAux> selectedProfiles = dialogController.getSelectedProfiles();

				// Perform bulk update on selected profiles
				boolean success = bulkUpdateSelectedProfiles(templateProfile, selectedProfiles);

				if (success) {
					showAlert(AlertType.INFORMATION, "SUCCESS",
						"Successfully updated " + selectedProfiles.size() +
						" profile(s) with settings from '" + templateProfile.getName() + "'.");
					// Refresh the profiles in the controller
					profileManagerLayoutController.loadProfiles();
				} else {
					showAlert(AlertType.ERROR, "ERROR",
						"Error occurred while updating profiles. Some profiles may not have been updated.");
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "Profile Manager", "-",
				"Failed to open bulk update dialog: " + e.getMessage());
			showAlert(AlertType.ERROR, "ERROR",
				"Failed to open bulk update dialog: " + e.getMessage());
		}
	}

	/**
	 * Shows the edit profile dialog for the specified profile
	 */
	public void showEditProfileDialog(ProfileAux profile, javafx.scene.Node ownerNode) {
		if (profile == null) {
			showAlert(Alert.AlertType.ERROR, "ERROR", "No profile selected for editing.");
			return;
		}

		try {
			// Load the edit profile dialog
			FXMLLoader loader = new FXMLLoader(
				getClass().getResource("/cl/camodev/wosbot/profile/view/EditProfile.fxml")
			);
			Parent root = loader.load();

			// Get the controller and setup the dialog
			EditProfileController dialogController = loader.getController();
			dialogController.setProfileToEdit(profile);
			dialogController.setActionController(this);

			// Create a new stage for the dialog
			Stage dialogStage = new Stage();
			dialogStage.setTitle("Edit Profile - " + profile.getName());

			// Create scene and apply CSS
			Scene scene = new Scene(root);
			scene.getStylesheets().add(ILauncherConstants.getCssPath());

			dialogStage.setScene(scene);
			dialogStage.initModality(Modality.WINDOW_MODAL);
			dialogStage.initOwner(ownerNode.getScene().getWindow());
			dialogStage.setResizable(false);

			// Set the dialog stage in the controller
			dialogController.setDialogStage(dialogStage);

			// Show dialog and wait for user response
			dialogStage.showAndWait();

			// If changes were saved, refresh the profiles list
			if (dialogController.isSaveClicked()) {
				profileManagerLayoutController.loadProfiles();
			}

		} catch (Exception e) {
			e.printStackTrace();
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "Profile Manager", "-",
				"Failed to open edit profile dialog: " + e.getMessage());
			showAlert(Alert.AlertType.ERROR, "ERROR",
				"Failed to open edit profile dialog: " + e.getMessage());
		}
	}

	/**
	 * Helper method to show alerts
	 */
	private void showAlert(AlertType alertType, String title, String message) {
		Alert alert = new Alert(alertType);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
	}
}
