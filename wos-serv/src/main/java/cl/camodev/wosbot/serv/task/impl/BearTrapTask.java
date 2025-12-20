package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilRally;
import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.ocr.BotTextRecognitionProvider;
import cl.camodev.wosbot.serv.task.*;
import cl.camodev.wosbot.serv.task.helper.BearTrapHelper;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static cl.camodev.wosbot.console.enumerable.EnumConfigurationKey.*;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.*;

/**
 * Bear Trap Task - Manages automated participation in the Bear Trap event.
 * 
 * <p>
 * This task handles all aspects of the Bear Trap event with time window-based
 * execution:
 * <ul>
 * <li>Preparation phase (recalls troops, activates pets, disables
 * autojoin)</li>
 * <li>Active phase (starts own rally, joins other rallies)</li>
 * <li>Cleanup phase (re-queues disabled tasks)</li>
 * </ul>
 * 
 * <p>
 * <b>Time Window Logic:</b>
 * <ul>
 * <li>Reference date/time marks the trap activation time (end of
 * preparation)</li>
 * <li>Preparation time is subtracted from reference to calculate window
 * START</li>
 * <li>Windows repeat every 2 days from the reference date</li>
 * <li>Example: Reference 21:00, 5min prep → Window: 20:55 to 21:00, trap active
 * until 21:30</li>
 * </ul>
 * 
 * <p>
 * <b>Execution Phases:</b>
 * <ol>
 * <li><b>Preparation Phase</b> (if still before trap activation):
 * <ul>
 * <li>Disable alliance autojoin</li>
 * <li>Recall all gathering troops</li>
 * <li>Activate configured pets</li>
 * <li>Navigate camera to bear trap location</li>
 * <li>Wait for trap auto-activation</li>
 * </ul>
 * </li>
 * <li><b>Active Phase</b> (trap is active for 30 minutes):
 * <ul>
 * <li>Continuously monitor trap duration</li>
 * <li>Start own rally if configured (once per trap)</li>
 * <li>Join other rallies if configured and marches available</li>
 * <li>Continue until trap ends</li>
 * </ul>
 * </li>
 * <li><b>Cleanup Phase</b>:
 * <ul>
 * <li>Reset rally flags</li>
 * <li>Re-queue gather tasks if enabled</li>
 * <li>Re-queue autojoin if enabled</li>
 * <li>Shutdown scheduled executors</li>
 * </ul>
 * </li>
 * </ol>
 * 
 * <p>
 * <b>Special Behaviors:</b>
 * <ul>
 * <li>Task execution is <b>BLOCKING</b> - runs continuously during trap</li>
 * <li>Waits for trap activation without releasing task queue</li>
 * <li>Other tasks do not run during bear trap execution</li>
 * <li>Rally flag automatically resets after rally duration</li>
 * </ul>
 * 
 * <p>
 * <b>Configuration:</b>
 * <ul>
 * <li>Reference time (UTC) - when trap activates</li>
 * <li>Preparation time - minutes before activation to start prep</li>
 * <li>Trap number (1 or 2) - which bear trap to attack</li>
 * <li>Call own rally - whether to start a rally</li>
 * <li>Join rallies - whether to join other players' rallies</li>
 * <li>Use pets - whether to activate pets</li>
 * <li>Recall troops - whether to recall gatherers</li>
 * <li>Rally flags - which flags to use for own/join rallies</li>
 * </ul>
 */
public class BearTrapTask extends DelayedTask {

    // ========== Rally Management ==========
    private final AtomicBoolean ownRallyActive = new AtomicBoolean(false);
    private ScheduledExecutorService rallyScheduler;
    private ScheduledFuture<?> rallyResetTask;
    
    // ========== Join Flag Rotation ==========
    private List<Integer> joinFlags = new ArrayList<>();
    private int currentJoinFlagIndex = 0;

    // ========== OCR Helpers ==========
    private TextRecognitionRetrier<Duration> durationHelper;

    // ========== Bear Trap Constants ==========
    private static final int TRAP_DURATION_MINUTES = 30;
    private static final int TRAP_ACTIVATION_OFFSET_MINUTES = 30;
    private static final int STATUS_LOG_INTERVAL = 10; // Log every 10 iterations

    // ========== Rally Timing Constants ==========
    private static final int OWN_RALLY_MIN_REMAINING_SECONDS = 360; // 6 minutes minimum
    private static final int RALLY_DURATION_BASE_MINUTES = 5;
    private static final int RALLY_DURATION_BUFFER_SECONDS = 3;
    private static final int RALLY_RETURN_BUFFER_MINUTES = 5;

    // ========== Retry Limits ==========
    private static final int MAX_GATHER_RECALL_ATTEMPTS = 120;
    private static final int MARCH_TIME_OCR_MAX_RETRIES = 5;
    private static final int TEMPLATE_SEARCH_RETRIES = 3;
    private static final int TEMPLATE_SEARCH_RETRIES_EXTENDED = 5;
    private static final int TEMPLATE_SEARCH_RETRIES_MAX = 10;

    // ========== UI Navigation - Alliance ==========
    private static final DTOPoint ALLIANCE_BUTTON_TL = new DTOPoint(493, 1187);
    private static final DTOPoint ALLIANCE_BUTTON_BR = new DTOPoint(561, 1240);

    // ========== UI Navigation - Territory and Bear Trap ==========
    private static final DTOPoint SPECIAL_BUILDINGS_BUTTON_TL = new DTOPoint(460, 110);
    private static final DTOPoint SPECIAL_BUILDINGS_BUTTON_BR = new DTOPoint(560, 130);
    private static final DTOPoint BEAR_TRAP_1_GO_BUTTON_TL = new DTOPoint(570, 350);
    private static final DTOPoint BEAR_TRAP_1_GO_BUTTON_BR = new DTOPoint(620, 370);
    private static final DTOPoint BEAR_TRAP_2_GO_BUTTON_TL = new DTOPoint(570, 530);
    private static final DTOPoint BEAR_TRAP_2_GO_BUTTON_BR = new DTOPoint(620, 550);

    // ========== UI Navigation - Bear Trap Attack ==========
    private static final DTOPoint BEAR_CENTER_POINT = new DTOPoint(370, 507);

