package cl.camodev.wosbot.serv.task.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import net.sourceforge.tess4j.TesseractException;

public class BeastSlayTask extends DelayedTask {

	private int stamina = 0;
	private int availableQueues = 0;
	private int maxQueues = 3;

	public BeastSlayTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	public static LocalDateTime calculateFullStaminaTime(int currentStamina, int maxStamina, int regenRateMinutes) {
		if (currentStamina >= maxStamina) {
			return LocalDateTime.now(); // Already full
		}

		int staminaNeeded = maxStamina - currentStamina;
		int minutesToFull = staminaNeeded * regenRateMinutes;

		return LocalDateTime.now().plusMinutes(minutesToFull);
	}

	@Override
	protected void execute() {

		// go to profile to see stamina
		tapPoint(new DTOPoint(50, 50));
		sleepTask(500);
		// go to stamina menu
		tapRandomPoint(new DTOPoint(220, 1100), new DTOPoint(250, 1125));
		sleepTask(2000);
		// ocr the stamina 350,270 490,300

		try {
			String staminaText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(350, 270),
					new DTOPoint(490, 300));
			logInfo("Stamina detected: " + staminaText);
			tapBackButton();
			tapBackButton();

			stamina = extractFirstNumber(staminaText);

			if (stamina < 10) {
				LocalDateTime fullStaminaTime = calculateFullStaminaTime(stamina, 100, 5);
				logInfo("Stamina (" + stamina + ") is below the threshold (10). Rescheduling task to "
						+ fullStaminaTime);
				this.reschedule(fullStaminaTime);
				return;
			}

		} catch (IOException | TesseractException e) {
			logError("An error occurred during stamina OCR: " + e.getMessage());
			return; // Can't continue without stamina info
		}

		// should get the number of available queues to attack beasts

		try {
			// go to profile
			tapPoint(new DTOPoint(50, 50));
			sleepTask(1000);

			// go to queue menu
			tapRandomPoint(new DTOPoint(210, 1190), new DTOPoint(330, 1250));
			sleepTask(1000);

			String queueText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(280, 230),
					new DTOPoint(340, 252));
			logInfo("Available queues detected: " + queueText);
			tapBackButton();
			tapBackButton();

			availableQueues = extractFirstNumber(queueText);

			if (stamina < 10) {
				LocalDateTime fullStaminaTime = calculateFullStaminaTime(stamina, 100, 5);
				logInfo("Stamina (" + stamina + ") is below the threshold (10). Rescheduling task to "
						+ fullStaminaTime);
				return;
			}

		} catch (IOException | TesseractException e) {
			logError("An error occurred during queue OCR: " + e.getMessage());
			return;
		}

		// if we got here, we have more than 10 stamina and should attack beasts until
		// stamina is less than 10, consumption is 8-10 per attack
		int beastLevel = 30;
		List<Long> activeBeasts = new ArrayList<>(); // List of beast completion times
		logInfo("Starting beast attacks.");

		while (stamina >= 10) {

			// Check if any beast has finished its time
			long currentTime = System.currentTimeMillis();
			Iterator<Long> iterator = activeBeasts.iterator();
			while (iterator.hasNext()) {
				if (currentTime >= iterator.next()) {
					iterator.remove(); // Remove beast that has finished its time
					availableQueues++; // Free up a queue
					logInfo("A beast attack has finished. Available queues: " + availableQueues);
				}
			}

			// Only attack if there is an available queue
			if (availableQueues > 0) {
				for (int i = 0; i < maxQueues; i++) {
					if (availableQueues <= 0)
						break; // If no more queues, exit the loop

					sleepTask(6000);
					// go to the beast
					tapRandomPoint(new DTOPoint(25, 850), new DTOPoint(67, 898));
					sleepTask(1000);

					emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(20, 910), new DTOPoint(70, 915));
					sleepTask(1000);
					// beast button
					tapRandomPoint(new DTOPoint(70, 880), new DTOPoint(120, 930));
					sleepTask(1000);
					// go to level 1
					emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(180, 1050), new DTOPoint(1, 1050));

					// select beast level
					tapRandomPoint(new DTOPoint(470, 1040), new DTOPoint(500, 1070), beastLevel - 1, 100);
					sleepTask(1000);
					// click search
					tapRandomPoint(new DTOPoint(301, 1200), new DTOPoint(412, 1229));
					sleepTask(6000);

					// click attack
					tapRandomPoint(new DTOPoint(270, 600), new DTOPoint(460, 630));
					sleepTask(6000);

					try {
						// Get stamina and remaining time via OCR

						String timeText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(519, 1141),
								new DTOPoint(618, 1164));
						logInfo("Attack time detected: " + timeText);

						timeText = timeText.trim().replaceAll("[^0-9:]", ""); // Only keep numbers and ":"

						// attack
						tapRandomPoint(new DTOPoint(450, 1183), new DTOPoint(640, 1240));

						stamina -= 10;
						availableQueues--;

						int totalSeconds = 0;
						String[] timeParts = timeText.split(":");

						if (timeParts.length == 3) {
							// HH:mm:ss format
							totalSeconds = Integer.parseInt(timeParts[0]) * 3600 + Integer.parseInt(timeParts[1]) * 60
									+ Integer.parseInt(timeParts[2]);
						} else if (timeParts.length == 2) {
							// mm:ss format
							totalSeconds = Integer.parseInt(timeParts[0]) * 60 + Integer.parseInt(timeParts[1]);
						} else {
							// invalid or incomplete format, assign a default value
							logWarning("Invalid time format from OCR: '" + timeText + "'. Using default 10 seconds.");
							totalSeconds = 10; // Default 10 seconds wait
						}

						// Calculate the beast's finish time
						long finishTime = System.currentTimeMillis() + ((totalSeconds * 1000L) * 2);
						activeBeasts.add(finishTime);
						logInfo("Beast attacked. March will return in approximately " + (totalSeconds * 2)
								+ " seconds.");

					} catch (Exception e) {
						logError("Failed to get beast information: " + e.getMessage());
					}
				}
			} else {
				// If no queues are available, wait a bit before checking again
				logInfo("All queues are busy. Waiting for a beast attack to finish...");
				sleepTask(5000); // 5 second wait before checking again
			}
		}

		logInfo("Beast Slay task finished.");
	}

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.WORLD;
	}

	private int extractFirstNumber(String text) {
		if (text == null || text.isEmpty()) {
			throw new IllegalArgumentException("OCR text cannot be null or empty.");
		}

		// Normalize OCR text (replace common errors)
		String normalizedText = text.replaceAll("[oO]", "0") // Replace 'o' or 'O' with '0'
				.replaceAll("[^0-9/]", "") // Remove characters that are not numbers or '/'
				.trim(); // Remove spaces at the ends

		// Regular expression to capture the part before the "/"
		Pattern pattern = Pattern.compile("^(\\d+)/\\d+$");
		Matcher matcher = pattern.matcher(normalizedText);

		if (matcher.find()) {
			return Integer.parseInt(matcher.group(1)); // Extracts the first part of the fraction as an integer
		} else {
			throw new NumberFormatException("No valid format found in OCR text: " + normalizedText);
		}
	}

}
