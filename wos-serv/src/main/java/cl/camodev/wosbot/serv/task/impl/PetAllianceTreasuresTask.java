package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

public class PetAllianceTreasuresTask extends DelayedTask {

	private int attempts = 0;

	public PetAllianceTreasuresTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	public EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.ANY;
	}

	@Override
	protected void execute() {
		if (attempts >= 3) {
			logWarning(
					"Could not find the Pet Alliance Treasures menu after multiple attempts. Removing task from scheduler.");
			this.setRecurring(false);
			return;
		}

		logInfo("Navigating to the Beast Cage to claim Alliance Treasures.");

		DTOImageSearchResult petsResult = templateSearchHelper.searchTemplate(EnumTemplates.GAME_HOME_PETS,
				SearchConfigConstants.DEFAULT_SINGLE);
		if (petsResult.isFound()) {
			logInfo("Pets button found. Tapping to open.");
			tapRandomPoint(petsResult.getPoint(), petsResult.getPoint());
			sleepTask(3000);

			DTOImageSearchResult beastCageResult = templateSearchHelper.searchTemplate(
					EnumTemplates.PETS_BEAST_CAGE, SearchConfigConstants.DEFAULT_SINGLE);
			if (beastCageResult.isFound()) {
				tapRandomPoint(beastCageResult.getPoint(), beastCageResult.getPoint());
				sleepTask(500);
				tapRandomPoint(new DTOPoint(547, 1150), new DTOPoint(650, 1210));
				sleepTask(500);

				tapRandomPoint(new DTOPoint(612, 1184), new DTOPoint(653, 1211));
				sleepTask(500);

				DTOImageSearchResult claimButton = templateSearchHelper.searchTemplate(
						EnumTemplates.PETS_BEAST_ALLIANCE_CLAIM, SearchConfigConstants.DEFAULT_SINGLE);
				if (claimButton.isFound()) {
					logInfo("Claim button found. Tapping to claim the treasure.");
					tapRandomPoint(claimButton.getPoint(), claimButton.getPoint());
					this.reschedule(UtilTime.getGameReset());
					logInfo("Alliance treasure claimed. Rescheduling for the next game reset.");
					tapBackButton();
					tapBackButton();
					tapBackButton();
				} else {
					logWarning("Claimable reward not found. Rescheduling for the next game reset.");
					this.reschedule(UtilTime.getGameReset());
					tapBackButton();
					tapBackButton();
					tapBackButton();
				}

			} else {
				logWarning("Beast Cage not found. Retrying later.");

			}

		} else {
			logWarning("Pets button not found. Retrying later.");
			attempts++;
		}
	}

}