    // ========== UI Navigation - Pets ==========
    private static final DTOPoint PET_RAZORBACK_TL = new DTOPoint(100, 410);
    private static final DTOPoint PET_RAZORBACK_BR = new DTOPoint(160, 460);
    private static final DTOPoint PET_QUICK_USE_BUTTON_TL = new DTOPoint(120, 1070);
    private static final DTOPoint PET_QUICK_USE_BUTTON_BR = new DTOPoint(280, 1100);
    private static final DTOPoint PET_USE_BUTTON_TL = new DTOPoint(460, 800);
    private static final DTOPoint PET_USE_BUTTON_BR = new DTOPoint(550, 830);

    // ========== UI Navigation - Autojoin ==========
    private static final DTOPoint AUTOJOIN_BUTTON_TL = new DTOPoint(260, 1200);
    private static final DTOPoint AUTOJOIN_BUTTON_BR = new DTOPoint(450, 1240);
    private static final DTOPoint AUTOJOIN_STOP_BUTTON_TL = new DTOPoint(120, 1070);
    private static final DTOPoint AUTOJOIN_STOP_BUTTON_BR = new DTOPoint(240, 1110);

    // ========== UI Navigation - March Recall ==========
    private static final DTOPoint RECALL_CONFIRM_BUTTON_TL = new DTOPoint(446, 780);
    private static final DTOPoint RECALL_CONFIRM_BUTTON_BR = new DTOPoint(578, 800);

    // ========== OCR Regions ==========
    private static final DTOPoint MARCH_TIME_OCR_TL = new DTOPoint(521, 1141);
    private static final DTOPoint MARCH_TIME_OCR_BR = new DTOPoint(608, 1162);
    private static final DTOPoint FREE_MARCHES_OCR_TL = new DTOPoint(203, 200);
    private static final DTOPoint FREE_MARCHES_OCR_BR = new DTOPoint(246, 226);

    // ========== Default Configuration Values ==========
    private static final int DEFAULT_TRAP_NUMBER = 1;
    private static final int DEFAULT_PREPARATION_TIME_MINUTES = 5;
    private static final int DEFAULT_OWN_RALLY_FLAG = 1;
    private static final int DEFAULT_JOIN_RALLY_FLAG = 1;
    private static final boolean DEFAULT_CALL_OWN_RALLY = false;
    private static final boolean DEFAULT_JOIN_RALLY = false;
    private static final boolean DEFAULT_USE_PETS = false;
    private static final boolean DEFAULT_RECALL_TROOPS = false;
    private static final int DEFAULT_FREE_MARCHES_FALLBACK = 1;

    // ========== Configuration (loaded in loadConfiguration()) ==========
    private boolean callOwnRally;
    private boolean joinRally;
    private boolean usePets;
    private boolean recallTroops;
    private int trapNumber;
    private int ownRallyFlag;
    private int trapPreparationTime;
    private LocalDateTime referenceTrapTime;

    // ========== OCR Settings ==========
    private static final DTOTesseractSettings FREE_MARCHES_OCR_SETTINGS = DTOTesseractSettings.builder()
            .setAllowedChars("0123456789/")
            .setRemoveBackground(true)
            .setTextColor(new Color(253, 253, 253))
            .setReuseLastImage(true)
            .build();

    /**
     * Constructs a new BearTrapTask.
     *
     * @param profile the profile this task belongs to
     * @param tpTask  the task type enum
     */
    public BearTrapTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    /**
     * Initializes OCR helpers after EMULATOR_NUMBER is available.
     * Called at the start of execute() to ensure proper initialization.
     */
    private void initializeOCRHelpers() {
        BotTextRecognitionProvider provider = new BotTextRecognitionProvider(emuManager, EMULATOR_NUMBER);
        this.durationHelper = new TextRecognitionRetrier<>(provider);
    }

    /**
     * Loads task configuration from the profile.
     * This must be called from execute() to ensure configuration is current.
     * 
     * <p>
     * Loads:
     * <ul>
     * <li>Reference trap time (UTC) - when trap activates</li>
     * <li>Preparation time in minutes</li>
     * <li>Trap number (1 or 2)</li>
     * <li>Call own rally flag</li>
     * <li>Join rally flag</li>
     * <li>Use pets flag</li>
     * <li>Recall troops flag</li>
     * <li>Own rally flag number</li>
     * <li>Join rally flag number</li>
     * </ul>
     * 
     * <p>
     * All configuration values have sensible defaults if not set.
     */
    private void loadConfiguration() {
        this.referenceTrapTime = getConfigDateTime(BEAR_TRAP_SCHEDULE_DATETIME_STRING);
        this.trapPreparationTime = getConfigInt(BEAR_TRAP_PREPARATION_TIME_INT, DEFAULT_PREPARATION_TIME_MINUTES);
        this.trapNumber = getConfigInt(BEAR_TRAP_NUMBER_INT, DEFAULT_TRAP_NUMBER);
        this.callOwnRally = getConfigBoolean(BEAR_TRAP_CALL_RALLY_BOOL, DEFAULT_CALL_OWN_RALLY);
        this.joinRally = getConfigBoolean(BEAR_TRAP_JOIN_RALLY_BOOL, DEFAULT_JOIN_RALLY);
        this.usePets = getConfigBoolean(BEAR_TRAP_ACTIVE_PETS_BOOL, DEFAULT_USE_PETS);
        this.recallTroops = getConfigBoolean(BEAR_TRAP_RECALL_TROOPS_BOOL, DEFAULT_RECALL_TROOPS);
        this.ownRallyFlag = getConfigInt(BEAR_TRAP_RALLY_FLAG_INT, DEFAULT_OWN_RALLY_FLAG);
        
        // Parse join flags (comma-separated string) and sort by priority (ascending)
        this.joinFlags = parseJoinFlags();
        this.currentJoinFlagIndex = 0; // Reset rotation index

        logDebug(String.format(
                "Configuration loaded - Trap: %d, PrepTime: %dmin, OwnRally: %s (flag:%d), JoinRally: %s (flags:%s), Pets: %s, Recall: %s",
                trapNumber, trapPreparationTime, callOwnRally, ownRallyFlag, joinRally, joinFlags, usePets,
                recallTroops));
    }

    /**
     * Helper method to safely retrieve boolean configuration values.
     * 
     * @param key          the configuration key to retrieve
     * @param defaultValue the default value if configuration is not set
     * @return the configured boolean value or default if not set
     */
    private boolean getConfigBoolean(EnumConfigurationKey key, boolean defaultValue) {
        Boolean value = profile.getConfig(key, Boolean.class);
        return (value != null) ? value : defaultValue;
    }

