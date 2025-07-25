package cl.camodev.wosbot.almac.entity;

import cl.camodev.wosbot.console.enumerable.TpConfigEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tp_config")
public class TpConfig {

	@Id
	@Column(name = "id", nullable = false, unique = true)
	private Integer id;

	@Column(name = "name", nullable = false, unique = true)
	private String name;

	// Constructor vacío
	public TpConfig() {
	}

	// Constructor con parámetros
	public TpConfig(TpConfigEnum tpConfigEnum) {
		this.id = tpConfigEnum.getId();
		this.name = tpConfigEnum.getName();

	}

	// Getters y Setters
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "TpConfig{" + "id=" + id + ", name='" + name + '\'' + '}';
	}
}
