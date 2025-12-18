package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.*;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

import java.awt.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import static cl.camodev.wosbot.console.enumerable.EnumConfigurationKey.*;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.*;
import static cl.camodev.wosbot.serv.task.constants.LeftMenuTextSettings.*;

/**
 * Task responsible for managing automated troop training across multiple troop
 * types.
 * 
 * <p>
 * This task handles:
 * <ul>
 * <li>Training of Infantry, Lancer, and Marksman troops</li>
 * <li>Ministry appointment integration for training optimization</li>
 * <li>Promotion-priority training mode</li>
 * <li>Intelligent scheduling based on queue status</li>
 * </ul>
 * 
 * <p>
 * <b>Ministry Appointment Strategy:</b>
 * <ol>
 * <li>Before appointment: Train maximum troops that finish before appointment
 * starts</li>
 * <li>During appointment: Train maximum troops (30-minute bonus window)</li>
 * <li>After appointment: Train normally</li>
 * </ol>
 * 
 * <p>
 * <b>Queue Status Handling:</b>
 * <ul>
 * <li><b>IDLE:</b> Ready to train</li>
 * <li><b>COMPLETE:</b> Training finished, ready to start new training</li>
 * <li><b>TRAINING:</b> Currently training troops</li>
 * <li><b>UPGRADING:</b> Troops being upgraded, cannot train</li>
 * <li><b>UNKNOWN:</b> Could not determine status (retries automatically)</li>
 * </ul>
 */
public class TrainingTask extends DelayedTask {

    // ===============================
    // CONSTANTS
    // ===============================

    private static final DTOArea INFANTRY_AREA = new DTOArea(
            new DTOPoint(161, 563),
            new DTOPoint(289, 588));

    private static final DTOArea LANCER_AREA = new DTOArea(
            new DTOPoint(161, 636),
            new DTOPoint(289, 664));

    private static final DTOArea MARKSMAN_AREA = new DTOArea(
            new DTOPoint(161, 708),
            new DTOPoint(289, 739));

    private static final DTOPoint TRAINING_CAMP_TAP_MIN = new DTOPoint(310, 650);
    private static final DTOPoint TRAINING_CAMP_TAP_MAX = new DTOPoint(450, 730);

    private static final DTOPoint MINISTRY_CONFIRM_MIN = new DTOPoint(440, 770);
    private static final DTOPoint MINISTRY_CONFIRM_MAX = new DTOPoint(580, 800);

    private static final DTOPoint MINISTRY_DETAILS_MIN = new DTOPoint(534, 692);
    private static final DTOPoint MINISTRY_DETAILS_MAX = new DTOPoint(633, 720);

    private static final DTOPoint MINISTRY_MORE_DETAILS_MIN = new DTOPoint(532, 1057);
    private static final DTOPoint MINISTRY_MORE_DETAILS_MAX = new DTOPoint(617, 1152);

    private static final DTOPoint TROOP_COUNT_INPUT_MIN = new DTOPoint(470, 1038);
    private static final DTOPoint TROOP_COUNT_INPUT_MAX = new DTOPoint(615, 1085);

    private static final DTOPoint TRAIN_TIME_TOP_LEFT = new DTOPoint(427, 1202);
    private static final DTOPoint TRAIN_TIME_BOTTOM_RIGHT = new DTOPoint(654, 1237);

    private static final DTOPoint TRAIN_TIME_STARTED_TOP_LEFT = new DTOPoint(434, 998);
    private static final DTOPoint TRAIN_TIME_STARTED_BOTTOM_RIGHT = new DTOPoint(584, 1028);

    private static final DTOPoint PROMOTION_TIME_TOP_LEFT = new DTOPoint(398, 897);
    private static final DTOPoint PROMOTION_TIME_BOTTOM_RIGHT = new DTOPoint(642, 935);

    private static final DTOPoint MINISTRY_TIME_TOP_LEFT = new DTOPoint(397, 1069);
    private static final DTOPoint MINISTRY_TIME_BOTTOM_RIGHT = new DTOPoint(596, 1094);

    private static final DTOPoint POPUP_DISMISS_MIN = new DTOPoint(1, 0);
    private static final DTOPoint POPUP_DISMISS_MAX = new DTOPoint(720, 0);

    private static final DTOPoint TAB_SWIPE_RIGHT_START = new DTOPoint(610, 140);
    private static final DTOPoint TAB_SWIPE_RIGHT_END = new DTOPoint(130, 140);
    private static final DTOPoint TAB_SWIPE_LEFT_START = new DTOPoint(500, 128);
    private static final DTOPoint TAB_SWIPE_LEFT_END = new DTOPoint(630, 143);

    private static final DTOPoint TROOP_LIST_LEFT = new DTOPoint(73, 785);
    private static final DTOPoint TROOP_LIST_RIGHT = new DTOPoint(690, 785);
    private static final DTOPoint TROOP_SCROLL_START = new DTOPoint(530, 773);
    private static final DTOPoint TROOP_SCROLL_END = new DTOPoint(490, 773);

    private static final DTOPoint PROMOTION_CONFIRM_POINT = new DTOPoint(523, 900);

    private static final int MAX_QUEUE_STATUS_RETRIES = 3;
    private static final int MAX_TEMPLATE_SEARCH_ATTEMPTS = 3;
    private static final int SOON_READY_THRESHOLD_MINUTES = 3;
    private static final int UPGRADING_RESCHEDULE_MINUTES = 10;
    private static final int TRAINING_BUTTON_RETRY_MINUTES = 5;
    private static final int MINISTRY_PROTECTION_WINDOW_MINUTES = 30;
    private static final int MAX_SUNFIRE_TAB_SWIPES = 3;
    private static final int TAB_RESET_SWIPES = 2;

    // ===============================
    // FIELDS
    // ===============================

    private List<DTOArea> queuesToCheck;
    private List<TroopType> enabledTroopTypes;
    private boolean trainInfantry;
    private boolean trainLancer;
    private boolean trainMarksman;
    private boolean prioritizePromotion;
    private boolean ministryAppointmentEnabled;
    private TroopType troopTypeBeingTrained;
    private boolean isPromotionTraining;
    private LocalDateTime promotionCompletionTime;
    private LocalDateTime appointmentTime;
    private final TextRecognitionRetrier<LocalDateTime> trainingTimeHelper;

    // ===============================
    // CONSTRUCTOR
    // ===============================

