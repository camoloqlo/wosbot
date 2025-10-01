package cl.camodev.wosbot.web.api;

import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTODailyTaskStatus;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API controller for task management operations.
 */
@RestController
@RequestMapping("/api")
public class TaskApi {

    private static final Logger logger = LoggerFactory.getLogger(TaskApi.class);

    @GetMapping("/tasks")
    public ResponseEntity<?> getTasks() {
        try {
            logger.info("Fetching tasks for all profiles");
            Map<Long, List<DTOTaskState>> tasksMap = new HashMap<>();
            List<DTOProfiles> profiles = ServProfiles.getServices().getProfiles();
            
            for (DTOProfiles profile : profiles) {
                List<DTODailyTaskStatus> taskStatuses = 
                    ServTaskManager.getInstance().getDailyTaskStatusPersistence(profile.getId());
                
                if (!taskStatuses.isEmpty()) {
                    List<DTOTaskState> taskStates = new ArrayList<>();
                    for (DTODailyTaskStatus taskStatus : taskStatuses) {
                        DTOTaskState taskState = ServTaskManager.getInstance()
                            .getTaskState(profile.getId(), taskStatus.getIdTpDailyTask());
                        
                        if (taskState == null) {
                            // Create taskState from persistence data if not in memory
                            taskState = new DTOTaskState();
                            taskState.setProfileId(profile.getId());
                            taskState.setTaskId(taskStatus.getIdTpDailyTask());
                            taskState.setScheduled(false);
                            taskState.setExecuting(false);
                            taskState.setLastExecutionTime(taskStatus.getLastExecution());
                            taskState.setNextExecutionTime(taskStatus.getNextSchedule());
                        }
                        
                        // Set task name from enum
                        try {
                            TpDailyTaskEnum taskEnum = TpDailyTaskEnum.fromId(taskState.getTaskId());
                            taskState.setTaskName(taskEnum.getName());
                        } catch (Exception e) {
                            taskState.setTaskName("Unknown Task");
                        }
                        taskStates.add(taskState);
                    }
                    if (!taskStates.isEmpty()) {
                        tasksMap.put(profile.getId(), taskStates);
                    }
                }
            }
            
            return ResponseEntity.ok(tasksMap);
        } catch (Exception e) {
            logger.error("Error fetching tasks: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "Failed to fetch tasks", "message", e.getMessage()));
        }
    }
}
