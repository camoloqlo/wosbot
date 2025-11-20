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
     * Converts a time string to a {@link Duration}.
     *
     * Accepts:
     * <ul>
     * <li>"HH:mm:ss" → strict format</li>
     * <li>"003359" → compact 6-digit format</li>
     * <li>"24d003359" → with days prefix</li>
     * </ul>
     *
     * @param s the time string to convert
     * @return a {@link Duration} representing the total time
     * @throws IllegalArgumentException if the string is invalid
     */
    public static Duration toDuration(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Time string is null or blank");
        }

        String trimmed = s.trim().toLowerCase();

        try {
            // Try strict HH:mm:ss first
            LocalTime t = LocalTime.parse(trimmed, STRICT_HH_MM_SS);
            return Duration.ofHours(t.getHour())
                    .plusMinutes(t.getMinute())
                    .plusSeconds(t.getSecond());
        } catch (Exception ignored) {
            // Fall back to custom parsing
        }

        int days = 0;
        String timePart;

        if (trimmed.contains("d")) {
            String[] parts = trimmed.split("d", 2);
            days = Integer.parseInt(parts[0].replaceAll("\\D", ""));
            timePart = parts[1].replaceAll("\\D", "");
        } else {
            timePart = trimmed.replaceAll("\\D", "");
        }

        if (timePart.length() != 6) {
            throw new IllegalArgumentException("Expected 6 digits for time, got: " + timePart);
        }

        int hours = Integer.parseInt(timePart.substring(0, 2));
        int minutes = Integer.parseInt(timePart.substring(2, 4));
        int seconds = Integer.parseInt(timePart.substring(4, 6));

        if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
            throw new IllegalArgumentException("Invalid time range: " + s);
        }

        return Duration.ofDays(days)
                .plusHours(hours)
                .plusMinutes(minutes)
                .plusSeconds(seconds);
    }
}