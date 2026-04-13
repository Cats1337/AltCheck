package io.github.cats1337.altcheck.event;

import io.github.cats1337.altcheck.service.AltCheck;
import io.github.cats1337.altcheck.util.IpUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import java.net.InetSocketAddress;

/**
 * PlayerJoinListener
 *
 * Handles:
 * - Player join event
 * - Player leave event
 *
 * Responsibilities:
 * - Extract player IP
 * - Hash IP
 * - Log player data
 * - Track sessions
 *
 * IMPORTANT:
 * No SQL should exist here.
 * All logic is delegated to AltCheckService.
 */
public class PlayerJoinListener {

    private final AltCheck service;

    public PlayerJoinListener(AltCheck service) {
        this.service = service;
    }

    public void register() {

        // =========================================================
        // PLAYER JOIN
        // =========================================================
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                String username = handler.getPlayer().getName().getString();
                String uuid = handler.getPlayer().getUuidAsString();

                String ip = "unknown";

                if (handler.getConnectionAddress() instanceof InetSocketAddress addr) {
                    if (addr.getAddress() != null) {
                        ip = addr.getAddress().getHostAddress();
                    }
                }

                String ipHash = IpUtil.hashIP(ip);

                service.logPlayer(uuid, username, ip, ipHash);

                service.closeStaleSessions(uuid);

                // IMPORTANT: only start if not already active
                if (!service.hasActiveSession(uuid)) {
                    service.startSession(uuid, username, ip, ipHash);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // =========================================================
        // PLAYER LEAVE
        // =========================================================
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            try {
                String uuid = handler.getPlayer().getUuidAsString();
                service.endSession(uuid);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}