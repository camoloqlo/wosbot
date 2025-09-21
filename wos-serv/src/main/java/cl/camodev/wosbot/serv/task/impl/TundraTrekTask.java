package cl.camodev.wosbot.serv.task.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import net.sourceforge.tess4j.TesseractException;

public class TundraTrekTask extends DelayedTask {

    public TundraTrekTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTask) {
        super(profile, tpDailyTask);
    }

    @Override
    public EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    @Override
    protected void execute() {
        if (navigateToTrekSupplies()) {
            // Search for claim button
            DTOImageSearchResult trekClaimButton = emuManager.searchTemplate(EMULATOR_NUMBER,
                    EnumTemplates.TUNDRA_TREK_CLAIM_BUTTON, 90);
            if (trekClaimButton.isFound()) {
                logInfo("Trek Supplies are available. Claiming now...");
                emuManager.tapAtPoint(EMULATOR_NUMBER, trekClaimButton.getPoint());
                sleepTask(2000);
            } else {
                logInfo("Trek Supplies have already been claimed or are not yet available.");
            }
            sleepTask(3000);

            // Do OCR to find next reward time and reschedule
            try {
                String nextRewardTimeStr = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(526, 592),
                        new DTOPoint(627, 616));
                LocalDateTime nextRewardTime = addTimeToLocalDateTime(LocalDateTime.now(), nextRewardTimeStr);
                this.reschedule(nextRewardTime);
                logInfo("Successfully parsed the next reward time. Rescheduling the task for: " + nextRewardTime);
            } catch (IOException | TesseractException | IllegalArgumentException e) {
                logError("Failed to read or parse the next reward time. Rescheduling for 1 hour from now.", e);
                this.reschedule(LocalDateTime.now().plusHours(1));
            }
            
            // Safely exit back to the main screen
            returnToMainScreen();
        } else {
            logError("Failed to navigate to Tundra Trek Supplies after multiple attempts. Rescheduling for 1 hour.");
            returnToMainScreen();
            reschedule(LocalDateTime.now().plusHours(1)); // Reschedule for later
        }
    }

    private boolean navigateToTrekSupplies() {
        logInfo("Navigating to Tundra Trek Supplies...");
        // This sequence of taps is intended to open the event list.
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(3, 513), new DTOPoint(26, 588));
        sleepTask(500);
        emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(110, 270));
        sleepTask(500);
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(20, 250), new DTOPoint(200, 280));
        sleepTask(1000);

        // Now in the event list, search for the supplies by swiping
        for (int i = 0; i < 5; i++) { // Try up to 5 times (swipes)
            DTOImageSearchResult trekSupplies = emuManager.searchTemplate(EMULATOR_NUMBER,
                    EnumTemplates.TUNDRA_TREK_SUPPLIES, 90);

            if (trekSupplies.isFound()) {
                logInfo("Found the Tundra Trek Supplies button.");
                emuManager.tapAtPoint(EMULATOR_NUMBER, trekSupplies.getPoint());
                sleepTask(500);
                // This tap seems necessary to open the final claim screen
                emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(344, 29), new DTOPoint(413, 49));
                sleepTask(3000);
                return true;
            } else {
                logInfo("Tundra Trek Supplies not visible. Swiping down to search... (Attempt " + (i + 1) + "/5)");
                emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(320, 765), new DTOPoint(50, 500));
                sleepTask(500);
            }
        }
        return false;
    }

    private void returnToMainScreen() {
        logInfo("Returning to the main city screen...");
        for (int i = 0; i < 5; i++) { // Try up to 5 times to get back
            DTOImageSearchResult homeResult = emuManager.searchTemplate(EMULATOR_NUMBER,
                    EnumTemplates.GAME_HOME_FURNACE, 90);
            if (homeResult.isFound()) {
                logInfo("Successfully returned to the main screen.");
                return;
            }
            tapBackButton();
            sleepTask(500);
        }
        logWarning("Could not confirm the return to the main screen after multiple back taps.");
    }

    /**
     * Parse time string and add it to the provided base LocalDateTime.
     * 
     * @param baseTime   The base time to which the parsed duration will be added.
     * @param timeString Time string in format "[n]d HH:mm:ss"
     * @return LocalDateTime with the parsed time added
     */
    public static LocalDateTime addTimeToLocalDateTime(LocalDateTime baseTime, String timeString) {
        Pattern pattern = Pattern.compile("(?i).*?(?:(\\d+)\\s*d\\s*)?(\\d{1,2}:\\d{2}:\\d{2}).*", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(timeString.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Time string does not match expected format [n]d HH:mm:ss: " + timeString);
        }

        String daysStr = matcher.group(1); // Optional days component
        String timeStr = matcher.group(2); // Required time component

        int daysToAdd = (daysStr != null) ? Integer.parseInt(daysStr) : 0;

        // Parse time component
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm:ss");
        LocalTime timePart = LocalTime.parse(timeStr, timeFormatter);

        return baseTime
                .plusDays(daysToAdd)
                .plusHours(timePart.getHour())
                .plusMinutes(timePart.getMinute())
                .plusSeconds(timePart.getSecond());
    }

}