    /**
     * Helper method to safely retrieve integer configuration values.
     * 
     * @param key          the configuration key to retrieve
     * @param defaultValue the default value if configuration is not set
     * @return the configured integer value or default if not set
     */
    private int getConfigInt(EnumConfigurationKey key, int defaultValue) {
        Integer value = profile.getConfig(key, Integer.class);
        return (value != null) ? value : defaultValue;
    }

    /**
     * Helper method to safely retrieve LocalDateTime configuration values.
     * 
     * <p>
     * If configuration is null, returns current time + 1 hour as a safe default.
     * 
     * @param key the configuration key to retrieve
     * @return the configured LocalDateTime value or safe default if not set
     */
    private LocalDateTime getConfigDateTime(EnumConfigurationKey key) {
        LocalDateTime value = profile.getConfig(key, LocalDateTime.class);
        if (value == null) {
            logWarning("Reference trap time not configured, using default: now + 1 hour");
            return LocalDateTime.now(ZoneId.of("UTC")).plusHours(1);
        }
        return value;
    }

    /**
     * Parses the join flag configuration (comma-separated string) into a sorted list.
     * 
     * <p>
     * Examples:
     * <ul>
     * <li>"1,3,4" → [1, 3, 4]</li>
     * <li>"5,2,7" → [2, 5, 7]</li>
     * <li>"" → [1] (default)</li>
     * </ul>
     * 
     * @return sorted list of join flag numbers
     */
    private List<Integer> parseJoinFlags() {
        String flagConfig = profile.getConfig(BEAR_TRAP_JOIN_FLAG_INT, String.class);
        List<Integer> flags = new ArrayList<>();
        
        if (flagConfig != null && !flagConfig.trim().isEmpty()) {
            String[] parts = flagConfig.split(",");
            for (String part : parts) {
                try {
                    int flag = Integer.parseInt(part.trim());
                    if (flag >= 1 && flag <= 8) {
                        flags.add(flag);
                    }
                } catch (NumberFormatException e) {
                    logWarning("Invalid join flag value: " + part);
                }
            }
        }
        
        // If no valid flags found, use default
        if (flags.isEmpty()) {
            flags.add(DEFAULT_JOIN_RALLY_FLAG);
        }
        
        // Sort flags by priority (ascending order)
        flags.sort(Integer::compareTo);
        
        return flags;
    }

    /**
     * Gets the next join flag in rotation sequence.
     * 
     * <p>
     * Rotates through flags in priority order: flag1 → flag2 → flag3 → flag1...
     * 
     * @return the next flag number to use
     */
    private int getNextJoinFlag() {
        if (joinFlags.isEmpty()) {
            return DEFAULT_JOIN_RALLY_FLAG;
        }
        
        int flag = joinFlags.get(currentJoinFlagIndex);
        currentJoinFlagIndex = (currentJoinFlagIndex + 1) % joinFlags.size();
        
        return flag;
    }

    /**
     * Main execution method for the Bear Trap task.
     * 
     * <p>
     * Flow:
     * <ol>
     * <li>Initialize OCR helpers</li>
     * <li>Load current configuration</li>
     * <li>Verify execution within valid time window</li>
     * <li>Calculate trap activation and end times</li>
     * <li>Execute preparation phase (if before activation)</li>
     * <li>Wait for trap auto-activation</li>
     * <li>Execute trap strategy (while trap is active)</li>
     * <li>Cleanup and reschedule for next window</li>
     * </ol>
     * 
     * <p>
     * <b>Error Handling:</b>
     * All exceptions are caught and logged. The task always reschedules
     * for the next window in the finally block, regardless of success or failure.
     * 
     * <p>
     * <b>BLOCKING BEHAVIOR:</b>
     * This task intentionally blocks during trap execution. It does NOT release
     * the task queue to other tasks while the trap is active. This ensures
     * continuous monitoring and participation in the bear trap event.
     */
    @Override
    protected void execute() {
        logInfo("Starting Bear Trap task execution");
        loadConfiguration();

        // Early exit if outside window - no execution, no cleanup needed
        if (!verifyExecutionWindow()) {
            rescheduleToNextWindow();
            return;
        }

        // Now we're inside the window and will actually execute
        try {
            initializeOCRHelpers();

            TrapTiming timing = calculateTrapTiming();
            logTrapTiming(timing);

            LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

            if (now.isBefore(timing.activationTime)) {
                executePreparationPhase(timing.activationTime);
            } else {
                logInfo("Trap is already ACTIVE (preparation time passed)");
                // Still execute pet activation and bear trap navigation even if already active
                logInfo("Executing essential setup (pets and navigation)...");
                if (usePets) {
                    logInfo("Activating pets...");
                    enablePets();
                }
                logInfo("Moving camera to Bear Trap " + trapNumber);
                navigateToBearTrap(trapNumber);
                sleepTask(1000); // Wait for camera to settle
            }

            now = LocalDateTime.now(ZoneId.of("UTC"));

            if (now.isBefore(timing.endTime)) {
                executeTrapActivePhase(timing.endTime);
            } else {
                logInfo("Trap already ended for this window");
            }

            logInfo("Bear Trap cycle completed successfully");

        } catch (Exception e) {
            logError("Error during Bear Trap execution: " + e.getMessage(), e);
        } finally {
            // Cleanup only happens if we actually executed (got past the window check)
            cleanup();
            rescheduleToNextWindow();
        }
    }

    /**
     * Reschedules the task for the next trap window.
     * Calculates the next window start time and updates both the task schedule
     * and the persisted configuration.
     */
    private void rescheduleToNextWindow() {
        BearTrapHelper.WindowResult result = getWindowState();

        LocalDateTime nextWindowStart = LocalDateTime.ofInstant(
                result.getNextWindowStart(),
                ZoneId.systemDefault());

        LocalDateTime nextWindowStartUtc = LocalDateTime.ofInstant(
                result.getNextWindowStart(),
                ZoneId.of("UTC"));

        logInfo("Rescheduling Bear Trap for (UTC): " + nextWindowStartUtc.format(DATETIME_FORMATTER));
        logInfo("Rescheduling Bear Trap for (Local): " + nextWindowStart.format(DATETIME_FORMATTER));

        reschedule(nextWindowStart);
        updateNextWindowDateTime();
    }

