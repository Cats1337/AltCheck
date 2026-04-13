package io.github.cats1337.altcheck.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.github.cats1337.altcheck.service.AltCheck;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.time.Instant;
import java.util.Map;

/**
 * Admin commands:
 * /altcheck purge <player>
 * /altcheck hide <player>
 * /altcheck cleanup
 * /altcheck stats
 *
 * Only contains execution logic, no command registration.
 */
public class AltCheckAdminCommands {

    private final AltCheck service;

    public AltCheckAdminCommands(AltCheck service) {
        this.service = service;
    }

    /**
     * Deletes all records of a player
     */
    public int purge(CommandContext<ServerCommandSource> ctx) {
        String player = StringArgumentType.getString(ctx, "player");

        try {
            service.purgePlayer(player);

            ctx.getSource().sendFeedback(
                    () -> Text.literal("§aPurged data for §f" + player),
                    false
            );

        } catch (Exception e) {
            ctx.getSource().sendError(
                    Text.literal("§cPurge failed: " + e.getMessage())
            );
        }

        return 1;
    }

    /**
     * Prevents player from being logged (or marks as hidden)
     */
    public int hide(CommandContext<ServerCommandSource> ctx) {
        String player = StringArgumentType.getString(ctx, "player");

        try {
            boolean hidden = service.toggleHidden(player);

            if (hidden) {
                ctx.getSource().sendFeedback(
                        () -> Text.literal("§eNow hidden: §f" + player),
                        false
                );
            } else {
                ctx.getSource().sendFeedback(
                        () -> Text.literal("§aNow visible: §f" + player),
                        false
                );
            }

        } catch (Exception e) {
            ctx.getSource().sendError(
                    Text.literal("§cToggle hide failed: " + e.getMessage())
            );
        }

        return 1;
    }

    /**
     * Cleans old records (30+ days by default)
     */
    public int cleanup(CommandContext<ServerCommandSource> ctx) {
        try {
            long cutoff = Instant.now().getEpochSecond() - (60L * 60 * 24 * 30);

            service.cleanupOld(cutoff);

            ctx.getSource().sendFeedback(
                    () -> Text.literal("§aCleanup completed (30d retention)"),
                    false
            );

        } catch (Exception e) {
            ctx.getSource().sendError(
                    Text.literal("§cCleanup failed: " + e.getMessage())
            );
        }

        return 1;
    }

    /**
     * Shows database statistics
     */
    public int stats(CommandContext<ServerCommandSource> ctx) {
        try {
            Map<String, Integer> stats = service.getStats();

            int players = stats.getOrDefault("players", 0);
            int records = stats.getOrDefault("records", 0);
            int ips = stats.getOrDefault("ips", 0);

            ctx.getSource().sendFeedback(
                    () -> Text.literal("""
                            §bAltCheck Statistics:
                            §fPlayers: %d
                            §fRecords: %d
                            §fUnique IPs: %d
                            """.formatted(players, records, ips)),
                    false
            );

        } catch (Exception e) {
            ctx.getSource().sendError(
                    Text.literal("§cStats failed: " + e.getMessage())
            );
        }

        return 1;
    }
}