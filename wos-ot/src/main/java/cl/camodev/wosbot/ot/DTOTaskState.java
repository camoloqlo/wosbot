package cl.camodev.wosbot.ot;

import java.time.LocalDateTime;

public class DTOTaskState {

	private Long profileId;
	private Integer taskId;
	private String taskName;
	private boolean scheduled;
	private boolean executing;
	private LocalDateTime lastExecutionTime;
	private LocalDateTime nextExecutionTime;

	public DTOTaskState() {
	}

	public Long getProfileId() {
		return profileId;
	}

	public void setProfileId(Long profileId) {
		this.profileId = profileId;
	}

	public Integer getTaskId() {
		return taskId;
	}

	public void setTaskId(Integer taskId) {
		this.taskId = taskId;
	}

	public String getTaskName() {
		return taskName;
	}

	public void setTaskName(String taskName) {
		this.taskName = taskName;
	}

	public boolean isScheduled() {
		return scheduled;
	}

	public void setScheduled(boolean scheduled) {
		this.scheduled = scheduled;
	}

	public boolean isExecuting() {
		return executing;
	}

	public void setExecuting(boolean executing) {
		this.executing = executing;
	}

	public LocalDateTime getLastExecutionTime() {
		return lastExecutionTime;
	}

	public void setLastExecutionTime(LocalDateTime lastExecutionTime) {
		this.lastExecutionTime = lastExecutionTime;
	}

	public LocalDateTime getNextExecutionTime() {
		return nextExecutionTime;
	}

	public void setNextExecutionTime(LocalDateTime nextExecutionTime) {
		this.nextExecutionTime = nextExecutionTime;
	}

}
