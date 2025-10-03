package cl.camodev.utiles.time;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility methods to validate time strings.
 */
public final class TimeValidators {

    private static final DateTimeFormatter STRICT_HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TimeValidators() {
        // Prevent instantiation
    }

    /**
     * Checks whether the given string matches the strict "HH:mm:ss" format.
     * Hours must be two digits (00–23) and minutes/seconds must be two digits.
     *
     * @param s the string to validate
     * @return {@code true} if the string is non-null and can be parsed as a time in
     *         the "HH:mm:ss" format; {@code false} otherwise
     */
    public static boolean isHHmmss(String s) {
        if (s == null) {
            return false;
        }
        String trimmed = s.trim();
        try {
            LocalTime.parse(trimmed, STRICT_HH_MM_SS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}