package cl.camodev.wosbot.profile.view;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.launcher.view.ILauncherConstants;
import cl.camodev.wosbot.ot.DTOProfileStatus;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.profile.controller.ProfileManagerActionController;
import cl.camodev.wosbot.profile.model.ConfigAux;
import cl.camodev.wosbot.profile.model.IProfileChangeObserver;
import cl.camodev.wosbot.profile.model.IProfileLoadListener;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.serv.impl.ServLogs;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.stage.FileChooser;

public class ProfileManagerLayoutController implements IProfileChangeObserver {

    // Serialize profile operations to avoid SQLite write contention
    private final ExecutorService profileQueueExecutor = Executors.newSingleThreadExecutor();
    private static final String JSON_FILTER_DESCRIPTION = "JSON Files";
    private static final String JSON_FILTER_EXTENSION = "*.json";
        private ProfileManagerActionController profileManagerActionController;
	private ObservableList<ProfileAux> profiles;
	private SortedList<ProfileAux> sortedProfiles;
	@FXML
	private TableView<ProfileAux> tableviewLogMessages;
	@FXML
	private TableColumn<ProfileAux, Void> columnDelete;
	@FXML
	private TableColumn<ProfileAux, String> columnEmulatorNumber;
	@FXML
	private TableColumn<ProfileAux, Boolean> columnEnabled;
	@FXML
        private TableColumn<ProfileAux, String> columnProfileName;
        @FXML
        private TableColumn<ProfileAux, String> columnStatus;
        @FXML
        private Button btnBulkUpdate;
       @FXML
       private Button btnExportProfiles;
       @FXML
       private Button btnImportProfiles;
	private Long loadedProfileId;
        private List<IProfileLoadListener> profileLoadListeners;

	@FXML
	private void initialize() {
		initializeController();
		initializeTableView();
		loadProfiles();

	}

	private void initializeController() {
		profileManagerActionController = new ProfileManagerActionController(this);
	}

        private void initializeTableView() {
		profiles = FXCollections.observableArrayList();

		columnProfileName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
		columnEmulatorNumber.setCellValueFactory(cellData -> cellData.getValue().emulatorNumberProperty());
               columnStatus.setCellValueFactory(cellData -> cellData.getValue().statusProperty());

               // Add double-click event handler to open edit dialog
               tableviewLogMessages.setRowFactory(tv -> {
                        javafx.scene.control.TableRow<ProfileAux> row = new javafx.scene.control.TableRow<>();
                        row.setOnMouseClicked(event -> {
                                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                                        ProfileAux selectedProfile = row.getItem();
                                        profileManagerActionController.showEditProfileDialog(selectedProfile, tableviewLogMessages);
                                }
                        });
                        return row;
               });

