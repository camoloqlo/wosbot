package cl.camodev.wosbot.serv.task;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;



import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.ex.StopExecutionException;
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

public class TaskQueue {


        private static final Logger logger = LoggerFactory.getLogger(TaskQueue.class);
        private final PriorityBlockingQueue<DelayedTask> taskQueue = new PriorityBlockingQueue<>();

        private static final int PAUSE_SLEEP_MS = 1000;
        private static final int IDLE_CHECK_INTERVAL_MS = 999;
        // Flag to stop the scheduler loop.
	private volatile boolean running = false;
        // Flag to pause/resume the scheduler.
	private volatile boolean paused = false;
        // Thread that evaluates and executes tasks.
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
	 * @param taskEnum The type of task to remove
	 * @return true if a task was removed, false if no matching task was found
	 */
	public boolean removeTask(TpDailyTaskEnum taskEnum) {
		// Create a prototype task to compare against
		DelayedTask prototype = DelayedTaskRegistry.create(taskEnum, profile);
		if (prototype == null) {
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, "TaskQueue",
				profile.getName(), "Cannot create prototype for task removal: " + taskEnum.getName());
			logger.warn("Cannot create prototype for task removal: " + taskEnum.getName());
			return false;
		}

		// Remove the task from the queue using the equals method
		boolean removed = taskQueue.removeIf(task -> task.equals(prototype));

