package io.github.cats1337.altcheck.database;

import java.io.File;
import java.sql.*;

/**
 * DatabaseManager
 *
 * Responsible for:
 * - Creating the SQLite database file
 * - Establishing the connection
 * - Creating tables + indexes
 *
 * IMPORTANT:
 * This class should ONLY manage the database lifecycle.
 * It should NOT contain gameplay logic or queries used by commands.
 */
public class DatabaseManager {

    // Shared connection used across the mod
    private Connection connection;

    /**
     * Initialize the database.
     *
     * Called once during mod startup.
     */
    public void init() throws SQLException {

        // Ensure config directory exists
        File dir = new File("config/altcheck");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Database file path
        File dbFile = new File(dir, "players.db");

        // Create SQLite connection
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

        try (Statement stmt = connection.createStatement()) {

            // Enable foreign keys (good practice even if not heavily used yet)
            stmt.execute("PRAGMA foreign_keys = ON;");

            // Enable WAL mode (better performance for concurrent reads/writes)
            stmt.execute("PRAGMA journal_mode = WAL;");
        }

        // Create tables + indexes
        createTables();
    }

    /**
     * Creates all required tables and indexes.
     *
     * Safe to call multiple times due to IF NOT EXISTS.
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // =========================================================
            // PLAYER IP TABLE
            // =========================================================
            //
            // Stores all player ↔ IP relationships.
            //
            // uuid           = player UUID
            // username       = last known username
            // ip_hash        = hashed IP (used for matching alts)
            // ip_encrypted   = stored IP (can later be encrypted)
            // first_seen     = first time this IP was seen for player
            // last_seen      = most recent login
            // hidden         = soft-hide flag (used for /hide command)
            //
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_ips (
                    uuid TEXT,
                    username TEXT,
                    ip_hash TEXT,
                    ip_encrypted TEXT,
                    first_seen INTEGER,
                    last_seen INTEGER,
                    hidden INTEGER DEFAULT 0,
                    UNIQUE(uuid, ip_hash)
                )
            """);

            // Indexes for faster lookups

            // Fast lookup by UUID (history, sessions, etc.)
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_uuid ON player_ips(uuid)");

            // Fast lookup by username (commands use this heavily)
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_username ON player_ips(username)");

            // Critical index for alt detection
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ip_hash ON player_ips(ip_hash)");

            // =========================================================
            // SESSION TABLE
            // =========================================================
            //
            // Tracks login sessions for players.
            //
            // uuid         = player UUID
            // ip_hash      = IP used during session
            // ip_encrypted = Encrypted IP
            // join_time    = when player joined
            // leave_time   = when player left (NULL if still online)
            //
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL,
                    username TEXT NOT NULL,
                    ip_hash TEXT NOT NULL,
                    ip_encrypted TEXT NOT NULL,
                    join_time INTEGER NOT NULL,
                    leave_time INTEGER
                )
            """);

            // Index for fast session lookups per player
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_uuid ON sessions(uuid)");

            // Index for IP-based session queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_ip ON sessions(ip_hash)");

            // Index for IP-based encryption session queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_ip_encrypted ON sessions(ip_encrypted)");
        }
    }

    /**
     * Returns the active database connection.
     *
     * Used by AltCheckService.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Closes the database connection.
     *
     * Should be called on server shutdown.
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}