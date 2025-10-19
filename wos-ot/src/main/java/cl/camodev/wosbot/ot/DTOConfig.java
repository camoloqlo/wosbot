package cl.camodev.wosbot.ot;

public class DTOConfig {
	private Long profileId; // To know which profile it belongs to
	private String configurationName;
	private String value;

	public DTOConfig(Long profileId, String configurationName, String value) {
		this.profileId = profileId;
		this.configurationName = configurationName;
		this.value = value;
	}

	// Getters and Setters

	public Long getProfileId() {
		return profileId;
	}

	public String getConfigurationName() {
		return configurationName;
	}

	public String getValue() {
		return value;
	}

	public void setProfileId(Long profileId) {
		this.profileId = profileId;
	}

	public void setConfigurationName(String configurationName) {
		this.configurationName = configurationName;
	}

	public void setValue(String value) {
		this.value = value;
	}

    @Override
    public String toString() {
        return "DTOConfig [profileId=" + profileId + ", configurationName=" + configurationName + ", value=" + value + "]";
    }
}