		if (removed) {
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue",
				profile.getName(), "Removed task " + taskEnum.getName() + " from queue");
			logger.info("Removed task " + taskEnum.getName() + " from queue");
		} else {
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue",
				profile.getName(), "Task " + taskEnum.getName() + " was not found in queue");
			logger.info("Task " + taskEnum.getName() + " was not found in queue");
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
         * Starts processing the queue.
         */
	public void start() {

		if (running)
			return;
		running = true;

		schedulerThread = new Thread(() -> {

			boolean idlingTimeExceded = false;
			ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "Getting queue slot"));
			try {
                                EmulatorManager.getInstance().acquireEmulatorSlot(profile, (thread, position) -> {
					ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "Waiting for slot, position: " + position));
				});
			} catch (InterruptedException e) {
				logger.error("Interrupted while acquiring emulator slot for profile " + profile.getName(), e);
			}
                        while (running) {
                                if (!EmulatorManager.getInstance().isRunning(profile.getEmulatorNumber())) {
                                        ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING,
                                                        "TaskQueue", profile.getName(),
                                                        "Emulator closed, stopping queue");
                                        running = false;
                                        break;
                                }
                                // Check if paused and skip execution if so
                                if (paused) {
                                        try {
						ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "PAUSED"));
						logger.info("Profile " + profile.getName() + " is paused.");
                                                Thread.sleep(PAUSE_SLEEP_MS); // Wait while paused
						continue;
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}

				boolean executedTask = false;
                                long nextTaskDelaySeconds = Long.MAX_VALUE;

                                // Perform pre-check to ensure the game is running

                                // Process tasks that are ready to run
				DelayedTask task;
				while ((task = taskQueue.peek()) != null) {
					DTOTaskState taskState = null;
					long delayInSeconds = task.getDelay(TimeUnit.SECONDS);

                                // If the first task is not ready, none will be (queue is ordered)
					if (delayInSeconds > 0) {
                                                nextTaskDelaySeconds = delayInSeconds;
						break;
					}

                                        // Remove the task from the queue
					taskQueue.poll();

					try {
						ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, task.getTaskName(), profile.getName(), "Starting task execution");
						ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "Executing " + task.getTaskName()));

						taskState = new DTOTaskState();
						taskState.setProfileId(profile.getId());
						taskState.setTaskId(task.getTpDailyTaskId());
						taskState.setScheduled(true);
						taskState.setExecuting(true);
						taskState.setLastExecutionTime(LocalDateTime.now());
						taskState.setNextExecutionTime(task.getScheduled());
						ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);

						task.run();
					} catch (HomeNotFoundException e) {
						ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, task.getTaskName(), profile.getName(), e.getMessage());
						logger.error("Error executing task " + task.getTaskName() + " for profile " + profile.getName() + ": " + e.getMessage(), e);
						addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
					} catch (StopExecutionException e){
						logger.error("Execution stopped for task " + task.getTaskName() + " for profile " + profile.getName() + ": " + e.getMessage(), e);
						stop();
					}
					catch (Exception e) {
						ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, task.getTaskName(), profile.getName(), e.getMessage());
						logger.error("Error executing task " + task.getTaskName() + " for profile " + profile.getName() + ": " + e.getMessage(), e);
					}

					if (task.isRecurring()) {
						ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, task.getTaskName(), profile.getName(), "Next schedule: " + UtilTime.localDateTimeToDDHHMMSS(task.getScheduled()));
						addTask(task);
					} else {
						ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, task.getTaskName(), profile.getName(), "Task removed from schedule");
					}

					boolean dailyAutoSchedule = profile.getConfig(EnumConfigurationKey.DAILY_MISSION_AUTO_SCHEDULE_BOOL,Boolean.class);
					if (dailyAutoSchedule) {
						DTOTaskState state = ServTaskManager.getInstance().getTaskState(profile.getId(), TpDailyTaskEnum.DAILY_MISSIONS.getId());
						LocalDateTime next = (state != null)? state.getNextExecutionTime(): null;
						LocalDateTime now = LocalDateTime.now();
						if (task.provideDailyMissionProgress()	&& (state == null || next == null || next.isAfter(now))) {
							DelayedTask prototype = DelayedTaskRegistry.create(TpDailyTaskEnum.DAILY_MISSIONS, profile);

							// verify if the task already exists in the queue
							DelayedTask existing = taskQueue.stream().filter(prototype::equals).findFirst().orElse(null);

							if (existing != null) {
								// task already exists, reschedule it to run now
								taskQueue.remove(existing);
								existing.reschedule(LocalDateTime.now());
								existing.setRecurring(true);
								taskQueue.offer(existing);

								ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue", profile.getName(), "Rescheduled existing " + TpDailyTaskEnum.DAILY_MISSIONS + " to run now");
							} else {
								// task does not exist, create a new instance and schedule it just once
								prototype.reschedule(LocalDateTime.now());
								prototype.setRecurring(false);
								taskQueue.offer(prototype);
								ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue", profile.getName(), "Enqueued new immediate " + TpDailyTaskEnum.DAILY_MISSIONS);
							}



						}
					}


					if (task.provideTriumphProgress()){

					}

					taskState.setExecuting(false);
					taskState.setScheduled(task.isRecurring());
					taskState.setLastExecutionTime(LocalDateTime.now());
					taskState.setNextExecutionTime(task.getScheduled());

					ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);
					ServScheduler.getServices().updateDailyTaskStatus(profile, task.getTpTask(), task.getScheduled());

					executedTask = true;
				}

                                // If no task was executed, get the delay of the next task
                                if (!executedTask && !taskQueue.isEmpty()) {
                                        DelayedTask nextTask = taskQueue.peek();
                                        if (nextTask != null) {
                                                nextTaskDelaySeconds = nextTask.getDelay(TimeUnit.SECONDS);
                                        }
				}

                                // Check conditions based on the minimum delay of the queue
                                if (nextTaskDelaySeconds != Long.MAX_VALUE) { // Ensure there are tasks in the queue
					long maxIdle = 0;
					maxIdle = Optional.ofNullable(profile.getGlobalsettings().get(EnumConfigurationKey.MAX_IDLE_TIME_INT.name())).map(Integer::parseInt).orElse(Integer.parseInt(EnumConfigurationKey.MAX_IDLE_TIME_INT.getDefaultValue()));

                                        if (!idlingTimeExceded && nextTaskDelaySeconds > TimeUnit.MINUTES.toSeconds(maxIdle)) {
                                                idlingTimeExceded = true;
                                                idlingEmulator(nextTaskDelaySeconds);
					}

                                        // If the delay drops below a minute, acquire the emulator slot and enqueue an initialization task
                                        if (idlingTimeExceded && nextTaskDelaySeconds < TimeUnit.MINUTES.toSeconds(1)) {
                                                enqueueNewTask();
                                                idlingTimeExceded = false; // Reset condition for future evaluations
					}
				}

                                // If no task was executed, wait a bit before checking again
				if (!executedTask) {
					try {
                                                String formattedTime;
                                                if (nextTaskDelaySeconds == Long.MAX_VALUE || nextTaskDelaySeconds > 86399) {
                                                // If there are no tasks or the delay is very long, show an appropriate message
							formattedTime = "No tasks";
						} else {
							DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                                                        // Convert the delay (seconds) to HH:mm:ss format
                                                        formattedTime = LocalTime.ofSecondOfDay(nextTaskDelaySeconds).format(timeFormatter);
						}

						ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "Idling for " + formattedTime + "\nNext task: " + (taskQueue.isEmpty() ? "None" : taskQueue.peek().getTaskName())));
                                                Thread.sleep(IDLE_CHECK_INTERVAL_MS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
                                }
                        }
                        EmulatorManager.getInstance().releaseEmulatorSlot(profile);
                        taskQueue.clear();
                        ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "NOT RUNNING "));
                        logger.info("TaskQueue stopped for profile " + profile.getName());
                });
                schedulerThread.start();
        }

        // Helper methods
    private void idlingEmulator(long delaySeconds) {
		EmulatorManager.getInstance().closeEmulator(profile.getEmulatorNumber());
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue", profile.getName(), "Closing game due to large inactivity");
        LocalDateTime scheduledTime = LocalDateTime.now().plusSeconds(delaySeconds);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "Idling till " + formatter.format(scheduledTime)));
		EmulatorManager.getInstance().releaseEmulatorSlot(profile);
	}

        private void enqueueNewTask() {
                ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue", profile.getName(), "Scheduled tasks will start soon");

                try {
                        EmulatorManager.getInstance().acquireEmulatorSlot(profile, (thread, position) -> {
                                ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "Waiting for slot, position: " + position));
                        });
                } catch (InterruptedException e) {
                        logger.error("Interrupted while acquiring emulator slot for profile " + profile.getName(), e);
                }
                addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
        }

        /**
         * Stops queue processing immediately, regardless of its current state.
         */
        public void stop() {
                running = false; // Stop the main loop

                if (schedulerThread != null) {
                        schedulerThread.interrupt(); // Interrupt to force immediate exit

                        try {
                                schedulerThread.join(1000); // Wait up to 1 second for thread to finish
                        } catch (InterruptedException e) {
                                logger.error("Interrupted while stopping TaskQueue for profile " + profile.getName(), e);
                                Thread.currentThread().interrupt();
                        }
                }

                EmulatorManager.getInstance().releaseEmulatorSlot(profile);
                taskQueue.clear();
                ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "NOT RUNNING "));
                logger.info("TaskQueue stopped immediately for profile " + profile.getName());
        }

        /**
         * Pauses queue processing while keeping tasks in the queue.
         */
	public void pause() {
        paused = true;
        ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "PAUSE REQUESTED"));
        logger.info("TaskQueue paused for profile " + profile.getName());
    }

        /**
         * Resumes queue processing.
         */
	public void resume() {
        paused = false;
        ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "RESUMING"));
        logger.info("TaskQueue resumed for profile " + profile.getName());
    }

	public void executeTaskNow(TpDailyTaskEnum taskEnum) {

		// Obtain the task prototype from the registry
		DelayedTask prototype = DelayedTaskRegistry.create(taskEnum, profile);
		if (prototype == null) {
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, "TaskQueue", profile.getName(), "Task not found: " + taskEnum);
			return;
		}

		// verify if the task already exists in the queue
		DelayedTask existing = taskQueue.stream().filter(prototype::equals).findFirst().orElse(null);

		if (existing != null) {
			// task already exists, reschedule it to run now
			taskQueue.remove(existing);
			existing.reschedule(LocalDateTime.now());
			existing.setRecurring(true);
			taskQueue.offer(existing);

			ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue", profile.getName(), "Rescheduled existing " + taskEnum + " to run now");
		} else {
			// task does not exist, create a new instance and schedule it just once
			prototype.reschedule(LocalDateTime.now());
			prototype.setRecurring(false);
			taskQueue.offer(prototype);
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue", profile.getName(), "Enqueued new immediate " + taskEnum);
		}

		DTOTaskState taskState = new DTOTaskState();
		taskState.setProfileId(profile.getId());
		taskState.setTaskId(taskEnum.getId());
		taskState.setScheduled(true);
		taskState.setExecuting(false);
		taskState.setLastExecutionTime(prototype.getScheduled());
		taskState.setNextExecutionTime(LocalDateTime.now());
		ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);
	}

}
