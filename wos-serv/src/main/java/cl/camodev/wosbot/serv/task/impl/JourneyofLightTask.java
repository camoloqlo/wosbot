package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

import java.time.LocalDateTime;

public class JourneyofLightTask extends DelayedTask {

    private TextRecognitionRetrier<LocalDateTime> textHelper;

    public JourneyofLightTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {

        logInfo("Starting Journey of Light task.");

        this.textHelper = new TextRecognitionRetrier<>(provider);

        DTOImageSearchResult dealsResult = templateSearchHelper.searchTemplate(
                EnumTemplates.HOME_DEALS_BUTTON, SearchConfigConstants.DEFAULT_SINGLE);

        if (!dealsResult.isFound()) {
            logWarning("The 'Deals' button was not found. Retrying in 5 minutes. ");
            reschedule(LocalDateTime.now().plusMinutes(5));
        }

        tapPoint(dealsResult.getPoint());
        sleepTask(1500);

        // Try to navigate to the event screen, retrying up to 3 times if necessary
        boolean navigated = navigateToEventScreen();
        for (int i = 0; i < 3 && !navigated; i++) {
            logDebug("Retrying navigation to the Journey of Light event screen. Attempt " + (i + 1) + " of 3.");
            sleepTask(1000);
            navigated = navigateToEventScreen();
        }

        if (!navigated) {
            logWarning(
                    "Failed to navigate to the Journey of Light event screen after 3 attempts. Rescheduling to next reset.");
            reschedule(UtilTime.getGameReset());
            return;
        }

        // Check if the event has ended
        if (eventHasEnded()) {
            logInfo("Journey of Light event has ended. Rescheduling to next reset.");
            reschedule(UtilTime.getGameReset());
            return;
        }

        // Do the actual JOL things
        tapRandomPoint(new DTOPoint(50, 1150), new DTOPoint(290, 1230), 5, 200);

        // fetch remaining time for all 4
        LocalDateTime nextScheduleTime = LocalDateTime.now().plusHours(1000);

        DTOPoint[][] queues = {
                { new DTOPoint(62, 1036), new DTOPoint(166, 1058) },
                { new DTOPoint(234, 1036), new DTOPoint(338, 1058) },
                { new DTOPoint(397, 1036), new DTOPoint(501, 1058) },
                { new DTOPoint(560, 1036), new DTOPoint(664, 1058) },
        };

        DTOTesseractSettings settings = DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setAllowedChars("0123456789:")
                .build();

        for (DTOPoint[] queue : queues) {
            LocalDateTime nextQueueTime = textHelper.execute(
                    queue[0],
                    queue[1],
                    3,
                    200L,
                    settings,
                    TimeValidators::isValidTime,
                    TimeConverters::toLocalDateTime);

            if (nextQueueTime == null) {
                logWarning("Failed to fetch next queue time for queue " + queue[0]);
                continue;
            }

            if (nextQueueTime.isBefore(nextScheduleTime)) {
                nextScheduleTime = nextQueueTime;
            }
            logInfo("Next queue time for queue " + profile.getName() + ": "
                    + UtilTime.localDateTimeToDDHHMMSS(nextQueueTime));
        }

        // set for when the next one is ready
        logInfo("Next task scheduled to run in: " + UtilTime.localDateTimeToDDHHMMSS(nextScheduleTime));
        reschedule(nextScheduleTime);

        sleepTask(200);
        checkAndClaimFreeWatches();
        for (int i = 0; i < 3; i++) {
            sleepTask(500);
            tapBackButton();
        }
    }

    private boolean navigateToEventScreen() {
        // Close any windows that may be open
        tapRandomPoint(new DTOPoint(529, 27), new DTOPoint(635, 63), 5, 300);

        // Search for the Journey of Light menu within deals
        DTOImageSearchResult result1 = templateSearchHelper.searchTemplate(
                EnumTemplates.JOURNEY_OF_LIGHT_TAB, SearchConfigConstants.DEFAULT_SINGLE);
        DTOImageSearchResult result2 = templateSearchHelper.searchTemplate(
                EnumTemplates.JOURNEY_OF_LIGHT_UNSELECTED_TAB, SearchConfigConstants.DEFAULT_SINGLE);

        if (result1.isFound() || result2.isFound()) {
            logInfo("Successfully navigated to the Journey of Light event.");
            sleepTask(500);
            tapPoint(result1.isFound() ? result1.getPoint() : result2.getPoint());
            sleepTask(1000);

            // Tap "Journey of Light" tab to make sure "My Treasures" tab is not active
            tapRandomPoint(new DTOPoint(50, 220), new DTOPoint(350, 260));
            sleepTask(500);

            return true;
        }

        return false;
    }

    private boolean eventHasEnded() {
        String result = stringHelper.execute(
                new DTOPoint(50, 300),
                new DTOPoint(400, 400),
                1,
                300L,
                null,
                s -> !s.isEmpty(),
                s -> s);
        if (result == null)
            return false;
        return result.contains("collect");
    }

    private void checkAndClaimFreeWatches() {
        DTOImageSearchResult result = templateSearchHelper.searchTemplate(
                EnumTemplates.JOURNEY_OF_LIGHT_FREE_WATCHES, SearchConfigConstants.DEFAULT_SINGLE);

        if (!result.isFound()) {
            logInfo("No free watches found, skipping claim.");
            return;
        }

        tapPoint(result.getPoint());
        sleepTask(500);

        DTOImageSearchResult freeWatch = templateSearchHelper.searchTemplate(
                EnumTemplates.JOURNEY_OF_LIGHT_CLAIM_WATCHES, SearchConfigConstants.DEFAULT_SINGLE);

        if (!freeWatch.isFound()) {
            logInfo("No free watches found, skipping claim.");
            return;
        }

        tapPoint(freeWatch.getPoint());
    }
}
