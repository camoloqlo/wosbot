package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cl.camodev.wosbot.console.enumerable.EnumConfigurationKey.*;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.*;

public class CrystalLaboratoryTask extends DelayedTask {

    private static final int MAX_SEARCH_RETRIES = 3;
    private static final int MAX_CONSECUTIVE_FAILED_CLAIMS = 5;
    private static final int RETRY_DELAY_MS = 300;
    private static final int CLAIM_DELAY_MS = 100;
    private static final Pattern FC_PATTERN = Pattern.compile("(\\d{1,3}(?:[.,]\\d{3})*|\\d+)");

    public CrystalLaboratoryTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    /**
     * Generic retry method for template searching
     */
    private DTOImageSearchResult searchWithRetry(EnumTemplates template, int maxRetries, String description) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            logDebug("Searching for " + description + " - attempt " + attempt + "/" + maxRetries);
            DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER, template, 90);
            if (result.isFound()) {
                logDebug(description + " found on attempt " + attempt);
                return result;
            }
            if (attempt < maxRetries) {
                sleepTask(RETRY_DELAY_MS);
            }
        }
        logDebug(description + " not found after " + maxRetries + " attempts");
        return null;
    }

    /**
     * OCR with retry logic
     */
    private int extractNumberWithOCR(DTOPoint start, DTOPoint end, String description, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            logInfo("Retrieving " + description + " via OCR, attempt " + attempt + "/" + maxRetries);
            try {
                String ocrResult = emuManager.ocrRegionText(EMULATOR_NUMBER, start, end);
                Matcher matcher = FC_PATTERN.matcher(ocrResult);
                if (matcher.find()) {
                    String normalized = matcher.group(1).replaceAll("[.,]", "");
                    return Integer.parseInt(normalized);
                }
            } catch (Exception e) {
                logWarning("OCR attempt " + attempt + " threw an exception: " + e.getMessage());
                if (attempt < maxRetries) {
                    sleepTask(1000);
                }
            }
        }
        logInfo("Failed to retrieve " + description + " via OCR after " + maxRetries + " attempts");
        return -1;
    }

    private int calculateFCNeeded(int current, int target) {
        int total = 0;
        for (int refine = current + 1; refine <= target; refine++) {
            if (refine <= 20) total += 20;
            else if (refine <= 40) total += 50;
            else if (refine <= 60) total += 100;
            else if (refine <= 80) total += 130;
            else if (refine <= 100) total += 160;
        }
        return total;
    }

    @Override
    protected void execute() {
        final boolean useDiscountedDailyRFC = profile.getConfig(BOOL_CRYSTAL_LAB_DAILY_DISCOUNTED_RFC, Boolean.class);

        logInfo("Navigating to Crystal Laboratory");
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(3, 513), new DTOPoint(26, 588));
        sleepTask(1000);
        emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(110, 270));
        sleepTask(500);

        // Search for troops button
        DTOImageSearchResult troopsResult = searchWithRetry(GAME_HOME_SHORTCUTS_LANCER, MAX_SEARCH_RETRIES, "troops button");
        if (troopsResult == null) {
            logInfo("Could not locate troops button. Skipping crystal lab tasks.");
            return;
        }

        tapPoint(troopsResult.getPoint());
        sleepTask(1000);
        swipe(new DTOPoint(710, 950), new DTOPoint(510, 810));
        sleepTask(2000);

        // Search for crystal lab FC button (with extended retries)
        DTOImageSearchResult crystalLabResult = searchWithRetry(CRYSTAL_LAB_FC_BUTTON, 10, "Crystal Lab FC button");
        boolean crystalLabFound = crystalLabResult != null;

        // Backup method if primary search fails
        if (!crystalLabFound) {
            logInfo("Attempting backup method to locate Crystal Lab FC button");
            for (int attempt = 1; attempt <= MAX_SEARCH_RETRIES && !crystalLabFound; attempt++) {
                logDebug("Backup method attempt " + attempt + "/" + MAX_SEARCH_RETRIES);
                emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(281, 697));
                sleepTask(1000);

                DTOImageSearchResult validationResult = searchWithRetry(VALIDATION_CRYSTAL_LAB_UI, 1, "Crystal Lab UI validation");
                if (validationResult != null) {
                    crystalLabFound = true;
                    logInfo("Crystal Lab UI validated using backup method on attempt " + attempt);
                    break;
                }
                if (attempt < MAX_SEARCH_RETRIES) sleepTask(RETRY_DELAY_MS);
            }
        } else {
            tapPoint(crystalLabResult.getPoint());
            sleepTask(1000);
        }

        if (!crystalLabFound) {
            logInfo("Could not locate Crystal Lab after all attempts. Skipping crystal lab tasks.");
            return;
        }

        // Claim crystals
        claimCrystals();

        // Handle discounted RFC
        if (useDiscountedDailyRFC) {
            handleDiscountedRFC();

            // Weekly RFC processing (Mondays only)
            if (LocalDateTime.now(Clock.systemUTC()).getDayOfWeek() == DayOfWeek.MONDAY) {
                processWeeklyRFC();
            }
        }

        reschedule(UtilTime.getGameReset());
    }

    private void claimCrystals() {
        DTOImageSearchResult claimResult = searchWithRetry(CRYSTAL_LAB_REFINE_BUTTON, MAX_SEARCH_RETRIES, "initial claim button");
        if (claimResult == null) {
            logInfo("No crystals available to claim.");
            return;
        }

        logInfo("Starting crystal claiming process");
        int consecutiveFailedClaims = 0;

        while (claimResult != null && consecutiveFailedClaims < MAX_CONSECUTIVE_FAILED_CLAIMS) {
            logInfo("Claiming crystal...");
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, claimResult.getPoint(), claimResult.getPoint());
            sleepTask(CLAIM_DELAY_MS);

            claimResult = searchWithRetry(CRYSTAL_LAB_REFINE_BUTTON, MAX_SEARCH_RETRIES, "next claim button");
            if (claimResult == null) {
                consecutiveFailedClaims++;
                logDebug("Failed to find next claim button. Consecutive failures: " + consecutiveFailedClaims + "/" + MAX_CONSECUTIVE_FAILED_CLAIMS);
            } else {
                consecutiveFailedClaims = 0;
            }
        }

        if (consecutiveFailedClaims >= MAX_CONSECUTIVE_FAILED_CLAIMS) {
            logInfo("Exiting claim loop - all crystals likely claimed.");
        } else {
            logInfo("Crystal claiming process completed successfully.");
        }
    }

    private void handleDiscountedRFC() {
        DTOImageSearchResult discountedResult = searchWithRetry(CRYSTAL_LAB_DAILY_DISCOUNTED_RFC, MAX_SEARCH_RETRIES, "discounted RFC");
        if (discountedResult == null) {
            logInfo("No discounted RFC available today.");
            return;
        }

        logInfo("50% discounted RFC available. Attempting to claim it now.");
        DTOImageSearchResult refineResult = searchWithRetry(CRYSTAL_LAB_RFC_REFINE_BUTTON, MAX_SEARCH_RETRIES, "RFC refine button");
        if (refineResult != null) {
            tapPoint(refineResult.getPoint());
            sleepTask(500);
            logInfo("Discounted RFC claimed successfully.");
        } else {
            logInfo("Could not find RFC refine button.");
        }
    }

    private void processWeeklyRFC() {
        int currentFC = extractNumberWithOCR(new DTOPoint(590, 21), new DTOPoint(700, 60), "current FC", 5);
        if (currentFC == -1) return;

        int currentRFC = extractNumberWithOCR(new DTOPoint(170, 1078), new DTOPoint(512, 1106), "current refined FC", 5);
        if (currentRFC == -1) return;

        int targetRefines = profile.getConfig(INT_WEEKLY_RFC, Integer.class);
        int neededFC = calculateFCNeeded(currentRFC, targetRefines);

        logInfo("Current FC: " + currentFC + ", Current refined FC: " + currentRFC +
                ", FC needed to reach " + targetRefines + " refines: " + neededFC);

        if (currentRFC >= targetRefines) {
            logInfo("Target of " + targetRefines + " refines already reached. No further RFC needed today.");
            reschedule(UtilTime.getGameReset());
            return;
        }

        if (neededFC > currentFC) {
            logInfo("Insufficient FC to reach " + targetRefines + " refines. Needed: " + neededFC + ", Available: " + currentFC);
            reschedule(LocalDateTime.now().plusHours(2));
            return;
        }

        logInfo("Sufficient FC available to reach " + targetRefines + " refines. Proceeding with RFC.");
        int refinesToDo = targetRefines - currentRFC;

        DTOImageSearchResult refineResult = searchWithRetry(CRYSTAL_LAB_RFC_REFINE_BUTTON, MAX_SEARCH_RETRIES, "RFC refine button for weekly processing");
        if (refineResult != null) {
            tapRandomPoint(refineResult.getPoint(), refineResult.getPoint(), refinesToDo, 500);
        }
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }
}
