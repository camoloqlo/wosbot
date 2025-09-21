package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class ChiefOrderTask extends DelayedTask {

	public enum ChiefOrderType {
	//@formatter:off
	RUSH_JOB("Rush Job", EnumTemplates.CHIEF_ORDER_RUSH_JOB, 24),
	URGENT_MOBILIZATION("Urgent Mobilization", EnumTemplates.CHIEF_ORDER_URGENT_MOBILISATION, 8),
	PRODUCTIVITY_DAY("Productivity Day", EnumTemplates.CHIEF_ORDER_PRODUCTIVITY_DAY, 12);
	//@formatter:on

		private final String description;
		private final EnumTemplates template;
		private final int cooldownHours;

		private ChiefOrderType(String description, EnumTemplates template, int cooldownHours) {
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

	private final ChiefOrderType chiefOrderType;

	public ChiefOrderTask(DTOProfiles profile, TpDailyTaskEnum tpTask, ChiefOrderType chiefOrderType) {
		super(profile, tpTask);
		this.chiefOrderType = chiefOrderType;
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}

	@Override
	protected void execute() {
		logInfo("Starting chief order task: " + chiefOrderType.getDescription() + 
				" (Cooldown: " + chiefOrderType.getCooldownHours() + " hours)");

	// Navigate to Chief Order menu (always start from HOME screen)
	logInfo("Looking for Chief Order menu access button.");
        
	// First, look for the main Chief Order menu button
		DTOImageSearchResult chiefOrderMenuButton = emuManager.searchTemplate(EMULATOR_NUMBER, 
				EnumTemplates.CHIEF_ORDER_MENU_BUTTON, 90);

		if (chiefOrderMenuButton.isFound()) {
			logInfo("Chief Order menu button found. Tapping to open menu.");
			emuManager.tapAtPoint(EMULATOR_NUMBER, chiefOrderMenuButton.getPoint());
			sleepTask(2000);

		// Wait 1.5 seconds before searching for the Chief Order type button (may not be visible immediately)
			sleepTask(1500);

		// Now search for the specific Chief Order type
		logInfo("Searching for Chief Order type: " + chiefOrderType.getDescription());
			DTOImageSearchResult specificOrderButton = emuManager.searchTemplate(EMULATOR_NUMBER, 
					chiefOrderType.getTemplate(), 90);

			if (specificOrderButton.isFound()) {
				logInfo(chiefOrderType.getDescription() + " button found. Tapping to activate.");
				emuManager.tapAtPoint(EMULATOR_NUMBER, specificOrderButton.getPoint());
				sleepTask(1500);

			// Wait 1.5 seconds before searching for the Enact button (chiefOrderEnactButton.png)
				sleepTask(1500);

			// Search for the Enact button and tap if found
			logInfo("Searching for Chief Order Enact button.");
				DTOImageSearchResult enactButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.CHIEF_ORDER_ENACT_BUTTON, 90);
				if (enactButton.isFound()) {
					logInfo("Chief Order Enact button found. Tapping to enact order.");
					emuManager.tapAtPoint(EMULATOR_NUMBER, enactButton.getPoint());
					sleepTask(1000);
				} else {
					logWarning("Chief Order Enact button not found. Skipping enact.");
				}
				
			// Navigate back after Enact
			tapBackButton();
			sleepTask(5000);
				
				logInfo(chiefOrderType.getDescription() + " activated successfully. " +
						"Rescheduling in " + chiefOrderType.getCooldownHours() + " hours.");
				
				// Reschedule based on cooldown
				this.reschedule(LocalDateTime.now().plusHours(chiefOrderType.getCooldownHours()));
				
			} else {
				logWarning(chiefOrderType.getDescription() + " button not found or currently on cooldown.");
				// Reschedule for shorter retry interval (6 hours) when not available
				logInfo("Rescheduling for retry in 6 hours.");
				this.reschedule(LocalDateTime.now().plusHours(6));
			}

		// Navigate back to main screen
		tapBackButton();
		} else {
			logError("Chief Order menu button not found. Unable to access Chief Orders.");
			// Reschedule for retry in 10 minutes when menu not accessible
			logInfo("Rescheduling for retry in 10 minutes.");
			this.reschedule(LocalDateTime.now().plusMinutes(10));
		}
	}

}
