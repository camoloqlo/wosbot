package cl.camodev.wosbot.serv.task.impl;

import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorehouseChest extends DelayedTask {

    private static final Logger log = LoggerFactory.getLogger(StorehouseChest.class);

    public StorehouseChest(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

	public static LocalDateTime parseNextReward(String ocrTime) {
		LocalDateTime now = LocalDateTime.now();

		if (ocrTime == null || ocrTime.isEmpty()) {
			return now;
		}

		// Preprocessing: Remove spaces, correct common OCR errors, reduce multiple colons
		String correctedTime = ocrTime
			.replaceAll("[Oo]", "0")
			.replaceAll("[lI]", "1")
			.replaceAll("S", "5")
			.replaceAll("[ \t]", "")
			.replaceAll("[:]{2,}", ":")
			.replaceAll("[^0-9:]", "");

		// Try to match HH:mm:ss or mm:ss
		try {
			if (correctedTime.matches("\\d{2}:\\d{2}:\\d{2}")) {
				LocalTime parsedTime = LocalTime.parse(correctedTime, DateTimeFormatter.ofPattern("HH:mm:ss"));
				return now.plusHours(parsedTime.getHour()).plusMinutes(parsedTime.getMinute()).plusSeconds(parsedTime.getSecond());
			} else if (correctedTime.matches("\\d{2}:\\d{2}")) {
				// If only mm:ss, treat as 00:mm:ss
				LocalTime parsedTime = LocalTime.parse("00:" + correctedTime, DateTimeFormatter.ofPattern("HH:mm:ss"));
				return now.plusMinutes(parsedTime.getMinute()).plusSeconds(parsedTime.getSecond());
			} else {
				log.warn("OCR time format not recognized: '" + correctedTime + "' (original: '" + ocrTime + "')");
				return now;
			}
		} catch (DateTimeParseException e) {
			log.warn("Error parsing time: '" + correctedTime + "' (original: '" + ocrTime + ")");
			return now;
		}
	}

	@Override
	protected void execute() {
	logInfo("Navigating to the storehouse.");

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(3, 513), new DTOPoint(26, 588));
		sleepTask(500);

		emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(110, 270));
		sleepTask(700);

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(20, 250), new DTOPoint(200, 280));
		sleepTask(700);

		DTOImageSearchResult researchCenter = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_SHORTCUTS_RESEARCH_CENTER,  90);

		if (researchCenter.isFound()) {
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, researchCenter.getPoint(), researchCenter.getPoint());
			sleepTask(700);
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(30, 430), new DTOPoint(50, 470));
			sleepTask(700);


			boolean chestClaimed = false;
			logInfo("Searching for the storehouse chest.");
			for (int i = 0; i < 10; i++) {
				DTOImageSearchResult chest = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.STOREHOUSE_CHEST,  90);
				if (chest.isFound()) {
					logInfo("Storehouse chest found. Tapping to claim.");
					emuManager.tapAtRandomPoint(EMULATOR_NUMBER, chest.getPoint(), chest.getPoint());
					sleepTask(500);
					chestClaimed = true;
					break;
				} else {
					logDebug("Storehouse chest not found on this attempt.");
					sleepTask(500);
				}
			}

			boolean staminaClaimed = false;
			logInfo("Searching for stamina rewards.");
			for (int j = 0; j < 10; j++) {
				DTOImageSearchResult stamina = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.STOREHOUSE_STAMINA, 90);
				if (stamina.isFound()) {
					logInfo("Stamina reward found. Claiming it.");
					emuManager.tapAtRandomPoint(EMULATOR_NUMBER, stamina.getPoint(), stamina.getPoint());
					sleepTask(500);
					emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(250, 930), new DTOPoint(450, 950));
					sleepTask(4000);
					staminaClaimed = true;
					break;
				} else {
					logDebug("Stamina reward not found on this attempt.");
					sleepTask(300);
				}
			}

			// Nur wenn etwas eingesammelt wurde, Back-Button drücken
			if (chestClaimed || staminaClaimed) {
				emuManager.tapBackButton(EMULATOR_NUMBER);
			}

			// Reschedule based on OCR

			try {
				// Wait to ensure UI is updated before OCR
				sleepTask(700);
				String nextRewardTime = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(285, 642), new DTOPoint(430, 666));
				sleepTask(700);
				logInfo("OCR result for next reward time: '" + nextRewardTime + "'");
				LocalDateTime nextReward = parseNextReward(nextRewardTime);
				LocalDateTime nextReset = UtilTime.getNextReset();

				// If OCR is empty or nextReward ~ now, handle as error
				if (nextRewardTime == null || nextRewardTime.trim().isEmpty() || nextReward.isBefore(LocalDateTime.now().plusSeconds(10))) {
					logWarning("OCR for storehouse chest cooldown failed or was invalid. Rescheduling in 5 minutes.");
					this.reschedule(LocalDateTime.now().plusMinutes(5));
					return;
				}

				LocalDateTime scheduledTime;
				if (!nextReward.isBefore(nextReset)) {
					scheduledTime = nextReset;
					logInfo("Next reward time exceeds next reset, scheduling at reset to avoid missing stamina.");
				} else {
					scheduledTime = nextReward.minusSeconds(3);
				}

				this.reschedule(scheduledTime);
				ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, scheduledTime);
				logInfo("Storehouse chest claimed. Next check at " + scheduledTime);

			} catch (TesseractException | IOException e) {
				logError("Error during OCR, rescheduling for 5 minutes.", e);
				this.reschedule(LocalDateTime.now().plusMinutes(5));
			}


		} else {
			logWarning("Research center shortcut not found. Rescheduling in 5 minutes.");
			this.reschedule(LocalDateTime.now().plusMinutes(5));
			emuManager.tapBackButton(EMULATOR_NUMBER);
		}
	}

	@Override
	public boolean provideDailyMissionProgress() {return true;}
}
