package dev.sharedrun.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sharedrun.endrun.EndRunHandler;
import dev.sharedrun.state.SharedRunPersistence;
import dev.sharedrun.state.SharedRunState;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MiscCommands {
    private MiscCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> save() {
        return CommandManager.literal("save").executes(ctx -> {
            SharedRunPersistence.save(ctx.getSource().getServer());
            ctx.getSource().sendFeedback(() -> Text.translatable("sharedrun.save.done"), true);
            return 1;
        });
    }

    public static LiteralArgumentBuilder<ServerCommandSource> tp() {
        return CommandManager.literal("tp")
                .then(CommandManager.literal("overworld").executes(ctx -> doDebriefTp(ctx, 1)))
                .then(CommandManager.literal("nether").executes(ctx -> doDebriefTp(ctx, 2)))
                .then(CommandManager.literal("stronghold").executes(ctx -> doDebriefTp(ctx, 3)))
                .then(CommandManager.literal("end").executes(ctx -> doDebriefTp(ctx, 4)));
    }

    public static LiteralArgumentBuilder<ServerCommandSource> debrief() {
        return CommandManager.literal("debrief").executes(ctx -> {
            ServerCommandSource src = ctx.getSource();
            ServerPlayerEntity player;
            try {
                player = src.getPlayerOrThrow();
            } catch (CommandSyntaxException e) {
                src.sendError(Text.literal("Cette commande doit être exécutée par un joueur."));
                return 0;
            }
            if (!EndRunHandler.resendSummaryTo(player)) {
                src.sendError(Text.literal("Aucun récap disponible — termine une run d'abord.")
                        .formatted(Formatting.RED));
                return 0;
            }
            return 1;
        });
    }

    private static int doDebriefTp(CommandContext<ServerCommandSource> ctx, int dest) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayerOrThrow();
        } catch (CommandSyntaxException e) {
            src.sendError(Text.literal("Cette commande doit être exécutée par un joueur."));
            return 0;
        }
        if (!SharedRunState.runEnded) {
            src.sendError(Text.literal("Disponible uniquement après la fin du run (debrief).")
                    .formatted(Formatting.RED));
            return 0;
        }
        EndRunHandler.handleDebriefTp(src.getServer(), player, dest);
        String label = switch (dest) {
            case 1 -> "Overworld";
            case 2 -> "Nether";
            case 3 -> "Stronghold";
            case 4 -> "End";
            default -> "?";
        };
        src.sendFeedback(() -> Text.literal("→ TP debrief : " + label).formatted(Formatting.GRAY), false);
        return 1;
    }
}
