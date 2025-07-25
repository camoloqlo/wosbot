package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.DelayedTask;

public class AllianceTechTask extends DelayedTask {

	public AllianceTechTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	protected void execute() {

		logInfo("Going to alliance chest");

		// Ir a la sección de cofres de alianza
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(493, 1187), new DTOPoint(561, 1240));
		sleepTask(3000);

		DTOImageSearchResult menuResult = emuManager.searchTemplate(EMULATOR_NUMBER,
				EnumTemplates.ALLIANCE_TECH_BUTTON.getTemplate(),  90);
		if (!menuResult.isFound()) {
			this.reschedule(LocalDateTime.now()
					.plusMinutes(profile.getConfig(EnumConfigurationKey.ALLIANCE_TECH_OFFSET_INT, Integer.class)));
			return;
		}

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, menuResult.getPoint(), menuResult.getPoint());
		sleepTask(500);

		// search for thumb up button

		DTOImageSearchResult thumbUpResult = emuManager.searchTemplate(EMULATOR_NUMBER,
				EnumTemplates.ALLIANCE_TECH_THUMB_UP.getTemplate(),  90);

		if (!thumbUpResult.isFound()) {
			logError("No task marked for upgrade, rescheduling task");
			this.reschedule(LocalDateTime.now()
					.plusMinutes(profile.getConfig(EnumConfigurationKey.ALLIANCE_TECH_OFFSET_INT, Integer.class)));
			return;
		}

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, thumbUpResult.getPoint(), thumbUpResult.getPoint());

		sleepTask(500);

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(450, 1000), new DTOPoint(580, 1050), 25, 150);
		emuManager.tapBackButton(EMULATOR_NUMBER);
		emuManager.tapBackButton(EMULATOR_NUMBER);
		emuManager.tapBackButton(EMULATOR_NUMBER);

		LocalDateTime nextSchedule = LocalDateTime.now()
				.plusMinutes(profile.getConfig(EnumConfigurationKey.ALLIANCE_TECH_OFFSET_INT, Integer.class));
		this.reschedule(nextSchedule);
		ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, nextSchedule);

	}

	@Override
	public boolean provideDailyMissionProgress() {return true;}

}
