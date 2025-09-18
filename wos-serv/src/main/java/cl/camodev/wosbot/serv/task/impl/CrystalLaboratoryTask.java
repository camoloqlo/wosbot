package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import static cl.camodev.wosbot.console.enumerable.EnumConfigurationKey.*;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.*;

public class CrystalLaboratoryTask extends DelayedTask {

	public CrystalLaboratoryTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
		super(profile, tpDailyTask);
	}

	@Override
	protected void execute() {
        final int MAX_SEARCH_RETRIES = 3;
        final int MAX_CONSECUTIVE_FAILED_CLAIMS = 5;
        final int RETRY_DELAY_MS = 300;
        final int CLAIM_DELAY_MS = 100;
        final boolean useDiscountedDailyRFC = profile.getConfig(BOOL_CRYSTAL_LAB_DAILY_DISCOUNTED_RFC,Boolean.class);

        logInfo("Navigating to Crystal Laboratory");
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(3, 513), new DTOPoint(26, 588));
        sleepTask(1000);

        // Tap on training tab
        emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(110, 270));
        sleepTask(500);

        // Search for the specific troop type with retry logic
        DTOImageSearchResult troopsResult = null;
        boolean troopsFound = false;
        for (int attempt = 1; attempt <= MAX_SEARCH_RETRIES && !troopsFound; attempt++) {
            logDebug("Searching for troops button - attempt " + attempt + "/" + MAX_SEARCH_RETRIES);
            troopsResult = emuManager.searchTemplate(EMULATOR_NUMBER, GAME_HOME_SHORTCUTS_LANCER, 90);
            if (troopsResult.isFound()) {
                troopsFound = true;
                logDebug("Troops button found on attempt " + attempt);
            } else {
                logDebug("Troops button not found on attempt " + attempt);
                if (attempt < MAX_SEARCH_RETRIES) {
                    sleepTask(RETRY_DELAY_MS);
                }
            }
        }

        if (troopsFound) {
            tapPoint(troopsResult.getPoint());
            sleepTask(1000);

            // Move the screen to better see the crystal lab
            swipe(new DTOPoint(710,950), new DTOPoint(510,810));
            sleepTask(2000);

            // Search for crystal lab FC button with enhanced retry logic
            boolean crystalLabFound = false;
            for (int attempt = 1; attempt <= 10 && !crystalLabFound; attempt++) {
                logDebug("Searching for Crystal Lab FC button - attempt " + attempt + "/10");
                DTOImageSearchResult crystalLabResult = emuManager.searchTemplate(EMULATOR_NUMBER, CRYSTAL_LAB_FC_BUTTON, 90);
                if (crystalLabResult.isFound()) {
                    tapPoint(crystalLabResult.getPoint());
                    sleepTask(1000);
                    crystalLabFound = true;
                    logInfo("Crystal Lab FC button found and tapped on attempt " + attempt);
                } else {
                    logDebug("Crystal Lab FC button not found on attempt " + attempt + ". Retrying...");
                    sleepTask(RETRY_DELAY_MS);
                }
            }
            //backup method to find crystal lab if the above fails
            if (!crystalLabFound) {
                logInfo("Attempting backup method to locate Crystal Lab FC button.");

                // Backup method with retry logic
                for (int backupAttempt = 1; backupAttempt <= MAX_SEARCH_RETRIES && !crystalLabFound; backupAttempt++) {
                    logDebug("Backup method attempt " + backupAttempt + "/" + MAX_SEARCH_RETRIES );
                    emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(281, 697));
                    sleepTask(1000);

                    DTOImageSearchResult crystalLabResult = emuManager.searchTemplate(EMULATOR_NUMBER, VALIDATION_CRYSTAL_LAB_UI, 90);
                    if (crystalLabResult.isFound()) {
                        crystalLabFound = true;
                        logInfo("Crystal Lab UI validated using backup method on attempt " + backupAttempt);
                    } else {
                        logDebug("Backup method validation failed on attempt " + backupAttempt);
                        if (backupAttempt < MAX_SEARCH_RETRIES) {
                            logDebug("Retrying backup method in " + RETRY_DELAY_MS + "ms...");
                            sleepTask(RETRY_DELAY_MS);
                        }
                    }
                }

                if (!crystalLabFound) {
                    logInfo("Backup method failed to validate Crystal Lab UI after " + MAX_SEARCH_RETRIES + " attempts.");
                }
            }


            if (crystalLabFound){
                // Search for claimable crystals with retry logic
                DTOImageSearchResult claimResult = null;
                boolean initialClaimFound = false;
                for (int attempt = 1; attempt <= MAX_SEARCH_RETRIES && !initialClaimFound; attempt++) {
                    logDebug("Searching for initial claim button - attempt " + attempt + "/" + MAX_SEARCH_RETRIES);
                    claimResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.CRYSTAL_LAB_REFINE_BUTTON, 90);
                    if (claimResult.isFound()) {
                        initialClaimFound = true;
                        logDebug("Initial claim button found on attempt " + attempt);
                    } else {
                        logDebug("Initial claim button not found on attempt " + attempt);
                        if (attempt < MAX_SEARCH_RETRIES) {
                            sleepTask(RETRY_DELAY_MS);
                        }
                    }
                }

                if (!initialClaimFound) {
                    logInfo("No crystals available to claim.");
                } else {
                    // Enhanced claiming loop with safety mechanism
                    int consecutiveFailedClaims = 0;
                    logInfo("Starting crystal claiming process");

                    while (claimResult != null && claimResult.isFound() && consecutiveFailedClaims < MAX_CONSECUTIVE_FAILED_CLAIMS) {
                        logInfo("Claiming crystal...");
                        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, claimResult.getPoint(), claimResult.getPoint());
                        sleepTask(CLAIM_DELAY_MS);

                        // Search for next claimable crystal with retry logic
                        DTOImageSearchResult nextClaimResult = null;
                        boolean nextClaimFound = false;

                        for (int attempt = 1; attempt <= MAX_SEARCH_RETRIES && !nextClaimFound; attempt++) {
                            nextClaimResult = emuManager.searchTemplate(EMULATOR_NUMBER, CRYSTAL_LAB_REFINE_BUTTON, 90);
                            if (nextClaimResult.isFound()) {
                                nextClaimFound = true;
                                consecutiveFailedClaims = 0; // Reset counter on successful find
                                logDebug("Next claim button found on attempt " + attempt);
                            } else {
                                logDebug("Next claim button not found on attempt " + attempt);
                                if (attempt < MAX_SEARCH_RETRIES) {
                                    sleepTask(RETRY_DELAY_MS);
                                }
                            }
                        }

                        if (!nextClaimFound) {
                            consecutiveFailedClaims++;
                            logDebug("Failed to find next claim button. Consecutive failures: " + consecutiveFailedClaims + "/" + MAX_CONSECUTIVE_FAILED_CLAIMS);
                        }

                        claimResult = nextClaimResult;
                    }

                    if (consecutiveFailedClaims >= MAX_CONSECUTIVE_FAILED_CLAIMS) {
                        logInfo("Exiting claim loop due to " + MAX_CONSECUTIVE_FAILED_CLAIMS + " consecutive failed searches. This might indicate all crystals have been claimed.");
                    } else {
                        logInfo("Crystal claiming process completed successfully.");
                    }
                }

                // Check if we have the discounted RFC option with retry logic
                if(useDiscountedDailyRFC){
                    DTOImageSearchResult discountedRFCResult = null;
                    boolean discountedRFCFound = false;

                    for (int attempt = 1; attempt <= MAX_SEARCH_RETRIES && !discountedRFCFound; attempt++) {
                        logDebug("Searching for discounted RFC - attempt " + attempt + "/" + MAX_SEARCH_RETRIES);
                        discountedRFCResult = emuManager.searchTemplate(EMULATOR_NUMBER, CRYSTAL_LAB_DAILY_DISCOUNTED_RFC, 90);
                        if (discountedRFCResult.isFound()) {
                            discountedRFCFound = true;
                            logDebug("Discounted RFC found on attempt " + attempt);
                        } else {
                            logDebug("Discounted RFC not found on attempt " + attempt);
                            if (attempt < MAX_SEARCH_RETRIES) {
                                sleepTask(RETRY_DELAY_MS);
                            }
                        }
                    }

                    if (discountedRFCFound) {
                        logInfo("50% discounted RFC available. Attempting to claim it now.");

                        // Search for refine button with retry logic
                        DTOImageSearchResult refineRFCResult = null;
                        boolean refineButtonFound = false;

                        for (int attempt = 1; attempt <= MAX_SEARCH_RETRIES && !refineButtonFound; attempt++) {
                            logDebug("Searching for RFC refine button - attempt " + attempt + "/" + MAX_SEARCH_RETRIES);
                            refineRFCResult = emuManager.searchTemplate(EMULATOR_NUMBER, CRYSTAL_LAB_RFC_REFINE_BUTTON, 90);
                            if (refineRFCResult.isFound()) {
                                refineButtonFound = true;
                                tapPoint(refineRFCResult.getPoint());
                                sleepTask(500);
                                logInfo("Discounted RFC claimed successfully.");
                            } else {
                                logDebug("RFC refine button not found on attempt " + attempt);
                                if (attempt < MAX_SEARCH_RETRIES) {
                                    sleepTask(RETRY_DELAY_MS);
                                }
                            }
                        }

                        if (!refineButtonFound) {
                            logInfo("Could not find RFC refine button after " + MAX_SEARCH_RETRIES + " attempts.");
                        }
                    } else {
                        logInfo("No discounted RFC available today.");
                    }
                }
                reschedule(UtilTime.getGameReset());
            } else {
                logInfo("Could not locate Crystal Lab FC button after 10 attempts. Skipping crystal lab tasks.");
            }
        } else {
            logInfo("Could not locate troops button after " + MAX_SEARCH_RETRIES + " attempts. Skipping crystal lab tasks.");
        }
	}

	@Override
	protected EnumStartLocation getRequiredStartLocation() {
		return EnumStartLocation.HOME;
	}
}
