package cl.camodev.wosbot.serv.task.helper;

import java.time.*;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

/**
 * Utility class for Alliance Championship task scheduling.
 *
 * Alliance Championship runs weekly with a specific execution window:
 * - START: Monday 00:01 UTC
 * - END: TUESDAY 22:55 UTC
 * - Total duration: ~46 hours
 *
 * This helper calculates whether we're currently inside a valid execution window
 * and provides timing information for scheduling.
 */
public final class AllianceChampionshipHelper extends TimeWindowHelper {

    /** Day of week when window starts (Monday = 1) */
    private static final int WINDOW_START_DAY = DayOfWeek.MONDAY.getValue();

    /** Day of week when window ends (TUESDAY = 3) */
    private static final int WINDOW_END_DAY = DayOfWeek.TUESDAY.getValue();

    /** Hour when window starts on Monday (00:01 = 0 hours, 1 minute) */
    private static final int WINDOW_START_HOUR = 0;
    private static final int WINDOW_START_MINUTE = 1;

    /** Hour when window ends on TUESDAY (22:55 = 22 hours, 55 minutes) */
    private static final int WINDOW_END_HOUR = 22;
    private static final int WINDOW_END_MINUTE = 55;

    /** Prevent instantiation */
    private AllianceChampionshipHelper() {}

    /**
     * Calculates the Alliance Championship window state for the current time.
     *
     * @return WindowResult containing current state and timing info
     */
    public static WindowResult calculateWindow() {
        return calculateWindow(Clock.systemUTC());
    }

    /**
     * Calculates the Alliance Championship window state using a testable Clock.
     *
     * Window definition:
     * - Starts: Monday 00:01 UTC
     * - Ends: Tuesday 22:55 UTC
     * - Repeats weekly
     *
     * @param clock Time source
     * @return WindowResult with computed timing and current state
     */
    public static WindowResult calculateWindow(Clock clock) {
        final ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));

        // Calculate current week's window
        final ZonedDateTime currentWindowStart = getWindowStart(now);
        final ZonedDateTime currentWindowEnd = getWindowEnd(now);
        final ZonedDateTime nextWindowStart = currentWindowStart.plusWeeks(1);

        // Calculate window duration in minutes
        final int windowDurationMinutes = (int) currentWindowStart.until(currentWindowEnd, ChronoUnit.MINUTES);

        // Use inherited method to determine state
        AllianceChampionshipHelper helper = new AllianceChampionshipHelper();
        return helper.determineWindowState(
                now.toInstant(),
                currentWindowStart.toInstant(),
                currentWindowEnd.toInstant(),
                nextWindowStart.toInstant(),
                windowDurationMinutes
        );
    }

    /**
     * Calculates the start of the current week's window (Monday 00:01 UTC).
     * If today is before Monday, returns this week's Monday.
     * If today is Monday or later, returns this week's Monday.
     *
     * @param now Current time in UTC
     * @return Start of current week's window
     */
    private static ZonedDateTime getWindowStart(ZonedDateTime now) {
        // Get this week's Monday at 00:01
        return now
                .with(ChronoField.DAY_OF_WEEK, WINDOW_START_DAY)
                .withHour(WINDOW_START_HOUR)
                .withMinute(WINDOW_START_MINUTE)
                .withSecond(0)
                .withNano(0);
    }

    /**
     * Calculates the end of the current week's window (Wednesday 22:55 UTC).
     *
     * @param now Current time in UTC
     * @return End of current week's window
     */
    private static ZonedDateTime getWindowEnd(ZonedDateTime now) {
        // Get this week's TUESDAY at 22:55
        return now
                .with(ChronoField.DAY_OF_WEEK, WINDOW_END_DAY)
                .withHour(WINDOW_END_HOUR)
                .withMinute(WINDOW_END_MINUTE)
                .withSecond(59)
                .withNano(999_999_999);
    }

    /**
     * Checks if the current time is inside a valid execution window.
     *
     * @return true if inside window, false otherwise
     */
    public static boolean isInsideWindow() {
        return calculateWindow().getState() == WindowState.INSIDE;
    }

    /**
     * Gets the next execution time based on current state.
     * - If BEFORE or AFTER: returns next window start
     * - If INSIDE: returns current instant (execute now)
     *
     * @return Next execution time
     */
    public static Instant getNextExecutionTime() {
        WindowResult result = calculateWindow();
        AllianceChampionshipHelper helper = new AllianceChampionshipHelper();
        return helper.getNextExecutionTime(result);
    }
}
