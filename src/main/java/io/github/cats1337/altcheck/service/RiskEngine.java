package io.github.cats1337.altcheck.service;

import io.github.cats1337.altcheck.model.SessionEntry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Session analysis layer.
 *
 * This is where intelligence lives:
 * - overlap detection
 * - risk scoring
 * - behavior patterns
 * - weighted fraud detection model
 */
public class RiskEngine {

    private final AltCheck service;
    private final VPNAnalysis vpn = new VPNAnalysis();

    public RiskEngine(AltCheck service) {
        this.service = service;
    }

    // =========================================================
    // OVERLAP SCORING
    // =========================================================

    public Map<String, Double> getIpOverlapScore() throws SQLException {

        Map<String, Double> score = new HashMap<>();
        Map<String, Set<String>> shared = service.getSharedIps();

        for (var entry : shared.entrySet()) {

            int users = entry.getValue().size();

            if (users <= 1) continue;

            double s = Math.log(users + 1) * 25;

            if (users >= 3) s += 20;
            if (users >= 5) s += 35;

            score.put(entry.getKey(), clamp(s));
        }

        return score;
    }

    // =========================================================
    // PLAYER RISK SCORE (MAIN FUNCTION)
    // =========================================================

    public double getRiskScore(String uuid) throws SQLException {

        List<SessionEntry> sessions = service.getSessions(uuid);

        if (sessions.isEmpty()) return 0;

        double vpnScore = vpnScoreForPlayer(sessions);
        double sessionScore = sessionBehaviorScore(sessions);
        double overlapScore = overlapScoreForPlayer(sessions);

        // weighted model
        double raw =
                vpnScore * 0.40 +
                        sessionScore * 0.35 +
                        overlapScore * 0.25;

        return clamp(raw);
    }

    // =========================================================
    // VPN SCORE (PLAYER-BASED)
    // =========================================================

    private double vpnScoreForPlayer(List<SessionEntry> sessions) {

        double max = 0;

        for (SessionEntry s : sessions) {

            String ip = s.ipHash(); // NOTE: hashed IP, replace if you later store raw IP

            double score = vpn.vpnScore(ip);

            if (score > max) {
                max = score;
            }
        }

        return max;
    }

    // =========================================================
    // SESSION BEHAVIOR ANALYSIS
    // =========================================================

    private double sessionBehaviorScore(List<SessionEntry> sessions) {

        double score = 0;

        int shortSessions = 0;
        long now = System.currentTimeMillis() / 1000;

        Set<String> ips = new HashSet<>();

        for (SessionEntry s : sessions) {

            long duration = s.duration();

            // short sessions = suspicious
            if (duration < 60) shortSessions++;

            // decay old sessions
            long ageDays = (now - s.joinTime()) / 86400;
            double decay = Math.pow(0.97, ageDays);

            score += (duration < 60 ? 8 : 2) * decay;

            ips.add(s.ipHash());
        }

        if (shortSessions > 5) score += 25;

        // IP reuse ratio
        if (ips.size() < sessions.size() / 2) {
            score += 30;
        }

        return clamp(score);
    }

    // =========================================================
    // OVERLAP SCORE (PLAYER-BASED)
    // =========================================================

    private double overlapScoreForPlayer(List<SessionEntry> sessions) throws SQLException {

        Set<String> ips = new HashSet<>();

        for (SessionEntry s : sessions) {
            ips.add(s.ipHash());
        }

        Map<String, Double> global = getIpAltScores();

        double score = 0;

        for (String ip : ips) {
            score += global.getOrDefault(ip, 0.0) * 0.6;
        }

        return clamp(score);
    }

    // =========================================================
    // IP ALT SCORES (GLOBAL GRAPH WEIGHT)
    // =========================================================

    public Map<String, Double> getIpAltScores() throws SQLException {

        Map<String, Double> scores = new HashMap<>();

        String sql = """
        SELECT ip_hash,
               COUNT(DISTINCT uuid) AS users,
               COUNT(*) AS sessions
        FROM sessions
        GROUP BY ip_hash
    """;

        try (Statement st = service.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {

                String ip = rs.getString("ip_hash");
                int users = rs.getInt("users");
                int sessions = rs.getInt("sessions");

                double score = 0;

                score += Math.log(users + 1) * 25;
                score += Math.log(sessions + 1) * 10;

                if (users >= 3) score += 25;
                if (users >= 5) score += 40;
                if (sessions > users * 3) score += 15;

                scores.put(ip, clamp(score));
            }
        }

        return scores;
    }

    // =========================================================
    // UTIL
    // =========================================================

    private double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }
}