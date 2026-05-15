package dev.sharedrun.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sharedrun.state.SharedRunState;
import dev.sharedrun.swap.PlayerSwapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ConfigCommands {
    private ConfigCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> status() {
        return CommandManager.literal("status").executes(ConfigCommands::showStatus);
    }

    public static LiteralArgumentBuilder<ServerCommandSource> help() {
        return CommandManager.literal("help").executes(ConfigCommands::showHelp);
    }

    public static int showStatus(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        MinecraftServer server = src.getServer();

        String state;
        if (SharedRunState.victoryTriggered) state = "Victory";
        else if (SharedRunState.runEnded) state = "Ended";
        else if (SharedRunState.preGame) state = "Pre-game";
        else if (!SharedRunState.timerRunning) state = "Paused";
        else state = "Running";

        int remainSec = SharedRunState.remainingTicks / 20;
        String time = formatTime(remainSec) + " / " + formatTime(SharedRunState.totalSeconds);

        String sync = String.format("HP=%s Hunger=%s Effects=%s",
                SharedRunState.hpSyncEnabled ? "ON" : "OFF",
                SharedRunState.hungerSyncEnabled ? "ON" : "OFF",
                SharedRunState.effectsSyncEnabled ? "ON" : "OFF");

        String swap;
        if (!SharedRunState.swapEnabled) {
            swap = "OFF";
        } else {
            int next = PlayerSwapper.secondsUntilNextSwap(server);
            int activation = PlayerSwapper.secondsUntilActivation();
            String mode = SharedRunState.swapIntervalOverride
                    ? String.format("%d-%d min (override)", SharedRunState.swapMinMinutes, SharedRunState.swapMaxMinutes)
                    : "auto";
            if (activation > 0) {
                swap = String.format("ON, %s, inactif encore %s", mode, formatTime(activation));
            } else if (next < 0) {
                swap = String.format("ON, %s", mode);
            } else {
                swap = String.format("ON, %s, prochain dans %s", mode, formatTime(next));
            }
        }

        String knockback = String.format("%s h=%.2f v=%.2f cd=%.1fs",
                SharedRunState.knockbackEnabled ? "ON" : "OFF",
                SharedRunState.knockbackHorizontal,
                SharedRunState.knockbackVertical,
                SharedRunState.knockbackCooldownTicks / 20.0);

        String lobby = String.format("preGame=%s, radius=%.1f",
                SharedRunState.preGame, SharedRunState.pregameRadius);

        Text divider = Text.literal("§7§m──────────────────────────────");
        src.sendFeedback(() -> divider, false);
        src.sendFeedback(() -> Text.literal("  ").append(
                Text.translatable("sharedrun.status.header").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        src.sendFeedback(() -> divider, false);
        sendKv(src, "sharedrun.status.state", state);
        sendKv(src, "sharedrun.status.time", time);
        sendKv(src, "sharedrun.status.sync", sync);
        sendKv(src, "sharedrun.status.swap", swap);
        sendKv(src, "sharedrun.status.knockback", knockback);
        sendKv(src, "sharedrun.status.lobby", lobby);
        src.sendFeedback(() -> divider, false);
        return 1;
    }

    private static int showHelp(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        Text divider = Text.literal("§7§m──────────────────────────────");
        src.sendFeedback(() -> divider, false);
        src.sendFeedback(() -> Text.literal("  ").append(
                Text.translatable("sharedrun.help.header").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        src.sendFeedback(() -> divider, false);
        String[] lines = {
                "§e§lActions",
                "§7  /sharedrun start | pause | resume | stop | reset",
                "§7  /sharedrun status — état du jeu",
                "",
                "§e§lConfiguration",
                "§7  /sharedrun settings show",
                "§7  /sharedrun settings timer <set|add|sub> <s>",
                "§7  /sharedrun settings hp <on|off|resync [v]>",
                "§7  /sharedrun settings hunger <on|off>",
                "§7  /sharedrun settings effects <on|off>",
                "§7  /sharedrun settings sync <on|off>",
                "§7  /sharedrun settings swap <on|off|now|interval|next|activation>",
                "§7  /sharedrun settings knockback <on|off|horizontal|vertical|cooldown|show|reset>",
                "§7  /sharedrun settings lobby <open|close|radius|gather>",
                "",
                "§e§lDebrief (post-run)",
                "§7  /sharedrun debrief — rouvrir l'écran de récap",
                "§7  /sharedrun tp <overworld|nether|stronghold|end> — TP debrief (spectator)",
                "",
                "§e§lAutres",
                "§7  /sharedrun stats <reset|show>",
                "§7  /sharedrun save",
                "",
                "§8(alias court : /sr)"
        };
        for (String line : lines) {
            final String l = line;
            src.sendFeedback(() -> Text.literal(l), false);
        }
        src.sendFeedback(() -> divider, false);
        return 1;
    }

    private static void sendKv(ServerCommandSource src, String key, String value) {
        src.sendFeedback(() -> Text.literal("  ")
                .append(Text.translatable(key).formatted(Formatting.YELLOW))
                .append(Text.literal(" "))
                .append(Text.literal(value).formatted(Formatting.WHITE)), false);
    }

    private static String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }
}
