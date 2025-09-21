package cl.camodev.wosbot.serv.task;

import java.util.HashMap;
import java.util.Map;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
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
			.sorted(Map.Entry.<Long, TaskQueue>comparingByValue((queue1, queue2) -> {
				// Get the delay configuration for both queues
				Integer delay = queue1.getProfile().getConfig(EnumConfigurationKey.MAX_IDLE_TIME_INT, Integer.class);

				// Check if queues have tasks with acceptable idle time
				boolean hasAcceptableIdle1 = queue1.hasTasksWithAcceptableIdleTime(delay);
				boolean hasAcceptableIdle2 = queue2.hasTasksWithAcceptableIdleTime(delay);

				// Prioritize queues with tasks having acceptable idle time
				if (hasAcceptableIdle1 && !hasAcceptableIdle2) {
					return -1; // queue1 has priority
				} else if (!hasAcceptableIdle1 && hasAcceptableIdle2) {
					return 1; // queue2 has priority
				} else {
					// If both have acceptable idle time or both don't, use original priority logic
					return Long.compare(queue2.getProfile().getPriority(), queue1.getProfile().getPriority());
				}
			}))
			.forEach(entry -> {
				TaskQueue queue = entry.getValue();
				DTOProfiles profile = queue.getProfile();
				Integer delay = profile.getConfig(EnumConfigurationKey.MAX_IDLE_TIME_INT, Integer.class);
				boolean hasAcceptableIdle = queue.hasTasksWithAcceptableIdleTime(delay);

				logger.info("Starting queue for profile: {} with priority: {} (has tasks with idle < {} min: {})",
					profile.getName(), profile.getPriority(), delay, hasAcceptableIdle);

				queue.start();
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
