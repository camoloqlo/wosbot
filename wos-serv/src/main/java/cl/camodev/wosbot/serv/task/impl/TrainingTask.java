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

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import static cl.camodev.LeftMenuTextSettings.*;
import static cl.camodev.wosbot.console.enumerable.EnumConfigurationKey.*;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.*;

public class TrainingTask extends DelayedTask {

    private final DTOArea INFANTRY_AREA = new DTOArea(new DTOPoint(161, 563), new DTOPoint(289, 588));
    private final DTOArea LANCER_AREA = new DTOArea(new DTOPoint(161, 636), new DTOPoint(289, 664));
    private final DTOArea MARKSMAN_AREA = new DTOArea(new DTOPoint(161, 708), new DTOPoint(289, 739));

    // List of queue areas to check
    private List<DTOArea> queues;

    // training settings
    private Boolean trainInfantry;
    private Boolean trainLancer;
    private Boolean trainMarksman;
    private Boolean priorizePromotion;
    private Boolean ministryAppointment;
    private LocalDateTime appointmentTime;

    // private ConcurrentHashMap<TroopType, QueueInfo> queueStatus = new
    // ConcurrentHashMap<>();

    // helper for flexible text recognition
    private final TextRecognitionRetrier<LocalDateTime> trainingTimeHelper;

