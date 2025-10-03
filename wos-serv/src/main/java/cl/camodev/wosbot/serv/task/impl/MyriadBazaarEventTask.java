package cl.camodev.wosbot.serv.task.impl;


import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

import static cl.camodev.wosbot.console.enumerable.EnumTemplates.DAILY_MISSION_CLAIM_BUTTON;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.EVENTS_MYRIAD_BAZAAR_ICON;

/**
 * Task implementation for claiming free rewards on Myriad Bazaar event.
 * This task handles the automation of claiming rewards from the Myriad Bazaar event.
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
        DTOImageSearchResult bazaarIcon = emuManager.searchTemplate(EMULATOR_NUMBER, EVENTS_MYRIAD_BAZAAR_ICON, 90);

        if (!bazaarIcon.isFound()) {
            logInfo("Myriad Bazaar event probably not active");
            reschedule(UtilTime.getGameReset());
            return;
        }
        logInfo("Myriad Bazaar is active, claiming free rewards");
        //wait for the event window to open
        emuManager.tapAtPoint(EMULATOR_NUMBER, bazaarIcon.getPoint());
        sleepTask(2000);

        //define area to search for free rewards
        DTOPoint topLeft = new DTOPoint(50, 280);
        DTOPoint bottomRight = new DTOPoint(650, 580);

        // claim all the rewards available using a while loop until no more rewards are availableD
        int failCount = 0;
        DTOImageSearchResult freeReward = emuManager.searchTemplate(EMULATOR_NUMBER, DAILY_MISSION_CLAIM_BUTTON, topLeft, bottomRight, 90);
        while (true) {
            if (freeReward != null && freeReward.isFound()) {
                logInfo("Claiming free rewards");
                emuManager.tapAtPoint(EMULATOR_NUMBER, freeReward.getPoint());
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
            freeReward = emuManager.searchTemplate(EMULATOR_NUMBER, DAILY_MISSION_CLAIM_BUTTON, topLeft, bottomRight, 90);
        }
        logInfo("Finished claiming Myriad Bazaar free rewards");
        reschedule(UtilTime.getGameReset());

    }

}
