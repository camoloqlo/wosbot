package cl.camodev.wosbot.taskmanager.view;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTODailyTaskStatus;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.serv.IProfileDataChangeListener;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.TaskQueue;
import cl.camodev.wosbot.taskmanager.controller.TaskManagerActionController;
import cl.camodev.wosbot.taskmanager.model.TaskManagerAux;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TaskManagerLayoutController implements IProfileDataChangeListener {

	public Node getSceneNode() {
		return tabPaneProfiles;
	}

	private static final Comparator<TaskManagerAux> TASK_AUX_COMPARATOR = (a, b) -> {
		if (a.isScheduled() && !b.isScheduled())
			return -1;
		if (!a.isScheduled() && b.isScheduled())
			return 1;
		if (a.isExecuting() && !b.isExecuting())
			return -1;
		if (!a.isExecuting() && b.isExecuting())
			return 1;
		if (a.hasReadyTask() && !b.hasReadyTask())
			return -1;
		if (!a.hasReadyTask() && b.hasReadyTask())
			return 1;
		return Long.compare(a.getNearestMinutesUntilExecution(), b.getNearestMinutesUntilExecution());
	};

	private final Image iconTrue = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/indicators/green.png")));
    private final Image iconFalse = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/indicators/red.png")));
    private final Image iconWaiting = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/indicators/yellow.png")));
    private final Image iconIdle = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/indicators/grey.png")));
	private final ObjectProperty<LocalDateTime> globalClock = new SimpleObjectProperty<>(LocalDateTime.now());
	private final Map<Long, Tab> profileTabsMap = new HashMap<>();
	private final Map<Long, ObservableList<TaskManagerAux>> tasks = new HashMap<>();
	private final Map<Long, FilteredList<TaskManagerAux>> filteredTasks = new HashMap<>();
	private final TaskManagerActionController taskManagerActionController = new TaskManagerActionController(this);

	@FXML
	private TabPane tabPaneProfiles;

	@FXML
	private TextField txtFilterTaskName;

	@FXML
	private Button btnToggleView;
	
	@FXML
	private javafx.scene.layout.VBox ganttViewContainer;
	
	private boolean showingGanttView = false;
	private Node ganttViewNode = null;
	private TaskGanttOverviewController ganttViewController = null;

	@FXML
	public void initialize() {
		ServProfiles.getServices().addProfileDataChangeListener(this);
		loadProfiles();
		setupFilterListener();

		Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(1), evt -> updateTimeValues()));
		ticker.setCycleCount(Animation.INDEFINITE);
		ticker.play();
	}
	
	@FXML
	private void handleToggleView() {
		showingGanttView = !showingGanttView;
		
		if (showingGanttView) {
			// Show Gantt view
			showGanttView();
			btnToggleView.setText("📋 Table View");
		} else {
			// Show standard table view
			showTableView();
			btnToggleView.setText("📊 Timeline View");
		}
	}
	
	private void showGanttView() {
		// Hide table view
		tabPaneProfiles.setVisible(false);
		tabPaneProfiles.setManaged(false);
		
		// Load and show Gantt view if not already loaded
		if (ganttViewNode == null) {
			try {
				javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
					getClass().getResource("/cl/camodev/wosbot/taskmanager/view/TaskGanttOverview.fxml")
				);
				ganttViewNode = loader.load();
				ganttViewController = loader.getController();
			} catch (java.io.IOException e) {
				e.printStackTrace();
				// If loading fails, revert to table view
				showTableView();
				showingGanttView = false;
				btnToggleView.setText("📊 Timeline View");
				return;
			}
		}
		if (ganttViewController != null && txtFilterTaskName != null) {
			ganttViewController.setTaskFilter(txtFilterTaskName.getText());
		}
		
		// Add to container if not already there
		if (!ganttViewContainer.getChildren().contains(ganttViewNode)) {
			ganttViewContainer.getChildren().clear();
			ganttViewContainer.getChildren().add(ganttViewNode);
		}
		
		ganttViewContainer.setVisible(true);
		ganttViewContainer.setManaged(true);
	}
	
	private void showTableView() {
		// Hide Gantt view
		ganttViewContainer.setVisible(false);
		ganttViewContainer.setManaged(false);
		
		// Show table view
		tabPaneProfiles.setVisible(true);
		tabPaneProfiles.setManaged(true);
	}

	private void setupFilterListener() {
		if (txtFilterTaskName != null) {
			txtFilterTaskName.textProperty().addListener((observable, oldValue, newValue) -> {
				applyFilter(newValue);
			});
		}
	}

	private void applyFilter(String filterText) {
		String filter = filterText == null ? "" : filterText.toLowerCase().trim();

		filteredTasks.forEach((profileId, filteredList) -> {
			filteredList.setPredicate(task -> {
				if (filter.isEmpty()) {
					return true;
				}
				return task.getTaskName().toLowerCase().contains(filter);
			});
		});

		if (ganttViewController != null) {
			ganttViewController.setTaskFilter(filterText);
		}
	}

	// Method to update time-dependent values and trigger reordering
	private void updateTimeValues() {
		Platform.runLater(() -> {
			LocalDateTime now = LocalDateTime.now();
			globalClock.set(now);

			// Update all tables for all profiles
			tasks.forEach((profileId, dataList) -> {
				boolean needsReorder = false;

				for (TaskManagerAux task : dataList) {
					if (task.getNextExecution() != null) {
						long newSeconds = ChronoUnit.SECONDS.between(now, task.getNextExecution());
						long oldSeconds = task.getNearestMinutesUntilExecution();
						boolean newReady = newSeconds <= 0;
						boolean oldReady = task.hasReadyTask();

						// Update values if they changed
						if (newSeconds != oldSeconds || newReady != oldReady) {
							task.setNearestMinutesUntilExecution(Math.max(0, newSeconds));
							task.setHasReadyTask(newReady);
							needsReorder = true;
						}
					}
				}

				// Reorder the table if any time values changed
				if (needsReorder) {
					FXCollections.sort(dataList, TASK_AUX_COMPARATOR);
				}
			});
		});
	}

	private void loadProfiles() {
		taskManagerActionController.loadProfiles(dtoProfiles -> {
			Platform.runLater(() -> {
				if (tabPaneProfiles == null)
					return;

				// Create a set of current profile IDs for efficient lookup
				Set<Long> currentProfileIds = dtoProfiles.stream()
						.map(DTOProfiles::getId)
						.collect(Collectors.toSet());

				// Remove tabs for deleted profiles
				profileTabsMap.entrySet().removeIf(entry -> {
					if (!currentProfileIds.contains(entry.getKey())) {
						tabPaneProfiles.getTabs().remove(entry.getValue());
						tasks.remove(entry.getKey());
						filteredTasks.remove(entry.getKey());
						return true;
					}
					return false;
				});

				// Add new tabs and update existing ones
				for (DTOProfiles profile : dtoProfiles) {
					Tab existingTab = profileTabsMap.get(profile.getId());
					if (existingTab == null) {
						// New profile -> create tab
						Tab newTab = createProfileTab(profile);
						profileTabsMap.put(profile.getId(), newTab);
						tabPaneProfiles.getTabs().add(newTab);
					} else {
						// Existing profile -> update name
						if (!existingTab.getText().equals(profile.getName())) {
							existingTab.setText(profile.getName());
						}
					}
				}

				// If no tab is selected and there are tabs, select the first one
				if (tabPaneProfiles.getSelectionModel().getSelectedItem() == null && !tabPaneProfiles.getTabs().isEmpty()) {
					tabPaneProfiles.getSelectionModel().selectFirst();
				}
			});
		});
	}

	private Tab createProfileTab(DTOProfiles profile) {
		Tab tab = new Tab(profile.getName());
		tab.setClosable(false);
		tab.setUserData(profile.getId());

		// 1) Prepare the table and the empty observable list
		ObservableList<TaskManagerAux> dataList = FXCollections.observableArrayList();

		// 2) Create FilteredList for filtering
		FilteredList<TaskManagerAux> filteredList = new FilteredList<>(dataList);

		TableView<TaskManagerAux> table = createTaskTable();
		table.setItems(filteredList);

		// Save both lists for future updates
		tasks.put(profile.getId(), dataList);
		filteredTasks.put(profile.getId(), filteredList);
		tab.setContent(table);

		// 3) Call the asynchronous builder and update the table when ready
		buildTaskManagerList(profile, list -> {
			// Always from JavaFX Application Thread
			dataList.setAll(list);
			FXCollections.sort(dataList, TASK_AUX_COMPARATOR);

			// Apply the current filter if it exists
			if (txtFilterTaskName != null && !txtFilterTaskName.getText().isEmpty()) {
				applyFilter(txtFilterTaskName.getText());
			}
		});

		return tab;
	}

	@Override
	public void onProfileDataChanged(DTOProfiles profile) {
		Platform.runLater(() -> {
			loadProfiles();
		});
	}

