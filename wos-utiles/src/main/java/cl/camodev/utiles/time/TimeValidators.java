package cl.camodev.utiles.time;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods to validate and parse time strings in various formats.
 * 
 * <p>
 * Supported formats (with or without day prefix):
 * <ul>
 * <li>HH:mm:ss (e.g., "13:45:30")</li>
 * <li>HHmmss (e.g., "134530")</li>
 * <li>HH:mm (e.g., "13:45")</li>
 * <li>HHmm (e.g., "1345")</li>
 * <li>mm:ss (e.g., "45:30")</li>
 * <li>mmss (e.g., "4530")</li>
 * <li>ss (e.g., "30")</li>
 * <li>With day prefix: "2d13:45:30", "2d134530", etc.</li>
 * </ul>
 */
public final class TimeValidators {

    private static final DateTimeFormatter STRICT_HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Regex patterns for different formats
    private static final Pattern PATTERN_HH_MM_COLON = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern PATTERN_MM_SS_COLON = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern PATTERN_HHMMSS = Pattern.compile("(\\d{6})");
    private static final Pattern PATTERN_HHMM = Pattern.compile("(\\d{4})");
    private static final Pattern PATTERN_MMSS = Pattern.compile("(\\d{4})");
    private static final Pattern PATTERN_SS = Pattern.compile("(\\d{1,2})");

    // Pattern for day prefix (e.g., "2d", "24d")
    private static final Pattern DAY_PREFIX_PATTERN = Pattern.compile("^(\\d+)d(.+)$", Pattern.CASE_INSENSITIVE);

    private TimeValidators() {
        // Prevent instantiation
    }

    /**
     * Checks whether the given string represents a valid time in any supported
     * format.
     * 
     * <p>
     * Accepts the following formats:
     * <ul>
     * <li>HH:mm:ss format (e.g., "13:45:30")</li>
     * <li>HHmmss compact format (e.g., "134530")</li>
     * <li>HH:mm format (e.g., "13:45")</li>
     * <li>HHmm compact format (e.g., "1345")</li>
     * <li>mm:ss format (e.g., "45:30")</li>
     * <li>mmss compact format (e.g., "4530")</li>
     * <li>ss format (e.g., "30")</li>
     * <li>Any of the above with day prefix (e.g., "2d13:45:30")</li>
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

        try {
            // Extract day prefix if present
            TimeComponents components = extractDayPrefix(trimmed);
            String timePart = components.timePart;

            // Try all supported formats
            if (isValidHHMMSSColon(timePart))
                return true;
            if (isValidHHMMColon(timePart))
                return true;
            if (isValidMMSSColon(timePart))
                return true;
            if (isValidHHMMSS(timePart))
                return true;
            if (isValidHHMM(timePart))
                return true;
            if (isValidMMSS(timePart))
                return true;
            if (isValidSS(timePart))
                return true;

            return false;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts day prefix from time string if present.
     * 
     * @param input the time string
     * @return TimeComponents containing days and remaining time part
     */
    private static TimeComponents extractDayPrefix(String input) {
        Matcher dayMatcher = DAY_PREFIX_PATTERN.matcher(input);

        if (dayMatcher.matches()) {
            int days = Integer.parseInt(dayMatcher.group(1));
            String timePart = dayMatcher.group(2).trim();
            return new TimeComponents(days, timePart);
        }

        return new TimeComponents(0, input);
    }

    /**
     * Validates HH:mm:ss format (e.g., "13:45:30").
     */
    private static boolean isValidHHMMSSColon(String timePart) {
        try {
            LocalTime.parse(timePart, STRICT_HH_MM_SS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates HH:mm format (e.g., "13:45").
     */
    private static boolean isValidHHMMColon(String timePart) {
        Matcher matcher = PATTERN_HH_MM_COLON.matcher(timePart);
        if (!matcher.matches())
            return false;

        int hours = Integer.parseInt(matcher.group(1));
        int minutes = Integer.parseInt(matcher.group(2));

        return hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59;
    }

    /**
     * Validates mm:ss format (e.g., "45:30").
     */
    private static boolean isValidMMSSColon(String timePart) {
        Matcher matcher = PATTERN_MM_SS_COLON.matcher(timePart);
        if (!matcher.matches())
            return false;

        int minutes = Integer.parseInt(matcher.group(1));
        int seconds = Integer.parseInt(matcher.group(2));

        return minutes >= 0 && minutes <= 59 && seconds >= 0 && seconds <= 59;
    }

    /**
     * Validates HHmmss compact format (e.g., "134530").
     */
    private static boolean isValidHHMMSS(String timePart) {
        if (timePart.length() != 6)
            return false;

        Matcher matcher = PATTERN_HHMMSS.matcher(timePart);
        if (!matcher.matches())
            return false;

        int hours = Integer.parseInt(timePart.substring(0, 2));
        int minutes = Integer.parseInt(timePart.substring(2, 4));
        int seconds = Integer.parseInt(timePart.substring(4, 6));

        return hours >= 0 && hours <= 23 &&
                minutes >= 0 && minutes <= 59 &&
                seconds >= 0 && seconds <= 59;
    }

    /**
     * Validates HHmm compact format (e.g., "1345").
     */
    private static boolean isValidHHMM(String timePart) {
        if (timePart.length() != 4)
            return false;

        Matcher matcher = PATTERN_HHMM.matcher(timePart);
        if (!matcher.matches())
            return false;

        int hours = Integer.parseInt(timePart.substring(0, 2));
        int minutes = Integer.parseInt(timePart.substring(2, 4));

        return hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59;
    }

    /**
     * Validates mmss compact format (e.g., "4530").
     * This is ambiguous with HHmm, so we check ranges to differentiate.
     */
    private static boolean isValidMMSS(String timePart) {
        if (timePart.length() != 4)
            return false;

        Matcher matcher = PATTERN_MMSS.matcher(timePart);
        if (!matcher.matches())
            return false;

        int firstPart = Integer.parseInt(timePart.substring(0, 2));
        int secondPart = Integer.parseInt(timePart.substring(2, 4));

        // If first part > 23, treat as mm:ss (minutes can be > 23)
        // If both parts are valid for HH:mm OR mm:ss, prefer HH:mm interpretation
        // To disambiguate: if firstPart > 23, it MUST be minutes
        if (firstPart > 23) {
            // Must be mm:ss
            return firstPart >= 0 && firstPart <= 59 && secondPart >= 0 && secondPart <= 59;
        }

        // Could be either HH:mm or mm:ss - both are valid
        // For validation purposes, we accept it
        return secondPart >= 0 && secondPart <= 59;
    }

    /**
     * Validates ss format (e.g., "30").
     */
    private static boolean isValidSS(String timePart) {
        if (timePart.length() > 2)
            return false;

        Matcher matcher = PATTERN_SS.matcher(timePart);
        if (!matcher.matches())
            return false;

        int seconds = Integer.parseInt(timePart);
        return seconds >= 0 && seconds <= 59;
    }

    /**
     * Helper class to hold day prefix and time part.
     */
    private static class TimeComponents {
        final String timePart;

        TimeComponents(int days, String timePart) {
            this.timePart = timePart;
        }
    }
}