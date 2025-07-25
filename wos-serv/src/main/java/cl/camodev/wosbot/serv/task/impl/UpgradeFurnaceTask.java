package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.task.DelayedTask;

public class UpgradeFurnaceTask extends DelayedTask {

	private final Map<EnumTemplates, TpDailyTaskEnum> TROOP_TASK_MAP = Map.of(
			EnumTemplates.BUILDING_DETAILS_INFANTRY, TpDailyTaskEnum.TRAINING_INFANTRY,
			EnumTemplates.BUILDING_DETAILS_MARKSMAN,  TpDailyTaskEnum.TRAINING_MARKSMAN,
			EnumTemplates.BUILDING_DETAILS_LANCER,    TpDailyTaskEnum.TRAINING_LANCER
	);

	public UpgradeFurnaceTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	protected void execute() {
		DTOImageSearchResult homeResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE.getTemplate(),  90);
		DTOImageSearchResult worldResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_WORLD.getTemplate(),  90);

		if (homeResult.isFound() || worldResult.isFound()) {
			if (worldResult.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, worldResult.getPoint());
				sleepTask(3000);
			}

			servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Going to check current building queue status");

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

						// Sumar la mitad al tiempo actual
						upgradeTime = parseNextFree(rawText);

						success = true;
						break;
					} catch (Exception e) {
						// Opcional: loggear intento fallido
						// System.out.println("OCR parsing failed at attempt " + (i + 1));
					}
				}

				if (success) {
					logInfo("Queue is already busy");
					reschedule(upgradeTime);
					return;
				} else {

					logInfo("No upgrades in progress, going to upgrade furnace");
					// going to check current furnace requierements
					// survivor status
					emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(320, 30));
					sleepTask(500);

					// search for cookhouse
					DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_CITY_STATUS_COOKHOUSE.getTemplate(),  90);
					if (result.isFound()) {
						// click on cookhouse
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
						sleepTask(500);

						// search go button
						DTOImageSearchResult goButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_CITY_STATUS_GO_BUTTON.getTemplate(),  90);
						if (goButton.isFound()) {
							// click on go button
							emuManager.tapAtRandomPoint(EMULATOR_NUMBER, goButton.getPoint(), goButton.getPoint());
							sleepTask(500);

							// go to furnace
							emuManager.tapBackButton(EMULATOR_NUMBER);
							sleepTask(500);
							emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(130, 650));
							sleepTask(1000);

							// go to upgrade button
							emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(608, 715), new DTOPoint(625, 728));
							sleepTask(500);

							// search go button for upgrade pending requirements

							DTOImageSearchResult upgradeGoButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_CITY_STATUS_GO_BUTTON.getTemplate(),  90);

							if (upgradeGoButton.isFound()) {
								logInfo("Pending upgrade requirements found, going to upgrade requierements");
								// click on go button
								emuManager.tapAtRandomPoint(EMULATOR_NUMBER, upgradeGoButton.getPoint(), upgradeGoButton.getPoint());
								sleepTask(1000);
								emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(330, 729), new DTOPoint(364, 731), 10, 10);

								DTOImageSearchResult upgradeButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_SHORTCUTS_UPGRADE.getTemplate(),  90);
								if (upgradeButton.isFound()) {
									// click on upgrade button
									emuManager.tapAtRandomPoint(EMULATOR_NUMBER, upgradeButton.getPoint(), upgradeButton.getPoint());
									sleepTask(1000);

									while ((result = emuManager.searchTemplate(EMULATOR_NUMBER,	EnumTemplates.GAME_HOME_SHORTCUTS_OBTAIN.getTemplate(),  90)).isFound()) {
										logInfo("Refilling resources for upgrade");
										emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
										sleepTask(500);
										// click replenish button
										emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(358,1135));
										sleepTask(300);

										// confirm replenish
										emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(511, 1056));
										sleepTask(1000);
									}

									emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(489, 1034), new DTOPoint(500, 1050));

								} else {
									//verify if the selected building is a troop training building (Infantry, Marksman, Lancer)
									servLogs.appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), "Upgrade button not found, checking if it's a troop training building");

									//if it's a troop training building, we need to reschedule the task till the training is done

									DTOImageSearchResult train = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.BUILDING_BUTTON_TRAIN.getTemplate(),  90);

									if (train.isFound()) {
										servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Troop training building found, rescheduling task till training is done");
										// i need to verify which troops are being trained, so I can reschedule the task accordingly

										DTOImageSearchResult detailsButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.BUILDING_BUTTON_DETAILS.getTemplate(),  90);
										if (detailsButton.isFound()){
											emuManager.tapAtRandomPoint(EMULATOR_NUMBER, detailsButton.getPoint(), detailsButton.getPoint());
											sleepTask(500);

											for (var entry : TROOP_TASK_MAP.entrySet()) {
												DTOImageSearchResult troop = emuManager.searchTemplate(EMULATOR_NUMBER,entry.getKey().getTemplate(), 90
												);
												if (troop.isFound()) {
													handleTroopReschedule(entry.getValue());
													return;
												}
											}

											servLogs.appendLog(
													EnumTpMessageSeverity.WARNING,
													taskName,
													profile.getName(),
													"No troop training found, rescheduling task for 1 hour"
											);
											reschedule(LocalDateTime.now().plusHours(1));
										}

									}

									servLogs.appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), "Upgrade button not found, skipping upgrade");
									reschedule(LocalDateTime.now());
									return;
								}

								for (int i = 0; i < MAX_ATTEMPTS; i++) {
									DTOImageSearchResult alliesHelpButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_SHORTCUTS_HELP_REQUEST.getTemplate(), 90);

									if (alliesHelpButton.isFound()) {
										logInfo("Allies help button found, requesting help");
										emuManager.tapAtRandomPoint(EMULATOR_NUMBER, alliesHelpButton.getPoint(), alliesHelpButton.getPoint());
										sleepTask(300);
										break;
									} else if (i == MAX_ATTEMPTS - 1) {
										logInfo("Allies help button not found, skipping request");
										reschedule(LocalDateTime.now());
										return;
									}
								}

							} else {
								// click on upgrade button


								while ((result = emuManager.searchTemplate(EMULATOR_NUMBER,	EnumTemplates.GAME_HOME_SHORTCUTS_OBTAIN.getTemplate(),  90)).isFound()) {
									logInfo("Refilling resources for upgrade");
									emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
									sleepTask(500);
									// click replenish button
									emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(358,1135));
									sleepTask(300);

									// confirm replenish
									emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(511, 1056));
									sleepTask(1000);
								}

								servLogs.appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), "No pending upgrade requirements found for furnace, upgrading furnace directly");
								emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(470, 1196), new DTOPoint(480, 1216));


								sleepTask(1000);

								// check if allies can help

								for (int i = 0; i < MAX_ATTEMPTS; i++) {
									DTOImageSearchResult alliesHelpButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_SHORTCUTS_HELP_REQUEST.getTemplate(),  90);

									if (alliesHelpButton.isFound()) {
										logInfo("Allies help button found, requesting help");
										emuManager.tapAtRandomPoint(EMULATOR_NUMBER, alliesHelpButton.getPoint(), alliesHelpButton.getPoint());
										sleepTask(500);
										break;
									} else if (i == MAX_ATTEMPTS - 1) {
										servLogs.appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), "Allies help button not found, skipping request");
									}
								}
								reschedule(LocalDateTime.now());
								return;

							}
							this.reschedule(LocalDateTime.now());
						} else {
							servLogs.appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), "Go button not found for cookhouse upgrade");
						}
					}

				}


                        } catch (Exception e) {
                                ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Error upgrading furnace: " + e.getMessage());
                        }

		} else{
			emuManager.tapBackButton(EMULATOR_NUMBER);
			this.reschedule(LocalDateTime.now());
		}

	}

	public LocalDateTime parseNextFree(String input) {
		// Regular expression to match the input format [n]d HH:mm:ss' o 'HH:mm:ss
		Pattern pattern = Pattern.compile("(?i).*?(?:(\\d+)\\s*d\\s*)?(\\d{1,2}:\\d{2}:\\d{2}).*", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(input.trim());

		if (!matcher.matches()) {
			throw new IllegalArgumentException("Input does not match the expected format. Expected format: [n]d HH:mm:ss' o 'HH:mm:ss");
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


	private void handleTroopReschedule(TpDailyTaskEnum taskEnum) {
		logInfo(taskEnum.name() + " build found, getting task state to reschedule");

		DTOTaskState taskState = ServTaskManager
				.getInstance()
				.getTaskState(profile.getId(), taskEnum.getId());

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime next = (taskState != null && taskState.getNextExecutionTime() != null)
				? taskState.getNextExecutionTime()
				: now;

		if (next.isBefore(now)) {
			logInfo("Next execution time is before now, rescheduling task for now");
			reschedule(now);
		} else {
			logInfo("Next execution time is after now, rescheduling task for " +
					"next execution time minus 5 seconds");
			reschedule(next.minusSeconds(5));
		}
	}
}
