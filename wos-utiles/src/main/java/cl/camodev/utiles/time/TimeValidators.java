package cl.camodev.utiles.time;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility methods to validate and parse time strings.
 */
public final class TimeValidators {

    private static final DateTimeFormatter STRICT_HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TimeValidators() {
        // Prevent instantiation
    }

    /**
     * Checks whether the given string represents a valid time.
     * 
     * Accepts the following formats:
     * <ul>
     * <li>Strict "HH:mm:ss" format (e.g. "13:45:00")</li>
     * <li>Compact 6-digit format (e.g. "134500" → 13h 45m 00s)</li>
     * <li>Optional day prefix, like "24d134500" → 24 days + 13h 45m 00s from
     * now</li>
     * </ul>
     *
     * @param s the string to validate
     * @return {@code true} if the string can be parsed as a valid time format;
     *         {@code false} otherwise
     */
    public static boolean isValidTime(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }

        String trimmed = s.trim();

        // First, check strict "HH:mm:ss" format
        try {
            LocalTime.parse(trimmed, STRICT_HH_MM_SS);
            return true;
        } catch (Exception ignored) {
            // Continue to alternate parsing
        }

        // Now handle compact formats like "003359" or "24d003359"
        String cleaned = trimmed.toLowerCase();

        try {
            int days = 0;
            String timePart;

            if (cleaned.contains("d")) {
                String[] parts = cleaned.split("d", 2);
                days = Integer.parseInt(parts[0].replaceAll("\\D", ""));
                timePart = parts[1].replaceAll("\\D", "");
            } else {
                timePart = cleaned.replaceAll("\\D", "");
            }

            // Must have 6 digits for HHMMSS
            if (timePart.length() != 6) {
                return false;
            }

            int hours = Integer.parseInt(timePart.substring(0, 2));
            int minutes = Integer.parseInt(timePart.substring(2, 4));
            int seconds = Integer.parseInt(timePart.substring(4, 6));

            // Sanity check ranges
            if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
                return false;
            }

            // If we reached here, the format is valid
            // (you could also compute the absolute expiration time if desired)
            LocalDateTime.now()
                    .plusDays(days)
                    .plusHours(hours)
                    .plusMinutes(minutes)
                    .plusSeconds(seconds);

            return true;

        } catch (Exception e) {
            return false;
        }
    }
}
