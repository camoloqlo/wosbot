package cl.camodev.wosbot.events.view;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

public class EventsLayoutController extends AbstractProfileController {
    @FXML
    private CheckBox checkBoxTundraEvent, checkBoxTundraUseGems, checkBoxTundraSSR, checkBoxHeroMission,
            checkBoxMercenaryEvent, checkBoxJourneyofLight, checkBoxMyriadBazaar, checkBoxTundraEventActivationHour;

    @FXML
    private TextField textfieldTundraActivationHour;

    @FXML
    private ComboBox<Integer> comboBoxMercenaryFlag, comboBoxHeroMissionFlag;


    @FXML
    private Label labelDateTimeError;

    @FXML
    private void initialize() {
        // Set up flag combobox with integer values
        comboBoxMercenaryFlag.getItems().addAll(0, 1, 2, 3, 4, 5, 6, 7, 8);
        comboBoxHeroMissionFlag.getItems().addAll(0, 1, 2, 3, 4, 5, 6, 7, 8);

        // Map UI elements to configuration keys
        comboBoxMappings.put(comboBoxMercenaryFlag, EnumConfigurationKey.MERCENARY_FLAG_INT);
        checkBoxMappings.put(checkBoxTundraEvent, EnumConfigurationKey.TUNDRA_TRUCK_EVENT_BOOL);
        checkBoxMappings.put(checkBoxTundraUseGems, EnumConfigurationKey.TUNDRA_TRUCK_USE_GEMS_BOOL);
        checkBoxMappings.put(checkBoxTundraSSR, EnumConfigurationKey.TUNDRA_TRUCK_SSR_BOOL);
        checkBoxMappings.put(checkBoxTundraEventActivationHour, EnumConfigurationKey.TUNDRA_TRUCK_ACTIVATION_TIME_BOOL);
        checkBoxMappings.put(checkBoxHeroMission, EnumConfigurationKey.HERO_MISSION_EVENT_BOOL);
        checkBoxMappings.put(checkBoxMercenaryEvent, EnumConfigurationKey.MERCENARY_EVENT_BOOL);
        checkBoxMappings.put(checkBoxJourneyofLight, EnumConfigurationKey.JOURNEY_OF_LIGHT_BOOL);
        checkBoxMappings.put(checkBoxMyriadBazaar, EnumConfigurationKey.MYRIAD_BAZAAR_EVENT_BOOL);

        comboBoxMappings.put(comboBoxHeroMissionFlag, EnumConfigurationKey.HERO_MISSION_FLAG_INT);

        // Map the activation hour text field
        textFieldMappings.put(textfieldTundraActivationHour, EnumConfigurationKey.TUNDRA_TRUCK_ACTIVATION_TIME_STRING);

        // Set up date/time validation for textFieldScheduleDateTime
        textfieldTundraActivationHour.textProperty().addListener((obs, oldVal, newVal) -> {
            validateTime(newVal);
        });

        setupTimeFieldHelpers();
        setupComponentBindings();
        initializeChangeEvents();
    }

