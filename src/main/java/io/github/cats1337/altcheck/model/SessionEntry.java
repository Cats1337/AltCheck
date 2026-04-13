package io.github.cats1337.altcheck.model;

import io.github.cats1337.altcheck.util.TimeUtil;

/**
 * A single player session.
 *
 * Core unit for:
 * - alt detection
 * - overlap analysis
 * - behavior scoring
 */
public record SessionEntry(
        String uuid,
        String username,
        String ipHash,
        String ipEncrypted,
        long joinTime,
        long leaveTime
) {

    /**
     * Checks if session is still active.
     */
    public boolean isActive() {
        return leaveTime <= 0;
    }

    /**
     * Duration in seconds.
     */
    public long duration() {
        long end = isActive() ? TimeUtil.now() : leaveTime;
        return Math.max(0, end - joinTime);
    }

    /**
     * True if session is shorter than threshold.
     *
     * Example:
     * 60 = 1 minute sessions
     */
    public boolean isShort(long thresholdSeconds) {
        return duration() < thresholdSeconds;
    }
}