package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper;

import static cl.camodev.wosbot.console.enumerable.EnumTemplates.DAILY_MISSION_CLAIM_BUTTON;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.EVENTS_MYRIAD_BAZAAR_ICON;

/**
 * Task implementation for claiming free rewards on Myriad Bazaar event.
 * This task handles the automation of claiming rewards from the Myriad Bazaar
 * event.
 */
public class MyriadBazaarEventTask extends DelayedTask {

    public MyriadBazaarEventTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    public EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    @Override
    protected void execute() {

        // search the myriad bazaar event icon and click it
        DTOImageSearchResult bazaarIcon = templateSearchHelper.searchTemplate(
                EVENTS_MYRIAD_BAZAAR_ICON, SearchConfigConstants.DEFAULT_SINGLE);

        if (!bazaarIcon.isFound()) {
            logInfo("Myriad Bazaar event probably not active");
            reschedule(UtilTime.getGameReset());
            return;
        }
        logInfo("Myriad Bazaar is active, claiming free rewards");
        // wait for the event window to open
        tapPoint(bazaarIcon.getPoint());
        sleepTask(2000);

        // define area to search for free rewards
        DTOPoint topLeft = new DTOPoint(50, 280);
        DTOPoint bottomRight = new DTOPoint(650, 580);

        // claim all the rewards available using a while loop until no more rewards are
        // availableD
        int failCount = 0;
        DTOImageSearchResult freeReward = templateSearchHelper.searchTemplate(DAILY_MISSION_CLAIM_BUTTON,
                TemplateSearchHelper.SearchConfig.builder()
                        .withMaxAttempts(1)
                        .withThreshold(90)
                        .withDelay(300L)
                        .withCoordinates(topLeft, bottomRight)
                        .build());
        while (true) {
            if (freeReward != null && freeReward.isFound()) {
                logInfo("Claiming free rewards");
                tapPoint(freeReward.getPoint());
                sleepTask(1000);
                failCount = 0;
            } else {
                failCount++;
                if (failCount >= 3) {
                    logInfo("No rewards found after 3 consecutive attempts, exiting loop");
                    break;
                }
                sleepTask(500);
            }
            freeReward = templateSearchHelper.searchTemplate(DAILY_MISSION_CLAIM_BUTTON,
                    TemplateSearchHelper.SearchConfig.builder()
                            .withMaxAttempts(1)
                            .withThreshold(90)
                            .withDelay(300L)
                            .withCoordinates(topLeft, bottomRight)
                            .build());
        }
        logInfo("Finished claiming Myriad Bazaar free rewards");
        reschedule(UtilTime.getGameReset());

    }

}
