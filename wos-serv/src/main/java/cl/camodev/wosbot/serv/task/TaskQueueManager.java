package cl.camodev.wosbot.serv.task;

import java.util.HashMap;
import java.util.Map;

import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskQueueManager {
	private final static Logger logger = LoggerFactory.getLogger(TaskQueueManager.class);
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
		logger.info("Starting queues ");
		taskQueues.entrySet().stream()
			.sorted(Map.Entry.<Long, TaskQueue>comparingByValue((queue1, queue2) ->
				Long.compare(queue2.getProfile().getPriority(), queue1.getProfile().getPriority())))
			.forEach(entry -> {
				logger.info("Starting queue for profile: {} with priority: {}",
					entry.getValue().getProfile().getName(), entry.getValue().getProfile().getPriority());
				entry.getValue().start();
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});
	}

	public void stopQueues() {
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager", "-", "Stopping queues");
		logger.info("Stopping queues");
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
		logger.info("Pausing queues");
		taskQueues.forEach((k, v) -> {
			v.pause();
		});
	}

	public void resumeQueues() {
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager", "-", "Resuming queues");
		logger.info("Resuming queues");
		taskQueues.forEach((k, v) -> {
			v.resume();
		});
	}

}
