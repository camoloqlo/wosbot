package cl.camodev.wosbot.serv.task;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.ADBConnectionException;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.ex.ProfileInReconnectStateException;
import cl.camodev.wosbot.ex.StopExecutionException;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfileStatus;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.task.impl.InitializeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TaskQueue manages and executes scheduled tasks for a game profile.
 * It handles task scheduling, execution, and error recovery.
 */
public class TaskQueue {

    private static final Logger logger = LoggerFactory.getLogger(TaskQueue.class);
    private static final long IDLE_WAIT_TIME = 999; // milliseconds to wait between task checking cycles
    
    private final PriorityBlockingQueue<DelayedTask> taskQueue = new PriorityBlockingQueue<>();
    protected EmulatorManager emuManager = EmulatorManager.getInstance();
    
    // State flags
    private volatile boolean running = false;
    private volatile boolean paused = false;
    
    // Thread that will evaluate and execute tasks
    private Thread schedulerThread;
    private DTOProfiles profile;
    private int helpAlliesCount = 0;

    public TaskQueue(DTOProfiles profile) {
        this.profile = profile;
    }

    /**
     * Adds a task to the queue.
     */
    public void addTask(DelayedTask task) {
        taskQueue.offer(task);
    }

    /**
     * Removes a specific task from the queue based on task type
     * @param taskEnum The type of task to remove
     * @return true if a task was removed, false if no matching task was found
     */
    public boolean removeTask(TpDailyTaskEnum taskEnum) {
        // Create a prototype task to compare against
        DelayedTask prototype = DelayedTaskRegistry.create(taskEnum, profile);
        if (prototype == null) {
            logWarning("Cannot create prototype for task removal: " + taskEnum.getName());
            return false;
        }

        // Remove the task from the queue using the equals method
        boolean removed = taskQueue.removeIf(task -> task.equals(prototype));

        if (removed) {
            logInfoWithTask(prototype, "Removed task " + taskEnum.getName() + " from queue");
        } else {
            logInfo("Task " + taskEnum.getName() + " was not found in queue");
        }

        return removed;
    }

    /**
     * Checks if a specific task type is currently scheduled in the queue
     * @param taskEnum The type of task to check
     * @return true if the task is in the queue, false otherwise
     */
    public boolean isTaskScheduled(TpDailyTaskEnum taskEnum) {
        DelayedTask prototype = DelayedTaskRegistry.create(taskEnum, profile);
        if (prototype == null) {
            return false;
        }
        return taskQueue.stream().anyMatch(task -> task.equals(prototype));
    }

    /**
     * Starts queue processing.
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;

        schedulerThread = new Thread(this::processTaskQueue);
        schedulerThread.setName("TaskQueue-" + profile.getName());
        schedulerThread.start();
    }

    /**
     * Main task processing loop
     */
    private void processTaskQueue() {
        boolean idlingTimeExceeded = false;
        acquireEmulatorSlot();

        while (running) {
            if (paused) {
                handlePausedState();
                continue;
            }

            boolean executedTask = false;
            long minDelay = Long.MAX_VALUE;

            // Process tasks that are ready to run
            DelayedTask task = taskQueue.peek();
            
            if (task != null && task.getDelay(TimeUnit.SECONDS) <= 0) {
                taskQueue.poll(); // Remove the task from the queue
                executedTask = executeTask(task);
                minDelay = 0;
            } else if (task != null) {
                minDelay = task.getDelay(TimeUnit.SECONDS);
            }
            
            // Handle background help actions
            checkAndHelpAllies();
            
            // Handle idle time logic
            idlingTimeExceeded = handleIdleTime(minDelay, idlingTimeExceeded);
            
            // If no task was executed, wait before checking again
            if (!executedTask) {
                waitForNextTask(minDelay);
            }
        }
    }
    
