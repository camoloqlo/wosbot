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

public class DailyMissionTask extends DelayedTask {

	public DailyMissionTask(DTOProfiles profile, TpDailyTaskEnum dailyMission) {
		super(profile, dailyMission);
	}

	@Override
	protected void execute() {

		logInfo("Starting daily mission task.");

		emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(50, 1050));
		sleepTask(3000);

		DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER,
				EnumTemplates.DAILY_MISSION_DAILY_TAB,  90);

		if (result.isFound()) {
			logInfo("Switching to the daily mission tab.");
			emuManager.tapAtPoint(EMULATOR_NUMBER, result.getPoint());
			sleepTask(500);
		}

		logInfo("Searching for the 'Claim All' button.");
		result = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.DAILY_MISSION_CLAIMALL_BUTTON,
				90);

		if (result.isFound()) {
			logInfo("'Claim All' button found. Claiming daily mission rewards.");
			emuManager.tapAtPoint(EMULATOR_NUMBER, result.getPoint());
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(10, 100), new DTOPoint(600, 120), 20, 150);
		} else {
			logWarning("'Claim All' button not found. Attempting to claim missions individually.");
			while ((result = emuManager.searchTemplate(EMULATOR_NUMBER,
					EnumTemplates.DAILY_MISSION_CLAIM_BUTTON, 90)).isFound()) {

				logInfo("Claim button found. Claiming reward.");

				emuManager.tapAtPoint(EMULATOR_NUMBER, result.getPoint());
				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(10, 100), new DTOPoint(600, 120), 10, 150);
				sleepTask(500);
			}
			logInfo("No more claim buttons found. All available missions claimed.");
		}
		tapBackButton();
		sleepTask(500);

		this.setRecurring(!profile.getConfig(EnumConfigurationKey.DAILY_MISSION_AUTO_SCHEDULE_BOOL,Boolean.class));

		if (recurring) {
			Integer minutes = profile.getConfig(EnumConfigurationKey.DAILY_MISSION_OFFSET_INT, Integer.class);
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime proposedSchedule = now.plusMinutes(minutes);
			LocalDateTime resetTime = UtilTime.getGameReset();
			LocalDateTime fiveMinutesBeforeReset = resetTime.minusMinutes(5);
			LocalDateTime nextSchedule;
			
			// If we're too close to reset (less than 6 minutes away), schedule for configured offset
			if (now.plusMinutes(6).isAfter(resetTime)) {
				nextSchedule = proposedSchedule;
			} else {
				// Normal scheduling logic
				nextSchedule = UtilTime.ensureBeforeGameReset(proposedSchedule);
				
				if (nextSchedule.isBefore(now) || nextSchedule.isAfter(resetTime)) {
					// If the time is in the past or after reset, schedule for this reset
					nextSchedule = fiveMinutesBeforeReset;
				}
			}
			
			this.reschedule(nextSchedule);
			logInfo("Daily mission task completed. Next execution scheduled for " + nextSchedule);
		} else {
			this.reschedule(LocalDateTime.now().plusMinutes(30));
			logInfo("Daily mission task completed. Auto-scheduling is disabled. A safety reschedule is set for 30 minutes from now.");
		}


	}

}