		columnDelete.setCellFactory(new Callback<TableColumn<ProfileAux, Void>, TableCell<ProfileAux, Void>>() {
			@Override
			public TableCell<ProfileAux, Void> call(TableColumn<ProfileAux, Void> param) {
				return new TableCell<ProfileAux, Void>() {
					private final Button btnDelete = new Button();
					private final Button btnLoad = new Button();
//					private final Button btnSave = new Button();

					{
						// Asignar iconos a los botones
						btnDelete.setGraphic(loadIcon("/icons/buttons/delete.png"));
						btnLoad.setGraphic(loadIcon("/icons/buttons/load.png"));
//						btnSave.setGraphic(loadIcon("/icons/buttons/save.png"));

						// Configurar el tamaño de los botones (sin texto)
						btnDelete.setPrefSize(30, 30);
						btnLoad.setPrefSize(30, 30);
//						btnSave.setPrefSize(30, 30);

						btnDelete.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
						btnLoad.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
//						btnSave.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

						// Acción para el botón Delete
						btnDelete.setOnAction((ActionEvent event) -> {
							if (getTableView().getItems().size() <= 1) {
								Alert alert = new Alert(Alert.AlertType.WARNING);
								alert.setTitle("WARNING");
								alert.setHeaderText(null);
								alert.setContentText("U MUST HAVE AT LEAST ONE PROFILE");
								alert.showAndWait();
								return;
							}

							ProfileAux currentProfile = getTableView().getItems().get(getIndex());
							System.out.println("Eliminando perfil con ID: " + currentProfile.getId());

							boolean deletionResult = profileManagerActionController.deleteProfile(new DTOProfiles(currentProfile.getId(), null, null, null));

							Alert alert;
							if (deletionResult) {
								alert = new Alert(Alert.AlertType.INFORMATION);
								alert.setTitle("SUCCESS DELETE");
								alert.setHeaderText(null);
								alert.setContentText("Profile deleted successfully.");
								loadProfiles();
							} else {
								alert = new Alert(Alert.AlertType.ERROR);
								alert.setTitle("ERROR DELETE");
								alert.setHeaderText(null);
								alert.setContentText("Error deleting profile.");
							}
							alert.showAndWait();
						});

						// Acción para el botón Load
						btnLoad.setOnAction((ActionEvent event) -> {
							ProfileAux currentProfile = getTableView().getItems().get(getIndex());
							System.out.println("Cargando perfil con ID: " + currentProfile.getId());
							loadedProfileId = currentProfile.getId();
							notifyProfileLoadListeners(currentProfile);
						});

						// Acción para el botón Save
//						btnSave.setOnAction((ActionEvent event) -> {
//							ProfileAux currentProfile = getTableView().getItems().get(getIndex());
//							System.out.println("Guardando perfil con ID: " + currentProfile.getId());
//							boolean saved = profileManagerActionController.saveProfile(currentProfile);
//
//							Alert alert;
//							if (saved) {
//								alert = new Alert(Alert.AlertType.INFORMATION);
//								alert.setTitle("SUCCESS UPDATE");
//								alert.setHeaderText(null);
//								alert.setContentText("Profile updated successfully.");
//								loadProfiles();
//							} else {
//								alert = new Alert(Alert.AlertType.ERROR);
//								alert.setTitle("ERROR UPDATE");
//								alert.setHeaderText(null);
//								alert.setContentText("Error updating profile.");
//
//							}
//							alert.showAndWait();
//						});
					}

					// Método para cargar un icono desde los recursos
					private ImageView loadIcon(String path) {
						Image image = new Image(getClass().getResourceAsStream(path));
						ImageView imageView = new ImageView(image);
						imageView.setFitWidth(16);
						imageView.setFitHeight(16);
						return imageView;
					}

					@Override
					protected void updateItem(Void item, boolean empty) {
						super.updateItem(item, empty);
						if (empty) {
							setGraphic(null);
						} else {
							HBox buttonContainer = new HBox(5, btnLoad/* , btnSave */, btnDelete);
							setGraphic(buttonContainer);
						}
					}

				};
			}
		});

		columnEnabled.setCellValueFactory(cellData -> cellData.getValue().enabledProperty());

