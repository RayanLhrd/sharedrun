package dev.sharedrun.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sharedrun.state.SharedRunState;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class KnockbackCommands {
    private KnockbackCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("knockback")
                .then(CommandManager.literal("on").executes(KnockbackCommands::on))
                .then(CommandManager.literal("off").executes(KnockbackCommands::off))
                .then(CommandManager.literal("horizontal")
                        .then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0, 5.0))
                                .executes(KnockbackCommands::setHorizontal)))
                .then(CommandManager.literal("vertical")
                        .then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0, 2.0))
                                .executes(KnockbackCommands::setVertical)))
                .then(CommandManager.literal("cooldown")
                        .then(CommandManager.argument("seconds", DoubleArgumentType.doubleArg(0.0, 60.0))
                                .executes(KnockbackCommands::setCooldown)))
                .then(CommandManager.literal("show").executes(KnockbackCommands::show))
                .then(CommandManager.literal("reset").executes(KnockbackCommands::reset));
    }

    private static int on(CommandContext<ServerCommandSource> ctx) {
        SharedRunState.knockbackEnabled = true;
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.knockback.on"), true);
        return 1;
    }

    private static int off(CommandContext<ServerCommandSource> ctx) {
        SharedRunState.knockbackEnabled = false;
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.knockback.off"), true);
        return 1;
    }

    private static int setHorizontal(CommandContext<ServerCommandSource> ctx) {
        double v = DoubleArgumentType.getDouble(ctx, "value");
        SharedRunState.knockbackHorizontal = v;
        ctx.getSource().sendFeedback(() ->
                Text.translatable("sharedrun.knockback.h", String.format("%.2f", v)), true);
        return 1;
    }

    private static int setVertical(CommandContext<ServerCommandSource> ctx) {
        double v = DoubleArgumentType.getDouble(ctx, "value");
        SharedRunState.knockbackVertical = v;
        ctx.getSource().sendFeedback(() ->
                Text.translatable("sharedrun.knockback.v", String.format("%.2f", v)), true);
        return 1;
    }

    private static int setCooldown(CommandContext<ServerCommandSource> ctx) {
        double s = DoubleArgumentType.getDouble(ctx, "seconds");
        int ticks = Math.max(0, (int) Math.round(s * 20));
        SharedRunState.knockbackCooldownTicks = ticks;
        ctx.getSource().sendFeedback(() ->
                Text.translatable("sharedrun.knockback.cooldown", String.format("%.2f", s), ticks), true);
        return 1;
    }

    private static int show(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.knockback.show",
                SharedRunState.knockbackEnabled ? "ON" : "OFF",
                String.format("%.2f", SharedRunState.knockbackHorizontal),
                String.format("%.2f", SharedRunState.knockbackVertical),
                String.format("%.2f", SharedRunState.knockbackCooldownTicks / 20.0)
        ), false);
        return 1;
    }

    private static int reset(CommandContext<ServerCommandSource> ctx) {
        SharedRunState.knockbackHorizontal = 0.2;
        SharedRunState.knockbackVertical = 0.2;
        SharedRunState.knockbackCooldownTicks = 30;
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.knockback.reset"), true);
        return 1;
    }
}
