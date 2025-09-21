package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

public class WarAcademyTask extends DelayedTask {

    private final int MAX_RETRY_ATTEMPTS = 3;

    public WarAcademyTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    public EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    @Override
    protected void execute() {
        //STEP 1: I need to go to left menu, then check if there's 2 matches of research template
        // left menu
        tapRandomPoint(new DTOPoint(3, 513), new DTOPoint(26, 588));

        sleepTask(1000);

        // ensure we are in city tab
        tapPoint(new DTOPoint(110, 270));
        sleepTask(500);

        // Search for research template with retry logic
        List<DTOImageSearchResult> researchResults = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            logInfo("Searching for research centers");
            logDebug("Searching for research centers (Attempt " + attempt + "/" + MAX_RETRY_ATTEMPTS + ")...");

            swipe(new DTOPoint(255, 477), new DTOPoint(255, 425));
            sleepTask(500);

            researchResults = emuManager.searchTemplates(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_SHORTCUTS_RESEARCH_CENTER, 90, 2);

            if (researchResults.size() >= 2) {
                logInfo("Found " + researchResults.size() + " research centers on attempt " + attempt + ".");
                break;
            } else {
                logWarning("Only " + researchResults.size() + " research centers were found on attempt " + attempt + ".");
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    sleepTask(1000); // Wait a bit before next attempt
                }
            }
        }

        if (researchResults.size() < 2) {
            logError("Not enough research centers were found after " + MAX_RETRY_ATTEMPTS + " attempts. Stopping the task.");
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
            logInfo("Searching for the research button");
            logDebug("Searching for the research button (Attempt " + buttonAttempt + "/" + MAX_RETRY_ATTEMPTS + ")...");

            researchButton = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.BUILDING_BUTTON_RESEARCH, 90);

            if (researchButton.isFound()) {
                logInfo("The research button was found on attempt " + buttonAttempt + ".");
                break;
            } else {
                logWarning("The research button was not found on attempt " + buttonAttempt + ".");
                if (buttonAttempt < MAX_RETRY_ATTEMPTS) {
                    sleepTask(1000); // Wait 1s before next attempt
                }
            }
        }

        if (!researchButton.isFound()) {
            logError("The research button was not found after " + MAX_RETRY_ATTEMPTS + " attempts. Stopping the task.");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }
        tapPoint(researchButton.getPoint());
        sleepTask(500);

        //STEP 4: check if we are in war academy UI with retry logic


        DTOImageSearchResult warAcademyUi = null;

        for (int uiAttempt = 1; uiAttempt <= MAX_RETRY_ATTEMPTS; uiAttempt++) {
            logInfo("Searching for the War Academy UI");
            logDebug("Searching for the War Academy UI (Attempt " + uiAttempt + "/" + MAX_RETRY_ATTEMPTS + ")...");

            warAcademyUi = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.VALIDATION_WAR_ACADEMY_UI, 90);

            if (warAcademyUi.isFound()) {
                logInfo("The War Academy UI was found on attempt " + uiAttempt + ".");
                break;
            } else {
                logWarning("The War Academy UI was not found on attempt " + uiAttempt + ".");
                if (uiAttempt < MAX_RETRY_ATTEMPTS) {
                    sleepTask(1000); // Wait 1s before next attempt
                }
            }
        }

        if (!warAcademyUi.isFound()) {
            logError("The War Academy UI was not found after " + MAX_RETRY_ATTEMPTS + " attempts.");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }


        //STEP 5: go to redeem button
        tapPoint(new DTOPoint(642, 164));
        sleepTask(500);

    //STEP 6: check the remaining shards using OCR with retry logic
    String ocrResult;
        int remainingShards = -1;

        for (int ocrAttempt = 1; ocrAttempt <= MAX_RETRY_ATTEMPTS; ocrAttempt++) {
            logInfo("Reading the number of remaining shards via OCR");
            logDebug("Reading the number of remaining shards via OCR (Attempt " + ocrAttempt + "/" + MAX_RETRY_ATTEMPTS + ")...");

            try {
                ocrResult = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(466, 456), new DTOPoint(624, 484));
                int parsed = parseOcrDigits(ocrResult);
                if (parsed >= 0) {
                    remainingShards = parsed;
                    logInfo("OCR was successful on attempt " + ocrAttempt + ". Found " + remainingShards + " shards.");
                    break;
                } else {
                    logWarning("OCR attempt " + ocrAttempt + " failed to find a numeric value in the result: " + ocrResult);
                    if (ocrAttempt < MAX_RETRY_ATTEMPTS) {
                        sleepTask(1000); // Wait 1s before retry
                    }
                }
            } catch (Exception e) {
                logWarning("OCR attempt " + ocrAttempt + " threw an exception: " + e.getMessage());
                if (ocrAttempt < MAX_RETRY_ATTEMPTS) {
                    sleepTask(1000); // Wait 1s before retry
                }
            }
        }

        if (remainingShards == -1) {
            logError("OCR failed to find any numeric value after " + MAX_RETRY_ATTEMPTS + " attempts. Rescheduling the task.");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }

        //STEP 7: check if the remaining shards are greater than 0
        if (remainingShards <= 0) {
            logInfo("There are no remaining shards to redeem.");
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
            logInfo("Reading the final number of remaining shards via OCR");
            logDebug("Reading the final number of remaining shards via OCR (Attempt " + finalOcrAttempt + "/" + MAX_RETRY_ATTEMPTS + ")...");

            try {
                ocrResult = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(466, 456), new DTOPoint(624, 484));
                int parsed = parseOcrDigits(ocrResult);
                if (parsed >= 0) {
                    finalRemainingShards = parsed;
                    logInfo("Final OCR was successful on attempt " + finalOcrAttempt + ". Found " + finalRemainingShards + " shards.");
                    break;
                } else {
                    logWarning("Final OCR attempt " + finalOcrAttempt + " failed to find a numeric value in the result: " + ocrResult);
                    if (finalOcrAttempt < MAX_RETRY_ATTEMPTS) {
                        sleepTask(1000); // Wait 1s before retry
                    }
                }
            } catch (Exception e) {
                logWarning("Final OCR attempt " + finalOcrAttempt + " threw an exception: " + e.getMessage());
                if (finalOcrAttempt < MAX_RETRY_ATTEMPTS) {
                    sleepTask(1000); // Wait 1s before retry
                }
            }
        }

        if (finalRemainingShards == -1) {
            logError("The final OCR failed to find any numeric value after " + MAX_RETRY_ATTEMPTS + " attempts.");
            reschedule(LocalDateTime.now().plusMinutes(5));
            return;
        }

        //STEP 10: check if the remaining shards are greater than 0
        if (finalRemainingShards > 0) {
            logInfo("Additional shards were found: " + finalRemainingShards + ". Rescheduling the task to redeem them.");
            reschedule(LocalDateTime.now().plusHours(2));

        } else {
            logInfo("No additional shards were found after the final check.");
            reschedule(UtilTime.getGameReset());

        }
    
    }

    private int parseOcrDigits(String text) {
            if (text == null) return -1;
            try {
                String normalized = text
                        .replaceAll("[Oo]", "0")
                        .replaceAll("[Il!\\|]", "1")
                        .replaceAll("[^0-9]", "");
                if (normalized.isEmpty()) return -1;
                return Integer.parseInt(normalized);
            } catch (Exception ex) {
                return -1;
            }
        }
}
