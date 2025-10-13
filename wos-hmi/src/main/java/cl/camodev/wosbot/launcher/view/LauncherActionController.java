package cl.camodev.wosbot.launcher.view;

import cl.camodev.wosbot.ot.DTOBotState;
import cl.camodev.wosbot.ot.DTOQueueState;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.profile.view.ProfileManagerLayoutController;
import cl.camodev.wosbot.serv.IBotStateListener;
import cl.camodev.wosbot.serv.IQueueStateListener;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import javafx.application.Platform;

public class LauncherActionController implements IBotStateListener, IQueueStateListener {

	private final LauncherLayoutController layoutController;
	private ProfileManagerLayoutController profileManagerLayoutController;

        public LauncherActionController(LauncherLayoutController launcherLayoutController) {
                this.layoutController = launcherLayoutController;
                ServScheduler.getServices().registryBotStateListener(this);
                ServScheduler.getServices().registryQueueStateListener(this);
        }

	public void setProfileManagerController(ProfileManagerLayoutController profileManagerLayoutController) {
		this.profileManagerLayoutController = profileManagerLayoutController;
	}

	public void startBot() {
		ServScheduler.getServices().startBot();
	}

	public void stopBot() {
		ServScheduler.getServices().stopBot();
	}

        public void pauseAllQueues() {
                ServScheduler.getServices().pauseBot();
        }

        public void resumeAllQueues() {
                ServScheduler.getServices().resumeBot();
        }

        public void pauseQueue(ProfileAux profile) {
                if (profile != null) {
                        ServScheduler.getServices().pauseQueue(profile.getId());
                }
        }

        public void resumeQueue(ProfileAux profile) {
                if (profile != null) {
                        ServScheduler.getServices().resumeQueue(profile.getId());
                }
        }

	public void captureScreenshots() {

	}


	public void loadProfilesIntoComboBox() {
		if (profileManagerLayoutController != null) {

			javafx.collections.ObservableList<ProfileAux> profiles = profileManagerLayoutController.getProfiles();

			if (profiles != null && !profiles.isEmpty()) {
				layoutController.updateComboBoxItems(profiles);

				if (layoutController.getSelectedProfile() == null) {
					ProfileAux firstProfile = profiles.getFirst();
					if (firstProfile != null) {
						layoutController.selectProfileInComboBox(firstProfile);
						selectProfile(firstProfile, false);
					}
				}
			} else {
				profileManagerLayoutController.loadProfiles();


				Platform.runLater(this::updateProfileComboBox);
			}
		}
	}

	public void updateProfileComboBox() {
		if (profileManagerLayoutController != null) {
			javafx.collections.ObservableList<ProfileAux> profiles = profileManagerLayoutController.getProfiles();

			if (profiles != null && !profiles.isEmpty()) {
				ProfileAux currentSelected = layoutController.getSelectedProfile();

				layoutController.updateComboBoxItems(profiles);

				if (currentSelected != null) {

					ProfileAux matchingProfile = profiles.stream()
						.filter(p -> p.getId().equals(currentSelected.getId()))
						.findFirst()
						.orElse(null);

					if (matchingProfile != null) {
						layoutController.selectProfileInComboBox(matchingProfile);
					} else {
						ProfileAux firstProfile = profiles.getFirst();
						layoutController.selectProfileInComboBox(firstProfile);
						selectProfile(firstProfile, false);
					}
				} else {
					ProfileAux firstProfile = profiles.getFirst();
					layoutController.selectProfileInComboBox(firstProfile);
					selectProfile(firstProfile, false);
				}
			}
		}
	}

	public void selectProfile(ProfileAux selectedProfile) {
		selectProfile(selectedProfile, true);
	}

	public void selectProfile(ProfileAux selectedProfile, boolean userTriggered) {
		if (selectedProfile != null) {

			if (profileManagerLayoutController != null) {
				profileManagerLayoutController.setLoadedProfileId(selectedProfile.getId());
				profileManagerLayoutController.notifyProfileLoadListeners(selectedProfile);
			}

			layoutController.onProfileLoad(selectedProfile);
		}
	}

	public void refreshProfileComboBox() {
		Platform.runLater(this::updateProfileComboBox);
	}

	@Override
	public void onBotStateChange(DTOBotState botState) {
		Platform.runLater(() -> {
			layoutController.onBotStateChange(botState);
		});
	}

	@Override
	public void onQueueStateChange(DTOQueueState queueState) {
		Platform.runLater(() -> {
			layoutController.onQueueStateChange(queueState);
		});
	}
}
