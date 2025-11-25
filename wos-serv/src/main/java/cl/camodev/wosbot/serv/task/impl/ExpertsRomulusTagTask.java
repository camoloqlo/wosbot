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

import java.time.LocalDateTime;

public class ExpertsRomulusTagTask extends DelayedTask {

    public ExpertsRomulusTagTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("Attempting to claim loyalty tags from Romulus.");

        navigateToInfirmary();

        boolean claimed = false;
        for (int i = 0; i < 10; i++) {
            logDebug("Searching for claim button (Attempt " + (i + 1) + "/10).");
            DTOImageSearchResult claimButton = templateSearchHelper.searchTemplate(
                    EnumTemplates.ROMULUS_CLAIM_TAG_BUTTON, SearchConfigConstants.HIGH_SENSITIVITY);
            if (claimButton.isFound()) {
                logInfo("Claiming loyalty tags from Romulus. Rescheduling for next reset.");
                tapPoint(claimButton.getPoint());
                sleepTask(1000);
                LocalDateTime nextReset = UtilTime.getGameReset(); // Reschedule for next reset
                this.reschedule(nextReset);
                claimed = true;
                break;
            }
            sleepTask(300);
        }

        if (!claimed) {
            logWarning(
                    "Could not find the final claim button after 10 attempts. Assuming already claimed. Rescheduling for next reset.");
            LocalDateTime nextReset = UtilTime.getGameReset();
            this.reschedule(nextReset);
        }
    }

    private void navigateToInfirmary() {
        logInfo("Navigating to the infirmary to reach enlistment office.");

        // Opens the left menu on city section
        openLeftMenuCitySection(true);

        DTOImageSearchResult researchCenter = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_SHORTCUTS_RESEARCH_CENTER, SearchConfigConstants.DEFAULT_SINGLE);

        if (researchCenter.isFound()) {
            tapPoint(researchCenter.getPoint());
            sleepTask(500);
            tapRandomPoint(new DTOPoint(488, 410), new DTOPoint(550, 450));
            sleepTask(500);
        } else {
            logWarning("Research Center shortcut not found. Rescheduling for 5 minutes.");
            this.reschedule(LocalDateTime.now().plusMinutes(5));
            tapBackButton();
        }
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }
}
