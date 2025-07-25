package cl.camodev.wosbot.almac.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "daily_task")
public class DailyTask {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, unique = true)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_dailytask_profile"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Profile profile;

	@ManyToOne
	@JoinColumn(name = "task_id", nullable = false, foreignKey = @ForeignKey(name = "fk_daily_task_tp_daily_task"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private TpDailyTask task;

	@Column(name = "last_execution", nullable = false)
	private LocalDateTime lastExecution;

	@Column(name = "next_schedule", nullable = false)
	private LocalDateTime nextSchedule;

	// Constructor vacío
	public DailyTask() {
	}

	// Constructor con parámetros
	public DailyTask(Profile profile, TpDailyTask task, LocalDateTime lastExecution, LocalDateTime nextSchedule) {
		this.profile = profile;
		this.task = task;
		this.lastExecution = lastExecution;
		this.nextSchedule = nextSchedule;
	}

	// Getters y Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Profile getProfile() {
		return profile;
	}

	public void setProfile(Profile profile) {
		this.profile = profile;
	}

	public TpDailyTask getTask() {
		return task;
	}

	public void setTask(TpDailyTask task) {
		this.task = task;
	}

	public LocalDateTime getLastExecution() {
		return lastExecution;
	}

	public void setLastExecution(LocalDateTime lastExecution) {
		this.lastExecution = lastExecution;
	}

	public LocalDateTime getNextSchedule() {
		return nextSchedule;
	}

	public void setNextSchedule(LocalDateTime nextSchedule) {
		this.nextSchedule = nextSchedule;
	}
}
