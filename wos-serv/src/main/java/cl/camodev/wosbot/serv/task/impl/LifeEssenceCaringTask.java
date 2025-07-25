package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

public class LifeEssenceCaringTask extends DelayedTask {

        private static final int IMAGE_MATCH_THRESHOLD = 90;
        private static final int MAX_SCROLL_ATTEMPTS = 8;
        private static final int MAX_BUTTON_SEARCH_ATTEMPTS = 3;


	public LifeEssenceCaringTask(DTOProfiles profile, TpDailyTaskEnum dailyMission) {
		super(profile, dailyMission);
	}

        // Navigate to the essence tree and check if a daily attempt is available.
        // If not, reschedule until the daily reset.
	@Override
	protected void execute() {

                logInfo("Going to Life Essence menu");
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(1, 509), new DTOPoint(24, 592));
                // Ensure we are in the city shortcut
		sleepTask(2000);
		emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(110, 270));
		sleepTask(1000);

                // Swipe down
		emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(220, 845), new DTOPoint(220, 94));
		sleepTask(1000);
                DTOImageSearchResult lifeEssenceMenu = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_MENU.getTemplate(), IMAGE_MATCH_THRESHOLD);

		if (lifeEssenceMenu.isFound()) {
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, lifeEssenceMenu.getPoint(), lifeEssenceMenu.getPoint());
			sleepTask(3000);
			emuManager.tapBackButton(EMULATOR_NUMBER);
			emuManager.tapBackButton(EMULATOR_NUMBER);
			sleepTask(500);
			servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Going to check if there's daily attempts available");
			emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(670, 100));
			sleepTask(2000);

                        DTOImageSearchResult dailyAttempt = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_DAILY_CARING_AVAILABLE.getTemplate(), IMAGE_MATCH_THRESHOLD);
			if (dailyAttempt.isFound()) {
				logInfo( "Daily attempt available, proceeding with caring");

                                // Search and scroll several times; if not found, reschedule in one hour

                                for (int i = 0; i < MAX_SCROLL_ATTEMPTS; i++) {
                                        DTOImageSearchResult caringAvailable = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_DAILY_CARING_GOTO_ISLAND.getTemplate(), IMAGE_MATCH_THRESHOLD);
					if (caringAvailable.isFound()) {
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, caringAvailable.getPoint(), caringAvailable.getPoint());
						sleepTask(5000);
						logInfo( "Caring available, proceeding with caring");
                                                // Search for the caring button; try a couple times due to animation

                                                for (int j = 0; j < MAX_BUTTON_SEARCH_ATTEMPTS; j++) {
                                                        DTOImageSearchResult caringButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_DAILY_CARING_BUTTON.getTemplate(),  IMAGE_MATCH_THRESHOLD);
							if (caringButton.isFound()) {
								emuManager.tapAtRandomPoint(EMULATOR_NUMBER, caringButton.getPoint(), caringButton.getPoint());
								sleepTask(5000);
								servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Caring done successfully");
								emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(42, 28));
								sleepTask(3000);
								emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(42, 28));
								return;
							}
						}

						return;
					} else {
						servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Caring not found, scrolling down");
						emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(350, 1100), new DTOPoint(350, 670));
						sleepTask(2000);
					}
				}

				this.reschedule(LocalDateTime.now().plusMinutes(profile.getConfig(EnumConfigurationKey.ALLIANCE_LIFE_ESSENCE_OFFSET_INT, Integer.class)));
				logInfo("No caring available after multiple attempts");

				emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(42, 28));
				sleepTask(3000);
				emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(42, 28));

			} else {
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "No daily attempts available, rescheduling for next day");
				this.reschedule(UtilTime.getGameReset());
				servScheduler.updateDailyTaskStatus(profile, tpTask, UtilTime.getGameReset());
				emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(42, 28));
				sleepTask(3000);
				emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(42, 28));
			}
		}

	}

}
