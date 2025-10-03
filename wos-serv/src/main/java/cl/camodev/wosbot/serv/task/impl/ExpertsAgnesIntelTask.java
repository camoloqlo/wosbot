package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

import java.time.LocalDateTime;

public class ExpertsAgnesIntelTask extends DelayedTask {

    public ExpertsAgnesIntelTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("Attempting to claim extra intel from Agnes.");
        ensureOnIntelScreen();

        boolean claimed = false;
        for (int i = 0; i < 10; i++) {
            logDebug("Searching for Agnes icon (Attempt " + (i + 1) + "/10).");
            DTOImageSearchResult agnes = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.AGNES_CLAIM_INTEL, 80);
            if (agnes.isFound()) {
                logInfo("Agnes icon found. Claiming intel.");
                emuManager.tapAtPoint(EMULATOR_NUMBER, agnes.getPoint());
                sleepTask(1000);
                LocalDateTime nextReset = UtilTime.getGameReset(); // Reschedule for next reset
                this.reschedule(nextReset);
                claimed = true;
                break;
            }
            sleepTask(300);
        }

        if (!claimed) {
            logWarning("Could not find Agnes icon for extra intel after 10 attempts. Assuming already claimed. Rescheduling for next reset.");
            LocalDateTime nextReset = UtilTime.getGameReset();
            this.reschedule(nextReset);
        }

        tapBackButton();
        logInfo("Agnes Intel task finished.");
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }
}
