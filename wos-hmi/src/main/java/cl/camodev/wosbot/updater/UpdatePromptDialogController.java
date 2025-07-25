package cl.camodev.wosbot.updater;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class UpdatePromptDialogController {
    @FXML private Label messageLabel;
    @FXML private Button yesButton;
    @FXML private Button noButton;

    private boolean userChoice = false;

    @FXML
    private void initialize() {
        yesButton.setOnAction(e -> {
            userChoice = true;
            close();
        });
        noButton.setOnAction(e -> {
            userChoice = false;
            close();
        });
    }

    public void setMessage(String message) {
        messageLabel.setText(message);
    }

    public boolean getUserChoice() {
        return userChoice;
    }

    private void close() {
        Stage stage = (Stage) yesButton.getScene().getWindow();
        stage.close();
    }
}