    /**
     * Configura los bindings para habilitar/deshabilitar componentes relacionados
     */
    private void setupComponentBindings() {
        // Tundra Event: controla todos sus componentes relacionados
        checkBoxTundraEvent.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean enabled = newVal;
            checkBoxTundraUseGems.setDisable(!enabled);
            checkBoxTundraSSR.setDisable(!enabled);
            checkBoxTundraEventActivationHour.setDisable(!enabled);

            // Si se deshabilita Tundra, también deshabilitar el campo de hora si su checkbox está deshabilitado
            if (!enabled) {
                textfieldTundraActivationHour.setDisable(true);
            } else {
                // Si se habilita, respetar el estado del checkbox de hora
                textfieldTundraActivationHour.setDisable(!checkBoxTundraEventActivationHour.isSelected());
            }
        });

        // Tundra Activation Hour: controla el campo de texto de hora
        checkBoxTundraEventActivationHour.selectedProperty().addListener((obs, oldVal, newVal) -> {
            textfieldTundraActivationHour.setDisable(!newVal || !checkBoxTundraEvent.isSelected());
        });

        // Hero Mission: controla sus componentes relacionados
        checkBoxHeroMission.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean enabled = newVal;
            comboBoxHeroMissionFlag.setDisable(!enabled);
        });

        // Mercenary Event: controla el combobox de flag
        checkBoxMercenaryEvent.selectedProperty().addListener((obs, oldVal, newVal) -> {
            comboBoxMercenaryFlag.setDisable(!newVal);
        });

        // Inicializar el estado de los componentes según los checkboxes
        updateComponentStates();
    }


    private void updateComponentStates() {
        // Tundra Event
        boolean tundraEnabled = checkBoxTundraEvent.isSelected();
        checkBoxTundraUseGems.setDisable(!tundraEnabled);
        checkBoxTundraSSR.setDisable(!tundraEnabled);
        checkBoxTundraEventActivationHour.setDisable(!tundraEnabled);
        textfieldTundraActivationHour.setDisable(!tundraEnabled || !checkBoxTundraEventActivationHour.isSelected());

        // Hero Mission
        boolean heroMissionEnabled = checkBoxHeroMission.isSelected();
        comboBoxHeroMissionFlag.setDisable(!heroMissionEnabled);

        // Mercenary Event
        boolean mercenaryEnabled = checkBoxMercenaryEvent.isSelected();
        comboBoxMercenaryFlag.setDisable(!mercenaryEnabled);
    }

    private void setupTimeFieldHelpers() {
        // Visual hints
        textfieldTundraActivationHour.setPromptText("HH:mm");
        textfieldTundraActivationHour.setTooltip(new Tooltip("Use format: HH:mm (e.g., 15:30)"));

        // Formatter that inserts ':' automatically
        textfieldTundraActivationHour.setTextFormatter(getTimeTextFormatter());

        // Normalization when leaving the field (focus lost)
        textfieldTundraActivationHour.focusedProperty().addListener((obs, had, has) -> {
            if (!has) {
                normalizeTimeField();
            }
        });

        // Normalize when pressing Enter (commits pending edits first)
        textfieldTundraActivationHour.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> {
                    textfieldTundraActivationHour.commitValue(); // ensures formatter value is applied
                    normalizeTimeField();
                }
                default -> {
                }
            }
        });
    }

    /**
     * Normalizes and formats the time input field.
     * Expected format: HH:mm (24-hour format)
     */
    private void normalizeTimeField() {
        String t = textfieldTundraActivationHour.getText();
        if (t == null || t.isBlank())
            return;

        String digits = t.replaceAll("\\D", "");
        if (digits.length() == 2) {
            // Example: "15" → "15:00"
            textfieldTundraActivationHour.setText(digits + ":00");
        } else if (digits.length() == 4) {
            // Example: "1530" → "15:30"
            textfieldTundraActivationHour.setText(digits.substring(0, 2) + ":" + digits.substring(2, 4));
        }
    }

    /**
     * Validates time format (HH:mm) and shows error messages if invalid.
     */
    private void validateTime(String timeText) {
        labelDateTimeError.setText("");
        textfieldTundraActivationHour.setStyle("");

        if (timeText == null || timeText.trim().isEmpty())
            return;

        final String regex = "\\d{2}:\\d{2}";
        if (!timeText.matches(regex)) {
            labelDateTimeError.setText("Invalid format. Use: HH:mm (e.g., 19:30)");
            textfieldTundraActivationHour.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
            return;
        }

        try {
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .parseStrict()
                    .appendPattern("HH:mm")
                    .toFormatter(Locale.ROOT);

            LocalTime.parse(timeText, formatter);

            // Passed validation
            labelDateTimeError.setText("");
            textfieldTundraActivationHour.setStyle("");

        } catch (java.time.DateTimeException ex) {
            labelDateTimeError.setText("Invalid time values (check hour/minute).");
            textfieldTundraActivationHour.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
        }
    }
}
