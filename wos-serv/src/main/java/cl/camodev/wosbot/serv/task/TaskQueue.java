package cl.camodev.wosbot.serv.task;

import java.time.Duration;
import java.time.LocalDateTime;
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
import cl.camodev.wosbot.ot.DTOTaskQueueStatus;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.impl.ServConfig;
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
    protected final EmulatorManager emuManager = EmulatorManager.getInstance();

    // State flags
    final DTOTaskQueueStatus taskQueueStatus = new DTOTaskQueueStatus();

    // Thread that will evaluate and execute tasks
    private Thread schedulerThread;
    private DTOProfiles profile;

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
     * 
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

    public LocalDateTime getDelay() {
        return taskQueueStatus.getDelayUntil();
    }

    /**
     * Checks if a specific task type is currently scheduled in the queue
     * 
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
        if (taskQueueStatus.isRunning()) {
            return;
        }

        taskQueueStatus.setRunning(true);

        schedulerThread = Thread.ofVirtual().unstarted(this::processTaskQueue);
        schedulerThread.setName("TaskQueue-" + profile.getName());
        schedulerThread.start();
    }

    /**
     * Main task processing loop
     */
    private void processTaskQueue() {
        acquireEmulatorSlot();

        while (taskQueueStatus.isRunning()) {
            taskQueueStatus.loopStarted();

            profile = ServProfiles.getServices().getProfiles().stream()
                    .filter(p -> p.getId().equals(profile.getId()))
                    .findFirst()
                    .orElse(profile);

            if (taskQueueStatus.isPaused()) {
                handlePausedState();
                continue;
            } else if (taskQueueStatus.isReadyToReconnect() && !emuManager.isRunning(profile.getEmulatorNumber())) {
                logInfo("Emulator is not running, acquiring emulator slot now");
                acquireEmulatorSlot();
            }

            DelayedTask task = taskQueue.peek();

            if (task != null && task.getDelay(TimeUnit.SECONDS) <= 0) {
                taskQueue.poll();
                taskQueueStatus.getLoopState().setExecutedTask(executeTask(task));
            } else if (task != null) {
                taskQueueStatus.setDelayUntil(task.getScheduled());
            }

            runBackgroundChecks();
            handleIdleTime();

            // Waits for the next task to be ready, displaying status information
            if (!taskQueueStatus.getLoopState().isExecutedTask() && !taskQueueStatus.isPaused()) {
                String nextTaskName = taskQueue.isEmpty() ? "None" : taskQueue.peek().getTaskName();
                String timeFormatted = formatTimeUntil(taskQueueStatus.getDelayUntil());
                updateProfileStatus("Idling for " + timeFormatted + "\nNext task: " + nextTaskName);

                // Sleep for remaining time to maintain consistent 1-second intervals
                taskQueueStatus.getLoopState().endLoop();
                long sleepTime = Math.max(0, IDLE_WAIT_TIME - taskQueueStatus.getLoopState().getDuration());
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Executes a task and handles any exceptions
     * 
     * @param task The task to execute
     * @return true if the task was executed, false if it wasn't
     */
    private boolean executeTask(DelayedTask task) {
        // Special handling for Initialize tasks - check if next task has acceptable
        // delay
        if (task.getTpTask() == TpDailyTaskEnum.INITIALIZE && !shouldExecuteInitializeTask()) {
            logInfoWithTask(task, "Skipping Initialize task - no upcoming tasks within acceptable idle time");
            return false;
        }

        LocalDateTime scheduledBefore = task.getScheduled();
        DTOTaskState taskState = createInitialTaskState(task);
        boolean executionSuccessful;

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
            executionSuccessful = false;
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
     * 
     * @return true if the Initialize task should proceed, false otherwise
     */
    private boolean shouldExecuteInitializeTask() {
        // Get the maximum idle time configuration desde ServConfig con fallback al
        // default
        int maxIdleMinutes = Optional
                .ofNullable(ServConfig.getServices().getGlobalConfig())
                .map(cfg -> cfg.get(EnumConfigurationKey.MAX_IDLE_TIME_INT.name()))
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(EnumConfigurationKey.MAX_IDLE_TIME_INT.getDefaultValue()));

        // Check if there are tasks with acceptable idle time (excluding Initialize
        // tasks)
        return hasTasksWithAcceptableIdleTime(maxIdleMinutes);
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
            if (task.isRecurring()) {
                logInfoWithTask(task, "Task " + task.getTaskName()
                        + " executed without rescheduling, changing scheduled time to now to avoid infinite loop");
                task.reschedule(LocalDateTime.now());
            }
        }

        if (task.isRecurring()) {
            logInfoWithTask(task,
                    "Next task scheduled to run in: " + UtilTime.localDateTimeToDDHHMMSS(task.getScheduled()));
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
        Long reconnectionTime = profile.getReconnectionTime(); // in minutes
        if (reconnectionTime != null && reconnectionTime > 0) {
            logInfo("Profile in reconnect state, pausing queue for " + reconnectionTime + " minutes");
            taskQueueStatus.setReconnectAt(reconnectionTime);
        } else {
            logError("Profile in reconnect state, but no reconnection time set");
            attemptReconnectAndInitialize();
        }
    }

    private void resumeAfterReconnectionDelay() {
        taskQueueStatus.setNeedsReconnect(false);
        updateProfileStatus("RESUMING AFTER PAUSE");
        logInfo("TaskQueue resuming after "
                + Duration.between(taskQueueStatus.getPausedAt(), LocalDateTime.now()).toMinutes()
                + " minutes pause");
        taskQueueStatus.setPaused(false);

        if (!emuManager.isRunning(profile.getEmulatorNumber())) {
            logInfo("While resuming, found instance closed. Acquiring a slot now.");
            acquireEmulatorSlot();
        }
        attemptReconnectAndInitialize();
    }

    private void attemptReconnectAndInitialize() {
        try {
            // Click reconnect button if found
            DTOImageSearchResult reconnect = emuManager.searchTemplate(profile.getEmulatorNumber(),
                    EnumTemplates.GAME_HOME_RECONNECT, 90);
            if (reconnect.isFound()) {
                emuManager.tapAtPoint(profile.getEmulatorNumber(), reconnect.getPoint());
            }

            addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
        } catch (Exception ex) {
            logError("Error during reconnection: " + ex.getMessage());
        }
    }

    private void checkAndScheduleDailyMissions(DelayedTask task) {
        boolean dailyAutoSchedule = profile.getConfig(EnumConfigurationKey.DAILY_MISSION_AUTO_SCHEDULE_BOOL,
                Boolean.class);
        if (!dailyAutoSchedule || !task.provideDailyMissionProgress()) {
            return;
        }

        DTOTaskState state = ServTaskManager.getInstance().getTaskState(profile.getId(),
                TpDailyTaskEnum.DAILY_MISSIONS.getId());
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
    private void idlingEmulator(LocalDateTime delayUntil) {
        boolean sendToBackground = Optional
                .ofNullable(ServConfig.getServices().getGlobalConfig())
                .map(cfg -> cfg.getOrDefault(
                        EnumConfigurationKey.IDLE_BEHAVIOR_SEND_TO_BACKGROUND_BOOL.name(),
                        EnumConfigurationKey.IDLE_BEHAVIOR_SEND_TO_BACKGROUND_BOOL.getDefaultValue()))
                .map(Boolean::parseBoolean)
                .orElse(Boolean
                        .parseBoolean(EnumConfigurationKey.IDLE_BEHAVIOR_SEND_TO_BACKGROUND_BOOL.getDefaultValue()));

        if (sendToBackground) {
            // Send game to background (home screen), keep emulator and game running
            emuManager.sendGameToBackground(profile.getEmulatorNumber());
            logInfo("Sending game to background due to large inactivity. Next task: " + delayUntil);
        } else {
            // Close the entire emulator (original behavior)
            emuManager.closeEmulator(profile.getEmulatorNumber());
            logInfo("Closing emulator due to large inactivity. Next task: " + delayUntil);
            emuManager.releaseEmulatorSlot(profile);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        updateProfileStatus("Idling till " + formatter.format(delayUntil));
    }

    private void enqueueNewTask() {
        logInfo("Scheduled task will start soon");

        // Only acquire a new emulator slot if the emulator is not running
        // (i.e., if we closed the entire emulator rather than just the game)
        if (!emuManager.isRunning(profile.getEmulatorNumber())) {
            acquireEmulatorSlot();
        }

        addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
    }

    /**
     * Acquires an emulator slot for this profile
     */
    private void acquireEmulatorSlot() {
        updateProfileStatus("Getting queue slot");
        try {
            emuManager.adquireEmulatorSlot(profile,
                    (thread, position) -> updateProfileStatus("Waiting for slot, position: " + position));
        } catch (InterruptedException e) {
            logError("Interrupted while acquiring emulator slot: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Handles the paused state of the task queue
     */
    private void handlePausedState() {
        if (taskQueueStatus.getDelayUntil().isBefore(LocalDateTime.now())) {
            if (taskQueueStatus.needsReconnect()) {
                resumeAfterReconnectionDelay();
            } else {
                taskQueueStatus.setPaused(false);
                updateProfileStatus("RESUMING");
                if (!emuManager.isRunning(profile.getEmulatorNumber())) {
                    logInfo("While resuming, found instance closed. Acquiring a slot now.");
                    acquireEmulatorSlot();
                }
            }
            return;
        }
        try {
            updateProfileStatus("PAUSED");
            if (LocalDateTime.now().getSecond() % 10 == 0)
                logInfo("Profile is paused");
            Thread.sleep(1000); // Wait while paused
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Formats the duration until the target time as HH:mm:ss
     */
    private String formatTimeUntil(LocalDateTime targetTime) {
        Duration timeUntilNext = Duration.between(LocalDateTime.now(), targetTime);

        long hours = timeUntilNext.toHours();
        long minutes = timeUntilNext.toMinutesPart();
        long seconds = timeUntilNext.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Handles idle time logic
     */
    private void handleIdleTime() {
        if (taskQueueStatus.getLoopState().isExecutedTask() || taskQueue.isEmpty()) {
            return;
        }

        // Obtener MAX_IDLE_TIME_INT desde ServConfig en lugar del perfil, con fallback
        // al default
        int idleLimit = Optional
                .ofNullable(ServConfig.getServices().getGlobalConfig())
                .map(cfg -> cfg.get(EnumConfigurationKey.MAX_IDLE_TIME_INT.name()))
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(EnumConfigurationKey.MAX_IDLE_TIME_INT.getDefaultValue()));
        taskQueueStatus.setIdleTimeLimit(idleLimit);

        // If delay exceeds max idle time, and we haven't yet handled it
        if (!taskQueueStatus.isIdleTimeExceeded() && taskQueueStatus.checkIdleTimeExceeded()) {
            idlingEmulator(taskQueueStatus.getDelayUntil());
            taskQueueStatus.setIdleTimeExceeded(true);
            return;
        }

        // If we're idling but the next task is coming soon, re-acquire the emulator
        if (taskQueueStatus.isIdleTimeExceeded()
                && LocalDateTime.now().plusMinutes(1).isAfter(taskQueueStatus.getDelayUntil())) {
            enqueueNewTask();
            taskQueueStatus.setIdleTimeExceeded(false);
            return;
        }
    }

    /**
     * Checks if the bear hunt is running
     */
    private void isBearRunning() {
        DTOImageSearchResult result = emuManager.searchTemplate(
                profile.getEmulatorNumber(),
                EnumTemplates.BEAR_HUNT_IS_RUNNING,
                90);
        if (result.isFound()) {
            logInfo("Bear is running, pausing task running for 30 minutes");
            pause();
            taskQueueStatus.setDelayUntil(LocalDateTime.now().plusMinutes(30));
        }
    }

    private void runBackgroundChecks() {
        // Counter logic for periodic checks
        if (!taskQueueStatus.shouldRunBackgroundChecks())
            return;

        // Early check with detailed logging
        synchronized (emuManager) {
            boolean emulatorRunning = emuManager.isRunning(profile.getEmulatorNumber());
            boolean notPaused = !taskQueueStatus.isPaused();

            if (!emulatorRunning || !notPaused) {
                logInfo(String.format("Skipping background checks - Emulator running: %s, Not paused: %s",
                        emulatorRunning, notPaused));
                return;
            }
        }

        boolean runHelpAllies = profile.getConfig(EnumConfigurationKey.ALLIANCE_HELP_BOOL, Boolean.class);

        // Now do the actual work with synchronization
        synchronized (emuManager) {
            // Double-check in case state changed
            if (!emuManager.isRunning(profile.getEmulatorNumber()) || taskQueueStatus.isPaused()) {
                logInfo("State changed during wait, skipping background checks");
                return;
            }

            logInfo("Running background checks...");
            try {
                isBearRunning();
                if (runHelpAllies) {
                    checkHelpAllies();
                }
            } catch (Exception e) {
                logError("Error running background tasks: " + e.getMessage());
            }
        }
    }

    private void checkHelpAllies() {
        DTOImageSearchResult helpRequest = emuManager.searchTemplate(
                profile.getEmulatorNumber(),
                EnumTemplates.GAME_HOME_SHORTCUTS_HELP_REQUEST2,
                90);
        if (helpRequest.isFound()) {
            emuManager.tapAtPoint(profile.getEmulatorNumber(), helpRequest.getPoint());
            logInfo("Help request found and tapped");
        }
    }

    /**
     * Checks if there are queued tasks (excluding Initialize tasks) with idle time
     * less than the specified delay
     * 
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
                    // return taskDelay >= 0 && taskDelay < maxIdleSeconds;
                    return taskDelay < maxIdleSeconds;

                });
    }

    // Logging helper methods
    @SuppressWarnings(value = { "unused" })
    private void logDebug(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.info(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.DEBUG, "TaskQueue", profile.getName(), message);
    }

    @SuppressWarnings(value = { "unused" })
    private void logDebugWithTask(DelayedTask task, String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.info(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.DEBUG, task.getTaskName(), profile.getName(), message);
    }

    private void logInfo(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.info(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue", profile.getName(), message);
    }

    private void logInfoWithTask(DelayedTask task, String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.info(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, task.getTaskName(), profile.getName(), message);
    }

    private void logWarning(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.warn(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, "TaskQueue", profile.getName(), message);
    }

    @SuppressWarnings(value = { "unused" })
    private void logWarningWithTask(DelayedTask task, String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.warn(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, task.getTaskName(), profile.getName(), message);
    }

    private void logError(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.error(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "TaskQueue", profile.getName(), message);
    }

    private void logErrorWithTask(DelayedTask task, String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.error(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, task.getTaskName(), profile.getName(), message);
    }

    private void updateProfileStatus(String status) {
        ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), status));
    }

    /**
     * Immediately stops queue processing, regardless of its state.
     */
    public void stop() {
        taskQueueStatus.setRunning(false); // Stop the main loop

        if (schedulerThread != null) {
            schedulerThread.interrupt(); // Interrupt the thread to force an immediate exit

            try {
                schedulerThread.join(1000); // Wait up to 1 second for the thread to finish
            } catch (InterruptedException e) {
                logError("Interrupted while stopping TaskQueue: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        taskQueueStatus.reset(); // Reset status
        // Remove all pending tasks from the queue
        taskQueue.clear();
        updateProfileStatus("NOT RUNNING");
        logInfo("TaskQueue stopped immediately");
    }

    /**
     * Pauses queue processing, keeping tasks in the queue.
     */
    public void pause() {
        taskQueueStatus.pause();
        updateProfileStatus("PAUSE REQUESTED");
        logInfo("TaskQueue paused");
    }

    /**
     * Resumes queue processing.
     */
    public void resume() {
        taskQueueStatus.setPaused(false);
        updateProfileStatus("RESUMING");
        logInfo("TaskQueue resumed");
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

        taskQueueStatus.setNeedsReconnect(true);
        taskQueueStatus.setPaused(false);

        // Check if the task already exists in the queue
        DelayedTask existing = taskQueue.stream()
                .filter(prototype::equals)
                .findFirst()
                .orElse(null);

        if (existing != null) {
            // Task already exists, reschedule it to run now
            taskQueue.remove(existing);
            existing.setProfile(profile);
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
        taskState.setLastExecutionTime(LocalDateTime.now());
        taskState.setNextExecutionTime(prototype.getScheduled());
        ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);
    }

    public DTOProfiles getProfile() {
        return profile;
    }
}
