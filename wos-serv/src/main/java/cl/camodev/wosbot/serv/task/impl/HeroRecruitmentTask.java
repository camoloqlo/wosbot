package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import net.sourceforge.tess4j.TesseractException;

import java.io.IOException;
import java.time.LocalDateTime;

public class HeroRecruitmentTask extends DelayedTask {

    public HeroRecruitmentTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    protected void execute() {

        logInfo("Starting hero recruitment task.");
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(160, 1190), new DTOPoint(217, 1250));
        sleepTask(500);
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(400, 1190), new DTOPoint(660, 1250));
        sleepTask(500);

        logInfo("Evaluating advanced recruitment...");
        DTOImageSearchResult claimResult = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.HERO_RECRUIT_CLAIM, new DTOPoint(40, 800), new DTOPoint(340, 1100), 95);
        LocalDateTime nextAdvanced = null;
        String text = "";

        if (claimResult.isFound()) {
            logInfo("Advanced recruitment is available. Claiming now.");
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 827), new DTOPoint(315, 875));
            sleepTask(1000);
            tapBackButton();
            tapBackButton();
            logInfo("Getting the next recruitment time.");
        } else {
            logInfo("No advanced recruitment rewards to claim. Getting the next recruitment time.");
        }

        try {
            text = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(40, 770), new DTOPoint(350, 810));
        } catch (Exception e) {
            logError("An error occurred during OCR for advanced recruitment: " + e.getMessage());
        }
        
        logInfo("Advanced recruitment OCR text: '" + text + "'. Rescheduling task.");
        nextAdvanced = UtilTime.parseTime(text);
        logInfo("Evaluating epic recruitment...");
        DTOImageSearchResult claimResultEpic = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.HERO_RECRUIT_CLAIM, new DTOPoint(40, 1160), new DTOPoint(340, 1255), 95);
        LocalDateTime nextEpic;

        if (claimResultEpic.isFound()) {
            logInfo("Epic recruitment is available. Claiming now.");
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(70, 1180), new DTOPoint(315, 1230));
            sleepTask(1000);
            tapBackButton();
            tapBackButton();
            logInfo("Getting the next recruitment time.");
        } else {
            logInfo("No epic recruitment rewards to claim. Getting the next recruitment time.");
        }


        try {
            text = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(53, 1130), new DTOPoint(330, 1160));
        } catch (IOException | TesseractException e) {
            logError("An error occurred during OCR for epic recruitment: " + e.getMessage());
        }
        nextEpic = UtilTime.parseTime(text);

        LocalDateTime nextReset = UtilTime.getGameReset();
        LocalDateTime effectiveNextAdvanced = getEarliest(nextAdvanced, nextReset);

        LocalDateTime nextExecution = getEarliest(effectiveNextAdvanced, nextEpic);
        logInfo("Next hero recruitment check is scheduled for: " + nextExecution);
        this.reschedule(nextExecution);
        tapBackButton();
        tapBackButton();
    }

    public LocalDateTime getEarliest(LocalDateTime dt1, LocalDateTime dt2) {
        return dt1.isBefore(dt2) ? dt1 : dt2;
    }

    @Override
    public boolean provideDailyMissionProgress() {return true;}

}