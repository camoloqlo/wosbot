package cl.camodev.utiles;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UtilTime {

	public static LocalDateTime getGameReset() {
		ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
		ZonedDateTime nextUtcMidnight = nowUtc.toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("UTC"));
		ZonedDateTime localNextMidnight = nextUtcMidnight.withZoneSameInstant(ZoneId.systemDefault());
		return localNextMidnight.toLocalDateTime();
	}

	public static LocalDateTime getNextReset() {
		ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));

		ZonedDateTime nextMidnightUtc = nowUtc.toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("UTC"));
		ZonedDateTime nextNoonUtc = nowUtc.toLocalDate().atTime(12, 0).atZone(ZoneId.of("UTC"));

		if (nowUtc.isAfter(nextNoonUtc)) {
			nextNoonUtc = nextNoonUtc.plusDays(1);
		}

		ZonedDateTime nextResetUtc = nowUtc.until(nextMidnightUtc, ChronoUnit.SECONDS) < nowUtc.until(nextNoonUtc, ChronoUnit.SECONDS) ? nextMidnightUtc : nextNoonUtc;
		ZonedDateTime localNextReset = nextResetUtc.withZoneSameInstant(ZoneId.systemDefault());
		return localNextReset.toLocalDateTime();
	}

	public static String localDateTimeToDDHHMMSS(LocalDateTime dateTime) {
		LocalDateTime now = LocalDateTime.now();

		if (dateTime.isBefore(now)) {
			return "ASAP";
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

	public static String formatLastExecution(LocalDateTime execution) {
		if (execution == null) {
			return "Never";
		}
		long minutesAgo = ChronoUnit.MINUTES.between(execution, LocalDateTime.now());
		return formatTimeAgo(minutesAgo);
	}


	private static String formatTimeAgo(long minutes) {
		if (minutes < 1) {
			return "Just now";
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

    /**
     * Ensures a scheduled time doesn't go beyond the game reset time.
     * If the proposed schedule time is after game reset, returns a time 5 minutes before game reset.
     * 
     * @param proposedSchedule The proposed schedule time
     * @return The adjusted schedule time (either the original time or 5 minutes before reset)
     */
    public static LocalDateTime ensureBeforeGameReset(LocalDateTime proposedSchedule) {
        LocalDateTime gameReset = getGameReset();
        LocalDateTime fiveMinutesBeforeReset = gameReset.minusMinutes(5);
        
        if (proposedSchedule.isAfter(fiveMinutesBeforeReset)) {
            return fiveMinutesBeforeReset;
        }
        
        return proposedSchedule;
    }

    /**
     * Returns the next Monday at 00:00 UTC in the system's local timezone.
     * If it's already Monday before midnight UTC, returns next week's Monday.
     *
     * @return LocalDateTime representing the next Monday at 00:00 UTC
     */
    public static LocalDateTime getNextMondayUtc() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime nextMondayUtc = nowUtc.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .truncatedTo(ChronoUnit.DAYS);
        return nextMondayUtc.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * Parses a time string in the format "HH:mm:ss" or "H:mm:ss" and converts it to total seconds.
     * If the input is invalid or null, returns -1.
     *
     * @param timeStr The time string to parse
     * @return The total time in seconds
     */
    public static long parseTimeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return -1;
        }
        Pattern pattern = Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})");
        Matcher matcher = pattern.matcher(timeStr.trim());
        if (matcher.find()) {
            try {
                int hours = Integer.parseInt(matcher.group(1));
                int minutes = Integer.parseInt(matcher.group(2));
                int seconds = Integer.parseInt(matcher.group(3));
                return (long) hours * 3600 + (long) minutes * 60 + seconds;
            } catch (NumberFormatException e) {
                System.out.println("Unable to parse time: " + timeStr);
            }
        }

        return -1;
    }
}
