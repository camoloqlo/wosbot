package cl.camodev.wosbot.bear.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.controlsfx.control.CheckComboBox;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

/**
 * Controller for the Bear Trap view.
 * Manages the UI for configuring Bear Trap task settings.
 */
public class BearTrapLayoutController extends AbstractProfileController {

    // Enable checkbox
    @FXML
    private CheckBox checkBoxEnableBearTrap;

    // Trap Schedule section
    @FXML
    private TextField textFieldScheduleDateTime;

    @FXML
    private TextField textFieldPreparationTime;

    // Preparation section
    @FXML
    private CheckBox checkBoxActivePets;

    @FXML
    private CheckBox checkBoxRecallTroops;

    // Trap Configuration section
    @FXML
    private ComboBox<Integer> comboBoxTrapNumber;

    @FXML
    private CheckBox checkBoxCallRally;

    @FXML
    private ComboBox<Integer> comboBoxRallyFlag;

    @FXML
    private CheckBox checkBoxEnableJoin;

    @FXML
    private CheckComboBox<Integer> checkComboBoxJoinFlag;

    @FXML
    private javafx.scene.control.Label labelDateTimeError;

    @FXML
    private void initialize() {
        // Map enable checkbox
        checkBoxMappings.put(checkBoxEnableBearTrap, EnumConfigurationKey.BEAR_TRAP_EVENT_BOOL);

        // Map text fields
        textFieldMappings.put(textFieldScheduleDateTime, EnumConfigurationKey.BEAR_TRAP_SCHEDULE_DATETIME_STRING);
        textFieldMappings.put(textFieldPreparationTime, EnumConfigurationKey.BEAR_TRAP_PREPARATION_TIME_INT);

        // Map preparation checkboxes
        checkBoxMappings.put(checkBoxActivePets, EnumConfigurationKey.BEAR_TRAP_ACTIVE_PETS_BOOL);
        checkBoxMappings.put(checkBoxRecallTroops, EnumConfigurationKey.BEAR_TRAP_RECALL_TROOPS_BOOL);

        // Initialize trap number ComboBox (1 or 2)
        comboBoxTrapNumber.getItems().addAll(1, 2);
        comboBoxMappings.put(comboBoxTrapNumber, EnumConfigurationKey.BEAR_TRAP_NUMBER_INT);

        // Map rally checkbox
        checkBoxMappings.put(checkBoxCallRally, EnumConfigurationKey.BEAR_TRAP_CALL_RALLY_BOOL);

        // Initialize rally flag ComboBox (1-8)
        comboBoxRallyFlag.getItems().addAll(1, 2, 3, 4, 5, 6, 7, 8);
        comboBoxMappings.put(comboBoxRallyFlag, EnumConfigurationKey.BEAR_TRAP_RALLY_FLAG_INT);

        // Map join checkbox
        checkBoxMappings.put(checkBoxEnableJoin, EnumConfigurationKey.BEAR_TRAP_JOIN_RALLY_BOOL);

        // Initialize join flag CheckComboBox (1-8)
        checkComboBoxJoinFlag.getItems().addAll(1, 2, 3, 4, 5, 6, 7, 8);
        checkComboBoxMappings.put(checkComboBoxJoinFlag, EnumConfigurationKey.BEAR_TRAP_JOIN_FLAG_INT);

        // Bind disable property of all controls to the enable checkbox (inverted)
        // When checkbox is NOT selected, controls are disabled
        textFieldScheduleDateTime.disableProperty().bind(checkBoxEnableBearTrap.selectedProperty().not());
        textFieldPreparationTime.disableProperty().bind(checkBoxEnableBearTrap.selectedProperty().not());
        checkBoxActivePets.disableProperty().bind(checkBoxEnableBearTrap.selectedProperty().not());
        checkBoxRecallTroops.disableProperty().bind(checkBoxEnableBearTrap.selectedProperty().not());
        comboBoxTrapNumber.disableProperty().bind(checkBoxEnableBearTrap.selectedProperty().not());
        checkBoxCallRally.disableProperty().bind(checkBoxEnableBearTrap.selectedProperty().not());
        comboBoxRallyFlag.disableProperty().bind(checkBoxEnableBearTrap.selectedProperty().not());
        checkBoxEnableJoin.disableProperty().bind(checkBoxEnableBearTrap.selectedProperty().not());
        checkComboBoxJoinFlag.disableProperty().bind(checkBoxEnableBearTrap.selectedProperty().not());

        // Set initial visibility of rally flag CheckComboBox based on call rally checkbox state
        comboBoxRallyFlag.setVisible(checkBoxCallRally.isSelected());
        comboBoxRallyFlag.setManaged(checkBoxCallRally.isSelected());

        // Set initial visibility of join flag CheckComboBox based on enable join checkbox state
        checkComboBoxJoinFlag.setVisible(checkBoxEnableJoin.isSelected());
        checkComboBoxJoinFlag.setManaged(checkBoxEnableJoin.isSelected());

        // Set up listener to show/hide rally flag CheckComboBox based on call rally checkbox state
        checkBoxCallRally.selectedProperty().addListener((obs, oldVal, newVal) -> {
            comboBoxRallyFlag.setVisible(newVal);
            comboBoxRallyFlag.setManaged(newVal);
        });

        // Set up listener to show/hide join flag CheckComboBox based on enable join checkbox state
        checkBoxEnableJoin.selectedProperty().addListener((obs, oldVal, newVal) -> {
            checkComboBoxJoinFlag.setVisible(newVal);
            checkComboBoxJoinFlag.setManaged(newVal);
        });

        // Set up date/time validation for textFieldScheduleDateTime
        textFieldScheduleDateTime.textProperty().addListener((obs, oldVal, newVal) -> {
            validateDateTime(newVal);
        });

        // Initialize change events (inherited from AbstractProfileController)
        setupDateTimeFieldHelpers();
        initializeChangeEvents();
    }

