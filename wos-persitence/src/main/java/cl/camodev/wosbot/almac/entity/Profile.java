package cl.camodev.wosbot.almac.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "profiles")
public class Profile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, unique = true)
	private Long id;

	@Column(name = "profile_name", nullable = false)
	private String name;

	@Column(name = "emulator_number", nullable = false)
	private String emulatorNumber;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled;

	@Column(name = "priority", nullable = false, columnDefinition = "BIGINT DEFAULT 50")
	private Long priority;

	@Column(name = "reconnection_time", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
	private Long reconnectionTime;

	@Column(name = "character_id", nullable = true)
	private String characterId;

	@Column(name = "character_name", nullable = true)
	private String characterName;

	@Column(name = "character_alliance_code", nullable = true, length = 3)
	private String characterAllianceCode;

	@Column(name = "character_server", nullable = true)
	private String characterServer;

	@OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Config> configs = new ArrayList<>();

	// Getters y Setters

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmulatorNumber() {
		return emulatorNumber;
	}

	public void setEmulatorNumber(String emulatorNumber) {
		this.emulatorNumber = emulatorNumber;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public List<Config> getConfigs() {
		return configs;
	}

	public void setConfigs(List<Config> configs) {
		this.configs = configs;
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
		this.reconnectionTime = reconnectionTime;
	}

	public String getCharacterId() {
		return characterId;
	}

	public void setCharacterId(String characterId) {
		this.characterId = characterId;
	}

	public String getCharacterName() {
		return characterName;
	}

	public void setCharacterName(String characterName) {
		this.characterName = characterName;
	}

	public String getCharacterAllianceCode() {
		return characterAllianceCode;
	}

	public void setCharacterAllianceCode(String characterAllianceCode) {
		this.characterAllianceCode = characterAllianceCode;
	}

	public String getCharacterServer() {
		return characterServer;
	}

	public void setCharacterServer(String characterServer) {
		this.characterServer = characterServer;
	}
}
