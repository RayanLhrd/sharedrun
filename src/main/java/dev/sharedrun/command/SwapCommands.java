package dev.sharedrun.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sharedrun.state.SharedRunState;
import dev.sharedrun.swap.PlayerSwapper;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class SwapCommands {
    private SwapCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("swap")
                .then(CommandManager.literal("on").executes(SwapCommands::on))
                .then(CommandManager.literal("off").executes(SwapCommands::off))
                .then(CommandManager.literal("now").executes(SwapCommands::now))
                .then(CommandManager.literal("interval")
                        .then(CommandManager.argument("min", IntegerArgumentType.integer(1, 60))
                                .then(CommandManager.argument("max", IntegerArgumentType.integer(1, 60))
                                        .executes(SwapCommands::intervalOverride)))
                        .then(CommandManager.literal("auto").executes(SwapCommands::intervalAuto))
                        .then(CommandManager.literal("show").executes(SwapCommands::intervalShow)))
                .then(CommandManager.literal("next").executes(SwapCommands::next))
                .then(CommandManager.literal("activation")
                        .then(CommandManager.argument("remainingSeconds", IntegerArgumentType.integer(0, 86400))
                                .executes(SwapCommands::activation)));
    }

    private static int on(CommandContext<ServerCommandSource> ctx) {
        SharedRunState.swapEnabled = true;
        PlayerSwapper.scheduleNext(ctx.getSource().getServer().getTicks());
        int s = PlayerSwapper.secondsUntilNextSwap(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.swap.on", s), true);
        return 1;
    }

    private static int off(CommandContext<ServerCommandSource> ctx) {
        SharedRunState.swapEnabled = false;
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.swap.off"), true);
        return 1;
    }

    private static int now(CommandContext<ServerCommandSource> ctx) {
        PlayerSwapper.doSwap(ctx.getSource().getServer());
        PlayerSwapper.scheduleNext(ctx.getSource().getServer().getTicks());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.swap.triggered"), true);
        return 1;
    }

    private static int intervalOverride(CommandContext<ServerCommandSource> ctx) {
        int min = IntegerArgumentType.getInteger(ctx, "min");
        int max = IntegerArgumentType.getInteger(ctx, "max");
        if (max < min) max = min;
        SharedRunState.swapMinMinutes = min;
        SharedRunState.swapMaxMinutes = max;
        SharedRunState.swapIntervalOverride = true;
        PlayerSwapper.scheduleNext(ctx.getSource().getServer().getTicks(), ctx.getSource().getServer());
        final int finalMax = max;
        ctx.getSource().sendFeedback(() ->
                Text.translatable("sharedrun.swap.interval.override", min, finalMax), true);
        return 1;
    }

    private static int intervalAuto(CommandContext<ServerCommandSource> ctx) {
        SharedRunState.swapIntervalOverride = false;
        PlayerSwapper.scheduleNext(ctx.getSource().getServer().getTicks(), ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.swap.interval.auto"), true);
        return 1;
    }

    private static int intervalShow(CommandContext<ServerCommandSource> ctx) {
        int[] range = PlayerSwapper.computeCurrentRangeSeconds(ctx.getSource().getServer());
        String mode = SharedRunState.swapIntervalOverride ? "override" : "auto";
        ctx.getSource().sendFeedback(() ->
                Text.translatable("sharedrun.swap.interval.show", mode, range[0], range[1]), false);
        return 1;
    }

    private static int next(CommandContext<ServerCommandSource> ctx) {
        int s = PlayerSwapper.secondsUntilNextSwap(ctx.getSource().getServer());
        int activation = PlayerSwapper.secondsUntilActivation();
        if (activation > 0) {
            ctx.getSource().sendFeedback(() ->
                    Text.translatable("sharedrun.swap.next.inactive", activation), false);
        } else {
            ctx.getSource().sendFeedback(() ->
                    Text.translatable("sharedrun.swap.next", s), false);
        }
        return 1;
    }

    private static int activation(CommandContext<ServerCommandSource> ctx) {
        int v = IntegerArgumentType.getInteger(ctx, "remainingSeconds");
        SharedRunState.swapActivationRemainingSeconds = v;
        SharedRunState.swapActivationNotified = false;
        ctx.getSource().sendFeedback(() ->
                Text.translatable("sharedrun.swap.activation.set", v), true);
        return 1;
    }
}
