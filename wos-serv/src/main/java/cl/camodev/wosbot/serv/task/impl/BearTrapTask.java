package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.BearTrapHelper;
import cl.camodev.utiles.UtilRally;
import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.ocr.BotTextRecognitionProvider;
import cl.camodev.wosbot.serv.task.*;

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static cl.camodev.wosbot.console.enumerable.EnumConfigurationKey.*;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.ALLIANCE_TERRITORY_BUTTON;

/**
 * Bear Trap Task
 *
 * This task manages the Bear Trap feature with configurable schedule and
 * preparation actions.
 * The task executes based on a reference date/time (in UTC) with preparation
 * windows every 2 days.
 *
 * Window logic:
 * <p>
 * - Reference date marks the END of preparation window (trap activation time)
 * <p>
 * - Preparation time is subtracted from reference to calculate window START
 * <p>
 * - Windows repeat every 2 days from the reference date
 * <p>
 * - Example: Reference 21:00, 5min prep -> Window: 20:55 to 21:00, trap active
 * until 21:30
 */
public class BearTrapTask extends DelayedTask {

    private final AtomicBoolean ownRallyActive = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    BotTextRecognitionProvider provider = new BotTextRecognitionProvider(emuManager, EMULATOR_NUMBER);
    TextRecognitionRetrier<Duration> durationHelper = new TextRecognitionRetrier<>(provider);

    // Trap configuration
    private boolean callOwnRally;
    private boolean joinRally;
    private boolean usePets;
    private boolean recallTroops;
    private int trapNumber;
    private int ownRallyFlag;
    private int joinRallyFlag;
    private int trapPreparationTime;
    private int trapDurationTime;
    private LocalDateTime referenceTrapTime;

    private DTOTesseractSettings settings = DTOTesseractSettings.builder()
            .setAllowedChars("0123456789/")
            .setRemoveBackground(true)
            .setTextColor(new Color(253, 253, 253)) // White text
            // .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
            .setReuseLastImage(true)
            //.setDebug(true)
            .build();

    public BearTrapTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
        reloadConfig();

        Instant referenceUTC = referenceTrapTime.atZone(ZoneId.of("UTC")).toInstant();

        // Calculate current window state using variable minutes
        BearTrapHelper.WindowResult result = BearTrapHelper.calculateWindow(referenceUTC, trapPreparationTime);
        // Determine next execution time based on state
        Instant nextExecutionInstant;

        switch (result.getState()) {
            case BEFORE:
                // If we're before the window, execute at current window start
                nextExecutionInstant = result.getCurrentWindowStart();
                logInfo("Current time is BEFORE window. Next execution (UTC): "
                        + LocalDateTime.ofInstant(nextExecutionInstant, ZoneId.of("UTC")).format(DATETIME_FORMATTER));
                break;

            case INSIDE:
                // If we're inside the window, execute NOW
                nextExecutionInstant = Instant.now();
                logInfo("Current time is INSIDE window. Executing NOW");
                break;

            case AFTER:
                // If we're after the window, use next window
                nextExecutionInstant = result.getNextWindowStart();
                logInfo("Current time is AFTER window. Next execution (UTC): " +
                        LocalDateTime.ofInstant(nextExecutionInstant, ZoneId.of("UTC")).format(DATETIME_FORMATTER));
                break;

            default:
                throw new IllegalStateException("Unrecognized window state");
        }

        // Convert from UTC Instant to local system time for scheduling
        LocalDateTime nextExecutionTimeLocal = LocalDateTime.ofInstant(
                nextExecutionInstant,
                ZoneId.systemDefault());

