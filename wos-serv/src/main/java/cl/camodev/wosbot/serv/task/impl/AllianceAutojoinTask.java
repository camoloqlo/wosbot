package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.NavigationHelper;

/**
 * Task responsible for enabling and configuring auto-join for Alliance Wars.
 * 
 * <p>
 * Auto-join allows the profile to automatically participate in alliance rally
 * attacks
 * without manual intervention. This task configures:
 * <ul>
 * <li>Number of queues (1-6) - how many rallies to join simultaneously</li>
 * <li>Troop selection - use all troops or specific formation</li>
 * </ul>
 * 
 * <p>
 * <b>Timing:</b> Auto-join expires after 8 hours, so this task runs every 7h50m
 * to maintain continuous participation with a 10-minute buffer.
 * 
 * <p>
 * <b>Scheduling:</b>
 * <ul>
 * <li>Success: Reschedules for 7 hours 50 minutes later</li>
 * <li>Failure: Retries after 5 minutes</li>
 * </ul>
 * 
 * @author WoS Bot
 * @see DelayedTask
 */
public class AllianceAutojoinTask extends DelayedTask {

	// ========================================================================
	// COORDINATE CONSTANTS
	// ========================================================================

	private static final DTOArea RALLY_SECTION_TAB = new DTOArea(
			new DTOPoint(81, 114),
			new DTOPoint(195, 152));

	private static final DTOArea AUTOJOIN_SETTINGS_BUTTON = new DTOArea(
			new DTOPoint(260, 1200),
			new DTOPoint(450, 1240));

	private static final DTOPoint USE_ALL_TROOPS_BUTTON = new DTOPoint(98, 376);
	private static final DTOPoint SPECIFIC_FORMATION_BUTTON = new DTOPoint(98, 442);

	private static final DTOPoint QUEUE_COUNTER_SWIPE_START = new DTOPoint(430, 600);
	private static final DTOPoint QUEUE_COUNTER_SWIPE_END = new DTOPoint(40, 600);

	private static final DTOArea QUEUE_INCREMENT_BUTTON = new DTOArea(
			new DTOPoint(460, 590),
			new DTOPoint(497, 610));

	private static final DTOArea ENABLE_AUTOJOIN_BUTTON = new DTOArea(
			new DTOPoint(380, 1070),
			new DTOPoint(640, 1120));

	// ========================================================================
	// CONFIGURATION CONSTANTS
	// ========================================================================

	private static final int MIN_QUEUE_COUNT = 1;
	private static final int MAX_QUEUE_COUNT = 6;
	private static final int DEFAULT_QUEUE_COUNT = 3;
	private static final int SCHEDULE_HOURS = 7;
	private static final int SCHEDULE_MINUTES = 50;

	// ========================================================================
	// INSTANCE FIELDS
	// ========================================================================

	private boolean useAllTroops;
	private int queueCount;

	/**
	 * Constructs a new Alliance Auto-Join task.
	 * 
	 * @param profile The profile to execute this task for
	 * @param tpTask  The task type enum
	 */
	public AllianceAutojoinTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	/**
	 * Loads task configuration from the profile.
	 * 
	 * <p>
	 * Configuration keys used:
	 * <ul>
	 * <li>{@link EnumConfigurationKey#ALLIANCE_AUTOJOIN_USE_ALL_TROOPS_BOOL}</li>
	 * <li>{@link EnumConfigurationKey#ALLIANCE_AUTOJOIN_QUEUES_INT}</li>
	 * </ul>
	 * 
	 * <p>
	 * Validates queue count and falls back to default if out of range.
	 */
	private void loadConfiguration() {
		useAllTroops = profile.getConfig(
				EnumConfigurationKey.ALLIANCE_AUTOJOIN_USE_ALL_TROOPS_BOOL,
				Boolean.class);

		int rawQueueCount = profile.getConfig(
				EnumConfigurationKey.ALLIANCE_AUTOJOIN_QUEUES_INT,
				Integer.class);

		// Validate queue count is within acceptable range
		if (rawQueueCount < MIN_QUEUE_COUNT || rawQueueCount > MAX_QUEUE_COUNT) {
			logWarning("Invalid queue count configured: " + rawQueueCount +
					". Using default: " + DEFAULT_QUEUE_COUNT);
			queueCount = DEFAULT_QUEUE_COUNT;
		} else {
			queueCount = rawQueueCount;
		}

		logInfo("Configuration loaded - Use all troops: " + useAllTroops +
				", Queue count: " + queueCount);
	}

