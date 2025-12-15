package cl.camodev.wosbot.serv.task;

import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.wosbot.almac.repo.ProfileRepository;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.serv.ocr.BotTextRecognitionProvider;
import cl.camodev.wosbot.serv.task.constants.CommonOCRSettings;
import cl.camodev.wosbot.serv.task.helper.*;
import cl.camodev.wosbot.serv.task.impl.ArenaTask;
import cl.camodev.wosbot.serv.task.impl.BearTrapTask;
import cl.camodev.wosbot.serv.task.impl.InitializeTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for all game automation tasks.
 * 
 * <p>
 * This class provides the core infrastructure for scheduled task execution
 * including:
 * <ul>
 * <li>Task scheduling and priority management</li>
 * <li>Profile configuration refresh</li>
 * <li>Helper class initialization (stamina, march, navigation, etc.)</li>
 * <li>Screen location verification</li>
 * <li>Logging utilities</li>
 * <li>Basic emulator interaction (tap, swipe)</li>
 * </ul>
 * 
 * <p>
 * <b>Task Lifecycle:</b>
 * <ol>
 * <li>Task is scheduled in {@link TaskQueue}</li>
 * <li>{@link #run()} method refreshes profile and validates game state</li>
 * <li>{@link #execute()} method (implemented by subclass) performs the
 * task</li>
 * <li>Task reschedules itself or completes</li>
 * </ol>
 * 
 * <p>
 * <b>Helper Classes:</b>
 * All helper instances are initialized eagerly in the constructor and available
 * to subclasses for use in task implementations.
 * 
 * @author WoS Bot
 * @see TaskQueue
 * @see StaminaHelper
 * @see MarchHelper
 * @see NavigationHelper
 */
public abstract class DelayedTask implements Runnable, Delayed {

    // ========================================================================
    // CORE TASK FIELDS
    // ========================================================================

    protected volatile boolean recurring = true;
    protected LocalDateTime lastExecutionTime;
    protected LocalDateTime scheduledTime;
    protected String taskName;
    protected DTOProfiles profile;
    protected String EMULATOR_NUMBER;
    protected TpDailyTaskEnum tpTask;
    protected boolean shouldUpdateConfig;

    // ========================================================================
    // SERVICE INSTANCES
    // ========================================================================

    protected EmulatorManager emuManager = EmulatorManager.getInstance();
    protected ServScheduler servScheduler = ServScheduler.getServices();
    protected ServLogs servLogs = ServLogs.getServices();
    private ProfileLogger logger;

    // ========================================================================
    // HELPER INSTANCES
    // ========================================================================

    protected BotTextRecognitionProvider provider;
    protected TextRecognitionRetrier<Integer> integerHelper;
    protected TextRecognitionRetrier<Duration> durationHelper;
    protected TextRecognitionRetrier<String> stringHelper;

    protected NavigationHelper navigationHelper;
    protected TemplateSearchHelper templateSearchHelper;
    protected StaminaHelper staminaHelper;
    protected MarchHelper marchHelper;
    protected IntelScreenHelper intelScreenHelper;
    protected AllianceHelper allianceHelper;
    protected EventHelper eventHelper;

    // ========================================================================
    // TIME FORMATTERS
    // ========================================================================

    protected static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    protected static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    /**
     * Constructs a new DelayedTask with all helper instances initialized.
     * 
     * @param profile The profile this task will execute for
     * @param tpTask  The task type enum
     */
    public DelayedTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        this.profile = profile;
        this.taskName = tpTask.getName();
        this.scheduledTime = LocalDateTime.now();
        this.EMULATOR_NUMBER = profile.getEmulatorNumber();
        this.tpTask = tpTask;
        this.logger = new ProfileLogger(this.getClass(), profile);

        // Initialize OCR providers and helpers
        this.provider = new BotTextRecognitionProvider(emuManager, EMULATOR_NUMBER);
        this.integerHelper = new TextRecognitionRetrier<>(provider);
        this.durationHelper = new TextRecognitionRetrier<>(provider);
        this.stringHelper = new TextRecognitionRetrier<>(provider);

        // Initialize game helpers
        this.templateSearchHelper = new TemplateSearchHelper(emuManager, EMULATOR_NUMBER, profile);
        this.navigationHelper = new NavigationHelper(emuManager, EMULATOR_NUMBER, profile);
        this.marchHelper = new MarchHelper(emuManager, EMULATOR_NUMBER, stringHelper, profile);
        this.staminaHelper = new StaminaHelper(emuManager, EMULATOR_NUMBER, integerHelper, durationHelper, profile,
                marchHelper);
        this.intelScreenHelper = new IntelScreenHelper(emuManager, EMULATOR_NUMBER, templateSearchHelper,
                navigationHelper, profile);
        this.allianceHelper = new AllianceHelper(emuManager, EMULATOR_NUMBER, templateSearchHelper, navigationHelper,
                profile);
        this.eventHelper = new EventHelper(emuManager, EMULATOR_NUMBER, profile);
    }

    /**
     * Returns a distinct key for task identification in equals/hashCode.
     * 
     * <p>
     * Override this to provide unique identification for tasks that can have
     * multiple instances with different parameters (e.g., different targets).
     * 
     * @return A unique identifier for this task instance, or null if not needed
     */
    protected Object getDistinctKey() {
        return null;
    }

    /**
     * Specifies the required screen location before task execution.
     * 
     * <p>
     * Override this to indicate whether the task needs to start from:
     * <ul>
     * <li>{@link EnumStartLocation#HOME} - City view</li>
     * <li>{@link EnumStartLocation#WORLD} - World map view</li>
     * <li>{@link EnumStartLocation#ANY} - Either location (default)</li>
     * </ul>
     * 
     * @return The required starting screen location
     */
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.ANY;
    }

    /**
     * Main task execution entry point.
     * 
     * <p>
     * This method:
     * <ol>
     * <li>Refreshes profile from database</li>
     * <li>Verifies game is running</li>
     * <li>Ensures correct screen location</li>
     * <li>Validates stamina if needed</li>
     * <li>Calls {@link #execute()} for task-specific logic</li>
     * <li>Saves profile if configuration changed</li>
     * <li>Returns to correct screen location</li>
     * </ol>
     */
    @Override
    public void run() {
        refreshProfileFromDatabase();

        // InitializeTask has special handling
        if (this instanceof InitializeTask) {
            execute();
            return;
        }

        validateGameIsRunning();
        navigationHelper.ensureCorrectScreenLocation(getRequiredStartLocation());

        if (consumesStamina()) {
            validateAndUpdateStamina();
        }

        execute();

        if (shouldUpdateConfig) {
            ServProfiles.getServices().saveProfile(profile);
            shouldUpdateConfig = false;
        }

        sleepTask(2000); // Brief delay before cleanup
        navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.ANY);
    }

    /**
     * Task-specific execution logic.
     * 
     * <p>
     * Subclasses must implement this method with their task-specific behavior.
     * This method is called after all setup and validation is complete.
     */
    protected abstract void execute();

    /**
     * Refreshes the profile from the database to ensure current configurations.
     */
    private void refreshProfileFromDatabase() {
        try {
            if (profile != null && profile.getId() != null) {
                DTOProfiles updated = ProfileRepository.getRepository()
                        .getProfileWithConfigsById(profile.getId());
                if (updated != null) {
                    this.profile = updated;
                }
            }
        } catch (Exception e) {
            logWarning("Could not refresh profile before execution: " + e.getMessage());
        }
    }

    /**
     * Validates that the game package is currently running.
     * 
     * @throws HomeNotFoundException if game is not running
     */
    private void validateGameIsRunning() {
        if (!emuManager.isPackageRunning(EMULATOR_NUMBER, EmulatorManager.GAME.getPackageName())) {
            throw new HomeNotFoundException("Game is not running");
        }
    }

    /**
     * Validates and updates stamina if service requires refresh.
     */
    private void validateAndUpdateStamina() {
        if (StaminaService.getServices().requiresUpdate(profile.getId())) {
            staminaHelper.updateStaminaFromProfile();
        }
    }

    // ========================================================================
    // OCR CONVENIENCE METHODS
    // ========================================================================

    /**
     * Reads an integer value from a screen region using OCR.
     * 
     * @param topLeft     Top-left corner of OCR region
     * @param bottomRight Bottom-right corner of OCR region
     * @param settings    Tesseract OCR settings
     * @return Parsed integer value, or null if OCR failed
     */
    protected Integer readNumberValue(DTOPoint topLeft, DTOPoint bottomRight, DTOTesseractSettings settings) {
        Integer result = integerHelper.execute(
                topLeft,
                bottomRight,
                5, // Max retry attempts
                200L, // Delay between retries
                settings,
                text -> NumberValidators.matchesPattern(text, CommonOCRSettings.NUMBER_PATTERN),
                text -> NumberConverters.regexToInt(text, CommonOCRSettings.NUMBER_PATTERN));

        logDebug("Number value read: " + (result != null ? result : "null"));
        return result;
    }

    /**
     * Reads a string value from a screen region using OCR.
     * 
     * @param topLeft     Top-left corner of OCR region
     * @param bottomRight Bottom-right corner of OCR region
     * @param settings    Tesseract OCR settings
     * @return Parsed string value, or null if OCR failed
     */
    protected String readStringValue(DTOPoint topLeft, DTOPoint bottomRight, DTOTesseractSettings settings) {
        String result = stringHelper.execute(
                topLeft,
                bottomRight,
                5, // Max retry attempts
                200L, // Delay between retries
                settings,
                Objects::nonNull,
                text -> text);

        logDebug("String value read: " + (result != null ? result : "null"));
        return result;
    }

    // ========================================================================
    // EMULATOR INTERACTION METHODS
    // ========================================================================

    /**
     * Taps at the specified point on the emulator screen.
     * 
     * @param point The point to tap
     */
    public void tapPoint(DTOPoint point) {
        emuManager.tapAtPoint(EMULATOR_NUMBER, point);
    }

    /**
     * Taps at a random point within the specified rectangle.
     * 
     * @param p1 First corner of the rectangle
     * @param p2 Opposite corner of the rectangle
     */
    public void tapRandomPoint(DTOPoint p1, DTOPoint p2) {
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, p1, p2);
    }

    /**
     * Taps at random points within the specified rectangle multiple times.
     * 
     * @param p1    First corner of the rectangle
     * @param p2    Opposite corner of the rectangle
     * @param count Number of taps to perform
     * @param delay Delay in milliseconds between taps
     */
    public void tapRandomPoint(DTOPoint p1, DTOPoint p2, int count, int delay) {
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, p1, p2, count, delay);
    }

    /**
     * Performs a swipe gesture on the emulator screen.
     * 
     * @param start Starting point of the swipe
     * @param end   Ending point of the swipe
     */
    public void swipe(DTOPoint start, DTOPoint end) {
        emuManager.executeSwipe(EMULATOR_NUMBER, start, end);
    }

    /**
     * Taps the back button on the emulator.
     */
    public void tapBackButton() {
        emuManager.tapBackButton(EMULATOR_NUMBER);
    }

    /**
     * Sleeps for the specified duration.
     * 
     * @param millis Duration to sleep in milliseconds
     * @throws RuntimeException if interrupted
     */
    protected void sleepTask(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task was interrupted during sleep", e);
        }
    }

    // ========================================================================
    // LOGGING METHODS
    // ========================================================================

    public void logInfo(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.info(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), message);
    }

    public void logWarning(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.warn(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), message);
    }

    public void logError(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.error(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), message);
    }

    public void logError(String message, Throwable t) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.error(prefixedMessage, t);
        servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), message);
    }

    public void logDebug(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.debug(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.DEBUG, taskName, profile.getName(), message);
    }

    // ========================================================================
    // HOOK METHODS (Override in subclasses)
    // ========================================================================

    /**
     * Indicates whether this task provides daily mission progress.
     * 
     * @return true if task completion counts toward daily missions
     */
    public boolean provideDailyMissionProgress() {
        return false;
    }

    /**
     * Indicates whether this task provides triumph progress.
     * 
     * @return true if task completion counts toward triumph rewards
     */
    public boolean provideTriumphProgress() {
        return false;
    }

    /**
     * Indicates whether this task consumes stamina.
     * 
     * @return true if task requires stamina validation before execution
     */
    protected boolean consumesStamina() {
        return false;
    }

    // ========================================================================
    // SCHEDULING METHODS
    // ========================================================================

    public void reschedule(LocalDateTime rescheduledTime) {
        Duration difference = Duration.between(LocalDateTime.now(), rescheduledTime);
        scheduledTime = LocalDateTime.now().plus(difference);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = scheduledTime.toEpochSecond(ZoneOffset.UTC) -
                LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        return unit.convert(diff, TimeUnit.SECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (this == o)
            return 0;

        // Priority 1: InitializeTask has absolute highest priority
        boolean thisInit = this instanceof InitializeTask;
        boolean otherInit = o instanceof InitializeTask;
        if (thisInit && !otherInit)
            return -1;
        if (!thisInit && otherInit)
            return 1;

        // Get delays
        long thisDelay = this.getDelay(TimeUnit.NANOSECONDS);
        long otherDelay = o.getDelay(TimeUnit.NANOSECONDS);

        boolean thisReady = thisDelay <= 0;
        boolean otherReady = otherDelay <= 0;

        // Priority 2: Tasks with delay <= 0 (ready) have higher priority
        if (thisReady && !otherReady)
            return -1;
        if (!thisReady && otherReady)
            return 1;

        // If both ready, prioritize by task type
        if (thisReady && otherReady) {
            // Priority 3: BearTrapTask
            boolean thisBearTrap = this instanceof BearTrapTask;
            boolean otherBearTrap = o instanceof BearTrapTask;
            if (thisBearTrap && !otherBearTrap)
                return -1;
            if (!thisBearTrap && otherBearTrap)
                return 1;

            // Priority 4: ArenaTask
            boolean thisArena = this instanceof ArenaTask;
            boolean otherArena = o instanceof ArenaTask;
            if (thisArena && !otherArena)
                return -1;
            if (!thisArena && otherArena)
                return 1;
        }

        // Priority 5: Compare by scheduled time
        return Long.compare(thisDelay, otherDelay);
    }

    // ========================================================================
    // GETTERS & SETTERS
    // ========================================================================

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public LocalDateTime getLastExecutionTime() {
        return lastExecutionTime;
    }

    public void setLastExecutionTime(LocalDateTime lastExecutionTime) {
        this.lastExecutionTime = lastExecutionTime;
    }

    public Integer getTpDailyTaskId() {
        return tpTask.getId();
    }

    public TpDailyTaskEnum getTpTask() {
        return tpTask;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setProfile(DTOProfiles profile) {
        this.profile = profile;
    }

    public LocalDateTime getScheduled() {
        return scheduledTime;
    }

    public void setShouldUpdateConfig(boolean shouldUpdateConfig) {
        this.shouldUpdateConfig = shouldUpdateConfig;
    }

    // ========================================================================
    // EQUALITY & HASHING
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof DelayedTask))
            return false;
        if (getClass() != o.getClass())
            return false;

        DelayedTask that = (DelayedTask) o;

        if (tpTask != that.tpTask)
            return false;
        if (!Objects.equals(profile.getId(), that.profile.getId()))
            return false;

        Object keyThis = this.getDistinctKey();
        Object keyThat = that.getDistinctKey();
        if (keyThis != null || keyThat != null) {
            return Objects.equals(keyThis, keyThat);
        }

        return true;
    }

    @Override
    public int hashCode() {
        Object key = getDistinctKey();
        if (key != null) {
            return Objects.hash(getClass(), tpTask, profile.getId(), key);
        } else {
            return Objects.hash(getClass(), tpTask, profile.getId());
        }
    }
}