package cl.camodev.wosbot.serv.task.impl;

import java.io.IOException;
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

public class StorehouseChest extends DelayedTask {

    private LocalDateTime nextStaminaClaim = LocalDateTime.now();

    public StorehouseChest(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
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
				logWarning("OCR time format not recognized: '" + correctedTime + "' (original: '" + ocrTime + "')");
				return now;
			}
		} catch (DateTimeParseException e) {
			logWarning("Error parsing time: '" + correctedTime + "' (original: '" + ocrTime + "')");
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
			sleepTask(1000);
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(30, 430), new DTOPoint(50, 470));
			sleepTask(1000);
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(1,636), new DTOPoint(2,636),2,300);

			logInfo("Searching for the storehouse chest.");
			for (int i = 0; i < 5; i++) {
				DTOImageSearchResult chest = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.STOREHOUSE_CHEST,  90);

				logDebug("Searching for storehouse chest (Attempt " + (i + 1) + "/5).");
				if (chest.isFound()) {
					logInfo("Storehouse chest found. Tapping to claim.");
					emuManager.tapAtRandomPoint(EMULATOR_NUMBER, chest.getPoint(), chest.getPoint());
					sleepTask(500);
					emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(1,636), new DTOPoint(2,636),5,300);
					break;
				} else {
					logDebug("Storehouse chest not found (Attempt " + (i + 1) + "/5).");
					emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(1,636), new DTOPoint(2,636),2,300);
				}
				sleepTask(300);
			}

			// Only search for stamina if current time is >= nextStaminaClaim
			if (!LocalDateTime.now().isBefore(nextStaminaClaim)) {
				logInfo("Searching for stamina rewards.");
				for (int j = 0; j < 10; j++) {
					DTOImageSearchResult stamina = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.STOREHOUSE_STAMINA, 90);

					logDebug("Searching for stamina reward (Attempt " + (j + 1) + "/10).");
					if (stamina.isFound()) {
						logInfo("Stamina reward found. Claiming it.");
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, stamina.getPoint(), stamina.getPoint());
						sleepTask(500);
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(250, 930), new DTOPoint(450, 950));
						sleepTask(4000);

						// After successfully claiming stamina, schedule next claim at next reset
						try {
							nextStaminaClaim = UtilTime.getNextReset();
							logInfo("Next stamina claim scheduled at " + nextStaminaClaim);
						} catch (Exception e) {
							logDebug("Error obtaining next reset for stamina claim; keeping previous schedule.");
						}

						break;
					} else {
						logDebug("Stamina reward not found (Attempt " + (j + 1) + "/10).");
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(1,636), new DTOPoint(2,636),2,300);
					}
					sleepTask(300);
				}
			} else {
				logInfo("Skipping stamina search until " + nextStaminaClaim);
			}

			// Reschedule based on OCR with retry logic
			try {
				for (int attempt = 0; attempt < 5; attempt++) {
					String nextRewardTime = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(285, 642), new DTOPoint(430, 666));
					LocalDateTime nextReward = parseNextReward(nextRewardTime);
					LocalDateTime nextReset = UtilTime.getNextReset();

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
					break;
				}

			} catch (TesseractException | IOException e) {
				logError("Error during OCR, rescheduling for 5 minutes.", e);
				this.reschedule(LocalDateTime.now().plusMinutes(5));
			} catch (Exception e) {
				logError("Unexpected error during OCR, rescheduling for 5 minutes.", e);
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