        logInfo("Next execution in local time: " + nextExecutionTimeLocal.format(DATETIME_FORMATTER));
        // Apply to scheduler
        this.scheduledTime = nextExecutionTimeLocal;
    }

    @Override
    protected void execute() {
        reloadConfig();
        logInfo("Starting Bear Trap task execution");

        try {
            // Verify we're inside a valid window
            if (!isInsideWindow()) {
                logWarning("Execute called OUTSIDE valid window. Rescheduling...");
                updateNextWindowDateTime();
                return;
            }

            logInfo("Confirmed: We are INSIDE a valid execution window");

            // Get current window information
            BearTrapHelper.WindowResult window = getWindowState();

            // Convert window times to LocalDateTime
            LocalDateTime windowStart = LocalDateTime.ofInstant(
                    window.getCurrentWindowStart(),
                    ZoneId.of("UTC"));
            LocalDateTime windowEnd = LocalDateTime.ofInstant(
                    window.getCurrentWindowEnd(),
                    ZoneId.of("UTC"));

            // Trap ACTIVATES automatically at END of preparation window
            LocalDateTime trapActivationTime = windowEnd.minusMinutes(30);

            LocalDateTime trapEndTime = trapActivationTime.plusMinutes(trapDurationTime);

            logInfo("Preparation window: " + windowStart.format(DATETIME_FORMATTER) + " to "
                    + trapActivationTime.format(DATETIME_FORMATTER));
            logInfo("Trap will auto-activate at: " + trapActivationTime.format(DATETIME_FORMATTER));
            logInfo("Trap will end at: " + trapEndTime.format(DATETIME_FORMATTER));

            LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

            // Phase 1: PREPARATION (if still in preparation time)
            if (now.isBefore(trapActivationTime)) {
                long secondsUntilActivation = ChronoUnit.SECONDS.between(now, trapActivationTime);
                logInfo("PREPARATION PHASE: " + secondsUntilActivation + " seconds until trap auto-activates");

                prepareForTrap();

                now = LocalDateTime.now(ZoneId.of("UTC"));
                secondsUntilActivation = ChronoUnit.SECONDS.between(now, trapActivationTime);

                if (secondsUntilActivation > 0) {
                    logInfo("Waiting for trap auto-activation in " + secondsUntilActivation + " seconds...");
                    sleepTask((secondsUntilActivation * 1000) + 2000);
                }
                logInfo("Trap has been ACTIVATED automatically!");
            } else {
                // this will skip preration and go directly to play event
                logInfo("Trap is already ACTIVE (preparation time passed)");
            }

            // Phase 2: TRAP ACTIVE - execute strategy until it ends
            now = LocalDateTime.now(ZoneId.of("UTC"));

            if (now.isBefore(trapEndTime)) {
                logInfo("TRAP ACTIVE: Executing strategy until trap ends...");
                executeTrapStrategy(trapEndTime);
            } else {
                logInfo("Trap already ended for this window");
            }

            logInfo("Bear Trap cycle completed successfully");
            cleanup();

        } catch (Exception e) {
            logError("Error during Bear Trap execution: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Persist next trap anchor regardless of outcome
            try {

                updateNextWindowDateTime();
            } catch (Exception ex) {
                logWarning("Could not persist next anchor at end of execution: " + ex.getMessage());
            }

        }
    }

    /**
     * Verifies if we're currently inside an execution window
     *
     * @return true if inside valid window, false otherwise
     */
    private boolean isInsideWindow() {
        LocalDateTime referenceDate = profile.getConfig(
                BEAR_TRAP_SCHEDULE_DATETIME_STRING,
                LocalDateTime.class);

        Instant referenceUTC = referenceDate.atZone(ZoneId.of("UTC")).toInstant();

        BearTrapHelper.WindowResult result = BearTrapHelper.calculateWindow(referenceUTC, trapPreparationTime);
        return result.getState() == BearTrapHelper.WindowState.INSIDE;
    }

    /**
     * Gets detailed information about the current window
     *
     * @return Window state with start, end, and next window times
     */
    private BearTrapHelper.WindowResult getWindowState() {
        LocalDateTime referenceDate = profile.getConfig(
                BEAR_TRAP_SCHEDULE_DATETIME_STRING,
                LocalDateTime.class);

        Instant referenceUTC = referenceDate.atZone(ZoneId.of("UTC")).toInstant();

        return BearTrapHelper.calculateWindow(referenceUTC, trapPreparationTime);
    }


    /**
     * Executes the trap strategy while the trap is active
     * The trap is ALREADY ACTIVE when this method is called
     *
     * @param trapEndTime When the trap will end
     */
    private void executeTrapStrategy(LocalDateTime trapEndTime) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        long iterationCount = 0;

        logInfo("=== TRAP IS NOW ACTIVE - Starting strategy execution ===");

        // Execute strategy while trap is active
        while (now.isBefore(trapEndTime)) {
            iterationCount++;

            // Calculate remaining time
            long secondsRemaining = ChronoUnit.SECONDS.between(now, trapEndTime);

            // Try to start own rally if conditions are met
            if (callOwnRally && !ownRallyActive.get() && secondsRemaining > 360) {
                long durationSeconds = callOwnRally();
                if (durationSeconds > 0) {
                    LocalDateTime returnTime = now.plusSeconds(durationSeconds * 2 + 2).plusMinutes(5);
                    logInfo("Own rally started successfully, returning in: " + returnTime.format(TIME_FORMATTER));
                    ownRallyActive.set(true);// marca el rally como activo
                    sleepTask(200);
                } else {
                    logWarning("Could not start rally (may already be active)");
                }
            }

            // handle join
            if (joinRally) {

                DTOPoint point1 = new DTOPoint(203, 200);
                DTOPoint point2 = new DTOPoint(246, 226);

                // check the available spots
                emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);
                Integer used = integerHelper.execute(
                        point1, point2, 5, 10L,
                        settings,
                        NumberValidators::isFractionFormat,
                        NumberConverters::fractionToFirstInt);

                Integer total = integerHelper.execute(
                        point1, point2, 5, 10L,
                        settings,
                        NumberValidators::isFractionFormat,
                        NumberConverters::fractionToSecondInt);

                int freeMarches;

                if (used != null && total != null) {
                    freeMarches = total - used;
                    logInfo("Free marches: " + freeMarches);
                } else {
                    // Si no se puede leer, usar 1 como valor por defecto
                    freeMarches = 1;
                    logInfo("Could not read marches, using default value: " + freeMarches);
                }

                // handle join marches
                if (freeMarches > 0) {
                    DTOImageSearchResult warButton = searchTemplateWithRetries(EnumTemplates.GAME_HOME_WAR, 90, 3);

                    if (warButton.isFound()) {
                        logInfo("Entering war section to check for rallies");
                        tapPoint(warButton.getPoint());
                        handleJoinRallies(freeMarches);
                    }

                }
            }

            // Periodic status log
            if (iterationCount % 10 == 0) {
                long minutesRemaining = secondsRemaining / 60;
                logInfo("Trap active - " + minutesRemaining + " minutes " + (secondsRemaining % 60)
                        + " seconds remaining");
            }

            // Update current time after sleep
            now = LocalDateTime.now(ZoneId.of("UTC"));

            // Sleep for a short interval to avoid tight loop
            sleepTask(1000);
        }

        logInfo("=== TRAP ENDED - Strategy execution completed ===");

        // Update configuration with next window start time
        updateNextWindowDateTime();
    }

    private void handleJoinRallies(int freeMarches) {
        // search the green plus icon
        DTOImageSearchResult plusIcon = searchTemplateWithRetries(EnumTemplates.BEAR_JOIN_PLUS_ICON, 90, 2);
        if (!plusIcon.isFound()) {
            logWarning("No joinable rallies found (plus icon not present)");
            ensureCorrectScreenLocation(EnumStartLocation.ANY);
            return;
        }
        logInfo("Joining rally with flag #" + joinRallyFlag);
        // tap the plus icon
        tapRandomPoint(plusIcon.getPoint(), plusIcon.getPoint(), 1, 100);
        DTOPoint flagPoint = UtilRally.getMarchFlagPoint(joinRallyFlag);
        tapRandomPoint(flagPoint, flagPoint, 1, 0);

        DTOImageSearchResult deploy = searchTemplateWithRetries(EnumTemplates.BEAR_DEPLOY_BUTTON, 90, 3);

        if (deploy.isFound()) {
            logWarning("Deploy button not found. Rescheduling to try again in 5 minutes.");
            tapPoint(deploy.getPoint());
        }
        ensureCorrectScreenLocation(EnumStartLocation.ANY);
    }

    private void cleanup() {
        logInfo("Cleaning up Bear Trap state");
        ownRallyActive.set(false);
        requeueTasks();
    }

    /**
     * Prepare for the trap event
     * - Disable autojoin
     * - Recall all troops to the city
     * - Enable pets if configured
     * - Move the camera to bear trap area based on config (Bear 1 or Bear 2)
     */
    private void prepareForTrap() {
        logInfo("Preparing for Bear Trap event...");

        logInfo("Disabling autojoin...");
        disableAutojoin();

        // Recall troops if configured
        if (recallTroops) {
            logInfo("Recalling all gather troops to the city...");
            recallGatherTroops();
        }

        // Enable pets if configured
        if (usePets) {
            logInfo("Activating pets...");
            enablePets();
        }

        // Move camera to bear trap area
        logInfo("Moving camera to Bear Trap " + trapNumber);
        navigateToBearTrap(trapNumber);
        // Navigate to bear trap location based on trapNumber (1 or 2)
        sleepTask(1000);
    }

    private boolean navigateToBearTrap(int trapNumber) {
        // go to alliance
        tapRandomPoint(new DTOPoint(493, 1187), new DTOPoint(561, 1240));
        sleepTask(3000);
        // go to territory

        DTOImageSearchResult territoryButton = searchTemplateWithRetries(ALLIANCE_TERRITORY_BUTTON, 1);
        if (!territoryButton.isFound()) {
            logError("Territory button not found to go to bear trap");
            return false;
        }
        tapRandomPoint(territoryButton.getPoint(), territoryButton.getPoint(), 1, 2000);
        // go to special buildings
        tapRandomPoint(new DTOPoint(460, 110), new DTOPoint(560, 130), 1, 300);
        // click on go button based on trap number

        switch (trapNumber) {
            case 1 ->
                tapRandomPoint(new DTOPoint(570, 350), new DTOPoint(620, 370), 1, 300);
            case 2 ->
                tapRandomPoint(new DTOPoint(570, 530), new DTOPoint(620, 550), 1, 300);
            default -> {
                logError("Invalid trap number: " + trapNumber);
                return false;
            }
        }
        return true;
    }

    /**
     * Enables pets for the trap event
     */
    private void enablePets() {
        DTOImageSearchResult petsButton = searchTemplateWithRetries(EnumTemplates.GAME_HOME_PETS, 90, 5);
        if (!petsButton.isFound()) {
            logError("Pets button not found to enable pets");
            return;
        }
        tapRandomPoint(petsButton.getPoint(), petsButton.getPoint(), 1, 500);
        tapRandomPoint(new DTOPoint(100, 410), new DTOPoint(160, 460), 1, 500); // razorbreak
        tapRandomPoint(new DTOPoint(120, 1070), new DTOPoint(280, 1100), 1, 500); // quick use
        tapRandomPoint(new DTOPoint(460, 800), new DTOPoint(550, 830), 1, 100); // use
        tapBackButton();
        ensureCorrectScreenLocation(EnumStartLocation.ANY);
    }

    /**
     * Recalls all gathering troops back to the city
     * Checks for returning arrows, march view, and speedup buttons
     * Continues until all troops are recalled or max retries reached
     */
    private void recallGatherTroops() {
        int maxRetries = 120; // Safety limit to avoid long-running loop
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;

            DTOImageSearchResult returningArrow = searchTemplateWithRetries(EnumTemplates.MARCHES_AREA_RECALL_BUTTON,
                    90, 3);
            DTOImageSearchResult marchView = searchTemplateWithRetries(EnumTemplates.MARCHES_AREA_VIEW_BUTTON, 90, 3);
            DTOImageSearchResult marchSpeedup = searchTemplateWithRetries(EnumTemplates.MARCHES_AREA_SPEEDUP_BUTTON, 90,
                    3);

            boolean foundReturning = returningArrow != null && returningArrow.isFound();
            boolean foundView = marchView != null && marchView.isFound();
            boolean foundSpeedup = marchSpeedup != null && marchSpeedup.isFound();

            logInfo(String.format("recallGatherTroops status => returning:%b view:%b speedup:%b (attempt %d)",
                    foundReturning, foundView, foundSpeedup, attempt));

            // If nothing is present, we're done
            if (!foundReturning && !foundView && !foundSpeedup) {
                logInfo("No march indicators found. All gather troops are recalled or none present.");
                return; // Finished successfully
            }

            // If there's a returning arrow (recall button), tap it to recall troops
            if (foundReturning) {
                logInfo("Returning arrow found - attempting to tap recall button");
                tapRandomPoint(returningArrow.getPoint(), returningArrow.getPoint(), 1, 300);
                tapRandomPoint(new DTOPoint(446, 780), new DTOPoint(578, 800), 1, 200); // Confirm recall
            }

            // If 'view' or 'speedup' buttons are present, wait for UI to clear
            if (foundView || foundSpeedup) {
                logInfo("Troops are still marching - waiting for them to return");
                sleepTask(1000);
            }

            // Short sleep between attempts to avoid tight loop
            sleepTask(200);
        }

        // If we reach here, max retries were hit
        logError("recallGatherTroops exceeded max attempts (" + maxRetries + "), exiting to avoid deadlock");
    }

    /**
     * Disables alliance autojoin for the trap event
     * Navigates to alliance war screen and stops autojoin
     */
    private void disableAutojoin() {
        // Navigate to alliance screen with alliance button
        tapRandomPoint(new DTOPoint(493, 1187), new DTOPoint(561, 1240));
        sleepTask(3000);

        DTOImageSearchResult warButton = searchTemplateWithRetries(EnumTemplates.ALLIANCE_WAR_BUTTON, 90, 5);

        if (!warButton.isFound()) {
            logError("Alliance War button not found to disable autojoin");
            return;
        }
        tapRandomPoint(warButton.getPoint(), warButton.getPoint(), 1, 1000);

        // Tap autojoin button
        tapRandomPoint(new DTOPoint(260, 1200), new DTOPoint(450, 1240), 1, 1500);

        // Tap stop button
        tapRandomPoint(new DTOPoint(120, 1070), new DTOPoint(240, 1110), 1, 500);

        ensureCorrectScreenLocation(EnumStartLocation.ANY);
    }

    /**
     * Re-queues tasks that were disabled during trap preparation
     * Checks configuration and re-queues:
     * - Gather task
     * - Alliance autojoin
     */
    private void requeueTasks() {
        logInfo("Re-queueing tasks after Bear Trap event...");

        // Get the task queue for this profile
        TaskQueue queue = servScheduler.getQueueManager().getQueue(profile.getId());
        if (queue == null) {
            logError("Could not access task queue for profile " + profile.getName());
            return;
        }

        // Check and re-queue gather task if enabled
        logInfo("Checking Gather Resources task...");
        if (profile.getConfig(EnumConfigurationKey.GATHER_TASK_BOOL, Boolean.class)) {
            queue.executeTaskNow(TpDailyTaskEnum.GATHER_RESOURCES, true);
            logInfo("Re-queued Gather Resources task");
        }

        // Check and re-queue autojoin task if enabled
        logInfo("Checking autojoin task...");
        if (profile.getConfig(EnumConfigurationKey.ALLIANCE_AUTOJOIN_BOOL, Boolean.class)) {
            queue.executeTaskNow(TpDailyTaskEnum.ALLIANCE_AUTOJOIN, true);
            logInfo("Re-queued Alliance Autojoin task");
        }

        sleepTask(1000);
    }

    /**
     * Calls own rally with non-blocking duration tracking
     *
     * Rally duration calculation: 5 minutes + (march time * 2) - 1 second buffer
     * This accounts for the march time to reach the bear and return
     *
     * @return The duration in seconds if rally was started, 0 if already active
     */
    private long callOwnRally() {
        long durationSeconds;

        // Set flag and schedule reset after duration (non-blocking)
        if (ownRallyActive.compareAndSet(false, true)) {
            logInfo("Calling own rally...");

            // Tap bear center (assuming camera is already on the bear)
            tapRandomPoint(new DTOPoint(370, 507), new DTOPoint(370, 507), 1, 200);

            // Search for rally button (it can be in different places)
            DTOImageSearchResult rallyButton = searchTemplateWithRetries(EnumTemplates.BEAR_RALLY_BUTTON, 80, 10);
            logInfo("Opening rally menu...");
            if (!rallyButton.isFound()) {
                logError("Rally button not found!");
                ownRallyActive.set(false);
                return 0;
            }
            tapRandomPoint(rallyButton.getPoint(), rallyButton.getPoint(), 1, 200);

            // Search and tap hold rally button
            DTOImageSearchResult holdRallyButton = searchTemplateWithRetries(EnumTemplates.RALLY_HOLD_BUTTON, 90, 10);
            if (!holdRallyButton.isFound()) {
                logError("Hold Rally button not found!");
                ownRallyActive.set(false);
                return 0;
            }
            tapRandomPoint(holdRallyButton.getPoint(), holdRallyButton.getPoint(), 1, 200);

            // Select rally flag
            DTOPoint flagPoint = UtilRally.getMarchFlagPoint(ownRallyFlag);
            tapRandomPoint(flagPoint, flagPoint, 1, 200);

            // Read march time from screen using OCR
            DTOPoint p1 = new DTOPoint(521, 1141);
            DTOPoint p2 = new DTOPoint(608, 1162);
            int maxRetries = 5;
            long delayMs = 200L;

            Duration marchingTime = durationHelper.execute(
                    p1,
                    p2,
                    maxRetries,
                    delayMs,
                    null,
                    TimeValidators::isValidTime,
                    TimeConverters::toDuration);

            if (marchingTime != null) {
                // Calculate rally duration: 5 minutes + (march time * 2) - 1 second buffer
                long marchSeconds = marchingTime.getSeconds();
                durationSeconds = 5 * 60 + marchSeconds * 2 - 3;
                DTOImageSearchResult deploy = searchTemplateWithRetries(EnumTemplates.BEAR_DEPLOY_BUTTON, 90, 3);

                if (!deploy.isFound()) {
                    logWarning("Deploy button not found. Rescheduling to try again in 5 minutes.");
                    return 0;
                }

                tapPoint(deploy.getPoint());

                // Schedule flag reset after rally duration (non-blocking)
                scheduler.schedule(() -> ownRallyActive.set(false),
                        durationSeconds,
                        TimeUnit.SECONDS);
                return marchSeconds;

            } else {
                logError("Could not read march time from screen");
                ownRallyActive.set(false);
                return 0;
            }

        } else {
            // Rally already active, nothing to do
            return 0;
        }
    }

    private void reloadConfig() {
        referenceTrapTime = profile.getConfig(BEAR_TRAP_SCHEDULE_DATETIME_STRING, LocalDateTime.class);
        trapPreparationTime = profile.getConfig(BEAR_TRAP_PREPARATION_TIME_INT, Integer.class);
        trapDurationTime = 30;
        callOwnRally = profile.getConfig(BEAR_TRAP_CALL_RALLY_BOOL, Boolean.class);
        usePets = profile.getConfig(BEAR_TRAP_ACTIVE_PETS_BOOL, Boolean.class);
        recallTroops = profile.getConfig(BEAR_TRAP_RECALL_TROOPS_BOOL, Boolean.class);
        trapNumber = profile.getConfig(BEAR_TRAP_NUMBER_INT, Integer.class);
        ownRallyFlag = profile.getConfig(BEAR_TRAP_RALLY_FLAG_INT, Integer.class);
        joinRallyFlag = profile.getConfig(BEAR_TRAP_JOIN_FLAG_INT, Integer.class);
        joinRally = profile.getConfig(BEAR_TRAP_JOIN_RALLY_BOOL, Boolean.class);
    }

    /**
     * Updates the BEAR_TRAP_SCHEDULE_DATETIME_STRING configuration with the next
     * trap activation time (anchor)
     * This method is called after the trap ends to persist the next execution time
     * in the database
     * The saved time is the trap activation time (anchor), not the preparation
     * window start time
     */
    private void updateNextWindowDateTime() {
        // Get the next window information
        BearTrapHelper.WindowResult result = getWindowState();

        // Get the next window START time
        LocalDateTime nextWindowStart = LocalDateTime.ofInstant(result.getNextWindowStart(), ZoneId.of("UTC"));

        // The trap activation time (anchor) is preparation time minutes after the
        // window start
        LocalDateTime nextTrapActivation = nextWindowStart.plusMinutes(trapPreparationTime);

        // Format in "dd-MM-yyyy HH:mm" format
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        String formattedDateTime = nextTrapActivation.format(formatter);

        logInfo("Updating next trap activation time to: " + formattedDateTime + " UTC");

        // Delegate to service to update the specific configuration
        ServConfig.getServices().updateProfileConfig(
                profile,
                BEAR_TRAP_SCHEDULE_DATETIME_STRING,
                formattedDateTime);
    }

    @Override
    public void reschedule(LocalDateTime newDateTime) {
        reloadConfig();
        BearTrapHelper.WindowResult result = getWindowState();

        Instant nextExecutionInstant;
        switch (result.getState()) {
            case BEFORE:
                nextExecutionInstant = result.getCurrentWindowStart();
                break;
            case INSIDE:
                nextExecutionInstant = Instant.now();
                break;
            case AFTER:
            default:
                nextExecutionInstant = result.getNextWindowStart();
                break;
        }

        LocalDateTime nextExecutionLocal = LocalDateTime.ofInstant(nextExecutionInstant, ZoneId.systemDefault());
        this.scheduledTime = nextExecutionLocal;

        logInfo("Rescheduling Bear Trap for (Local): " + nextExecutionLocal.format(DATETIME_FORMATTER));
        logInfo("Rescheduling Bear Trap for (UTC): " + LocalDateTime.ofInstant(nextExecutionInstant, ZoneId.of("UTC")).format(DATETIME_FORMATTER));
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.WORLD;
    }

    @Override
    public boolean consumesStamina() {
        return false;
    }
}
