package cl.camodev.wosbot.events.view;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
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
    private ComboBox<String> comboBoxHeroMissionMode;

    @FXML
    private Label labelDateTimeError;

    @FXML
    private void initialize() {
        // Set up flag combobox with integer values
        comboBoxMercenaryFlag.getItems().addAll(0, 1, 2, 3, 4, 5, 6, 7, 8);
        comboBoxHeroMissionFlag.getItems().addAll(0, 1, 2, 3, 4, 5, 6, 7, 8);
        comboBoxHeroMissionMode.getItems().addAll("Limited (10)", "Unlimited");

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
        comboBoxMappings.put(comboBoxHeroMissionMode, EnumConfigurationKey.HERO_MISSION_MODE_STRING);

        // Map the activation hour text field
        textFieldMappings.put(textfieldTundraActivationHour, EnumConfigurationKey.TUNDRA_TRUCK_ACTIVATION_TIME_STRING);

        // Set up date/time validation for textFieldScheduleDateTime
        textfieldTundraActivationHour.textProperty().addListener((obs, oldVal, newVal) -> {
            validateTime(newVal);
        });
        
        setupTimeFieldHelpers();
        initializeChangeEvents();
    }

    /**
	 * Validates and formats the time input field.
	 * Expected format: HH:mm (24-hour format)
	 */
	private void setupTimeFieldHelpers() {
		// Visual hints
		textfieldTundraActivationHour.setPromptText("HH:mm");
		textfieldTundraActivationHour.setTooltip(new Tooltip("Use format: HH:mm (e.g., 19:30)"));

		// TextFormatter that auto-inserts ':' and restricts to mask "##:##"
		TextFormatter<String> formatter = getTimeTextFormatter();
		textfieldTundraActivationHour.setTextFormatter(formatter);

		// Optional: normalize/pad on focus lost (e.g., "9:5" → "09:05")
		textfieldTundraActivationHour.focusedProperty().addListener((obs, had, has) -> {
			if (!has) {
				String t = textfieldTundraActivationHour.getText();
				if (t == null || t.isBlank())
					return;

				// Keep only digits
				String digits = t.replaceAll("\\D", "");
				if (digits.length() == 4) {
					String HH = digits.substring(0, 2);
					String mm = digits.substring(2, 4);
					textfieldTundraActivationHour.setText(HH + ":" + mm);
				}
			}
		});
	}

	/**
	 * Builds a TextFormatter that automatically adds ":" and limits to HH:mm
	 * format.
	 */
	private static @NotNull TextFormatter<String> getTimeTextFormatter() {
		final int maxDigits = 4; // HHmm
		final int maxLenMasked = 5; // HH:mm

		return new TextFormatter<>(change -> {
			if (change.isContentChange()) {
				// Extract digits
				StringBuilder digits = new StringBuilder();
				String newText = change.getControlNewText();
				for (int i = 0; i < newText.length(); i++) {
					char c = newText.charAt(i);
					if (Character.isDigit(c))
						digits.append(c);
				}

				// Limit to maxDigits
				if (digits.length() > maxDigits) {
					digits.setLength(maxDigits);
				}

				// Rebuild with colon after HH
				StringBuilder masked = new StringBuilder();
				for (int d = 0; d < digits.length(); d++) {
					if (d == 2)
						masked.append(':');
					masked.append(digits.charAt(d));
				}

				// Limit total length
				if (masked.length() > maxLenMasked)
					masked.setLength(maxLenMasked);

				// Replace entire text
				change.setRange(0, change.getControlText().length());
				change.setText(masked.toString());
				change.setCaretPosition(masked.length());
				change.setAnchor(masked.length());
			}
			return change;
		});
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
