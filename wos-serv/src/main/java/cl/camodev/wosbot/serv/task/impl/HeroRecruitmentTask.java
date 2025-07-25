package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import net.sourceforge.tess4j.TesseractException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeroRecruitmentTask extends DelayedTask {

    public HeroRecruitmentTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    protected void execute() {

        logInfo("going hero recruitment");
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(160, 1190), new DTOPoint(217, 1250));
        sleepTask(500);
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(400, 1190), new DTOPoint(660, 1250));
        sleepTask(500);


        logInfo("evaluating advanced recruitment");
        DTOImageSearchResult claimResult = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.HERO_RECRUIT_CLAIM.getTemplate(), new DTOPoint(40, 800), new DTOPoint(340, 1100), 95);
        LocalDateTime nextAdvanced = null;
        String text = "";
        if (claimResult.isFound()) {
            logInfo("advanced recruitment available, tapping");
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 827), new DTOPoint(315, 875));
            sleepTask(500);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130));
            sleepTask(300);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130));
            sleepTask(300);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130));
            sleepTask(300);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130));
            sleepTask(300);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130));
            sleepTask(1000);
            logInfo("getting next recruitment time");


        } else {
            logInfo("no rewards to claim, getting next recruitment time");

        }

        try {
            text = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(40, 770), new DTOPoint(350, 810));
        } catch (Exception e) {
            logError("Error reading advanced recruitment timer: " + e.getMessage());
        }
        logInfo(text + " rescheduling task");
        nextAdvanced = parseNextFree(text);
        logInfo("evaluating epic recruitment");
        DTOImageSearchResult claimResultEpic = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.HERO_RECRUIT_CLAIM.getTemplate(), new DTOPoint(40, 1160), new DTOPoint(340, 1255), 95);
        LocalDateTime nextEpic;
        if (claimResultEpic.isFound()) {
            logInfo("epic recruitment available, tapping");
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(70, 1180), new DTOPoint(315, 1230));
            sleepTask(500);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130));
            sleepTask(300);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130));
            sleepTask(300);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130));
            sleepTask(300);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130));
            sleepTask(300);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130));
            sleepTask(1000);
            logInfo("getting next recruitment time");

        } else {
            logInfo("no rewards to claim, getting next recruitment time");

        }


        try {
            text = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(53, 1130), new DTOPoint(330, 1160));
        } catch (IOException | TesseractException e) {
            logError("Error reading epic recruitment timer: " + e.getMessage());
        }
        nextEpic = parseNextFree(text);

        LocalDateTime nextExecution = getEarliest(nextAdvanced, nextEpic);
        this.reschedule(nextExecution);
        emuManager.tapBackButton(EMULATOR_NUMBER);
        emuManager.tapBackButton(EMULATOR_NUMBER);

    }

    public LocalDateTime getEarliest(LocalDateTime dt1, LocalDateTime dt2) {
        return dt1.isBefore(dt2) ? dt1 : dt2;
    }

    public LocalDateTime parseNextFree(String input) {
        // Regular expression to match the input format [n]d HH:mm:ss' o 'HH:mm:ss
        Pattern pattern = Pattern.compile("(?i).*?(?:(\\d+)\\s*d\\s*)?(\\d{1,2}:\\d{2}:\\d{2}).*", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Input does not match the expected format. Expected format: [n]d HH:mm:ss' o 'HH:mm:ss");
        }


        String daysStr = matcher.group(1);   // optional, can be null
        String timeStr = matcher.group(2);   // always present

        int daysToAdd = (daysStr != null) ? Integer.parseInt(daysStr) : 0;

        // parser for time part
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm:ss");
        LocalTime timePart = LocalTime.parse(timeStr, timeFormatter);


        return LocalDateTime.now()
                .plusDays(daysToAdd)
                .plusHours(timePart.getHour())
                .plusMinutes(timePart.getMinute())
                .plusSeconds(timePart.getSecond());
    }

    @Override
    public boolean provideDailyMissionProgress() {return true;}

}