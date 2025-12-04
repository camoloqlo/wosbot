package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

/**
 * Task responsible for activating Chief Orders in the game.
 * 
 * <p>
 * Chief Orders are special time-limited buffs that provide various benefits
 * to the player's settlement. This task automates the activation of different
 * order types based on their individual cooldown periods.
 * 
 * <p>
 * <b>Available Chief Orders:</b>
 * <ul>
 * <li><b>Rush Job</b>: Speeds up construction/upgrades (24-hour cooldown)</li>
 * <li><b>Urgent Mobilization</b>: Boosts troop training (8-hour cooldown)</li>
 * <li><b>Productivity Day</b>: Increases resource production (12-hour
 * cooldown)</li>
 * </ul>
 * 
 * <p>
 * <b>Execution Flow:</b>
 * <ol>
 * <li>Navigate to Chief Order menu from HOME screen</li>
 * <li>Select the specific order type</li>
 * <li>Tap Enact button to activate the order</li>
 * <li>Reschedule based on order's cooldown period</li>
 * </ol>
 * 
 * <p>
 * <b>Scheduling:</b>
 * <ul>
 * <li>Success: Reschedules based on order type cooldown (8/12/24 hours)</li>
 * <li>Failure: Retries after 10 minutes</li>
 * </ul>
 */
public class ChiefOrderTask extends DelayedTask {

	/**
	 * Enumeration of available Chief Order types with their properties.
	 * 
	 * <p>
	 * Each order type has:
	 * <ul>
	 * <li>A descriptive name for logging</li>
	 * <li>A template enum for UI detection</li>
	 * <li>A cooldown period in hours before it can be activated again</li>
	 * </ul>
	 */
	public enum ChiefOrderType {
		/** Rush Job - Speeds up construction/upgrades (24-hour cooldown) */
		RUSH_JOB("Rush Job", EnumTemplates.CHIEF_ORDER_RUSH_JOB, 24),

		/** Urgent Mobilization - Boosts troop training (8-hour cooldown) */
		URGENT_MOBILIZATION("Urgent Mobilization", EnumTemplates.CHIEF_ORDER_URGENT_MOBILISATION, 8),

		/** Productivity Day - Increases resource production (12-hour cooldown) */
		PRODUCTIVITY_DAY("Productivity Day", EnumTemplates.CHIEF_ORDER_PRODUCTIVITY_DAY, 12);

		private final String description;
		private final EnumTemplates template;
		private final int cooldownHours;

		ChiefOrderType(String description, EnumTemplates template, int cooldownHours) {
			this.description = description;
			this.template = template;
			this.cooldownHours = cooldownHours;
		}

		public String getDescription() {
			return description;
		}

		public EnumTemplates getTemplate() {
			return template;
		}

		public int getCooldownHours() {
			return cooldownHours;
		}
	}

	// ========================================================================
	// CONFIGURATION CONSTANTS
	// ========================================================================

	private static final int ERROR_RETRY_MINUTES = 10;

	// ========================================================================
	// INSTANCE FIELDS
	// ========================================================================

	private final ChiefOrderType chiefOrderType;

	/**
	 * Constructs a new Chief Order task.
	 * 
	 * @param profile        The profile to execute this task for
	 * @param tpTask         The task type enum
	 * @param chiefOrderType The specific chief order type to activate
	 */
	public ChiefOrderTask(DTOProfiles profile, TpDailyTaskEnum tpTask, ChiefOrderType chiefOrderType) {
		super(profile, tpTask);
		this.chiefOrderType = chiefOrderType;
	}

	/**
	 * Indicates that this task must start from the HOME screen.
	 * 
	 * @return {@link EnumStartLocation#HOME}
	 */
	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

	/**
	 * Executes the Chief Order activation task.
	 * 
	 * <p>
	 * <b>Execution Flow:</b>
	 * <ol>
	 * <li>Open Chief Order menu</li>
	 * <li>Select specific order type</li>
	 * <li>Enact the order</li>
	 * <li>Schedule next execution</li>
	 * </ol>
	 * 
	 * <p>
	 * On any failure, the task reschedules for retry after 10 minutes.
	 */
	@Override
	protected void execute() {
		logInfo("Starting Chief Order task: " + chiefOrderType.getDescription() +
				" (Cooldown: " + chiefOrderType.getCooldownHours() + " hours)");

		if (!openChiefOrderMenu()) {
			handleTaskFailure("Failed to open Chief Order menu");
			return;
		}

		if (!selectOrderType()) {
			handleTaskFailure("Order type not available (likely on cooldown)");
			return;
		}

		if (!enactOrder()) {
			handleTaskFailure("Failed to enact order");
			return;
		}

		scheduleNextRun();
	}