		columnEnabled.setCellFactory(col -> new TableCell<ProfileAux, Boolean>() {

			private final ToggleButton toggleButton = new ToggleButton();
			private final StackPane switchContainer;
			private final Circle knob;
			private final Rectangle background;

			{
				// Fondo del switch más pequeño
				background = new Rectangle(30, 15, Color.LIGHTGRAY);
				background.setArcWidth(15);
				background.setArcHeight(15);

				// Círculo más pequeño (botón)
				knob = new Circle(6, Color.WHITE);
				knob.setTranslateX(-7); // Posición inicial

				// Contenedor del switch
				switchContainer = new StackPane(background, knob);
				switchContainer.setMinSize(40, 20);
				switchContainer.setMaxSize(40, 20);
				switchContainer.setAlignment(Pos.CENTER); // Centra los elementos dentro del StackPane
				switchContainer.setOnMouseClicked(event -> toggleSwitch());

				// Acción al presionar el ToggleButton
				toggleButton.setOnAction(event -> {
					ProfileAux currentProfile = getTableView().getItems().get(getIndex());
					boolean newValue = toggleButton.isSelected();
					currentProfile.setEnabled(newValue);
					animateSwitch(newValue);

				});
			}

			private void toggleSwitch() {
				boolean newValue = !toggleButton.isSelected();
				toggleButton.setSelected(newValue);
				animateSwitch(newValue);

				// Actualizar el objeto en la tabla
				ProfileAux currentProfile = getTableView().getItems().get(getIndex());
				if (currentProfile != null) {
					currentProfile.setEnabled(newValue);
					profileManagerActionController.saveProfile(currentProfile);
				}
			}

			private void animateSwitch(boolean isOn) {
				TranslateTransition slide = new TranslateTransition(Duration.millis(180), knob);
				slide.setToX(isOn ? 7 : -7); // Movimiento más corto
				background.setFill(isOn ? Color.GREEN : Color.LIGHTGRAY);
				slide.play();
			}

			@Override
			protected void updateItem(Boolean item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setGraphic(null);
				} else {
					toggleButton.setSelected(item);
					background.setFill(item ? Color.GREEN : Color.LIGHTGRAY);
					knob.setTranslateX(item ? 7 : -7);

					// Asegurar que el switchContainer se centre en la celda
					setGraphic(switchContainer);
					setAlignment(Pos.CENTER); // Centra el StackPane en la celda
				}
			}
		});

		sortedProfiles = new SortedList<>(profiles);
		sortedProfiles.comparatorProperty().bind(tableviewLogMessages.comparatorProperty());
		tableviewLogMessages.setItems(sortedProfiles);

	}

	@FXML
	void handleButtonAddProfile(ActionEvent event) {
		profileManagerActionController.showNewProfileDialog();
	}

	@FXML
        void handleButtonBulkUpdateProfiles(ActionEvent event) {
                profileManagerActionController.showBulkUpdateDialog(loadedProfileId, profiles, btnBulkUpdate);
        }

       @FXML
       void handleButtonExportProfiles(ActionEvent event) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.getExtensionFilters()
                                .add(new FileChooser.ExtensionFilter(JSON_FILTER_DESCRIPTION, JSON_FILTER_EXTENSION));
               File selectedFile = fileChooser.showSaveDialog(btnExportProfiles.getScene().getWindow());
               if (selectedFile != null) {
                       boolean exported = profileManagerActionController.exportProfiles(selectedFile);
                       Alert alert;
                       if (exported) {
                               alert = new Alert(Alert.AlertType.INFORMATION, "Profiles exported successfully", ButtonType.OK);
                       } else {
                               alert = new Alert(Alert.AlertType.ERROR, "Error exporting profiles", ButtonType.OK);
                       }
                       alert.showAndWait();
               }
       }

       @FXML
       void handleButtonImportProfiles(ActionEvent event) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.getExtensionFilters()
                                .add(new FileChooser.ExtensionFilter(JSON_FILTER_DESCRIPTION, JSON_FILTER_EXTENSION));
               File selectedFile = fileChooser.showOpenDialog(btnImportProfiles.getScene().getWindow());
               if (selectedFile != null) {
                       boolean imported = profileManagerActionController.importProfiles(selectedFile);
                       Alert alert;
                       if (imported) {
                               alert = new Alert(Alert.AlertType.INFORMATION, "Profiles imported successfully", ButtonType.OK);
                               loadProfiles();
                       } else {
                               alert = new Alert(Alert.AlertType.ERROR, "Error importing profiles", ButtonType.OK);
                       }
                       alert.showAndWait();
               }
       }

       public boolean saveProfile(ProfileAux profile) {
               return profileManagerActionController.saveProfile(profile);
       }

        public void loadProfiles() {
                profileManagerActionController.loadProfiles(dtoProfiles -> {
			Platform.runLater(() -> {
				profiles.clear();
				dtoProfiles.forEach(dtoProfile -> {
					ProfileAux profileAux = new ProfileAux(dtoProfile.getId(), dtoProfile.getName(), dtoProfile.getEmulatorNumber(), dtoProfile.getEnabled(), "NOT RUNNING");
					dtoProfile.getConfigs().forEach(config -> {
						profileAux.getConfigs().add(new ConfigAux(config.getNombreConfiguracion(), config.getValor()));
					});
					profiles.add(profileAux);
				});

				if (!profiles.isEmpty()) {
					ProfileAux selectedProfile = profiles.stream().filter(p -> p.getId().equals(loadedProfileId)).findFirst().orElse(profiles.get(0));

					notifyProfileLoadListeners(selectedProfile);
					loadedProfileId = selectedProfile.getId();
				}
			});
		});
	}

	private void notifyProfileLoadListeners(ProfileAux currentProfile) {
		if (profileLoadListeners != null) {
			profileLoadListeners.forEach(listener -> listener.onProfileLoad(currentProfile));
		}

	}

	public void addProfileLoadListener(IProfileLoadListener moduleController) {
		if (profileLoadListeners == null) {
			profileLoadListeners = new ArrayList<>();
		}
		profileLoadListeners.add(moduleController);
	}

	public void handleProfileStatusChange(DTOProfileStatus status) {
		Platform.runLater(() -> {
			profiles.stream().filter(p -> p.getId() == status.getId()).forEach(p -> {
				p.setStatus(status.getStatus());
			});
			tableviewLogMessages.refresh();
			tableviewLogMessages.sort();
		});

	}

	@Override
	public void notifyProfileChange(EnumConfigurationKey key, Object value) {
		try {

			ProfileAux loadedProfile = profiles.stream().filter(profile -> profile.getId().equals(loadedProfileId)).findFirst().orElse(null);

			if (loadedProfile == null) {
				return;
			}

			loadedProfile.setConfig(key, value);
			profileQueueExecutor.submit(() -> {
				profileManagerActionController.saveProfile(loadedProfile);
			});

                } catch (Exception e) {
                        ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "Profile Manager", "-", "Error while saving profile: " + e.getMessage());
                }
	}

}