	/**
	 * Executes the Alliance Auto-Join configuration task.
	 * 
	 * <p>
	 * <b>Execution Flow:</b>
	 * <ol>
	 * <li>Load configuration from profile</li>
	 * <li>Navigate to Alliance War menu</li>
	 * <li>Open auto-join settings popup</li>
	 * <li>Configure troop selection (all troops vs formation)</li>
	 * <li>Set number of queues to join</li>
	 * <li>Enable auto-join</li>
	 * <li>Schedule next execution</li>
	 * </ol>
	 * 
	 * <p>
	 * On failure at any step, task is rescheduled for retry after 5 minutes.
	 */
	@Override
	protected void execute() {
		logInfo("Starting Alliance auto-join configuration");

		loadConfiguration();

		if (!openAllianceWarMenu()) {
			handleTaskFailure("Failed to open Alliance War menu");
			return;
		}

		if (!openAutoJoinSettings()) {
			handleTaskFailure("Failed to open auto-join settings");
			return;
		}

		configureTroopSelection();
		setAutoJoinQueues(queueCount);
		enableAutoJoin();

		scheduleNextRun();
	}

	/**
	 * Opens the Alliance War menu using the navigation helper.
	 * 
	 * <p>
	 * Uses
	 * {@link NavigationHelper#navigateToAllianceMenu(NavigationHelper.AllianceMenu)}
	 * to handle navigation to the Alliance screen and locating the War button.
	 * 
	 * @return true if Alliance War menu was successfully opened, false otherwise
	 */
	private boolean openAllianceWarMenu() {
		logDebug("Opening Alliance War menu");

		boolean success = navigationHelper.navigateToAllianceMenu(NavigationHelper.AllianceMenu.WAR);

		if (success) {
			sleepTask(1000); // Wait for Alliance War screen to load
			logDebug("Alliance War menu opened successfully");
		} else {
			logError("Failed to navigate to Alliance War menu");
		}

		return success;
	}

	/**
	 * Opens the auto-join settings popup from the Alliance War menu.
	 * 
	 * <p>
	 * <b>Navigation Steps:</b>
	 * <ol>
	 * <li>Tap rally section tab</li>
	 * <li>Tap auto-join settings button</li>
	 * <li>Wait for popup to appear</li>
	 * </ol>
	 * 
	 * @return true if auto-join settings popup was opened, false otherwise
	 */
	private boolean openAutoJoinSettings() {
		logDebug("Opening rally section");
		tapRandomPoint(RALLY_SECTION_TAB.topLeft(), RALLY_SECTION_TAB.bottomRight());
		sleepTask(500); // Wait for rally section to load

		logDebug("Opening auto-join settings popup");
		tapRandomPoint(AUTOJOIN_SETTINGS_BUTTON.topLeft(), AUTOJOIN_SETTINGS_BUTTON.bottomRight());
		sleepTask(1500); // Wait for popup to appear and load

		// Assume success since there's no reliable way to verify popup state
		logDebug("Auto-join settings popup should be open");
		return true;
	}

