package cl.camodev.wosbot.ot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;

public class DTOProfiles {
    private Long id;
    private String name;
    private String emulatorNumber;
    private Boolean enabled;
    private Long priority;
    private String status;
    private Long reconnectionTime; // Reconnection time in seconds
    private int queuePosition = Integer.MAX_VALUE;
    private List<DTOConfig> configs = new ArrayList<>();
    private HashMap<String, String> globalsettings = new HashMap<>();

    /**
     * Constructor for the DTOProfiles class.
     *
     * @param id The unique identifier of the profile.
     */
    public DTOProfiles(Long id) {
        this.id = id;
    }

    // Full constructor including reconnectionTime
    public DTOProfiles(Long id, String name, String emulatorNumber, Boolean enabled, Long priority, Long reconnectionTime) {
        this.id = id;
        this.name = name;
        this.emulatorNumber = emulatorNumber;
        this.enabled = enabled;
        this.priority = priority;
        this.reconnectionTime = reconnectionTime;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmulatorNumber() {
        return emulatorNumber;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public List<DTOConfig> getConfigs() {
        return configs;
    }

    public void setConfigs(List<DTOConfig> configs) {
        this.configs = configs;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmulatorNumber(String emulatorNumber) {
        this.emulatorNumber = emulatorNumber;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setGlobalSettings(HashMap<String, String> globalsettings) {
        this.setGlobalsettings(globalsettings);

    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(int queuePosition) {
        this.queuePosition = queuePosition;
    }

    /**
     * Gets the value of a specific configuration using EnumConfigurationKey. It is a generic method that returns the correct type
     * based on the key.
     */
    public <T> T getConfig(EnumConfigurationKey key, Class<T> clazz) {
        Optional<DTOConfig> configOptional = configs.stream().filter(config -> config.getConfigurationName().equalsIgnoreCase(key.name())).findFirst();

        if (!configOptional.isPresent()) {

            DTOConfig defaultConfig = new DTOConfig(-1L, key.name(), key.getDefaultValue());
            configs.add(defaultConfig);
        }
        String value = configOptional.map(DTOConfig::getValue).orElse(key.getDefaultValue());

        return key.castValue(value);
    }

    public <T> void setConfig(EnumConfigurationKey key, T value) {
        String valueToStore = value.toString();
        Optional<DTOConfig> configOptional = configs.stream().filter(config -> config.getConfigurationName().equalsIgnoreCase(key.name())).findFirst();

        if (configOptional.isPresent()) {
            configOptional.get().setValue(valueToStore);
        } else {
            DTOConfig newConfig = new DTOConfig(getId(), key.name(), valueToStore);
            configs.add(newConfig);
        }
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public HashMap<String, String> getGlobalsettings() {
        return globalsettings;
    }

    public void setGlobalsettings(HashMap<String, String> globalsettings) {
        this.globalsettings = globalsettings;
    }

    public Long getPriority() {
        return priority;
    }

    public void setPriority(Long priority) {
        this.priority = priority;
    }

    public Long getReconnectionTime() {
        return reconnectionTime;
    }

    public void setReconnectionTime(Long reconnectionTime) {
        this.reconnectionTime = reconnectionTime != null && reconnectionTime >= 0 ? reconnectionTime : 30L;
    }

}
