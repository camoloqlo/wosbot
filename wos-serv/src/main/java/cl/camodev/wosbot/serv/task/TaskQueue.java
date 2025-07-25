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
import cl.camodev.wosbot.ex.ADBConnectionException;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.ex.ProfileInReconnectStateException;
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
	// Bandera para detener el loop del scheduler.
	private volatile boolean running = false;
	// Bandera para pausar/reanudar el scheduler.
	private volatile boolean paused = false;
	// Hilo que se encargará de evaluar y ejecutar las tareas.
	private Thread schedulerThread;
	private DTOProfiles profile;

	public TaskQueue(DTOProfiles profile) {
		this.profile = profile;
	}

	/**
	 * Agrega una tarea a la cola.
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
	 * Inicia el procesamiento de la cola.
	 */
	public void start() {

		if (running)
			return;
		running = true;

		schedulerThread = new Thread(() -> {

			boolean idlingTimeExceded = false;
			ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "Getting queue slot"));
			try {
				EmulatorManager.getInstance().adquireEmulatorSlot(profile, (thread, position) -> {
					ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "Waiting for slot, position: " + position));
				});
			} catch (InterruptedException e) {
				logger.error("Interrupted while acquiring emulator slot for profile " + profile.getName(), e);
			}
			while (running) {
				// Check if paused and skip execution if so
				if (paused) {
					try {
						ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "PAUSED"));
						logger.info("Profile {} is paused.", profile.getName());
						Thread.sleep(1000); // Wait 1 second while paused
						continue;
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}

				boolean executedTask = false;
				long minDelay = Long.MAX_VALUE;

				// realizar preverificacion de que el jeugo esta corriendo

				// Procesar tareas que están listas para ejecutar
				DelayedTask task;
				while ((task = taskQueue.peek()) != null) {
					DTOTaskState taskState = null;
					long delayInSeconds = task.getDelay(TimeUnit.SECONDS);

					// Si la primera tarea no está lista, ninguna lo estará (cola ordenada)
					if (delayInSeconds > 0) {
						minDelay = delayInSeconds;
						break;
					}


				// Remover la tarea de la cola
				taskQueue.poll();
				LocalDateTime scheduledBefore = task.getScheduled();
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
				} catch (StopExecutionException e) {
					logger.error("Execution stopped for task " + task.getTaskName() + " for profile " + profile.getName() + ": " + e.getMessage(), e);
					stop();
				} catch (ProfileInReconnectStateException e) {
					Long reconnectionTime = profile.getReconnectionTime();
					if (reconnectionTime != null && reconnectionTime > 0) {
						ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, task.getTaskName(), profile.getName(), "Profile in reconnect state before executing task, pausing queue for " + reconnectionTime + " minutes");
						logger.info("Profile {} is in reconnect state, pausing TaskQueue for {} minutes", profile.getName(), reconnectionTime);
						paused = true;
						new Thread(() -> {
							try {
								Thread.sleep(TimeUnit.MINUTES.toMillis(reconnectionTime));
							} catch (InterruptedException ignored) {
							}
							if (paused) {
								paused = false;
								ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "RESUMING AFTER PAUSE"));
								logger.info("TaskQueue resumed for profile {} after {} minutes pause", profile.getName(), reconnectionTime);
								addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
							}
						}).start();
					} else {
						ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, task.getTaskName(), profile.getName(), "Profile in reconnect state, but no reconnection time set");
						logger.error("Profile {} is in reconnect state, but no reconnection time set, resuming execution", profile.getName());
						addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
					}
				} catch (ADBConnectionException e) {
					logger.error("ADB connection error executing task {} for profile {}: {}", task.getTaskName(), profile.getName(), e.getMessage(), e);
					addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
				} catch (Exception e) {
					ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, task.getTaskName(), profile.getName(), e.getMessage());
					logger.error("Error executing task " + task.getTaskName() + " for profile " + profile.getName() + ": " + e.getMessage(), e);
				}
				LocalDateTime scheduledAfter = task.getScheduled();
				if (scheduledBefore.equals(scheduledAfter)) {
					logger.info("Task {} for profile {} executed without rescheduling, changing scheduled time to now to avoid infinite loop", task.getTaskName(), profile.getName());
					task.reschedule(LocalDateTime.now());
				}

				if (task.isRecurring()) {
					ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, task.getTaskName(), profile.getName(), "Next schedule: " + UtilTime.localDateTimeToDDHHMMSS(task.getScheduled()));
					addTask(task);
				} else {
					ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, task.getTaskName(), profile.getName(), "Task removed from schedule");
				}

				boolean dailyAutoSchedule = profile.getConfig(EnumConfigurationKey.DAILY_MISSION_AUTO_SCHEDULE_BOOL, Boolean.class);
				if (dailyAutoSchedule) {
					DTOTaskState state = ServTaskManager.getInstance().getTaskState(profile.getId(), TpDailyTaskEnum.DAILY_MISSIONS.getId());
					LocalDateTime next = (state != null) ? state.getNextExecutionTime() : null;
					LocalDateTime now = LocalDateTime.now();
					if (task.provideDailyMissionProgress() && (state == null || next == null || next.isAfter(now))) {
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


				if (task.provideTriumphProgress()) {

				}

				assert taskState != null;
				taskState.setExecuting(false);
				taskState.setScheduled(task.isRecurring());
				taskState.setLastExecutionTime(LocalDateTime.now());
				taskState.setNextExecutionTime(task.getScheduled());

				ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);
				ServScheduler.getServices().updateDailyTaskStatus(profile, task.getTpTask(), task.getScheduled());

				executedTask = true;
			}

				// Si no se ejecutó ninguna tarea, obtener el delay de la próxima tarea
				if (!executedTask && !taskQueue.isEmpty()) {
					DelayedTask nextTask = taskQueue.peek();
					if (nextTask != null) {
						minDelay = nextTask.getDelay(TimeUnit.SECONDS);
					}
				}

				// Verificar condiciones según el delay mínimo de la cola de tareas
				if (minDelay != Long.MAX_VALUE) { // Asegurar que hay tareas en la cola
					long maxIdle = 0;
					maxIdle = Optional.ofNullable(profile.getGlobalsettings().get(EnumConfigurationKey.MAX_IDLE_TIME_INT.name())).map(Integer::parseInt).orElse(Integer.parseInt(EnumConfigurationKey.MAX_IDLE_TIME_INT.getDefaultValue()));

					if (!idlingTimeExceded && minDelay > TimeUnit.MINUTES.toSeconds(maxIdle)) {
						idlingTimeExceded = true;
						idlingEmulator(minDelay);
					}

					// Si la demora baja a menos de 1 minuto y intentamos obtener el slot de emulador y encolamos tarea de inicialización
					if (idlingTimeExceded && minDelay < TimeUnit.MINUTES.toSeconds(1)) {
						encolarNuevaTarea();
						idlingTimeExceded = false; // Restablecer la condición para futuras evaluaciones
					}
				}

				// Si no se ejecutó ninguna tarea, esperar un poco antes de volver a evaluar
				if (!executedTask) {
					try {
						String formattedTime;
						if (minDelay == Long.MAX_VALUE || minDelay > 86399) {
							// Si no hay tareas o el delay es muy largo, mostrar un mensaje apropiado
							formattedTime = "No tasks";
						} else {
							DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
							// Convertir minDelay (segundos) a formato HH:mm:ss
							formattedTime = LocalTime.ofSecondOfDay(minDelay).format(timeFormatter);
						}

						ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "Idling for " + formattedTime + "\nNext task: " + (taskQueue.isEmpty() ? "None" : taskQueue.peek().getTaskName())));
						Thread.sleep(999);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		});
		schedulerThread.start();
	}

	// Métodos auxiliares
	private void idlingEmulator(long minDelay) {
		EmulatorManager.getInstance().closeEmulator(profile.getEmulatorNumber());
		ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue", profile.getName(), "Closing game due to large inactivity");
		LocalDateTime scheduledTime = LocalDateTime.now().plusSeconds(minDelay);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "Idling till " + formatter.format(scheduledTime)));
		EmulatorManager.getInstance().releaseEmulatorSlot(profile);
	}

	private void encolarNuevaTarea() {
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue", profile.getName(), "shcheduled task's will start soon");

        try {
            EmulatorManager.getInstance().adquireEmulatorSlot(profile, (thread, position) -> {
                ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "Waiting for slot, position: " + position));
            });
        } catch (InterruptedException e) {
            logger.error("Interrupted while acquiring emulator slot for profile " + profile.getName(), e);
        }
        addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
    }

	/**
	 * Detiene inmediatamente el procesamiento de la cola, sin importar en qué estado esté.
	 */
	public void stop() {
		running = false; // Detener el bucle principal

		if (schedulerThread != null) {
			schedulerThread.interrupt(); // Interrumpir el hilo para forzar la salida inmediata

			try {
				schedulerThread.join(1000); // Esperar hasta 1 segundo para que el hilo termine
			} catch (InterruptedException e) {
				logger.error("Interrupted while stopping TaskQueue for profile " + profile.getName(), e);
				Thread.currentThread().interrupt();
			}
		}

		// Eliminar todas las tareas pendientes en la cola
		taskQueue.clear();
		ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "NOT RUNNING "));
		logger.info("TaskQueue stopped immediately for profile " + profile.getName());
	}

	/**
	 * Pausa el procesamiento de la cola, manteniendo las tareas en la cola.
	 */
	public void pause() {
        paused = true;
        ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), "PAUSE REQUESTED"));
        logger.info("TaskQueue paused for profile " + profile.getName());
    }

	/**
	 * Reanuda el procesamiento de la cola.
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

	public DTOProfiles getProfile() {
		return profile;
	}
}
