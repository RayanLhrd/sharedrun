package dev.sharedrun.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sharedrun.state.SharedRunState;
import dev.sharedrun.sync.HpSyncManager;
import dev.sharedrun.sync.HungerSyncManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class SyncCommands {
    private SyncCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> hp() {
        return CommandManager.literal("hp")
                .then(CommandManager.literal("on").executes(ctx -> {
                    SharedRunState.hpSyncEnabled = true;
                    ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.hp.on"), true);
                    return 1;
                }))
                .then(CommandManager.literal("off").executes(ctx -> {
                    SharedRunState.hpSyncEnabled = false;
                    HpSyncManager.clearTracking();
                    ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.hp.off"), true);
                    return 1;
                }))
                .then(CommandManager.literal("resync")
                        .executes(SyncCommands::resyncDefault)
                        .then(CommandManager.argument("value", IntegerArgumentType.integer(0, 1000))
                                .executes(SyncCommands::resyncValue)));
    }

    public static LiteralArgumentBuilder<ServerCommandSource> hunger() {
        return CommandManager.literal("hunger")
                .then(CommandManager.literal("on").executes(ctx -> {
                    SharedRunState.hungerSyncEnabled = true;
                    ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.sync.hunger.on"), true);
                    return 1;
                }))
                .then(CommandManager.literal("off").executes(ctx -> {
                    SharedRunState.hungerSyncEnabled = false;
                    HungerSyncManager.clearTracking();
                    ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.sync.hunger.off"), true);
                    return 1;
                }));
    }

    public static LiteralArgumentBuilder<ServerCommandSource> effects() {
        return CommandManager.literal("effects")
                .then(CommandManager.literal("on").executes(ctx -> {
                    SharedRunState.effectsSyncEnabled = true;
                    ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.sync.effects.on"), true);
                    return 1;
                }))
                .then(CommandManager.literal("off").executes(ctx -> {
                    SharedRunState.effectsSyncEnabled = false;
                    ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.sync.effects.off"), true);
                    return 1;
                }));
    }

    public static LiteralArgumentBuilder<ServerCommandSource> syncAll() {
        return CommandManager.literal("sync")
                .then(CommandManager.literal("on").executes(ctx -> {
                    SharedRunState.hpSyncEnabled = true;
                    SharedRunState.hungerSyncEnabled = true;
                    SharedRunState.effectsSyncEnabled = true;
                    ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.sync.all.on"), true);
                    return 1;
                }))
                .then(CommandManager.literal("off").executes(ctx -> {
                    SharedRunState.hpSyncEnabled = false;
                    SharedRunState.hungerSyncEnabled = false;
                    SharedRunState.effectsSyncEnabled = false;
                    HpSyncManager.clearTracking();
                    HungerSyncManager.clearTracking();
                    ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.sync.all.off"), true);
                    return 1;
                }));
    }

    private static int resyncDefault(CommandContext<ServerCommandSource> ctx) {
        HpSyncManager.resyncAll(ctx.getSource().getServer(), SharedRunState.sharedHealth);
        final float v = SharedRunState.sharedHealth;
        ctx.getSource().sendFeedback(() ->
                Text.translatable("sharedrun.hp.resync", String.format("%.1f", v)), true);
        return 1;
    }

    private static int resyncValue(CommandContext<ServerCommandSource> ctx) {
        float v = IntegerArgumentType.getInteger(ctx, "value");
        HpSyncManager.resyncAll(ctx.getSource().getServer(), v);
        ctx.getSource().sendFeedback(() ->
                Text.translatable("sharedrun.hp.resync", String.format("%.1f", v)), true);
        return 1;
    }
}