    /**
     * Constructs a new TrainingTask.
     * 
     * @param profile The game profile this task operates on
     * @param tpTask  The task type enum from the daily task registry
     */
    public TrainingTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
        this.trainingTimeHelper = new TextRecognitionRetrier<>(provider);
    }

    // ===============================
    // CONFIGURATION
    // ===============================

    /**
     * Loads task configuration from the profile.
     * 
     * <p>
     * Configuration loaded:
     * <ul>
     * <li><b>TRAIN_INFANTRY_BOOL:</b> Enable Infantry training</li>
     * <li><b>TRAIN_LANCER_BOOL:</b> Enable Lancer training</li>
     * <li><b>TRAIN_MARKSMAN_BOOL:</b> Enable Marksman training</li>
     * <li><b>TRAIN_PRIORITIZE_PROMOTION_BOOL:</b> Prioritize promotions</li>
     * <li><b>TRAIN_MINISTRY_APPOINTMENT_BOOL:</b> Enable ministry optimization</li>
     * <li><b>TRAIN_MINISTRY_APPOINTMENT_TIME_LONG:</b> Current appointment
     * time</li>
     * </ul>
     */
    protected void loadConfiguration() {
        this.trainInfantry = profile.getConfig(TRAIN_INFANTRY_BOOL, Boolean.class);
        this.trainLancer = profile.getConfig(TRAIN_LANCER_BOOL, Boolean.class);
        this.trainMarksman = profile.getConfig(TRAIN_MARKSMAN_BOOL, Boolean.class);
        this.prioritizePromotion = profile.getConfig(TRAIN_PRIORITIZE_PROMOTION_BOOL, Boolean.class);
        this.ministryAppointmentEnabled = profile.getConfig(TRAIN_MINISTRY_APPOINTMENT_BOOL, Boolean.class);

        Long appointmentTimestamp = profile.getConfig(TRAIN_MINISTRY_APPOINTMENT_TIME_LONG, Long.class);
        if (appointmentTimestamp != null && appointmentTimestamp > 0) {
            this.appointmentTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(appointmentTimestamp),
                    ZoneId.systemDefault());
        } else {
            this.appointmentTime = LocalDateTime.MIN;
        }

        logInfo(String.format(
                "Configuration loaded - Infantry: %s, Lancer: %s, Marksman: %s, Promotion Priority: %s, Ministry: %s",
                trainInfantry, trainLancer, trainMarksman, prioritizePromotion, ministryAppointmentEnabled));
    }

    // ===============================
    // MAIN EXECUTION
    // ===============================

    /**
     * Main execution method for the training task.
     * 
     * <p>
     * <b>Execution Flow:</b>
     * <ol>
     * <li>Build list of queues to check based on configuration</li>
     * <li>Analyze all queue statuses</li>
     * <li>Handle special cases (soon ready, all training, upgrading)</li>
     * <li>Update ministry appointment if needed</li>
     * <li>Train troops in ready queues</li>
     * <li>Extract completion times and reschedule</li>
     * </ol>
     * 
     * <p>
     * All execution paths ensure the task is properly rescheduled.
     */
    @Override
    protected void execute() {
        logInfo("=== Starting Training Task ===");

        loadConfiguration();
        buildQueuesList();

        if (queuesToCheck.isEmpty()) {
            handleNoQueuesSelected();
            return;
        }

        List<QueueInfo> analyzedQueues = analyzeAllQueues();

        if (handleSoonReadyQueue(analyzedQueues))
            return;
        if (handleAllTrainingQueues(analyzedQueues))
            return;
        if (handleUpgradingQueues(analyzedQueues))
            return;

        List<QueueInfo> readyQueues = filterReadyQueues(analyzedQueues);

        if (readyQueues.isEmpty()) {
            handleNoReadyQueues();
            return;
        }

        updateMinistryAppointmentIfNeeded();

        // Collect completion times from BOTH newly trained AND already-training queues
        List<LocalDateTime> allCompletionTimes = new ArrayList<>();

        // Add completion times from queues already training
        allCompletionTimes.addAll(extractExistingCompletionTimes(analyzedQueues));

        // Add completion times from newly trained queues
        List<LocalDateTime> newCompletionTimes = trainAllReadyQueues(readyQueues);
        allCompletionTimes.addAll(newCompletionTimes);

        rescheduleToEarliestCompletion(allCompletionTimes);
    }

    /**
     * Builds the list of queue areas to check based on configuration.
     * 
     * <p>
     * Only adds queues for troop types that are enabled in the configuration.
     */
    private void buildQueuesList() {
        queuesToCheck = new ArrayList<>();
        enabledTroopTypes = new ArrayList<>();

        if (trainInfantry) {
            queuesToCheck.add(INFANTRY_AREA);
            enabledTroopTypes.add(TroopType.INFANTRY);
        }
        if (trainLancer) {
            queuesToCheck.add(LANCER_AREA);
            enabledTroopTypes.add(TroopType.LANCER);
        }
        if (trainMarksman) {
            queuesToCheck.add(MARKSMAN_AREA);
            enabledTroopTypes.add(TroopType.MARKSMAN);
        }
    }

    /**
     * Handles the case when no troop types are selected for training.
     * 
     * <p>
     * Disables recurring execution since there's nothing to do.
     * User can re-enable troop types and manually trigger the task from the UI.
     */
    private void handleNoQueuesSelected() {
        logInfo("No troop types selected for training. Disabling task.");
        setRecurring(false);
    }

    /**
     * Handles the case when no queues are ready for training.
     * 
     * <p>
     * This can happen if all queues are currently training or upgrading,
     * but weren't caught by earlier special case handlers.
     * 
     * <p>
     * Reschedules to check again soon.
     */
    private void handleNoReadyQueues() {
        logInfo("No queues are ready for training. Rescheduling check shortly.");
        reschedule(LocalDateTime.now().plusMinutes(TRAINING_BUTTON_RETRY_MINUTES));
    }

    // ===============================
    // QUEUE ANALYSIS
    // ===============================

    /**
     * Analyzes all configured queues and returns their current status.
     * 
     * <p>
     * For each queue, attempts to determine if it's:
     * <ul>
     * <li>IDLE - Ready to train</li>
     * <li>COMPLETE - Training finished</li>
     * <li>TRAINING - Currently training (includes completion time)</li>
     * <li>UPGRADING - Troops being upgraded</li>
     * <li>UNKNOWN - Could not determine (triggers retries)</li>
     * </ul>
     * 
     * <p>
     * Automatically retries queues with UNKNOWN status up to 3 times.
     * 
     * @return List of QueueInfo containing status for each configured queue
     */
    private List<QueueInfo> analyzeAllQueues() {
        marchHelper.openLeftMenuCitySection(true);
        List<QueueInfo> result = new ArrayList<>();

        emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);

        for (int i = 0; i < queuesToCheck.size(); i++) {
            DTOArea queueArea = queuesToCheck.get(i);
            TroopType troopType = enabledTroopTypes.get(i);

            logInfo("Analyzing queue for " + troopType.name());
            QueueInfo queueInfo = analyzeQueueState(queueArea, troopType);
            result.add(queueInfo);
        }

        result = retryUnknownQueues(result);
        marchHelper.closeLeftMenu();
        return result;
    }

    /**
     * Retries queue analysis for any queues with UNKNOWN status.
     * 
     * <p>
     * Performs up to {@value #MAX_QUEUE_STATUS_RETRIES} retry attempts,
     * recapturing screenshots between attempts to handle temporary OCR failures.
     * 
     * @param initialResults Initial queue analysis results
     * @return Updated list with resolved queue statuses
     */
    private List<QueueInfo> retryUnknownQueues(List<QueueInfo> initialResults) {
        List<Integer> unknownIndices = findUnknownQueueIndices(initialResults);

        if (unknownIndices.isEmpty()) {
            return initialResults;
        }

        logInfo("Found " + unknownIndices.size() + " queues with UNKNOWN status. Performing retries.");

        for (int attempt = 1; attempt <= MAX_QUEUE_STATUS_RETRIES; attempt++) {
            if (unknownIndices.isEmpty())
                break;

            logInfo("Retry attempt " + attempt + "/" + MAX_QUEUE_STATUS_RETRIES);

            marchHelper.openLeftMenuCitySection(true);
            emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);

            unknownIndices = retryUnknownQueuesOnce(initialResults, unknownIndices);
        }

        logUnresolvedQueues(unknownIndices);
        return initialResults;
    }

    /**
     * Finds indices of queues that have UNKNOWN status.
     * 
     * @param results List of queue analysis results
     * @return List of indices where status is UNKNOWN
     */
    private List<Integer> findUnknownQueueIndices(List<QueueInfo> results) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).status() == QueueStatus.UNKNOWN) {
                indices.add(i);
            }
        }
        return indices;
    }

    /**
     * Performs a single retry attempt for queues with UNKNOWN status.
     * 
     * @param results        The result list to update
     * @param unknownIndices Indices of queues to retry
     * @return Updated list of indices that are still UNKNOWN
     */
    private List<Integer> retryUnknownQueuesOnce(
            List<QueueInfo> results,
            List<Integer> unknownIndices) {

        List<Integer> stillUnknown = new ArrayList<>();

        for (int queueIndex : unknownIndices) {
            TroopType troopType = enabledTroopTypes.get(queueIndex);
            DTOArea queueArea = queuesToCheck.get(queueIndex);

            logDebug("Retrying queue: " + troopType.name());

            QueueInfo newInfo = analyzeQueueState(queueArea, troopType);

            if (newInfo.status() != QueueStatus.UNKNOWN) {
                logInfo("Queue " + troopType.name() + " resolved to: " + newInfo.status());
                results.set(queueIndex, newInfo);
            } else {
                stillUnknown.add(queueIndex);
            }
        }

        return stillUnknown;
    }

    /**
     * Logs warning messages for queues that remain UNKNOWN after all retries.
     * 
     * @param unknownIndices Indices of unresolved queues
     */
    private void logUnresolvedQueues(List<Integer> unknownIndices) {
        if (!unknownIndices.isEmpty()) {
            logWarning("After " + MAX_QUEUE_STATUS_RETRIES + " retries, " +
                    unknownIndices.size() + " queues still have UNKNOWN status.");

            for (int index : unknownIndices) {
                logWarning("Queue " + enabledTroopTypes.get(index).name() + " remains UNKNOWN");
            }
        } else {
            logInfo("All queues successfully identified after retries.");
        }
    }

    /**
     * Analyzes the state of a single training queue.
     * 
     * <p>
     * Attempts to read the queue status using multiple OCR configurations
     * to handle different text colors and formats.
     * 
     * @param queueArea Screen area containing the queue status
     * @param troopType Type of troop for this queue
     * @return QueueInfo containing the determined status and ready time if
     *         applicable
     */
    private QueueInfo analyzeQueueState(DTOArea queueArea, TroopType troopType) {
        DTOTesseractSettings[] settingsToTry = {
                WHITE_SETTINGS,
                WHITE_NUMBERS,
                ORANGE_SETTINGS,
                GREEN_TEXT_SETTINGS
        };

        QueueInfo stateInfo = checkForStateKeywords(queueArea, troopType, settingsToTry);
        if (stateInfo != null) {
            return stateInfo;
        }

        return checkForTrainingTime(queueArea, troopType, settingsToTry);
    }

    /**
     * Checks for state keywords (IDLE, UPGRADING, COMPLETE) in the queue area.
     * 
     * @param queueArea     Screen area to check
     * @param troopType     Type of troop for logging
     * @param settingsToTry Array of OCR settings to attempt
     * @return QueueInfo if a keyword is found, null otherwise
     */
    private QueueInfo checkForStateKeywords(
            DTOArea queueArea,
            TroopType troopType,
            DTOTesseractSettings[] settingsToTry) {

        for (DTOTesseractSettings settings : settingsToTry) {
            try {
                String text = stringHelper.execute(
                        queueArea.topLeft(),
                        queueArea.bottomRight(),
                        1,
                        300L,
                        settings,
                        s -> !s.isEmpty(),
                        s -> s);

                if (text != null && !text.trim().isEmpty()) {
                    String lowerText = text.trim().toLowerCase();

                    if (lowerText.contains("idle")) {
                        logInfo(troopType + " queue is IDLE");
                        return new QueueInfo(troopType, QueueStatus.IDLE, null);
                    }

                    if (lowerText.contains("upgrading") || lowerText.contains("upgrade")) {
                        logInfo(troopType + " queue is UPGRADING");
                        return new QueueInfo(troopType, QueueStatus.UPGRADING, null);
                    }

                    if (lowerText.contains("complete")) {
                        logInfo(troopType + " queue is COMPLETE");
                        return new QueueInfo(troopType, QueueStatus.COMPLETE, null);
                    }
                }
            } catch (Exception e) {
                logWarning("Error extracting queue state text: " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Attempts to extract training completion time from the queue area.
     * 
     * <p>
     * Tries multiple OCR configurations to handle different text formats.
     * If successful, returns TRAINING status with the completion time.
     * 
     * @param queueArea     Screen area to check
     * @param troopType     Type of troop for logging
     * @param settingsToTry Array of OCR settings to attempt
     * @return QueueInfo with TRAINING status and time, or UNKNOWN if extraction
     *         fails
     */
    private QueueInfo checkForTrainingTime(
            DTOArea queueArea,
            TroopType troopType,
            DTOTesseractSettings[] settingsToTry) {

        for (DTOTesseractSettings settings : settingsToTry) {
            try {
                LocalDateTime readyAt = trainingTimeHelper.execute(
                        queueArea,
                        3,
                        10,
                        settings,
                        TimeValidators::isValidTime,
                        text -> LocalDateTime.now().plus(TimeConverters.toDuration(text)));

                if (readyAt != null) {
                    logInfo(troopType + " training ready at: " + readyAt.format(DATETIME_FORMATTER));
                    return new QueueInfo(troopType, QueueStatus.TRAINING, readyAt);
                }
            } catch (Exception e) {
                logWarning("Error extracting training time: " + e.getMessage());
            }
        }

        logWarning("Could not determine state for " + troopType.name() + " queue");
        return new QueueInfo(troopType, QueueStatus.UNKNOWN, null);
    }

    // ===============================
    // SPECIAL CASE HANDLERS
    // ===============================

    /**
     * Handles the case where a queue will be ready very soon.
     * 
     * <p>
     * If any queue will be ready within {@value #SOON_READY_THRESHOLD_MINUTES}
     * minutes,
     * reschedules the task to run exactly when that queue becomes ready.
     * 
     * @param queues List of analyzed queues
     * @return true if rescheduled for soon-ready queue, false otherwise
     */
    private boolean handleSoonReadyQueue(List<QueueInfo> queues) {
        Optional<QueueInfo> soonReady = queues.stream()
                .filter(q -> q.status() == QueueStatus.TRAINING && q.readyAt() != null)
                .filter(q -> Duration.between(LocalDateTime.now(), q.readyAt())
                        .toMinutes() <= SOON_READY_THRESHOLD_MINUTES)
                .findFirst();

        if (soonReady.isPresent()) {
            QueueInfo queue = soonReady.get();
            logInfo(String.format("Queue %s ready in <%d minutes. Rescheduling to %s",
                    queue.type().name(),
                    SOON_READY_THRESHOLD_MINUTES,
                    queue.readyAt().format(DATETIME_FORMATTER)));

            reschedule(queue.readyAt());
            return true;
        }

        return false;
    }

    /**
     * Handles the case where all selected queues are currently training.
     * 
     * <p>
     * Reschedules the task to run when the earliest queue finishes training.
     * 
     * @param queues List of analyzed queues
     * @return true if all queues training and rescheduled, false otherwise
     */
    private boolean handleAllTrainingQueues(List<QueueInfo> queues) {
        boolean allTraining = queues.stream()
                .allMatch(q -> q.status() == QueueStatus.TRAINING);

        if (!allTraining) {
            return false;
        }

        Optional<LocalDateTime> nextReadyTime = queues.stream()
                .map(QueueInfo::readyAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo);

        if (nextReadyTime.isPresent()) {
            logInfo("All queues TRAINING. Rescheduling to earliest completion: " +
                    nextReadyTime.get().format(DATETIME_FORMATTER));

            reschedule(nextReadyTime.get());
            marchHelper.closeLeftMenu();
            return true;
        } else {
            logWarning("All queues TRAINING but couldn't determine next ready time. Continuing.");
            return false;
        }
    }

    /**
     * Handles the case where any queue is in UPGRADING state.
     * 
     * <p>
     * When troops are being upgraded, they cannot be trained or promoted.
     * Reschedules a short check to see when upgrades are complete.
     * 
     * @param queues List of analyzed queues
     * @return true if any queue upgrading and rescheduled, false otherwise
     */
    private boolean handleUpgradingQueues(List<QueueInfo> queues) {
        boolean anyUpgrading = queues.stream()
                .anyMatch(q -> q.status() == QueueStatus.UPGRADING);

        if (anyUpgrading) {
            logInfo("At least one queue UPGRADING. Rescheduling check in " +
                    UPGRADING_RESCHEDULE_MINUTES + " minutes.");

            reschedule(LocalDateTime.now().plusMinutes(UPGRADING_RESCHEDULE_MINUTES));
            return true;
        }

        return false;
    }

    /**
     * Filters queues to find those ready for training.
     * 
     * <p>
     * Ready queues are those with status COMPLETE or IDLE.
     * 
     * @param queues List of analyzed queues
     * @return List containing only queues ready for training
     */
    private List<QueueInfo> filterReadyQueues(List<QueueInfo> queues) {
        return queues.stream()
                .filter(q -> q.status() == QueueStatus.COMPLETE || q.status() == QueueStatus.IDLE)
                .toList();
    }

    // ===============================
    // MINISTRY APPOINTMENT MANAGEMENT
    // ===============================

    /**
     * Updates ministry appointment time if needed.
     * 
     * <p>
     * <b>Ministry Appointment Protection Window:</b>
     * After applying for a ministry position, the player cannot be removed
     * for 30 minutes. This method:
     * <ol>
     * <li>Checks if we're outside the 30-minute protection window</li>
     * <li>If so, navigates to Sunfire Castle and applies for appointment</li>
     * <li>Reads the new appointment time via OCR</li>
     * <li>Updates the profile configuration</li>
     * </ol>
     * 
     * <p>
     * If appointment reading fails, sets appointment to "now" so training
     * proceeds normally (treats as being inside appointment window).
     */
    private void updateMinistryAppointmentIfNeeded() {
        if (!ministryAppointmentEnabled || appointmentTime == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        long minutesSinceAppointment = ChronoUnit.MINUTES.between(appointmentTime, now);

        if (minutesSinceAppointment < MINISTRY_PROTECTION_WINDOW_MINUTES) {
            logInfo(String.format("Ministry appointment protected. %d minutes remaining.",
                    MINISTRY_PROTECTION_WINDOW_MINUTES - minutesSinceAppointment));
            return;
        }

        logInfo("Ministry appointment protection expired. Checking for reappointment.");

        if (!navigateToSunfireCastleTab()) {
            logWarning("Could not navigate to Sunfire Castle. Skipping ministry appointment update.");
            return;
        }

        applyForMinistryAppointment();
        readAndUpdateAppointmentTime();

        persistAppointmentTime();
        
        // Return to home screen for training execution
        navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.HOME);
    }

    /**
     * Navigates to the Sunfire Castle event tab.
     * 
     * <p>
     * Navigation strategy:
     * <ol>
     * <li>Click Events button</li>
     * <li>Search for Sunfire Castle tab</li>
     * <li>If not found, swipe tabs and search again</li>
     * </ol>
     * 
     * @return true if navigation successful, false otherwise
     */
    private boolean navigateToSunfireCastleTab() {
        logInfo("Navigating to Sunfire Castle tab");

        if (!clickEventsButton()) {
            return false;
        }

        dismissPopups();

        DTOImageSearchResult sunfireCastle = templateSearchHelper.searchTemplate(
                EVENTS_SUNFIRE_TAB,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (sunfireCastle.isFound()) {
            tapRandomPoint(sunfireCastle.getPoint(), sunfireCastle.getPoint(), 1, 500);
            return true;
        }

        return searchSunfireCastleWithSwipe();
    }

    /**
     * Clicks the Events button on the home screen.
     * 
     * @return true if button found and clicked, false otherwise
     */
    private boolean clickEventsButton() {
        DTOImageSearchResult eventsButton = templateSearchHelper.searchTemplate(
                HOME_EVENTS_BUTTON,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!eventsButton.isFound()) {
            logWarning("Events button not found");
            return false;
        }

        tapPoint(eventsButton.getPoint());
        sleepTask(1000); // Wait for events menu to open
        return true;
    }

    /**
     * Dismisses any popup dialogs by tapping the top of the screen.
     */
    private void dismissPopups() {
        tapRandomPoint(POPUP_DISMISS_MIN, POPUP_DISMISS_MAX, 5, 200);
    }

    /**
     * Searches for Sunfire Castle tab by swiping through event tabs.
     * 
     * <p>
     * Strategy:
     * <ol>
     * <li>Swipe completely right to reset position</li>
     * <li>Swipe left progressively while searching</li>
     * </ol>
     * 
     * @return true if tab found, false after all swipe attempts
     */
    private boolean searchSunfireCastleWithSwipe() {
        logInfo("Sunfire Castle not immediately visible. Swiping to locate.");

        resetTabPosition();

        for (int attempt = 0; attempt < MAX_SUNFIRE_TAB_SWIPES; attempt++) {
            DTOImageSearchResult sunfireCastle = templateSearchHelper.searchTemplate(
                    EVENTS_SUNFIRE_TAB,
                    SearchConfigConstants.DEFAULT_SINGLE);

            if (sunfireCastle.isFound()) {
                tapRandomPoint(sunfireCastle.getPoint(), sunfireCastle.getPoint(), 1, 500);
                return true;
            }

            logDebug("Sunfire Castle not found. Swiping left (attempt " +
                    (attempt + 1) + "/" + MAX_SUNFIRE_TAB_SWIPES + ")");

            swipe(TAB_SWIPE_LEFT_START, TAB_SWIPE_LEFT_END);
            sleepTask(300); // Wait for swipe animation
        }

        logWarning("Sunfire Castle tab not found after all swipe attempts");
        return false;
    }

    /**
     * Resets tab carousel position by swiping completely right.
     */
    private void resetTabPosition() {
        for (int i = 0; i < TAB_RESET_SWIPES; i++) {
            swipe(TAB_SWIPE_RIGHT_START, TAB_SWIPE_RIGHT_END);
            sleepTask(100); // Brief pause between swipes
        }
        sleepTask(300); // Wait for carousel to settle
    }

    /**
     * Applies for ministry appointment if available.
     * 
     * <p>
     * Opens ministry details and taps the apply button if present.
     */
    private void applyForMinistryAppointment() {
        tapRandomPoint(MINISTRY_DETAILS_MIN, MINISTRY_DETAILS_MAX, 1, 300);
        tapRandomPoint(MINISTRY_MORE_DETAILS_MIN, MINISTRY_MORE_DETAILS_MAX, 1, 300);

        DTOImageSearchResult applyButton = templateSearchHelper.searchTemplate(
                SUNFIRE_MINISTRY_APPLY_BUTTON,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (applyButton.isFound()) {
            logInfo("Applying for ministry appointment");
            tapRandomPoint(applyButton.getPoint(), applyButton.getPoint(), 1, 1000);
            tapRandomPoint(MINISTRY_CONFIRM_MIN, MINISTRY_CONFIRM_MAX, 1, 2500);
        } else {
            logInfo("Apply button not found. May already have active appointment.");
        }
    }

    /**
     * Reads the current appointment time via OCR and updates the field.
     * 
     * <p>
     * If reading fails, sets appointment time to "now" so training proceeds
     * normally by treating it as being inside the appointment window.
     */
    private void readAndUpdateAppointmentTime() {
        Duration activeAppointmentTime = durationHelper.execute(
                MINISTRY_TIME_TOP_LEFT,
                MINISTRY_TIME_BOTTOM_RIGHT,
                5,
                200L,
                DTOTesseractSettings.builder()
                        .setRemoveBackground(true)
                        .setTextColor(new Color(121, 136, 155))
                        .setAllowedChars("0123456789:d")
                        .build(),
                text -> TimeValidators.isValidTime(normalizeMinistryTimeText(text)),
                text -> TimeConverters.toDuration(normalizeMinistryTimeText(text)));

        if (activeAppointmentTime != null) {
            appointmentTime = LocalDateTime.now().plusSeconds(activeAppointmentTime.getSeconds());
            logInfo("Ministry appointment time: " + appointmentTime.format(DATETIME_FORMATTER));
        } else {
            logInfo("Could not read appointment time. Setting to now for normal training.");
            appointmentTime = LocalDateTime.now();
        }
    }

    private String normalizeMinistryTimeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim().toLowerCase().replace(" ", "");
        if (normalized.startsWith(":")) {
            normalized = normalized.substring(1);
        }
        return normalized.replace("d:", "d");
    }

    /**
     * Persists the appointment time to profile configuration.
     */
    private void persistAppointmentTime() {
        long timestamp = appointmentTime.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        ServConfig.getServices().updateProfileConfig(
                profile,
                TRAIN_MINISTRY_APPOINTMENT_TIME_LONG,
                String.valueOf(timestamp));
    }

    // ===============================
    // TRAINING EXECUTION
    // ===============================

    /**
     * Trains all ready queues and collects their completion times.
     * 
     * <p>
     * For each ready queue:
     * <ol>
     * <li>Navigate to the troop type</li>
     * <li>Determine optimal troop count (ministry-aware or max)</li>
     * <li>Execute training</li>
     * <li>Extract completion time</li>
     * </ol>
     * 
     * @param readyQueues List of queues ready for training
     * @return List of completion times for rescheduling
     */
    private List<LocalDateTime> trainAllReadyQueues(List<QueueInfo> readyQueues) {
        List<LocalDateTime> completionTimes = new ArrayList<>();

        for (QueueInfo queue : readyQueues) {
            troopTypeBeingTrained = queue.type();
            LocalDateTime completionTime = trainSingleQueue(queue);

            if (completionTime != null) {
                completionTimes.add(completionTime);
            }
        }

        return completionTimes;
    }

    /**
     * Trains a single queue and returns the completion time.
     * 
     * @param queue The queue to train
     * @return Completion time, or null if training failed
     */
    private LocalDateTime trainSingleQueue(QueueInfo queue) {
        navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.HOME);
        marchHelper.openLeftMenuCitySection(true);

        promotionCompletionTime = null;
        isPromotionTraining = false;

        DTOArea areaToTap = getQueueArea(queue.type());

        logInfo("Preparing to train " + queue.type().name());
        tapRandomPoint(areaToTap.topLeft(), areaToTap.bottomRight(), 1, 500);

        tapRandomPoint(TRAINING_CAMP_TAP_MIN, TRAINING_CAMP_TAP_MAX, 10, 100);

        if (!openTrainingInterface()) {
            return handleTrainingButtonNotFound(queue);
        }

        // dismissPopups();

        executeTrainingForQueue(queue);

        return extractTrainingCompletionTime();
    }

    /**
     * Gets the queue area for a specific troop type.
     * 
     * @param type The troop type
     * @return DTOArea for that troop type's queue
     */
    private DTOArea getQueueArea(TroopType type) {
        return switch (type) {
            case INFANTRY -> INFANTRY_AREA;
            case LANCER -> LANCER_AREA;
            case MARKSMAN -> MARKSMAN_AREA;
        };
    }

    /**
     * Opens the training interface from the training camp.
     * 
     * @return true if interface opened successfully, false otherwise
     */
    private boolean openTrainingInterface() {
        DTOImageSearchResult trainingButton = templateSearchHelper.searchTemplate(
                BUILDING_BUTTON_TRAIN,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!trainingButton.isFound()) {
            return false;
        }

        tapRandomPoint(trainingButton.getPoint(), trainingButton.getPoint(), 1, 1000);
        return true;
    }

    /**
     * Handles the case when training button is not found.
     * 
     * <p>
     * This could indicate:
     * <ul>
     * <li>Navigation failure</li>
     * <li>UI changes</li>
     * <li>Temporary rendering issues</li>
     * </ul>
     * 
     * <p>
     * Reschedules to retry soon and continues with other queues.
     * 
     * @param queue The queue that couldn't be trained
     * @return null (no completion time)
     */
    private LocalDateTime handleTrainingButtonNotFound(QueueInfo queue) {
        logWarning("Training button not found for " + queue.type().name() +
                ". Will retry in " + TRAINING_BUTTON_RETRY_MINUTES + " minutes.");

        // Note: We don't reschedule here as we want to try other queues first
        // The final reschedule will be handled by rescheduleToEarliestCompletion()
        return null;
    }

    /**
     * Executes the training process for a queue.
     * 
     * <p>
     * Determines training strategy based on ministry appointment:
     * <ul>
     * <li><b>Before appointment:</b> Train troops that finish before
     * appointment</li>
     * <li><b>During/After appointment:</b> Train max troops or use promotion
     * priority</li>
     * </ul>
     * 
     * @param queue The queue to train
     */
    private void executeTrainingForQueue(QueueInfo queue) {
        if (shouldLimitTrainingForAppointment()) {
            executeLimitedTrainingForAppointment(queue);
        } else {
            executeMaximumTraining(queue);
        }
    }

    /**
     * Determines if training should be limited based on ministry appointment.
     * 
     * <p>
     * Returns true if:
     * <ul>
     * <li>Ministry appointment is enabled</li>
     * <li>Appointment time is set</li>
     * <li>Current time is BEFORE appointment starts</li>
     * </ul>
     * 
     * @return true if training should be limited, false otherwise
     */
    private boolean shouldLimitTrainingForAppointment() {
        if (!ministryAppointmentEnabled || appointmentTime == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime appointmentEnd = appointmentTime.plusMinutes(MINISTRY_PROTECTION_WINDOW_MINUTES);

        if (now.isBefore(appointmentTime)) {
            Duration timeUntilAppointment = Duration.between(now, appointmentTime);
            logInfo(String.format("BEFORE appointment window. Time until appointment: %02d:%02d:%02d",
                    timeUntilAppointment.toHours(),
                    timeUntilAppointment.toMinutesPart(),
                    timeUntilAppointment.toSecondsPart()));
            return true;
        } else if (now.isAfter(appointmentTime) && now.isBefore(appointmentEnd)) {
            long minutesIntoAppointment = Duration.between(appointmentTime, now).toMinutes();
            logInfo(String.format("INSIDE appointment window (%d/%d minutes). Training maximum.",
                    minutesIntoAppointment, MINISTRY_PROTECTION_WINDOW_MINUTES));
            return false;
        } else {
            logInfo("AFTER appointment window. Training maximum.");
            return false;
        }
    }

    /**
     * Executes limited training to finish before ministry appointment.
     * 
     * <p>
     * Calculates the maximum number of troops that can finish training
     * before the appointment starts, then trains that amount.
     * 
     * @param queue The queue to train
     */
    private void executeLimitedTrainingForAppointment(QueueInfo queue) {
        LocalDateTime now = LocalDateTime.now();
        // Calculate exact time until appointment (OCR retries provide sufficient safety margin)
        Duration neededTime = Duration.between(now, appointmentTime);

        logInfo(String.format("Calculating limited training. Time until appointment: %02d:%02d:%02d",
                neededTime.toHours(),
                neededTime.toMinutesPart(),
                neededTime.toSecondsPart()));

        // Select highest troop level BEFORE reading training data
        selectHighestTroopLevel(troopTypeBeingTrained);
        
        emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);

        Duration trainTime = extractMaxTrainingTime();
        Integer maxTroops = extractMaxTroopCount();

        if (trainTime == null || maxTroops == null) {
            logWarning("Could not read training data. Training maximum as fallback.");
            // Troop already selected above, just click train
            clickTrainButton();
            return;
        }

        if (maxTroops == 0) {
            logWarning("Max troops is zero. No training possible.");
            return;
        }

        trainOptimalTroopCount(trainTime, maxTroops, neededTime);
    }

    /**
     * Extracts the maximum training time from the UI.
     * 
     * @return Duration for max troop training, or null if extraction fails
     */
    private Duration extractMaxTrainingTime() {
        return durationHelper.execute(
                TRAIN_TIME_TOP_LEFT,
                TRAIN_TIME_BOTTOM_RIGHT,
                3,
                200L,
                DTOTesseractSettings.builder()
                        .setAllowedChars("0123456789")
                        .setRemoveBackground(true)
                        .setTextColor(new Color(254, 254, 254))
                        .build(),
                TimeValidators::isValidTime,
                TimeConverters::toDuration);
    }

    /**
     * Extracts the maximum troop count from the UI.
     * 
     * @return Maximum trainable troops, or null if extraction fails
     */
    private Integer extractMaxTroopCount() {
        return integerHelper.execute(
                TROOP_COUNT_INPUT_MIN,
                TROOP_COUNT_INPUT_MAX,
                5,
                200L,
                DTOTesseractSettings.builder()
                        .setAllowedChars("0123456789")
                        .setRemoveBackground(true)
                        .setTextColor(new Color(254, 254, 254))
                        .build(),
                text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));
    }

    /**
     * Trains the optimal number of troops based on time constraints.
     * 
     * @param trainTime  Time required to train max troops
     * @param maxTroops  Maximum trainable troops
     * @param neededTime Time available before appointment
     */
    private void trainOptimalTroopCount(Duration trainTime, int maxTroops, Duration neededTime) {
        String trainTimeStr = formatDuration(trainTime);
        String neededTimeStr = formatDuration(neededTime);

        logInfo(String.format("Max troops: %d | Train time for max: %s | Time available: %s",
                maxTroops, trainTimeStr, neededTimeStr));

        if (trainTime.compareTo(neededTime) <= 0) {
            trainMaximumTroops(trainTimeStr, neededTimeStr, maxTroops);
        } else {
            trainCalculatedTroops(trainTime, maxTroops, neededTime, trainTimeStr, neededTimeStr);
        }
    }

    /**
     * Trains maximum troops when training time fits within available time.
     * 
     * @param trainTimeStr  Formatted training time string for logging
     * @param neededTimeStr Formatted needed time string for logging
     * @param maxTroops     Maximum troop count
     */
    private void trainMaximumTroops(String trainTimeStr, String neededTimeStr, int maxTroops) {
        logInfo(String.format("✓ Train time (%s) fits within available time (%s). Training MAX: %d",
                trainTimeStr, neededTimeStr, maxTroops));

        clickTrainButton();
    }

    /**
     * Calculates and trains a limited number of troops to fit time constraint.
     * 
     * @param trainTime     Full training duration
     * @param maxTroops     Maximum trainable troops
     * @param neededTime    Available time before appointment
     * @param trainTimeStr  Formatted training time for logging
     * @param neededTimeStr Formatted needed time for logging
     */
    private void trainCalculatedTroops(
            Duration trainTime,
            int maxTroops,
            Duration neededTime,
            String trainTimeStr,
            String neededTimeStr) {

        long secondsPerTroop = trainTime.getSeconds() / maxTroops;
        int troopsToTrain = (int) (neededTime.getSeconds() / secondsPerTroop);

        if (troopsToTrain > maxTroops) {
            troopsToTrain = maxTroops;
        }

        if (troopsToTrain <= 0) {
            logWarning("Calculated troops is zero or negative. Skipping training.");
            return;
        }

        Duration calculatedTrainTime = Duration.ofSeconds(secondsPerTroop * troopsToTrain);
        String calculatedTimeStr = formatDuration(calculatedTrainTime);

        logInfo(String.format("Train time (%s) exceeds available time (%s).",
                trainTimeStr, neededTimeStr));
        logInfo(String.format("Calculated: %d troops (%.1f sec/troop) = ~%s training time",
                troopsToTrain, (double) secondsPerTroop, calculatedTimeStr));

        inputTroopCount(troopsToTrain);
        clickTrainButton();
    }

    /**
     * Inputs a specific troop count into the training interface.
     * 
     * @param count Number of troops to train
     */
    private void inputTroopCount(int count) {
        tapRandomPoint(TROOP_COUNT_INPUT_MIN, TROOP_COUNT_INPUT_MAX, 1, 100);
        emuManager.clearText(EMULATOR_NUMBER, 6);
        emuManager.writeText(EMULATOR_NUMBER, count + "\n");
        sleepTask(1000); // Wait for input to register
    }

    /**
     * Formats a Duration for logging.
     * 
     * @param duration Duration to format
     * @return Formatted string in format "HH:MM:SS"
     */
    private String formatDuration(Duration duration) {
        return String.format("%02d:%02d:%02d",
                duration.toHours(),
                duration.toMinutesPart(),
                duration.toSecondsPart());
    }

    /**
     * Executes maximum training with promotion priority if enabled.
     * 
     * @param queue The queue to train
     */
    private void executeMaximumTraining(QueueInfo queue) {
        logInfo("Training maximum troops (no appointment constraints).");

        if (!ministryAppointmentEnabled && prioritizePromotion) {
            executePromotionPriorityTraining(queue);
        } else {
            selectHighestTroopLevel(troopTypeBeingTrained);
            clickTrainButton();
        }
    }

    /**
     * Executes promotion-priority training logic.
     * 
     * <p>
     * Searches for lower-tier troops to promote before training new troops.
     * If no promotable troops found, falls back to normal training.
     * 
     * @param queue The queue to train
     */
    private void executePromotionPriorityTraining(QueueInfo queue) {
        logInfo("Executing promotion-priority training for " + queue.type().name());

        resetTroopListToEnd();

        int maxLevel = findMaxAvailableTroopLevel(queue.type());

        if (maxLevel == -1) {
            logWarning("No troop levels detected. Falling back to normal training.");
            clickTrainButton();
            return;
        }

        logInfo("Maximum available troop level: " + maxLevel);
        resetTroopListToStart();

        boolean promotionExecuted = attemptTroopPromotions(queue.type(), maxLevel);

        if (promotionExecuted) {
            logInfo("Promotion executed successfully.");
        } else {
            logInfo("No promotable troops found. Executing normal training.");
            clickTrainButton();
        }
    }

    /**
     * Resets the troop list position to the end.
     */
    private void resetTroopListToEnd() {
        logDebug("Resetting troop list to the end.");

        swipe(TROOP_LIST_RIGHT, TROOP_LIST_LEFT);
        sleepTask(100); // Brief pause between swipes

        swipe(TROOP_LIST_RIGHT, TROOP_LIST_LEFT);
        sleepTask(400); // Wait for scroll to settle
    }

    /**
     * Resets the troop list position to the start.
     */
    private void resetTroopListToStart() {
        logDebug("Resetting troop list to the start.");

        swipe(TROOP_LIST_LEFT, TROOP_LIST_RIGHT);
        sleepTask(100); // Brief pause between swipes

        swipe(TROOP_LIST_LEFT, TROOP_LIST_RIGHT);
        sleepTask(400); // Wait for scroll to settle
    }

    /**
     * Finds the maximum available troop level by searching templates.
     * 
     * @param troopType Type of troop to search
     * @return Maximum level (1-11), or -1 if none found
     */
    private int findMaxAvailableTroopLevel(TroopType troopType) {
        List<EnumTemplates> templates = getTroopsTemplates(troopType);
        logDebug("Searching for max level among " + templates.size() + " templates.");

        for (EnumTemplates template : templates) {
            DTOImageSearchResult troop = templateSearchHelper.searchTemplate(
                    template,
                    SearchConfigConstants.STRICT_MATCHING);

            if (troop.isFound()) {
                int level = extractLevelFromTemplateName(template.name());
                if (level > 0) {
                    logInfo("Found highest level: " + level + " (" + template.name() + ")");
                    return level;
                }
            } else {
                swipe(TROOP_SCROLL_END, TROOP_SCROLL_START);
                sleepTask(400); // Wait for scroll animation
            }
        }

        logWarning("No troop templates found.");
        return -1;
    }

    /**
     * Scrolls to the next troop type in the carousel.
     */
    private void scrollToNextTroopType() {
        swipe(TROOP_SCROLL_START, TROOP_SCROLL_END);
        sleepTask(400); // Wait for scroll animation
    }

    /**
     * Extracts numeric level from template name.
     * 
     * @param templateName Template enum name
     * @return Extracted level, or -1 if not found
     */
    private int extractLevelFromTemplateName(String templateName) {
        try {
            String levelStr = templateName.replaceAll("[^0-9]", "");

            if (levelStr.isEmpty()) {
                return -1;
            }

            return Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            logError("Failed to extract level from: " + templateName, e);
            return -1;
        }
    }

    /**
     * Attempts to find and promote troops below the maximum level.
     * 
     * @param troopType Type of troop
     * @param maxLevel  Maximum level available
     * @return true if promotion executed, false otherwise
     */
    private boolean attemptTroopPromotions(TroopType troopType, int maxLevel) {
        List<EnumTemplates> templates = getTroopsTemplates(troopType);
        logInfo("Searching for promotable troops (levels < " + maxLevel + ")");

        for (int i = templates.size() - 1; i >= 0; i--) {
            EnumTemplates template = templates.get(i);
            int templateLevel = extractLevelFromTemplateName(template.name());

            if (templateLevel > 0 && templateLevel < maxLevel) {
                if (attemptSingleTroopPromotion(template)) {
                    return true;
                }
            }
        }

        logInfo("No promotable troops found.");
        return false;
    }

    /**
     * Attempts to promote a single troop type.
     * 
     * @param template Template to search for
     * @return true if promotion executed, false otherwise
     */
    private boolean attemptSingleTroopPromotion(EnumTemplates template) {
        logDebug("Attempting promotion for: " + template.name());

        for (int attempt = 1; attempt <= MAX_TEMPLATE_SEARCH_ATTEMPTS; attempt++) {
            DTOImageSearchResult troop = templateSearchHelper.searchTemplate(
                    template,
                    SearchConfigConstants.DEFAULT_SINGLE);

            if (troop.isFound()) {
                tapPoint(troop.getPoint());
                sleepTask(500); // Wait for details

                DTOImageSearchResult promoteButton = templateSearchHelper.searchTemplate(
                        TRAINING_TROOP_PROMOTE,
                        SearchConfigConstants.DEFAULT_SINGLE);

                if (promoteButton.isFound()) {
                    return executePromotion(template, promoteButton);
                } else {
                    logDebug("Promotion not available for: " + template.name());
                    break;
                }
            } else {
                logDebug("Template not found (attempt " + attempt + "/" +
                        MAX_TEMPLATE_SEARCH_ATTEMPTS + "): " + template.name());
            }

            sleepTask(300); // Brief pause before retry
        }

        logDebug("Promotion attempt failed for: " + template.name());
        scrollToNextTroopType();
        return false;
    }

    /**
     * Executes the promotion process.
     * 
     * @param template      Template being promoted
     * @param promoteButton Promote button search result
     * @return true (promotion considered successful)
     */
    private boolean executePromotion(EnumTemplates template, DTOImageSearchResult promoteButton) {
        logInfo("Executing promotion for: " + template.name());
        isPromotionTraining = true;

        tapRandomPoint(promoteButton.getPoint(), promoteButton.getPoint());
        sleepTask(500); // Wait for confirmation dialog

        capturePromotionCompletionTime();

        tapPoint(PROMOTION_CONFIRM_POINT);
        sleepTask(500); // Wait for promotion to process

        logInfo("Promotion confirmed for: " + template.name());
        return true;
    }

    private void selectHighestTroopLevel(TroopType troopType) {
        List<EnumTemplates> templates = getTroopsTemplates(troopType);
        resetTroopListToEnd();
        for (EnumTemplates template : templates) {
            DTOImageSearchResult troop = templateSearchHelper.searchTemplate(
                    template,
                    SearchConfigConstants.STRICT_MATCHING);

            if (troop.isFound()) {
                tapPoint(troop.getPoint());
                sleepTask(500); // Wait for selection

                DTOImageSearchResult lockedIndicator = templateSearchHelper.searchTemplate(
                        TRAINING_TROOP_LOCKED,
                        SearchConfigConstants.DEFAULT_SINGLE);
                if (lockedIndicator.isFound()) {
                    logInfo("Troop level locked: " + template.name() + ". Continuing search.");
                    continue;
                }

                logInfo("Selected highest troop level: " + template.name());
                return;
            } else {
                swipe(TROOP_SCROLL_END, TROOP_SCROLL_START);
                sleepTask(400); // Wait for scroll animation
            }
        }

        logWarning("Could not select highest troop level.");
    }

    /**
     * Clicks the train button to start training.
     * Note: Assumes highest troop level is already selected before calling this method.
     */
    private void clickTrainButton() {
        DTOImageSearchResult trainButton = templateSearchHelper.searchTemplate(
                TRAINING_TRAIN_BUTTON,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (trainButton.isFound()) {
            tapRandomPoint(trainButton.getPoint(), trainButton.getPoint(), 1, 500);
        } else {
            logWarning("Train button not found.");
        }
    }

    private void capturePromotionCompletionTime() {
        try {
            Duration promotionDuration = durationHelper.execute(
                    PROMOTION_TIME_TOP_LEFT,
                    PROMOTION_TIME_BOTTOM_RIGHT,
                    3,
                    200L,
                    DTOTesseractSettings.builder()
                            .setAllowedChars("0123456789")
                            .build(),
                    TimeValidators::isValidTime,
                    TimeConverters::toDuration);

            if (promotionDuration != null) {
                promotionCompletionTime = LocalDateTime.now().plus(promotionDuration);
                logInfo("Promotion will complete at: " + promotionCompletionTime.format(DATETIME_FORMATTER));
                return;
            }

            logWarning("Could not read promotion completion time.");
        } catch (Exception e) {
            logError("Error extracting promotion completion time: " + e.getMessage());
        }
    }

    /**
     * Extracts the training completion time from the UI.
     * 
     * @return LocalDateTime when training completes, or null if extraction fails
     */
    private LocalDateTime extractTrainingCompletionTime() {
        try {
            if (isPromotionTraining) {
                isPromotionTraining = false;
                if (promotionCompletionTime != null) {
                    LocalDateTime completion = promotionCompletionTime;
                    promotionCompletionTime = null;
                    return completion;
                }
                logWarning("Promotion completion time unavailable, falling back to normal timer.");
            }
            Duration trainingDuration = durationHelper.execute(
                    TRAIN_TIME_STARTED_TOP_LEFT,
                    TRAIN_TIME_STARTED_BOTTOM_RIGHT,
                    3,
                    200L,
                    DTOTesseractSettings.builder()
                            .setAllowedChars("0123456789")
                            .build(),
                    TimeValidators::isValidTime,
                    TimeConverters::toDuration);

            if (trainingDuration != null) {
                LocalDateTime completionTime = LocalDateTime.now().plus(trainingDuration);
                logInfo("Training will complete at: " + completionTime.format(DATETIME_FORMATTER));
                return completionTime;
            }
        } catch (Exception e) {
            logError("Error extracting training completion time: " + e.getMessage());
        }

        return null;
    }

    /**
     * Extracts completion times from queues that are already training.
     * 
     * @param queues List of all analyzed queues
     * @return List of completion times from queues in TRAINING status
     */
    private List<LocalDateTime> extractExistingCompletionTimes(List<QueueInfo> queues) {
        return queues.stream()
                .filter(q -> q.status() == QueueStatus.TRAINING)
                .map(QueueInfo::readyAt)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Gets the list of troop templates for a specific troop type.
     * 
     * <p>
     * Templates are ordered from highest to lowest tier (T11 → T1).
     * 
     * @param type Troop type
     * @return List of templates in descending tier order
     */
    private List<EnumTemplates> getTroopsTemplates(TroopType type) {
        List<EnumTemplates> templates = new ArrayList<>();

        return switch (type) {
            case INFANTRY -> {
                templates.add(TRAINING_INFANTRY_T11);
                templates.add(TRAINING_INFANTRY_T10);
                templates.add(TRAINING_INFANTRY_T9);
                templates.add(TRAINING_INFANTRY_T8);
                templates.add(TRAINING_INFANTRY_T7);
                templates.add(TRAINING_INFANTRY_T6);
                templates.add(TRAINING_INFANTRY_T5);
                templates.add(TRAINING_INFANTRY_T4);
                templates.add(TRAINING_INFANTRY_T3);
                templates.add(TRAINING_INFANTRY_T2);
                templates.add(TRAINING_INFANTRY_T1);
                yield templates;
            }
            case LANCER -> {
                templates.add(TRAINING_LANCER_T11);
                templates.add(TRAINING_LANCER_T10);
                templates.add(TRAINING_LANCER_T9);
                templates.add(TRAINING_LANCER_T8);
                templates.add(TRAINING_LANCER_T7);
                templates.add(TRAINING_LANCER_T6);
                templates.add(TRAINING_LANCER_T5);
                templates.add(TRAINING_LANCER_T4);
                templates.add(TRAINING_LANCER_T3);
                templates.add(TRAINING_LANCER_T2);
                templates.add(TRAINING_LANCER_T1);
                yield templates;
            }
            case MARKSMAN -> {
                templates.add(TRAINING_MARKSMAN_T11);
                templates.add(TRAINING_MARKSMAN_T10);
                templates.add(TRAINING_MARKSMAN_T9);
                templates.add(TRAINING_MARKSMAN_T8);
                templates.add(TRAINING_MARKSMAN_T7);
                templates.add(TRAINING_MARKSMAN_T6);
                templates.add(TRAINING_MARKSMAN_T5);
                templates.add(TRAINING_MARKSMAN_T4);
                templates.add(TRAINING_MARKSMAN_T3);
                templates.add(TRAINING_MARKSMAN_T2);
                templates.add(TRAINING_MARKSMAN_T1);
                yield templates;
            }
        };
    }

    // ===============================
    // RESCHEDULING
    // ===============================

    /**
     * Reschedules the task to the earliest training completion time.
     * 
     * <p>
     * Strategy:
     * <ul>
     * <li>If completion times available: Schedule for earliest to enable continuous training</li>
     * <li>If earliest is very close to appointment (within threshold): Wait for appointment instead</li>
     * <li>If no completion times: Retry soon (training may have failed)</li>
     * </ul>
     * 
     * @param completionTimes List of training completion times
     */
    private void rescheduleToEarliestCompletion(List<LocalDateTime> completionTimes) {
        if (completionTimes.isEmpty()) {
            logInfo("No completion times extracted. Rescheduling retry soon.");
            reschedule(LocalDateTime.now().plusMinutes(TRAINING_BUTTON_RETRY_MINUTES));
            return;
        }

        LocalDateTime earliest = completionTimes.stream()
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().plusMinutes(TRAINING_BUTTON_RETRY_MINUTES));

        // If ministry appointment enabled and earliest completion is very close to appointment,
        // wait for appointment to maximize the bonus window instead of training more limited troops
        if (ministryAppointmentEnabled && appointmentTime != null) {
            if (earliest.isBefore(appointmentTime)) {
                long minutesUntilAppointment = ChronoUnit.MINUTES.between(earliest, appointmentTime);
                
                // If completion is within threshold of appointment, wait for appointment
                if (minutesUntilAppointment <= SOON_READY_THRESHOLD_MINUTES) {
                    logInfo(String.format("Training completes %d min before appointment. Waiting for appointment at %s to maximize bonus.",
                            minutesUntilAppointment,
                            appointmentTime.format(DATETIME_FORMATTER)));
                    reschedule(appointmentTime);
                    return;
                }
                // Otherwise, reschedule to earliest to enable continuous training cycles
                logInfo(String.format("Training completes at %s (%d min before appointment at %s). Will train more limited troops.",
                        earliest.format(DATETIME_FORMATTER),
                        minutesUntilAppointment,
                        appointmentTime.format(DATETIME_FORMATTER)));
            }
        }

        logInfo("Rescheduling to earliest completion: " + earliest.format(DATETIME_FORMATTER));
        reschedule(earliest);
    }

    // ===============================
    // TASK FRAMEWORK OVERRIDES
    // ===============================

    /**
     * Indicates that this task provides progress toward daily missions.
     * 
     * @return true to trigger daily mission progress checks
     */
    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }

    /**
     * Specifies the required starting screen location for this task.
     * 
     * @return HOME as the required start location
     */
    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    // ===============================
    // INNER CLASSES
    // ===============================

    /**
     * Enumeration of troop types for training.
     */
    private enum TroopType {
        INFANTRY,
        LANCER,
        MARKSMAN
    }

    /**
     * Enumeration of possible queue statuses.
     */
    private enum QueueStatus {
        IDLE,
        TRAINING,
        COMPLETE,
        UPGRADING,
        UNKNOWN
    }

    /**
     * Record containing queue information.
     * 
     * @param type    The troop type for this queue
     * @param status  Current status of the queue
     * @param readyAt When training will complete (null if not training)
     */
    private record QueueInfo(TroopType type, QueueStatus status, LocalDateTime readyAt) {
    }
}