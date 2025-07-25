package cl.camodev.wosbot.almac.entity;

import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tp_daily_task")
public class TpDailyTask {

	@Id
	@Column(name = "id", nullable = false, unique = true)
	private Integer id;

	@Column(name = "task_name", nullable = false, unique = true)
	private String taskName;

	public TpDailyTask() {
	}

	public TpDailyTask(TpDailyTaskEnum taskName) {
		this.id = taskName.getId();
		this.taskName = taskName.getName();
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getTaskName() {
		return taskName;
	}

	public void setTaskName(String taskName) {
		this.taskName = taskName;
	}
}
