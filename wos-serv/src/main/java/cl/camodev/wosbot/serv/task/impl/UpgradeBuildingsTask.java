package cl.camodev.wosbot.serv.task.impl;


import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.*;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import org.jetbrains.annotations.NotNull;


import java.awt.Color;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static cl.camodev.ButtonContants.*;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.BUILDING_BUTTON_INFO;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.BUILDING_BUTTON_SPEED;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.BUILDING_BUTTON_TRAIN;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.BUILDING_BUTTON_UPGRADE;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.BUILDING_SURVIVOR_BUTTON_UPGRADE;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.GAME_HOME_SHORTCUTS_HELP_REQUEST;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.GAME_HOME_SHORTCUTS_HELP_REQUEST1;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.GAME_HOME_SHORTCUTS_OBTAIN;


public class UpgradeBuildingsTask extends DelayedTask {

    // OCR Settings for different types of text detection
    private static final DTOTesseractSettings WHITE_SETTINGS = DTOTesseractSettings.builder()
            .setRemoveBackground(true)
            .setTextColor(new Color(255,255,255))
            .setReuseLastImage(true)
            .setDebug(true)
            .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
            .build();

    private static final DTOTesseractSettings WHITE_NUMBERS = DTOTesseractSettings.builder()
            .setRemoveBackground(true)
            .setTextColor(new Color(255,255,255))
            .setReuseLastImage(true)
            .setAllowedChars("0123456789d")
            .setDebug(true)
            .build();

    private static final DTOTesseractSettings RED_SETTINGS = DTOTesseractSettings.builder()
            .setRemoveBackground(true)
            .setTextColor(new Color(243,59,59))
            .setReuseLastImage(true)
            .setDebug(true)
            .build();


    private static final DTOTesseractSettings ORANGE_SETTINGS = DTOTesseractSettings.builder()
            .setRemoveBackground(true)
            .setTextColor(new Color(237, 138, 33))
            .setReuseLastImage(true)
            .setDebug(true)
            .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
            .build();

    // Upgrade queue areas
    private static final DTOArea QUEUE_AREA_1 = new DTOArea(new DTOPoint(108, 377), new DTOPoint(358, 398));
    private static final DTOArea QUEUE_AREA_2 = new DTOArea(new DTOPoint(108,450), new DTOPoint(358, 474));

    // List of queue areas to check
    private final List<DTOArea> queues = new ArrayList<>(Arrays.asList(QUEUE_AREA_1, QUEUE_AREA_2));

