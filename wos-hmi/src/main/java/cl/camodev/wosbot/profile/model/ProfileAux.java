package cl.camodev.wosbot.profile.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ProfileAux {

	private LongProperty id;
	private StringProperty name;
	private StringProperty emulatorNumber;
	private BooleanProperty enabled;
	private LongProperty priority;
	private StringProperty status;
	private LongProperty reconnectionTime;
	private StringProperty characterId;
	private StringProperty characterName;
	private StringProperty characterAllianceCode;
	private StringProperty characterServer;

	private List<ConfigAux> configs = new ArrayList<ConfigAux>();


	public ProfileAux(Long id, String name, String emulatorNumber, boolean enabled, Long priority, String status, Long reconnectionTime) {
		this.id = new SimpleLongProperty(id);
		this.name = new SimpleStringProperty(name);
		this.emulatorNumber = new SimpleStringProperty(emulatorNumber);
		this.enabled = new SimpleBooleanProperty(enabled);
		this.priority = new SimpleLongProperty(priority);
		this.status = new SimpleStringProperty(status);
		this.reconnectionTime = new SimpleLongProperty(reconnectionTime);
		this.characterId = new SimpleStringProperty("");
		this.characterName = new SimpleStringProperty("");
		this.characterAllianceCode = new SimpleStringProperty("");
		this.characterServer = new SimpleStringProperty("");
	}

	public ProfileAux(Long id, String name, String emulatorNumber, boolean enabled, Long priority, String status, Long reconnectionTime, String characterId, String characterName, String characterAllianceCode, String characterServer) {
		this.id = new SimpleLongProperty(id);
		this.name = new SimpleStringProperty(name);
		this.emulatorNumber = new SimpleStringProperty(emulatorNumber);
		this.enabled = new SimpleBooleanProperty(enabled);
		this.priority = new SimpleLongProperty(priority);
		this.status = new SimpleStringProperty(status);
		this.reconnectionTime = new SimpleLongProperty(reconnectionTime);
		this.characterId = new SimpleStringProperty(characterId != null ? characterId : "");
		this.characterName = new SimpleStringProperty(characterName != null ? characterName : "");
		this.characterAllianceCode = new SimpleStringProperty(characterAllianceCode != null ? characterAllianceCode : "");
		this.characterServer = new SimpleStringProperty(characterServer != null ? characterServer : "");
	}

	// Métodos para la propiedad 'id'
	public Long getId() {
		return id.get();
	}

	public void setId(Long id) {
		this.id.set(id);
	}

	public LongProperty idProperty() {
		return id;
	}

	// Métodos para la propiedad 'name'
	public String getName() {
		return name.get();
	}

	public void setName(String name) {
		this.name.set(name);
	}

	public StringProperty nameProperty() {
		return name;
	}

	// Métodos para la propiedad 'emulatorNumber'
	public String getEmulatorNumber() {
		return emulatorNumber.get();
	}

	public void setEmulatorNumber(String emulatorNumber) {
		this.emulatorNumber.set(emulatorNumber);
	}

	public StringProperty emulatorNumberProperty() {
		return emulatorNumber;
	}

	// Métodos para la propiedad 'enabled'
	public Boolean isEnabled() {
		return enabled.get();
	}

	public void setEnabled(boolean enabled) {
		this.enabled.set(enabled);
	}

	public BooleanProperty enabledProperty() {
		return enabled;
	}

	// Métodos para la propiedad 'status'
	public String getStatus() {
		return status.get();
	}

	public void setStatus(String status) {
		this.status.set(status);
	}

	public StringProperty statusProperty() {
		return status;
	}

	// Métodos para la propiedad 'priority'
	public Long getPriority() {
		return priority.get();
	}

	public void setPriority(Long priority) {
		this.priority.set(priority != null ? priority : 50L);
	}

	public LongProperty priorityProperty() {
		return priority;
	}

	public Long getReconnectionTime() {
		return reconnectionTime.get();
	}

	public void setReconnectionTime(Long reconnectionTime) {
		this.reconnectionTime.set(reconnectionTime);
	}

	public LongProperty reconnectionTimeProperty() {
		return reconnectionTime;
	}

	// Métodos para la propiedad 'characterName'
	public String getCharacterName() {
		return characterName.get();
	}

	public void setCharacterName(String characterName) {
		this.characterName.set(characterName != null ? characterName : "");
	}

	public StringProperty characterNameProperty() {
		return characterName;
	}

	// Métodos para la propiedad 'characterId'
	public String getCharacterId() {
		return characterId.get();
	}

	public void setCharacterId(String characterId) {
		this.characterId.set(characterId != null ? characterId : "");
	}

	public StringProperty characterIdProperty() {
		return characterId;
	}

	// Métodos para la propiedad 'characterAllianceCode'
	public String getCharacterAllianceCode() {
		return characterAllianceCode.get();
	}

	public void setCharacterAllianceCode(String characterAllianceCode) {
		this.characterAllianceCode.set(characterAllianceCode != null ? characterAllianceCode : "");
	}

	public StringProperty characterAllianceCodeProperty() {
		return characterAllianceCode;
	}

	// Métodos para la propiedad 'characterServer'
	public String getCharacterServer() {
		return characterServer.get();
	}

	public void setCharacterServer(String characterServer) {
		this.characterServer.set(characterServer != null ? characterServer : "");
	}

	public StringProperty characterServerProperty() {
		return characterServer;
	}

	public <T> T getConfiguration(EnumConfigurationKey key) {
		Optional<ConfigAux> config = configs.stream()
				.filter(c -> c.getName().equals(key.name()))
				.findFirst();

		if (config.isPresent()) {
			return key.castValue(config.get().getValue());
		} else {
			return key.castValue(key.getDefaultValue());
		}
	}

	public List<ConfigAux> getConfigs() {
		return configs;
	}

	public void setConfigs(List<ConfigAux> configs) {
		this.configs = configs;
	}

	/**
	 * Obtiene el valor de una configuración específica utilizando EnumConfigurationKey. Es un método genérico que devuelve el tipo correcto
	 * basado en la clave.
	 */
	public <T> T getConfig(EnumConfigurationKey key, Class<T> clazz) {
		Optional<ConfigAux> configOptional = configs.stream().filter(config -> config.getName().equalsIgnoreCase(key.name())).findFirst();

		if (!configOptional.isPresent()) {

			ConfigAux defaultConfig = new ConfigAux(key.name(), key.getDefaultValue());
			configs.add(defaultConfig);
		}
		String valor = configOptional.map(ConfigAux::getValue).orElse(key.getDefaultValue());

		return key.castValue(valor);
	}

	public <T> void setConfig(EnumConfigurationKey key, T value) {
		String valorAAlmacenar = value.toString();
		Optional<ConfigAux> configOptional = configs.stream().filter(config -> config.getName().equalsIgnoreCase(key.name())).findFirst();

		if (configOptional.isPresent()) {
			configOptional.get().setValue(valorAAlmacenar);
		} else {
			ConfigAux nuevaConfig = new ConfigAux(key.name(), valorAAlmacenar);
			configs.add(nuevaConfig);
		}
	}
}
