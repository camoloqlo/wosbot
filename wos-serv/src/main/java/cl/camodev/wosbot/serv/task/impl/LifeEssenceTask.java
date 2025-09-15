package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.DelayedTask;

import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class LifeEssenceTask extends DelayedTask {

	private int attempts = 0;

	public LifeEssenceTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	protected void execute() {
		if (attempts > 5) {
			this.setRecurring(false);
			logWarning("Too many failed attempts. Removing Life Essence task from the scheduler.");
		}

		logInfo("Navigating to the Life Essence menu.");
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(1, 509), new DTOPoint(24, 592));
		// make sure to be in the city shortcut
		sleepTask(2000);
		emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(110, 270));
		sleepTask(1000);

		// swipe down
		emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(220, 845), new DTOPoint(220, 94));
		sleepTask(1000);
		DTOImageSearchResult lifeEssenceMenu = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_MENU,  90);
		int claim = 0;
		if (lifeEssenceMenu.isFound()) {
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, lifeEssenceMenu.getPoint(), lifeEssenceMenu.getPoint());
			sleepTask(3000);
			emuManager.tapBackButton(EMULATOR_NUMBER);
			emuManager.tapBackButton(EMULATOR_NUMBER);
			logInfo("Searching for Life Essence to claim.");
			for (int i = 1; i < 11; i++) {
				logDebug("Searching for Life Essence, attempt " + i + ".");
				DTOImageSearchResult lifeEssence = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.LIFE_ESSENCE_CLAIM, new DTOPoint(0, 80), new DTOPoint(720, 1280), 90);
				if (lifeEssence.isFound()) {
					emuManager.tapAtPoint(EMULATOR_NUMBER, lifeEssence.getPoint());
					sleepTask(100);
					claim++;
				}
				if (claim > 4) {
					break;
				}
			}
			LocalDateTime nextSchedule = LocalDateTime.now().plusMinutes(profile.getConfig(EnumConfigurationKey.LIFE_ESSENCE_OFFSET_INT, Integer.class));
			this.reschedule(nextSchedule);
			ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, nextSchedule);
			logInfo("Life Essence claimed: " + claim + ". Rescheduling for " + nextSchedule + ".");
			
			emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(40, 30));
			sleepTask(1000);

		} else {
			logWarning("Life Essence menu not found.");
			attempts++;
		}
	}

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

}
