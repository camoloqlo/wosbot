package cl.camodev.wosbot.web.api;

import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for task management operations.
 * Provides endpoints to retrieve task information and status.
 */
public class TaskApi {

    private static final Logger logger = LoggerFactory.getLogger(TaskApi.class);

    /**
     * Gets all tasks for all profiles.
     * GET /api/tasks
     * 
     * @param ctx Javalin context
     */
    public void getTasks(Context ctx) {
        try {
            logger.info("Fetching tasks for all profiles");
            Map<Long, List<cl.camodev.wosbot.ot.DTOTaskState>> tasksMap = new HashMap<>();
            List<cl.camodev.wosbot.ot.DTOProfiles> profiles = ServProfiles.getServices().getProfiles();
            logger.info("Found {} profiles", profiles.size());
            
            for (cl.camodev.wosbot.ot.DTOProfiles profile : profiles) {
                logger.info("Processing profile: {} (ID: {})", profile.getName(), profile.getId());
                List<cl.camodev.wosbot.ot.DTODailyTaskStatus> taskStatuses = 
                    ServTaskManager.getInstance().getDailyTaskStatusPersistence(profile.getId());
                logger.info("Found {} task statuses from persistence for profile {}", taskStatuses.size(), profile.getId());
                
                if (!taskStatuses.isEmpty()) {
                    List<cl.camodev.wosbot.ot.DTOTaskState> taskStates = new ArrayList<>();
                    for (cl.camodev.wosbot.ot.DTODailyTaskStatus taskStatus : taskStatuses) {
                        logger.info("Processing task status: profileId={}, taskId={}", taskStatus.getIdProfile(), taskStatus.getIdTpDailyTask());
                        cl.camodev.wosbot.ot.DTOTaskState taskState = ServTaskManager.getInstance()
                            .getTaskState(profile.getId(), taskStatus.getIdTpDailyTask());
                        
                        if (taskState == null) {
                            // Create taskState from persistence data if not in memory
                            logger.info("TaskState not in memory, creating from persistence for taskId={}", taskStatus.getIdTpDailyTask());
                            taskState = new cl.camodev.wosbot.ot.DTOTaskState();
                            taskState.setProfileId(profile.getId());
                            taskState.setTaskId(taskStatus.getIdTpDailyTask());
                            taskState.setScheduled(true);
                            taskState.setExecuting(false);
                            taskState.setLastExecutionTime(taskStatus.getLastExecution());
                            taskState.setNextExecutionTime(taskStatus.getNextSchedule());
                        }
                        
                        // Set task name from enum
                        try {
                            cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum taskEnum = 
                                cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum.fromId(taskState.getTaskId());
                            taskState.setTaskName(taskEnum.getName());
                            logger.info("Set task name: {}", taskEnum.getName());
                        } catch (Exception e) {
                            logger.warn("Failed to get task name for taskId {}: {}", taskState.getTaskId(), e.getMessage());
                            taskState.setTaskName("Unknown Task");
                        }
                        taskStates.add(taskState);
                    }
                    if (!taskStates.isEmpty()) {
                        logger.info("Adding {} task states for profile {}", taskStates.size(), profile.getId());
                        tasksMap.put(profile.getId(), taskStates);
                    }
                }
            }
            
            logger.info("Returning tasks map with {} profiles", tasksMap.size());
            ctx.json(tasksMap);
        } catch (Exception e) {
            logger.error("Error fetching tasks: {}", e.getMessage(), e);
            ctx.status(500).json(Map.of("error", "Failed to fetch tasks", "message", e.getMessage()));
        }
    }
}
