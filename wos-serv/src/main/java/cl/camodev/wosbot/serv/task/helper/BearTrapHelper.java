package cl.camodev.wosbot.serv.task.helper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Utility class that helps determine the current status of the
 * "Bear Trap" event window — a recurring time window anchored at a reference time.
 *
 * Semantics (anchor model):
 * - Start  = referenceAnchorUtc - n minutes
 * - End    = referenceAnchorUtc + 30 minutes
 * - Length = 30 + n minutes
 * - Repeats every 'intervalDays' days (default: 2)
 *
 * This class can calculate whether the current time is:
 * BEFORE the active window, INSIDE the active window (end inclusive), or AFTER it,
 * and provide timing details such as how long until the next cycle.
 */
public final class BearTrapHelper extends TimeWindowHelper {

    /** Fixed portion of the window (in minutes). */
    private static final int DEFAULT_FIXED_WINDOW_MINUTES = 30;

    /** Interval (in days) between each Bear Trap cycle. */
    private static final int DEFAULT_INTERVAL_DAYS = 2;

    /** Prevent instantiation. */
    private BearTrapHelper() {}

    /**
     * Calculates the Bear Trap window state for an anchor time.
     * Anchor semantics:
     * - Start = anchor - variableMinutes
     * - End   = anchor + fixedWindowMinutes (default 30)
     *
     * @param referenceAnchorUtc Anchor instant in UTC.
     * @param variableMinutes    Extra minutes n (must be >= 0).
     * @return WindowResult containing current state and timing info.
     */
    public static WindowResult calculateWindow(Instant referenceAnchorUtc, int variableMinutes) {
        return calculateWindow(
                referenceAnchorUtc,
                variableMinutes,
                DEFAULT_FIXED_WINDOW_MINUTES,
                DEFAULT_INTERVAL_DAYS,
                Clock.systemUTC()
        );
    }

    /**
     * Calculates the Bear Trap window state using configurable parameters and a testable Clock.
     *
     * Anchor semantics:
     * - Start = anchor - variableMinutes
     * - End   = anchor + fixedWindowMinutes
     * - Length = fixedWindowMinutes + variableMinutes
     *
     * End is treated as inclusive (i.e., now == end → INSIDE).
     *
     * @param referenceAnchorUtc    Anchor instant in UTC.
     * @param variableMinutes       Extra minutes n (must be >= 0).
     * @param fixedWindowMinutes    Fixed minutes (default 30) (must be > 0).
     * @param intervalDays          Cycle interval in days (must be > 0).
     * @param clock                 Time source.
     * @return WindowResult with computed timing and current state.
     * @throws IllegalArgumentException on invalid parameters.
     */
    public static WindowResult calculateWindow(
            Instant referenceAnchorUtc,
            int variableMinutes,
            int fixedWindowMinutes,
            int intervalDays,
            Clock clock
    ) {
        // Validation using inherited methods
        BearTrapHelper helper = new BearTrapHelper();
        helper.validatePositive(fixedWindowMinutes, "Fixed window minutes");
        helper.validateNonNegative(variableMinutes, "Variable minutes (n)");
        helper.validatePositive(intervalDays, "Interval in days");

        final Instant now = Instant.now(clock);

        // Anchor semantics
        final Instant referenceStart = referenceAnchorUtc.minus(variableMinutes, ChronoUnit.MINUTES);
        final int windowDuration = fixedWindowMinutes + variableMinutes;

        final long intervalMinutes = intervalDays * 24L * 60L;
        final long minutesSinceRefStart = referenceStart.until(now, ChronoUnit.MINUTES);

        // Before the first anchored window
        if (minutesSinceRefStart < 0) {
            final Instant windowEnd = referenceStart.plus(windowDuration, ChronoUnit.MINUTES);
            return helper.determineWindowState(
                    now,
                    referenceStart,
                    windowEnd,
                    referenceStart,
                    windowDuration
            );
        }

        // Current cycle based on the anchored start
        final long cycles = minutesSinceRefStart / intervalMinutes;
        final Instant currentStart = referenceStart.plus(cycles * intervalMinutes, ChronoUnit.MINUTES);
        final Instant currentEnd   = currentStart.plus(windowDuration, ChronoUnit.MINUTES);
        final Instant nextStart    = currentStart.plus(intervalMinutes, ChronoUnit.MINUTES);

        return helper.determineWindowState(
                now,
                currentStart,
                currentEnd,
                nextStart,
                windowDuration
        );
    }

    /**
     * Checks whether the Bear Trap event should execute now.
     * Returns true only if the current time is within the active window
     * (end inclusive).
     *
     * @param referenceAnchorUtc Anchor instant in UTC.
     * @param variableMinutes    Extra minutes n (>= 0).
     * @return true if inside the active window; false otherwise.
     */
    public static boolean shouldRun(Instant referenceAnchorUtc, int variableMinutes) {
        return calculateWindow(referenceAnchorUtc, variableMinutes).getState() == WindowState.INSIDE;
    }
}