    /**
     * Constructor for UpgradeFurnaceTask
     *
     * @param profile The profile configuration
     * @param tpDailyTaskEnum The daily task type
     */
    public UpgradeBuildingsTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTaskEnum) {
        super(profile, tpDailyTaskEnum);
    }

    /**
     * Main execution method that handles the entire upgrade process.
     * Analyzes queues, decides which to process, and schedules future runs.
     */
    @Override
    protected void execute() {
        logInfo("Starting Upgrade Minor Buildings task");

        // Navigate to city view
        navigateToCityView();

        // Analyze all queues and store results
        List<UpgradeBuildingsTask.QueueAnalysisResult> queueResults = analyzeAllQueues();

        // Log summary of all queues
        logQueueSummary(queueResults);

        // Process idle queues or reschedule based on busy queues
        boolean hasIdleQueue = queueResults.stream()
                .anyMatch(result -> result.state.status == UpgradeBuildingsTask.QueueStatus.IDLE ||
                        result.state.status == UpgradeBuildingsTask.QueueStatus.IDLE_TEMP);

        if (hasIdleQueue) {
            // Process idle queues
            for (UpgradeBuildingsTask.QueueAnalysisResult result : queueResults) {
                if (result.state.status == UpgradeBuildingsTask.QueueStatus.IDLE ||
                        result.state.status == UpgradeBuildingsTask.QueueStatus.IDLE_TEMP) {
                    logInfo("Processing queue " + result.queueNumber + " (Status: " + result.state.status + ")");
                    processQueue(result);
                }
            }

            ensureCorrectScreenLocation(EnumStartLocation.HOME);

            logInfo("Reanalyzing queues after processing idle queues...");
            sleepTask(1000); // Pequeña pausa para que el UI se actualice

            navigateToCityView();

            // Reanalizar todas las colas
            List<UpgradeBuildingsTask.QueueAnalysisResult> updatedResults = analyzeAllQueues();

            // Log del resumen actualizado
            logInfo("=== Updated Queue Analysis After Processing ===");
            logQueueSummary(updatedResults);

            // Reprogramar con base en las colas ocupadas
            rescheduleBasedOnBusyQueues(updatedResults);
            closeLeftMenu();
        } else {
            // No idle queues, reschedule based on shortest busy queue
            rescheduleBasedOnBusyQueues(queueResults);
        }
    }

    /**
     * Navigates to the city view screen
     */
    private void navigateToCityView() {
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, LEFT_MENU.topLeft(), LEFT_MENU.bottomRight(), 1, 1000);
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, LEFT_MENU_CITY_TAB.topLeft(), LEFT_MENU_CITY_TAB.bottomRight(), 1, 1000);
    }

    /**
     * Analyzes all building queues and returns their states
     *
     * @return List of queue analysis results
     */
    private List<UpgradeBuildingsTask.QueueAnalysisResult> analyzeAllQueues() {
        List<UpgradeBuildingsTask.QueueAnalysisResult> results = new ArrayList<>();

        try {
            // Capture screenshot once for all OCR operations
            emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);

            int queueIndex = 1;
            for (DTOArea queueArea : queues) {
                logInfo("Analyzing queue " + queueIndex);

                // Analyze queue state
                UpgradeBuildingsTask.QueueState state = analyzeQueueState(queueArea);

                // Store result
                UpgradeBuildingsTask.QueueAnalysisResult result = new UpgradeBuildingsTask.QueueAnalysisResult(queueIndex, queueArea, state);
                results.add(result);

                // Log queue state
                logQueueState(queueIndex, state);

                queueIndex++;
            }
        } catch (Exception e) {
            logError("Error analyzing construction queues: " + e.getMessage());
        }

        return results;
    }

    /**
     * Logs the state of a queue
     *
     * @param queueIndex The queue number
     * @param state The queue state
     */
    private void logQueueState(int queueIndex, UpgradeBuildingsTask.QueueState state) {
        switch (state.status) {
            case IDLE:
                logInfo("Queue " + queueIndex + " is IDLE - available for use");
                break;
            case BUSY:
                logInfo("Queue " + queueIndex + " is BUSY - Time remaining: " + state.timeRemaining);
                break;
            case NOT_PURCHASED:
                logInfo("Queue " + queueIndex + " is NOT PURCHASED - needs to be acquired");
                break;
            case IDLE_TEMP:
                logInfo("Queue " + queueIndex + " is IDLE_TEMP - detected by orange color");
                break;
            case UNKNOWN:
                logWarning("Queue " + queueIndex + " state is UNKNOWN - OCR failed to detect state");
                break;
        }
    }

    /**
     * Logs a summary of all analyzed queues
     *
     * @param queueResults List of queue analysis results
     */
    private void logQueueSummary(List<UpgradeBuildingsTask.QueueAnalysisResult> queueResults) {
        logInfo("=== Queue Analysis Summary ===");
        for (UpgradeBuildingsTask.QueueAnalysisResult result : queueResults) {
            logInfo(result.toString());
        }
    }

    /**
     * Processes a queue by tapping on it and handling the upgrade dialog
     *
     * @param queueResult The queue to process
     */
    private void processQueue(UpgradeBuildingsTask.QueueAnalysisResult queueResult) {
        // Navigate to city view to ensure we're in the right screen
        navigateToCityView();
        sleepTask(500);

        // Tap on the queue area
        emuManager.tapAtRandomPoint(
                EMULATOR_NUMBER,
                queueResult.queueArea.topLeft(),
                queueResult.queueArea.bottomRight()
        );
        sleepTask(500);

        // Check for survivor building or city building
        DTOImageSearchResult lowBuilding = searchTemplateWithRetries(BUILDING_BUTTON_INFO, 90, 5, 100);

        if (lowBuilding.isFound()) {
            // Survivor building flow
            tapPoint(new DTOPoint(lowBuilding.getPoint().getX() + 100, lowBuilding.getPoint().getY()));
            processSurvivorBuilding();
        } else {
            // Try to find city building
            tapRandomPoint(new DTOPoint(338, 799), new DTOPoint(353, 807), 3, 100);
            DTOImageSearchResult upgradeButton = searchTemplateWithRetries(BUILDING_BUTTON_UPGRADE, 90, 5, 100);

            if (upgradeButton.isFound()) {
                processCityBuilding();
            } else {
                // Check if this is a troop training building
                processTroopBuilding();
            }
        }
    }

    /**
     * Processes a troop training building
     */
    private void processTroopBuilding() {
        DTOImageSearchResult train = emuManager.searchTemplate(EMULATOR_NUMBER, BUILDING_BUTTON_TRAIN, 90);

        if (train.isFound()) {
            logInfo("A troop training building was found. Rescheduling the task until the training is complete.");

            DTOImageSearchResult speedupButton = emuManager.searchTemplate(EMULATOR_NUMBER, BUILDING_BUTTON_SPEED, 90);

            if (speedupButton.isFound()) {
                tapRandomPoint(speedupButton.getPoint(), speedupButton.getPoint(), 1, 500);

                Duration trainingTime = durationHelper.execute(
                        new DTOPoint(292, 284),
                        new DTOPoint(432, 314),
                        5,
                        300,
                        null,
                        TimeValidators::isHHmmss,
                        TimeConverters::hhmmssToDuration);

                if (trainingTime == null) {
                    return;
                }

                logInfo("A skill is currently being trained. Rescheduling task to run after training completes in " +
                        trainingTime.toMinutes() + " minutes.");
                reschedule(LocalDateTime.now().plus(trainingTime).minusSeconds(5));
            }
        }
    }

    /**
     * Processes a city building upgrade
     */
    private void processCityBuilding() {
        logInfo("Handling City Building");

        // Try to find upgrade button
        DTOImageSearchResult upgradeButton = searchTemplateWithRetries(BUILDING_BUTTON_UPGRADE, 90, 5, 100);

        if (!upgradeButton.isFound()) {
            logWarning("Upgrade button not found");
            this.setRecurring(false);
            return;
        }

        // Tap upgrade button
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, upgradeButton.getPoint(), upgradeButton.getPoint());
        sleepTask(1000);

        // Check if resources need replenishing
        refillResourcesIfNeeded();

        // Confirm upgrade
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(489, 1034), new DTOPoint(500, 1050));

        // Try to request help
        requestHelp();
    }

    /**
     * Processes a survivor building upgrade
     */
    private void processSurvivorBuilding() {
        logInfo("Handling Survivor Building");

        // Try to find survivor upgrade button with retries
        int limit = 100;
        DTOImageSearchResult survivorUpgrade;

        while (!(survivorUpgrade = searchTemplateWithRetries(BUILDING_SURVIVOR_BUTTON_UPGRADE, 90, 2, 10)).isFound()) {
            tapRandomPoint(new DTOPoint(560, 640), new DTOPoint(650, 690), 1, 200);
            limit--;
            if (limit <= 0) {
                break;
            }
        }

        // Tap upgrade button
        tapRandomPoint(survivorUpgrade.getPoint(), survivorUpgrade.getPoint(), 1, 1000);

        // Check if resources need replenishing
        refillResourcesIfNeeded();

        // Confirm upgrade
        tapRandomPoint(new DTOPoint(450, 1190), new DTOPoint(600, 1230), 1, 1000);

        // Try to request help
        DTOImageSearchResult helpButton = searchTemplateWithRetries(GAME_HOME_SHORTCUTS_HELP_REQUEST, 90, 10, 10);
        if (helpButton.isFound()) {
            tapRandomPoint(helpButton.getPoint(), helpButton.getPoint(), 1, 1000);
            sleepTask(500);
            tapRandomPoint(new DTOPoint(540, 1200), new DTOPoint(700, 1250), 1, 1000);
        }
    }

    /**
     * Refills resources if needed for an upgrade
     */
    private void refillResourcesIfNeeded() {
        DTOImageSearchResult result;
        while ((result = emuManager.searchTemplate(EMULATOR_NUMBER, GAME_HOME_SHORTCUTS_OBTAIN, 90)).isFound()) {
            logInfo("Refilling resources for the upgrade...");
            emuManager.tapAtRandomPoint(EMULATOR_NUMBER, result.getPoint(), result.getPoint());
            sleepTask(500);

            // Click replenish button
            emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(358, 1135));
            sleepTask(300);

            // Confirm replenish
            emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(511, 1056));
            sleepTask(1000);
        }
    }

    /**
     * Requests help from alliance members for an upgrade
     */
    private void requestHelp() {
        // Search for help buttons in parallel
        CompletableFuture<DTOImageSearchResult> helpFuture = CompletableFuture.supplyAsync(() ->
                searchTemplateWithRetries(GAME_HOME_SHORTCUTS_HELP_REQUEST, 90, 10, 10)
        );

        CompletableFuture<DTOImageSearchResult> help1Future = CompletableFuture.supplyAsync(() ->
                searchTemplateWithRetries(GAME_HOME_SHORTCUTS_HELP_REQUEST1, 90, 10, 10)
        );

        CompletableFuture.allOf(helpFuture, help1Future).join();

        DTOImageSearchResult helpButton = helpFuture.join();
        DTOImageSearchResult helpButton1 = help1Future.join();

        if (helpButton.isFound()) {
            tapRandomPoint(helpButton.getPoint(), helpButton.getPoint(), 1, 0);
        }
        if (helpButton1.isFound()) {
            tapRandomPoint(helpButton1.getPoint(), helpButton1.getPoint(), 1, 0);
        }
    }

    /**
     * Analyzes the state of a queue using OCR with different text color configurations
     *
     * @param queueArea The area to analyze
     * @return QueueState representing the queue's current status
     */
    private UpgradeBuildingsTask.QueueState analyzeQueueState(DTOArea queueArea) {
        try {
            // Try all OCR configurations in order
            DTOTesseractSettings[] settingsToTry = {
                    WHITE_SETTINGS,
                    WHITE_NUMBERS,
                    RED_SETTINGS,
                    ORANGE_SETTINGS,
            };

            for (DTOTesseractSettings settings : settingsToTry) {
                String ocrText = emuManager.ocrRegionText(
                        EMULATOR_NUMBER,
                        queueArea.topLeft(),
                        queueArea.bottomRight(),
                        settings
                ).trim();

                logDebug("OCR result with settings " + settings.getClass().getSimpleName() + ": '" + ocrText + "'");

                // Check for "Idle" state (case-insensitive)
                if (ocrText.toLowerCase().contains("idle")) {

                    if (settings == ORANGE_SETTINGS) {
                        logDebug("Orange 'idle' text detected - IDLE_TEMP");
                        return new UpgradeBuildingsTask.QueueState(UpgradeBuildingsTask.QueueStatus.IDLE_TEMP, null);
                    } else {
                        return new UpgradeBuildingsTask.QueueState(UpgradeBuildingsTask.QueueStatus.IDLE, null);
                    }
                }

                // Check for "Purchase Queue" state (case insensitive)
                if (ocrText.toLowerCase().contains("purchase") ||
                        ocrText.toLowerCase().contains("queue")) {
                    return new UpgradeBuildingsTask.QueueState(UpgradeBuildingsTask.QueueStatus.NOT_PURCHASED, null);
                }

                // Check for time duration format: xxd hhmmss or hhmmss (without colons)
                if (ocrText.matches(".*(\\d+d\\s*)?\\d{6}.*")) {
                    // Clean up the text to extract time
                    String cleanedTime = ocrText.replaceAll("[^0-9d]", "").trim();
                    if (!cleanedTime.isEmpty()) {
                        return new UpgradeBuildingsTask.QueueState(UpgradeBuildingsTask.QueueStatus.BUSY, cleanedTime);
                    }
                }
            }

            // If no configuration detected a valid state
            return new UpgradeBuildingsTask.QueueState(UpgradeBuildingsTask.QueueStatus.UNKNOWN, null);

        } catch (Exception e) {
            logError("Error during OCR analysis: " + e.getMessage());
            return new UpgradeBuildingsTask.QueueState(UpgradeBuildingsTask.QueueStatus.UNKNOWN, null);
        }
    }


    /**
     * Reschedules the task based on the shortest busy queue time
     *
     * @param queueResults List of queue analysis results
     */
    private void rescheduleBasedOnBusyQueues(List<UpgradeBuildingsTask.QueueAnalysisResult> queueResults) {
        logInfo("No IDLE queues available. Checking BUSY queues to reschedule...");

        // Filter only BUSY queues and find the one with minimum time
        UpgradeBuildingsTask.QueueAnalysisResult shortestBusyQueue = queueResults.stream()
                .filter(result -> result.state.status == UpgradeBuildingsTask.QueueStatus.BUSY && result.state.timeRemaining != null)
                .min((q1, q2) -> {
                    long time1 = parseTimeToMinutes(q1.state.timeRemaining);
                    long time2 = parseTimeToMinutes(q2.state.timeRemaining);
                    return Long.compare(time1, time2);
                })
                .orElse(null);

        if (shortestBusyQueue != null) {
            long minutesToWait = parseTimeToMinutes(shortestBusyQueue.state.timeRemaining);
            LocalDateTime rescheduleTime = LocalDateTime.now().plusMinutes(minutesToWait);

            logInfo("Shortest busy queue: Queue " + shortestBusyQueue.queueNumber +
                    " with " + shortestBusyQueue.state.timeRemaining + " remaining");
            logInfo("Rescheduling task for: " + rescheduleTime + " (in " + minutesToWait + " minutes)");

            this.reschedule(rescheduleTime);
        } else {
            // No busy queues with time info, reschedule for default time
            LocalDateTime rescheduleTime = LocalDateTime.now().plusHours(1);
            logWarning("No BUSY queues with time information found. Rescheduling for 1 hour: " + rescheduleTime);
            this.reschedule(rescheduleTime);
        }
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation(){
        return EnumStartLocation.HOME;
    }

    /**
     * Parses time string to total minutes
     *
     * @param timeString Time string in various formats
     * @return Total minutes
     */
    private long parseTimeToMinutes(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return 0;
        }

        try {
            long totalMinutes = 0;
            String timePart = timeString.trim();

            // Extract days if present
            if (timePart.toLowerCase().contains("d")) {
                String[] daysPart = timePart.toLowerCase().split("d");
                if (daysPart.length > 0) {
                    String daysStr = daysPart[0].replaceAll("[^0-9]", "");
                    if (!daysStr.isEmpty()) {
                        int days = Integer.parseInt(daysStr);
                        totalMinutes += (long) days * 24 * 60; // Convert days to minutes
                    }
                }
                // Get the time part after 'd'
                if (daysPart.length > 1) {
                    timePart = daysPart[1].trim();
                } else {
                    return totalMinutes;
                }
            }

            // Clean the time part - remove any non-digit and non-colon characters
            timePart = timePart.replaceAll("[^0-9:]", "");

            if (timePart.isEmpty()) {
                return totalMinutes;
            }

            // Check if format has colons (hh:mm:ss) or not (hhmmss)
            if (timePart.contains(":")) {
                // Parse hh:mm:ss format
                String[] timeParts = timePart.split(":");
                if (timeParts.length >= 2) {
                    // Hours
                    if (!timeParts[0].isEmpty()) {
                        int hours = Integer.parseInt(timeParts[0]);
                        totalMinutes += hours * 60L;
                    }

                    // Minutes
                    if (!timeParts[1].isEmpty()) {
                        int minutes = Integer.parseInt(timeParts[1]);
                        totalMinutes += minutes;
                    }
                }
            } else {
                // Parse hhmmss format (6 digits)
                if (timePart.length() >= 4) {
                    // Extract hours (first 2 digits)
                    String hoursStr = timePart.substring(0, 2);
                    int hours = Integer.parseInt(hoursStr);
                    totalMinutes += hours * 60L;

                    // Extract minutes (next 2 digits)
                    String minutesStr = timePart.substring(2, 4);
                    int minutes = Integer.parseInt(minutesStr);
                    totalMinutes += minutes;
                }
            }

            return totalMinutes;

        } catch (Exception e) {
            logError("Error parsing time string '" + timeString + "': " + e.getMessage());
            return 60; // Default to 1 hour if parsing fails
        }
    }

    /**
     * Enum representing the possible states of a construction queue
     */
    private enum QueueStatus {
        IDLE,           // Queue is available
        BUSY,           // Queue is currently upgrading something
        NOT_PURCHASED,  // Queue needs to be purchased
        IDLE_TEMP,      // Queue is available but is temp queue
        UNKNOWN         // Could not determine state
    }

    /**
     * Class to hold queue state information
     */
    private record QueueState(UpgradeBuildingsTask.QueueStatus status, String timeRemaining) {
    }

    /**
     * Class to hold queue analysis results including queue number and state
     */
    private record QueueAnalysisResult(int queueNumber, DTOArea queueArea, UpgradeBuildingsTask.QueueState state) {

        @Override
        public @NotNull String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Queue ").append(queueNumber).append(": ");
            sb.append(state.status);
            if (state.timeRemaining != null) {
                sb.append(" (").append(state.timeRemaining).append(")");
            }
            return sb.toString();
        }
    }
}
