package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class WarAcademyTask extends DelayedTask {

    private final int MAX_RETRY_ATTEMPTS = 3;

    public WarAcademyTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }


    @Override
    protected void execute() {
        //STEP 1: i need to go to left menu, then cheok if there's 2 matches of research template
        // left menu
        tapRandomPoint(new DTOPoint(3, 513), new DTOPoint(26, 588));

        sleepTask(1000);

        // ensure we are in city tab
        tapPoint(new DTOPoint(110, 270));
        sleepTask(500);

        // Search for research template with retry logic
        List<DTOImageSearchResult> researchResults = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Searching for research centers, attempt " + attempt + " of " + MAX_RETRY_ATTEMPTS);

            swipe(new DTOPoint(255, 477), new DTOPoint(255, 425));
            sleepTask(500);

            researchResults = emuManager.searchTemplates(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_SHORTCUTS_RESEARCH_CENTER.getTemplate(), 90, 2);

            if (researchResults.size() >= 2) {
                ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Found " + researchResults.size() + " research centers on attempt " + attempt);
                break;
            } else {
                ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), "Only found " + researchResults.size() + " research centers on attempt " + attempt);
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    sleepTask(1000); // Wait a bit before next attempt
                }
            }
        }

        if (researchResults.size() < 2) {
            ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Not enough research centers found after " + MAX_RETRY_ATTEMPTS + " attempts, stopping task");
            return;
        }
        //STEP 2: tap on the match with highest y coordinate
        DTOImageSearchResult highestYMatch = researchResults.stream()
                .max(Comparator.comparingInt(r -> r.getPoint().getY()))
                .orElseThrow(() -> new RuntimeException("No valid research center found"));

        tapPoint(highestYMatch.getPoint());

        sleepTask(1000);
        tapRandomPoint(new DTOPoint(360, 790), new DTOPoint(360, 790), 5, 100);

        //STEP 3: search for building reseach button template with retry logic
        DTOImageSearchResult researchButton = null;

        for (int buttonAttempt = 1; buttonAttempt <= MAX_RETRY_ATTEMPTS; buttonAttempt++) {
            logInfo("Searching for research button, attempt " + buttonAttempt + " of " + MAX_RETRY_ATTEMPTS);

            researchButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.BUILDING_BUTTON_RESEARCH.getTemplate(), 90);

            if (researchButton.isFound()) {
                logInfo("Research button found on attempt " + buttonAttempt);
                break;
            } else {
                logWarning("Research button not found on attempt " + buttonAttempt);
                if (buttonAttempt < MAX_RETRY_ATTEMPTS) {
                    sleepTask(1000); // Wait a bit before next attempt
                }
            }
        }

        if (!researchButton.isFound()) {
            logError("Research button not found after " + MAX_RETRY_ATTEMPTS + " attempts, stopping task");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }
        tapPoint(researchButton.getPoint());
        sleepTask(500);

        //STEP 4: check if we are in war academy ui with retry logic


        DTOImageSearchResult warAcademyUi = null;

        for (int uiAttempt = 1; uiAttempt <= MAX_RETRY_ATTEMPTS; uiAttempt++) {
            logInfo("Searching for War Academy UI, attempt " + uiAttempt + " of " + MAX_RETRY_ATTEMPTS);

            warAcademyUi = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.VALIDATION_WAR_ACADEMY_UI.getTemplate(), 90);

            if (warAcademyUi.isFound()) {
                logInfo("War Academy UI found on attempt " + uiAttempt);
                break;
            } else {
                logWarning("War Academy UI not found on attempt " + uiAttempt);
                if (uiAttempt < MAX_RETRY_ATTEMPTS) {
                    sleepTask(1000); // Wait a bit before next attempt
                }
            }
        }

        if (!warAcademyUi.isFound()) {
            logError("War Academy UI not found after " + MAX_RETRY_ATTEMPTS + " attempts");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }


        //STEP 5: go to redeem button
        tapPoint(new DTOPoint(642, 164));
        sleepTask(500);

        //STEP 6: check the remaining shards using OCR with retry logic
        String ocrResult;
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher;
        int remainingShards = -1;

        for (int ocrAttempt = 1; ocrAttempt <= MAX_RETRY_ATTEMPTS; ocrAttempt++) {
            logInfo("Reading remaining shards via OCR, attempt " + ocrAttempt + " of " + MAX_RETRY_ATTEMPTS);

            try {
                ocrResult = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(466, 456), new DTOPoint(624, 484));
                matcher = pattern.matcher(ocrResult);

                if (matcher.find()) {
                    String numericValue = matcher.group();
                    remainingShards = Integer.parseInt(numericValue);
                    logInfo("OCR successful on attempt " + ocrAttempt + ", found " + remainingShards + " shards");
                    break;
                } else {
                    logWarning("OCR attempt " + ocrAttempt + " failed to find numeric value in result: " + ocrResult);
                    if (ocrAttempt < MAX_RETRY_ATTEMPTS) {
                        sleepTask(1000); // Wait before retry
                    }
                }
            } catch (Exception e) {
                logWarning("OCR attempt " + ocrAttempt + " throws exception: " + e.getMessage());
                if (ocrAttempt < MAX_RETRY_ATTEMPTS) {
                    sleepTask(1000); // Wait before retry
                }
            }
        }

        if (remainingShards == -1) {
            logError("OCR failed to find any numeric value after " + MAX_RETRY_ATTEMPTS + " attempts");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }

        //STEP 7: check if the remaining shards are greater than 0
        if (remainingShards <= 0) {
            logInfo("No remaining shards to redeem");
            reschedule(UtilTime.getGameReset());
            return;
        }

        //STEP 8: confirm redeem and select the maximum number of shards to redeem
        tapPoint(new DTOPoint(545, 520));
        sleepTask(500);
        // tap on the maximum amount of shards to redeem
        tapPoint(new DTOPoint(614, 705));
        sleepTask(100);
        // tap on the confirm button
        tapPoint(new DTOPoint(358, 828));

        sleepTask(1000);

        //STEP 9: check if there's additional shards to redeem with retry logic
        int finalRemainingShards = -1;

        for (int finalOcrAttempt = 1; finalOcrAttempt <= MAX_RETRY_ATTEMPTS; finalOcrAttempt++) {
            logInfo("Reading final remaining shards via OCR, attempt " + finalOcrAttempt + " of " + MAX_RETRY_ATTEMPTS);

            try {
                ocrResult = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(466, 456), new DTOPoint(624, 484));
                matcher = pattern.matcher(ocrResult);

                if (matcher.find()) {
                    String numericValue = matcher.group();
                    finalRemainingShards = Integer.parseInt(numericValue);
                    logInfo("Final OCR successful on attempt " + finalOcrAttempt + ", found " + finalRemainingShards + " shards");
                    break;
                } else {
                    logWarning("Final OCR attempt " + finalOcrAttempt + " failed to find numeric value in result: " + ocrResult);
                    if (finalOcrAttempt < MAX_RETRY_ATTEMPTS) {
                        sleepTask(1000); // Wait before retry
                    }
                }
            } catch (Exception e) {
                logWarning("Final OCR attempt " + finalOcrAttempt + " threw exception: " + e.getMessage());
                if (finalOcrAttempt < MAX_RETRY_ATTEMPTS) {
                    sleepTask(1000); // Wait before retry
                }
            }
        }

        if (finalRemainingShards == -1) {
            ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Final OCR failed to find any numeric value after " + MAX_RETRY_ATTEMPTS + " attempts");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }

        //STEP 10: check if the remaining shards are greater than 0
        if (finalRemainingShards > 0) {
            logInfo("Additional shards found: " + finalRemainingShards + ", rescheduling task to redeem them");
            reschedule(LocalDateTime.now().plusHours(2));

        } else {
            logInfo("No additional shards found after final check");
            reschedule(UtilTime.getGameReset());

        }
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

}
