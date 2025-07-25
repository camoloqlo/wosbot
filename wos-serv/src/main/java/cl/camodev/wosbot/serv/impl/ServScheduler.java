package cl.camodev.wosbot.serv.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import cl.camodev.wosbot.almac.entity.Config;
import cl.camodev.wosbot.almac.entity.DailyTask;
import cl.camodev.wosbot.almac.entity.Profile;
import cl.camodev.wosbot.almac.entity.TpConfig;
import cl.camodev.wosbot.almac.entity.TpDailyTask;
import cl.camodev.wosbot.almac.repo.ConfigRepository;
import cl.camodev.wosbot.almac.repo.DailyTaskRepository;
import cl.camodev.wosbot.almac.repo.IConfigRepository;
import cl.camodev.wosbot.almac.repo.IDailyTaskRepository;
import cl.camodev.wosbot.almac.repo.IProfileRepository;
import cl.camodev.wosbot.almac.repo.ProfileRepository;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpConfigEnum;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOBotState;
import cl.camodev.wosbot.ot.DTODailyTaskStatus;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.serv.IBotStateListener;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.DelayedTaskRegistry;
import cl.camodev.wosbot.serv.task.TaskQueue;
import cl.camodev.wosbot.serv.task.TaskQueueManager;

public class ServScheduler {
	private static ServScheduler instance;

	private final TaskQueueManager queueManager = new TaskQueueManager();

	private List<IBotStateListener> listeners = new ArrayList<IBotStateListener>();

	private IDailyTaskRepository iDailyTaskRepository = DailyTaskRepository.getRepository();

	private IProfileRepository iProfileRepository = ProfileRepository.getRepository();

	private IConfigRepository iConfigRepository = ConfigRepository.getRepository();

	private ServScheduler() {

	}

	public static ServScheduler getServices() {
		if (instance == null) {
			instance = new ServScheduler();
		}
		return instance;
	}