    /**
     * Executes a task and handles any exceptions
     * @param task The task to execute
     * @return true if the task was executed, false if it wasn't
     */
    private boolean executeTask(DelayedTask task) {
        // Special handling for Initialize tasks - check if next task has acceptable delay
        if (task.getTpTask() == TpDailyTaskEnum.INITIALIZE && !shouldExecuteInitializeTask()) {
            logInfoWithTask(task, "Skipping Initialize task - no upcoming tasks within acceptable idle time");
            return false;
        }

        LocalDateTime scheduledBefore = task.getScheduled();
        DTOTaskState taskState = createInitialTaskState(task);
        boolean executionSuccessful = false;

        try {
            logInfoWithTask(task, "Starting task execution: " + task.getTaskName());
            updateProfileStatus("Executing " + task.getTaskName());
            
            task.setLastExecutionTime(LocalDateTime.now());
            task.run();
            
            executionSuccessful = true;

            // Check if daily missions should be scheduled
            checkAndScheduleDailyMissions(task);
            
            // Add support for triumph progress if needed
            if (task.provideTriumphProgress()) {
                // Handle triumph progress logic here if needed
            }
            
        } catch (Exception e) {
            handleTaskExecutionException(task, e);
        } finally {
            // Always handle task rescheduling, regardless of success or failure
            handleTaskRescheduling(task, scheduledBefore);
            finalizeTaskState(task, taskState);
        }

        return executionSuccessful;
    }
    
    /**
     * Determines if an Initialize task should be executed by checking if there are
     * upcoming tasks within the acceptable idle time window
     * @return true if the Initialize task should proceed, false otherwise
     */
    private boolean shouldExecuteInitializeTask() {
        // Get the maximum idle time configuration
        long maxIdleMinutes = Optional.ofNullable(profile.getGlobalsettings().get(EnumConfigurationKey.MAX_IDLE_TIME_INT.name()))
            .map(Integer::parseInt)
            .orElse(Integer.parseInt(EnumConfigurationKey.MAX_IDLE_TIME_INT.getDefaultValue()));

        // Check if there are tasks with acceptable idle time (excluding Initialize tasks)
        return hasTasksWithAcceptableIdleTime((int) maxIdleMinutes);
    }