    public TrainingTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
        this.trainingTimeHelper = new TextRecognitionRetrier<>(provider);
    }

    @Override
    protected void execute() {
        updateTaskConfig();
        // analyze queues that are marked for training in the configuration
        queues = new ArrayList<>();
        if (trainInfantry)
            queues.add(INFANTRY_AREA);
        if (trainLancer)
            queues.add(LANCER_AREA);
        if (trainMarksman)
            queues.add(MARKSMAN_AREA);

        if (queues.isEmpty()) {
            logInfo("No troop types selected for training. Exiting task.");
            this.setRecurring(false);
            return;
        }

        List<QueueInfo> analyzedQueues = analyzeQueues();

        // i need to add a delta time for those queues that are in "TRAINING" state and
        // will be ready in less than 3 minutes
        // maybe i could make this time configurable in the future
        // if that is the case, I'll reschedule the task to run in when that queue is
        // ready
        Optional<QueueInfo> soonReady = analyzedQueues.stream()
                .filter(q -> q.status() == QueueStatus.TRAINING && q.readyAt() != null)
                .filter(q -> Duration.between(LocalDateTime.now(), q.readyAt()).toMinutes() <= 3)
                .findFirst();

        if (soonReady.isPresent()) {
            QueueInfo queue = soonReady.get();
            logInfo("Queue for " + queue.type().name()
                    + " will be ready in less than 3 minutes. Rescheduling task when it's ready at " + queue.readyAt().format(DATETIME_FORMATTER));
            reschedule(queue.readyAt());
            return;
        }

        // if all the queues are in training state, i can reschedule the task to run
        // when most of them are ready
        boolean allTraining = analyzedQueues.stream().allMatch(q -> q.status() == QueueStatus.TRAINING);

        if (allTraining) {
            Optional<LocalDateTime> nextReadyTime = analyzedQueues.stream()
                    .map(QueueInfo::readyAt)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo);

            if (nextReadyTime.isPresent()) {
                logInfo("All selected queues are in TRAINING state. Rescheduling task to run when the next queue is ready at "
                        + nextReadyTime.get().format(DATETIME_FORMATTER));
                reschedule(nextReadyTime.get());
                closeLeftMenu();
                return;
            } else {
                logWarning(
                        "All selected queues are in TRAINING state but could not determine next ready time. Continuing with task execution.");
            }
        }

        // if any of the queues is in "UPGRADING" state i'll execute short reschedules
        // of 10 minutes until all are ready
        boolean anyUpgrading = analyzedQueues.stream().anyMatch(q -> q.status() == QueueStatus.UPGRADING);

        if (anyUpgrading) {
            logInfo("At least one selected queue is in UPGRADING state. Rescheduling task to check again in 10 minutes.");
            reschedule(LocalDateTime.now().plusMinutes(10));
            return;
        }

        // recollect those queues that are in state "COMPLETE" or "IDLE" and are
        // configured to be trained
        List<QueueInfo> readyQueues = analyzedQueues.stream()
                .filter(q -> (q.status() == QueueStatus.COMPLETE || q.status() == QueueStatus.IDLE)).toList();

        if (readyQueues.isEmpty()) {
            logInfo("No queues are ready for training.");
            return;
        }

        if (ministryAppointment && appointmentTime != null) {
            LocalDateTime now = LocalDateTime.now();
            long minutesSinceAppointment = ChronoUnit.MINUTES.between(appointmentTime, now);

            if (minutesSinceAppointment < 30) {
                logInfo("Ministry appointment is protected. " + (30 - minutesSinceAppointment) + " minutes remaining.");
                // User cannot be removed from ministry position yet
            } else {
                logInfo("Ministry appointment protection window has expired. Position can be changed.");

                // go to sunfire castle tab
                boolean sunfireCastleTabFound = navigateToSunfireCastleTab();

                if (!sunfireCastleTabFound) {
                    logError("Sunfire castle tab not found. Cannot proceed to change ministry appointment.");
                } else {
                    tapRandomPoint(new DTOPoint(534, 692), new DTOPoint(633, 720), 1, 300); // appointment details
                    tapRandomPoint(new DTOPoint(532, 1057), new DTOPoint(617, 1152), 1, 300); // moe details

                    // at this point i need to search the "apply" button
                    // if the buttons is there, i need to apply appointment and get the new
                    // appointment time ocring the time
                    // otherwise i need to check if there's an appointment active ocring the same
                    // area
                    // if both cases fails, i need to check if the appointment is active
                    DTOImageSearchResult applyButton = emuManager
                            .searchTemplate(EMULATOR_NUMBER, SUNFIRE_MINISTRY_APPLY_BUTTON, 90);
                    if (applyButton.isFound()) {
                        logInfo("Applying for ministry appointment...");
                        tapRandomPoint(applyButton.getPoint(), applyButton.getPoint(), 1, 1000);
                        tapRandomPoint(new DTOPoint(440, 770), new DTOPoint(580, 800), 1, 2000); // confirmation
                        // now i need to ocr the appointment time
                        Duration newAppointmentTime = durationHelper.execute(
                                new DTOPoint(397, 1069),
                                new DTOPoint(596, 1094),
                                5,
                                200L,
                                DTOTesseractSettings.builder()
                                        .setRemoveBackground(true)
                                        .setTextColor(new Color(121, 136, 155))
                                        .setReuseLastImage(false)
                                        .setAllowedChars("0123456789:")
                                        .build(),
                                TimeValidators::isHHmmss,
                                TimeConverters::hhmmssToDuration);

                        if (newAppointmentTime != null) {
                            appointmentTime = LocalDateTime.now().plusMinutes(newAppointmentTime.getSeconds());
                            logInfo("New ministry appointment time set for: " + appointmentTime.format(DATETIME_FORMATTER));
                        } else {
                            appointmentTime = LocalDateTime.now();
                        }
                    } else {
                        // check if there's an active appointment
                        Duration activeAppointmentTime = durationHelper.execute(
                                new DTOPoint(397, 1069),
                                new DTOPoint(596, 1094),
                                5,
                                200L,
                                DTOTesseractSettings.builder()
                                        .setRemoveBackground(true)
                                        .setTextColor(new Color(121, 136, 155))
                                        .setReuseLastImage(false)
                                        .setAllowedChars("0123456789:d")
                                        .build(),
                                (text) -> {
                                    // text can be ":hh:mm:ss" or "xxd :hh:mm:ss"
                                    String trimmed = text.trim();

                                    // Verificar si tiene formato con días (contiene 'd')
                                    if (trimmed.matches(".*\\d+d\\s*:.*")) {
                                        // Formato: "xxd :hh:mm:ss"
                                        return trimmed.matches("\\d+d\\s*:\\d{1,2}:\\d{2}:\\d{2}");
                                    } else {
                                        // Formato: ":hh:mm:ss"
                                        return trimmed.matches(":\\d{1,2}:\\d{2}:\\d{2}");
                                    }
                                },
                                (text) -> {
                                    String trimmed = text.trim();

                                    long days = 0;
                                    long hours = 0;
                                    long minutes = 0;
                                    long seconds = 0;

                                    // Verificar si tiene formato con días
                                    if (trimmed.contains("d")) {
                                        // Formato: "xxd :hh:mm:ss"
                                        String[] parts = trimmed.split("d\\s*:");
                                        days = Long.parseLong(parts[0].trim());

                                        // Parsear el resto ":hh:mm:ss" -> "hh:mm:ss"
                                        String timePart = parts[1].trim();
                                        String[] timeParts = timePart.split(":");
                                        hours = Long.parseLong(timeParts[0]);
                                        minutes = Long.parseLong(timeParts[1]);
                                        seconds = Long.parseLong(timeParts[2]);
                                    } else {
                                        // Formato: ":hh:mm:ss"
                                        String[] timeParts = trimmed.substring(1).split(":"); // Remover el ':' inicial
                                        hours = Long.parseLong(timeParts[0]);
                                        minutes = Long.parseLong(timeParts[1]);
                                        seconds = Long.parseLong(timeParts[2]);
                                    }

                                    return Duration.ofDays(days)
                                            .plusHours(hours)
                                            .plusMinutes(minutes)
                                            .plusSeconds(seconds);
                                });

                        if (activeAppointmentTime != null) {
                            appointmentTime = LocalDateTime.now().plusSeconds(activeAppointmentTime.getSeconds());
                            logInfo("Existing ministry appointment time found: " + appointmentTime.format(DATETIME_FORMATTER));
                        } else {
                            // if both fails, i need to check if the appointment is active
                            logInfo("Trying to determine if ministry appointment is active...");
                            appointmentTime = LocalDateTime.now();

                        }
                    }
                }

            }

            ServConfig.getServices().updateProfileConfig(profile,
                    TRAIN_MINISTRY_APPOINTMENT_TIME_LONG,
                    String.valueOf(appointmentTime.atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()));
        }

        // procedd to train troops based on configuration
        for (QueueInfo queue : readyQueues) {
            ensureCorrectScreenLocation(EnumStartLocation.HOME);
            openLeftMenuCitySection(true);
            TroopType type = queue.type();
            // i should tap training area based on troop type
            DTOArea areaToTap = switch (type) {
                case INFANTRY -> INFANTRY_AREA;
                case LANCER -> LANCER_AREA;
                case MARKSMAN -> MARKSMAN_AREA;
            };
            logInfo("Preparing going to train " + type.name());
            tapRandomPoint(areaToTap.topLeft(), areaToTap.bottomRight(), 1, 500);

            // in case there's troops to claim, i need to claim them first
            tapRandomPoint(new DTOPoint(310, 650), new DTOPoint(450, 730), 10, 100);

            // search the train button
            DTOImageSearchResult trainingButtonResult = emuManager
                    .searchTemplate(EMULATOR_NUMBER, BUILDING_BUTTON_TRAIN, 90);

            if (!trainingButtonResult.isFound()) {
                // if not found,i need to add a retry logic TODO
                return;
            }
            tapRandomPoint(trainingButtonResult.getPoint(), trainingButtonResult.getPoint(), 1, 1000);
            tapRandomPoint(new DTOPoint(1, 0), new DTOPoint(720, 0), 5, 200);// force to close any possible popup
            boolean shouldMatchAppointment = false;
            Duration neededTime = null;

            // Solo calcular tropas si ministryAppointment está activo Y appointmentTime no
            // es null
            if (ministryAppointment && appointmentTime != null) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime appointmentEnd = appointmentTime.plusMinutes(30);

                // Formato para fechas legibles
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

                logInfo(String.format("Current time: %s | Appointment: %s | Window ends: %s",
                        now.format(formatter),
                        appointmentTime.format(formatter),
                        appointmentEnd.format(formatter)));

                // Check if we're OUTSIDE the 30-minute appointment window
                if (now.isBefore(appointmentTime)) {
                    shouldMatchAppointment = true;
                    neededTime = Duration.between(now, appointmentTime).plusMinutes(1);

                    long hours = neededTime.toHours();
                    long minutes = neededTime.toMinutesPart();
                    long seconds = neededTime.toSecondsPart();

                    logInfo(String.format(
                            "Status: BEFORE appointment window. Need to finish training in %02d:%02d:%02d",
                            hours, minutes, seconds));
                } else if (now.isAfter(appointmentTime) && now.isBefore(appointmentEnd)) {
                    long minutesIntoAppointment = Duration.between(appointmentTime, now).toMinutes();
                    logInfo(String.format(
                            "Status: INSIDE appointment window (%d minutes in, ends at %s). Training normally.",
                            minutesIntoAppointment,
                            appointmentEnd.format(formatter)));
                } else {
                    logInfo(String.format("Status: OUTSIDE appointment window (ended at %s). Training normally.",
                            appointmentEnd.format(formatter)));
                }
            } else {
                // Si ministryAppointment es false o appointmentTime es null
                if (!ministryAppointment) {
                    logInfo("Ministry appointment is DISABLED. Training normally (max troops).");
                } else {
                    logInfo("Ministry appointment is ENABLED but no appointment time is set. Training normally (max troops).");
                }
            }

            if (shouldMatchAppointment && neededTime != null) {
                // now i have to check what's the max time i can train now
                // and i need to know the troops number to do the maths
                emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);
                Duration trainTime = durationHelper.execute(
                        new DTOPoint(427, 1202),
                        new DTOPoint(654, 1237),
                        3,
                        200L,
                        WHITE_DURATION,
                        TimeValidators::isHHmmss,
                        TimeConverters::hhmmssToDuration);

                Integer maxtroops = integerHelper.execute(
                        new DTOPoint(470, 1038),
                        new DTOPoint(615, 1085),
                        5,
                        200L,
                        WHITE_ONLY_NUMBERS,
                        text -> NumberValidators.matchesPattern(text, Pattern.compile(".*?(\\d+).*")),
                        text -> NumberConverters.regexToInt(text, Pattern.compile(".*?(\\d+).*")));

                // in case that the train time is smaller than the appointment time, i can train
                // max troops
                // otherwise i need to calculate how many troops i can train to match the
                // appointment time
                int troopsToTrain;
                if (trainTime != null && maxtroops != null) {
                    // Formatear durations para logs legibles
                    String trainTimeFormatted = String.format("%02d:%02d:%02d",
                            trainTime.toHours(),
                            trainTime.toMinutesPart(),
                            trainTime.toSecondsPart());

                    String neededTimeFormatted = String.format("%02d:%02d:%02d",
                            neededTime.toHours(),
                            neededTime.toMinutesPart(),
                            neededTime.toSecondsPart());

                    logInfo(String.format("Max troops available: %d | Train time for max: %s | Time needed: %s",
                            maxtroops,
                            trainTimeFormatted,
                            neededTimeFormatted));

                    if (trainTime.compareTo(neededTime) <= 0) {
                        // Si el tiempo de entrenamiento es menor o igual al tiempo disponible,
                        // puedo entrenar todas las tropas
                        troopsToTrain = maxtroops;
                        logInfo(String.format("✓ Train time (%s) fits within needed time (%s). Training MAX troops: %d",
                                trainTimeFormatted,
                                neededTimeFormatted,
                                troopsToTrain));

                        DTOImageSearchResult trainButton = emuManager
                                .searchTemplate(EMULATOR_NUMBER, TRAINING_TRAIN_BUTTON, 90);
                        if (trainButton.isFound()) {
                            tapRandomPoint(trainButton.getPoint(), trainButton.getPoint(), 1, 500);
                        }

                    } else {
                        // Si el tiempo de entrenamiento excede el tiempo disponible,
                        // calculo cuántas tropas puedo entrenar
                        long secondsPerTroop = trainTime.getSeconds() / maxtroops;
                        troopsToTrain = (int) (neededTime.getSeconds() / secondsPerTroop);

                        // Asegurar que no exceda max troops
                        if (troopsToTrain > maxtroops) {
                            troopsToTrain = maxtroops;
                        }

                        Duration calculatedTrainTime = Duration.ofSeconds(secondsPerTroop * troopsToTrain);
                        String calculatedTimeFormatted = String.format("%02d:%02d:%02d",
                                calculatedTrainTime.toHours(),
                                calculatedTrainTime.toMinutesPart(),
                                calculatedTrainTime.toSecondsPart());

                        logInfo(String.format("Train time (%s) exceeds needed time (%s).",
                                trainTimeFormatted,
                                neededTimeFormatted));
                        logInfo(String.format("Calculated: %d troops (%.1f seconds/troop) = ~%s training time",
                                troopsToTrain,
                                (double) secondsPerTroop,
                                calculatedTimeFormatted));

                        if (troopsToTrain > 0) {
                            tapRandomPoint(new DTOPoint(470, 1038), new DTOPoint(615, 1085), 1, 100);
                            emuManager.clearText(EMULATOR_NUMBER, 6);
                            emuManager.writeText(EMULATOR_NUMBER, troopsToTrain + "\n");
                            sleepTask(1000);
                            DTOImageSearchResult trainButton = emuManager
                                    .searchTemplate(EMULATOR_NUMBER, TRAINING_TRAIN_BUTTON, 90);
                            if (trainButton.isFound()) {
                                tapRandomPoint(trainButton.getPoint(), trainButton.getPoint(), 1, 500);
                            }

                        } else {
                            logWarning("Calculated troops is zero or negative. Skipping training.");
                        }
                    }
                } else {
                    logWarning(String.format(
                            "Could not read training data (trainTime: %s, maxTroops: %s). Training normally.",
                            trainTime != null ? "OK" : "NULL",
                            maxtroops != null ? maxtroops : "NULL"));
                    DTOImageSearchResult trainButton = emuManager
                            .searchTemplate(EMULATOR_NUMBER, TRAINING_TRAIN_BUTTON, 90);
                    if (trainButton.isFound()) {
                        tapRandomPoint(trainButton.getPoint(), trainButton.getPoint(), 1, 500);
                    }
                }
            } else {
                // Train normally (max troops) in all other cases:
                // - ministryAppointment is false
                // - appointmentTime is null
                // - Inside appointment window
                // - After appointment window

                // im im here i shold train or promote troops based on priorizePromotion setting
                // when ministry appointment disabled
                if (!ministryAppointment && priorizePromotion) {

                }
                logInfo("Training MAX troops (no appointment constraints applied).");
                DTOImageSearchResult trainButton = emuManager
                        .searchTemplate(EMULATOR_NUMBER, TRAINING_TRAIN_BUTTON, 90);
                if (trainButton.isFound()) {
                    tapRandomPoint(trainButton.getPoint(), trainButton.getPoint(), 1, 500);
                }
            }
        }

    }

    /**
     * Navigate to Sunfire Castle section
     */
    private boolean navigateToSunfireCastleTab() {
        logInfo("Navigating to Sunfire Castle tab");

        // Find and click Events button
        DTOImageSearchResult eventsButton = searchTemplateWithRetries(EnumTemplates.HOME_EVENTS_BUTTON);
        if (!eventsButton.isFound()) {
            logWarning("Events button not found");
            return false;
        }

        tapPoint(eventsButton.getPoint());
        sleepTask(1000);

        // Close any popups
        tapRandomPoint(new DTOPoint(1, 0), new DTOPoint(720, 0), 5, 200);

        // Search for Sunfire Castle tab
        DTOImageSearchResult sunfireCastle = searchTemplateWithRetries(EVENTS_SUNFIRE_TAB, 90, 2);

        if (sunfireCastle.isFound()) {
            tapRandomPoint(sunfireCastle.getPoint(), sunfireCastle.getPoint(), 1, 500);
            return true;
        }

        // Tab not immediately visible - try swiping
        logInfo("Sunfire Castle tab not immediately visible. Swiping to locate it.");

        // Swipe completely right first
        for (int i = 0; i < 2; i++) {
            swipe(new DTOPoint(610, 140), new DTOPoint(130, 140));
            sleepTask(100);
        }
        sleepTask(300);

        // Search while swiping left
        for (int attempt = 0; attempt < 3; attempt++) {
            sunfireCastle = searchTemplateWithRetries(EVENTS_SUNFIRE_TAB, 90, 2);

            if (sunfireCastle.isFound()) {
                tapRandomPoint(sunfireCastle.getPoint(), sunfireCastle.getPoint(), 1, 500);
                return true;
            }

            logDebug("Sunfire Castle tab not found. Swiping left (attempt " + (attempt + 1) + "/" + 3 + ")");
            swipe(new DTOPoint(500, 128), new DTOPoint(630, 143));
            sleepTask(300);
        }

        return false;
    }

    private List<QueueInfo> analyzeQueues() {
        openLeftMenuCitySection(true);
        List<QueueInfo> result = new ArrayList<>();

        emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);

        // i need to analyze only those queues that are configured to be trained
        TroopType[] troopTypes = { TroopType.INFANTRY, TroopType.LANCER, TroopType.MARKSMAN };

        for (int i = 0; i < queues.size(); i++) {

            DTOArea queueArea = queues.get(i);
            TroopType troopType = troopTypes[i];

            logInfo("Analyzing queue for " + troopType.name());

            // Analyze the queue and get its status
            QueueInfo queueInfo = analyzeQueueState(queueArea, troopType);

            // Add the result to the list
            result.add(queueInfo);
        }

        // Lista para almacenar índices de colas con estado UNKNOWN
        List<Integer> unknownQueueIndices = new ArrayList<>();

        // Identificar las colas que quedaron con estado UNKNOWN
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).status() == QueueStatus.UNKNOWN) {
                unknownQueueIndices.add(i);
            }
        }

        if (!unknownQueueIndices.isEmpty()) {
            logInfo("Found " + unknownQueueIndices.size() + " queues with UNKNOWN status. Performing retries...");

            // Retry up to 3 times for UNKNOWN queues
            for (int attempt = 1; attempt <= 3; attempt++) {
                if (unknownQueueIndices.isEmpty()) {
                    break; // If there are no more UNKNOWN queues, stop retrying
                }

                logInfo("Retry " + attempt + " of 3 for UNKNOWN queues");
                openLeftMenuCitySection(true);
                // Capture a new screenshot for the retry
                emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);

                // Temporary list to store indices that are no longer UNKNOWN
                List<Integer> resolvedIndices = new ArrayList<>();

                // Retry only for queues with UNKNOWN status
                for (int queueIndex : unknownQueueIndices) {
                    TroopType troopType = troopTypes[queueIndex];
                    DTOArea queueArea = queues.get(queueIndex);

                    logInfo("Retry " + attempt + " for queue " + troopType.name());

                    // Reanalyze the queue
                    QueueInfo newQueueInfo = analyzeQueueState(queueArea, troopType);

                    // If the status is no longer UNKNOWN, update the result and the map
                    if (newQueueInfo.status() != QueueStatus.UNKNOWN) {
                        logInfo("Queue for " + troopType.name() + " now has status: " + newQueueInfo.status());
                        result.set(queueIndex, newQueueInfo);
                        resolvedIndices.add(queueIndex);
                    }
                }

                // Remove resolved indices from the UNKNOWN list
                unknownQueueIndices.removeAll(resolvedIndices);
            }

            // Log if there are still unresolved queues after retries
            if (!unknownQueueIndices.isEmpty()) {
                logWarning("After 3 retries, " + unknownQueueIndices.size() +
                        " queues still have UNKNOWN status.");

                for (int index : unknownQueueIndices) {
                    logWarning("Queue " + troopTypes[index].name() + " remains in UNKNOWN status");
                }
            } else {
                logInfo("All queues have been correctly identified after retries.");
            }
        }

        return result;
    }

    /**
     * Analiza el estado de una cola de entrenamiento de tropas.
     *
     * @param queueArea Área de la pantalla donde se encuentra la cola
     * @param troopType Tipo de tropa asociado a esta cola
     * @return Información sobre el estado de la cola
     */
    private QueueInfo analyzeQueueState(DTOArea queueArea, TroopType troopType) {
        // Configuraciones de Tesseract para probar
        DTOTesseractSettings[] settingsToTry = {
                WHITE_SETTINGS,
                WHITE_NUMBERS,
                ORANGE_SETTINGS,
                GREEN_TEXT_SETTINGS
        };

        // Primer intento: Buscar estado IDLE o UPGRADING
        QueueInfo stateInfo = checkForStateText(queueArea, troopType, settingsToTry);
        if (stateInfo != null) {
            return stateInfo;
        }

        // Segundo intento: Buscar tiempo de entrenamiento
        return checkForTrainingTime(queueArea, troopType, settingsToTry);
    }

    /**
     * Busca texto que indique un estado específico (IDLE, UPGRADING)
     */
    private QueueInfo checkForStateText(DTOArea queueArea, TroopType troopType, DTOTesseractSettings[] settingsToTry) {
        for (DTOTesseractSettings settings : settingsToTry) {
            try {
                String text = emuManager.ocrRegionText(EMULATOR_NUMBER, queueArea.topLeft(), queueArea.bottomRight(),
                        settings);

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
                logWarning("Error al extraer texto para estado de cola: " + e.getMessage());
            }
        }

        return null; // No se encontró un estado conocido
    }

    private QueueInfo checkForTrainingTime(DTOArea queueArea, TroopType troopType,
            DTOTesseractSettings[] settingsToTry) {
        for (DTOTesseractSettings settings : settingsToTry) {
            try {
                // Usar el nuevo trainingTimeHelper que devuelve directamente LocalDateTime
                LocalDateTime readyAt = trainingTimeHelper.execute(
                        queueArea,
                        3,
                        10,
                        settings,
                        this::isValidTrainingTimeFormat, // Validador personalizado
                        text -> convertTimeTextToLocalDateTime(text, troopType) // Conversor que devuelve LocalDateTime
                );

                if (readyAt != null) {
                    return new QueueInfo(troopType, QueueStatus.TRAINING, readyAt);
                }
            } catch (Exception e) {
                logWarning("Error al extraer tiempo de entrenamiento: " + e.getMessage());
            }
        }

        // Si llegamos aquí, no pudimos determinar el estado de la cola
        logWarning("No se pudo determinar el estado de la cola de " + troopType.name());
        return new QueueInfo(troopType, QueueStatus.UNKNOWN, null);
    }

    /**
     * Validador que determina si el texto tiene un formato válido de tiempo de
     * entrenamiento
     */
    private boolean isValidTrainingTimeFormat(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Eliminar cualquier caracter que no sea número o 'd'
        String timePattern = text.replaceAll("[^0-9d]", "");

        // Validar formato: debe tener 'd' seguido de 6 dígitos, o simplemente 6 dígitos
        boolean hasValidFormat = (timePattern.contains("d") && timePattern.split("d")[1].length() == 6)
                || (!timePattern.contains("d") && timePattern.length() == 6);

        if (hasValidFormat) {
            logDebug("Formato de tiempo válido detectado: " + timePattern);
            return true;
        }

        return false;
    }

    /**
     * Converts a time text in format "[n]dHHMMSS" or "HHMMSS" to a LocalDateTime
     * representing when the training will be completed.
     *
     * @param text      Time text to convert
     * @param troopType Type of troop for logging purposes
     * @return LocalDateTime when the training will be completed, or null if parsing
     *         fails
     */
    private LocalDateTime convertTimeTextToLocalDateTime(String text, TroopType troopType) {
        try {
            String timePattern = text.replaceAll("[^0-9d]", "");

            int days = 0;
            String hourMinSec;

            // parsing days if present
            if (timePattern.contains("d")) {
                String[] parts = timePattern.split("d");
                days = Integer.parseInt(parts[0]);
                hourMinSec = parts[1];
            } else {
                hourMinSec = timePattern;
            }

            // parsing HHMMSS
            if (hourMinSec.length() == 6) {
                int hours = Integer.parseInt(hourMinSec.substring(0, 2));
                int minutes = Integer.parseInt(hourMinSec.substring(2, 4));
                int seconds = Integer.parseInt(hourMinSec.substring(4, 6));

                // Calculating the exact LocalDateTime when training will be ready
                LocalDateTime readyAt = LocalDateTime.now()
                        .plusDays(days)
                        .plusHours(hours)
                        .plusMinutes(minutes)
                        .plusSeconds(seconds);

                logInfo(troopType + " training will be ready at: " + readyAt.format(DATETIME_FORMATTER));

                return readyAt;
            } else {
                logWarning("Format time not recognized for " + troopType + ": " + text);
            }
        } catch (Exception e) {
            logError("Error parsing training time for " + troopType + ": " + e.getMessage());
        }

        return null;
    }

    private void updateTaskConfig() {
        this.trainInfantry = profile.getConfig(TRAIN_INFANTRY_BOOL, Boolean.class);
        this.trainLancer = profile.getConfig(TRAIN_LANCER_BOOL, Boolean.class);
        this.trainMarksman = profile.getConfig(TRAIN_MARKSMAN_BOOL, Boolean.class);
        this.priorizePromotion = profile.getConfig(TRAIN_PRIORITIZE_PROMOTION_BOOL, Boolean.class);
        this.ministryAppointment = profile.getConfig(TRAIN_MINISTRY_APPOINTMENT_BOOL, Boolean.class);
        Long appointmentTimestamp = profile.getConfig(TRAIN_MINISTRY_APPOINTMENT_TIME_LONG, Long.class);
        if (appointmentTimestamp != null && appointmentTimestamp > 0) {
            this.appointmentTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(appointmentTimestamp),
                    ZoneId.systemDefault());
        } else {
            this.appointmentTime = LocalDateTime.MIN;
        }
    }

    private enum TroopType {
        INFANTRY,
        LANCER,
        MARKSMAN
    }

    private enum QueueStatus {
        IDLE,
        TRAINING,
        COMPLETE,
        UPGRADING,
        UNKNOWN
    }

    private record QueueInfo(TroopType type, QueueStatus status, LocalDateTime readyAt) {
    }
}
