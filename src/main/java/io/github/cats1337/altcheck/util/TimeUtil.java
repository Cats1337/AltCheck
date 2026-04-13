package io.github.cats1337.altcheck.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility for time formatting.
 *
 * Used for:
 * - sessions
 * - history logs
 * - debugging timestamps
 */
public class TimeUtil {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    /**
     * Converts epoch seconds into readable time.
     */
    public static String format(long epochSeconds) {
        if (epochSeconds <= 0) {
            return "never";
        }

        return FORMATTER.format(Instant.ofEpochSecond(epochSeconds));
    }

    /**
     * Returns current time in epoch seconds.
     */
    public static long now() {
        return Instant.now().getEpochSecond();
    }

    /**
     * Formats a session range.
     */
    public static String formatSession(long join, long leave) {
        return "Join: " + format(join) + " | Leave: " +
                (leave > 0 ? format(leave) : "online");
    }
}