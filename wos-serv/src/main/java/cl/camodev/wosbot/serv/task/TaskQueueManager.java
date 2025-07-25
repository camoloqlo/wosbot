package cl.camodev.wosbot.serv.task;

import java.util.HashMap;
import java.util.Map;

import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServTaskManager;

public class TaskQueueManager {

	private final Map<Long, TaskQueue> taskQueues = new HashMap<>();

	public void createQueue(DTOProfiles profile) {
		if (!taskQueues.containsKey(profile.getId())) {
			taskQueues.put(profile.getId(), new TaskQueue(profile));
		}
	}

	public TaskQueue getQueue(Long queueName) {
		return taskQueues.get(queueName);
	}

	public void startQueues() {
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager", "-", "Starting queues");
		taskQueues.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			entry.getValue().start();
			try {
				Thread.sleep(200);
                        } catch (InterruptedException e) {
                                ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "TaskQueueManager", "-", "Queue startup interrupted: " + e.getMessage());
                        }
		});

	}

	public void stopQueues() {
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager", "-", "Stopping queues");
		taskQueues.forEach((k, v) -> {
			for (TpDailyTaskEnum task : TpDailyTaskEnum.values()) {
				DTOTaskState taskState = ServTaskManager.getInstance().getTaskState(k, task.getId());
				if (taskState != null) {
					taskState.setScheduled(false);
					ServTaskManager.getInstance().setTaskState(k, taskState);
				}
			}

			v.stop();
		});
		taskQueues.clear();
	}

	public void pauseQueues() {
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager", "-", "Pausing queues");
		taskQueues.forEach((k, v) -> {
			v.pause();
		});
	}

	public void resumeQueues() {
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager", "-", "Resuming queues");
		taskQueues.forEach((k, v) -> {
			v.resume();
		});
	}

}
