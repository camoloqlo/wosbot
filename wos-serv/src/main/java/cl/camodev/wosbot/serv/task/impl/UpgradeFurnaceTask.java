package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpgradeFurnaceTask extends DelayedTask {

	public UpgradeFurnaceTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

	@Override
	protected void execute() {
		logInfo("Checking the current building queue status...");

		// left menu
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(3, 513), new DTOPoint(26, 588));
		sleepTask(500);

		// city tab
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(20, 250), new DTOPoint(200, 280));
		sleepTask(500);

		// ocr to check current building queue status

		final int MAX_ATTEMPTS = 10;
		boolean success = false;
		LocalDateTime upgradeTime = null;

		try {
			for (int i = 0; i < MAX_ATTEMPTS; i++) {
				try {
					String rawText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(162, 378), new DTOPoint(293, 397));
					if (rawText.contains("Idle")) {
						break;
					}

					// Add half the time to the current time
					upgradeTime = parseNextFree(rawText);

					success = true;
					break;
				} catch (Exception e) {
					// Optional: log failed attempt
					// System.out.println("OCR parsing failed at attempt " + (i + 1));
				}
			}

			if (success) {
				logInfo("The building queue is currently busy. Rescheduling for later.");
				reschedule(upgradeTime);
				return;
			} else {

				logInfo("No upgrades are in progress. Proceeding to upgrade the furnace.");
				// going to check current furnace requirements
				// survivor status
				emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(320, 30));
				sleepTask(500);

				// search for cookhouse
				DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_CITY_STATUS_COOKHOUSE,  90);
				if (result.isFound()) {
					// click on cookhouse
					emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
					sleepTask(500);

					// search go button
					DTOImageSearchResult goButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_CITY_STATUS_GO_BUTTON,  90);
					if (goButton.isFound()) {
						// click on go button
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, goButton.getPoint(), goButton.getPoint());
						sleepTask(3000);

						// click on furnace
						emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(320, 600));
						sleepTask(500);

						// click on details
						emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(320, 880));
						sleepTask(500);

						// click on upgrade
						DTOImageSearchResult upgradeButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.BUILDING_BUTTON_UPGRADE,  90);
						if (upgradeButton.isFound()) {
							emuManager.tapAtPoint(EMULATOR_NUMBER, upgradeButton.getPoint());
							sleepTask(500);

							// check if can upgrade
							DTOImageSearchResult upgradeConfirmButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.BUILDING_BUTTON_UPGRADE,  90);
							if (upgradeConfirmButton.isFound()) {
								emuManager.tapAtPoint(EMULATOR_NUMBER, upgradeConfirmButton.getPoint());
								logInfo("Furnace upgrade started successfully.");
								reschedule(LocalDateTime.now().plusMinutes(5));
							} else {
								logInfo("Furnace cannot be upgraded at this time. Checking for missing requirements.");
								// check for missing requirements
								// go back
								tapBackButton();
								sleepTask(500);
								tapBackButton();
								sleepTask(500);
								tapBackButton();
								sleepTask(500);
								tapBackButton();
								sleepTask(500);
								reschedule(LocalDateTime.now().plusHours(1));
							}
						} else {
							logInfo("Furnace is max level or cannot be upgraded. Task will not run again.");
							this.setRecurring(false);
						}
					} else {
						logInfo("Cookhouse not found. Cannot proceed with furnace upgrade check.");
						reschedule(LocalDateTime.now().plusHours(1));
					}
				} else {
					logInfo("Cookhouse not found. Cannot proceed with furnace upgrade check.");
					reschedule(LocalDateTime.now().plusHours(1));
				}
			}
		} catch (Exception e) {
			logError("An error occurred during the furnace upgrade task.", e);
			reschedule(LocalDateTime.now().plusMinutes(5));
		}
	}

	public LocalDateTime parseNextFree(String input) {
		// Regular expression to match the input format [n]d HH:mm:ss or HH:mm:ss
		Pattern pattern = Pattern.compile("(?i).*?(?:(\\d+)\\s*d\\s*)?(\\d{1,2}:\\d{2}:\\d{2}).*", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(input.trim());

		if (!matcher.matches()) {
			throw new IllegalArgumentException("Input does not match the expected format. Expected format: [n]d HH:mm:ss or HH:mm:ss");
		}


		String daysStr = matcher.group(1);   // optional, can be null
		String timeStr = matcher.group(2);   // always present

		int daysToAdd = (daysStr != null) ? Integer.parseInt(daysStr) : 0;

		// parser for time part
		DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm:ss");
		LocalTime timePart = LocalTime.parse(timeStr, timeFormatter);


		return LocalDateTime.now()
				.plusDays(daysToAdd)
				.plusHours(timePart.getHour())
				.plusMinutes(timePart.getMinute())
				.plusSeconds(timePart.getSecond());
	}
}