	/**
	 * Opens the Chief Order menu from the HOME screen.
	 * 
	 * <p>
	 * <b>Navigation Steps:</b>
	 * <ol>
	 * <li>Search for Chief Order menu button</li>
	 * <li>Tap button to open menu</li>
	 * <li>Wait for menu to fully load</li>
	 * </ol>
	 * 
	 * @return true if menu was successfully opened, false otherwise
	 */
	private boolean openChiefOrderMenu() {
		logInfo("Looking for Chief Order menu access button");

		DTOImageSearchResult menuButton = templateSearchHelper.searchTemplate(
				EnumTemplates.CHIEF_ORDER_MENU_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (!menuButton.isFound()) {
			logError("Chief Order menu button not found");
			return false;
		}

		logInfo("Chief Order menu button found. Tapping to open menu");
		tapPoint(menuButton.getPoint());
		sleepTask(2000); // Wait for menu to open

		return true;
	}

	/**
	 * Selects and taps the specific chief order type in the menu.
	 * 
	 * <p>
	 * <b>Selection Process:</b>
	 * <ol>
	 * <li>Wait for menu UI to stabilize</li>
	 * <li>Search for the specific order type button</li>
	 * <li>Tap button to open order details</li>
	 * <li>Wait for details screen to load</li>
	 * </ol>
	 * 
	 * <p>
	 * If the order button is not found, it typically means the order is
	 * currently on cooldown and not yet available.
	 * 
	 * @return true if order type was found and selected, false if on cooldown
	 */
	private boolean selectOrderType() {
		sleepTask(1500); // Wait for menu UI to fully render

		logInfo("Searching for Chief Order type: " + chiefOrderType.getDescription());

		DTOImageSearchResult orderButton = templateSearchHelper.searchTemplate(
				chiefOrderType.getTemplate(),
				SearchConfigConstants.DEFAULT_SINGLE);

		if (!orderButton.isFound()) {
			logWarning(chiefOrderType.getDescription() +
					" button not found or currently on cooldown");
			return false;
		}

		logInfo(chiefOrderType.getDescription() + " button found. Tapping to activate");
		tapPoint(orderButton.getPoint());
		sleepTask(1500); // Wait for order details to open

		return true;
	}

	/**
	 * Enacts the selected chief order by tapping the Enact button.
	 * 
	 * <p>
	 * <b>Enactment Process:</b>
	 * <ol>
	 * <li>Wait for order details to fully load</li>
	 * <li>Search for Enact button</li>
	 * <li>Tap Enact button to activate order</li>
	 * <li>Wait for activation animation</li>
	 * <li>Navigate back to skip completion animation</li>
	 * </ol>
	 * 
	 * <p>
	 * If the Enact button is not found, it indicates an error state and
	 * the task should be retried.
	 * 
	 * @return true if order was successfully enacted, false otherwise
	 */
	private boolean enactOrder() {
		sleepTask(1500); // Wait for order details screen to fully load

		logInfo("Searching for Chief Order Enact button");

		DTOImageSearchResult enactButton = templateSearchHelper.searchTemplate(
				EnumTemplates.CHIEF_ORDER_ENACT_BUTTON,
				SearchConfigConstants.DEFAULT_SINGLE);

		if (!enactButton.isFound()) {
			logError("Chief Order Enact button not found");
			return false;
		}

		logInfo("Enact button found. Tapping to enact order");
		tapPoint(enactButton.getPoint());
		sleepTask(1000); // Wait for enact action to register

		// Navigate back to skip activation animation
		tapBackButton();
		sleepTask(5000); // Wait for animation to complete

		logInfo(chiefOrderType.getDescription() + " activated successfully");
		return true;
	}

	/**
	 * Schedules the next execution of this task after successful completion.
	 * 
	 * <p>
	 * The task reschedules itself based on the order type's cooldown period,
	 * which varies by order:
	 * <ul>
	 * <li>Rush Job: 24 hours</li>
	 * <li>Urgent Mobilization: 8 hours</li>
	 * <li>Productivity Day: 12 hours</li>
	 * </ul>
	 */
	private void scheduleNextRun() {
		LocalDateTime nextExecutionTime = LocalDateTime.now()
				.plusHours(chiefOrderType.getCooldownHours());

		reschedule(nextExecutionTime);

		logInfo("Task completed successfully. Next execution in " +
				chiefOrderType.getCooldownHours() + " hours");
	}

	/**
	 * Handles task failure by logging the reason and scheduling a retry.
	 * 
	 * <p>
	 * On failure, the task reschedules to retry after {@value ERROR_RETRY_MINUTES}
	 * minutes
	 * to avoid excessive retry attempts while allowing timely recovery.
	 * 
	 * <p>
	 * Common failure scenarios:
	 * <ul>
	 * <li>Menu button not found (UI issue or game state problem)</li>
	 * <li>Order type not available (on cooldown or not unlocked)</li>
	 * <li>Enact button not found (unexpected state)</li>
	 * </ul>
	 * 
	 * @param reason A descriptive reason for the task failure
	 */
	private void handleTaskFailure(String reason) {
		logWarning("Task failed: " + reason);

		LocalDateTime retryTime = LocalDateTime.now().plusMinutes(ERROR_RETRY_MINUTES);
		reschedule(retryTime);

		logInfo("Task rescheduled to retry in " + ERROR_RETRY_MINUTES + " minutes");
	}
}