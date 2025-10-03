package cl.camodev.utiles.number;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for converting substrings matched by a regular expression into {@link Integer} values.
 */
public final class NumberConverters {

    private NumberConverters() {}

    /**
     * Extracts an integer from the provided input using the supplied regular expression pattern.
     * The pattern is expected to contain at least one capturing group that corresponds to the integer value.
     * If the input does not match the pattern or if the captured group cannot be parsed into an integer,
     * this method returns {@code null}.
     *
     * @param input   the string to extract an integer from, may be null
     * @param pattern the compiled regular expression pattern with at least one capturing group, must not be null
     * @return an {@link Integer} containing the parsed value, or {@code null} if parsing is not possible
     */
    public static Integer regexToInt(String input, Pattern pattern) {
        Objects.requireNonNull(pattern, "pattern");
        if (input == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(input.trim());
        if (!matcher.matches()) {
            return null;
        }
        // Use first capturing group if available; otherwise use the entire match
        String numberStr = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
        try {
            return Integer.parseInt(numberStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}