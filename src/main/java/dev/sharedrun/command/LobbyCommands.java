package dev.sharedrun.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sharedrun.lobby.LobbyManager;
import dev.sharedrun.state.SharedRunState;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class LobbyCommands {
    private LobbyCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("lobby")
                .then(CommandManager.literal("open").executes(LobbyCommands::open))
                .then(CommandManager.literal("close").executes(LobbyCommands::close))
                .then(CommandManager.literal("radius")
                        .then(CommandManager.argument("blocks", DoubleArgumentType.doubleArg(2.0, 200.0))
                                .executes(LobbyCommands::radius)))
                .then(CommandManager.literal("gather").executes(LobbyCommands::gather));
    }

    private static int open(CommandContext<ServerCommandSource> ctx) {
        SharedRunState.preGame = true;
        LobbyManager.applyLobbyBorder(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.lobby.opened"), true);
        return 1;
    }

    private static int close(CommandContext<ServerCommandSource> ctx) {
        SharedRunState.preGame = false;
        LobbyManager.restoreBorder(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.lobby.closed"), true);
        return 1;
    }

    private static int radius(CommandContext<ServerCommandSource> ctx) {
        double r = DoubleArgumentType.getDouble(ctx, "blocks");
        SharedRunState.pregameRadius = r;
        if (SharedRunState.preGame) {
            LobbyManager.applyLobbyBorder(ctx.getSource().getServer());
        }
        ctx.getSource().sendFeedback(() ->
                Text.translatable("sharedrun.lobby.radius.set", String.format("%.1f", r)), true);
        return 1;
    }

    private static int gather(CommandContext<ServerCommandSource> ctx) {
        LobbyManager.gatherAll(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.lobby.gathered"), true);
        return 1;
    }
}
