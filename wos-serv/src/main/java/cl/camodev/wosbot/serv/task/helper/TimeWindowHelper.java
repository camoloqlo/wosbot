package cl.camodev.wosbot.serv.task.helper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Abstract base class for recurring time window calculations.
 *
 * Provides common functionality for determining if the current time is:
 * - BEFORE the active window
 * - INSIDE the active window
 * - AFTER the active window
 *
 * Subclasses must implement the specific logic for calculating window
 * boundaries.
 */
public abstract class TimeWindowHelper {

    /**
     * Possible states relative to a time window.
     */
    public enum WindowState {
        BEFORE, // Before the current cycle's window
        INSIDE, // Inside the execution window
        AFTER // After the current cycle's window
    }

    /**
     * Result of a window calculation with timing details and current state.
     */
    public static class WindowResult {
        private final WindowState state;
        private final Instant currentWindowStart;
        private final Instant currentWindowEnd;
        private final Instant nextWindowStart;
        private final long minutesUntilNextWindow;
        private final int currentWindowDurationMinutes;

        public WindowResult(
                WindowState state,
                Instant currentWindowStart,
                Instant currentWindowEnd,
                Instant nextWindowStart,
                long minutesUntilNextWindow,
                int currentWindowDurationMinutes) {
            this.state = state;
            this.currentWindowStart = currentWindowStart;
            this.currentWindowEnd = currentWindowEnd;
            this.nextWindowStart = nextWindowStart;
            this.minutesUntilNextWindow = minutesUntilNextWindow;
            this.currentWindowDurationMinutes = currentWindowDurationMinutes;
        }

        public WindowState getState() {
            return state;
        }

        public Instant getCurrentWindowStart() {
            return currentWindowStart;
        }

        public Instant getCurrentWindowEnd() {
            return currentWindowEnd;
        }

        public Instant getNextWindowStart() {
            return nextWindowStart;
        }

        public long getMinutesUntilNextWindow() {
            return minutesUntilNextWindow;
        }

        public int getCurrentWindowDurationMinutes() {
            return currentWindowDurationMinutes;
        }

        @Override
        public String toString() {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy HH:mm:ss 'UTC'")
                    .withZone(ZoneOffset.UTC);

            StringBuilder sb = new StringBuilder();
            sb.append("State: ").append(state).append("\n");
            sb.append("Current window duration: ").append(currentWindowDurationMinutes).append(" minutes\n");
            sb.append("Current window: ").append(fmt.format(currentWindowStart))
                    .append(" - ").append(fmt.format(currentWindowEnd)).append("\n");
            sb.append("Next window: ").append(fmt.format(nextWindowStart)).append("\n");
            sb.append("Minutes until next window: ").append(minutesUntilNextWindow);
            return sb.toString();
        }
    }

    /**
     * Determines the current state relative to the time window.
     *
     * @param now                   Current instant
     * @param currentStart          Start of current window
     * @param currentEnd            End of current window (inclusive)
     * @param nextStart             Start of next window
     * @param windowDurationMinutes Duration of the window in minutes
     * @return WindowResult with state and timing information
     */
    protected WindowResult determineWindowState(
            Instant now,
            Instant currentStart,
            Instant currentEnd,
            Instant nextStart,
            int windowDurationMinutes) {
        WindowState state;
        long minutesUntilNext;

        if (now.isBefore(currentStart)) {
            state = WindowState.BEFORE;
            minutesUntilNext = now.until(currentStart, ChronoUnit.MINUTES);
        } else if (!now.isAfter(currentEnd)) {
            // Inside window (end is inclusive)
            state = WindowState.INSIDE;
            minutesUntilNext = now.until(nextStart, ChronoUnit.MINUTES);
        } else {
            state = WindowState.AFTER;
            minutesUntilNext = now.until(nextStart, ChronoUnit.MINUTES);
        }

        return new WindowResult(
                state,
                currentStart,
                currentEnd,
                nextStart,
                minutesUntilNext,
                windowDurationMinutes);
    }

    /**
     * Checks if we're currently inside a valid execution window.
     *
     * @param result The window calculation result
     * @return true if inside window, false otherwise
     */
    protected boolean isInsideWindow(WindowResult result) {
        return result.getState() == WindowState.INSIDE;
    }

    /**
     * Gets the next execution time based on current state.
     * - If INSIDE: returns Instant.now() (execute immediately)
     * - If BEFORE or AFTER: returns next window start
     *
     * @param result The window calculation result
     * @return Next execution time
     */
    protected Instant getNextExecutionTime(WindowResult result) {
        if (result.getState() == WindowState.INSIDE) {
            return Instant.now();
        } else {
            return result.getNextWindowStart();
        }
    }

    /**
     * Validates that input parameters are positive.
     *
     * @param value         The value to validate
     * @param parameterName Name of the parameter for error message
     * @throws IllegalArgumentException if value <= 0
     */
    protected void validatePositive(int value, String parameterName) {
        if (value <= 0) {
            throw new IllegalArgumentException(parameterName + " must be greater than 0");
        }
    }

    /**
     * Validates that input parameters are non-negative.
     *
     * @param value         The value to validate
     * @param parameterName Name of the parameter for error message
     * @throws IllegalArgumentException if value < 0
     */
    protected void validateNonNegative(int value, String parameterName) {
        if (value < 0) {
            throw new IllegalArgumentException(parameterName + " must be >= 0");
        }
    }
}
