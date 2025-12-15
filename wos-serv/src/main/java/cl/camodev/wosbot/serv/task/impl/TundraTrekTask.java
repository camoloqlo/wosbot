package cl.camodev.wosbot.serv.task.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

public class TundraTrekTask extends DelayedTask {

    public TundraTrekTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    public EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.ANY;
    }

    @Override
    protected void execute() {
        if (navigateToTrekSupplies()) {
            // Search for claim button
            DTOImageSearchResult trekClaimButton = templateSearchHelper.searchTemplate(
                    EnumTemplates.TUNDRA_TREK_CLAIM_BUTTON,
                    SearchConfigConstants.DEFAULT_SINGLE);
            if (trekClaimButton.isFound()) {
                logInfo("Trek Supplies are available. Claiming now...");
                tapPoint(trekClaimButton.getPoint());
                sleepTask(3000);
            } else {
                logInfo("Trek Supplies have already been claimed or are not yet available.");
                sleepTask(500);
            }

            // Do OCR to find next reward time and reschedule
            try {
                Duration nextRewardTimeDuration = durationHelper.execute(
                        new DTOPoint(526, 592),
                        new DTOPoint(627, 616),
                        3,
                        200L,
                        null,
                        TimeValidators::isValidTime,
                        TimeConverters::toDuration);
                LocalDateTime nextRewardTime = LocalDateTime.now().plus(nextRewardTimeDuration);
                reschedule(nextRewardTime);
                logInfo("Successfully parsed the next reward time. Rescheduling the task for: "
                        + nextRewardTime.format(DATETIME_FORMATTER));
            } catch (IllegalArgumentException e) {
                logError("Failed to read or parse the next reward time. Rescheduling for 1 hour from now.", e);
                reschedule(LocalDateTime.now().plusHours(1));
            }
        } else {
            logError("Failed to navigate to Tundra Trek Supplies after multiple attempts. Rescheduling for 1 hour.");
            reschedule(LocalDateTime.now().plusHours(1)); // Reschedule for later
        }
    }

    private boolean navigateToTrekSupplies() {
        logInfo("Navigating to Tundra Trek Supplies...");

        // Open left menu on city section
        marchHelper.openLeftMenuCitySection(true);

        for (int i = 0; i < 5; i++) { // Try up to 5 times (swipes)
            DTOImageSearchResult trekSupplies = templateSearchHelper.searchTemplate(
                    EnumTemplates.TUNDRA_TREK_SUPPLIES,
                    SearchConfigConstants.DEFAULT_SINGLE);

            if (trekSupplies.isFound()) {
                logInfo("Found the Tundra Trek Supplies button.");
                tapPoint(trekSupplies.getPoint());
                sleepTask(1000);

                // Open supplies claim screen
                tapRandomPoint(new DTOPoint(500, 29), new DTOPoint(590, 49));
                sleepTask(2000);
                return true;
            } else {
                logInfo("Tundra Trek Supplies not visible. Swiping down to search... (Attempt " + (i + 1) + "/5)");
                swipe(new DTOPoint(320, 765), new DTOPoint(50, 500));
                sleepTask(1000);
            }
        }
        return false;
    }
}
