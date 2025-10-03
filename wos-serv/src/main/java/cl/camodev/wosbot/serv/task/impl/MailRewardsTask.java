package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

public class MailRewardsTask extends DelayedTask {

	private final DTOPoint[] buttons = { new DTOPoint(230, 120), new DTOPoint(360, 120), new DTOPoint(500, 120) };

	public MailRewardsTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	@Override
	protected void execute() {
		sleepTask(1000);
		logInfo("Navigating to the mail screen.");
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(640, 1033),
				new DTOPoint(686, 1064));
		sleepTask(1000);
		for (DTOPoint button : buttons) {
			// Change tabs
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, button, button);
			sleepTask(1000);

			// Claim rewards
			logInfo("Attempting to claim rewards in the current tab.");
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(420, 1227),
					new DTOPoint(450, 1250), 4, 500);
			sleepTask(500);

			// Check if there are excess unread mail
			int searchAttempts = 0;
			while (true) {
				DTOImageSearchResult unclaimedRewards = emuManager.searchTemplate(EMULATOR_NUMBER,
						EnumTemplates.MAIL_UNCLAIMED_REWARDS, 90);
				if (unclaimedRewards.isFound()) {
					
					if(searchAttempts > 0) {
						logInfo("More unread mail found. Swiping down to reveal more and claiming again.");
						
						// Swipe down 10 times
						for (int i = 0; i < 10; i++) {
							emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(40, 913), new DTOPoint(40, 400));
							sleepTask(300);
						}
					}

					// Claim rewards
					emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(420, 1227),
							new DTOPoint(450, 1250), 3, 1000);
					sleepTask(500);

					searchAttempts++;
				} else {
					break;
				}
				sleepTask(500);
			}
		}
		LocalDateTime nextSchedule = LocalDateTime.now()
				.plusMinutes(profile.getConfig(EnumConfigurationKey.MAIL_REWARDS_OFFSET_INT, Integer.class));
		this.reschedule(nextSchedule);
		logInfo("Mail rewards claimed. Rescheduling task for " + nextSchedule);
		tapBackButton();
	}

}