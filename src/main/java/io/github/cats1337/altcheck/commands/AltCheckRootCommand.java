package io.github.cats1337.altcheck.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.cats1337.altcheck.service.AltCheck;
import io.github.cats1337.altcheck.util.PlayerSuggestions;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;

import com.mojang.brigadier.arguments.StringArgumentType;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Root command:
 * /altcheck
 *
 * Only responsibility:
 * wiring subcommands into Brigadier.
 */
public class AltCheckRootCommand {

    private final AltCheckCoreCommands core;
    private final AltCheckInvestigationCommands inv;
    private final AltCheckAdminCommands admin;

    public AltCheckRootCommand(AltCheck service) {
        this.core = new AltCheckCoreCommands(service);
        this.inv = new AltCheckInvestigationCommands(service);
        this.admin = new AltCheckAdminCommands(service);
    }

    public void register() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(literal("altcheck")
                    .requires(src -> Permissions.check(src, "altcheck.use", 1))

                    // =====================================================
                    // HELP
                    // =====================================================
                    .then(literal("help")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(
                                    () -> SmartHelp.build(ctx.getSource()),
                                    false
                            );
                            return 1;
                        })
                        .then(argument("category", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("core");
                                builder.suggest("inv");
                                builder.suggest("admin");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String raw = StringArgumentType.getString(ctx, "category");
                                SmartHelp.Category category = SmartHelp.parseCategory(raw);

                                if (category == null) {
                                    ctx.getSource().sendError(Text.literal("§cUnknown category. Use core, inv, or admin."));
                                    return 0;
                                }

                                ctx.getSource().sendFeedback(
                                        () -> SmartHelp.build(ctx.getSource(), category),
                                        false
                                );
                                return 1;
                            })
                        )
                    )

                    // =====================================================
                    // CORE
                    // =====================================================

                    .then(literal("trace")
                        .requires(src -> Permissions.check(src, "altcheck.trace", 4))
                        .then(argument("player", StringArgumentType.word())
                                .suggests(PlayerSuggestions::suggestPlayers)
                                .executes(core::trace)))

                    .then(literal("list")
                        .requires(src -> Permissions.check(src, "altcheck.list", 4))
                        .executes(core::list))

                    .then(literal("lookup")
                        .requires(src -> Permissions.check(src, "altcheck.lookup", 4))
                        .then(argument("ip", StringArgumentType.word())
                            .executes(core::lookup)))

                    .then(literal("history")
                        .requires(src -> Permissions.check(src, "altcheck.history", 4))
                        .then(argument("player", StringArgumentType.word())
                            .suggests(PlayerSuggestions::suggestPlayers)
                            .executes(core::history)))

                    // =====================================================
                    // INVESTIGATION
                    // =====================================================

                    .then(literal("alts")
                        .requires(src -> Permissions.check(src, "altcheck.alts", 4))
                        .then(argument("player", StringArgumentType.word())
                            .suggests(PlayerSuggestions::suggestPlayers)
                            .executes(inv::alts)))

                    .then(literal("ip")
                        .requires(src -> Permissions.check(src, "altcheck.ip", 4))
                        .then(argument("player", StringArgumentType.word())
                            .suggests(PlayerSuggestions::suggestPlayers)
                            .executes(inv::ip)))

                    .then(literal("sessions")
                            .requires(src -> Permissions.check(src, "altcheck.sessions", 4))
                            .then(argument("player", StringArgumentType.word())
                                    .suggests(PlayerSuggestions::suggestPlayers)
                                    .executes(inv::sessions)
                                    .then(argument("page", IntegerArgumentType.integer(1))
                                            .executes(inv::sessions))))
                    .then(literal("score")
                        .requires(src -> Permissions.check(src, "altcheck.score", 4))
                        .then(argument("player", StringArgumentType.word())
                            .suggests(PlayerSuggestions::suggestPlayers)
                            .executes(inv::score)))

                    // =====================================================
                    // ADMIN
                    // =====================================================

                    .then(literal("purge")
                        .requires(src -> Permissions.check(src, "altcheck.purge", 4))
                        .then(argument("player", StringArgumentType.word())
                            .suggests(PlayerSuggestions::suggestPlayers)
                            .executes(admin::purge)))

                    .then(literal("hide")
                        .requires(src -> Permissions.check(src, "altcheck.hide", 4))
                        .then(argument("player", StringArgumentType.word())
                            .suggests(PlayerSuggestions::suggestPlayers)
                            .executes(admin::hide)))

                    .then(literal("cleanup")
                        .requires(src -> Permissions.check(src, "altcheck.cleanup", 4))
                        .executes(admin::cleanup))

                    .then(literal("stats")
                        .requires(src -> Permissions.check(src, "altcheck.stats", 4))
                        .executes(admin::stats))
            );


            dispatcher.register(literal("altc")
                .redirect(dispatcher.getRoot().getChild("altcheck")));
        });
    }
}