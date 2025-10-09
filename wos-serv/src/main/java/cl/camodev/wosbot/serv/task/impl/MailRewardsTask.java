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
		logInfo("Navigating to the mail screen.");
        DTOImageSearchResult mailMenu = searchTemplateRegionWithRetries(EnumTemplates.MAIL_MENU,
                new DTOPoint(600, 1000),
                new DTOPoint(715, 1100));
        if (!mailMenu.isFound()) {
            logError("Unable to find mail menu, trying again in an hour. ");
            this.reschedule(LocalDateTime.now().plusHours(1));
            return;
        }

        emuManager.tapAtPoint(EMULATOR_NUMBER, mailMenu.getPoint()); // open mail menu
        DTOImageSearchResult inMailMenu = searchTemplateRegionWithRetries(EnumTemplates.MAIL_MENU_OPEN,
                new DTOPoint(75, 10), new DTOPoint(175, 60),
                5, 200L
                );
        if (!inMailMenu.isFound()) {
            logError("Unable to find mail menu opened, trying again in an hour. ");
            this.reschedule(LocalDateTime.now().plusHours(1));
            return;
        }

		for (DTOPoint button : buttons) {
			// Change tabs
			emuManager.tapAtPoint(EMULATOR_NUMBER, button);
			sleepTask(200);

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
                if (!unclaimedRewards.isFound()) {
                    break;
                }
                if(searchAttempts > 0) {
                    logInfo("More unread mail found. Swiping down to reveal more and claiming again.");

                    // Swipe down 10 times
                    for (int i = 0; i < 10; i++) {
                        emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(40, 913), new DTOPoint(40, 400));
                        sleepTask(250);
                    }
                }

                // Claim rewards
                emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(420, 1227),
                        new DTOPoint(450, 1250), 4, 500);
                sleepTask(500);

                searchAttempts++;
                if (searchAttempts > 500) {
                    logError("There is absolutely no way this condition should ever be hit in a normal scenario. " +
                            "Something is broken. Either you have not checked your mail in DAYS, " +
                            "or we are stuck somewhere you shouldn't be. " +
                            "Please report to the devs which menu this was stuck on if you see this message.");
                    break;
                }
			}

		}
		LocalDateTime nextSchedule = LocalDateTime.now()
				.plusMinutes(profile.getConfig(EnumConfigurationKey.MAIL_REWARDS_OFFSET_INT, Integer.class));
		this.reschedule(nextSchedule);
		logInfo("Mail rewards claimed. Rescheduling task for " + nextSchedule);
		tapBackButton();
	}
}