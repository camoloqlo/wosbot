package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class VipTask extends DelayedTask {

	public VipTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

	@Override
	protected void execute() {

		logInfo("Navigating to the VIP menu.");
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(430, 48), new DTOPoint(530, 85));
		sleepTask(3000);

		if (profile.getConfig(EnumConfigurationKey.VIP_BUY_MONTHLY, Boolean.class)) {
			logInfo("Verifying VIP status.");
			DTOImageSearchResult monthlyVip = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.VIP_UNLOCK_BUTTON,  90);
			if (monthlyVip.isFound()) {
				logInfo("VIP is not active. Purchasing the monthly VIP pass.");
				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, monthlyVip.getPoint(), monthlyVip.getPoint());
				sleepTask(1000);
				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(520, 810), new DTOPoint(650, 850));
				sleepTask(500);
				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(250, 770), new DTOPoint(480, 800));
				sleepTask(500);
				tapBackButton();
				sleepTask(500);

			}
		}

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(540, 813), new DTOPoint(624, 835), 7, 300);
		sleepTask(500);

		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(602, 263), new DTOPoint(650, 293), 7, 300);
		sleepTask(500);

		reschedule(UtilTime.getGameReset());
		tapBackButton();

	}

}
