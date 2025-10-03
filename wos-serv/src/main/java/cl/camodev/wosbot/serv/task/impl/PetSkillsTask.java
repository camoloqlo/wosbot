
package cl.camodev.wosbot.serv.task.impl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class PetSkillsTask extends DelayedTask {

	private static final Logger log = LoggerFactory.getLogger(PetSkillsTask.class);
	private final PetSkill petSkill;

	//@formatter:on
	private int attempts = 0;

	public PetSkillsTask(DTOProfiles profile, TpDailyTaskEnum tpTask, PetSkill petSkill) {
		super(profile, tpTask);
		this.petSkill = petSkill;
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

	@Override
	protected void execute() {
		if (attempts >= 3) {
			logWarning("Could not find the Pet Skills menu after multiple attempts. Removing task from scheduler.");
			this.setRecurring(false);
			return;
		}

		logInfo("Starting Pet Skills task for " + petSkill.name() + ".");

		DTOImageSearchResult petsResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_PETS,  90);
		if (petsResult.isFound()) {
			logInfo("Pets button found. Tapping to open.");
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, petsResult.getPoint(), petsResult.getPoint());
			sleepTask(1000);

			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, petSkill.getPoint1(), petSkill.getPoint2());
			sleepTask(300);

			DTOImageSearchResult infoSkill = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_INFO_SKILLS,  90);

			if (!infoSkill.isFound()) {
				logInfo("Skill " + petSkill.name() + " is not learned yet. Task will not recur.");
				this.setRecurring(false);
				tapBackButton();
				return;
			}

			DTOImageSearchResult unlockText = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_UNLOCK_TEXT,  90);

			if (unlockText.isFound()) {
				logInfo("Skill " + petSkill.name() + " is locked. Task will not recur.");
				tapBackButton();
				this.setRecurring(false);
				return;
			}

			DTOImageSearchResult skillButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_SKILL_USE,  90);
			if (skillButton.isFound()) {
				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, skillButton.getPoint(), skillButton.getPoint(), 10, 100);
				sleepTask(500);
			}

			try {
				logInfo("Skill used. Parsing cooldown to determine next schedule for " + petSkill.name() + ".");
				String nextSchedulteText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(210, 1080), new DTOPoint(520, 1105));
				if (nextSchedulteText != null && nextSchedulteText.toLowerCase().contains("active")) {
					LocalDateTime nextSchedule = LocalDateTime.now().plusHours(1);
					logInfo("Skill is active, no cooldown. Rescheduling task for 1 hour: " + nextSchedule);
					this.reschedule(nextSchedule);
					ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, nextSchedule);
				} else if (nextSchedulteText != null && (nextSchedulteText.toLowerCase().contains("on cooldown:") || nextSchedulteText.matches(".*\\d+:\\d+:\\d+.*"))) {
					LocalDateTime nextSchedule = parseCooldown(nextSchedulteText);
					this.reschedule(nextSchedule);
					ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, nextSchedule);
					logInfo("Rescheduled " + petSkill.name() + " task for " + nextSchedule);
				} else {
					logWarning("Unexpected cooldown text: '" + nextSchedulteText + "'. Rescheduling in 5 minutes.");
					this.reschedule(LocalDateTime.now().plusMinutes(5));
				}
			} catch (Exception e) {
				logError("Error parsing cooldown for " + petSkill.name() + ". Rescheduling for 5 minutes.", e);
				this.reschedule(LocalDateTime.now().plusMinutes(5));
			}
			tapBackButton();
		} else {
			logWarning("Pets button not found. Retrying later.");
			attempts++;
		}
	}

	public LocalDateTime parseCooldown(String input) {
		if (input == null || !input.toLowerCase().contains("on cooldown:")) {
			throw new IllegalArgumentException("Invalid format: " + input);
		}

		try {
			String lower = input.toLowerCase();
			String timePart = input.substring(lower.indexOf("on cooldown:") + 12).trim();
			timePart = timePart.replaceAll("\\s+", "").replaceAll("[Oo]", "0").replaceAll("[lI]", "1").replaceAll("[S]", "5").replaceAll("[B]", "8").replaceAll("[Z]", "2").replaceAll("[^0-9d:]", "");

			int days = 0, hours = 0, minutes = 0, seconds = 0;
			if (timePart.contains("d")) {
				String[] daySplit = timePart.split("d", 2);
				days = parseNumber(daySplit[0]); // Extract days
				timePart = daySplit[1]; // Rest of the string without days
			}
			String[] parts = timePart.split(":");
			if (parts.length == 3) { // Standard case hh:mm:ss
				hours = parseNumber(parts[0]);
				minutes = parseNumber(parts[1]);
				seconds = parseNumber(parts[2]);
			} else {
				throw new IllegalArgumentException("Incorrect time format: " + timePart);
			}
			return LocalDateTime.now().plusDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds);
		} catch (Exception e) {
			throw new RuntimeException("Error processing cooldown: " + input, e);
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
