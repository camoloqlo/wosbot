package cl.camodev.utiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/** Utility helpers for time calculations used across the project. */
public final class UtilTime {
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final String ASAP = "ASAP";
    private static final String NEVER = "Never";
    private static final String JUST_NOW = "Just now";

    private UtilTime() {}

    /**
     * Gets the next midnight in local time based on UTC reset.
     *
     * @return local date time of the next game reset at midnight UTC
     */
    public static LocalDateTime getGameReset() {
        ZonedDateTime nowUtc = ZonedDateTime.now(UTC);
        ZonedDateTime nextUtcMidnight = nowUtc.toLocalDate().plusDays(1).atStartOfDay(UTC);
        return nextUtcMidnight.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * Returns the next reset which occurs at either noon or midnight UTC, whichever comes first.
     *
     * @return local date time of the next reset
     */
    public static LocalDateTime getNextReset() {
        ZonedDateTime nowUtc = ZonedDateTime.now(UTC);
        ZonedDateTime nextMidnightUtc = nowUtc.toLocalDate().plusDays(1).atStartOfDay(UTC);
        ZonedDateTime nextNoonUtc = nowUtc.toLocalDate().atTime(LocalTime.NOON).atZone(UTC);
        if (nowUtc.isAfter(nextNoonUtc)) {
            nextNoonUtc = nextNoonUtc.plusDays(1);
        }
        ZonedDateTime nextResetUtc =
                nowUtc.until(nextMidnightUtc, ChronoUnit.SECONDS)
                                < nowUtc.until(nextNoonUtc, ChronoUnit.SECONDS)
                        ? nextMidnightUtc
                        : nextNoonUtc;
        return nextResetUtc.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * Formats the remaining time until the given date time in DD HH:MM:SS format.
     *
     * @param dateTime target date time
     * @return formatted countdown string or {@code ASAP} if the time has passed
     */
    public static String localDateTimeToDDHHMMSS(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        if (dateTime.isBefore(now)) {
            return ASAP;
        }
        Duration duration = Duration.between(now, dateTime);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        StringBuilder formattedString = new StringBuilder();
        if (days > 0) {
            formattedString.append(days).append(" days ");
        }
        formattedString.append(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        return formattedString.toString();
    }

    /**
     * Formats the last execution time in a human-readable "time ago" form.
     *
     * @param execution time of last execution
     * @return formatted string or {@code Never} if {@code execution} is {@code null}
     */
    public static String formatLastExecution(LocalDateTime execution) {
        if (execution == null) {
            return NEVER;
        }
        long minutesAgo = ChronoUnit.MINUTES.between(execution, LocalDateTime.now());
        return formatTimeAgo(minutesAgo);
    }

    private static String formatTimeAgo(long minutes) {
        if (minutes < 1) {
            return JUST_NOW;
        } else if (minutes < 60) {
            return minutes + "m ago";
        } else if (minutes < 1440) {
            long hours = minutes / 60;
            return hours + "h ago";
        } else {
            long days = minutes / 1440;
            return days + "d ago";
        }
    }
}
