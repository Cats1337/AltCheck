package io.github.cats1337.altcheck.commands;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class SmartHelp {
    public enum Category {
        CORE,
        INV,
        ADMIN
    }
    public record Entry(
            Category category,
            String permission,
            int level,
            String command,
            String description
    ) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry(Category.CORE, "altcheck.trace", 2, "/altcheck trace <player>", "Trace a player"),
            new Entry(Category.CORE, "altcheck.lookup", 4, "/altcheck lookup <ip>", "Lookup IP data"),
            new Entry(Category.CORE, "altcheck.history", 4, "/altcheck history <player>", "View IP history"),
            new Entry(Category.CORE, "altcheck.list", 4, "/altcheck list", "Show IP groups"),

            new Entry(Category.INV, "altcheck.alts", 2, "/altcheck alts <player>", "Find alternate accounts"),
            new Entry(Category.INV, "altcheck.ip", 4, "/altcheck ip <player>", "Show IP links"),
            new Entry(Category.INV, "altcheck.sessions", 4, "/altcheck sessions <player> [page]", "View session history"),
            new Entry(Category.INV, "altcheck.score", 2, "/altcheck score <player>", "Calculate risk score"),

            new Entry(Category.ADMIN, "altcheck.purge", 4, "/altcheck purge <player>", "Delete player data"),
            new Entry(Category.ADMIN, "altcheck.hide", 4, "/altcheck hide <player>", "Toggle visibility"),
            new Entry(Category.ADMIN, "altcheck.cleanup", 4, "/altcheck cleanup", "Remove old records"),
            new Entry(Category.ADMIN, "altcheck.stats", 2, "/altcheck stats", "View database stats")
    );

    public static Text build(ServerCommandSource src) {
        return build(src, null);
    }

    public static Text build(ServerCommandSource src, Category filter) {
        MutableText out = Text.literal("§9§l§nAltCheck Help\n\n");

        boolean any = false;

        for (Entry e : ENTRIES) {
            if (filter != null && e.category != filter) {
                continue;
            }

            if (!Permissions.check(src, e.permission(), e.level())) {
                continue;
            }

            any = true;
            out.append(helpLine(e));
        }

        if (!any) {
            return Text.literal("§7No available commands.");
        }

        return out;
    }

    private static Text helpLine(Entry e) {
        return Text.literal("§b" + e.command)
                .styled(style -> style
                        .withColor(Formatting.AQUA)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.SUGGEST_COMMAND,
                                e.command.substring(0, e.command.indexOf(" <") > 0
                                        ? e.command.indexOf(" <")
                                        : e.command.length())
                        ))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Text.literal("§9AltCheck\n§7" + e.command + "\n§8Click to suggest")
                        )))
                .append(Text.literal(" §8- §7" + e.description))
                .append(Text.literal("\n"));
    }

    public static Category parseCategory(String raw) {
        if (raw == null) return null;

        return switch (raw.toLowerCase()) {
            case "core" -> Category.CORE;
            case "inv" -> Category.INV;
            case "admin" -> Category.ADMIN;
            default -> null;
        };
    }
}