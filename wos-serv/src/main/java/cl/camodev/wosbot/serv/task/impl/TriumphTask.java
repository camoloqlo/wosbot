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
import cl.camodev.wosbot.serv.task.EnumStartLocation;

/**
 * Task responsible for claiming daily and weekly alliance triumph rewards.
 * The task checks if rewards are available and claims them accordingly.
 * If rewards are not available, it reschedules itself based on configuration.
 */
public class TriumphTask extends DelayedTask {

	public TriumphTask(DTOProfiles profile, TpDailyTaskEnum dailyMission) {
		super(profile, dailyMission);
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

	@Override
	protected void execute() {
		logInfo("Starting Alliance Triumph task to claim rewards");
		
		// Navigate to alliance menu
		logInfo("Tapping alliance button at bottom of screen");
		emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(493, 1187), new DTOPoint(561, 1240));
		sleepTask(3000);

		// Search for the Triumph button
		DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER,
				EnumTemplates.ALLIANCE_TRIUMPH_BUTTON, 90);
		if (result.isFound()) {
			logInfo("Alliance Triumph button found. Tapping to open the menu.");
			emuManager.tapAtPoint(EMULATOR_NUMBER, result.getPoint());
			sleepTask(2000);

			logInfo("Checking daily Triumph rewards status");
			// Check if daily rewards have already been claimed
			result = emuManager.searchTemplate(EMULATOR_NUMBER,
					EnumTemplates.ALLIANCE_TRIUMPH_DAILY_CLAIMED, 90);

			if (result.isFound()) {
				logInfo("Daily Triumph rewards already claimed - rescheduling for next game reset");
				this.reschedule(UtilTime.getGameReset());
			} else {
				// Check if daily rewards are ready to claim
				logInfo("Daily rewards not claimed yet, checking if they are available");
				result = emuManager.searchTemplate(EMULATOR_NUMBER, 
				        EnumTemplates.ALLIANCE_TRIUMPH_DAILY, 90);
				        
				if (result.isFound()) {
					logInfo("Daily Triumph rewards are available - claiming now");
					emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint(), 10, 50);
					sleepTask(1000); // Add delay after claiming to ensure UI updates
					logInfo("Daily rewards claimed successfully");
					reschedule(UtilTime.getGameReset());
				} else {
					// Rewards not ready yet
					int offset = profile.getConfig(EnumConfigurationKey.ALLIANCE_TRIUMPH_OFFSET_INT, Integer.class);
					logWarning("Daily Triumph rewards not available - rescheduling to check in " + offset + " minutes");
					reschedule(LocalDateTime.now().plusMinutes(offset));
				}
			}

			// Check for weekly rewards
			logInfo("Checking weekly Triumph rewards status");
			result = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.ALLIANCE_TRIUMPH_WEEKLY, 90);

			if (result.isFound()) {
				logInfo("Weekly Triumph rewards are available - claiming now");
				emuManager.tapAtPoint(EMULATOR_NUMBER, result.getPoint());
				sleepTask(1500); // Increased delay to ensure reward animation completes
				tapBackButton();
				logInfo("Weekly Triumph claimed successfully");
			} else {
				logInfo("Weekly Triumph rewards not available or already claimed");
			}

			// Return to home screen
			logDebug("Returning to home screen");
			tapBackButton();
			sleepTask(300);
			tapBackButton();
		} else {
			int offset = profile.getConfig(EnumConfigurationKey.ALLIANCE_TRIUMPH_OFFSET_INT, Integer.class);
			logError("Alliance Triumph button not found - unable to claim rewards");
			logInfo("Rescheduling task to try again in " + offset + " minutes");
			tapBackButton();
			sleepTask(500);
			reschedule(LocalDateTime.now().plusMinutes(offset));
		}
		
		logInfo("Alliance Triumph task completed");
	}

}