    /**
     * Verifies that current time is within the valid execution window.
     * 
     * <p>
     * If outside window, logs warning and reschedules for next window.
     * 
     * @return true if inside valid window, false otherwise
     */
    private boolean verifyExecutionWindow() {
        if (!isInsideWindow()) {
            logWarning("Execute called OUTSIDE valid window. Rescheduling...");
            return false;
        }

        logInfo("Confirmed: We are INSIDE a valid execution window");
        return true;
    }

    /**
     * Calculates trap activation and end times based on current window.
     * 
     * @return TrapTiming object containing activation and end times
     */
    private TrapTiming calculateTrapTiming() {
        BearTrapHelper.WindowResult window = getWindowState();

        LocalDateTime windowStart = LocalDateTime.ofInstant(
                window.getCurrentWindowStart(),
                ZoneId.of("UTC"));
        LocalDateTime windowEnd = LocalDateTime.ofInstant(
                window.getCurrentWindowEnd(),
                ZoneId.of("UTC"));

        LocalDateTime activationTime = windowEnd.minusMinutes(TRAP_ACTIVATION_OFFSET_MINUTES);
        LocalDateTime endTime = activationTime.plusMinutes(TRAP_DURATION_MINUTES);

        return new TrapTiming(windowStart, activationTime, endTime);
    }

    /**
     * Logs detailed trap timing information.
     * 
     * @param timing the trap timing details to log
     */
    private void logTrapTiming(TrapTiming timing) {
        logInfo("Preparation window: " + timing.windowStart.format(DATETIME_FORMATTER) + " to " +
                timing.activationTime.format(DATETIME_FORMATTER));
        logInfo("Trap will auto-activate at: " + timing.activationTime.format(DATETIME_FORMATTER));
        logInfo("Trap will end at: " + timing.endTime.format(DATETIME_FORMATTER));
    }

    /**
     * Executes the preparation phase before trap activation.
     * 
     * <p>
     * Steps:
     * <ol>
     * <li>Disable alliance autojoin</li>
     * <li>Recall all gathering troops (if configured)</li>
     * <li>Activate pets (if configured)</li>
     * <li>Navigate camera to bear trap location</li>
     * <li>Wait for trap auto-activation</li>
     * </ol>
     * 
     * <p>
     * <b>BLOCKING:</b> This method blocks the task thread until trap activates.
     * This is intentional to ensure the task doesn't release the queue to other
     * tasks.
     * 
     * @param activationTime when the trap will auto-activate
     */
    private void executePreparationPhase(LocalDateTime activationTime) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        long secondsUntilActivation = ChronoUnit.SECONDS.between(now, activationTime);

        logInfo("PREPARATION PHASE: " + secondsUntilActivation + " seconds until trap auto-activates");

        prepareForTrap();

        now = LocalDateTime.now(ZoneId.of("UTC"));
        secondsUntilActivation = ChronoUnit.SECONDS.between(now, activationTime);

        if (secondsUntilActivation > 0) {
            logInfo("Waiting for trap auto-activation in " + secondsUntilActivation + " seconds...");
            sleepTask((secondsUntilActivation * 1000) + 2000); // Wait for activation + 2s buffer
        }