//	private void refreshProfileTab(DTOProfiles profile) {
//		ObservableList<TaskManagerAux> dataList = tasks.get(profile.getId());
//		if (dataList == null)
//			return;
//
//		List<TaskManagerAux> updated = buildTaskManagerList(profile);
//		dataList.setAll(updated);
//	}

	/**
	 * Reloads the task status and, when available, builds the TaskManagerAux list and delivers it to the consumer.
	 */
	private void buildTaskManagerList(DTOProfiles profile, Consumer<List<TaskManagerAux>> onListReady) {
		// Now `statuses` is a List<DTODailyTaskStatus>
		taskManagerActionController.loadDailyTaskStatus(profile.getId(), (List<DTODailyTaskStatus> statuses) -> {
			List<TaskManagerAux> list = Arrays.stream(TpDailyTaskEnum.values()).map(task -> {
				// Search for the status whose ID matches the task ID
//				System.out.println(">>> statuses.size=" + statuses.size() + "  searching for id=" + task.getId());

				DTODailyTaskStatus s = statuses.stream().filter(st -> st.getIdTpDailyTask() == task.getId()) // or st.getTaskId()
						.findFirst().orElse(null);

				if (s == null) {
					return new TaskManagerAux(task.getName(), null, null, task, profile.getId(), Long.MAX_VALUE, false, false, false);
				}

				long diffInSeconds = Long.MAX_VALUE;
				boolean ready = false;
				if (s.getNextSchedule() != null) {
					diffInSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), s.getNextSchedule());
					if (diffInSeconds <= 0) {
						ready = true;
						diffInSeconds = 0;
					}
				}

				boolean scheduled = Optional.ofNullable(ServScheduler.getServices().getQueueManager().getQueue(profile.getId())).map(q -> q.isTaskScheduled(task)).orElse(false);

				return new TaskManagerAux(task.getName(), s.getLastExecution(), s.getNextSchedule(), task, profile.getId(), diffInSeconds, ready, scheduled, false);
			}).sorted((a, b) -> {
				if (a.isScheduled() && !b.isScheduled())
					return -1;
				if (!a.isScheduled() && b.isScheduled())
					return 1;
				if (a.hasReadyTask() && !b.hasReadyTask())
					return -1;
				if (!a.hasReadyTask() && b.hasReadyTask())
					return 1;
				return Long.compare(a.getNearestMinutesUntilExecution(), b.getNearestMinutesUntilExecution());
			}).collect(Collectors.toList());

			Platform.runLater(() -> onListReady.accept(list));
		});
	}

	private TableView<TaskManagerAux> createTaskTable() {
		TableView<TaskManagerAux> table = new TableView<>();
		table.getStyleClass().add("table-view");


		// Task Name column
		TableColumn<TaskManagerAux, String> colTaskName = new TableColumn<>("Task Name");
		colTaskName.setCellValueFactory(cellData -> cellData.getValue().taskNameProperty());
		colTaskName.setPrefWidth(200);
		colTaskName.setCellFactory(column -> new TableCell<TaskManagerAux, String>() {
			private final ImageView imageView = new ImageView();

			{
				// Adjust icon size if necessary
				imageView.setFitWidth(16);
				imageView.setFitHeight(16);
			}

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
					setGraphic(null);
					setStyle("");
				} else {
					setText(item);
					// Get the current row's object
					TaskManagerAux task = getTableRow().getItem();
					if (task != null) {
						// Choose the icon based on the boolean property
                        ImageView iv = getTaskStatusIcon(task, imageView);
                        setGraphic(iv);
						setContentDisplay(ContentDisplay.LEFT);
					} else {
						setGraphic(null);
					}
					setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
				}
			}
		});

		// Last Execution column
		TableColumn<TaskManagerAux, String> colLastExecution = new TableColumn<>("Last Execution");
		colLastExecution.setCellValueFactory(cellData -> {
			TaskManagerAux t = cellData.getValue();
			return Bindings.createStringBinding(() -> {
				return UtilTime.formatLastExecution(t.getLastExecution());
			}, t.nextExecutionProperty(), t.executingProperty(), globalClock);
		});
		colLastExecution.setPrefWidth(150);
		colLastExecution.setCellFactory(column -> new TableCell<>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
					setStyle("");
				} else {
					setText(item);
					setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
				}
			}
		});

		// Next Execution column
		TableColumn<TaskManagerAux, String> colNextExecution = new TableColumn<>("Next Execution");
		colNextExecution.setPrefWidth(150);
		colNextExecution.setCellValueFactory(cellData -> {
			TaskManagerAux t = cellData.getValue();
			return Bindings.createStringBinding(() -> {
				LocalDateTime now = globalClock.get();
				LocalDateTime next = t.getNextExecution();
				if (t.executingProperty().get()) {
					return "Executing";
				}
				if (next == null) {
					return "Never";
				}
				long diff = java.time.Duration.between(now, next).getSeconds();
				if (diff <= 0) {
					return "Ready";
				} else if (diff < 60) {
					return diff + "s";
				} else if (diff < 3600) {
					long min = diff / 60;
					return min + "m";
				} else if (diff < 86400) {
					long h = diff / 3600;
					long m = (diff % 3600) / 60;
					return h + "h " + m + "m";
				} else {
					long d = diff / 86400;
					long h = (diff % 86400) / 3600;
					long m = (diff % 3600) / 60;
					return d + "d " + h + "h " + m + "m";
				}
			}, t.nextExecutionProperty(), t.executingProperty(), globalClock);
		});

		colNextExecution.setCellFactory(column -> new TableCell<>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
					getStyleClass().removeAll(
							"next-execution-ready", "next-execution-never", "next-execution-executing", "next-execution-seconds",
							"next-execution-minutes-short", "next-execution-minutes-medium", "next-execution-minutes-long",
							"next-execution-hours", "next-execution-days"
					);
					setStyle("");
					return;
				}
				setText(item);

				// Remove all custom classes first
				getStyleClass().removeAll(
						"next-execution-ready", "next-execution-never", "next-execution-executing", "next-execution-seconds",
						"next-execution-minutes-short", "next-execution-minutes-medium", "next-execution-minutes-long",
						"next-execution-hours", "next-execution-days"
				);

				// Assign class based on value
				if ("Ready".equals(item)) {
					getStyleClass().add("next-execution-ready");
				} else if ("Never".equals(item) || "--".equals(item)) {
					getStyleClass().add("next-execution-never");
				} else if ("Executing".equals(item)) {
					getStyleClass().add("next-execution-executing");
				} else if (item.endsWith("s")) {
					getStyleClass().add("next-execution-seconds");
				} else if (item.matches("\\d+m")) {
					int min = Integer.parseInt(item.replace("m", ""));
					if (min <= 15) {
						getStyleClass().add("next-execution-minutes-short");
					} else if (min <= 60) {
						getStyleClass().add("next-execution-minutes-medium");
					} else {
						getStyleClass().add("next-execution-minutes-long");
					}
				} else if (item.matches("\\d+h \\d+m")) {
					getStyleClass().add("next-execution-hours");
				} else if (item.matches("\\d+d \\d+h \\d+m")) {
					getStyleClass().add("next-execution-days");
				}
			}
		});

		TableColumn<TaskManagerAux, Void> colActions = new TableColumn<>("Actions");
		colActions.setPrefWidth(250); // Increase width to accommodate the third button
		colActions.setCellFactory(column -> new TableCell<>() {
			private final Button btnSchedule = new Button("Schedule");
			private final Button btnRemove = new Button("Remove");
			private final Button btnExecute = new Button("Execute");

			{
				btnSchedule.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 11px; " +
						"-fx-padding: 4px 8px; -fx-border-radius: 3px; -fx-background-radius: 3px;");
				btnRemove.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 11px; " +
						"-fx-padding: 4px 8px; -fx-border-radius: 3px; -fx-background-radius: 3px;");
				btnExecute.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 11px; " +
						"-fx-padding: 4px 8px; -fx-border-radius: 3px; -fx-background-radius: 3px;");

				btnSchedule.setOnAction(ev -> {
					TaskManagerAux item = getTableView().getItems().get(getIndex());
					taskManagerActionController.showScheduleDialog(item);
				});

				btnRemove.setOnAction(ev -> {
					TaskManagerAux item = getTableView().getItems().get(getIndex());
					DTOProfiles profile = taskManagerActionController.findProfileById(item.getProfileId());

					taskManagerActionController.removeTask(item, () -> {
						// Refresh the table after successful removal
						buildTaskManagerList(profile, list -> {
							ObservableList<TaskManagerAux> dataList = tasks.get(profile.getId());
							if (dataList != null) {
								dataList.setAll(list);
								FXCollections.sort(dataList, TASK_AUX_COMPARATOR);
							}
						});
					});
				});

				btnExecute.setOnAction(ev -> {
					TaskManagerAux item = getTableView().getItems().get(getIndex());
					DTOProfiles profile = taskManagerActionController.findProfileById(item.getProfileId());

					taskManagerActionController.executeTaskDirectly(item);

					// Refresh the table after execution
					buildTaskManagerList(profile, list -> {
						ObservableList<TaskManagerAux> dataList = tasks.get(profile.getId());
						if (dataList != null) {
							dataList.setAll(list);
							FXCollections.sort(dataList, TASK_AUX_COMPARATOR);
						}
					});
				});
			}

			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);
				if (empty) {
					setGraphic(null);
				} else {
					// Get the task data to check its state
					TaskManagerAux task = getTableRow().getItem();

					if (task != null) {
						// Check if queue is active for this profile
						boolean queueActive = ServScheduler.getServices().getQueueManager().getQueue(task.getProfileId()) != null;

						// Enable/disable schedule button based on queue status
						btnSchedule.setDisable(!queueActive);

						// Update schedule button style when disabled
						if (!queueActive) {
							btnSchedule.setStyle("-fx-background-color: #757575; -fx-text-fill: #bdbdbd; -fx-font-size: 11px; " +
									"-fx-padding: 4px 8px; -fx-border-radius: 3px; -fx-background-radius: 3px;");
						} else {
							btnSchedule.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 11px; " +
									"-fx-padding: 4px 8px; -fx-border-radius: 3px; -fx-background-radius: 3px;");
						}

						// Enable/disable remove button based on task state
						boolean canRemove = task.scheduledProperty().get() && !task.executingProperty().get();
						btnRemove.setDisable(!canRemove);

						// Update remove button style when disabled
						if (!canRemove) {
							btnRemove.setStyle("-fx-background-color: #757575; -fx-text-fill: #bdbdbd; -fx-font-size: 11px; " +
									"-fx-padding: 4px 8px; -fx-border-radius: 3px; -fx-background-radius: 3px;");
						} else {
							btnRemove.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 11px; " +
									"-fx-padding: 4px 8px; -fx-border-radius: 3px; -fx-background-radius: 3px;");
						}

						// Enable/disable execute button based on queue status and task execution state
						boolean canExecute = queueActive && !task.executingProperty().get();
						btnExecute.setDisable(!canExecute);

						// Update execute button style when disabled
						if (!canExecute) {
							btnExecute.setStyle("-fx-background-color: #757575; -fx-text-fill: #bdbdbd; -fx-font-size: 11px; " +
									"-fx-padding: 4px 8px; -fx-border-radius: 3px; -fx-background-radius: 3px;");
						} else {
							btnExecute.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 11px; " +
									"-fx-padding: 4px 8px; -fx-border-radius: 3px; -fx-background-radius: 3px;");
						}
					}

					// Create HBox to hold all three buttons
					HBox buttonBox = new HBox(5);
					buttonBox.getChildren().addAll(btnSchedule, btnRemove, btnExecute);
					setGraphic(buttonBox);
				}
			}
		});

		table.getColumns().add(colTaskName);
		table.getColumns().add(colLastExecution);
		table.getColumns().add(colNextExecution);
		table.getColumns().add(colActions);
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

		return table;
	}

    public void updateTabOrder () {
        profileTabsMap.forEach((profileId, tab) -> {
            if (ServScheduler.getServices().getQueueManager().getQueue(profileId) != null) {
                DTOProfiles profile = ServScheduler.getServices().getQueueManager().getQueue(profileId).getProfile();
                ImageView iv = new ImageView();
                if (profile.getQueuePosition() == 0) {
                    iv.setImage(iconTrue);
                } else if (profile.getQueuePosition() == Integer.MAX_VALUE && profile.getEnabled()) {
                    iv.setImage(iconIdle);
                } else if (!profile.getEnabled()) {
                    iv.setImage(iconFalse);
                } else if (profile.getQueuePosition() != 0 && profile.getQueuePosition() != Integer.MAX_VALUE){
                    iv.setImage(iconWaiting);
                }
                iv.setFitWidth(16);
                iv.setFitHeight(16);
                profileTabsMap.get(profileId).graphicProperty().set(iv);
            }
        });

        List<Tab> sortedTabs = profileTabsMap.entrySet().stream()
                .sorted((e1, e2) -> {
                    if (Objects.equals(e1.getKey(), e2.getKey())) return 0;
                    TaskQueue q1 = ServScheduler.getServices().getQueueManager().getQueue(e1.getKey());
                    TaskQueue q2 = ServScheduler.getServices().getQueueManager().getQueue(e2.getKey());
                    boolean b1 = q1 != null;
                    boolean b2 = q2 != null;
                    if (b1 && b2) {
                        int queuePos1 = q1.getProfile().getQueuePosition();
                        int queuePos2 = q2.getProfile().getQueuePosition();
                        if (queuePos1 == Integer.MAX_VALUE && queuePos2 == Integer.MAX_VALUE) return q1.getDelay().isAfter(q2.getDelay()) ? 1 : -1;
                        return queuePos1 > queuePos2 ? 1 : -1;
                    }
                    return b1 ? -1 : 1; // true first
                })
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        tabPaneProfiles.getTabs().setAll(sortedTabs);
    }

    private ImageView getTaskStatusIcon(TaskManagerAux task, ImageView iv) {
        boolean flag = task.scheduledProperty().get();
        boolean waiting = !task.isExecuting() && !task.hasReadyTask() && task.isScheduled() && ChronoUnit.SECONDS.between(LocalDateTime.now(), task.getNextExecution()) >= 60;
        if (waiting) {
            iv.setImage(iconWaiting);
        } else if (flag) {
            iv.setImage(iconTrue);
        } else {
            if (Objects.equals(task.getTaskName(), "Initialize") || task.getNextExecution() != null) {
                iv.setImage(iconWaiting);
            }
            iv.setImage(iconIdle);
        }
        return iv;
    }

	public void updateTaskStatus(Long profileId, int taskNameId, DTOTaskState taskState) {
		Platform.runLater(() -> {
            ObservableList<TaskManagerAux> dataList = tasks.get(profileId);
            if (dataList == null)
                return;
            Optional<TaskManagerAux> optionalTask = dataList.stream().filter(aux -> aux.getTaskEnum().getId() == taskNameId).findFirst();
            if (!optionalTask.isPresent())
                return;

            TaskManagerAux taskAux = optionalTask.get();
            taskAux.setLastExecution(taskState.getLastExecutionTime());
            taskAux.setNextExecution(taskState.getNextExecutionTime());
            taskAux.setScheduled(taskState.isScheduled());
            taskAux.setExecuting(taskState.isExecuting());
            taskAux.setHasReadyTask(taskState.getNextExecutionTime() != null && ChronoUnit.SECONDS.between(LocalDateTime.now(), taskState.getNextExecutionTime()) <= 0);
            taskAux.setNearestMinutesUntilExecution(taskState.getNextExecutionTime() != null ? ChronoUnit.SECONDS.between(LocalDateTime.now(), taskState.getNextExecutionTime()) : Long.MAX_VALUE);

            FXCollections.sort(dataList, TASK_AUX_COMPARATOR);

            updateTabOrder();
        });
	}
}

