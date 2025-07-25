package cl.camodev.wosbot.serv.task;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.ex.ProfileInReconnectStateException;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.impl.InitializeTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public abstract class DelayedTask implements Runnable, Delayed, Comparable<Delayed> {

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

        if (!EmulatorManager.getInstance().isPackageRunning(EMULATOR_NUMBER, EmulatorManager.WHITEOUT_PACKAGE)) {
            throw new HomeNotFoundException("Game is not running");
        }

        EnumStartLocation requiredLocation = getRequiredStartLocation();

        for (int attempt = 1; attempt <= 10; attempt++) {
            DTOImageSearchResult home = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE.getTemplate(), 90);
            DTOImageSearchResult world = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_WORLD.getTemplate(), 90);
            DTOImageSearchResult reconnect = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_RECONNECT.getTemplate(), 90);

            if (reconnect.isFound()) {
                throw new ProfileInReconnectStateException("Profile " + profile.getName() + " is in reconnect state, cannot execute task: " + taskName);
            }

            if (home.isFound() || world.isFound()) {
                // Found either home or world, now check if we need to navigate to the correct location
                if (requiredLocation == EnumStartLocation.HOME && !home.isFound()) {
                    // We need HOME but we're in WORLD, navigate to HOME
                    emuManager.tapAtPoint(EMULATOR_NUMBER, world.getPoint());
                    sleepTask(2000); // Wait for navigation

                    // Validate that we actually moved to HOME
                    DTOImageSearchResult homeAfterNav = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE.getTemplate(), 90);
                    if (!homeAfterNav.isFound()) {
                        logWarning("Failed to navigate to HOME, retrying...");
                        continue; // Try again
                    }

                } else if (requiredLocation == EnumStartLocation.WORLD && !world.isFound()) {
                    // We need WORLD but we're in HOME, navigate to WORLD
                    emuManager.tapAtPoint(EMULATOR_NUMBER, home.getPoint());
                    sleepTask(2000); // Wait for navigation

                    // Validate that we actually moved to WORLD
                    DTOImageSearchResult worldAfterNav = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_WORLD.getTemplate(), 90);
                    if (!worldAfterNav.isFound()) {
                        logWarning("Failed to navigate to WORLD, retrying...");
                        continue; // Try again
                    }
                }
                // If requiredLocation is ANY, we can execute from either location

                execute();
                return;
            } else {
                EmulatorManager.getInstance().tapBackButton(EMULATOR_NUMBER);
                sleepTask(100);
            }
        }

        throw new HomeNotFoundException("Home not found after 10 attempts");
    }


    protected abstract void execute();

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
            long speedFactor = (long) (millis*1.3);
            Thread.sleep(speedFactor);
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
        servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), message);
    }

    public void logWarning(String message) {
        servLogs.appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), message);
    }

    public void logError(String message) {
        servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), message);
    }

    public void logDebug(String message) {
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
