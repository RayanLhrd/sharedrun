package dev.sharedrun.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public final class SettingsCommands {
    private SettingsCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("settings")
                .then(CommandManager.literal("show").executes(ConfigCommands::showStatus))
                .then(TimerCommands.build())
                .then(SyncCommands.hp())
                .then(SyncCommands.hunger())
                .then(SyncCommands.effects())
                .then(SyncCommands.syncAll())
                .then(SwapCommands.build())
                .then(KnockbackCommands.build())
                .then(LobbyCommands.build());
    }
}