    /**
     * Validates the date/time format and shows error messages if invalid.
     * Expected format: dd-MM-yyyy HH:mm (24-hour format)
     */
    private void setupDateTimeFieldHelpers() {
        // Visual hints
        textFieldScheduleDateTime.setPromptText("dd-MM-yyyy HH:mm");
        textFieldScheduleDateTime.setTooltip(new Tooltip("Use format: dd-MM-yyyy HH:mm (e.g., 10-06-2025 19:30)"));

        // TextFormatter that auto-inserts separators and restricts to the mask "##-##-#### ##:##"
        // It tolerates edits/paste: strips non-digits, rebuilds with separators, trims to max length.
        TextFormatter<String> formatter = getStringTextFormatter();

        textFieldScheduleDateTime.setTextFormatter(formatter);

        // Optional: reformat/pad leading zeros on focus lost (e.g., "1-2-2025 3:5" → "01-02-2025 03:05")
        textFieldScheduleDateTime.focusedProperty().addListener((obs, had, has) -> {
            if (!has) {
                String t = textFieldScheduleDateTime.getText();
                if (t == null || t.isBlank()) return;
                // If user left mid-way, attempt to normalize only if we have enough digits
                String digits = t.replaceAll("\\D", "");
                if (digits.length() == 12) {
                    // Rebuild strictly with zero padding
                    String mm = digits.substring(0, 2);
                    String dd = digits.substring(2, 4);
                    String yyyy = digits.substring(4, 8);
                    String HH = digits.substring(8, 10);
                    String mm2 = digits.substring(10, 12);
                    textFieldScheduleDateTime.setText(mm + "-" + dd + "-" + yyyy + " " + HH + ":" + mm2);
                }
            }
        });
    }

    private static @NotNull TextFormatter<String> getStringTextFormatter() {
        final int maxDigits = 12;              // MM dd yyyy HH mm = 2+2+4+2+2
        final int maxLenMasked = 16;           // "MM-dd-yyyy HH:mm"

        TextFormatter<String> formatter = new TextFormatter<>(change -> {
            if (change.isContentChange()) {
                // Build a digits-only string from the prospective text
                StringBuilder digits = new StringBuilder();
                String newText = change.getControlNewText();
                for (int i = 0; i < newText.length(); i++) {
                    char c = newText.charAt(i);
                    if (Character.isDigit(c)) digits.append(c);
                }
                // Limit to maxDigits
                if (digits.length() > maxDigits) {
                    digits.setLength(maxDigits);
                }

                // Rebuild with separators at fixed cut points
                StringBuilder masked = new StringBuilder();
                for (int d = 0; d < digits.length(); d++) {
                    // Insert '-' after MM and dd, space after yyyy, ':' after HH
                    if (d == 2) masked.append('-');
                    if (d == 4) masked.append('-');
                    if (d == 8) masked.append(' ');
                    if (d == 10) masked.append(':');
                    masked.append(digits.charAt(d));
                }

                // Enforce total max length
                if (masked.length() > maxLenMasked) masked.setLength(maxLenMasked);

                // Replace the entire text; we also move the caret to the end of inserted part
                change.setRange(0, change.getControlText().length());
                change.setText(masked.toString());
                change.setCaretPosition(masked.length());
                change.setAnchor(masked.length());
            }
            return change;
        });
        return formatter;
    }

    private void validateDateTime(String dateTimeText) {
        // Clear error UI first
        labelDateTimeError.setText("");
        textFieldScheduleDateTime.setStyle("");

        // Allow empty (optional field)
        if (dateTimeText == null || dateTimeText.trim().isEmpty()) return;

        // Expected mask: dd-MM-yyyy HH:mm
        final String regex = "\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2}";
        if (!dateTimeText.matches(regex)) {
            labelDateTimeError.setText("Invalid format. Use: dd-MM-yyyy HH:mm (e.g., 10-06-2025 19:30)");
            textFieldScheduleDateTime.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            return;
        }

        // Strict parse using a strict resolver to catch invalid dates like 13-32-2025, 24:61, etc.
        try {
            DateTimeFormatter formatter =
                    new DateTimeFormatterBuilder()
                            .parseStrict()
                            .appendPattern("dd-MM-yyyy HH:mm")
                            .toFormatter(Locale.ROOT);

            LocalDateTime.parse(dateTimeText, formatter);

            // Passed validation
            labelDateTimeError.setText("");
            textFieldScheduleDateTime.setStyle("");

        } catch (java.time.DateTimeException ex) {
            // Catches DateTimeParseException and invalid ranges under strict mode
            labelDateTimeError.setText("Invalid date/time values (check month/day/hour/minute).");
            textFieldScheduleDateTime.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
        }
    }
}
