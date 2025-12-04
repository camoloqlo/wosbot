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
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

/**
 * Task responsible for claiming daily mission rewards.
 * 
 * <p>
 * This task handles two modes of operation:
 * <ul>
 * <li><b>Auto-Schedule Mode (Enabled):</b> Task is triggered by other tasks
 * that
 * provide daily mission progress. Runs once per trigger, non-recurring.</li>
 * <li><b>Manual Schedule Mode (Disabled):</b> Task runs on its own recurring
 * schedule
 * based on configured offset, checking periodically and finally right before
 * reset.</li>
 * </ul>
 * 
 * <p>
 * <b>Claiming Strategy:</b>
 * <ol>
 * <li>Open daily missions interface</li>
 * <li>Switch to daily missions tab if needed</li>
 * <li>Try to claim all rewards at once via "Claim All" button</li>
 * <li>If "Claim All" not available, claim individually</li>
 * <li>Dismiss reward popups</li>
 * </ol>
 * 
 * <p>
 * <b>Scheduling Logic (Manual Mode):</b>
 * Runs at configured offset intervals until close to game reset, then schedules
 * final check at 2 minutes before reset to ensure all claims are collected.
 */
public class DailyMissionTask extends DelayedTask {

	// ===============================
	// CONSTANTS
	// ===============================

	private static final int FINAL_CHECK_BEFORE_RESET_MINUTES = 2;
	private static final int SAFETY_RESCHEDULE_MINUTES = 30;
	private static final int POPUP_DISMISS_TAP_COUNT = 3;

	private static final DTOPoint DAILY_MISSIONS_BUTTON = new DTOPoint(50, 1050);

	private static final DTOPoint POPUP_DISMISS_MIN = new DTOPoint(10, 100);
	private static final DTOPoint POPUP_DISMISS_MAX = new DTOPoint(600, 120);

	// ===============================
	// FIELDS
	// ===============================

	private boolean autoScheduleEnabled;
	private int checkOffsetMinutes;

	// ===============================
	// CONSTRUCTOR
	// ===============================

	/**
	 * Constructs a new DailyMissionTask.
	 * 
	 * @param profile      The game profile this task operates on
	 * @param dailyMission The task type enum from the daily task registry
	 */
	public DailyMissionTask(DTOProfiles profile, TpDailyTaskEnum dailyMission) {
		super(profile, dailyMission);
	}

	// ===============================
	// MAIN EXECUTION
	// ===============================

	/**
	 * Main execution method for the daily mission task.
	 * 
	 * <p>
	 * <b>Execution Flow:</b>
	 * <ol>
	 * <li>Load configuration</li>
	 * <li>Navigate to daily missions interface</li>
	 * <li>Switch to daily missions tab if needed</li>
	 * <li>Claim all rewards (bulk or individual)</li>
	 * <li>Close interface</li>
	 * <li>Configure recurring behavior based on auto-schedule setting</li>
	 * <li>Calculate and reschedule next execution</li>
	 * </ol>
	 * 
	 * <p>
	 * All execution paths ensure the task is properly rescheduled.
	 */
	@Override
	protected void execute() {
		logInfo("Starting daily mission task");

		loadTaskConfiguration();
		navigateToDailyMissions();
		switchToDailyMissionsTab();
		claimAllRewards();
		closeInterface();

		configureRecurringBehavior();
		scheduleNextExecution();
	}

	/**
	 * Loads task configuration from the profile.
	 * 
	 * <p>
	 * Configuration loaded:
	 * <ul>
	 * <li><b>DAILY_MISSION_AUTO_SCHEDULE_BOOL:</b> Whether other tasks trigger this
	 * task</li>
	 * <li><b>DAILY_MISSION_OFFSET_INT:</b> Minutes between checks in manual
	 * mode</li>
	 * </ul>
	 */
	private void loadTaskConfiguration() {
		this.autoScheduleEnabled = profile.getConfig(
				EnumConfigurationKey.DAILY_MISSION_AUTO_SCHEDULE_BOOL,
				Boolean.class);

		this.checkOffsetMinutes = profile.getConfig(
				EnumConfigurationKey.DAILY_MISSION_OFFSET_INT,
				Integer.class);

		logInfo(String.format("Configuration - Auto-schedule: %s, Check offset: %d minutes",
				autoScheduleEnabled, checkOffsetMinutes));
	}

	// ===============================
	// NAVIGATION
	// ===============================

	/**
	 * Navigates to the daily missions interface.
	 * 
	 * <p>
	 * Taps the daily missions button and waits for interface to load.
	 */
	private void navigateToDailyMissions() {
		logInfo("Navigating to daily missions interface");

		tapPoint(DAILY_MISSIONS_BUTTON);
		sleepTask(3000); // Wait for daily missions interface to fully load
	}

