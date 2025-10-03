package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.serv.task.DelayedTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PetAdventureChestTask extends DelayedTask {

	private int attempts = 0;

	public PetAdventureChestTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	@Override
	protected void execute() {
		if (attempts >= 3) {
			logWarning("Could not find the Pet Adventure menu after multiple attempts. Removing task from scheduler.");
			this.setRecurring(false);
			return;
		}

		logInfo("Navigating to the Pet Adventures screen.");

		DTOImageSearchResult petsResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_PETS,  90);
		if (petsResult.isFound()) {
			logInfo("Pets button found. Tapping to open.");
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, petsResult.getPoint(), petsResult.getPoint());
			sleepTask(3000);

			DTOImageSearchResult beastCageResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_BEAST_CAGE, 90);
			if (beastCageResult.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, beastCageResult.getPoint());
				sleepTask(500);
				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(547, 1150), new DTOPoint(650, 1210));

				for (int i = 0; i < 10; i++) {
					logDebug("Searching for completed chests to claim.");
					DTOImageSearchResult doneChest = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_CHEST_COMPLETED, 90);
					if (doneChest.isFound()) {
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, doneChest.getPoint(), doneChest.getPoint());
						sleepTask(500);
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(270, 735), new DTOPoint(450, 760), 20, 100);

						DTOImageSearchResult share = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_CHEST_SHARE,  90);
						if (share.isFound()) {
							logInfo("Sharing the completed chest with the alliance.");
							emuManager.tapAtRandomPoint(EMULATOR_NUMBER, share.getPoint(), share.getPoint());
							sleepTask(500);
						}
						tapBackButton();
						sleepTask(500);
					}
				}

				List<EnumTemplates> chests = List.of(EnumTemplates.PETS_CHEST_RED, EnumTemplates.PETS_CHEST_PURPLE, EnumTemplates.PETS_CHEST_BLUE);

				boolean foundAnyChest; // Control variable

				do {
					foundAnyChest = false; // Reset on each iteration

					for (EnumTemplates enumTemplates : chests) {
						for (int attempt = 0; attempt < 5; attempt++) {
							logDebug("Searching for " + enumTemplates + ", attempt " + (attempt + 1) + ".");

							DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER, enumTemplates,  90);
							if (result.isFound()) {
								foundAnyChest = true; // A chest was found, the loop will repeat

								logInfo("Found chest: " + enumTemplates + ". Attempting to start adventure.");

								emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
								sleepTask(500);

								DTOImageSearchResult chestSelect = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_CHEST_SELECT,  90);

								if (chestSelect.isFound()) {
									emuManager.tapAtPoint(EMULATOR_NUMBER, chestSelect.getPoint());
									sleepTask(500);

									DTOImageSearchResult chestStart = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_CHEST_START,  90);

									if (chestStart.isFound()) {
										emuManager.tapAtPoint(EMULATOR_NUMBER, chestStart.getPoint());
										sleepTask(500);

										tapBackButton();
										sleepTask(500);
                                        StaminaService.getServices().subtractStamina(profile.getId(),10);
										break; // Exits the attempt, but not the main loop
									} else {
										DTOImageSearchResult attemptsResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.PETS_CHEST_ATTEMPT,  90);
										if (attemptsResult.isFound()) {
											logInfo("No more adventure attempts available. Rescheduling for the next game reset.");
											this.reschedule(UtilTime.getGameReset());
											tapBackButton();
											tapBackButton();
											tapBackButton();
											tapBackButton();
											return;
										}
									}
								}
							}
						}
					}

					if (foundAnyChest) {
						logInfo("At least one chest was found and started. Restarting search for more chests.");
						sleepTask(5000); // Wait 5 seconds before repeating
					}

				} while (foundAnyChest); // The loop repeats until no more chests are found

				logInfo("No more available chests found to start. Rescheduling for 2 hours.");
				this.reschedule(LocalDateTime.now().plusHours(2));
				tapBackButton();
				tapBackButton();

			}

		} else {
			logWarning("Could not find the pets button on the home screen. Retrying in 15 minutes.");
			reschedule(LocalDateTime.now().plusMinutes(15));
			attempts++;
		}
	}

	public Integer extractRemainingAttempts(String ocrText) {
		if (ocrText == null || ocrText.isEmpty()) {
			return null; // Handle null or empty cases
		}

		// Normalize text to reduce OCR errors
		String normalizedText = ocrText.replaceAll("[^a-zA-Z0-9: ]", "").trim();

		// Regular expression to search for a number after "attempts"
		Pattern pattern = Pattern.compile("(?i)attempts.*?\\b(\\d+)\\b");
		Matcher matcher = pattern.matcher(normalizedText);

		if (matcher.find()) {
			try {
				return Integer.parseInt(matcher.group(1));
			} catch (NumberFormatException e) {
				return null; // Return null if the number cannot be parsed
			}
		}

		return null; // Return null if the number is not found
	}

	@Override
	public boolean provideDailyMissionProgress() {return true;}


    @Override
    protected boolean consumesStamina() {
        return true;
    }

}