	public void startBot() {
		EmulatorManager emulator = EmulatorManager.getInstance();

		try {
			emulator.initialize();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		HashMap<String, String> globalsettings = ServConfig.getServices().getGlobalConfig();
		globalsettings.forEach((key, value) -> {
			if (key.equals(EnumConfigurationKey.MUMU_PATH_STRING.name())) {
				saveEmulatorPath(EnumConfigurationKey.MUMU_PATH_STRING.name(), value);
			} else if (key.equals(EnumConfigurationKey.LDPLAYER_PATH_STRING.name())) {
				saveEmulatorPath(EnumConfigurationKey.LDPLAYER_PATH_STRING.name(), value);
			}
		});
		List<DTOProfiles> profiles = ServProfiles.getServices().getProfiles();

		if (profiles == null || profiles.isEmpty()) {
			return;
		}

		if (profiles.stream().filter(DTOProfiles::getEnabled).findAny().isEmpty()) {
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, "ServScheduler", "-", "No Enabled profiles");
			return;
		} else {
			TaskQueueManager queueManager = ServScheduler.getServices().getQueueManager();
			DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

			profiles.stream().filter(DTOProfiles::getEnabled).sorted(Comparator.comparing(DTOProfiles::getPriority).reversed()).forEach(profile -> {
				profile.setGlobalSettings(globalsettings);
				ServLogs.getServices().appendLog(EnumTpMessageSeverity.DEBUG, "ServScheduler", "-", "starting queue");

				queueManager.createQueue(profile);
				TaskQueue queue = queueManager.getQueue(profile.getId());

				queue.addTask(DelayedTaskRegistry.create(TpDailyTaskEnum.INITIALIZE, profile));

				// load task using registry
				EnumMap<EnumConfigurationKey, List<Supplier<DelayedTask>>> taskMappings = Arrays.stream(TpDailyTaskEnum.values()).filter(t -> t.getConfigKey() != null)
						.collect(Collectors.groupingBy(TpDailyTaskEnum::getConfigKey, () -> new EnumMap<>(EnumConfigurationKey.class), Collectors.mapping(t -> (Supplier<DelayedTask>) () -> DelayedTaskRegistry.create(t, profile), Collectors.toList())));

				// obtain current task schedules
				Map<Integer, DTODailyTaskStatus> taskSchedules = iDailyTaskRepository.findDailyTasksStatusByProfile(profile.getId());

				// Enqueue tasks based on profile configuration
				taskMappings.forEach((configKey, suppliers) -> {
					if (profile.getConfig(configKey, Boolean.class)) {
						for (Supplier<DelayedTask> sup : suppliers) {
							DelayedTask task = sup.get();

							// Construir estado y encolar
							DTOTaskState taskState = new DTOTaskState();
							taskState.setProfileId(profile.getId());
							taskState.setTaskId(task.getTpTask().getId());
							taskState.setExecuting(false);
							taskState.setScheduled(true);

							DTODailyTaskStatus status = taskSchedules.get(task.getTpDailyTaskId());
							if (status != null) {
								LocalDateTime next = status.getNextSchedule();
								task.reschedule(next);
								taskState.setLastExecutionTime(status.getLastExecution());
								taskState.setNextExecutionTime(next);
								ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, task.getTaskName(), profile.getName(), "Next Execution: " + next.format(fmt));
							} else {
								task.reschedule(LocalDateTime.now());
								taskState.setLastExecutionTime(null);
								taskState.setNextExecutionTime(task.getScheduled());
								ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, task.getTaskName(), profile.getName(), "Task not completed, scheduling for today");
							}

							ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);
							queue.addTask(task);
						}
					}
				});
			});

			queueManager.startQueues();

			listeners.forEach(e -> {
				DTOBotState state = new DTOBotState();
				state.setRunning(true);
				state.setPaused(false);
				state.setActionTime(LocalDateTime.now());
				e.onBotStateChange(state);
			});

		}

	}

	public void registryBotStateListener(IBotStateListener listener) {

		if (listeners == null) {
			listeners = new ArrayList<IBotStateListener>();
		}
		listeners.add(listener);
	}

	public void stopBot() {
		queueManager.stopQueues();

		listeners.forEach(e -> {
			DTOBotState state = new DTOBotState();
			state.setRunning(false);
			state.setPaused(false);
			state.setActionTime(LocalDateTime.now());
			e.onBotStateChange(state);
		});
	}

	public void pauseBot() {
		queueManager.pauseQueues();

		listeners.forEach(e -> {
			DTOBotState state = new DTOBotState();
			state.setRunning(true);
			state.setPaused(true);
			state.setActionTime(LocalDateTime.now());
			e.onBotStateChange(state);
		});
	}

	public void resumeBot() {
		queueManager.resumeQueues();

		listeners.forEach(e -> {
			DTOBotState state = new DTOBotState();
			state.setRunning(true);
			state.setPaused(false);
			state.setActionTime(LocalDateTime.now());
			e.onBotStateChange(state);
		});
	}

	public void updateDailyTaskStatus(DTOProfiles profile, TpDailyTaskEnum task, LocalDateTime nextSchedule) {

		DailyTask dailyTask = iDailyTaskRepository.findByProfileIdAndTaskName(profile.getId(), task);

		if (dailyTask == null) {
			// Crear nueva tarea si no existe
			dailyTask = new DailyTask();

			Profile profileEntity = iProfileRepository.getProfileById(profile.getId());
			TpDailyTask tpDailyTaskEntity = iDailyTaskRepository.findTpDailyTaskById(task.getId());
			dailyTask.setProfile(profileEntity);
			dailyTask.setTask(tpDailyTaskEntity);
			dailyTask.setLastExecution(LocalDateTime.now());
			dailyTask.setNextSchedule(nextSchedule);
			iDailyTaskRepository.addDailyTask(dailyTask);
		}

		dailyTask.setLastExecution(LocalDateTime.now());
		dailyTask.setNextSchedule(nextSchedule);

		// Guardar la entidad (ya sea nueva o existente)
		iDailyTaskRepository.saveDailyTask(dailyTask);
	}

	/**
	 * Removes a task from the scheduler for a specific profile
	 * @param profileId The profile ID
	 * @param taskEnum The task to remove
	 */
	public void removeTaskFromScheduler(Long profileId, TpDailyTaskEnum taskEnum) {
		try {
			// Get the task queue for the profile
			TaskQueue queue = queueManager.getQueue(profileId);
			if (queue != null) {
				// Remove the task from the queue
				boolean removedFromQueue = queue.removeTask(taskEnum);
				ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "Scheduler",
					"Profile " + profileId, "Removing task " + taskEnum.getName() + " from scheduler. Removed from queue: " + removedFromQueue);
			} else {
				ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, "Scheduler",
					"Profile " + profileId, "No queue found for profile when removing task " + taskEnum.getName());
			}

			// Update task state in ServTaskManager to reflect removal
			DTOTaskState taskState = new DTOTaskState();
			taskState.setProfileId(profileId);
			taskState.setTaskId(taskEnum.getId());
			taskState.setScheduled(false);
			taskState.setExecuting(false);
			taskState.setLastExecutionTime(LocalDateTime.now());
			taskState.setNextExecutionTime(null);
			ServTaskManager.getInstance().setTaskState(profileId, taskState);

			// Notify listeners about the change
			listeners.forEach(listener -> {
				DTOBotState state = new DTOBotState();
				state.setRunning(true);
				state.setPaused(false);
				state.setActionTime(LocalDateTime.now());
				listener.onBotStateChange(state);
			});

		} catch (Exception e) {
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "Scheduler",
				"Profile " + profileId, "Error removing task " + taskEnum.getName() + ": " + e.getMessage());
		}
	}

	public void saveEmulatorPath(String enumConfigurationKey, String filePath) {
		List<Config> configs = iConfigRepository.getGlobalConfigs();

		Config config = configs.stream().filter(c -> c.getKey().equals(enumConfigurationKey)).findFirst().orElse(null);

		if (config == null) {
			TpConfig tpConfig = iConfigRepository.getTpConfig(TpConfigEnum.GLOBAL_CONFIG);
			config = new Config();
			config.setKey(enumConfigurationKey);
			config.setValor(filePath);
			config.setTpConfig(tpConfig);
			iConfigRepository.addConfig(config);
		} else {
			config.setValor(filePath);
			iConfigRepository.saveConfig(config);
		}
	}

	public TaskQueueManager getQueueManager() {
		return queueManager;
	}

}
