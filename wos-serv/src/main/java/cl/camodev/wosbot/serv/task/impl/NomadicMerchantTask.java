package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;

import java.time.LocalDateTime;

public class NomadicMerchantTask extends DelayedTask {

    private final EnumTemplates[] TEMPLATES = {EnumTemplates.NOMADIC_MERCHANT_COAL, EnumTemplates.NOMADIC_MERCHANT_MEAT, EnumTemplates.NOMADIC_MERCHANT_STONE, EnumTemplates.NOMADIC_MERCHANT_WOOD};


    public NomadicMerchantTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    protected void execute() {
        logInfo("Starting the Nomadic Merchant task.");

        // STEP 1: Navigate to shop - Search for the bottom bar shop button
        DTOImageSearchResult shopButtonResult = emuManager.searchTemplate(
                EMULATOR_NUMBER,
                EnumTemplates.GAME_HOME_BOTTOM_BAR_SHOP_BUTTON,
                90
        );

        if (!shopButtonResult.isFound()) {
            logWarning("Shop button not found on the main screen. Rescheduling for 1 hour.");
            LocalDateTime nextAttempt = LocalDateTime.now().plusHours(1);
            this.reschedule(nextAttempt);
            return;
        }

        // Tap on shop button and wait for shop to load
        logInfo("Navigating to the shop.");
        tapRandomPoint(shopButtonResult.getPoint(), shopButtonResult.getPoint());
        sleepTask(2000);


        // STEP 2: Main loop to handle all nomadic merchant operations
        boolean continueOperations = true;

        while (continueOperations) {
            // PHASE 1: Search for resource templates until none are found
            boolean foundResourceTemplate = true;
            logInfo("Searching for free resources to claim.");

            while (foundResourceTemplate) {
                foundResourceTemplate = false;

                // Iterate through each resource template
                for (EnumTemplates template : TEMPLATES) {
                    DTOImageSearchResult result = emuManager.searchTemplate(
                            EMULATOR_NUMBER,
                            template,
                            new DTOPoint(25,412),
                            new DTOPoint(690,1200),
                            90
                    );

                    if (result.isFound()) {
                        logInfo("Found resource: " + template.name() + ". Purchasing it.");
                        tapPoint(result.getPoint());
                        sleepTask(500);
                        foundResourceTemplate = true;
                        break; // Restart resource search from beginning
                    }
                }
            }

            // PHASE 2: Check if VIP purchase is enabled and search for VIP templates
            boolean vipBuyEnabled = profile.getConfig(EnumConfigurationKey.BOOL_NOMADIC_MERCHANT_VIP_POINTS, Boolean.class);
            boolean foundVipTemplate = false;

            if (vipBuyEnabled) {
                logInfo("VIP purchase is enabled. Searching for VIP points to buy.");

                // Search for VIP template in the entire screen
                DTOImageSearchResult vipResult = emuManager.searchTemplate(
                        EMULATOR_NUMBER,
                        EnumTemplates.NOMADIC_MERCHANT_VIP,
                        90
                );

                if (vipResult.isFound()) {
                    logInfo("Found VIP points. Purchasing with gems.");
                    // Tap slightly below the VIP template to access purchase options
                    tapPoint(new DTOPoint(vipResult.getPoint().getX(), vipResult.getPoint().getY() + 100));
                    sleepTask(1000);

                    // Tap buy with gems button
                    tapPoint(new DTOPoint(368, 830));
                    sleepTask(1000);

                    // Confirm purchase
                    tapPoint(new DTOPoint(355, 788));
                    sleepTask(1000);

                    foundVipTemplate = true;
                }
            }

            // PHASE 3: If VIP was purchased, recheck for new resource templates
            if (foundVipTemplate) {
                logInfo("VIP points purchased. Re-checking for new resource templates.");
                continue; // Go back to PHASE 1 to check for new resources
            }

            // PHASE 4: Check for daily refresh button if no resources or VIP were found
            logInfo("No more resources or VIP points found. Checking for daily refresh.");
            DTOImageSearchResult dailyRefreshResult = emuManager.searchTemplate(
                    EMULATOR_NUMBER,
                    EnumTemplates.MYSTERY_SHOP_DAILY_REFRESH,
                    90
            );

            if (dailyRefreshResult.isFound()) {
                logInfo("Daily refresh is available. Using it now.");
                tapRandomPoint(dailyRefreshResult.getPoint(), dailyRefreshResult.getPoint());
                sleepTask(2000); // Wait longer for refresh to complete
                // Continue the main loop to check for new items after refresh
            } else {
                // PHASE 5: No refresh available, operations complete
                logInfo("No daily refresh available. All Nomadic Merchant operations are complete.");
                continueOperations = false;
            }
        }
        // Final step: schedule task till game reset
        logInfo("Rescheduling Nomadic Merchant task for the next game reset.");
        reschedule(UtilTime.getGameReset());
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }
}