        logInfo("Trap has been ACTIVATED automatically!");
    }

    /**
     * Prepares for the trap event by executing all preparation steps.
     * 
     * <p>
     * Preparation steps:
     * <ul>
     * <li>Disable alliance autojoin</li>
     * <li>Recall gathering troops (if configured)</li>
     * <li>Activate pets (if configured)</li>
     * <li>Navigate camera to bear trap location</li>
     * </ul>
     */
    private void prepareForTrap() {
        logInfo("Preparing for Bear Trap event...");

        logInfo("Disabling autojoin...");
        disableAutojoin();

        if (recallTroops) {
            logInfo("Recalling all gather troops to the city...");
            recallGatherTroops();
        }

        if (usePets) {
            logInfo("Activating pets...");
            enablePets();
        }

        logInfo("Moving camera to Bear Trap " + trapNumber);
        navigateToBearTrap(trapNumber);
        sleepTask(1000); // Wait for camera to settle
    }

    /**
     * Executes the active trap phase strategy.
     * 
     * <p>
     * While trap is active:
     * <ul>
     * <li>Monitors trap duration continuously</li>
     * <li>Attempts to start own rally (once, if configured)</li>
     * <li>Joins other rallies (continuously, if configured and marches
     * available)</li>
     * <li>Logs periodic status updates</li>
     * </ul>
     * 
     * <p>
     * <b>BLOCKING:</b> This method blocks until trap ends. The task does not
     * release the queue during this time to ensure continuous trap participation.
     * 
     * @param trapEndTime when the trap will end
     */
    private void executeTrapActivePhase(LocalDateTime trapEndTime) {
        logInfo("=== TRAP IS NOW ACTIVE - Starting strategy execution ===");

        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        long iterationCount = 0;

        while (now.isBefore(trapEndTime)) {
            iterationCount++;
            long secondsRemaining = ChronoUnit.SECONDS.between(now, trapEndTime);

            tryStartOwnRally(secondsRemaining);
            processJoinRallies();

            logPeriodicStatus(iterationCount, secondsRemaining);

            now = LocalDateTime.now(ZoneId.of("UTC"));
            sleepTask(1000); // Check every second
        }

        logInfo("=== TRAP ENDED - Strategy execution completed ===");
    }

    /**
     * Attempts to start own rally if conditions are met.
     * 
     * <p>
     * Conditions:
     * <ul>
     * <li>Call own rally is configured</li>
     * <li>Rally not already active</li>
     * <li>At least 6 minutes remaining in trap</li>
     * </ul>
     * 
     * <p>
     * If rally is started successfully, schedules automatic flag reset
     * after rally duration completes.
     * 
     * <p>
     * If an ADB connection error occurs during rally startup (likely due to
     * emulator lag or resource constraints), logs the error and resets the
     * ownRallyActive flag to allow retry on next cycle.
     * 
     * @param secondsRemaining seconds remaining in trap duration
     */
    private void tryStartOwnRally(long secondsRemaining) {
        if (!callOwnRally || ownRallyActive.get() || secondsRemaining <= OWN_RALLY_MIN_REMAINING_SECONDS) {
            return;
        }

        try {
            long marchDurationSeconds = startOwnRally();

            if (marchDurationSeconds > 0) {
                LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
                LocalDateTime returnTime = now.plusSeconds(marchDurationSeconds * 2 + 3)
                        .plusMinutes(RALLY_RETURN_BUFFER_MINUTES);

                logInfo("Own rally started successfully, returning in: " + returnTime.format(TIME_FORMATTER));
                ownRallyActive.set(true);
                scheduleRallyFlagReset(marchDurationSeconds);
                sleepTask(500); // Brief pause after rally start
            } else {
                logWarning("Could not start rally (may already be active)");
            }
        } catch (cl.camodev.wosbot.ex.ADBConnectionException e) {
            logWarning("ADB connection error during rally startup (emulator may be lagging): " + e.getMessage());
            logDebug("Skipping this rally startup attempt, will retry on next cycle");
            ownRallyActive.set(false); // Reset flag to allow retry
            // Don't re-throw - allow task to continue and retry on next iteration
        } catch (Exception e) {
            logError("Unexpected error during rally startup: " + e.getMessage(), e);
            ownRallyActive.set(false); // Reset flag to allow retry
            // Don't re-throw for unexpected errors either - try to recover gracefully
        }
    }

    /**
     * Processes joining other players' rallies if configured and marches are
     * available.
     * 
     * <p>
     * Flow:
     * <ol>
     * <li>Check free march slots available</li>
     * <li>If slots available, enter war section</li>
     * <li>Search for joinable rallies</li>
     * <li>Join rallies with configured flag</li>
     * </ol>
     * 
     * <p>
     * If an ADB connection error occurs during rally joining (likely due to
     * emulator lag or resource constraints), logs the error and skips this
     * iteration to allow the task to continue on the next cycle.
     */
    private void processJoinRallies() {
        if (!joinRally) {
            return;
        }

        try {
            int freeMarches = checkFreeMarches();

            if (freeMarches > 0) {
                DTOImageSearchResult warButton = templateSearchHelper.searchTemplate(
                        GAME_HOME_WAR,
                        SearchConfig.builder()
                                .withThreshold(90)
                                .withMaxAttempts(TEMPLATE_SEARCH_RETRIES)
                                .build());

                if (warButton.isFound()) {
                    logInfo("Entering war section to check for rallies");
                    tapPoint(warButton.getPoint());
                    handleJoinRallies(freeMarches);
                }
            }
        } catch (cl.camodev.wosbot.ex.ADBConnectionException e) {
            logWarning("ADB connection error during rally joining (emulator may be lagging): " + e.getMessage());
            logDebug("Skipping this rally join iteration, will retry on next cycle");
            // Don't re-throw - allow task to continue and retry on next iteration
        } catch (Exception e) {
            logError("Unexpected error during rally joining: " + e.getMessage(), e);
            // Don't re-throw for unexpected errors either - try to recover gracefully
        }
    }

    /**
     * Checks the number of free march slots available.
     * 
     * <p>
     * Uses OCR to read the march counter (e.g., "3/6").
     * Falls back to default value if OCR fails, as the march counter section
     * only appears after sending at least one march.
     * 
     * @return number of free march slots, or default fallback value
     */
    private int checkFreeMarches() {
        emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);

        Integer used = integerHelper.execute(
                FREE_MARCHES_OCR_TL,
                FREE_MARCHES_OCR_BR,
                5,
                10L,
                FREE_MARCHES_OCR_SETTINGS,
                NumberValidators::isFractionFormat,
                NumberConverters::fractionToFirstInt);

        Integer total = integerHelper.execute(
                FREE_MARCHES_OCR_TL,
                FREE_MARCHES_OCR_BR,
                5,
                10L,
                FREE_MARCHES_OCR_SETTINGS,
                NumberValidators::isFractionFormat,
                NumberConverters::fractionToSecondInt);

        int freeMarches;

        if (used != null && total != null) {
            freeMarches = total - used;
            logInfo("Free marches: " + freeMarches);
        } else {
            // Default to 1 as the march counter section only appears after sending at least
            // one march
            freeMarches = DEFAULT_FREE_MARCHES_FALLBACK;
            logInfo("Could not read marches (counter may not be visible yet), using default value: " + freeMarches);
        }

        return freeMarches;
    }

    /**
     * Handles joining rallies in the war section.
     * 
     * <p>
     * Flow:
     * <ol>
     * <li>Search for joinable rally indicator (plus icon)</li>
     * <li>Tap plus icon to open rally selection</li>
     * <li>Select configured flag</li>
     * <li>Tap deploy button</li>
     * <li>Return to home screen</li>
     * </ol>
     * 
     * @param freeMarches number of free march slots available (unused but logged)
     */
    private void handleJoinRallies(int freeMarches) {
        DTOImageSearchResult plusIcon = templateSearchHelper.searchTemplate(
                BEAR_JOIN_PLUS_ICON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(2)
                        .build());

        if (!plusIcon.isFound()) {
            logWarning("No joinable rallies found (plus icon not present)");
            navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.ANY);
            return;
        }

        int selectedFlag = getNextJoinFlag();
        logInfo("Joining rally with flag #" + selectedFlag + " (rotation: " + joinFlags + ")");

        tapRandomPoint(plusIcon.getPoint(), plusIcon.getPoint(), 1, 100);
        sleepTask(300); // Wait for flag selection screen

        DTOPoint flagPoint = UtilRally.getMarchFlagPoint(selectedFlag);
        tapRandomPoint(flagPoint, flagPoint, 1, 0);
        sleepTask(300); // Wait for deploy button

        DTOImageSearchResult deploy = templateSearchHelper.searchTemplate(
                BEAR_DEPLOY_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES)
                        .build());

        if (!deploy.isFound()) {
            logWarning("Deploy button not found after selecting flag.");
        } else {
            tapPoint(deploy.getPoint());
            sleepTask(500); // Wait for deployment
        }

        navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.ANY);
    }

    /**
     * Logs periodic status updates during trap execution.
     * 
     * <p>
     * Logs every 10 iterations to avoid log spam while still providing
     * visibility into trap progress.
     * 
     * @param iterationCount   current iteration number
     * @param secondsRemaining seconds remaining in trap duration
     */
    private void logPeriodicStatus(long iterationCount, long secondsRemaining) {
        if (iterationCount % STATUS_LOG_INTERVAL == 0) {
            long minutesRemaining = secondsRemaining / 60;
            logInfo("Trap active - " + minutesRemaining + " minutes " +
                    (secondsRemaining % 60) + " seconds remaining");
        }
    }

    /**
     * Starts own rally on the bear trap.
     * 
     * <p>
     * Flow:
     * <ol>
     * <li>Tap bear center to select it</li>
     * <li>Search for and tap rally button</li>
     * <li>Search for and tap hold rally button</li>
     * <li>Select configured rally flag</li>
     * <li>Read march time via OCR</li>
     * <li>Calculate rally duration</li>
     * <li>Tap deploy button</li>
     * </ol>
     * 
     * <p>
     * Rally duration is calculated as:
     * {@code 5 minutes + (march time * 2) - 3 seconds buffer}
     * 
     * <p>
     * This accounts for march time to reach the bear and return,
     * plus the base 5-minute rally timer.
     * 
     * @return march duration in seconds if successful, 0 if failed
     */
    private long startOwnRally() {
        if (!ownRallyActive.compareAndSet(false, true)) {
            return 0; // Already active
        }

        logInfo("Calling own rally...");

        tapRandomPoint(BEAR_CENTER_POINT, BEAR_CENTER_POINT, 1, 200);
        sleepTask(500); // Wait for bear selection

        DTOImageSearchResult rallyButton = templateSearchHelper.searchTemplate(
                BEAR_RALLY_BUTTON,
                SearchConfig.builder()
                        .withThreshold(80)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES_MAX)
                        .build());

        if (!rallyButton.isFound()) {
            logError("Rally button not found!");
            ownRallyActive.set(false);
            return 0;
        }

        logInfo("Opening rally menu...");
        tapRandomPoint(rallyButton.getPoint(), rallyButton.getPoint(), 1, 200);
        sleepTask(500); // Wait for rally menu

        DTOImageSearchResult holdRallyButton = templateSearchHelper.searchTemplate(
                RALLY_HOLD_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES_MAX)
                        .build());

        if (!holdRallyButton.isFound()) {
            logError("Hold Rally button not found!");
            ownRallyActive.set(false);
            return 0;
        }

        tapRandomPoint(holdRallyButton.getPoint(), holdRallyButton.getPoint(), 1, 200);
        sleepTask(300); // Wait for flag selection

        DTOPoint flagPoint = UtilRally.getMarchFlagPoint(ownRallyFlag);
        tapRandomPoint(flagPoint, flagPoint, 1, 200);
        sleepTask(300); // Wait for march time to appear

        long marchSeconds = readMarchTime();

        if (marchSeconds == 0) {
            logError("Could not read march time from screen");
            ownRallyActive.set(false);
            return 0;
        }

        DTOImageSearchResult deploy = templateSearchHelper.searchTemplate(
                BEAR_DEPLOY_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES)
                        .build());

        if (!deploy.isFound()) {
            logWarning("Deploy button not found after selecting flag.");
            ownRallyActive.set(false);
            return 0;
        }

        tapPoint(deploy.getPoint());
        sleepTask(500); // Wait for deployment

        logInfo("Rally deployed successfully. March time: " + marchSeconds + " seconds");
        return marchSeconds;
    }

    /**
     * Reads the march time from screen using OCR.
     * 
     * <p>
     * Converts the time string (e.g., "00:03:45") to total seconds.
     * 
     * @return march time in seconds, or 0 if OCR fails
     */
    private long readMarchTime() {
        Duration marchingTime = durationHelper.execute(
                MARCH_TIME_OCR_TL,
                MARCH_TIME_OCR_BR,
                MARCH_TIME_OCR_MAX_RETRIES,
                200L,
                null,
                TimeValidators::isValidTime,
                TimeConverters::toDuration);

        if (marchingTime != null) {
            return marchingTime.getSeconds();
        }

        return 0;
    }

    /**
     * Schedules automatic reset of the rally active flag after rally duration.
     * 
     * <p>
     * The rally duration is calculated as:
     * {@code 5 minutes + (march time * 2) - 3 seconds buffer}
     * 
     * <p>
     * This allows the task to start another rally after the first one completes.
     * The scheduled task runs on a separate thread and will execute even if
     * the main task has moved on to other activities.
     * 
     * @param marchSeconds march time in seconds
     */
    private void scheduleRallyFlagReset(long marchSeconds) {
        long durationSeconds = RALLY_DURATION_BASE_MINUTES * 60 +
                marchSeconds * 2 -
                RALLY_DURATION_BUFFER_SECONDS;

        if (rallyScheduler == null || rallyScheduler.isShutdown() || rallyScheduler.isTerminated()) {
            rallyScheduler = Executors.newSingleThreadScheduledExecutor();
        }
        rallyResetTask = rallyScheduler.schedule(
                () -> {
                    ownRallyActive.set(false);
                    logInfo("Rally active flag automatically reset after duration");
                },
                durationSeconds,
                TimeUnit.SECONDS);

        logDebug("Scheduled rally flag reset in " + durationSeconds + " seconds");
    }

    /**
     * Navigates camera to the specified bear trap location.
     * 
     * <p>
     * Navigation flow:
     * <ol>
     * <li>Open alliance menu</li>
     * <li>Open territory screen</li>
     * <li>Open special buildings list</li>
     * <li>Tap "Go" button for specified trap (1 or 2)</li>
     * </ol>
     * 
     * @param trapNumber which bear trap to navigate to (1 or 2)
     * @return true if navigation successful, false otherwise
     */
    private boolean navigateToBearTrap(int trapNumber) {
        tapRandomPoint(ALLIANCE_BUTTON_TL, ALLIANCE_BUTTON_BR);
        sleepTask(3000); // Wait for alliance menu

        DTOImageSearchResult territoryButton = templateSearchHelper.searchTemplate(
                ALLIANCE_TERRITORY_BUTTON,
                SearchConfig.builder()
                        .withMaxAttempts(1)
                        .build());

        if (!territoryButton.isFound()) {
            logError("Territory button not found to go to bear trap");
            return false;
        }

        tapRandomPoint(territoryButton.getPoint(), territoryButton.getPoint(), 1, 2000);
        sleepTask(1000); // Wait for territory screen

        tapRandomPoint(SPECIAL_BUILDINGS_BUTTON_TL, SPECIAL_BUILDINGS_BUTTON_BR, 1, 300);
        sleepTask(500); // Wait for special buildings list

        boolean success = tapBearTrapGoButton(trapNumber);

        if (success) {
            sleepTask(2000); // Wait for camera to move to bear
        }

        return success;
    }

    /**
     * Taps the appropriate "Go" button for the specified bear trap.
     * 
     * @param trapNumber which bear trap (1 or 2)
     * @return true if valid trap number, false otherwise
     */
    private boolean tapBearTrapGoButton(int trapNumber) {
        switch (trapNumber) {
            case 1:
                tapRandomPoint(BEAR_TRAP_1_GO_BUTTON_TL, BEAR_TRAP_1_GO_BUTTON_BR, 1, 300);
                return true;
            case 2:
                tapRandomPoint(BEAR_TRAP_2_GO_BUTTON_TL, BEAR_TRAP_2_GO_BUTTON_BR, 1, 300);
                return true;
            default:
                logError("Invalid trap number: " + trapNumber);
                return false;
        }
    }

    /**
     * Activates pets for the bear trap event.
     * 
     * <p>
     * Flow:
     * <ol>
     * <li>Open pets menu</li>
     * <li>Select Razorback pet</li>
     * <li>Use quick-use button</li>
     * <li>Confirm use</li>
     * <li>Return to home</li>
     * </ol>
     */
    private void enablePets() {
        DTOImageSearchResult petsButton = templateSearchHelper.searchTemplate(
                GAME_HOME_PETS,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES_EXTENDED)
                        .build());

        if (!petsButton.isFound()) {
            logError("Pets button not found to enable pets");
            return;
        }

        tapRandomPoint(petsButton.getPoint(), petsButton.getPoint(), 1, 500);
        sleepTask(1000); // Wait for pets menu

        tapRandomPoint(PET_RAZORBACK_TL, PET_RAZORBACK_BR, 1, 500);
        sleepTask(300); // Wait for pet selection

        tapRandomPoint(PET_QUICK_USE_BUTTON_TL, PET_QUICK_USE_BUTTON_BR, 1, 500);
        sleepTask(300); // Wait for confirmation dialog

        tapRandomPoint(PET_USE_BUTTON_TL, PET_USE_BUTTON_BR, 1, 100);
        sleepTask(500); // Wait for pet activation

        tapBackButton();
        sleepTask(300); // Wait for menu close

        navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.ANY);
    }

    /**
     * Recalls all gathering troops back to the city.
     * 
     * <p>
     * Continuously checks for march indicators and recalls troops until
     * no more march indicators are found. Uses a safety limit to prevent
     * infinite loops.
     * 
     * <p>
     * March indicators checked:
     * <ul>
     * <li>Recall button (returning arrow) - troops gathering, can recall</li>
     * <li>View button - troops visible on map</li>
     * <li>Speedup button - troops marching</li>
     * </ul>
     * 
     * <p>
     * If no indicators are found, all troops are assumed to be in the city.
     */
    private void recallGatherTroops() {
        int attempt = 0;

        while (attempt < MAX_GATHER_RECALL_ATTEMPTS) {
            attempt++;

            MarchStatus status = checkMarchStatus();

            logDebug(String.format(
                    "recallGatherTroops status => returning:%b view:%b speedup:%b (attempt %d)",
                    status.hasRecallButton, status.hasViewButton, status.hasSpeedupButton, attempt));

            if (status.noMarchesFound()) {
                logInfo("No march indicators found. All gather troops are recalled or none present.");
                return;
            }

            if (status.hasRecallButton) {
                recallMarch();
            }

            if (status.hasViewButton || status.hasSpeedupButton) {
                logInfo("Troops are still marching - waiting for them to return");
                sleepTask(1000); // Wait for troops to return
            }

            sleepTask(200); // Brief pause between checks
        }

        logError("recallGatherTroops exceeded max attempts (" + MAX_GATHER_RECALL_ATTEMPTS +
                "), exiting to avoid deadlock");
    }

    /**
     * Checks the current march status by searching for march indicators.
     * 
     * @return MarchStatus object containing status of all indicators
     */
    private MarchStatus checkMarchStatus() {
        DTOImageSearchResult returningArrow = templateSearchHelper.searchTemplate(
                MARCHES_AREA_RECALL_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES)
                        .build());

        DTOImageSearchResult marchView = templateSearchHelper.searchTemplate(
                MARCHES_AREA_VIEW_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES)
                        .build());

        DTOImageSearchResult marchSpeedup = templateSearchHelper.searchTemplate(
                MARCHES_AREA_SPEEDUP_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES)
                        .build());

        return new MarchStatus(
                returningArrow != null && returningArrow.isFound(),
                marchView != null && marchView.isFound(),
                marchSpeedup != null && marchSpeedup.isFound());
    }

    /**
     * Recalls a single march by tapping the recall button and confirming.
     */
    private void recallMarch() {
        logInfo("Returning arrow found - attempting to tap recall button");

        DTOImageSearchResult recallButton = templateSearchHelper.searchTemplate(
                MARCHES_AREA_RECALL_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES)
                        .build());

        if (recallButton.isFound()) {
            tapRandomPoint(recallButton.getPoint(), recallButton.getPoint(), 1, 300);
            sleepTask(300); // Wait for confirmation dialog

            tapRandomPoint(RECALL_CONFIRM_BUTTON_TL, RECALL_CONFIRM_BUTTON_BR, 1, 200);
            sleepTask(500); // Wait for recall to process
        }
    }

    /**
     * Disables alliance autojoin for the trap event.
     * 
     * <p>
     * Navigation flow:
     * <ol>
     * <li>Open alliance menu</li>
     * <li>Open alliance war screen</li>
     * <li>Open autojoin settings</li>
     * <li>Tap stop button</li>
     * <li>Return to home</li>
     * </ol>
     */
    private void disableAutojoin() {
        tapRandomPoint(ALLIANCE_BUTTON_TL, ALLIANCE_BUTTON_BR);
        sleepTask(3000); // Wait for alliance menu

        DTOImageSearchResult warButton = templateSearchHelper.searchTemplate(
                ALLIANCE_WAR_BUTTON,
                SearchConfig.builder()
                        .withThreshold(90)
                        .withMaxAttempts(TEMPLATE_SEARCH_RETRIES_EXTENDED)
                        .build());

        if (!warButton.isFound()) {
            logError("Alliance War button not found to disable autojoin");
            return;
        }

        tapRandomPoint(warButton.getPoint(), warButton.getPoint(), 1, 1000);
        sleepTask(1000); // Wait for war screen

        tapRandomPoint(AUTOJOIN_BUTTON_TL, AUTOJOIN_BUTTON_BR, 1, 1500);
        sleepTask(500); // Wait for autojoin menu

        tapRandomPoint(AUTOJOIN_STOP_BUTTON_TL, AUTOJOIN_STOP_BUTTON_BR, 1, 500);
        sleepTask(500); // Wait for stop to process

        navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.ANY);
    }

    /**
     * Cleans up bear trap state and re-queues disabled tasks.
     * 
     * <p>
     * Cleanup steps:
     * <ul>
     * <li>Reset rally active flag</li>
     * <li>Cancel scheduled rally reset task</li>
     * <li>Shutdown rally scheduler executor</li>
     * <li>Re-queue gather task if enabled</li>
     * <li>Re-queue autojoin task if enabled</li>
     * </ul>
     */
    private void cleanup() {
        logInfo("Cleaning up Bear Trap state");

        ownRallyActive.set(false);

        if (rallyResetTask != null && !rallyResetTask.isDone()) {
            rallyResetTask.cancel(false);
            logDebug("Cancelled pending rally reset task");
        }

        if (rallyScheduler != null && !rallyScheduler.isShutdown()) {
            rallyScheduler.shutdown();
            try {
                if (!rallyScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    rallyScheduler.shutdownNow();
                }
                logDebug("Rally scheduler shutdown successfully");
            } catch (InterruptedException e) {
                rallyScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        requeueDisabledTasks();
    }

    /**
     * Re-queues tasks that were disabled during trap preparation.
     * 
     * <p>
     * Checks configuration and re-queues:
     * <ul>
     * <li>Gather Resources task (if enabled)</li>
     * <li>Alliance Autojoin task (if enabled)</li>
     * </ul>
     */
    private void requeueDisabledTasks() {
        logInfo("Re-queueing tasks after Bear Trap event...");

        TaskQueue queue = servScheduler.getQueueManager().getQueue(profile.getId());

        if (queue == null) {
            logError("Could not access task queue for profile " + profile.getName());
            return;
        }

        requeueGatherTask(queue);
        requeueAutojoinTask(queue);

        sleepTask(1000); // Brief pause after re-queuing
    }

    /**
     * Re-queues the Gather Resources task if enabled in configuration.
     * 
     * @param queue the task queue for this profile
     */
    private void requeueGatherTask(TaskQueue queue) {
        logInfo("Checking Gather Resources task...");

        Boolean gatherEnabled = profile.getConfig(
                EnumConfigurationKey.GATHER_TASK_BOOL,
                Boolean.class);

        if (Boolean.TRUE.equals(gatherEnabled)) {
            queue.executeTaskNow(TpDailyTaskEnum.GATHER_RESOURCES, true);
            logInfo("Re-queued Gather Resources task");
        }
    }

    /**
     * Re-queues the Alliance Autojoin task if enabled in configuration.
     * 
     * @param queue the task queue for this profile
     */
    private void requeueAutojoinTask(TaskQueue queue) {
        logInfo("Checking autojoin task...");

        Boolean autojoinEnabled = profile.getConfig(
                EnumConfigurationKey.ALLIANCE_AUTOJOIN_BOOL,
                Boolean.class);

        if (Boolean.TRUE.equals(autojoinEnabled)) {
            queue.executeTaskNow(TpDailyTaskEnum.ALLIANCE_AUTOJOIN, true);
            logInfo("Re-queued Alliance Autojoin task");
        }
    }

    /**
     * Verifies if we're currently inside an execution window.
     *
     * @return true if inside valid window, false otherwise
     */
    private boolean isInsideWindow() {
        Instant referenceUTC = referenceTrapTime.atZone(ZoneId.of("UTC")).toInstant();
        BearTrapHelper.WindowResult result = BearTrapHelper.calculateWindow(referenceUTC, trapPreparationTime);
        return result.getState() == BearTrapHelper.WindowState.INSIDE;
    }

    /**
     * Gets detailed information about the current window.
     *
     * @return Window state with start, end, and next window times
     */
    private BearTrapHelper.WindowResult getWindowState() {
        Instant referenceUTC = referenceTrapTime.atZone(ZoneId.of("UTC")).toInstant();
        return BearTrapHelper.calculateWindow(referenceUTC, trapPreparationTime);
    }

    /**
     * Updates the BEAR_TRAP_SCHEDULE_DATETIME_STRING configuration with the next
     * trap activation time (anchor).
     * 
     * <p>
     * This method is called after the trap ends to persist the next execution time
     * in the database. The saved time is the trap activation time (anchor), not the
     * preparation window start time.
     */
    private void updateNextWindowDateTime() {
        BearTrapHelper.WindowResult result = getWindowState();

        LocalDateTime nextWindowStart = LocalDateTime.ofInstant(
                result.getNextWindowStart(),
                ZoneId.of("UTC"));

        LocalDateTime nextTrapActivation = nextWindowStart.plusMinutes(trapPreparationTime);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        String formattedDateTime = nextTrapActivation.format(formatter);

        logInfo("Updating next trap activation time to: " + formattedDateTime + " UTC");

        ServConfig.getServices().updateProfileConfig(
                profile,
                BEAR_TRAP_SCHEDULE_DATETIME_STRING,
                formattedDateTime);
    }

    /**
     * Specifies that this task should start from the world screen.
     * The task needs to navigate to bear trap locations which requires world view.
     * 
     * @return EnumStartLocation.WORLD
     */
    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.WORLD;
    }

    /**
     * Indicates that this task does not consume stamina.
     * 
     * @return false
     */
    @Override
    public boolean consumesStamina() {
        return false;
    }

    /**
     * Indicates that this task does not provide daily mission progress.
     * 
     * @return false
     */
    @Override
    public boolean provideDailyMissionProgress() {
        return false;
    }

    /**
     * Helper class to encapsulate trap timing information.
     */
    private static class TrapTiming {
        final LocalDateTime windowStart;
        final LocalDateTime activationTime;
        final LocalDateTime endTime;

        TrapTiming(LocalDateTime windowStart, LocalDateTime activationTime, LocalDateTime endTime) {
            this.windowStart = windowStart;
            this.activationTime = activationTime;
            this.endTime = endTime;
        }
    }

    /**
     * Helper class to encapsulate march status information.
     */
    private static class MarchStatus {
        final boolean hasRecallButton;
        final boolean hasViewButton;
        final boolean hasSpeedupButton;

        MarchStatus(boolean hasRecallButton, boolean hasViewButton, boolean hasSpeedupButton) {
            this.hasRecallButton = hasRecallButton;
            this.hasViewButton = hasViewButton;
            this.hasSpeedupButton = hasSpeedupButton;
        }

        boolean noMarchesFound() {
            return !hasRecallButton && !hasViewButton && !hasSpeedupButton;
        }
    }
}