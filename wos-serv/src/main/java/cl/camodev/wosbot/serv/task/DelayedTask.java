package cl.camodev.wosbot.serv.task;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.ex.ProfileInReconnectStateException;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.impl.InitializeTask;
import net.sourceforge.tess4j.TesseractException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public abstract class DelayedTask implements Runnable, Delayed {

    private ProfileLogger logger; // Will be initialized in constructor

    protected volatile boolean recurring = true;
    protected LocalDateTime lastExecutionTime;
    protected LocalDateTime scheduledTime;
    protected String taskName;
    protected DTOProfiles profile;
    protected String EMULATOR_NUMBER;
    protected TpDailyTaskEnum tpTask;

    protected EmulatorManager emuManager = EmulatorManager.getInstance();
    protected ServScheduler servScheduler = ServScheduler.getServices();
    protected ServLogs servLogs = ServLogs.getServices();

    public DelayedTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        this.profile = profile;
        this.taskName = tpTask.getName();
        this.scheduledTime = LocalDateTime.now();
        this.EMULATOR_NUMBER = profile.getEmulatorNumber();
        this.tpTask = tpTask;
        this.logger = new ProfileLogger(this.getClass(), profile);
    }

    protected Object getDistinctKey() {
        return null;
    }

    /**
     * Override this method to specify where the task should start execution.
     *
     * @return EnumStartLocation indicating the required starting location
     */
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.ANY;
    }

    @Override
    public void run() {

        if (this instanceof InitializeTask) {
            execute();
            return;
        }

        if (!EmulatorManager.getInstance().isPackageRunning(EMULATOR_NUMBER, EmulatorManager.GAME.getPackageName())) {
            throw new HomeNotFoundException("Game is not running");
        }

        ensureCorrectScreenLocation(getRequiredStartLocation());
        execute();
    }


    protected abstract void execute();

    /**
     * Ensures the emulator is on the correct screen (Home or World) before proceeding.
     * It will attempt to navigate if it's on the wrong screen or press back if lost.
     * @param requiredLocation The desired screen location (HOME, WORLD, or ANY).
     */
    protected void ensureCorrectScreenLocation(EnumStartLocation requiredLocation) {
        logDebug("Verifying screen location. Required: " + requiredLocation);

        for (int attempt = 1; attempt <= 10; attempt++) {
            DTOImageSearchResult home = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE, 90);
            DTOImageSearchResult world = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_WORLD, 90);
            DTOImageSearchResult reconnect = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_RECONNECT, 90);

            if (reconnect.isFound()) {
                throw new ProfileInReconnectStateException("Profile " + profile.getName() + " is in reconnect state, cannot execute task: " + taskName);
            }

            if (home.isFound() || world.isFound()) {
                // Found either home or world, now check if we need to navigate to the correct location
                if (requiredLocation == EnumStartLocation.HOME && !home.isFound()) {
                    // We need HOME but we're in WORLD, navigate to HOME
                    logInfo("Navigating from WORLD to HOME screen...");
                    emuManager.tapAtPoint(EMULATOR_NUMBER, world.getPoint());
                    sleepTask(2000); // Wait for navigation

                    // Validate that we actually moved to HOME
                    DTOImageSearchResult homeAfterNav = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE, 90);
                    if (!homeAfterNav.isFound()) {
                        logWarning("Failed to navigate to HOME, retrying...");
                        continue; // Try again
                    }
                    logInfo("Successfully navigated to HOME screen.");

                } else if (requiredLocation == EnumStartLocation.WORLD && !world.isFound()) {
                    // We need WORLD but we're in HOME, navigate to WORLD
                    logInfo("Navigating from HOME to WORLD screen...");
                    emuManager.tapAtPoint(EMULATOR_NUMBER, home.getPoint());
                    sleepTask(2000); // Wait for navigation

                    // Validate that we actually moved to WORLD
                    DTOImageSearchResult worldAfterNav = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_WORLD, 90);
                    if (!worldAfterNav.isFound()) {
                        logWarning("Failed to navigate to WORLD, retrying...");
                        continue; // Try again
                    }
                    logInfo("Successfully navigated to WORLD screen.");
                }
                // If requiredLocation is ANY, we can execute from either location
                return; // Success, correct screen is found
            } else {
                logWarning("Home/World screen not found. Tapping back button (Attempt " + attempt + "/10)");
                EmulatorManager.getInstance().tapBackButton(EMULATOR_NUMBER);
                sleepTask(100);
            }
        }

        logError("Failed to find Home/World screen after 10 attempts.");
        throw new HomeNotFoundException("Home not found after 10 attempts");
    }

    protected void ensureOnIntelScreen() {
        sleepTask(500);
        logInfo("Ensuring we are on the intel screen.");

        // First, check if we are already on the intel screen.
        if (isIntelScreenActive()) {
            logInfo("Already on the intel screen.");
            return;
        }
        logWarning("Not on intel screen. Attempting to navigate.");

        // If not on the intel screen, make sure we are on the world screen to find the intel button.
        ensureCorrectScreenLocation(EnumStartLocation.WORLD);

        // Now, find and click the intel button.
        for (int i = 0; i < 3; i++) {
            DTOImageSearchResult intelButton = emuManager.searchTemplate(EMULATOR_NUMBER,
                    EnumTemplates.GAME_HOME_INTEL, 90);
            if (intelButton.isFound()) {
                logInfo("Intel button found. Tapping to open the intel screen.");
                emuManager.tapAtPoint(EMULATOR_NUMBER, intelButton.getPoint());
                sleepTask(1000); // Wait for screen transition

                // Check if successfully navigated to intel screen
                if (isIntelScreenActive()) {
                    logInfo("Successfully navigated to the intel screen.");
                    return;
                }

                logWarning("Tapped intel button, but still not on intel screen. Retrying...");
                tapBackButton();
                sleepTask(500);
            } else {
                logDebug("Intel button not found. Attempt " + (i + 1) + "/3. Retrying...");
                sleepTask(300);
            }
        }
        
        logError("Failed to find the intel button after 3 attempts.");
        throw new HomeNotFoundException("Failed to navigate to intel screen.");
    }
    
    /**
     * Helper method to check if we're currently on the intel screen.
     * Uses both image recognition and OCR for verification.
     * Makes two attempts before determining the screen state.
     * 
     * @return true if we're on the intel screen, false otherwise
     */
    private boolean isIntelScreenActive() {
        // Make two attempts at detection
        for (int attempt = 0; attempt < 2; attempt++) {
            // Try image recognition first (faster)
            DTOImageSearchResult intelScreen1 = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_SCREEN_1, 90);
            DTOImageSearchResult intelScreen2 = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.INTEL_SCREEN_2, 90);
            
            if (intelScreen1.isFound() || intelScreen2.isFound()) {
                logDebug("Intel screen confirmed via image template (attempt " + (attempt + 1) + ")");
                return true;
            }
            
            // Fallback to OCR check
            try {
                String intelText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(85, 15), new DTOPoint(171, 62));
                if (intelText != null && intelText.toLowerCase().contains("intel")) {
                    logDebug("Intel screen confirmed via OCR (attempt " + (attempt + 1) + ")");
                    return true;
                }
            } catch (IOException | TesseractException e) {
                logWarning("Could not perform OCR to check for intel screen. Error: " + e.getMessage());
            }
            
            // If this is the first attempt and we didn't find the intel screen, wait briefly before trying again
            if (attempt == 0) {
                sleepTask(300);
            }
        }
        
        // After two attempts, we still couldn't find the intel screen
        logDebug("Intel screen not detected after two attempts");
        return false;
    }

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

    public void reschedule(LocalDateTime rescheduledTime) {
        Duration difference = Duration.between(LocalDateTime.now(), rescheduledTime);
        scheduledTime = LocalDateTime.now().plus(difference);
    }

    protected void sleepTask(long millis) {
        try {
            //long speedFactor = (long) (millis*1.3);
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task was interrupted during sleep", e);
        }
    }

    public String getTaskName() {
        return taskName;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = scheduledTime.toEpochSecond(ZoneOffset.UTC) - LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        return unit.convert(diff, TimeUnit.SECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (this == o) return 0;

        boolean thisInit = this instanceof InitializeTask;
        boolean otherInit = o instanceof InitializeTask;
        if (thisInit && !otherInit) return -1;
        if (!thisInit && otherInit) return 1;


        long diff = this.getDelay(TimeUnit.NANOSECONDS)
                - o.getDelay(TimeUnit.NANOSECONDS);
        return Long.compare(diff, 0);
    }

    public LocalDateTime getScheduled() {
        return scheduledTime;
    }

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


    public boolean provideDailyMissionProgress() {
        return false;
    }

    public boolean provideTriumphProgress() {
        return false;
    }

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

    /**
     * Taps at the specified point on the emulator screen.
     *
     * @param point The point to tap.
     */
    public void tapPoint(DTOPoint point) {
        emuManager.tapAtPoint(EMULATOR_NUMBER, point);
    }

    /**
     * Taps at a random point within the rectangle defined by two points on the emulator screen.
     *
     * @param p1 The first corner of the rectangle.
     * @param p2 The opposite corner of the rectangle.
     */
    public void tapRandomPoint(DTOPoint p1, DTOPoint p2) {
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, p1, p2);
    }

    /**
     * Taps at random points within the rectangle defined by two points on the emulator screen,
     * repeating the action a specified number of times with a delay between taps.
     *
     * @param p1    The first corner of the rectangle.
     * @param p2    The opposite corner of the rectangle.
     * @param count The number of taps to perform.
     * @param delay The delay in milliseconds between each tap.
     */
    public void tapRandomPoint(DTOPoint p1, DTOPoint p2, int count, int delay) {
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, p1, p2, count, delay);
    }


    /**
     * Performs a swipe action from a start point to an end point on the emulator screen.
     *
     * @param start The starting point of the swipe.
     * @param end   The ending point of the swipe.
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

}
