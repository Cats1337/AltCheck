package io.github.cats1337.altcheck.service;

import io.github.cats1337.altcheck.model.HistoryEntry;
import io.github.cats1337.altcheck.model.SessionEntry;
import io.github.cats1337.altcheck.util.Crypto;
import io.github.cats1337.altcheck.util.IpUtil;
import io.github.cats1337.altcheck.util.TimeUtil;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Core service layer for AltCheck.
 *
 * This class is responsible for ALL logic:
 * - Database reads/writes
 * - Alt detection
 * - Session tracking
 * - Admin actions
 *
 * IMPORTANT:
 * Commands should NEVER directly access the database.
 * They should ONLY call methods from this class.
 */
public class AltCheck {

    // Shared database connection (provided by DatabaseManager)
    private final Connection connection;
    private final VPNAnalysis vpn = new VPNAnalysis();

    public AltCheck(Connection connection) {
        this.connection = connection;
    }

    public Connection getConnection() {
        return connection;
    }

    // =========================================================
    // IP LOGGING
    // =========================================================

    public String getUuidByUsername(String username) throws SQLException {
        String sql = """
        SELECT uuid
        FROM player_ips
        WHERE username = ?
        ORDER BY last_seen DESC
        LIMIT 1
    """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("uuid");
            }
        }

        return null;
    }

    public String getIpFromHash(String ipHash) throws SQLException {
        String sql = """
        SELECT ip_encrypted
        FROM player_ips
        WHERE ip_hash = ?
        LIMIT 1
    """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ipHash);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Crypto.decrypt(rs.getString("ip_encrypted"));
            }
        }

        return "unknown";
    }

    /**
     * Logs or updates a player's IP record.
     *
     * If the (uuid + ip_hash) already exists:
     *   → updates last_seen + username
     * Else:
     *   → inserts a new row
     */
    public void logPlayer(String uuid, String username, String ip, String ipHash) throws SQLException {
        long now = Instant.now().getEpochSecond();

        // Check if this player already used this IP before
        String select = "SELECT 1 FROM player_ips WHERE uuid = ? AND ip_hash = ?";

        try (PreparedStatement ps = connection.prepareStatement(select)) {
            ps.setString(1, uuid);
            ps.setString(2, ipHash);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Existing record → update last_seen + username (in case name changed)
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE player_ips SET last_seen = ?, username = ? WHERE uuid = ? AND ip_hash = ?")) {

                    update.setLong(1, now);
                    update.setString(2, username);
                    update.setString(3, uuid);
                    update.setString(4, ipHash);
                    update.executeUpdate();
                }
            } else {
                // New record → insert fresh entry
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO player_ips (uuid, username, ip_hash, ip_encrypted, first_seen, last_seen) VALUES (?, ?, ?, ?, ?, ?)")) {

                    insert.setString(1, uuid);
                    insert.setString(2, username);
                    insert.setString(3, ipHash);

                    insert.setString(4, Crypto.encrypt(ip));

                    insert.setLong(5, now);
                    insert.setLong(6, now);
                    insert.executeUpdate();
                }
            }
        }
    }

    // =========================================================
    // LOOKUPS
    // =========================================================

    /**
     * Get all IPs used by a player.
     */
    public Set<String> getIps(String player) throws SQLException {
        Set<String> ips = new HashSet<>();

        String sql = "SELECT ip_encrypted FROM player_ips WHERE username = ? AND hidden = 0";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ips.add(Crypto.decrypt(rs.getString("ip_encrypted")));
            }
        }

        return ips;
    }

    /**
     * Get all players that have used a specific IP hash.
     *
     * NOTE:
     * Caller must hash the IP before using this.
     */
    public Set<String> getPlayersByIpHash(String ipHash) throws SQLException {
        Set<String> players = new HashSet<>();

        String sql = "SELECT username FROM player_ips WHERE ip_hash = ? AND hidden = 0";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ipHash);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                players.add(rs.getString("username"));
            }
        }

        return players;
    }

    // =========================================================
    // ALT DETECTION
    // =========================================================

    /**
     * Find all alternate accounts for a given player.
     *
     * Works by:
     * - Finding all IPs used by player
     * - Finding other users with same IPs
     */
    public Set<String> getAlts(String player) throws SQLException {
        Set<String> alts = new HashSet<>();

        String sql = """
            SELECT DISTINCT p2.username
            FROM player_ips p1
            JOIN player_ips p2 ON p1.ip_hash = p2.ip_hash
            WHERE p1.username = ?
              AND p2.username != ?
              AND p1.hidden = 0
              AND p2.hidden = 0
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);
            ps.setString(2, player);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                alts.add(rs.getString("username"));
            }
        }

        return alts;
    }

    /**
     * Returns all IP hashes mapped to players.
     *
     * Used for:
     * - /same command
     */
    public Map<String, Set<String>> getSharedIps() throws SQLException {

        Map<String, Set<String>> map = new HashMap<>();

        String sql = """
        SELECT ip_encrypted, username
        FROM player_ips
        WHERE hidden = 0
    """;

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {

                String ip = Crypto.decrypt(rs.getString("ip_encrypted"));
                String user = rs.getString("username");

                map.computeIfAbsent(ip, k -> new HashSet<>()).add(user);
            }
        }

        return map;
    }

    // =========================================================
    // HISTORY
    // =========================================================

    /**
     * Get full IP history for a player.
     */
    public List<HistoryEntry> getHistory(String player) throws SQLException {

        List<HistoryEntry> history = new ArrayList<>();
        Map<String, Integer> sessionCounts = getSessionCountByPlayer();

        String sql = """
        SELECT ip_encrypted, first_seen, last_seen
        FROM player_ips
        WHERE username = ? AND hidden = 0
        ORDER BY last_seen DESC
    """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {

                String decryptedIp = Crypto.decrypt(rs.getString("ip_encrypted"));

                long first = rs.getLong("first_seen");
                long last = rs.getLong("last_seen");

                int sessions = sessionCounts.getOrDefault(player, 0);

                history.add(new HistoryEntry(
                        decryptedIp,
                        IpUtil.maskIP(decryptedIp),
                        first,
                        last,
                        sessions
                ));
            }
        }

        return history;
    }

    // =========================================================
    // SESSIONS
    // =========================================================

    /**
     * Start a session when a player joins.
     *
     * Prevents duplicate open sessions.
     */
    public void startSession(String uuid, String username, String ip, String ipHash) throws SQLException {
        long now = TimeUtil.now();

        // close any active session first
        try (PreparedStatement ps = connection.prepareStatement("""
        UPDATE sessions
        SET leave_time = ?
        WHERE uuid = ? AND leave_time IS NULL
    """)) {
            ps.setLong(1, now);
            ps.setString(2, uuid);
            ps.executeUpdate();
        }

        // insert new session
        try (PreparedStatement ps = connection.prepareStatement("""
        INSERT INTO sessions (uuid, username, ip_hash, ip_encrypted, join_time)
        VALUES (?, ?, ?, ?, ?)
    """)) {
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.setString(3, ipHash);
            ps.setString(4, Crypto.encrypt(ip));
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    /**
     * End the current active session.
     */
    public void endSession(String uuid) throws SQLException {
        long now = TimeUtil.now();

        try (PreparedStatement ps = connection.prepareStatement("""
        UPDATE sessions
        SET leave_time = ?
        WHERE uuid = ? AND leave_time IS NULL
    """)) {
            ps.setLong(1, now);
            ps.setString(2, uuid);
            ps.executeUpdate();
        }
    }

    /**
     * Get formatted session history for a player.
     */
    public List<SessionEntry> getSessions(String uuid) throws SQLException {

        List<SessionEntry> sessions = new ArrayList<>();

        String sql = """
        SELECT uuid, username, ip_hash, ip_encrypted, join_time, leave_time
        FROM sessions
        WHERE uuid = ?
        ORDER BY join_time DESC
    """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {

                sessions.add(new SessionEntry(
                        rs.getString("uuid"),
                        rs.getString("username"),
                        rs.getString("ip_hash"),
                        rs.getString("ip_encrypted"),
                        rs.getLong("join_time"),
                        rs.getLong("leave_time")
                ));
            }
        }

        return sessions;
    }

    public boolean hasActiveSession(String uuid) throws SQLException {
        String sql = """
        SELECT 1 FROM sessions
        WHERE uuid = ? AND leave_time IS NULL
        LIMIT 1
    """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);

            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    public void closeStaleSessions(String uuid) throws SQLException {
        long now = TimeUtil.now();

        try (PreparedStatement ps = connection.prepareStatement("""
        UPDATE sessions
        SET leave_time = ?
        WHERE uuid = ? AND leave_time IS NULL
    """)) {
            ps.setLong(1, now);
            ps.setString(2, uuid);
            ps.executeUpdate();
        }
    }

    public Map<String, Integer> getSessionCountByPlayer() throws SQLException {

        Map<String, Integer> map = new HashMap<>();

        String sql = """
        SELECT uuid, COUNT(*) AS total
        FROM sessions
        GROUP BY uuid
    """;

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                map.put(rs.getString("uuid"), rs.getInt("total"));
            }
        }

        return map;
    }

    public Map<String, Integer> getSessionCountByIp() throws SQLException {

        Map<String, Integer> map = new HashMap<>();

        String sql = """
        SELECT ip_hash, COUNT(*) AS total
        FROM sessions
        GROUP BY ip_hash
    """;

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                map.put(rs.getString("ip_hash"), rs.getInt("total"));
            }
        }

        return map;
    }

    public long getAverageSessionLength(String uuid) throws SQLException {

        String sql = """
        SELECT join_time, leave_time
        FROM sessions
        WHERE uuid = ?
    """;

        long total = 0;
        int count = 0;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {

                long join = rs.getLong("join_time");
                long leave = rs.getLong("leave_time");

                if (leave > 0) {
                    total += (leave - join);
                    count++;
                }
            }
        }

        return count == 0 ? 0 : total / count;
    }

    public List<SessionEntry> getActiveSessions() throws SQLException {

        List<SessionEntry> active = new ArrayList<>();

        String sql = """
        SELECT uuid, username, ip_hash, ip_encrypted, join_time
        FROM sessions
        WHERE leave_time IS NULL
    """;

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {

                active.add(new SessionEntry(
                        rs.getString("uuid"),
                        rs.getString("username"),
                        rs.getString("ip_hash"),
                        rs.getString("ip_encrypted"),
                        rs.getLong("join_time"),
                        0
                ));
            }
        }

        return active;
    }

    // =========================================================
    // ADMIN
    // =========================================================

    /**
     * Delete all records for a player.
     */
    public void purgePlayer(String player) throws SQLException {
        String sql = "DELETE FROM player_ips WHERE username = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);
            ps.executeUpdate();
        }
    }

    /**
     * Hide a player from results (soft delete).
     */
    public boolean toggleHidden(String player) throws SQLException {

        // check current state
        String select = "SELECT hidden FROM player_ips WHERE username = ? LIMIT 1";

        boolean newState = true; // default to hidden if not found

        try (PreparedStatement ps = connection.prepareStatement(select)) {
            ps.setString(1, player);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                newState = rs.getInt("hidden") == 0;
            }
        }

        // apply toggle
        String update = "UPDATE player_ips SET hidden = ? WHERE username = ?";

        try (PreparedStatement ps = connection.prepareStatement(update)) {
            ps.setInt(1, newState ? 1 : 0);
            ps.setString(2, player);
            ps.executeUpdate();
        }

        return newState;
    }

    /**
     * Remove old records based on timestamp.
     */
    public void cleanupOld(long cutoffEpoch) throws SQLException {
        String sql = "DELETE FROM player_ips WHERE last_seen < ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, cutoffEpoch);
            ps.executeUpdate();
        }
    }

    /**
     * Get database statistics.
     */
    public Map<String, Integer> getStats() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();

        try (Statement st = connection.createStatement()) {

            // Unique players
            ResultSet rs1 = st.executeQuery("SELECT COUNT(DISTINCT uuid) AS total FROM player_ips");
            stats.put("players", rs1.next() ? rs1.getInt("total") : 0);

            // Total records
            ResultSet rs2 = st.executeQuery("SELECT COUNT(*) AS total FROM player_ips");
            stats.put("records", rs2.next() ? rs2.getInt("total") : 0);

            // Unique IPs
            ResultSet rs3 = st.executeQuery("SELECT COUNT(DISTINCT ip_hash) AS total FROM player_ips");
            stats.put("ips", rs3.next() ? rs3.getInt("total") : 0);
        }

        return stats;
    }

    /**
     *  Get all known players in the database.
     */
    public Set<String> getAllKnownPlayers() {
        Set<String> players = new HashSet<>();

        String sql = "SELECT DISTINCT username FROM player_ips";

        try (var stmt = connection.prepareStatement(sql);
             var rs = stmt.executeQuery()) {

            while (rs.next()) {
                players.add(rs.getString("username"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return players;
    }

    /**
     * Get the latest IP used by a player.
     */
    public String getLatestIp(String uuid) throws SQLException {
        String sql = """
        SELECT ip_encrypted
        FROM player_ips
        WHERE uuid = ?
        ORDER BY last_seen DESC
        LIMIT 1
    """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Crypto.decrypt(rs.getString("ip_encrypted"));
            }
        }

        return null;
    }

    // =========================================================
    // VPN
    // =========================================================

    public double getVpnScore(String ip) {
        return vpn.vpnScore(ip);
    }

    public double getPlayerRiskScore(String uuid) throws SQLException {
        double score = 0;

        // VPN risk (latest IP)
        String ipSql = """
        SELECT ip_encrypted
        FROM player_ips
        WHERE uuid = ?
        ORDER BY last_seen DESC
        LIMIT 1
    """;

        try (PreparedStatement ps = connection.prepareStatement(ipSql)) {
            ps.setString(1, uuid);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String ip = Crypto.decrypt(rs.getString("ip_encrypted"));
                score += vpn.vpnScore(ip) * 0.4;
            }
        }

        // session risk
        List<SessionEntry> sessions = getSessions(uuid);

        long shortSessions = sessions.stream()
                .filter(s -> s.duration() < 60)
                .count();

        score += Math.min(30, shortSessions * 2);

        // multi-IP behavior
        Set<String> ips = new HashSet<>();
        for (SessionEntry s : sessions) {
            ips.add(s.ipHash());
        }

        if (ips.size() < Math.max(1, sessions.size() / 2)) {
            score += 25;
        }

        return Math.min(score, 100);
    }
}