package cl.camodev.wosbot.almac.entity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "config")
public class Config {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, unique = true)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "profile_id", nullable = true, foreignKey = @ForeignKey(name = "fk_config_profile"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Profile profile;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tp_config_id", nullable = false)
	private TpConfig tpConfig;

	@Column(name = "config_key", nullable = false)
	private String key;

	@Column(name = "value", nullable = false)
	private String value;

	public Config() {
	}

	public Config(Profile profile, TpConfig tpConfig, String key, String value) {
		this.profile = profile;
		this.tpConfig = tpConfig;
		this.key = key;
		this.value = value;
	}

	// Getters and Setters
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Profile getProfile() {
		return profile;
	}

	public void setProfile(Profile profile) {
		this.profile = profile;
	}

	public TpConfig getTpConfig() {
		return tpConfig;
	}

	public void setTpConfig(TpConfig tpConfig) {
		this.tpConfig = tpConfig;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "Config{" + "id=" + id + ", profile=" + (profile != null ? profile.getId() : "Global") + ", tpConfig=" + tpConfig.getName() + ", key='" + key + '\'' + ", value='" + value + '\'' + '}';
	}
}
