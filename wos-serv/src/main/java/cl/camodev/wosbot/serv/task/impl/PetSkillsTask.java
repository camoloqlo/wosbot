package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

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

public class PetSkillsTask extends DelayedTask {

	private final EmulatorManager emuManager = EmulatorManager.getInstance();
	private final ServLogs servLogs = ServLogs.getServices();
	private final PetSkill petSkill;

	//@formatter:on
	private int attempts = 0;

	public PetSkillsTask(DTOProfiles profile, TpDailyTaskEnum tpTask, PetSkill petSkill) {
		super(profile, tpTask);
		this.petSkill = petSkill;
	}

	@Override
	protected void execute() {
		if (attempts >= 3) {
			servLogs.appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), "Menu not found, removing task from scheduler");
			this.setRecurring(false);
			return;
		}

		DTOImageSearchResult homeResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE.getTemplate(),  90);
		DTOImageSearchResult worldResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_WORLD.getTemplate(),  90);
		if (homeResult.isFound() || worldResult.isFound()) {
			servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "going pet skills");

			DTOImageSearchResult petsResult = EmulatorManager.getInstance().searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_PETS.getTemplate(),  90);
			if (petsResult.isFound()) {
				ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "button pets found, taping");
				EmulatorManager.getInstance().tapAtRandomPoint(EMULATOR_NUMBER, petsResult.getPoint(), petsResult.getPoint());
				sleepTask(1000);

				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, petSkill.getPoint1(), petSkill.getPoint2());
				sleepTask(300);

				DTOImageSearchResult infoSkill = EmulatorManager.getInstance().searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_INFO_SKILLS.getTemplate(),  90);

				if (!infoSkill.isFound()) {
					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "skill not learned");
					this.setRecurring(false);
					EmulatorManager.getInstance().tapBackButton(EMULATOR_NUMBER);
					return;
				}

				DTOImageSearchResult unlockText = EmulatorManager.getInstance().searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_UNLOCK_TEXT.getTemplate(),  90);

				if (unlockText.isFound()) {
					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "skill is locked");
					EmulatorManager.getInstance().tapBackButton(EMULATOR_NUMBER);
					this.setRecurring(false);
					return;
				}

				DTOImageSearchResult skillButton = EmulatorManager.getInstance().searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_SKILL_USE.getTemplate(),  90);
				if (skillButton.isFound()) {
					EmulatorManager.getInstance().tapAtRandomPoint(EMULATOR_NUMBER, skillButton.getPoint(), skillButton.getPoint(), 10, 100);
					sleepTask(500);
				}

				try {
					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "getting next schedule for " + petSkill.name());
					String nextSchedulteText = EmulatorManager.getInstance().ocrRegionText(EMULATOR_NUMBER, new DTOPoint(210, 1080), new DTOPoint(520, 1105));
					LocalDateTime nextSchedule = parseCooldown(nextSchedulteText);
					this.reschedule(parseCooldown(nextSchedulteText));
					ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, nextSchedule);
                                } catch (Exception e) {
                                        ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Error processing pet skill cooldown: " + e.getMessage());
                                        this.reschedule(LocalDateTime.now().plusMinutes(5));
                                }
				EmulatorManager.getInstance().tapBackButton(EMULATOR_NUMBER);
			} else {
				ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), "button pets not found retrying later");
				attempts++;
			}

		} else {
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), "Home not found");
			EmulatorManager.getInstance().tapBackButton(EMULATOR_NUMBER);

		}
	}

	public LocalDateTime parseCooldown(String input) {
		if (input == null || !input.toLowerCase().contains("on cooldown:")) {
			throw new IllegalArgumentException("Formato inválido: " + input);
		}

		try {

			String timePart = input.substring(input.toLowerCase().indexOf("on cooldown:") + 12).trim();

			timePart = timePart.replaceAll("\\s+", "").replaceAll("[Oo]", "0").replaceAll("[lI]", "1").replaceAll("[S]", "5").replaceAll("[B]", "8").replaceAll("[Z]", "2").replaceAll("[^0-9d:]", "");

			int days = 0, hours = 0, minutes = 0, seconds = 0;

			if (timePart.contains("d")) {
				String[] daySplit = timePart.split("d", 2);
				days = parseNumber(daySplit[0]); // Extrae los días
				timePart = daySplit[1]; // Resto del string sin los días
			}

			String[] parts = timePart.split(":");
			if (parts.length == 3) { // Caso estándar hh:mm:ss
				hours = parseNumber(parts[0]);
				minutes = parseNumber(parts[1]);
				seconds = parseNumber(parts[2]);
			} else {
				throw new IllegalArgumentException("Formato de tiempo incorrecto: " + timePart);
			}

			return LocalDateTime.now().plusDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds);
		} catch (Exception e) {
			throw new RuntimeException("Error al procesar el cooldown: " + input, e);
		}
	}

	private int parseNumber(String number) {
		try {
			return Integer.parseInt(number.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	//@formatter:off
	public enum PetSkill {
		STAMINA(		new DTOPoint(240, 260), new DTOPoint(320, 350)),
		GATHERING(		new DTOPoint(380, 260), new DTOPoint(460, 350)),
		FOOD(			new DTOPoint(540, 260), new DTOPoint(620, 350)),
		TREASURE(		new DTOPoint(240, 410), new DTOPoint(320, 490));

		private final DTOPoint point1;

		private final DTOPoint point2;

		PetSkill(DTOPoint dtoPoint, DTOPoint dtoPoint2) {
			this.point1 = dtoPoint;
			this.point2 = dtoPoint2;
		}

		public DTOPoint getPoint1() {
            return point1;
        }

		public DTOPoint getPoint2() {
            return point2;
        }

	}

}
