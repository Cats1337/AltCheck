package io.github.cats1337.altcheck.util;

import java.security.MessageDigest;

/**
 * Utility class for IP handling.
 *
 * Responsibilities:
 * - Hash IPs for matching
 * - Mask IPs for safe display (optional use later)
 */
public class IpUtil {

    /**
     * Hashes an IP using SHA-256.
     *
     * Used for:
     * - alt detection
     * - database matching
     *
     * NOTE:
     * Never store raw IPs for comparisons.
     */
    public static String hashIP(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            byte[] hash = md.digest(ip.getBytes());
            StringBuilder hex = new StringBuilder();

            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to hash IP", e);
        }
    }

    /**
     * Masks an IP for safe display.
     *
     * ie:
     * - 127.*.*.*
     * - 192.*.*.*
     */
    public static String maskIP(String ip) {
        if (ip == null || !ip.contains(".")) return "unknown";

        String[] parts = ip.split("\\.");
        if (parts.length != 4) return "unknown";

        return parts[0] + ".*.*.*";
    }
}