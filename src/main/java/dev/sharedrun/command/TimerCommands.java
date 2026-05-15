package dev.sharedrun.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sharedrun.state.SharedRunState;
import dev.sharedrun.timer.GameTimer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class TimerCommands {
    private TimerCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("timer")
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1, 86400))
                                .executes(TimerCommands::set)))
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1, 86400))
                                .executes(TimerCommands::add)))
                .then(CommandManager.literal("sub")
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1, 86400))
                                .executes(TimerCommands::sub)));
    }

    public static int start(CommandContext<ServerCommandSource> ctx) {
        GameTimer.start(ctx.getSource().getServer());
        GameTimer.broadcast(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.timer.start"), true);
        return 1;
    }

    public static int pause(CommandContext<ServerCommandSource> ctx) {
        GameTimer.pause();
        GameTimer.broadcast(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.timer.pause"), true);
        return 1;
    }

    public static int resume(CommandContext<ServerCommandSource> ctx) {
        GameTimer.resume();
        GameTimer.broadcast(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.timer.resume"), true);
        return 1;
    }

    public static int stop(CommandContext<ServerCommandSource> ctx) {
        GameTimer.stop();
        GameTimer.broadcast(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.timer.stop"), true);
        return 1;
    }

    public static int reset(CommandContext<ServerCommandSource> ctx) {
        GameTimer.reset();
        GameTimer.broadcast(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.timer.reset"), true);
        return 1;
    }

    private static int set(CommandContext<ServerCommandSource> ctx) {
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        GameTimer.setSeconds(seconds);
        GameTimer.broadcast(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.timer.set", seconds), true);
        return 1;
    }

    private static int add(CommandContext<ServerCommandSource> ctx) {
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        SharedRunState.remainingTicks = Math.min(SharedRunState.totalSeconds * 20,
                SharedRunState.remainingTicks + seconds * 20);
        GameTimer.seedCheckpointsFromRemaining();
        GameTimer.broadcast(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.timer.add", seconds), true);
        return 1;
    }

    private static int sub(CommandContext<ServerCommandSource> ctx) {
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        SharedRunState.remainingTicks = Math.max(0, SharedRunState.remainingTicks - seconds * 20);
        GameTimer.seedCheckpointsFromRemaining();
        GameTimer.broadcast(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.timer.sub", seconds), true);
        return 1;
    }
}
