package dev.sharedrun.lobby;

import dev.sharedrun.state.SharedRunState;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.rule.GameRules;

public final class LobbyManager {
    private static final double VANILLA_BORDER_SIZE = 5.9999968E7;

    private LobbyManager() {}

    public static Vec3d getLobbyCenter(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        BlockPos spawn = overworld.getSpawnPoint().getPos();
        return new Vec3d(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
    }

    public static void onPlayerJoin(ServerPlayerEntity p) {
        if (!SharedRunState.preGame) return;
        if (!p.isSpectator() && p.interactionManager.getGameMode() != GameMode.CREATIVE) {
            p.changeGameMode(GameMode.ADVENTURE);
            fillHunger(p);
        }
        MinecraftServer server = p.getEntityWorld().getServer();
        if (server == null) return;
        applyLobbyBorder(server);
        if (p.getEntityWorld() != server.getOverworld()) {
            Vec3d center = getLobbyCenter(server);
            TeleportTarget target = new TeleportTarget(
                    server.getOverworld(),
                    center,
                    Vec3d.ZERO,
                    p.getYaw(), p.getPitch(),
                    TeleportTarget.NO_OP
            );
            p.teleportTo(target);
        }
    }

    public static void tick(MinecraftServer server) {
        if (!SharedRunState.preGame) return;
        if (server.getTicks() % 2 != 0) return;

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            if (p.interactionManager.getGameMode() == GameMode.CREATIVE) continue;
            fillHunger(p);
        }
    }

    public static void gatherAll(MinecraftServer server) {
        Vec3d center = getLobbyCenter(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            TeleportTarget target = new TeleportTarget(
                    server.getOverworld(),
                    center,
                    Vec3d.ZERO,
                    p.getYaw(), p.getPitch(),
                    TeleportTarget.NO_OP
            );
            p.teleportTo(target);
        }
    }

    public static void applyLobbyBorder(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        WorldBorder border = overworld.getWorldBorder();
        Vec3d center = getLobbyCenter(server);
        double size = SharedRunState.pregameRadius * 2.0;
        if (border.getCenterX() != center.x || border.getCenterZ() != center.z) {
            border.setCenter(center.x, center.z);
        }
        if (Math.abs(border.getSize() - size) > 0.01) {
            border.setSize(size);
        }

        overworld.getGameRules().setValue(GameRules.ADVANCE_TIME, false, server);
        overworld.setTimeOfDay(6000L);
    }

    public static void restoreBorder(MinecraftServer server) {
        WorldBorder border = server.getOverworld().getWorldBorder();
        if (Math.abs(border.getSize() - VANILLA_BORDER_SIZE) > 0.01) {
            border.setSize(VANILLA_BORDER_SIZE);
        }
    }

    private static void fillHunger(ServerPlayerEntity p) {
        HungerManager hm = p.getHungerManager();
        if (hm.getFoodLevel() != 20) hm.setFoodLevel(20);
        if (hm.getSaturationLevel() < 20f) hm.setSaturationLevel(20f);
    }
}
