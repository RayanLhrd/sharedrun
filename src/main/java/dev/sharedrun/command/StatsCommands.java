package dev.sharedrun.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sharedrun.stats.HeartsCausedTracker;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class StatsCommands {
    private StatsCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("stats")
                .then(CommandManager.literal("reset").executes(StatsCommands::reset))
                .then(CommandManager.literal("show").executes(StatsCommands::show));
    }

    private static int reset(CommandContext<ServerCommandSource> ctx) {
        HeartsCausedTracker.resetAll();
        ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.stats.reset"), true);
        return 1;
    }

    private static int show(CommandContext<ServerCommandSource> ctx) {
        var board = HeartsCausedTracker.sortedLeaderboard();
        ServerCommandSource src = ctx.getSource();
        Text divider = Text.literal("§6§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        src.sendFeedback(() -> Text.literal(""), false);
        src.sendFeedback(() -> divider, false);
        src.sendFeedback(() -> Text.literal("           ")
                .append(Text.translatable("sharedrun.stats.show.header").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        src.sendFeedback(() -> Text.literal(""), false);
        if (board.isEmpty()) {
            src.sendFeedback(() -> Text.translatable("sharedrun.stats.show.empty").formatted(Formatting.GRAY), false);
        } else {
            int rank = 1;
            for (var e : board) {
                String medal = switch (rank) {
                    case 1 -> "§e①";
                    case 2 -> "§7②";
                    case 3 -> "§6③";
                    default -> "§8" + rank + ".";
                };
                final int finalRank = rank;
                Text line = Text.literal(" " + medal + " ")
                        .append(Text.literal(e.name()).formatted(Formatting.WHITE, Formatting.BOLD))
                        .append(Text.literal(" — ").formatted(Formatting.DARK_GRAY))
                        .append(Text.literal(String.format("%.1f ❤", e.hearts())).formatted(Formatting.RED));
                src.sendFeedback(() -> line, false);
                rank++;
            }
        }
        src.sendFeedback(() -> Text.literal(""), false);
        src.sendFeedback(() -> divider, false);
        return 1;
    }
}
