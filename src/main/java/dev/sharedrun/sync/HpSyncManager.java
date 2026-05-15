package dev.sharedrun.sync;

import dev.sharedrun.state.SharedRunState;
import dev.sharedrun.stats.HeartsCausedTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HpSyncManager {
    private static final Map<UUID, Float> prevHealth = new HashMap<>();
    private static boolean reentrant = false;

    private HpSyncManager() {}

    public static void tick(MinecraftServer server) {
        if (!SharedRunState.hpSyncEnabled) return;
        if (reentrant) return;

        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        if (!SharedRunState.sharedHealthInitialized) {
            float maxHp = players.get(0).getMaxHealth();
            SharedRunState.sharedHealth = maxHp;
            SharedRunState.sharedHealthInitialized = true;
        }

        float maxPosDelta = 0f;
        float minNegDelta = 0f;
        ServerPlayerEntity damagedBy = null;
        boolean anyAlive = false;

        for (ServerPlayerEntity p : players) {
            if (p.isDead() || p.isSpectator()) continue;
            anyAlive = true;
            float current = p.getHealth();
            Float prev = prevHealth.get(p.getUuid());
            if (prev == null) {
                prevHealth.put(p.getUuid(), current);
                continue;
            }
            float delta = current - prev;
            if (delta > maxPosDelta) maxPosDelta = delta;
            if (delta < minNegDelta) {
                minNegDelta = delta;
                damagedBy = p;
            }
        }

        if (!anyAlive) return;

        if (damagedBy != null && minNegDelta < -0.01f) {
            float lostHearts = -minNegDelta / 2f;
            HeartsCausedTracker.addHpDamage(damagedBy.getUuid(), damagedBy.getGameProfile().name(), -minNegDelta);
            Text msg = Text.translatable("sharedrun.damage.notify",
                            damagedBy.getDisplayName(),
                            String.format("%.1f", lostHearts))
                    .formatted(Formatting.RED);
            for (ServerPlayerEntity p : players) {
                if (p.isSpectator()) continue;
                p.sendMessageToClient(msg, true);
            }
        }

        float maxHp = players.get(0).getMaxHealth();
        SharedRunState.sharedHealth = clamp(SharedRunState.sharedHealth + maxPosDelta + minNegDelta, 0f, maxHp);

        reentrant = true;
        try {
            for (ServerPlayerEntity p : players) {
                if (p.isSpectator()) continue;
                if (p.isDead()) {
                    prevHealth.remove(p.getUuid());
                    continue;
                }
                float target = SharedRunState.sharedHealth;
                if (Math.abs(p.getHealth() - target) > 0.001f) {
                    p.setHealth(target);
                }
                prevHealth.put(p.getUuid(), target);
            }
        } finally {
            reentrant = false;
        }
    }

    public static void onPlayerJoin(ServerPlayerEntity player) {
        if (!SharedRunState.hpSyncEnabled) return;
        if (!SharedRunState.sharedHealthInitialized) {
            SharedRunState.sharedHealth = player.getMaxHealth();
            SharedRunState.sharedHealthInitialized = true;
        }
        player.setHealth(SharedRunState.sharedHealth);
        prevHealth.put(player.getUuid(), SharedRunState.sharedHealth);
    }

    public static void resyncAll(MinecraftServer server, float value) {
        SharedRunState.sharedHealth = value;
        SharedRunState.sharedHealthInitialized = true;
        reentrant = true;
        try {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isSpectator() || p.isDead()) continue;
                p.setHealth(value);
                prevHealth.put(p.getUuid(), value);
            }
        } finally {
            reentrant = false;
        }
    }

    public static void clearTracking() {
        prevHealth.clear();
    }

    public static void onPlayerDisconnect(UUID uuid) {
        prevHealth.remove(uuid);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
