package io.github.cats1337.altcheck.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import io.github.cats1337.altcheck.model.SessionEntry;
import io.github.cats1337.altcheck.service.AltCheck;
import io.github.cats1337.altcheck.util.IpUtil;
import io.github.cats1337.altcheck.util.TextUtil;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;

/**
 * Investigation commands:
 * /alts <player>
 * /ip <player>
 * /sessions <player>
 * /score <player>
 *
 * No registration logic here.
 * Only execution logic.
 */
public class AltCheckInvestigationCommands {

    private final AltCheck service;

    public AltCheckInvestigationCommands(AltCheck service) {
        this.service = service;
    }

    /**
     * Show alternative accounts linked to player
     */
    public int alts(CommandContext<ServerCommandSource> ctx) {
        String player = StringArgumentType.getString(ctx, "player");

        try {
            Set<String> alts = service.getAlts(player);

            ctx.getSource().sendFeedback(() -> {
                if (alts == null || alts.isEmpty()) {
                    return Text.literal("§7No alts found for §f" + player);
                }
                return Text.literal("§bAlts: §f" + String.join(", ", alts));
            }, false);

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§cAlts error: " + e.getMessage()));
        }

        return 1;
    }

    /**
     * Show IPs linked to player
     */
    public int ip(CommandContext<ServerCommandSource> ctx) {
        String player = StringArgumentType.getString(ctx, "player");

        try {
            Set<String> ips = service.getIps(player);

            if (ips == null || ips.isEmpty()) {
                ctx.getSource().sendFeedback(
                        () -> Text.literal("§7No IPs found for §f" + player),
                        false
                );
                return 1;
            }

            MutableText out = Text.literal("§bIPs for §f" + player + "§b:\n");

            for (String fullIp : ips) {
                String masked = IpUtil.maskIP(fullIp);
                out.append(Text.literal("§7- "));
                var ipText = Permissions.check(ctx.getSource(), "altcheck.view.ip", 4)
                        ? TextUtil.ip(fullIp, masked)
                        : Text.literal(masked).formatted(Formatting.AQUA);
                out.append(ipText);
                out.append(Text.literal("\n"));
            }

            ctx.getSource().sendFeedback(() -> out, false);

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§cIP lookup error: " + e.getMessage()));
        }

        return 1;
    }

    /**
     * Show session history for player
     */
    public int sessions(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        int page = ctx.getNodes().stream()
                .filter(n -> n.getNode().getName().equals("page"))
                .findFirst()
                .map(n -> IntegerArgumentType.getInteger(ctx, "page"))
                .orElse(1);

        try {
            String uuid = service.getUuidByUsername(playerName);

            if (uuid == null) {
                ctx.getSource().sendError(Text.literal("§cPlayer not found in database"));
                return 1;
            }

            var sessions = service.getSessions(uuid);

            if (sessions == null || sessions.isEmpty()) {
                ctx.getSource().sendFeedback(
                        () -> Text.literal("§7No sessions found for §f" + playerName),
                        false
                );
                return 1;
            }

            int perPage = 10;
            int totalPages = Math.max(1, (int) Math.ceil(sessions.size() / (double) perPage));

            if (page < 1 || page > totalPages) {
                ctx.getSource().sendError(
                        Text.literal("§cPage must be between 1 and " + totalPages)
                );
                return 1;
            }

            int from = (page - 1) * perPage;
            int to = Math.min(from + perPage, sessions.size());
            var pageSessions = sessions.subList(from, to);

            MutableText text = Text.literal("§bSessions for §f" + playerName + " ");
            text.append(pageNav(playerName, page, totalPages));
            text.append(Text.literal("\n"));

            for (SessionEntry s : pageSessions) {
                String status = s.isActive() ? "§aONLINE" : "§7OFFLINE";

                String realIp = service.getIpFromHash(s.ipHash());
                String masked = IpUtil.maskIP(realIp);

                var ipText = Permissions.check(ctx.getSource(), "altcheck.view.ip", 4)
                        ? TextUtil.ip(realIp, masked)
                        : Text.literal(masked).formatted(Formatting.AQUA);

                text.append(Text.literal("§7- §f" + s.username() + " §8| "))
                        .append(ipText)
                        .append(Text.literal(
                                " §8| §a" + s.duration() + "s" +
                                        " §8| " + status + "\n"
                        ));
            }

            ctx.getSource().sendFeedback(() -> text, false);

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§cSession error: " + e.getMessage()));
        }

        return 1;
    }

    private MutableText pageNav(String playerName, int page, int totalPages) {
        MutableText nav = Text.literal("§8[");

        if (page > 1) {
            nav.append(Text.literal("§bPrev")
                    .styled(style -> style
                            .withClickEvent(new net.minecraft.text.ClickEvent(
                                    net.minecraft.text.ClickEvent.Action.RUN_COMMAND,
                                    "/altcheck sessions " + playerName + " " + (page - 1)
                            ))
                            .withHoverEvent(new net.minecraft.text.HoverEvent(
                                    net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("§7Go to page " + (page - 1))
                            ))));
        } else {
            nav.append(Text.literal("§7Prev"));
        }

        nav.append(Text.literal(" §f" + page + "/" + totalPages + " §8"));

        if (page < totalPages) {
            nav.append(Text.literal("§bNext")
                    .styled(style -> style
                            .withClickEvent(new net.minecraft.text.ClickEvent(
                                    net.minecraft.text.ClickEvent.Action.RUN_COMMAND,
                                    "/altcheck sessions " + playerName + " " + (page + 1)
                            ))
                            .withHoverEvent(new net.minecraft.text.HoverEvent(
                                    net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                                    Text.literal("§7Go to page " + (page + 1))
                            ))));
        } else {
            nav.append(Text.literal("§7Next"));
        }

        nav.append(Text.literal("§8]"));
        return nav;
    }

    /**
     * Get VPN score
     */
    public int score(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");

        try {
            ServerPlayerEntity playerEntity =
                    ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);

            if (playerEntity == null) {
                ctx.getSource().sendError(Text.literal("§cPlayer not found"));
                return 1;
            }

            String uuid = playerEntity.getUuidAsString();

            String ip = service.getLatestIp(uuid);

            double vpnScore = ip == null ? 0 : service.getVpnScore(ip);
            double riskScore = service.getPlayerRiskScore(uuid);

            double combined = Math.min(100, (vpnScore * 0.4) + (riskScore * 0.6));

            ctx.getSource().sendFeedback(() ->
                    Text.literal("§bAltCheck Score for §f" + playerName + "\n")
                            .append(Text.literal("§7VPN Score: " + label(vpnScore) + String.format("%.1f", vpnScore) + "\n"))
                            .append(Text.literal("§7Risk Score: §f" + label(riskScore) + String.format("%.1f", riskScore) + "\n"))
                            .append(Text.literal("§eOverall: §f" + label(combined) + String.format("%.1f", combined)))
            , false);

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§cScore error: " + e.getMessage()));
        }

        return 1;
    }

    private String label(double score) {
        if (score >= 90) return "§4"; // critical (dark red)
        if (score >= 75) return "§c"; // high (red)
        if (score >= 60) return "§6"; // elevated (orange)
        if (score >= 40) return "§e"; // medium (yellow)
        if (score >= 20) return "§2"; // low (green)
        return "§a"; // very low (lime)
    }
}