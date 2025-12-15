package cl.camodev.wosbot.common.view;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.PrioritizableItem;
import cl.camodev.wosbot.ot.DTOPriorityItem;
import cl.camodev.wosbot.profile.model.IProfileChangeObserver;
import cl.camodev.wosbot.profile.model.IProfileLoadListener;
import cl.camodev.wosbot.profile.model.IProfileObserverInjectable;
import cl.camodev.wosbot.profile.model.ProfileAux;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import org.controlsfx.control.CheckComboBox;

import java.util.stream.Collectors;

public abstract class AbstractProfileController implements IProfileLoadListener, IProfileObserverInjectable {

	protected final Map<CheckBox, EnumConfigurationKey> checkBoxMappings = new HashMap<>();
	protected final Map<TextField, EnumConfigurationKey> textFieldMappings = new HashMap<>();
	protected final Map<RadioButton, EnumConfigurationKey> radioButtonMappings = new HashMap<>();
	protected final Map<ComboBox<?>, EnumConfigurationKey> comboBoxMappings = new HashMap<>();
	protected final Map<CheckComboBox<?>, EnumConfigurationKey> checkComboBoxMappings = new HashMap<>();
	protected final Map<PriorityListView, EnumConfigurationKey> priorityListMappings = new HashMap<>();
	protected final Map<PriorityListView, Class<? extends Enum<?>>> priorityListEnumClasses = new HashMap<>();
	protected IProfileChangeObserver profileObserver;
	protected boolean isLoadingProfile = false;

	@Override
	public void setProfileObserver(IProfileChangeObserver observer) {
		this.profileObserver = observer;
	}

	protected <T extends Enum<T> & PrioritizableItem> void registerPriorityList(
			PriorityListView priorityListView,
			EnumConfigurationKey configKey,
			Class<T> enumClass) {
		priorityListMappings.put(priorityListView, configKey);
		priorityListEnumClasses.put(priorityListView, enumClass);
	}

	protected void initializeChangeEvents() {
		checkBoxMappings.forEach(this::setupCheckBoxListener);
		textFieldMappings.forEach(this::setupTextFieldUpdateOnFocusOrEnter);
		radioButtonMappings.forEach(this::setupRadioButtonListener);
		comboBoxMappings.forEach(this::setupComboBoxListener);
		checkComboBoxMappings.forEach(this::setupCheckComboBoxListener);
		priorityListMappings.forEach(this::setupPriorityListListener);
		priorityListEnumClasses.forEach(this::initializePriorityListFromEnum);
	}

	protected void createToggleGroup(RadioButton... radioButtons) {
		ToggleGroup toggleGroup = new ToggleGroup();
		for (RadioButton radioButton : radioButtons) {
			radioButton.setToggleGroup(toggleGroup);
		}
	}

