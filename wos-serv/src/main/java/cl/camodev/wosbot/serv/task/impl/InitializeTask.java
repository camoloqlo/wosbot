package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.ProfileInReconnectStateException;
import cl.camodev.wosbot.ex.StopExecutionException;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

/**
 * Initialize task that starts the bot and prepares the game for automation.
 * 
 * <p>
 * This task is the first task executed when the bot starts and performs
 * critical initialization:
 * <ul>
 * <li>Ensures the emulator is running (launches if needed)</li>
 * <li>Verifies Whiteout Survival is installed</li>
 * <li>Launches the game if not already running</li>
 * <li>Waits for home or world screen to appear</li>
 * <li>Reads initial stamina value from profile</li>
 * </ul>
 * 
 * <p>
 * <b>Unique Behavior:</b>
 * <ul>
 * <li>This task does NOT reschedule after execution</li>
 * <li>Sets recurring=false on start to prevent re-execution</li>
 * <li>Exception on failure: Sets recurring=true to retry immediately</li>
 * <li>Exception on success: Task completes without rescheduling</li>
 * </ul>
 * 
 * <p>
 * <b>Error Handling:</b>
 * <ul>
 * <li>Game not installed: Throws StopExecutionException (stops queue)</li>
 * <li>Reconnect state detected: Throws ProfileInReconnectStateException</li>
 * <li>Home screen not found: Restarts emulator and sets recurring=true
 * (retry)</li>
 * </ul>
 * 
 * <p>
 * <b>State Management:</b>
 * The {@code isStarted} field is instance state that persists between
 * executions.
 * This is intentional - if the task retries (recurring=true), it will re-check
 * emulator status but maintain this flag across retry attempts.
 */
public class InitializeTask extends DelayedTask {

	// ========== Home Screen Detection Constants ==========
	private static final int MAX_HOME_SCREEN_ATTEMPTS = 10;

	// ========== Instance State ==========
	/**
	 * Tracks whether the emulator has been successfully started.
	 * This persists across task executions (when recurring=true triggers retry).
	 */
	boolean isStarted = false;

	/**
	 * Constructs a new InitializeTask.
	 *
	 * @param profile     the profile this task belongs to
	 * @param tpDailyTask the task type enum
	 */
	public InitializeTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	/**
	 * Main execution method for initialization.
	 * 
	 * <p>
	 * Flow:
	 * <ol>
	 * <li>Set recurring=false (one-time execution by default)</li>
	 * <li>Ensure emulator is running</li>
	 * <li>Verify game is installed</li>
	 * <li>Launch game if needed</li>
	 * <li>Wait for home/world screen</li>
	 * <li>Update initial stamina value</li>
	 * </ol>
	 * 
	 * <p>
	 * <b>No Reschedule:</b>
	 * This task intentionally does not call reschedule(). It either:
	 * <ul>
	 * <li>Completes successfully (recurring=false, task stops)</li>
	 * <li>Fails and sets recurring=true (immediate retry)</li>
	 * <li>Throws exception (queue handles appropriately)</li>
	 * </ul>
	 * 
	 * @throws StopExecutionException           if game is not installed
	 * @throws ProfileInReconnectStateException if profile needs reconnection
	 */
	@Override
	protected void execute() {
		setRecurring(false);
		logInfo("Starting initialization task...");

		ensureEmulatorRunning();
		ensureGameInstalled();
		ensureGameRunning();
		waitForHomeScreen();
	}

	/**
	 * Ensures the emulator is running, launching it if necessary.
	 * 
	 * <p>
	 * This method loops until the emulator is confirmed running.
	 * If not running, it attempts to launch and waits before checking again.
	 * 
	 * <p>
	 * The {@code isStarted} flag prevents redundant checks on subsequent
	 * calls within the same execution.
	 */
	private void ensureEmulatorRunning() {
		logInfo("Checking emulator status...");

		while (!isStarted) {
			if (emuManager.isRunning(EMULATOR_NUMBER)) {
				isStarted = true;
				logInfo("Emulator is running.");
			} else {
				logInfo("Emulator not found. Attempting to start it...");
				emuManager.launchEmulator(EMULATOR_NUMBER);
				logInfo("Waiting 10 seconds before checking again.");
				sleepTask(10000); // Wait for emulator to start
			}
		}
	}

	/**
	 * Verifies that Whiteout Survival is installed on the emulator.
	 * 
	 * <p>
	 * If the game is not installed, throws StopExecutionException to halt
	 * the task queue, as automation cannot proceed without the game.
	 * 
	 * @throws StopExecutionException if game is not installed
	 */
	private void ensureGameInstalled() {
		if (!emuManager.isWhiteoutSurvivalInstalled(EMULATOR_NUMBER)) {
			logError("Whiteout Survival is not installed. Stopping the task queue.");
			throw new StopExecutionException("Game not installed");
		}
	}

