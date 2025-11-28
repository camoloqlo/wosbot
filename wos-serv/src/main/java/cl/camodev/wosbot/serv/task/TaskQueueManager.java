package cl.camodev.wosbot.serv.task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOQueueProfileState;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.impl.ServConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskQueueManager {
        private final static Logger logger = LoggerFactory.getLogger(TaskQueueManager.class);
        private final Map<Long, TaskQueue> taskQueues = new HashMap<>();
        private final Map<Long, Boolean> queuePausedStates = new ConcurrentHashMap<>();

        public void createQueue(DTOProfiles profile) {
                if (!taskQueues.containsKey(profile.getId())) {
                        taskQueues.put(profile.getId(), new TaskQueue(profile));
                        queuePausedStates.put(profile.getId(), Boolean.FALSE);
                }
        }

        public TaskQueue getQueue(Long queueName) {
                return taskQueues.get(queueName);
        }

        public void startQueues() {
                ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager", "-",
                                "Starting queues");
                logger.info("Starting queues ");
                taskQueues.entrySet().stream()
                                .sorted(Map.Entry.<Long, TaskQueue>comparingByValue((queue1, queue2) -> {
                                        // Obtener el delay global desde ServConfig con fallback al default
                                        int delay = Optional
                                                        .ofNullable(ServConfig.getServices().getGlobalConfig())
                                                        .map(cfg -> cfg.get(
                                                                        EnumConfigurationKey.MAX_IDLE_TIME_INT.name()))
                                                        .map(Integer::parseInt)
                                                        .orElse(Integer.parseInt(EnumConfigurationKey.MAX_IDLE_TIME_INT
                                                                        .getDefaultValue()));

                                        // Check if queues have tasks with acceptable idle time
                                        boolean hasAcceptableIdle1 = queue1.hasTasksWithAcceptableIdleTime(delay);
                                        boolean hasAcceptableIdle2 = queue2.hasTasksWithAcceptableIdleTime(delay);

                                        // Prioritize queues with tasks having acceptable idle time
                                        if (hasAcceptableIdle1 && !hasAcceptableIdle2) {
                                                return -1; // queue1 has priority
                                        } else if (!hasAcceptableIdle1 && hasAcceptableIdle2) {
                                                return 1; // queue2 has priority
                                        } else {
                                                // If both have acceptable idle time or both don't, use original
                                                // priority logic
                                                return Long.compare(queue2.getProfile().getPriority(),
                                                                queue1.getProfile().getPriority());
                                        }
                                }))
                                .forEach(entry -> {
                                        TaskQueue queue = entry.getValue();
                                        DTOProfiles profile = queue.getProfile();
                                        int delay = Optional
                                                        .ofNullable(ServConfig.getServices().getGlobalConfig())
                                                        .map(cfg -> cfg.get(
                                                                        EnumConfigurationKey.MAX_IDLE_TIME_INT.name()))
                                                        .map(Integer::parseInt)
                                                        .orElse(Integer.parseInt(EnumConfigurationKey.MAX_IDLE_TIME_INT
                                                                        .getDefaultValue()));
                                        boolean hasAcceptableIdle = queue.hasTasksWithAcceptableIdleTime(delay);

                                        logger.info("Starting queue for profile: {} with priority: {} (has tasks with idle < {} min: {})",
                                                        profile.getName(), profile.getPriority(), delay,
                                                        hasAcceptableIdle);

                                        queue.start();
                                        try {
                                                Thread.sleep(200);
                                        } catch (InterruptedException e) {
                                                e.printStackTrace();
                                        }
                                });
        }

        public void stopQueues() {
                ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager", "-",
                                "Stopping queues");
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
                queuePausedStates.clear();
        }

        public void pauseQueues() {
                ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager", "-", "Pausing queues");
                logger.info("Pausing all queues");
                taskQueues.forEach((k, v) -> {
                        v.pause();
                        queuePausedStates.put(k, Boolean.TRUE);
                });
        }

        public void resumeQueues() {
                ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager", "-",
                                "Resuming queues");
                logger.info("Resuming all queues");
                taskQueues.forEach((k, v) -> {
                        v.resume();
                        queuePausedStates.put(k, Boolean.FALSE);
                });
        }

        public void pauseQueue(Long profileId) {
                TaskQueue queue = taskQueues.get(profileId);
                if (queue != null) {
                        ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager",
                                        String.valueOf(profileId), "Pausing queue");
                        logger.info("Pausing queue for profile {}", profileId);
                        queue.pause();
                        queuePausedStates.put(profileId, Boolean.TRUE);
                }
        }

        public void resumeQueue(Long profileId) {
                TaskQueue queue = taskQueues.get(profileId);
                if (queue != null) {
                        ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueueManager",
                                        String.valueOf(profileId), "Resuming queue");
                        logger.info("Resuming queue for profile {}", profileId);
                        queue.resume();
                        queuePausedStates.put(profileId, Boolean.FALSE);
                }
        }

        public List<DTOQueueProfileState> getActiveQueueStates() {
                List<DTOQueueProfileState> states = new ArrayList<>();
                taskQueues.forEach((profileId, queue) -> {
                        DTOProfiles profile = queue.getProfile();
                        String profileName = profile != null ? profile.getName() : String.valueOf(profileId);
                        boolean paused = queuePausedStates.getOrDefault(profileId, Boolean.FALSE);
                        states.add(new DTOQueueProfileState(profileId, profileName, paused));
                });

                states.sort(Comparator.comparing(DTOQueueProfileState::getProfileName, String.CASE_INSENSITIVE_ORDER));
                return states;
        }

}
