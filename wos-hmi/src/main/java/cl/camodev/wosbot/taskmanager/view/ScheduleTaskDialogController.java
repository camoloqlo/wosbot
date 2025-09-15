package cl.camodev.wosbot.taskmanager.view;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import cl.camodev.wosbot.taskmanager.model.TaskManagerAux;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ScheduleTaskDialogController {

    @FXML
    private Label lblTaskName;

    @FXML
    private CheckBox cbImmediate;

    @FXML
    private TextField timeField;

    @FXML
    private CheckBox cbRecurring;

    @FXML
    private Label lblInfo;

    private boolean confirmed = false;
    private LocalDateTime scheduledTime;
    private boolean immediate;
    private boolean recurring;

    public void initialize() {
        // Set up listeners
        cbImmediate.selectedProperty().addListener((obs, oldVal, newVal) -> {
            timeField.setDisable(newVal);
            if (newVal) {
                timeField.clear();
            }
        });
    }

    public void setTask(TaskManagerAux task) {
        // Set task name in header
        lblTaskName.setText("Schedule: " + task.getTaskEnum().getName());

        // Check if task is already scheduled to determine default behavior
        boolean isTaskScheduled = task.scheduledProperty().get();

        if (isTaskScheduled) {
            // Task is already scheduled: Enable both execute now AND recurring by default
            cbImmediate.setSelected(true);
            cbRecurring.setSelected(true);
            timeField.setDisable(true);

            // Pre-fill with current execution time for convenience
            if (task.getNextExecution() != null) {
                String currentTime = task.getNextExecution().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                timeField.setText(currentTime);
            }

            // Show info message
            lblInfo.setText("Task is currently scheduled");
            lblInfo.setVisible(true);
        } else {
            // Task is not scheduled: Only execute now by default
            cbImmediate.setSelected(true);
            cbRecurring.setSelected(false);
            timeField.setDisable(true);
            timeField.clear();
            lblInfo.setVisible(false);
        }
    }

    @FXML
    private void handleSchedule() {
        immediate = cbImmediate.isSelected();
        recurring = cbRecurring.isSelected();

        if (!immediate) {
            String timeInput = timeField.getText().trim();
            if (timeInput.isEmpty()) {
                showError("Please enter a valid time in HH:MM:SS format");
                return;
            }

            try {
                LocalTime time = LocalTime.parse(timeInput, DateTimeFormatter.ofPattern("HH:mm:ss"));
                scheduledTime = LocalDateTime.now().with(time);

                // If the time is in the past for today, schedule for tomorrow
                if (scheduledTime.isBefore(LocalDateTime.now())) {
                    scheduledTime = scheduledTime.plusDays(1);
                }
            } catch (DateTimeParseException e) {
                showError("Invalid time format. Please use HH:MM:SS format (e.g., 14:30:00)");
                return;
            }
        }

        confirmed = true;
        closeDialog();
    }

    @FXML
    private void handleCancel() {
        confirmed = false;
        closeDialog();
    }

    private void showError(String message) {
        lblInfo.setText("Error: " + message);
        lblInfo.setStyle("-fx-text-fill: #ff5722;");
        lblInfo.setVisible(true);
    }

    private void closeDialog() {
        Stage stage = (Stage) lblTaskName.getScene().getWindow();
        stage.close();
    }

    // Getters for the results
    public boolean isConfirmed() {
        return confirmed;
    }

    public boolean isImmediate() {
        return immediate;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }
}
