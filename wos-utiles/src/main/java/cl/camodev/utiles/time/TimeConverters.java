package cl.camodev.utiles.time;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for converting time strings into {@link Duration} objects.
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
 * 
 * <p>
 * <b>Ambiguity Handling:</b>
 * <ul>
 * <li>4-digit formats are ambiguous between HHmm and mmss</li>
 * <li>If first two digits > 23, interpreted as mm:ss</li>
 * <li>If first two digits ≤ 23, interpreted as HH:mm (hours:minutes)</li>
 * </ul>
 */
public final class TimeConverters {

    private static final DateTimeFormatter STRICT_HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Pattern for day prefix (e.g., "2d", "24d")
    private static final Pattern DAY_PREFIX_PATTERN = Pattern.compile("^(\\d+)d(.+)$", Pattern.CASE_INSENSITIVE);

    private TimeConverters() {
        // Prevent instantiation
    }

    /**
     * Converts a time string to a {@link Duration}.
     *
     * <p>
     * Accepts:
     * <ul>
     * <li>"HH:mm:ss" → strict format with colons</li>
     * <li>"HHmmss" → compact 6-digit format</li>
     * <li>"HH:mm" → hours and minutes with colon</li>
     * <li>"HHmm" → compact 4-digit hours:minutes</li>
     * <li>"mm:ss" → minutes and seconds with colon</li>
     * <li>"mmss" → compact 4-digit minutes:seconds</li>
     * <li>"ss" → seconds only</li>
     * <li>"Xd..." → any of above with day prefix (e.g., "2d13:45:30")</li>
     * </ul>
     *
     * @param s the time string to convert
     * @return a {@link Duration} representing the total time
     * @throws IllegalArgumentException if the string is invalid or cannot be parsed
     */
    public static Duration toDuration(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Time string is null or blank");
        }

        String trimmed = s.trim().toLowerCase().replaceAll("\\s+", "");

        try {
            // Extract day prefix if present
            TimeComponents components = extractDayPrefix(trimmed);
            int days = components.days;
            String timePart = components.timePart;

            // Try parsing in order of specificity
            Duration duration = null;

            // Try HH:mm:ss with colons
            duration = tryParseHHMMSSColon(timePart);
            if (duration != null)
                return addDays(duration, days);

            // Try HH:mm with colon
            duration = tryParseHHMMColon(timePart);
            if (duration != null)
                return addDays(duration, days);

            // Try mm:ss with colon
            duration = tryParseMMSSColon(timePart);
            if (duration != null)
                return addDays(duration, days);

            // Try compact 6-digit HHmmss
            duration = tryParseHHMMSS(timePart);
            if (duration != null)
                return addDays(duration, days);

            // Try compact 4-digit (ambiguous between HHmm and mmss)
            duration = tryParse4Digit(timePart);
            if (duration != null)
                return addDays(duration, days);

            // Try seconds only
            duration = tryParseSS(timePart);
            if (duration != null)
                return addDays(duration, days);

            throw new IllegalArgumentException("Time string does not match any supported format: " + s);

        } catch (IllegalArgumentException e) {
            throw e; // Re-throw our own exceptions
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse time string: " + s, e);
        }
    }

    public static LocalDateTime toLocalDateTime(String input) {
        Duration duration = toDuration(input);
        if (duration != null) {
            return LocalDateTime.now().plus(duration);
        }
        return null;
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
     * Adds days to a duration.
     */
    private static Duration addDays(Duration duration, int days) {
        if (days > 0) {
            return duration.plusDays(days);
        }
        return duration;
    }

    /**
     * Tries to parse HH:mm:ss format with colons (e.g., "13:45:30").
     */
    private static Duration tryParseHHMMSSColon(String timePart) {
        try {
            LocalTime t = LocalTime.parse(timePart, STRICT_HH_MM_SS);
            return Duration.ofHours(t.getHour())
                    .plusMinutes(t.getMinute())
                    .plusSeconds(t.getSecond());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tries to parse HH:mm format with colon (e.g., "13:45").
     */
    private static Duration tryParseHHMMColon(String timePart) {
        try {
            // Check if it matches HH:mm pattern
            if (!timePart.matches("\\d{1,2}:\\d{2}")) {
                return null;
            }

            String[] parts = timePart.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);

            if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
                return null;
            }

            return Duration.ofHours(hours).plusMinutes(minutes);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tries to parse mm:ss format with colon (e.g., "45:30").
     */
    private static Duration tryParseMMSSColon(String timePart) {
        try {
            // Check if it matches mm:ss pattern
            if (!timePart.matches("\\d{1,2}:\\d{2}")) {
                return null;
            }

            String[] parts = timePart.split(":");
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);

            if (minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
                return null;
            }

            return Duration.ofMinutes(minutes).plusSeconds(seconds);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tries to parse HHmmss compact 6-digit format (e.g., "134530").
     */
    private static Duration tryParseHHMMSS(String timePart) {
        try {
            if (timePart.length() != 6 || !timePart.matches("\\d{6}")) {
                return null;
            }

            int hours = Integer.parseInt(timePart.substring(0, 2));
            int minutes = Integer.parseInt(timePart.substring(2, 4));
            int seconds = Integer.parseInt(timePart.substring(4, 6));

            if (hours < 0 || hours > 23 ||
                    minutes < 0 || minutes > 59 ||
                    seconds < 0 || seconds > 59) {
                return null;
            }

            return Duration.ofHours(hours)
                    .plusMinutes(minutes)
                    .plusSeconds(seconds);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tries to parse 4-digit compact format.
     * 
     * <p>
     * This is ambiguous between HHmm and mmss.
     * <ul>
     * <li>If first two digits > 23, treat as mm:ss</li>
     * <li>If first two digits ≤ 23, treat as HH:mm</li>
     * </ul>
     */
    private static Duration tryParse4Digit(String timePart) {
        try {
            if (timePart.length() != 4 || !timePart.matches("\\d{4}")) {
                return null;
            }

            int firstPart = Integer.parseInt(timePart.substring(0, 2));
            int secondPart = Integer.parseInt(timePart.substring(2, 4));

            // Validate second part for both interpretations
            if (secondPart < 0 || secondPart > 59) {
                return null;
            }

            // If first part > 23, must be mm:ss
            if (firstPart > 23) {
                if (firstPart > 59) {
                    return null;
                }
                // Interpret as mm:ss
                return Duration.ofMinutes(firstPart).plusSeconds(secondPart);
            }

            // First part ≤ 23, interpret as HH:mm
            return Duration.ofHours(firstPart).plusMinutes(secondPart);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tries to parse seconds only (e.g., "30").
     */
    private static Duration tryParseSS(String timePart) {
        try {
            if (timePart.length() > 2 || !timePart.matches("\\d{1,2}")) {
                return null;
            }

            int seconds = Integer.parseInt(timePart);

            if (seconds < 0 || seconds > 59) {
                return null;
            }

            return Duration.ofSeconds(seconds);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Helper class to hold day prefix and time part.
     */
    private static class TimeComponents {
        final int days;
        final String timePart;

        TimeComponents(int days, String timePart) {
            this.days = days;
            this.timePart = timePart;
        }
    }
}