	/**
	 * Switches to the daily missions tab if not already there.
	 * 
	 * <p>
	 * Searches for the daily missions tab button. If found, taps it.
	 * If not found, assumes we're already on the correct tab.
	 */
	private void switchToDailyMissionsTab() {
		DTOImageSearchResult dailyTabResult = templateSearchHelper.searchTemplate(
				EnumTemplates.DAILY_MISSION_DAILY_TAB,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (dailyTabResult.isFound()) {
			logInfo("Switching to daily missions tab");
			tapPoint(dailyTabResult.getPoint());
			sleepTask(500); // Wait for tab to switch
		} else {
			logDebug("Daily tab not found - may already be on correct tab");
		}
	}

	/**
	 * Closes the daily missions interface.
	 * 
	 * <p>
	 * Taps back button to return to previous screen quickly,
	 * allowing framework to handle final navigation to home.
	 */
	private void closeInterface() {
		tapBackButton();
		sleepTask(500); // Brief pause before next operations
	}

	// ===============================
	// REWARD CLAIMING
	// ===============================

	/**
	 * Claims all available daily mission rewards.
	 * 
	 * <p>
	 * <b>Strategy:</b>
	 * <ol>
	 * <li>Try to use "Claim All" button for efficiency</li>
	 * <li>If not available, claim rewards individually</li>
	 * </ol>
	 */
	private void claimAllRewards() {
		logInfo("Searching for claim buttons");

		DTOImageSearchResult claimAllResult = searchForClaimAllButton();

		if (claimAllResult.isFound()) {
			claimAllRewardsAtOnce(claimAllResult);
		} else {
			claimRewardsIndividually();
		}
	}

	/**
	 * Searches for the "Claim All" button.
	 * 
	 * @return Search result for the "Claim All" button
	 */
	private DTOImageSearchResult searchForClaimAllButton() {
		return templateSearchHelper.searchTemplate(
				EnumTemplates.DAILY_MISSION_CLAIMALL_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);
	}

	/**
	 * Claims all rewards at once using the "Claim All" button.
	 * 
	 * @param claimAllResult Search result containing button location
	 */
	private void claimAllRewardsAtOnce(DTOImageSearchResult claimAllResult) {
		logInfo("'Claim All' button found. Claiming all rewards at once");

		tapPoint(claimAllResult.getPoint());
		dismissRewardPopups();
	}

	/**
	 * Claims rewards individually when "Claim All" is not available.
	 * 
	 * <p>
	 * Searches for and taps individual claim buttons until none remain.
	 * The button disappears after claiming, so loop naturally exits.
	 */
	private void claimRewardsIndividually() {
		logWarning("'Claim All' button not found. Claiming missions individually");

		DTOImageSearchResult claimResult;
		int claimedCount = 0;

		while ((claimResult = searchForIndividualClaimButton()).isFound()) {
			claimedCount++;
			logDebug("Claiming individual reward #" + claimedCount);

			tapPoint(claimResult.getPoint());
			dismissRewardPopups();
			sleepTask(500); // Wait for claim to process and button to update
		}

		logInfo("Individual claiming complete. Claimed " + claimedCount + " rewards");
	}

	/**
	 * Searches for an individual claim button.
	 * 
	 * @return Search result for individual claim button
	 */
	private DTOImageSearchResult searchForIndividualClaimButton() {
		return templateSearchHelper.searchTemplate(
				EnumTemplates.DAILY_MISSION_CLAIM_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);
	}

	/**
	 * Dismisses reward popup animations by tapping the top of the screen.
	 * 
	 * <p>
	 * Taps multiple times to ensure all popups and animations are cleared.
	 * Uses random points within the dismiss area to simulate natural interaction.
	 */
	private void dismissRewardPopups() {
		tapRandomPoint(
				POPUP_DISMISS_MIN,
				POPUP_DISMISS_MAX,
				POPUP_DISMISS_TAP_COUNT,
				150 // Delay between taps
		);
	}

	// ===============================
	// SCHEDULING LOGIC
	// ===============================

	/**
	 * Configures the recurring behavior based on auto-schedule setting.
	 * 
	 * <p>
	 * <b>Logic:</b>
	 * <ul>
	 * <li>If auto-schedule ENABLED: Task is triggered by other tasks →
	 * non-recurring</li>
	 * <li>If auto-schedule DISABLED: Task self-schedules → recurring</li>
	 * </ul>
	 * 
	 * <p>
	 * This inverted logic ensures tasks don't conflict with each other.
	 */
	private void configureRecurringBehavior() {
		boolean shouldRecur = !autoScheduleEnabled;
		setRecurring(shouldRecur);

		logInfo(String.format("Task recurring: %s (auto-schedule: %s)",
				shouldRecur, autoScheduleEnabled));
	}

	/**
	 * Schedules the next execution based on current mode and time.
	 * 
	 * <p>
	 * <b>Scheduling Modes:</b>
	 * <ul>
	 * <li><b>Manual Mode (recurring):</b> Schedule based on offset, with final
	 * check before reset</li>
	 * <li><b>Auto Mode (non-recurring):</b> Safety reschedule in 30 minutes</li>
	 * </ul>
	 */
	private void scheduleNextExecution() {
		if (isRecurring()) {
			scheduleManualMode();
		} else {
			scheduleAutoMode();
		}
	}

	/**
	 * Schedules next execution for manual mode (recurring).
	 * 
	 * <p>
	 * <b>Strategy:</b>
	 * <ol>
	 * <li>Calculate proposed time: now + offset</li>
	 * <li>If the next scheduled time would be after reset: Schedule final check
	 * before reset</li>
	 * <li>Otherwise: Schedule at offset time (capped at 2min before reset while
	 * still
	 * before the final check window)</li>
	 * </ol>
	 */
	private void scheduleManualMode() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime gameReset = UtilTime.getGameReset();
		LocalDateTime proposedTime = now.plusMinutes(checkOffsetMinutes);
		LocalDateTime finalCheckTime = gameReset.minusMinutes(FINAL_CHECK_BEFORE_RESET_MINUTES);
		boolean beforeFinalCheckWindow = now.isBefore(finalCheckTime);

		LocalDateTime nextExecution;

		if (beforeFinalCheckWindow && proposedTime.isAfter(gameReset)) {
			nextExecution = scheduleFinalCheckBeforeReset(gameReset);
		} else {
			nextExecution = scheduleAtOffsetTime(proposedTime, gameReset, beforeFinalCheckWindow);
		}

		reschedule(nextExecution);
		logInfo("Next execution scheduled for: " + nextExecution.format(DATETIME_FORMATTER) +
				" (Manual mode)");
	}