	/**
	 * Configures troop selection based on user preference.
	 * 
	 * <p>
	 * Two options:
	 * <ul>
	 * <li><b>Use all troops:</b> Automatically sends all available troops</li>
	 * <li><b>Specific formation:</b> Uses a pre-configured formation</li>
	 * </ul>
	 * 
	 * <p>
	 * Note: A default formation always exists even if user hasn't configured one.
	 */
	private void configureTroopSelection() {
		if (useAllTroops) {
			logInfo("Selecting 'Use all troops' option");
			tapPoint(USE_ALL_TROOPS_BUTTON);
		} else {
			logInfo("Selecting 'Specific formation' option");
			tapPoint(SPECIFIC_FORMATION_BUTTON);
		}
		sleepTask(700); // Wait for selection to register
	}

	/**
	 * Sets the number of rally queues to join automatically.
	 * 
	 * <p>
	 * <b>Configuration Process:</b>
	 * <ol>
	 * <li>Reset counter to zero via swipe gesture</li>
	 * <li>Increment counter (count - 1) times to reach desired value</li>
	 * </ol>
	 * 
	 * <p>
	 * The swipe gesture is always reliable for resetting the counter.
	 * 
	 * @param count The number of queues to join (1-6)
	 */
	private void setAutoJoinQueues(int count) {
		logInfo("Setting auto-join queue count to " + count);

		// Reset queue counter to zero
		logDebug("Resetting queue counter to zero");
		swipe(QUEUE_COUNTER_SWIPE_START, QUEUE_COUNTER_SWIPE_END);
		sleepTask(300); // Wait for swipe animation to complete

		// Increment to desired count (tap count-1 times since we start at 1)
		if (count > 1) {
			logDebug("Incrementing queue counter " + (count - 1) + " times");
			tapRandomPoint(
					QUEUE_INCREMENT_BUTTON.topLeft(),
					QUEUE_INCREMENT_BUTTON.bottomRight(),
					(count - 1),
					400 // Delay between taps
			);
			sleepTask(300); // Wait for final increment to register
		}

		logDebug("Queue count set to " + count);
	}

	/**
	 * Enables the auto-join feature by tapping the confirmation button.
	 * 
	 * <p>
	 * This is the final step that activates auto-join with the configured settings.
	 * The action is expected to always succeed.
	 */
	private void enableAutoJoin() {
		logInfo("Enabling auto-join");
		tapRandomPoint(ENABLE_AUTOJOIN_BUTTON.topLeft(), ENABLE_AUTOJOIN_BUTTON.bottomRight());
		sleepTask(500); // Wait for activation to register
		logDebug("Auto-join activation command sent");
	}

	/**
	 * Schedules the next execution of this task after successful completion.
	 * 
	 * <p>
	 * Alliance auto-join expires after 8 hours. This task reschedules itself
	 * for 7 hours and 50 minutes later, providing a 10-minute buffer before
	 * expiration.
	 */
	private void scheduleNextRun() {
		LocalDateTime nextExecutionTime = LocalDateTime.now()
				.plusHours(SCHEDULE_HOURS)
				.plusMinutes(SCHEDULE_MINUTES);

		reschedule(nextExecutionTime);

		logInfo("Alliance auto-join configured successfully. Next execution in " +
				SCHEDULE_HOURS + "h " + SCHEDULE_MINUTES + "m");
	}

	/**
	 * Handles task failure by logging the reason and scheduling a retry.
	 * 
	 * <p>
	 * On failure, the task is rescheduled to retry after 5 minutes.
	 * No cleanup is needed as {@link DelayedTask} automatically handles
	 * returning to the correct screen location after task completion.
	 * 
	 * @param reason A descriptive reason for the task failure
	 */
	private void handleTaskFailure(String reason) {
		logWarning("Task failed: " + reason);

		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(5);
		reschedule(retryTime);

		logInfo("Task rescheduled to retry in 5 minutes");
	}

	/**
	 * Indicates that this task can start from any screen location.
	 * 
	 * <p>
	 * The task will navigate to the Alliance screen regardless of starting
	 * position.
	 * 
	 * @return {@link EnumStartLocation#ANY}
	 */
	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.ANY;
	}
}