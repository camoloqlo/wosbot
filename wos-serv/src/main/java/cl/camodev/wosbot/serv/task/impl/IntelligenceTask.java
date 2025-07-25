package cl.camodev.wosbot.serv.task.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cl.camodev.wosbot.almac.entity.DailyTask;
import cl.camodev.wosbot.almac.repo.DailyTaskRepository;
import cl.camodev.wosbot.almac.repo.IDailyTaskRepository;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.DelayedTask;
import net.sourceforge.tess4j.TesseractException;

public class IntelligenceTask extends DelayedTask {

	private final IDailyTaskRepository iDailyTaskRepository = DailyTaskRepository.getRepository();
	private boolean marchQueueLimitReached = false;
	private boolean fcEra = false;

        public IntelligenceTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
                super(profile, tpTask);
        }

	@Override
	protected void execute() {
		// Check if GatherTask reschedule time is greater than 10 minutes
		long gatherRemainingMinutes = isGatherTaskReadyForIntelligence();
		if (gatherRemainingMinutes > 10) {
			servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "GatherTask reschedule time is more than 10 minutes, postponing IntelligenceTask");
			reschedule(LocalDateTime.now().plusMinutes(gatherRemainingMinutes));
			ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, LocalDateTime.now().plusMinutes(gatherRemainingMinutes));
			return;
		}

		fcEra = profile.getConfig(EnumConfigurationKey.INTEL_FC_ERA_BOOL,Boolean.class);


		boolean intelFound = false;
		marchQueueLimitReached = false;

		DTOImageSearchResult homeResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE.getTemplate(),  90);
		DTOImageSearchResult worldResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_WORLD.getTemplate(),  90);

		if (homeResult.isFound() || worldResult.isFound()) {
			if (homeResult.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, homeResult.getPoint());
				sleepTask(3000);
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Going to intelligence");
			}

			DTOImageSearchResult intelligence = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_INTEL.getTemplate(),  90);
			if (intelligence.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, intelligence.getPoint());
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for completed missions");
				for (int i = 0; i < 5; i++) {
					servLogs.appendLog(EnumTpMessageSeverity.DEBUG, taskName, profile.getName(), "Searching for completed missions attempt " + i);
					DTOImageSearchResult completed = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_COMPLETED.getTemplate(),  90);
					if (completed.isFound()) {
						emuManager.tapAtPoint(EMULATOR_NUMBER, completed.getPoint());
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(700, 1270), new DTOPoint(710, 1280), 10, 100);
					}
				}
			}

			sleepTask(500);
			intelligence = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_INTEL.getTemplate(),  90);
			if (intelligence.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, intelligence.getPoint());
			}

			if (profile.getConfig(EnumConfigurationKey.INTEL_FIRE_BEAST_BOOL, Boolean.class)) {
				sleepTask(500);
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for fire beasts");
				if (searchAndProcessBeast(EnumTemplates.INTEL_FIRE_BEAST, 5)) {
					intelFound = true;
				}
				if (marchQueueLimitReached)
					return;
			}

			sleepTask(500);
			intelligence = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_INTEL.getTemplate(),  90);
			if (intelligence.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, intelligence.getPoint());
			}

			if (profile.getConfig(EnumConfigurationKey.INTEL_BEASTS_BOOL, Boolean.class)) {
				sleepTask(500);
				// @formatter:off

				List<EnumTemplates> beastPriorities=null;
				if (fcEra){
					beastPriorities = Arrays.asList(
							EnumTemplates.INTEL_BEAST_YELLOW,
							EnumTemplates.INTEL_BEAST_PURPLE,
							EnumTemplates.INTEL_BEAST_BLUE);
					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for beasts in FC era");
				}else{
					beastPriorities = Arrays.asList(
							EnumTemplates.INTEL_PREFC_BEAST_YELLOW,
							EnumTemplates.INTEL_PREFC_BEAST_PURPLE,
							EnumTemplates.INTEL_PREFC_BEAST_BLUE,
							EnumTemplates.INTEL_PREFC_BEAST_GREEN,
							EnumTemplates.INTEL_PREFC_BEAST_GREY);
					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for beasts in pre-FC era");
				}

				// @formatter:on
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for beasts");
				for (EnumTemplates beast : beastPriorities) {
					if (searchAndProcessBeast(beast, 5)) {
						intelFound = true;
						break;
					}
					if (marchQueueLimitReached)
						return;
				}
			}

			sleepTask(500);
			intelligence = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_INTEL.getTemplate(),  90);
			if (intelligence.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, intelligence.getPoint());
			}

			if (profile.getConfig(EnumConfigurationKey.INTEL_CAMP_BOOL, Boolean.class)) {
				sleepTask(500);
				// @formatter:off
				List<EnumTemplates> priorities = null;

				if (fcEra) {
					priorities = Arrays.asList(
							EnumTemplates.INTEL_SURVIVOR_YELLOW,
							EnumTemplates.INTEL_SURVIVOR_PURPLE,
							EnumTemplates.INTEL_SURVIVOR_BLUE);
					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for camps in FC era");
				} else {
					priorities = Arrays.asList(
							EnumTemplates.INTEL_PREFC_SURVIVOR_YELLOW,
							EnumTemplates.INTEL_PREFC_SURVIVOR_PURPLE,
							EnumTemplates.INTEL_PREFC_SURVIVOR_BLUE,
							EnumTemplates.INTEL_PREFC_SURVIVOR_GREEN,
							EnumTemplates.INTEL_PREFC_SURVIVOR_GREY);
					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for camps in pre-FC era");
				}
				// @formatter:on
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for survivors");
				for (EnumTemplates beast : priorities) {
					if (searchAndProcessSurvivor(beast, 5)) {
						intelFound = true;
						break;
					}
					if (marchQueueLimitReached)
						return;
				}

			}

			sleepTask(500);
			intelligence = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_INTEL.getTemplate(),  90);
			if (intelligence.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, intelligence.getPoint());
			}

			if (profile.getConfig(EnumConfigurationKey.INTEL_EXPLORATION_BOOL, Boolean.class)) {
				sleepTask(500);
				// @formatter:off
				List<EnumTemplates> priorities = null;
				if (fcEra) {
					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for explorations in FC era");
					priorities = Arrays.asList(
							EnumTemplates.INTEL_JOURNEY_YELLOW,
							EnumTemplates.INTEL_JOURNEY_PURPLE,
							EnumTemplates.INTEL_JOURNEY_BLUE);

				} else {
					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for explorations in pre-FC era");
				priorities = Arrays.asList(
						EnumTemplates.INTEL_PREFC_JOURNEY_YELLOW,
						EnumTemplates.INTEL_PREFC_JOURNEY_PURPLE,
						EnumTemplates.INTEL_PREFC_JOURNEY_BLUE,
						EnumTemplates.INTEL_PREFC_JOURNEY_GREEN,
						EnumTemplates.INTEL_PREFC_JOURNEY_GREY);
				}
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for explorations");
				for (EnumTemplates beast : priorities) {
					if (searchAndProcessExploration(beast, 5)) {
						intelFound = true;
						break;
					}
					if (marchQueueLimitReached)
						return;
				}

			}

			sleepTask(500);
			if (intelFound == false) {
				try {
					String rescheduleTime = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(120, 110), new DTOPoint(600, 146));
					LocalDateTime reshchedule = parseAndAddTime(rescheduleTime);
					this.reschedule(reshchedule);
					emuManager.tapBackButton(EMULATOR_NUMBER);
					ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, reshchedule);
					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "No intelligence tasks found, rescheduling to: " + reshchedule);
                                } catch (IOException | TesseractException e) {
                                        this.reschedule(LocalDateTime.now().plusMinutes(5));
                                        servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Error occurred while processing: " + e.getMessage());
                                }
			} else {
				this.reschedule(LocalDateTime.now());
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Intelligence tasks completed, rescheduling now to check for new tasks.");
			}

		} else {
			emuManager.tapBackButton(EMULATOR_NUMBER);
			reschedule(LocalDateTime.now());
		}

	}

	private boolean searchAndProcessExploration(EnumTemplates exploration, int maxAttempts) {
		for (int attempt = 0; attempt < maxAttempts; attempt++) {
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.DEBUG, taskName, profile.getName(), "Searching for " + exploration + " attempt " + attempt);
			DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER, exploration.getTemplate(),  95);

			if (result.isFound()) {
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Found :" + exploration);
				processJourney(result);
				return true; // Salir del bucle, bestia encontrada
			}
		}
		return false; // No se encontró la bestia después de maxAttempts intentos
	}

	private void processJourney(DTOImageSearchResult result) {
		emuManager.tapAtPoint(EMULATOR_NUMBER, result.getPoint());
		sleepTask(2000);

		DTOImageSearchResult view = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_VIEW.getTemplate(),  90);
		if (view.isFound()) {
			emuManager.tapAtPoint(EMULATOR_NUMBER, view.getPoint());
			sleepTask(500);
			DTOImageSearchResult explore = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_EXPLORE.getTemplate(),  90);
			if (explore.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, explore.getPoint());
				sleepTask(500);
				emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(520, 1200));
				sleepTask(1000);
				emuManager.tapBackButton(EMULATOR_NUMBER);
			} else {
				// March queue limit reached, cannot process exploration
				servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Error: March queue limit reached, cannot process exploration. Rescheduling task for 1 hour.");
				LocalDateTime rescheduleTime = LocalDateTime.now().plusHours(1);
				this.reschedule(rescheduleTime);
				ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, rescheduleTime);
				marchQueueLimitReached = true;
				return;
			}
		}
	}

	private boolean searchAndProcessSurvivor(EnumTemplates survivor, int maxAttempts) {
		for (int attempt = 0; attempt < maxAttempts; attempt++) {
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.DEBUG, taskName, profile.getName(), "Searching for " + survivor + " attempt " + attempt);
			DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER, survivor.getTemplate(),  95);

			if (result.isFound()) {
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Found :" + survivor);
				processSurvivor(result);
				return true; // Salir del bucle, bestia encontrada
			}
		}
		return false; // No se encontró la bestia después de maxAttempts intentos
	}

	private void processSurvivor(DTOImageSearchResult result) {
		emuManager.tapAtPoint(EMULATOR_NUMBER, result.getPoint());
		sleepTask(2000);

		DTOImageSearchResult view = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_VIEW.getTemplate(),  90);
		if (view.isFound()) {
			emuManager.tapAtPoint(EMULATOR_NUMBER, view.getPoint());
			sleepTask(500);
			DTOImageSearchResult rescue = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_RESCUE.getTemplate(),  90);
			if (rescue.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, rescue.getPoint());
			} else {
				// March queue limit reached, cannot process survivor
				servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Error: March queue limit reached, cannot process survivor. Rescheduling task for 1 hour.");
				LocalDateTime rescheduleTime = LocalDateTime.now().plusHours(1);
				this.reschedule(rescheduleTime);
				ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, rescheduleTime);
				marchQueueLimitReached = true;
				return;
			}
		}
	}

	private boolean searchAndProcessBeast(EnumTemplates beast, int maxAttempts) {
		for (int attempt = 0; attempt < maxAttempts; attempt++) {
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.DEBUG, taskName, profile.getName(), "Searching for " + beast + " attempt " + attempt);
			DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER, beast.getTemplate(),  80);

			if (result.isFound()) {
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Found :" + beast);
				processBeast(result);
				return true; // Salir del bucle, bestia encontrada
			}
		}
		return false; // No se encontró la bestia después de maxAttempts intentos
	}

	private void processBeast(DTOImageSearchResult beast) {
		emuManager.tapAtPoint(EMULATOR_NUMBER, beast.getPoint());
		sleepTask(2000);

		DTOImageSearchResult view = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_VIEW.getTemplate(),  90);
		if (view.isFound()) {
			emuManager.tapAtPoint(EMULATOR_NUMBER, view.getPoint());
			sleepTask(500);
			DTOImageSearchResult attack = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_ATTACK.getTemplate(),  90);
			if (attack.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, attack.getPoint());
				sleepTask(500);

				DTOImageSearchResult equalizeButton = emuManager.searchTemplate(EMULATOR_NUMBER,
						EnumTemplates.RALLY_EQUALIZE_BUTTON.getTemplate(),  90);

				if (equalizeButton.isFound()){
					emuManager.tapAtPoint(EMULATOR_NUMBER, equalizeButton.getPoint());
				}else{
					//hard coded coords
					emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(198, 1188));
					sleepTask(500);
				}

				DTOImageSearchResult rally = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_ATTACK_CONFIRM.getTemplate(),  90);
				if (rally.isFound()) {
					emuManager.tapAtPoint(EMULATOR_NUMBER, rally.getPoint());
				} else {
					// March queue limit reached, cannot process beast
					servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Error: March queue limit reached, cannot process beast. Rescheduling task for 1 hour.");
					LocalDateTime rescheduleTime = LocalDateTime.now().plusHours(1);
					this.reschedule(rescheduleTime);
					ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, rescheduleTime);
					marchQueueLimitReached = true;
					return;
				}
			}
		}
	}

	public LocalDateTime parseAndAddTime(String ocrText) {
		// Expresión regular para capturar el tiempo en formato HH:mm:ss
		Pattern pattern = Pattern.compile("(\\d{1,2}):(\\d{1,2}):(\\d{1,2})");
		Matcher matcher = pattern.matcher(ocrText);

		if (matcher.find()) {
			try {
				int hours = Integer.parseInt(matcher.group(1));
				int minutes = Integer.parseInt(matcher.group(2));
				int seconds = Integer.parseInt(matcher.group(3));

				return LocalDateTime.now().plus(hours, ChronoUnit.HOURS).plus(minutes, ChronoUnit.MINUTES).plus(seconds, ChronoUnit.SECONDS);
                        } catch (NumberFormatException e) {
                                servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Invalid time format: " + e.getMessage());
                        }
		}

		return LocalDateTime.now();
	}

	private long isGatherTaskReadyForIntelligence() {
		try {
			DailyTask gatherTask = iDailyTaskRepository.findByProfileIdAndTaskName(profile.getId(), TpDailyTaskEnum.GATHER_MEAT);

			if (gatherTask == null) {
				// GatherTask has never been executed, so intelligence can proceed
				servLogs.appendLog(EnumTpMessageSeverity.DEBUG, taskName, profile.getName(), "GatherTask has never been executed, IntelligenceTask can proceed");
				return (long) 0;
			}

			LocalDateTime nextSchedule = gatherTask.getNextSchedule();
			if (nextSchedule == null) {
				// If there's no next schedule, allow intelligence to proceed
				return (long) 0;
			}

			// Check if the next schedule is more than 10 minutes from now
			long minutesUntilNextSchedule = ChronoUnit.MINUTES.between(LocalDateTime.now(), nextSchedule);

			if (minutesUntilNextSchedule < 10) {
				servLogs.appendLog(EnumTpMessageSeverity.DEBUG, taskName, profile.getName(), "GatherTask next schedule is in " + minutesUntilNextSchedule + " minutes, IntelligenceTask can proceed");
				return (long) 0;
			} else {
				servLogs.appendLog(EnumTpMessageSeverity.DEBUG, taskName, profile.getName(), "GatherTask next schedule is in " + minutesUntilNextSchedule + " minutes, postponing IntelligenceTask");
				return minutesUntilNextSchedule;
			}

		} catch (Exception e) {
			servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Error checking GatherTask status: " + e.getMessage());
			// In case of error, allow intelligence to proceed
			return (long) 0;
		}
	}

	@Override
	public boolean provideDailyMissionProgress() {return true;}

}