	/**
	 * Ensures the game is running, launching it if necessary.
	 * 
	 * <p>
	 * Checks if Whiteout Survival is currently running. If not, launches
	 * the game and waits for it to start.
	 */
	private void ensureGameRunning() {
		if (!emuManager.isPackageRunning(EMULATOR_NUMBER, EmulatorManager.GAME.getPackageName())) {
			logInfo("Whiteout Survival is not running. Launching the game...");
			emuManager.launchApp(EMULATOR_NUMBER, EmulatorManager.GAME.getPackageName());
			sleepTask(10000); // Wait for game to launch
		} else {
			logInfo("Whiteout Survival is already running.");
		}
	}

	/**
	 * Waits for the home or world screen to appear.
	 * 
	 * <p>
	 * Continuously searches for home/world screen indicators up to
	 * MAX_HOME_SCREEN_ATTEMPTS. If a reconnect popup is detected, throws
	 * ProfileInReconnectStateException.
	 * 
	 * <p>
	 * If home screen is not found after all attempts:
	 * <ul>
	 * <li>Closes the emulator</li>
	 * <li>Resets isStarted flag</li>
	 * <li>Sets recurring=true (triggers immediate retry)</li>
	 * </ul>
	 * 
	 * <p>
	 * If home screen is found:
	 * <ul>
	 * <li>Updates initial stamina from profile</li>
	 * <li>Task completes (recurring=false, no reschedule)</li>
	 * </ul>
	 * 
	 * @throws ProfileInReconnectStateException if reconnect popup detected
	 */
	private void waitForHomeScreen() {
		int attempts = 0;
		boolean homeScreenFound = false;

		while (attempts < MAX_HOME_SCREEN_ATTEMPTS) {
			if (searchForHomeScreen()) {
				homeScreenFound = true;
				logInfo("Home screen found.");
				break;
			}

			checkForReconnectState();

			logWarning("Home screen not found. Waiting 5 seconds before retrying...");
			tapBackButton(); // Try to dismiss any overlays
			sleepTask(5000); // Wait before retry
			attempts++;
		}

		if (!homeScreenFound) {
			handleHomeScreenNotFound();
		} else {
			handleInitializationSuccess();
		}
	}

	/**
	 * Searches for home or world screen indicators.
	 * 
	 * @return true if home or world screen is found, false otherwise
	 */
	private boolean searchForHomeScreen() {
		DTOImageSearchResult home = templateSearchHelper.searchTemplate(
				EnumTemplates.GAME_HOME_FURNACE,
				SearchConfig.builder()
						.withMaxAttempts(1)
						.build());

		DTOImageSearchResult world = templateSearchHelper.searchTemplate(
				EnumTemplates.GAME_HOME_WORLD,
				SearchConfig.builder()
						.withMaxAttempts(1)
						.build());

		return home.isFound() || world.isFound();
	}

	/**
	 * Checks for reconnect state popup.
	 * 
	 * <p>
	 * If the reconnect popup is detected, throws ProfileInReconnectStateException
	 * to notify the queue that the profile needs to reconnect before automation
	 * can continue.
	 * 
	 * @throws ProfileInReconnectStateException if reconnect popup is found
	 */
	private void checkForReconnectState() {
		DTOImageSearchResult reconnect = templateSearchHelper.searchTemplate(
				EnumTemplates.GAME_HOME_RECONNECT,
				SearchConfig.builder()
						.withMaxAttempts(2)
						.build());

		if (reconnect.isFound()) {
			throw new ProfileInReconnectStateException(
					"Profile " + profile.getName() + " is in a reconnect state and cannot execute the task: "
							+ taskName);
		}
	}

	/**
	 * Handles the case where home screen was not found after all attempts.
	 * 
	 * <p>
	 * Strategy:
	 * <ol>
	 * <li>Closes the emulator (clean slate)</li>
	 * <li>Resets isStarted flag (will re-launch emulator on retry)</li>
	 * <li>Sets recurring=true (triggers immediate re-execution)</li>
	 * </ol>
	 * 
	 * <p>
	 * When the task re-executes (due to recurring=true), it will go through
	 * the full initialization flow again, including relaunching the emulator.
	 */
	private void handleHomeScreenNotFound() {
		logError("Home screen not found after multiple attempts. Restarting the emulator.");
		emuManager.closeEmulator(EMULATOR_NUMBER);
		isStarted = false;
		setRecurring(true); // Trigger immediate retry
	}

	/**
	 * Handles successful initialization.
	 * 
	 * <p>
	 * Reads the current stamina value from the profile screen and stores it
	 * in the StaminaService for use by other tasks.
	 * 
	 * <p>
	 * After this method completes, the task ends without rescheduling
	 * (recurring=false is already set at the start of execute()).
	 */
	private void handleInitializationSuccess() {
		logInfo("Initialization successful. Reading initial stamina value.");
		staminaHelper.updateStaminaFromProfile();
		logInfo("Initialization task completed successfully.");
	}

	/**
	 * Specifies that this task can start from any screen location.
	 * Since this is the first task, the screen state is unknown.
	 * 
	 * @return EnumStartLocation.ANY
	 */
	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.ANY;
	}

	/**
	 * Indicates that this task does not provide daily mission progress.
	 * 
	 * @return false
	 */
	@Override
	public boolean provideDailyMissionProgress() {
		return false;
	}
}