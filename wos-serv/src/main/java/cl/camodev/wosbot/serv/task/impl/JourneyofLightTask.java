package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.almac.repo.DailyTaskRepository;
import cl.camodev.wosbot.almac.repo.IDailyTaskRepository;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.DelayedTask;
import net.sourceforge.tess4j.TesseractException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.EnumMap;

public class JourneyofLightTask extends DelayedTask {
    private final IDailyTaskRepository iDailyTaskRepository = DailyTaskRepository.getRepository();
	public JourneyofLightTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	@Override
	protected void execute() {

		logInfo("Starting Journey of Light task.");
        DTOImageSearchResult dealsResult = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.HOME_DEALS_BUTTON, 90);
        if (!dealsResult.isFound()) {
            logWarning("The 'Deals' button was not found. Retrying in 5 minutes. ");
            reschedule(LocalDateTime.now().plusMinutes(5));
        }
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, dealsResult.getPoint(), dealsResult.getPoint());
        sleepTask(1500);
        // Close any windows that may be open
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(529, 27), new DTOPoint(635, 63), 5, 300);
        sleepTask(100);
        // swipe to leftmost
        emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(100, 150), new DTOPoint(600, 150));
        sleepTask(200);
        emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(100, 150), new DTOPoint(600, 150));
        sleepTask(500);

        // Search for the journey of light menu within deals
        DTOImageSearchResult result1 = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.JOURNEY_OF_LIGHT_TAB, 90);
        DTOImageSearchResult result2 = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.JOURNEY_OF_LIGHT_UNSELECTED_TAB, 90);

        if (!result1.isFound() && !result2.isFound()) {
            logWarning("Journey of Light event not found, removing from schedule.");
            this.recurring = false;
            return;
        }
        DTOImageSearchResult result = result1.isFound() ? result1 : result2;

        // open JOL menu
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
        sleepTask(500);
        logInfo("Successfully navigated to the Journey of Light event.");

        // open JOL actual journey menu (not sure if you can even open it to the other one)
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(50, 220), new DTOPoint(350, 260));
        sleepTask(200);

        // Check if the event has ended
        if (eventHasEnded()) {
            logInfo("Journey of Light event has ended. Removing from schedule.");
            this.recurring = false;
            return;
        }

        // Do the actual JOL things
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(50, 1150), new DTOPoint(290, 1230), 5, 200);

        // fetch remaining time for all 4
        LocalDateTime nextScheduleTime = LocalDateTime.now().plusHours(1000);

        DTOPoint[][] queues = {
                {new DTOPoint(62, 1036), new DTOPoint(166, 1058)},
                {new DTOPoint(234, 1036), new DTOPoint(338, 1058)},
                {new DTOPoint(397, 1036), new DTOPoint(501, 1058)},
                {new DTOPoint(560, 1036), new DTOPoint(664, 1058)},
        };

        for (DTOPoint[] queue : queues) {
            String nextQueueTime = OCRWithRetries(queue[0], queue[1], 5);
            if (nextQueueTime == null) {
                logWarning("Failed to fetch next queue time for queue " + queue[0]);
                continue;
            }
            LocalDateTime nextQueueDateTime = LocalDateTime.now().plusHours(1000);
            try {
                nextQueueDateTime = UtilTime.parseTime(nextQueueTime);
            } catch (Exception e) {
                logWarning("Failed to parse next queue time for queue: " + e.getMessage());
            }
            if (nextQueueDateTime.isBefore(nextScheduleTime)) {
                nextScheduleTime = nextQueueDateTime;
            }
            logInfo("Next queue time for queue " + profile.getName() + ": " + nextQueueTime.toLowerCase());
        }
        // set for when the next one is ready
        logInfo("Next schedule: " + UtilTime.localDateTimeToDDHHMMSS(nextScheduleTime));
        this.reschedule(nextScheduleTime);
	}

    private boolean eventHasEnded() {
        String result = OCRWithRetries("collect", new DTOPoint(50, 300), new DTOPoint(400, 400), 5);
        if (result == null) return false;
        if (!result.isEmpty()) return true;
        return false;
    }

    private String OCRWithRetries(String searchString, DTOPoint p1, DTOPoint p2, int maxRetries) {
        String result = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            result = OCRWithRetries(p1, p2, maxRetries);
            if (result != null && result.contains(searchString)) return result;
            sleepTask(200);
        }
        return null;
    }

    private String OCRWithRetries(DTOPoint p1, DTOPoint p2, int maxRetries) {
        String result = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                result = emuManager.ocrRegionText(EMULATOR_NUMBER, p1, p2);
            } catch (IOException | TesseractException e) {
                logWarning("OCR attempt " + attempt + " threw an exception: " + e.getMessage());
                if (attempt >= maxRetries) return null;
            }
            if (result != null && !result.isEmpty()) return result;
            sleepTask(200);
        }
        return result;
    }
}