    private DTOTaskState createInitialTaskState(DelayedTask task) {
        DTOTaskState taskState = new DTOTaskState();
        taskState.setProfileId(profile.getId());
        taskState.setTaskId(task.getTpDailyTaskId());
        taskState.setScheduled(true);
        taskState.setExecuting(true);
        taskState.setLastExecutionTime(LocalDateTime.now());
        taskState.setNextExecutionTime(task.getScheduled());
        
        ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);
        return taskState;
    }
    
    private void finalizeTaskState(DelayedTask task, DTOTaskState taskState) {
        taskState.setExecuting(false);
        taskState.setScheduled(task.isRecurring());
        taskState.setLastExecutionTime(LocalDateTime.now());
        taskState.setNextExecutionTime(task.getScheduled());
        
        ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);
        ServScheduler.getServices().updateDailyTaskStatus(profile, task.getTpTask(), task.getScheduled());
    }
    
    private void handleTaskRescheduling(DelayedTask task, LocalDateTime scheduledBefore) {
        LocalDateTime scheduledAfter = task.getScheduled();
        
        // Prevent infinite loop by ensuring the scheduled time has changed
        if (scheduledBefore.equals(scheduledAfter)) {
            logger.info("Task {} for profile {} executed without rescheduling, changing scheduled time to now to avoid infinite loop", 
                task.getTaskName(), profile.getName());
            task.reschedule(LocalDateTime.now());
        }
        
        if (task.isRecurring()) {
            logInfoWithTask(task, "Next schedule: " + UtilTime.localDateTimeToDDHHMMSS(task.getScheduled()));
            addTask(task);
        } else {
            logInfoWithTask(task, "Task removed from schedule");
        }
    }
    
    private void handleTaskExecutionException(DelayedTask task, Exception e) {
        if (e instanceof HomeNotFoundException) {
            logErrorWithTask(task, "Home not found: " + e.getMessage());
            addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
        } else if (e instanceof StopExecutionException) {
            logErrorWithTask(task, "Execution stopped: " + e.getMessage());
        } else if (e instanceof ProfileInReconnectStateException) {
            handleReconnectStateException((ProfileInReconnectStateException) e);
        } else if (e instanceof ADBConnectionException) {
            logErrorWithTask(task, "ADB connection error: " + e.getMessage());
            addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
        } else {
            logErrorWithTask(task, "Error executing task: " + e.getMessage());
        }
    }
    
    private void handleReconnectStateException(ProfileInReconnectStateException e) {
        Long reconnectionTime = profile.getReconnectionTime();
        if (reconnectionTime != null && reconnectionTime > 0) {
            logInfo("Profile in reconnect state, pausing queue for " + reconnectionTime + " minutes");
            paused = true;
            
            new Thread(() -> {
                try {
                    Thread.sleep(TimeUnit.MINUTES.toMillis(reconnectionTime));
                } catch (InterruptedException ignored) { }
                
                if (paused) {
                    resumeAfterReconnectionDelay(reconnectionTime);
                }
            }).start();
        } else {
            logError("Profile in reconnect state, but no reconnection time set");
            attemptReconnectAndInitialize();
        }
    }
    
    private void resumeAfterReconnectionDelay(Long reconnectionTime) {
        paused = false;
        updateProfileStatus("RESUMING AFTER PAUSE");
        logger.info("TaskQueue resumed for profile {} after {} minutes pause", profile.getName(), reconnectionTime);
        
        attemptReconnectAndInitialize();
    }
    
    private void attemptReconnectAndInitialize() {
        try {
            // Click reconnect button if found
            DTOImageSearchResult reconnect = emuManager.searchTemplate(profile.getEmulatorNumber(), EnumTemplates.GAME_HOME_RECONNECT, 90);
            if (reconnect.isFound()) {
                emuManager.tapAtPoint(profile.getEmulatorNumber(), reconnect.getPoint());
            }
            
            addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
        } catch (Exception ex) {
            logger.error("Error during reconnection: {}", ex.getMessage(), ex);
        }
    }
    
    private void checkAndScheduleDailyMissions(DelayedTask task) {
        boolean dailyAutoSchedule = profile.getConfig(EnumConfigurationKey.DAILY_MISSION_AUTO_SCHEDULE_BOOL, Boolean.class);
        if (!dailyAutoSchedule || !task.provideDailyMissionProgress()) {
            return;
        }
        
        DTOTaskState state = ServTaskManager.getInstance().getTaskState(profile.getId(), TpDailyTaskEnum.DAILY_MISSIONS.getId());
        LocalDateTime next = (state != null) ? state.getNextExecutionTime() : null;
        LocalDateTime now = LocalDateTime.now();
        
        if (state == null || next == null || next.isAfter(now)) {
            scheduleDailyMissionsNow();
        }
    }
    
    private void scheduleDailyMissionsNow() {
        DelayedTask prototype = DelayedTaskRegistry.create(TpDailyTaskEnum.DAILY_MISSIONS, profile);
        
        // Check if task already exists in the queue
        DelayedTask existing = taskQueue.stream()
            .filter(prototype::equals)
            .findFirst()
            .orElse(null);
        
        if (existing != null) {
            // Task already exists, reschedule it to run now
            taskQueue.remove(existing);
            existing.reschedule(LocalDateTime.now());
            existing.setRecurring(true);
            taskQueue.offer(existing);
            
            logInfoWithTask(existing, "Rescheduled existing " + TpDailyTaskEnum.DAILY_MISSIONS + " to run now");
        } else {
            // Task does not exist, create a new instance
            prototype.reschedule(LocalDateTime.now());
            prototype.setRecurring(false);
            taskQueue.offer(prototype);
            logInfoWithTask(prototype, "Enqueued new immediate " + TpDailyTaskEnum.DAILY_MISSIONS);
        }
    }
    
    // Idle time management methods
    private void idlingEmulator(long minDelay) {
        emuManager.closeEmulator(profile.getEmulatorNumber());
        logInfo("Closing game due to large inactivity");
        
        LocalDateTime scheduledTime = LocalDateTime.now().plusSeconds(minDelay);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        updateProfileStatus("Idling till " + formatter.format(scheduledTime));
        
        emuManager.releaseEmulatorSlot(profile);
    }

    private void enqueueNewTask() {
        logInfo("Scheduled task will start soon");
        acquireEmulatorSlot();
        addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
    }
    
    /**
     * Acquires an emulator slot for this profile
     */
    private void acquireEmulatorSlot() {
        updateProfileStatus("Getting queue slot");
        try {
            emuManager.adquireEmulatorSlot(profile, (thread, position) -> {
                updateProfileStatus("Waiting for slot, position: " + position);
            });
        } catch (InterruptedException e) {
            logger.error("Interrupted while acquiring emulator slot for profile " + profile.getName(), e);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Handles the paused state of the task queue
     */
    private void handlePausedState() {
        try {
            updateProfileStatus("PAUSED");
            logger.info("Profile {} is paused.", profile.getName());
            Thread.sleep(1000); // Wait while paused
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Waits for the next task to be ready, displaying status information
     */
    private void waitForNextTask(long minDelay) {
        try {
            String formattedTime = formatWaitTime(minDelay);
            String nextTaskName = taskQueue.isEmpty() ? "None" : taskQueue.peek().getTaskName();
            
            updateProfileStatus("Idling for " + formattedTime + "\nNext task: " + nextTaskName);
            Thread.sleep(IDLE_WAIT_TIME);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Formats delay time for display
     */
    private String formatWaitTime(long delayInSeconds) {
        if (delayInSeconds == Long.MAX_VALUE || delayInSeconds > 86399) {
            return "No tasks";
        }
        
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        long safeDelay = Math.max(0, delayInSeconds);
        return LocalTime.ofSecondOfDay(safeDelay).format(timeFormatter);
    }
    
    /**
     * Handles idle time logic
     * @return updated idling state
     */
    private boolean handleIdleTime(long minDelay, boolean idlingTimeExceeded) {
        if (minDelay == Long.MAX_VALUE) {
            return idlingTimeExceeded; // No change if no tasks in queue
        }
        
        long maxIdleMinutes = Optional.ofNullable(profile.getGlobalsettings().get(EnumConfigurationKey.MAX_IDLE_TIME_INT.name()))
            .map(Integer::parseInt)
            .orElse(Integer.parseInt(EnumConfigurationKey.MAX_IDLE_TIME_INT.getDefaultValue()));
        
        // If delay exceeds max idle time and we haven't already handled it
        if (!idlingTimeExceeded && minDelay > TimeUnit.MINUTES.toSeconds(maxIdleMinutes)) {
            idlingEmulator(minDelay);
            return true;
        }
        
        // If we're idling but the next task is coming soon, re-acquire the emulator
        if (idlingTimeExceeded && minDelay < TimeUnit.MINUTES.toSeconds(1)) {
            enqueueNewTask();
            return false;
        }
        
        return idlingTimeExceeded;
    }
    
    /**
     * Periodically checks for and helps allies if enabled
     */
    private void checkAndHelpAllies() {
        helpAlliesCount++;
        if (helpAlliesCount % 10 != 0) {
            return; // Only check every 10 cycles
        }
        
        helpAlliesCount = 0;
        
        if (!profile.getConfig(EnumConfigurationKey.ALLIANCE_HELP_BOOL, Boolean.class)) {
            return; // Help is disabled
        }
        
        try {
            new Thread(() -> {
                if (emuManager.isRunning(profile.getEmulatorNumber())) {
                    DTOImageSearchResult helpRequest = emuManager.searchTemplate(
                        profile.getEmulatorNumber(), 
                        EnumTemplates.GAME_HOME_SHORTCUTS_HELP_REQUEST2, 
                        90
                    );
                    
                    if (helpRequest.isFound()) {
                        emuManager.tapAtPoint(profile.getEmulatorNumber(), helpRequest.getPoint());
                        logInfo("Help request found and tapped");
                    }
                }
            }).start();
        } catch (Exception e) {
            logError("Error checking help request: " + e.getMessage());
        }
    }
    
    /**
     * Checks if there are queued tasks (excluding Initialize tasks) with idle time less than the specified delay
     * @param maxIdleMinutes Maximum idle time allowed in minutes
     * @return true if there are tasks with acceptable idle time, false otherwise
     */
    public boolean hasTasksWithAcceptableIdleTime(int maxIdleMinutes) {
        if (taskQueue.isEmpty()) {
            return false;
        }

        long maxIdleSeconds = TimeUnit.MINUTES.toSeconds(maxIdleMinutes);

        return taskQueue.stream()
            .filter(task -> task.getTpTask() != TpDailyTaskEnum.INITIALIZE) // Exclude Initialize tasks
            .anyMatch(task -> {
                long taskDelay = task.getDelay(TimeUnit.SECONDS);
                return taskDelay >= 0 && taskDelay < maxIdleSeconds;
            });
    }

    // Logging helper methods
    private void logInfo(String message) {
        logger.info(message);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue", profile.getName(), message);
    }
    
    private void logInfoWithTask(DelayedTask task, String message) {
        logger.info(message);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, task.getTaskName(), profile.getName(), message);
    }
    
    private void logWarning(String message) {
        logger.warn(message);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, "TaskQueue", profile.getName(), message);
    }
    
    private void logWarningWithTask(DelayedTask task, String message) {
        logger.warn(message);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, task.getTaskName(), profile.getName(), message);
    }
    
    private void logError(String message) {
        logger.error(message);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "TaskQueue", profile.getName(), message);
    }
    
    private void logErrorWithTask(DelayedTask task, String message) {
        logger.error(message);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, task.getTaskName(), profile.getName(), message);
    }
    
    private void updateProfileStatus(String status) {
        ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), status));
    }

    /**
     * Immediately stops queue processing, regardless of its state.
     */
    public void stop() {
        running = false; // Stop the main loop

        if (schedulerThread != null) {
            schedulerThread.interrupt(); // Interrupt the thread to force an immediate exit

            try {
                schedulerThread.join(1000); // Wait up to 1 second for the thread to finish
            } catch (InterruptedException e) {
                logger.error("Interrupted while stopping TaskQueue for profile " + profile.getName(), e);
                Thread.currentThread().interrupt();
            }
        }

        // Remove all pending tasks from the queue
        taskQueue.clear();
        updateProfileStatus("NOT RUNNING");
        logger.info("TaskQueue stopped immediately for profile " + profile.getName());
    }

    /**
     * Pauses queue processing, keeping tasks in the queue.
     */
    public void pause() {
        paused = true;
        updateProfileStatus("PAUSE REQUESTED");
        logger.info("TaskQueue paused for profile " + profile.getName());
    }

    /**
     * Resumes queue processing.
     */
    public void resume() {
        paused = false;
        updateProfileStatus("RESUMING");
        logger.info("TaskQueue resumed for profile " + profile.getName());
    }

    /**
     * Executes a specific task immediately
     */
    public void executeTaskNow(TpDailyTaskEnum taskEnum, boolean recurring) {
        // Obtain the task prototype from the registry
        DelayedTask prototype = DelayedTaskRegistry.create(taskEnum, profile);
        if (prototype == null) {
            logWarning("Task not found: " + taskEnum);
            return;
        }

        // Check if the task already exists in the queue
        DelayedTask existing = taskQueue.stream()
            .filter(prototype::equals)
            .findFirst()
            .orElse(null);

        if (existing != null) {
            // Task already exists, reschedule it to run now
            taskQueue.remove(existing);
            existing.reschedule(LocalDateTime.now());
            existing.setRecurring(recurring);
            taskQueue.offer(existing);

            logInfoWithTask(existing, "Rescheduled existing " + taskEnum + " to run now");
        } else {
            // Task does not exist, create a new instance
            prototype.reschedule(LocalDateTime.now());
            prototype.setRecurring(recurring);
            taskQueue.offer(prototype);
            logInfoWithTask(prototype, "Enqueued new immediate " + taskEnum);
        }

        // Update task state
        DTOTaskState taskState = new DTOTaskState();
        taskState.setProfileId(profile.getId());
        taskState.setTaskId(taskEnum.getId());
        taskState.setScheduled(true);
        taskState.setExecuting(false);
        taskState.setLastExecutionTime(prototype.getScheduled());
        taskState.setNextExecutionTime(LocalDateTime.now());
        ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);
    }

    public DTOProfiles getProfile() {
        return profile;
    }
}

