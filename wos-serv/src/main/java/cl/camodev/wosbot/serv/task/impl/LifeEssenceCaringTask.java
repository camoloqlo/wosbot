package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

public class LifeEssenceCaringTask extends DelayedTask {


	public LifeEssenceCaringTask(DTOProfiles profile, TpDailyTaskEnum dailyMission) {
		super(profile, dailyMission);
	}

	// i should go to essence tree, check if there's daily attempt available, if not, reschedule till daily reset,
	@Override
	protected void execute() {

		logInfo( "Starting Life Essence Caring task.");
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(1, 509), new DTOPoint(24, 592));
		// make sure we are in the city shortcut
		sleepTask(2000);
		emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(110, 270));
		sleepTask(1000);

		// swipe down
		emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(220, 845), new DTOPoint(220, 94));
		sleepTask(1000);
		DTOImageSearchResult lifeEssenceMenu = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_MENU,  90);

		if (lifeEssenceMenu.isFound()) {
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, lifeEssenceMenu.getPoint(), lifeEssenceMenu.getPoint());
			sleepTask(3000);
			tapBackButton();
			tapBackButton();
			sleepTask(500);
			logInfo("Checking for available daily caring attempts.");
			emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(670, 100));
			sleepTask(2000);

			DTOImageSearchResult dailyAttempt = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_DAILY_CARING_AVAILABLE, 90);
			if (dailyAttempt.isFound()) {
				logInfo( "Daily caring attempt available. Proceeding.");

				// I should search and scroll about 7-8 times, if I don't find anything, reschedule for an hour

				for (int i = 0; i < 10; i++) {
					DTOImageSearchResult caringAvailable = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_DAILY_CARING_GOTO_ISLAND, 90);
					if (caringAvailable.isFound()) {
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, caringAvailable.getPoint(), caringAvailable.getPoint());
						sleepTask(5000);
						logInfo( "Found an island that needs caring. Navigating to it.");
						// search for the caring button, I should search a couple of times due to movement

						for (int j = 0; j < 3; j++) {
							DTOImageSearchResult caringButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_DAILY_CARING_BUTTON,  90);
							if (caringButton.isFound()) {
								emuManager.tapAtRandomPoint(EMULATOR_NUMBER, caringButton.getPoint(), caringButton.getPoint());
								sleepTask(5000);
								logInfo("Caring completed successfully.");
								emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(42, 28));
								sleepTask(3000);
								emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(42, 28));
								return;
							}
						}

						return;
					} else {
						logInfo("No island needing care found on screen. Scrolling down.");
						emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(350, 1100), new DTOPoint(350, 670));
						sleepTask(2000);
					}
				}

				this.reschedule(LocalDateTime.now().plusMinutes(profile.getConfig(EnumConfigurationKey.ALLIANCE_LIFE_ESSENCE_OFFSET_INT, Integer.class)));
				logInfo("No island needing care found after multiple scrolls. Rescheduling.");
                emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(42, 28),new DTOPoint(42, 28) ,3,1500);

			} else {
				logInfo("No daily caring attempts available. Rescheduling for the next game day.");
				this.reschedule(UtilTime.getGameReset());
                emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(42, 28),new DTOPoint(42, 28) ,3,1500);
			}
		}

	}

}
