package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

public class AllianceAutojoinTask extends DelayedTask {

	public AllianceAutojoinTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	@Override
	protected void execute() {

		logInfo("Starting alliance auto-join task");

		// Go to the alliance chests section
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(493, 1187), new DTOPoint(561, 1240));
		sleepTask(3000);

		DTOImageSearchResult menuResult = emuManager.searchTemplate(EMULATOR_NUMBER,
				EnumTemplates.ALLIANCE_WAR_BUTTON,  90);
		if (!menuResult.isFound()) {
			logError("Alliance war button not found, rescheduling task");
			this.reschedule(LocalDateTime.now().plusHours(1));
			return;
		}

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, menuResult.getPoint(), menuResult.getPoint());
		sleepTask(4000);

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(260, 1200), new DTOPoint(450, 1240));

		sleepTask(1000);

		emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(430, 600), new DTOPoint(40, 600));
		sleepTask(200);

		int attempts = profile.getConfig(EnumConfigurationKey.ALLIANCE_AUTOJOIN_QUEUES_INT, Integer.class);

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(460, 590), new DTOPoint(497, 610), (attempts - 1),
				400);

        sleepTask(200);
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(380, 1070), new DTOPoint(640, 1120));
        sleepTask(300);

		LocalDateTime nextSchedule = LocalDateTime.now().plusHours(7).plusMinutes(50);
		this.reschedule(nextSchedule);
		logInfo("Alliance autojoin task completed. Next execution in 7 hours.");

		emuManager.tapBackButton(EMULATOR_NUMBER);
		emuManager.tapBackButton(EMULATOR_NUMBER);
		emuManager.tapBackButton(EMULATOR_NUMBER);

	}

}
