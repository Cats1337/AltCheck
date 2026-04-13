package io.github.cats1337.altcheck.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Lightweight VPN detection heuristic engine.
 *
 * No external APIs required.
 * Pure local scoring model.
 *
 * Now includes:
 * - caching (performance)
 * - confidence weighting (noise reduction)
 * - clean extensible structure
 */
public class VPNAnalysis {

    private static final Set<String> HOSTING_KEYWORDS = Set.of(
            "amazon", "aws",
            "google", "gcp",
            "microsoft", "azure",
            "digitalocean",
            "hetzner",
            "ovh",
            "linode",
            "vultr",
            "contabo"
    );

    private static final String[] DC_PREFIXES = {
            "104.", "35.", "34.", "13.", "18."
    };

    // Cache results to avoid repeated DNS + parsing cost
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long TTL = 1000L * 60 * 30; // 30 minutes

    private static class CacheEntry {
        final double value;
        final long time;

        CacheEntry(double value) {
            this.value = value;
            this.time = System.currentTimeMillis();
        }
    }

    /**
     * Public entry point
     */
    public double vpnScore(String ip) {
        if (ip == null || ip.isBlank()) return 0.0;

        CacheEntry cached = cache.get(ip);
        if (cached != null && System.currentTimeMillis() - cached.time < TTL) {
            return cached.value;
        }

        double value = computeScore(ip);
        cache.put(ip, new CacheEntry(value));
        return value;
    }

    /**
     * Core scoring logic
     */
    private double computeScore(String ip) {

        double score = 0.0;
        double confidence = 0.0;

        try {
            InetAddress addr = InetAddress.getByName(ip);

            // 1. Local / private networks
            if (addr.isSiteLocalAddress() || addr.isLoopbackAddress()) {
                return 0.0;
            }

            // 2. Reverse DNS check
            String host = addr.getHostName().toLowerCase();

            for (String keyword : HOSTING_KEYWORDS) {
                if (host.contains(keyword)) {
                    score += 60.0;
                    confidence += 0.6;
                    break;
                }
            }

        } catch (UnknownHostException ignored) {
            // DNS failure slightly suspicious
            score += 10.0;
            confidence += 0.3;
        }

        // 3. Datacenter IP pattern check
        if (isDatacenterPattern(ip)) {
            score += 25.0;
            confidence += 0.7;
        }

        // 4. Final weighted normalization
        double finalScore = score + (score * confidence);

        return Math.max(0.0, Math.min(100.0, finalScore));
    }

    /**
     * Detects obvious datacenter-style IP behavior patterns.
     */
    private boolean isDatacenterPattern(String ip) {
        if (ip == null) return false;

        for (String prefix : DC_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }
}