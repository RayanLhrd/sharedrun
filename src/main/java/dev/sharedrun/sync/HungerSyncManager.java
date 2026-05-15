package dev.sharedrun.sync;

import dev.sharedrun.state.SharedRunState;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HungerSyncManager {
    private static final Map<UUID, Integer> prevFood = new HashMap<>();
    private static final Map<UUID, Float> prevSat = new HashMap<>();
    private static boolean reentrant = false;

    private HungerSyncManager() {}

    public static void tick(MinecraftServer server) {
        if (!SharedRunState.hungerSyncEnabled) return;
        if (reentrant) return;

        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        if (!SharedRunState.hungerInitialized) {
            HungerManager hm = players.get(0).getHungerManager();
            SharedRunState.sharedFoodLevel = hm.getFoodLevel();
            SharedRunState.sharedSaturation = hm.getSaturationLevel();
            SharedRunState.hungerInitialized = true;
        }

        int maxPosFood = 0, minNegFood = 0;
        float maxPosSat = 0f, minNegSat = 0f;
        boolean anyAlive = false;

        for (ServerPlayerEntity p : players) {
            if (p.isDead() || p.isSpectator()) continue;
            anyAlive = true;
            HungerManager hm = p.getHungerManager();
            int curFood = hm.getFoodLevel();
            float curSat = hm.getSaturationLevel();
            Integer pf = prevFood.get(p.getUuid());
            Float ps = prevSat.get(p.getUuid());
            if (pf == null || ps == null) {
                prevFood.put(p.getUuid(), curFood);
                prevSat.put(p.getUuid(), curSat);
                continue;
            }
            int df = curFood - pf;
            float ds = curSat - ps;
            if (df > maxPosFood) maxPosFood = df;
            if (df < minNegFood) minNegFood = df;
            if (ds > maxPosSat) maxPosSat = ds;
            if (ds < minNegSat) minNegSat = ds;
        }

        if (!anyAlive) return;

        SharedRunState.sharedFoodLevel = clamp(SharedRunState.sharedFoodLevel + maxPosFood + minNegFood, 0, 20);
        SharedRunState.sharedSaturation = clampF(SharedRunState.sharedSaturation + maxPosSat + minNegSat, 0f, (float) SharedRunState.sharedFoodLevel);

        reentrant = true;
        try {
            for (ServerPlayerEntity p : players) {
                if (p.isSpectator() || p.isDead()) continue;
                HungerManager hm = p.getHungerManager();
                if (hm.getFoodLevel() != SharedRunState.sharedFoodLevel) {
                    hm.setFoodLevel(SharedRunState.sharedFoodLevel);
                }
                if (Math.abs(hm.getSaturationLevel() - SharedRunState.sharedSaturation) > 0.01f) {
                    hm.setSaturationLevel(SharedRunState.sharedSaturation);
                }
                prevFood.put(p.getUuid(), SharedRunState.sharedFoodLevel);
                prevSat.put(p.getUuid(), SharedRunState.sharedSaturation);
            }
        } finally {
            reentrant = false;
        }
    }

    public static void onPlayerJoin(ServerPlayerEntity player) {
        if (!SharedRunState.hungerSyncEnabled) return;
        if (!SharedRunState.hungerInitialized) {
            HungerManager hm = player.getHungerManager();
            SharedRunState.sharedFoodLevel = hm.getFoodLevel();
            SharedRunState.sharedSaturation = hm.getSaturationLevel();
            SharedRunState.hungerInitialized = true;
        }
        HungerManager hm = player.getHungerManager();
        hm.setFoodLevel(SharedRunState.sharedFoodLevel);
        hm.setSaturationLevel(SharedRunState.sharedSaturation);
        prevFood.put(player.getUuid(), SharedRunState.sharedFoodLevel);
        prevSat.put(player.getUuid(), SharedRunState.sharedSaturation);
    }

    public static void clearTracking() {
        prevFood.clear();
        prevSat.clear();
    }

    public static void onPlayerDisconnect(UUID uuid) {
        prevFood.remove(uuid);
        prevSat.remove(uuid);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clampF(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
