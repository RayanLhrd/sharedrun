package dev.sharedrun.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public final class SharedRunCommands {
    private SharedRunCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> root = dispatcher.register(
                CommandManager.literal("sharedrun")
                        .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                        // === Actions de jeu ===
                        .then(CommandManager.literal("start").executes(TimerCommands::start))
                        .then(CommandManager.literal("pause").executes(TimerCommands::pause))
                        .then(CommandManager.literal("resume").executes(TimerCommands::resume))
                        .then(CommandManager.literal("stop").executes(TimerCommands::stop))
                        .then(CommandManager.literal("reset").executes(TimerCommands::reset))
                        // === Info ===
                        .then(ConfigCommands.status())
                        .then(ConfigCommands.help())
                        // === Configuration complète ===
                        .then(SettingsCommands.build())
                        // === Données de partie ===
                        .then(StatsCommands.build())
                        // === Actions admin ===
                        .then(MiscCommands.save())
                        // === Debrief ===
                        .then(MiscCommands.tp())
                        .then(MiscCommands.debrief())
        );

        dispatcher.register(CommandManager.literal("sr")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .redirect(root));
    }
}
