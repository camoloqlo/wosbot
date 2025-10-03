package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.ProfileInReconnectStateException;
import cl.camodev.wosbot.ex.StopExecutionException;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

public class InitializeTask extends DelayedTask {
	boolean isStarted = false;

	public InitializeTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	protected void execute() {
		this.setRecurring(false);
		logInfo("Starting initialization task...");
		logInfo("Checking emulator status...");
		while (!isStarted) {

			if (emuManager.isRunning(EMULATOR_NUMBER)) {
				isStarted = true;
				logInfo("Emulator is running.");
			} else {
				logInfo("Emulator not found. Attempting to start it...");
				emuManager.launchEmulator(EMULATOR_NUMBER);
				logInfo("Waiting 10 seconds before checking again.");
				sleepTask(10000);
			}

		}

		if (!emuManager.isWhiteoutSurvivalInstalled(EMULATOR_NUMBER)) {
			logError("Whiteout Survival is not installed. Stopping the task queue.");
			throw new StopExecutionException("Game not installed");
		} else {

			logInfo("Whiteout Survival is installed. Launching the game...");
			emuManager.launchApp(EMULATOR_NUMBER, EmulatorManager. GAME.getPackageName());
			sleepTask(10000);

			final int MAX_ATTEMPTS = 10;
			final int WAIT_TIME = 5000;

			boolean homeScreen = false;
			int attempts = 0;
			while (attempts <= MAX_ATTEMPTS) {
				DTOImageSearchResult home = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE, 90);
				DTOImageSearchResult world = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_WORLD, 90);

				if (home.isFound() || world.isFound()) {
					homeScreen = true;
					logInfo("Home screen found.");
					break;
				}

				DTOImageSearchResult reconnect = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_RECONNECT, 90);
				if (reconnect.isFound()) {
					throw new ProfileInReconnectStateException("Profile " + profile.getName() + " is in a reconnect state and cannot execute the task: " + taskName);
				}

				logWarning("Home screen not found. Waiting 5 seconds before retrying...");
				tapBackButton();
				sleepTask(WAIT_TIME);
				attempts++;
			}

			if (!homeScreen) {
				logError("Home screen not found after multiple attempts. Restarting the emulator.");
				emuManager.closeEmulator(EMULATOR_NUMBER);
				isStarted = false;
				this.setRecurring(true);
			}else {
                // Successfully reached home screen, reading stamina
                updateStaminaFromProfile();
            }

		}
	}


}