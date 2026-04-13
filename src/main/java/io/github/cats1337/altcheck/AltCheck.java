package io.github.cats1337.altcheck;

import io.github.cats1337.altcheck.commands.AltCheckRootCommand;
import io.github.cats1337.altcheck.database.DatabaseManager;
import io.github.cats1337.altcheck.event.PlayerJoinListener;
import net.fabricmc.api.ModInitializer;

/**
 * Main entry point for AltCheck mod.
 *
 * Responsibilities:
 * - Initialize database
 * - Create service layer
 * - Register commands
 * - Register events
 *
 * IMPORTANT:
 * Keep this file minimal.
 * No business logic should live here.
 */
public class AltCheck implements ModInitializer {

    // Core systems
    private DatabaseManager databaseManager;
    private io.github.cats1337.altcheck.service.AltCheck service;

    @Override
    public void onInitialize() {

        try {
            // =========================================================
            // DATABASE INITIALIZATION
            // =========================================================
            databaseManager = new DatabaseManager();
            databaseManager.init();

            // =========================================================
            // SERVICE LAYER INITIALIZATION
            // =========================================================
            // All gameplay / logic operations go through this class
            service = new io.github.cats1337.altcheck.service.AltCheck(databaseManager.getConnection());

            // =========================================================
            // EVENT REGISTRATION
            // =========================================================
            new PlayerJoinListener(service).register();

            // =========================================================
            // COMMAND REGISTRATION
            // =========================================================
            new AltCheckRootCommand(service).register();

        } catch (Exception e) {

            // Never crash server during mod init
            System.err.println("[AltCheck] Failed to initialize mod:");
            e.printStackTrace();
        }
    }
}