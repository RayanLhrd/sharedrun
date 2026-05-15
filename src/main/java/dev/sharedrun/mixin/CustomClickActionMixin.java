package dev.sharedrun.mixin;

import dev.sharedrun.endrun.EndRunHandler;
import net.minecraft.network.packet.c2s.common.CustomClickActionC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepte les ClickEvent.Custom envoyés depuis le chat pour notre namespace `sharedrun`.
 * Évite la popup de confirmation que ClickEvent.RunCommand déclenche par défaut.
 *
 * Identifiers gérés :
 *   sharedrun:tp_overworld | tp_nether | tp_stronghold | tp_end → TP debrief
 *   sharedrun:debrief                                            → ré-ouvre l'écran récap
 */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class CustomClickActionMixin {

    @Inject(method = "onCustomClickAction", at = @At("HEAD"))
    private void sharedrun$intercept(CustomClickActionC2SPacket packet, CallbackInfo ci) {
        Identifier id = packet.id();
        if (id == null) return;
        if (!"sharedrun".equals(id.getNamespace())) return;

        // ServerCommonNetworkHandler est la base ; on a besoin du play handler pour avoir le joueur
        if (!(((Object) this) instanceof ServerPlayNetworkHandler playHandler)) return;
        ServerPlayerEntity player = playHandler.player;
        if (player == null) return;
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        String path = id.getPath();
        int dest;
        switch (path) {
            case "tp_overworld" -> dest = 1;
            case "tp_nether"    -> dest = 2;
            case "tp_stronghold"-> dest = 3;
            case "tp_end"       -> dest = 4;
            case "tp_fortress"  -> dest = 5;
            case "debrief"      -> {
                server.execute(() -> EndRunHandler.resendSummaryTo(player));
                return;
            }
            default -> { return; }
        }
        final int finalDest = dest;
        server.execute(() -> EndRunHandler.handleDebriefTp(server, player, finalDest));
    }
}