	/**
	 * Schedules final check before game reset.
	 * 
	 * <p>
	 * This ensures all daily missions are claimed before reset.
	 * 
	 * @param gameReset Game reset time
	 * @return Time for final check (2 minutes before reset)
	 */
	private LocalDateTime scheduleFinalCheckBeforeReset(LocalDateTime gameReset) {
		LocalDateTime finalCheck = gameReset.minusMinutes(FINAL_CHECK_BEFORE_RESET_MINUTES);
		logInfo("Scheduling final check before reset at: " +
				finalCheck.format(DATETIME_FORMATTER));
		return finalCheck;
	}

	/**
	 * Schedules at configured offset time, capped at 2 minutes before reset.
	 * 
	 * @param proposedTime           Proposed time (now + offset)
	 * @param gameReset              Game reset time
	 * @param beforeFinalCheckWindow Whether current time is still before the final
	 *                               check window
	 * @return Scheduled time (capped if necessary)
	 */
	private LocalDateTime scheduleAtOffsetTime(LocalDateTime proposedTime, LocalDateTime gameReset,
			boolean beforeFinalCheckWindow) {
		LocalDateTime cappedTime = gameReset.minusMinutes(FINAL_CHECK_BEFORE_RESET_MINUTES);

		if (beforeFinalCheckWindow && proposedTime.isAfter(cappedTime)) {
			logInfo("Proposed time exceeds reset window. Capping at: " +
					cappedTime.format(DATETIME_FORMATTER));
			return cappedTime;
		}

		return proposedTime;
	}

	/**
	 * Schedules safety check for auto mode (non-recurring).
	 * 
	 * <p>
	 * This safety reschedule allows the task to be manually triggered
	 * from the UI if needed, even though it's not recurring.
	 */
	private void scheduleAutoMode() {
		LocalDateTime safetyTime = LocalDateTime.now().plusMinutes(SAFETY_RESCHEDULE_MINUTES);
		reschedule(safetyTime);

		logInfo("Auto-schedule mode - safety reschedule at: " +
				safetyTime.format(DATETIME_FORMATTER));
	}

	// ===============================
	// TASK FRAMEWORK OVERRIDES
	// ===============================

	/**
	 * Specifies the required starting screen location for this task.
	 * 
	 * <p>
	 * This task can start from any screen as it performs its own navigation.
	 * 
	 * @return ANY as the required start location
	 */
	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.ANY;
	}
}
