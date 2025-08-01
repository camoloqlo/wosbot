package cl.camodev.wosbot.launcher.view;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import cl.camodev.utiles.UtilCV;
import cl.camodev.wosbot.alliance.view.AllianceLayoutController;
import cl.camodev.wosbot.city.view.CityEventsLayoutController;
import cl.camodev.wosbot.city.view.CityUpgradesLayoutController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.view.ConsoleLogLayoutController;
import cl.camodev.wosbot.emulator.EmulatorType;
import cl.camodev.wosbot.emulator.view.EmuConfigLayoutController;
import cl.camodev.wosbot.gather.view.GatherLayoutController;
import cl.camodev.wosbot.intel.view.IntelLayoutController;
import cl.camodev.wosbot.ot.DTOBotState;
import cl.camodev.wosbot.ot.DTOLogMessage;
import cl.camodev.wosbot.pets.view.PetsLayoutController;
import cl.camodev.wosbot.profile.model.IProfileChangeObserver;
import cl.camodev.wosbot.profile.model.IProfileLoadListener;
import cl.camodev.wosbot.profile.model.IProfileObserverInjectable;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.profile.view.ProfileManagerLayoutController;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.shop.view.ShopLayoutController;
import cl.camodev.wosbot.taskmanager.view.TaskManagerLayoutController;
import cl.camodev.wosbot.training.view.TrainingLayoutController;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class LauncherLayoutController implements IProfileLoadListener {

       private static final double PROFILE_IMAGE_SIZE = 128.0;

        @FXML
        private VBox buttonsContainer;
        @FXML
        private Button buttonStartStop;

        @FXML
        private Button buttonPauseResume;

        @FXML
        private AnchorPane mainContentPane;

        @FXML
        private Label labelRunTime;

        @FXML
        private Label labelVersion;

        @FXML
       private ImageView imageProfile;

       @FXML
       private Label labelProfileName;

       @FXML
       private Button buttonChangePhoto;

       @FXML
       private Circle imagePlaceholder;

        private Stage stage;

	private LauncherActionController actionController;

	private ConsoleLogLayoutController consoleLogLayoutController;

	private ProfileManagerLayoutController profileManagerLayoutController;

	private Map<String, Object> moduleControllers = new HashMap<>();

       private boolean estado = false;

       private ProfileAux currentProfile;

	public LauncherLayoutController(Stage stage) {
		this.stage = stage;
	}

	@FXML
	private void initialize() {
               initializeDiscordBot();
               initializeEmulatorManager();
               initializeLogModule();
               initializeProfileModule();
               initializeModules();
               initializeExternalLibraries();
               showVersion();
               initializeProfileHeader();

        }

        private void showVersion() {
                String version = getVersion();
                labelVersion.setText("Version: " + version);
        }

       private void initializeProfileHeader() {
               imageProfile.setFitHeight(PROFILE_IMAGE_SIZE);
               imageProfile.setFitWidth(PROFILE_IMAGE_SIZE);
               imageProfile.setClip(new Circle(PROFILE_IMAGE_SIZE / 2, PROFILE_IMAGE_SIZE / 2, PROFILE_IMAGE_SIZE / 2));
               imagePlaceholder.setRadius(PROFILE_IMAGE_SIZE / 2);
               imagePlaceholder.setVisible(true);
               imageProfile.setImage(null);
               labelProfileName.setText("");
       }

       private void loadProfileImageAsync(File file) {
               imageProfile.setImage(null);
               imagePlaceholder.setVisible(true);
               Task<Image> loadTask = new Task<>() {
                       @Override
                       protected Image call() {
                               return new Image(file.toURI().toString(), PROFILE_IMAGE_SIZE, PROFILE_IMAGE_SIZE, true, true);
                       }
               };
               loadTask.setOnSucceeded(e -> {
                       imageProfile.setImage(loadTask.getValue());
                       imagePlaceholder.setVisible(false);
               });
               loadTask.setOnFailed(e -> imagePlaceholder.setVisible(true));
               new Thread(loadTask, "ProfileImageLoader").start();
       }

        private String getVersion() {
		// If running as JAR
		Package pkg = getClass().getPackage();
		if (pkg != null && pkg.getImplementationVersion() != null) {
			return pkg.getImplementationVersion();
		}
		// Read version from parent project pom.xml
		try {
			Path parentPomPath = Paths.get("..", "pom.xml");
			if (!Files.exists(parentPomPath)) {
				parentPomPath = Paths.get("pom.xml");
			}
			List<String> lines = Files.readAllLines(parentPomPath);
			String revision = null;
			for (String line : lines) {
				line = line.trim();
				if (line.startsWith("<revision>") && line.endsWith("</revision>")) {
					revision = line.replace("<revision>", "").replace("</revision>", "").trim();
					break;
				}
			}
			if (revision != null) {
				return revision;
			}
		} catch (Exception e) {
			// Ignore error
		}
		return "Unknown";
	}

	private void initializeEmulatorManager() {
		HashMap<String, String> globalConfig = ServConfig.getServices().getGlobalConfig();

		if (globalConfig == null || globalConfig.isEmpty()) {
			globalConfig = new HashMap<>();
		}

		// Verificar si hay un emulador activo y validar su path
		String savedActiveEmulator = globalConfig.get(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name());
		EmulatorType activeEmulator = savedActiveEmulator != null ? EmulatorType.valueOf(savedActiveEmulator) : null;
		boolean activeEmulatorValid = false;

		if (activeEmulator != null) {
			String activePath = globalConfig.get(activeEmulator.getConfigKey());
			if (activePath != null && new File(activePath).exists()) {
				activeEmulatorValid = true;
			} else {
				ServScheduler.getServices().saveEmulatorPath(activeEmulator.getConfigKey(), null); // Invalidar path no válido
			}
		}

		// Validar el otro emulador si el activo no es válido
		List<EmulatorType> foundEmulators = new ArrayList<>();
		for (EmulatorType emulator : EmulatorType.values()) {
			if (activeEmulator == emulator)
				continue;

			String emulatorPath = globalConfig.get(emulator.getConfigKey());
			if (emulatorPath != null && new File(emulatorPath).exists()) {
				foundEmulators.add(emulator);
			} else {
				File emulatorFile = new File(emulator.getDefaultPath());
				if (emulatorFile.exists()) {
					ServScheduler.getServices().saveEmulatorPath(emulator.getConfigKey(), emulatorFile.getParent());
					foundEmulators.add(emulator);
				}
			}
		}

		if (!activeEmulatorValid) {
			if (foundEmulators.size() == 1) {
				ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name(), foundEmulators.get(0).name());
				return;
			} else if (foundEmulators.isEmpty()) {
				selectEmulatorManually();
			} else {
				EmulatorType selectedEmulator = askUserForPreferredEmulator(foundEmulators);
				ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name(), selectedEmulator.name());
			}
		}
	}

	private EmulatorType askUserForPreferredEmulator(List<EmulatorType> emulators) {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle("Select Emulator");
		alert.setHeaderText("Multiple emulators found. Please select which one to use.");

		List<ButtonType> buttons = new ArrayList<>();
		for (EmulatorType emulator : emulators) {
			buttons.add(new ButtonType(emulator.getDisplayName()));
		}
		ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
		buttons.add(cancelButton);

		alert.getButtonTypes().setAll(buttons);
		Optional<ButtonType> result = alert.showAndWait();

		for (EmulatorType emulator : emulators) {
			if (result.isPresent() && result.get().getText().equals(emulator.getDisplayName())) {
				return emulator;
			}
		}

		showErrorAndExit("No emulator selected. The application will close.");
		return null; // Nunca debería llegar aquí porque el sistema se cerrará antes.
	}

	private void selectEmulatorManually() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select Emulator Executable");

		FileChooser.ExtensionFilter exeFilter = new FileChooser.ExtensionFilter("Emulator Executable", "*.exe");
		fileChooser.getExtensionFilters().add(exeFilter);
		fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

		File selectedFile = fileChooser.showOpenDialog(stage);

		if (selectedFile != null) {
			for (EmulatorType emulator : EmulatorType.values()) {
				if (selectedFile.getName().equals(new File(emulator.getDefaultPath()).getName())) {
					ServScheduler.getServices().saveEmulatorPath(emulator.getConfigKey(), selectedFile.getParent());
					ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name(), emulator.name());
					return;
				}
			}
			showErrorAndExit("Invalid emulator file selected. Please select a valid emulator executable.");
		} else {
			showErrorAndExit("No emulator selected. The application will close.");
		}
	}

	private void showErrorAndExit(String message) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle("ERROR");
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
		System.exit(0);
	}

	private void initializeDiscordBot() {
//		ServDiscord.getServices();

	}

	private void initializeProfileModule() {
		profileManagerLayoutController = new ProfileManagerLayoutController();
		addButton("ProfileManagerLayout", "Profiles", profileManagerLayoutController);

	}

	private void initializeExternalLibraries() {
                try {
                        UtilCV.loadNativeLibrary("/native/opencv/opencv_java4110.dll");
                } catch (IOException e) {
                        System.err.println("Failed to load OpenCV library: " + e.getMessage());
                }

	}

	private void initializeModules() {
		//@formatter:off
		List<ModuleDefinition> modules = Arrays.asList(				
				new ModuleDefinition("TaskManagerLayout", "Task Manager", TaskManagerLayoutController::new),
				new ModuleDefinition("CityUpgradesLayout", "City Upgrades", CityUpgradesLayoutController::new),
				new ModuleDefinition("CityEventsLayout", "City Events", CityEventsLayoutController::new),
				new ModuleDefinition("ShopLayout", "Shop", ShopLayoutController::new),
				new ModuleDefinition("GatherLayout", "Gather", GatherLayoutController::new),
				new ModuleDefinition("IntelLayout", "Intel", IntelLayoutController::new),
				new ModuleDefinition("AllianceLayout", "Alliance", AllianceLayoutController::new),
				new ModuleDefinition("TrainingLayout", "Training", TrainingLayoutController::new),
				new ModuleDefinition("PetsLayout", "Pets", PetsLayoutController::new),
				new ModuleDefinition("EmuConfigLayout", "Config", EmuConfigLayoutController::new)
				
				);
		//@formatter:on

		for (ModuleDefinition module : modules) {
			consoleLogLayoutController.appendMessage(new DTOLogMessage(EnumTpMessageSeverity.INFO, "Loading module: " + module.getButtonTitle(), "-", "-"));

			// Crear el controlador con la instancia de profileObserver
			Object controller = module.createController(profileManagerLayoutController);
			moduleControllers.put(module.getButtonTitle(), controller);
			addButton(module.getFxmlName(), module.getButtonTitle(), controller);

			if (controller instanceof IProfileLoadListener) {
				profileManagerLayoutController.addProfileLoadListener((IProfileLoadListener) controller);
			}
		}
		profileManagerLayoutController.addProfileLoadListener(this);
	}

	private void initializeLogModule() {
		actionController = new LauncherActionController(this);
		consoleLogLayoutController = new ConsoleLogLayoutController();
		addButton("ConsoleLogLayout", "Logs", consoleLogLayoutController).fire();

	}
	@FXML
	public void handleButtonStartStop(ActionEvent event) {
		if (!estado) {
			actionController.startBot();
		} else {
			actionController.stopBot();
		}
	}

	@FXML
	public void handleButtonPauseResume(ActionEvent event) {
		if (buttonPauseResume.getText().equals("Pause Bot")) {
			actionController.pauseBot();
		} else {
			actionController.resumeBot();
		}
	}

	private Button addButton(String fxmlName, String title, Object controller) {
		try {
			FXMLLoader loader = new FXMLLoader(controller.getClass().getResource(fxmlName + ".fxml"));
			loader.setController(controller);
			Parent root = loader.load();

			Button button = new Button(title);
			button.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(button, Priority.ALWAYS);

			// Asigna la clase personalizada para esquinas cuadradas a este botón
			button.getStyleClass().add("square-button");

			button.setOnAction(e -> {
				// Limpia el contenido actual y agrega el nuevo panel
				mainContentPane.getChildren().clear();
				AnchorPane.setTopAnchor(root, 0.0);
				AnchorPane.setBottomAnchor(root, 0.0);
				AnchorPane.setLeftAnchor(root, 0.0);
				AnchorPane.setRightAnchor(root, 0.0);
				mainContentPane.getChildren().add(root);

				// Remueve la clase de estilo "active" de todos los botones
				for (Node node : buttonsContainer.getChildren()) {
					if (node instanceof Button) {
						node.getStyleClass().remove("active");
					}
				}
				// Agrega la clase "active" al botón actual para resaltarlo
				button.getStyleClass().add("active");
			});

			buttonsContainer.getChildren().add(button);
			return button;
                } catch (IOException e) {
                        System.err.println("Failed to load module " + fxmlName + ": " + e.getMessage());
                }
		return null;
	}

	public <T> T getModuleController(String key, Class<T> type) {
		Object controller = moduleControllers.get(key);
		if (controller == null) {
			return null;
		}
		return type.cast(controller);
	}

        @Override
        public void onProfileLoad(ProfileAux profile) {
               currentProfile = profile;
               labelProfileName.setText(profile.getName());
               String photoPath = profile.getConfig(EnumConfigurationKey.PROFILE_IMAGE_PATH_STRING, String.class);
               if (photoPath != null && !photoPath.trim().isEmpty()) {
                       File photoFile = new File(photoPath);
                       if (photoFile.exists()) {
                               loadProfileImageAsync(photoFile);
                       } else {
                               imageProfile.setImage(null);
                               imagePlaceholder.setVisible(true);
                       }
               } else {
                       imageProfile.setImage(null);
                       imagePlaceholder.setVisible(true);
               }
               String version = getVersion();
               stage.setTitle("Whiteout Survival Bot v" + version + " - " + profile.getName());
               buttonStartStop.setDisable(false);
               buttonPauseResume.setDisable(true);
       }

       @FXML
       private void handleChangePhoto() {
               if (currentProfile == null) {
                       return;
               }
               FileChooser fileChooser = new FileChooser();
               fileChooser.setTitle("Select Profile Photo");
               fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Images", "*.png"));
               File selectedFile = fileChooser.showOpenDialog(stage);
               if (selectedFile != null) {
                       loadProfileImageAsync(selectedFile);
                       currentProfile.setConfig(EnumConfigurationKey.PROFILE_IMAGE_PATH_STRING, selectedFile.getAbsolutePath());
                       profileManagerLayoutController.saveProfile(currentProfile);
               }
       }

	public void onBotStateChange(DTOBotState botState) {
		if (botState != null) {
			if (botState.getRunning()) {
				if (botState.getPaused() != null && botState.getPaused()) {
					// Bot is running but paused
					buttonStartStop.setText("Stop");
					buttonStartStop.setDisable(false);
					buttonPauseResume.setText("Resume Bot");
					buttonPauseResume.setDisable(false);
					estado = true;
				} else {
					// Bot is running and active
					buttonStartStop.setText("Stop");
					buttonStartStop.setDisable(false);
					buttonPauseResume.setText("Pause Bot");
					buttonPauseResume.setDisable(false);
					estado = true;
				}
			} else {
				// Bot is stopped
				buttonStartStop.setText("Start Bot");
				buttonStartStop.setDisable(false);
				buttonPauseResume.setText("Pause Bot");
				buttonPauseResume.setDisable(true);
				estado = false;
			}
		}
	}

	private static class ModuleDefinition {
		private final String fxmlName;
		private final String buttonTitle;
		private final Supplier<Object> controllerSupplier;

		public ModuleDefinition(String fxmlName, String buttonTitle, Supplier<Object> controllerSupplier) {
			this.fxmlName = fxmlName;
			this.buttonTitle = buttonTitle;
			this.controllerSupplier = controllerSupplier;
		}

		public Object createController(IProfileChangeObserver profileObserver) {
			Object controller = controllerSupplier.get();
			if (controller instanceof IProfileObserverInjectable) {
				((IProfileObserverInjectable) controller).setProfileObserver(profileObserver);
			}
			return controller;
		}

		public String getFxmlName() {
			return fxmlName;
		}

		public String getButtonTitle() {
			return buttonTitle;
		}

	}

}
