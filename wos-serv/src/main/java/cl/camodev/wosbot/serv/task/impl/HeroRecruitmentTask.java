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
            sleepTask(500);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130), 5, 300);
            sleepTask(1000);
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
            sleepTask(500);
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(80, 90), new DTOPoint(140, 130), 5, 300);
            sleepTask(1000);
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
        emuManager.tapBackButton(EMULATOR_NUMBER);
        emuManager.tapBackButton(EMULATOR_NUMBER);
    }

    public LocalDateTime getEarliest(LocalDateTime dt1, LocalDateTime dt2) {
        return dt1.isBefore(dt2) ? dt1 : dt2;
    }

    public LocalDateTime parseNextFree(String input) {
        // Simpler regular expression that extracts only numbers and time
        // Looks for: optional number followed by any character, then HH:mm:ss
        Pattern pattern = Pattern.compile("(?i).*?(\\d+)[^\\d:]*?(\\d{1,2}:\\d{2}:\\d{2}).*", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input.trim());

        if (!matcher.matches()) {
            // If the pattern with days is not found, try with time only
            Pattern timeOnlyPattern = Pattern.compile("(?i).*?(\\d{1,2}:\\d{2}:\\d{2}).*", Pattern.DOTALL);
            Matcher timeOnlyMatcher = timeOnlyPattern.matcher(input.trim());

            if (timeOnlyMatcher.matches()) {
                String timeStr = timeOnlyMatcher.group(1);
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm:ss");
                LocalTime timePart = LocalTime.parse(timeStr, timeFormatter);

                return LocalDateTime.now()
                        .plusHours(timePart.getHour())
                        .plusMinutes(timePart.getMinute())
                        .plusSeconds(timePart.getSecond());
            }

            throw new IllegalArgumentException("Input does not match expected format. Input: " + input);
        }

        String daysStr = matcher.group(1);   // number before the time
        String timeStr = matcher.group(2);   // time HH:mm:ss

        int daysToAdd = Integer.parseInt(daysStr);

        // parser for the time part
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