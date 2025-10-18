package cl.camodev.wosbot.serv.task.impl;

import java.awt.*;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class PetSkillsTask extends DelayedTask {

	private final PetSkill petSkill;

	//@formatter:on
	private int attempts = 0;

	public PetSkillsTask(DTOProfiles profile, TpDailyTaskEnum tpTask, PetSkill petSkill) {
		super(profile, tpTask);
		this.petSkill = petSkill;
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.ANY;
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
                if (petSkill == PetSkill.STAMINA) {
                    Integer level = integerHelper.execute(
                            new DTOPoint(276, 779),
                            new DTOPoint(363, 811),
                            5,
                            200L,
                            DTOTesseractSettings.builder()
                                    .setAllowedChars("0123456789")
                                    .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                                    .setRemoveBackground(true)
                                    .setTextColor(new Color(69, 88, 110))
                                    .build(),
                            text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                            text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));
                    if (level != null) {
                        int staminaToAdd = 35 + (level - 1) * 5;
                        StaminaService.getServices().addStamina(profile.getId(), staminaToAdd);
                        logInfo("Skill " + petSkill.name() + " level is " + level + ". Added " + staminaToAdd + " stamina to the profile.");
                    }

                }
			}

			try {
				logInfo("Skill used. Parsing cooldown to determine next schedule for " + petSkill.name() + ".");
				String nextScheduleText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(210, 1080), new DTOPoint(520, 1105));
				LocalDateTime nextSchedule = parseCooldown(nextScheduleText);
				this.reschedule(parseCooldown(nextScheduleText));
                logInfo("Rescheduled " + petSkill.name() + " task for " + nextSchedule);
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

			String timePart = input.substring(input.toLowerCase().indexOf("on cooldown:") + 12).trim();

			timePart = timePart.replaceAll("\\s+", "").replaceAll("[Oo]", "0").replaceAll("[lI]", "1").replaceAll("S", "5").replaceAll("B", "8").replaceAll("Z", "2").replaceAll("[^0-9d:]", "");

			int days = 0, hours, minutes, seconds;

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
