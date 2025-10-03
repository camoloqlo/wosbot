package cl.camodev.utiles.number;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods to validate integer strings using regular expressions.
 */
public final class NumberValidators {
    private NumberValidators() {}

    /**
     * Tests whether the provided input matches the supplied regular expression pattern.
     *
     * @param input   the string to test, may be null
     * @param pattern the compiled regular expression pattern, must not be null
     * @return {@code true} if the input is non-null and matches the pattern; {@code false} otherwise
     */
    public static boolean matchesPattern(String input, Pattern pattern) {
        Objects.requireNonNull(pattern, "pattern");
        if (input == null) {
            return false;
        }
        Matcher matcher = pattern.matcher(input.trim());
        return matcher.matches();
    }
}