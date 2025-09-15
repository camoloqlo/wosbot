package cl.camodev.wosbot.taskmanager.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTODailyTaskStatus;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.profile.model.IProfileModel;
import cl.camodev.wosbot.profile.model.impl.ProfileCallback;
import cl.camodev.wosbot.profile.model.impl.ProfileModel;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.DelayedTaskRegistry;
import cl.camodev.wosbot.serv.task.TaskQueue;
import cl.camodev.wosbot.taskmanager.ITaskStatusChangeListener;
import cl.camodev.wosbot.taskmanager.model.ITaskStatusModel;
import cl.camodev.wosbot.taskmanager.model.impl.TaskCallback;
import cl.camodev.wosbot.taskmanager.model.impl.TaskStatusModel;
import cl.camodev.wosbot.taskmanager.model.TaskManagerAux;
import cl.camodev.wosbot.taskmanager.view.ScheduleTaskDialogController;
import cl.camodev.wosbot.taskmanager.view.TaskManagerLayoutController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class TaskManagerActionController implements ITaskStatusChangeListener {

	private TaskManagerLayoutController taskManagerLayoutController;

	private IProfileModel profileModel;

	private ITaskStatusModel taskStatusModel;

	public TaskManagerActionController(TaskManagerLayoutController taskManagerLayoutController) {
		this.taskManagerLayoutController = taskManagerLayoutController;
		this.profileModel = new ProfileModel();
		this.taskStatusModel = new TaskStatusModel();
		this.taskStatusModel.addTaskStatusChangeListener(this);
	}

	public void loadProfiles(ProfileCallback callback) {
		CompletableFuture.supplyAsync(() -> {
			List<DTOProfiles> profiles = profileModel.getProfiles();
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

	public void loadDailyTaskStatus(Long profileId, TaskCallback callback) {
		CompletableFuture.supplyAsync(() -> {
			List<DTODailyTaskStatus> taskStates = taskStatusModel.getDailyTaskStatusList(profileId);
			return taskStates;
		}).thenAccept(taskStates -> {

			if (callback != null) {
				callback.onTasksLoaded(taskStates);
			}

		}).exceptionally(ex -> {
			ex.printStackTrace();
			return null;
		});
	}

	@Override
	public void onTaskStatusChange(Long profileId, int taskNameId, DTOTaskState taskState) {
		if (taskManagerLayoutController != null) {
			taskManagerLayoutController.updateTaskStatus(profileId, taskNameId, taskState);
		}
	}

	/**
	 * Validates if the queue is active for a profile
	 */
	public boolean isQueueActive(Long profileId) {
		return ServScheduler.getServices().getQueueManager().getQueue(profileId) != null;
	}

	/**
	 * Validates if a task can be removed
	 */
	public boolean canRemoveTask(TaskManagerAux task) {
		return task.scheduledProperty().get() && !task.executingProperty().get();
	}

	/**
	 * Handles the execute now action
	 */
	public void executeTaskNow(TaskManagerAux task, boolean recurring) {
		DTOProfiles profile = findProfileById(task.getProfileId());
		if (profile == null) {
			System.err.println("Profile not found: " + task.getProfileId());
			return;
		}

		ServScheduler scheduler = ServScheduler.getServices();
		scheduler.updateDailyTaskStatus(profile, task.getTaskEnum(), LocalDateTime.now());
		scheduler.getQueueManager().getQueue(profile.getId()).executeTaskNow(task.getTaskEnum(), recurring);
	}

	/**
	 * Executes a task directly without asking anything.
	 * If the task is scheduled, it marks it as recurring.
	 * If not scheduled, it runs it one time only.
	 */
	public void executeTaskDirectly(TaskManagerAux task) {
		DTOProfiles profile = findProfileById(task.getProfileId());
		if (profile == null) {
			System.err.println("Profile not found: " + task.getProfileId());
			return;
		}

		ServScheduler scheduler = ServScheduler.getServices();
		TaskQueue queue = scheduler.getQueueManager().getQueue(profile.getId());
		if (queue == null) {
			System.err.println("No active queue found for profile: " + profile.getName());
			return;
		}

		if (task.scheduledProperty().get()) {
			// Task is already scheduled - mark as recurring and execute now
			scheduleTaskInQueue(queue, task.getTaskEnum(), LocalDateTime.now(), true, profile);
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskExecutor", profile.getName(),
				"Executed scheduled task " + task.getTaskEnum().getName() + " and marked as recurring");
		} else {
			// Task is not scheduled - execute once
			scheduler.updateDailyTaskStatus(profile, task.getTaskEnum(), LocalDateTime.now());
			queue.executeTaskNow(task.getTaskEnum(),false);
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskExecutor", profile.getName(),
				"Executed task " + task.getTaskEnum().getName() + " one time");
		}
	}

	/**
	 * Handles the remove task action with confirmation dialog
	 */
	public void removeTask(TaskManagerAux task, Runnable onSuccess) {
		DTOProfiles profile = findProfileById(task.getProfileId());
		if (profile == null) {
			System.err.println("Profile not found: " + task.getProfileId());
			return;
		}

		// Show confirmation dialog
		Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
		confirmDialog.setTitle("Remove Task");
		confirmDialog.setHeaderText("Remove task from scheduler");
		confirmDialog.setContentText("Are you sure you want to remove '" + task.getTaskEnum().getName() +
			"' from the scheduler for profile '" + profile.getName() + "'?");

		Optional<ButtonType> result = confirmDialog.showAndWait();
		if (result.isPresent() && result.get() == ButtonType.OK) {
			ServScheduler scheduler = ServScheduler.getServices();
			scheduler.removeTaskFromScheduler(profile.getId(), task.getTaskEnum());

			if (onSuccess != null) {
				onSuccess.run();
			}
		}
	}

	/**
	 * Shows a dialog to schedule task execution using FXML form
	 */
	public void showScheduleDialog(TaskManagerAux task) {
		DTOProfiles profile = findProfileById(task.getProfileId());
		if (profile == null) {
			System.err.println("Profile not found: " + task.getProfileId());
			return;
		}

		try {
			// Load FXML
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ScheduleTaskDialog.fxml"));
			Parent root = loader.load();

			// Get controller and set task data
			ScheduleTaskDialogController controller = loader.getController();
			controller.setTask(task);

			// Apply CSS styles
			root.getStylesheets().add(getClass().getResource("/styles/style.css").toExternalForm());

			// Get the parent window to make dialog modal to it
			Stage parentStage = (Stage) taskManagerLayoutController.getSceneNode().getScene().getWindow();

			// Create and configure stage
			Stage dialogStage = new Stage();
			dialogStage.setTitle("Schedule Task");

			// Make dialog modal - this prevents interaction with parent window
			dialogStage.initModality(Modality.APPLICATION_MODAL);

			// Set parent stage if available
			if (parentStage != null) {
				dialogStage.initOwner(parentStage);
			}

			// Configure dialog properties
			dialogStage.setResizable(false);
			dialogStage.setAlwaysOnTop(true); // Keep dialog on top
			dialogStage.setScene(new Scene(root));

			// Center the dialog
			dialogStage.centerOnScreen();

			// Show dialog and wait for result (this blocks interaction with parent)
			dialogStage.showAndWait();

			// Process results if confirmed
			if (controller.isConfirmed()) {
				if (controller.isImmediate()) {
					// Execute immediately
					executeTaskNow(task,controller.isRecurring());
				} else {
					// Schedule task with specified time
					scheduleTask(task, controller.getScheduledTime(), controller.isRecurring());
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			showErrorAlert("Error", "Could not load schedule dialog: " + e.getMessage());
		}
	}

	/**
	 * Schedules a task for execution at a specific time
	 */
	private void scheduleTask(TaskManagerAux task, LocalDateTime scheduledTime, boolean recurring) {
		DTOProfiles profile = findProfileById(task.getProfileId());
		if (profile == null) {
			System.err.println("Profile not found: " + task.getProfileId());
			return;
		}

		ServScheduler scheduler = ServScheduler.getServices();

		// Get the task queue for this profile
		TaskQueue queue = scheduler.getQueueManager().getQueue(profile.getId());
		if (queue == null) {
			showErrorAlert("Error", "No active queue found for profile: " + profile.getName());
			return;
		}

		// Use the existing executeTaskNow method but modify it for scheduled execution
		scheduleTaskInQueue(queue, task.getTaskEnum(), scheduledTime, recurring, profile);

		// Update the daily task status in the database
		scheduler.updateDailyTaskStatus(profile, task.getTaskEnum(), scheduledTime);

		showInfoAlert("Success", "Task scheduled successfully for: " +
			scheduledTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) +
			(recurring ? " (recurring)" : " (one-time)"));
	}

	/**
	 * Schedules a task in the queue with custom timing and recurrence settings
	 */
	private void scheduleTaskInQueue(TaskQueue queue, TpDailyTaskEnum taskEnum, LocalDateTime scheduledTime, boolean recurring, DTOProfiles profile) {
		// Create a custom implementation similar to executeTaskNow but with scheduled time
		DelayedTask prototype = DelayedTaskRegistry.create(taskEnum, profile);
		if (prototype == null) {
			showErrorAlert("Error", "Task not found: " + taskEnum.getName());
			return;
		}

		// Remove existing task if it exists
		queue.removeTask(taskEnum);

		// Schedule the task for the specified time
		prototype.reschedule(scheduledTime);
		prototype.setRecurring(recurring);

		// Add the task to the queue
		queue.addTask(prototype);

		// Update task state
		DTOTaskState taskState = new DTOTaskState();
		taskState.setProfileId(profile.getId());
		taskState.setTaskId(taskEnum.getId());
		taskState.setScheduled(true);
		taskState.setExecuting(false);
		taskState.setLastExecutionTime(LocalDateTime.now());
		taskState.setNextExecutionTime(scheduledTime);
		ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);

		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskScheduler", profile.getName(),
			"Scheduled " + taskEnum.getName() + " for " + scheduledTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) +
			(recurring ? " (recurring)" : " (one-time)"));
	}

	/**
	 * Shows an error alert dialog
	 */
	private void showErrorAlert(String title, String message) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
	}

	/**
	 * Shows an information alert dialog
	 */
	private void showInfoAlert(String title, String message) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
	}

	/**
	 * Helper method to find profile by ID
	 */
	public DTOProfiles findProfileById(Long profileId) {
		List<DTOProfiles> allProfiles = ServProfiles.getServices().getProfiles();
		return allProfiles.stream()
			.filter(p -> p.getId().equals(profileId))
			.findFirst()
			.orElse(null);
	}
}
