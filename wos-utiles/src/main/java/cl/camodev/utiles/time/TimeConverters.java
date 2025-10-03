package cl.camodev.utiles.time;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility methods for converting time strings into {@link Duration} objects.
 */
public final class TimeConverters {

    private static final DateTimeFormatter STRICT_HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TimeConverters() {
        // Prevent instantiation
    }

    /**
     * Converts a time string in the strict format "HH:mm:ss" to a {@link Duration}.
     * The duration is computed relative to midnight (00:00:00), ignoring days.
     *
     * @param s the time string to convert
     * @return a {@link Duration} representing the hours, minutes and seconds contained
     *         in the string, or {@code null} if the string is null
     * @throws java.time.format.DateTimeParseException if the string does not match the expected format
     */
    public static Duration hhmmssToDuration(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        LocalTime t = LocalTime.parse(trimmed, STRICT_HH_MM_SS);
        return Duration.ofHours(t.getHour())
                       .plusMinutes(t.getMinute())
                       .plusSeconds(t.getSecond());
    }
}