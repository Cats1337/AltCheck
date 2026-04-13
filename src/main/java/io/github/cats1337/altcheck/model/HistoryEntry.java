package io.github.cats1337.altcheck.model;

/**
 * Represents a single IP history record for a player.
 *
 * This is a simple data container (DTO).
 * No logic should live here.
 */
public record HistoryEntry(
        String ip,
        String maskedIp,
        long firstSeen,
        long lastSeen,
        int sessionCount
) {}