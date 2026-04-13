package io.github.cats1337.altcheck.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import io.github.cats1337.altcheck.service.AltCheck;
import net.minecraft.server.command.ServerCommandSource;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Suggests:
 * - online players
 * - offline players (from DB via service)
 * - fuzzy matching support
 */
public class PlayerSuggestions {

    private static AltCheck service;

    /**
     * Inject service once from mod init
     */
    public static void init(AltCheck svc) {
        service = svc;
    }

    /**
     * Main suggestion provider (used by Brigadier)
     */
    public static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<ServerCommandSource> ctx,
            SuggestionsBuilder builder
    ) {
        Set<String> results = new HashSet<>();

        var server = ctx.getSource().getServer();

        // =====================================================
        // 1. ONLINE PLAYERS
        // =====================================================
        server.getPlayerManager().getPlayerList().forEach(p ->
                results.add(p.getName().getString())
        );

        // =====================================================
        // 2. OFFLINE PLAYERS (DATABASE)
        // =====================================================
        if (service != null) {
            results.addAll(service.getAllKnownPlayers());
        }

        // =====================================================
        // 3. FUZZY FILTERING
        // =====================================================
        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        results.stream()
                .distinct()
                .filter(name -> fuzzyMatch(name.toLowerCase(Locale.ROOT), input))
                .sorted()
                .forEach(builder::suggest);

        return builder.buildFuture();
    }

    /**
     * Simple fuzzy matcher:
     * - substring match
     * - prefix match
     * - basic typo tolerance
     */
    private static boolean fuzzyMatch(String name, String input) {
        if (input.isEmpty()) return true;

        if (name.contains(input)) return true;

        if (name.startsWith(input)) return true;

        return levenshtein(name, input) <= 2;
    }

    /**
     * Levenshtein distance (cheap typo tolerance)
     */
    private static int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            int lastValue = i;

            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else if (j > 0) {
                    int newValue = costs[j - 1];

                    if (a.charAt(i - 1) != b.charAt(j - 1)) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    }

                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }

            if (i > 0) costs[b.length()] = lastValue;
        }

        return costs[b.length()];
    }
}