	protected void setupRadioButtonListener(RadioButton radioButton, EnumConfigurationKey configKey) {
		radioButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
			if (!isLoadingProfile) {
				profileObserver.notifyProfileChange(configKey, newVal);
			}
		});
	}

	protected void setupCheckBoxListener(CheckBox checkBox, EnumConfigurationKey configKey) {
		checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
			if (!isLoadingProfile) {
				profileObserver.notifyProfileChange(configKey, newVal);
			}
		});
	}

	protected void setupTextFieldUpdateOnFocusOrEnter(TextField textField, EnumConfigurationKey configKey) {
		textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
			if (!isNowFocused && !isLoadingProfile) {
				updateProfile(textField, configKey);
			}
		});

		textField.setOnAction(event -> {
			if (!isLoadingProfile) {
				updateProfile(textField, configKey);
			}
		});
	}

	protected void setupComboBoxListener(ComboBox<?> comboBox, EnumConfigurationKey configKey) {
		comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
			if (!isLoadingProfile && newVal != null) {
				profileObserver.notifyProfileChange(configKey, newVal);
			}
		});
	}

	protected void setupCheckComboBoxListener(CheckComboBox<?> checkComboBox, EnumConfigurationKey configKey) {
		checkComboBox.getCheckModel().getCheckedItems().addListener(
			(javafx.collections.ListChangeListener.Change<?> change) -> {
				if (!isLoadingProfile) {
					String selectedValues = checkComboBox.getCheckModel().getCheckedItems().stream()
						.map(String::valueOf)
						.collect(Collectors.joining(","));
					profileObserver.notifyProfileChange(configKey, selectedValues);
				}
			});
	}

	protected void setupPriorityListListener(PriorityListView priorityListView, EnumConfigurationKey configKey) {
		priorityListView.setOnChangeCallback(() -> {
			if (!isLoadingProfile) {
				String configValue = priorityListView.toConfigString();
				profileObserver.notifyProfileChange(configKey, configValue);
			}
		});
	}

	private void updateProfile(TextField textField, EnumConfigurationKey configKey) {
		String newVal = textField.getText();
		if (configKey.getType() == Integer.class) {
			if (isValidPositiveInteger(newVal)) {
				profileObserver.notifyProfileChange(configKey, Integer.valueOf(newVal));
			} else {
				textField.setText(configKey.getDefaultValue());
			}
		} else if (configKey.getType() == java.time.LocalDateTime.class) {
			// For LocalDateTime values, parse the string
			if (newVal == null || newVal.trim().isEmpty()) {
				// Empty value means use "NOW" default
				profileObserver.notifyProfileChange(configKey, "NOW");
			} else {
				// Validate and parse the datetime string
				try {
					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
					LocalDateTime.parse(newVal, formatter);
					// Valid format, save as string (will be converted to LocalDateTime by config
					// system)
					profileObserver.notifyProfileChange(configKey, newVal);
				} catch (DateTimeParseException e) {
					// Invalid format, reset to default
					textField.setText(configKey.getDefaultValue());
				}
			}
		} else {
			// For String values, just pass them through
			profileObserver.notifyProfileChange(configKey, newVal);
		}
	}

	private boolean isValidPositiveInteger(String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}
		try {
			int number = Integer.parseInt(value);
			return number >= 0 && number <= 99999999;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	@Override
	public void onProfileLoad(ProfileAux profile) {
		isLoadingProfile = true;
		try {
			checkBoxMappings.forEach((checkBox, key) -> {
				Boolean value = profile.getConfiguration(key);
				checkBox.setSelected(value);
			});

			textFieldMappings.forEach((textField, key) -> {
				Object value = profile.getConfiguration(key);
				if (value != null) {
					// Special handling for LocalDateTime - format it
					if (value instanceof LocalDateTime) {
						DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
						textField.setText(((LocalDateTime) value).format(formatter));
					} else {
						textField.setText(value.toString());
					}
				} else {
					textField.setText(key.getDefaultValue());
				}
			});

			radioButtonMappings.forEach((radioButton, key) -> {
				Boolean value = profile.getConfiguration(key);
				radioButton.setSelected(value);
			});

			comboBoxMappings.forEach((comboBox, key) -> {
				Object value = profile.getConfiguration(key);
				if (value != null) {
					@SuppressWarnings("unchecked")
					ComboBox<Object> uncheckedComboBox = (ComboBox<Object>) comboBox;
					uncheckedComboBox.setValue(value);
				}
			});

			checkComboBoxMappings.forEach((checkComboBox, key) -> {
				String value = profile.getConfiguration(key);
				checkComboBox.getCheckModel().clearChecks();
				
				if (value != null && !value.trim().isEmpty()) {
					String[] selectedValues = value.split(",");
					for (String val : selectedValues) {
						try {
							@SuppressWarnings("unchecked")
							CheckComboBox<Object> uncheckedCheckComboBox = (CheckComboBox<Object>) checkComboBox;
							// Parse based on the type of items in the CheckComboBox
							if (!checkComboBox.getItems().isEmpty()) {
								Object firstItem = checkComboBox.getItems().get(0);
								if (firstItem instanceof Integer) {
									Integer intVal = Integer.parseInt(val.trim());
									uncheckedCheckComboBox.getCheckModel().check(intVal);
								} else {
									uncheckedCheckComboBox.getCheckModel().check(val.trim());
								}
							}
						} catch (NumberFormatException e) {
							// Skip invalid values
						}
					}
				}
			});

			priorityListMappings.forEach((priorityListView, key) -> {
				String value = profile.getConfiguration(key);
				Class<? extends Enum<?>> enumClass = priorityListEnumClasses.get(priorityListView);
				if (value != null && !value.trim().isEmpty()) {
					// Load saved configuration first
					priorityListView.fromConfigString(value);
					// After loading saved configuration, merge with current enum values so any
					// newly added enum constants are included in the list
					if (enumClass != null) {
						@SuppressWarnings("rawtypes")
						Class castClass = (Class) enumClass;
						// Suppress unchecked warnings - this is required due to Java's type erasure with generics
						@SuppressWarnings("unchecked")
						Runnable call = () -> mergeEnumWithSavedPriorities(priorityListView, castClass, key);
						call.run();
					}
				} else {
					if (enumClass != null) {
						reinitializePriorityListWithDefaults(priorityListView, enumClass);
					}
				}
			});

		} finally {
			isLoadingProfile = false;
		}
	}

	protected <T extends Enum<T> & PrioritizableItem> void initializePriorityListFromEnum(
			PriorityListView priorityListView,
			Class<? extends Enum<?>> enumClass) {

		List<DTOPriorityItem> items = new ArrayList<>();
		@SuppressWarnings("unchecked")
		T[] enumConstants = ((Class<T>) enumClass).getEnumConstants();

		for (int i = 0; i < enumConstants.length; i++) {
			items.add(new DTOPriorityItem(
					enumConstants[i].getIdentifier(),
					enumConstants[i].getDisplayName(),
					i + 1,
					false));
		}

		priorityListView.setItems(items);
	}

	private <T extends Enum<T> & PrioritizableItem> void reinitializePriorityListWithDefaults(
			PriorityListView priorityListView,
			Class<? extends Enum<?>> enumClass) {

		List<DTOPriorityItem> items = new ArrayList<>();
		@SuppressWarnings("unchecked")
		T[] enumConstants = ((Class<T>) enumClass).getEnumConstants();

		for (int i = 0; i < enumConstants.length; i++) {
			items.add(new DTOPriorityItem(
					enumConstants[i].getIdentifier(),
					enumConstants[i].getDisplayName(),
					i + 1,
					false // All disabled
			));
		}

		priorityListView.setItems(items);
	}

	protected <T extends Enum<T> & PrioritizableItem> void mergeEnumWithSavedPriorities(
			PriorityListView priorityListView,
			Class<T> enumClass,
			EnumConfigurationKey configKey) {

		List<DTOPriorityItem> currentItems = priorityListView.getItems();

		if (currentItems.isEmpty()) {
			return;
		}

		Map<String, DTOPriorityItem> savedItemsMap = new HashMap<>();
		for (DTOPriorityItem item : currentItems) {
			savedItemsMap.put(item.getIdentifier(), item);
		}

		List<DTOPriorityItem> mergedItems = new ArrayList<>();
		T[] enumConstants = enumClass.getEnumConstants();

		for (T enumItem : enumConstants) {
			String identifier = enumItem.getIdentifier();

			if (savedItemsMap.containsKey(identifier)) {
				DTOPriorityItem savedItem = savedItemsMap.get(identifier);
				mergedItems.add(new DTOPriorityItem(
						identifier,
						enumItem.getDisplayName(),
						savedItem.getPriority(),
						savedItem.isEnabled()));
			}
		}

		mergedItems.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));

		List<DTOPriorityItem> newItems = new ArrayList<>();
		for (T enumItem : enumConstants) {
			String identifier = enumItem.getIdentifier();

			if (!savedItemsMap.containsKey(identifier)) {
				newItems.add(new DTOPriorityItem(
						identifier,
						enumItem.getDisplayName(),
						0,
						false));
			}
		}

		mergedItems.addAll(newItems);

		for (int i = 0; i < mergedItems.size(); i++) {
			mergedItems.get(i).setPriority(i + 1);
		}

		if (mergedItems.size() != currentItems.size() ||
				!haveSameIdentifiers(mergedItems, currentItems)) {

			priorityListView.setItems(mergedItems);

			if (profileObserver != null && !isLoadingProfile) {
				String mergedConfig = priorityListView.toConfigString();
				profileObserver.notifyProfileChange(configKey, mergedConfig);
			}
		} else {
			priorityListView.setItems(mergedItems);
		}
	}

	private boolean haveSameIdentifiers(List<DTOPriorityItem> list1, List<DTOPriorityItem> list2) {
		if (list1.size() != list2.size()) {
			return false;
		}

		for (int i = 0; i < list1.size(); i++) {
			if (!list1.get(i).getIdentifier().equals(list2.get(i).getIdentifier())) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Builds a TextFormatter that automatically adds ":" and limits to HH:mm
	 * format.
	 */
	protected static @NotNull TextFormatter<String> getTimeTextFormatter() {
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
}
