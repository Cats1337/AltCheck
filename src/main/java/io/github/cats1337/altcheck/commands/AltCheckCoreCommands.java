package io.github.cats1337.altcheck.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import io.github.cats1337.altcheck.service.AltCheck;
import io.github.cats1337.altcheck.util.IpUtil;

import io.github.cats1337.altcheck.util.TextUtil;
import io.github.cats1337.altcheck.util.TimeUtil;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.Set;

/**
 * Core commands:
 * /altcheck trace <player>
 * /altcheck lookup <ip>
 * /altcheck history <player>
 * /altcheck list
 *
 * No command registration here, only execution logic.
 */
public class AltCheckCoreCommands {

    private final AltCheck service;

    public AltCheckCoreCommands(AltCheck service) {
        this.service = service;
    }

    // =========================================================
    // TRACE PLAYER
    // =========================================================
    public int trace(CommandContext<ServerCommandSource> ctx) {

        String player = StringArgumentType.getString(ctx, "player");

        try {
            Set<String> alts = service.getAlts(player);

            if (alts == null || alts.isEmpty()) {
                ctx.getSource().sendFeedback(
                        () -> Text.literal("§7No alts found for §f" + player),
                        false
                );
                return 1;
            }

            ctx.getSource().sendFeedback(
                    () -> Text.literal("§bAlts for §f" + player + "§b: §f" + String.join(", ", alts)),
                    false
            );

        } catch (Exception e) {
            ctx.getSource().sendError(
                    Text.literal("§cTrace error: " + e.getMessage())
            );
        }

        return 1;
    }

    // =========================================================
    // LIST ALTS
    // =========================================================
    /**
     * Shows grouped players by shared IPs.
     * Useful for quick alt detection overview.
     */
    public int list(CommandContext<ServerCommandSource> ctx) {

        try {
            var map = service.getSharedIps();

            if (map == null || map.isEmpty()) {
                ctx.getSource().sendFeedback(
                        () -> Text.literal("§7No IP groups found."),
                        false
                );
                return 1;
            }

            MutableText out = Text.literal("§bAltCheck IP Groups:\n");

            boolean found = false;

            for (var entry : map.entrySet()) {

                var players = entry.getValue();
                var ip = entry.getKey();
                var masked = IpUtil.maskIP(ip);

                // Only show potential alt groups
                if (players == null || players.size() < 2) continue;

                found = true;

                var ipText = Permissions.check(ctx.getSource(), "altcheck.view.ip", 4)
                        ? TextUtil.ip(ip, masked)
                        : Text.literal(masked).formatted(Formatting.AQUA);

                out.append(Text.literal("§7- §f"))
                        .append(ipText)
                        .append(Text.literal(" §8| §f"));

                // Player list
                out.append(Text.literal(String.join(", ", players)));

                out.append(Text.literal("\n"));
            }

            if (!found) {
                ctx.getSource().sendFeedback(
                        () -> Text.literal("§7No shared IP groups found."),
                        false
                );
                return 1;
            }

            ctx.getSource().sendFeedback(() -> out, false);

        } catch (Exception e) {
            ctx.getSource().sendError(
                    Text.literal("§cList error: " + e.getMessage())
            );
        }

        return 1;
    }

    // =========================================================
    // LOOKUP IP
    // =========================================================
    public int lookup(CommandContext<ServerCommandSource> ctx) {

        String ip = StringArgumentType.getString(ctx, "ip");

        try {
            String hash = IpUtil.hashIP(ip);

            Set<String> players = service.getPlayersByIpHash(hash);

            if (players == null || players.isEmpty()) {
                ctx.getSource().sendFeedback(
                        () -> Text.literal("§7No players found for IP."),
                        false
                );
                return 1;
            }

            ctx.getSource().sendFeedback(
                    () -> Text.literal("§bPlayers on IP: §f" + String.join(", ", players)),
                    false
            );

        } catch (Exception e) {
            ctx.getSource().sendError(
                    Text.literal("§cLookup error: " + e.getMessage())
            );
        }

        return 1;
    }

    // =========================================================
    // HISTORY
    // =========================================================
    public int history(CommandContext<ServerCommandSource> ctx) {

        String player = StringArgumentType.getString(ctx, "player");

        try {
            var history = service.getHistory(player);

            if (history == null || history.isEmpty()) {
                ctx.getSource().sendFeedback(
                        () -> Text.literal("§7No history found for §f" + player),
                        false
                );
                return 1;
            }

            MutableText out = Text.literal("§bHistory for §f" + player + "\n");

            for (var entry : history) {

                String ip = entry.ip();
                String masked = entry.maskedIp() != null ? entry.maskedIp() : ip;

                var ipText = Permissions.check(ctx.getSource(), "altcheck.view.ip", 4)
                        ? TextUtil.ip(ip, masked)
                        : Text.literal(masked).formatted(Formatting.AQUA);

                out.append(Text.literal("§7- §f"));
                out.append(ipText);

                out.append(Text.literal(" §8|"))
                        .append(Text.literal("§a" + TimeUtil.format(entry.firstSeen())))
                        .append(Text.literal(" §7→ "))
                        .append(Text.literal("§c" + TimeUtil.format(entry.lastSeen())))
                        .append(Text.literal(" §8(" + entry.sessionCount() + " sessions)"));
            }

            ctx.getSource().sendFeedback(() -> out, false);

        } catch (Exception e) {
            ctx.getSource().sendError(
                    Text.literal("§cHistory error: " + e.getMessage())
            );
        }

        return 1;
    }
}