package dev.sharedrun.sync;

import dev.sharedrun.state.SharedRunState;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EffectsSyncManager {
    private static final Map<UUID, Map<RegistryEntry<StatusEffect>, Integer>> prevEffects = new HashMap<>();
    private static boolean reentrant = false;

    private EffectsSyncManager() {}

    public static void tick(MinecraftServer server) {
        if (!SharedRunState.effectsSyncEnabled) return;
        if (reentrant) return;

        var players = server.getPlayerManager().getPlayerList();
        if (players.size() < 2) return;
        if (server.getTicks() % 5 != 0) return;

        Set<UUID> alive = new HashSet<>();
        Map<RegistryEntry<StatusEffect>, StatusEffectInstance> toPropagate = new HashMap<>();

        for (ServerPlayerEntity p : players) {
            if (p.isDead() || p.isSpectator()) continue;
            alive.add(p.getUuid());
            Map<RegistryEntry<StatusEffect>, Integer> pPrev = prevEffects.getOrDefault(p.getUuid(), Map.of());
            for (StatusEffectInstance inst : p.getStatusEffects()) {
                var key = inst.getEffectType();
                Integer prevAmp = pPrev.get(key);
                boolean newlyAddedOrUpgraded = (prevAmp == null) || (inst.getAmplifier() > prevAmp);
                if (!newlyAddedOrUpgraded) continue;
                StatusEffectInstance existing = toPropagate.get(key);
                if (existing == null
                        || inst.getAmplifier() > existing.getAmplifier()
                        || (inst.getAmplifier() == existing.getAmplifier() && inst.getDuration() > existing.getDuration())) {
                    toPropagate.put(key, inst);
                }
            }
        }

        if (!toPropagate.isEmpty()) {
            reentrant = true;
            try {
                for (ServerPlayerEntity p : players) {
                    if (p.isDead() || p.isSpectator()) continue;
                    for (var entry : toPropagate.entrySet()) {
                        StatusEffectInstance target = entry.getValue();
                        StatusEffectInstance current = p.getStatusEffect(target.getEffectType());
                        if (current == null || target.getAmplifier() > current.getAmplifier()) {
                            p.addStatusEffect(new StatusEffectInstance(
                                    target.getEffectType(),
                                    target.getDuration(),
                                    target.getAmplifier(),
                                    target.isAmbient(),
                                    target.shouldShowParticles(),
                                    target.shouldShowIcon()
                            ));
                        }
                    }
                }
            } finally {
                reentrant = false;
            }
        }

        for (ServerPlayerEntity p : players) {
            if (p.isDead() || p.isSpectator()) continue;
            Map<RegistryEntry<StatusEffect>, Integer> snap = new HashMap<>();
            for (StatusEffectInstance inst : p.getStatusEffects()) {
                snap.put(inst.getEffectType(), inst.getAmplifier());
            }
            prevEffects.put(p.getUuid(), snap);
        }
        prevEffects.keySet().retainAll(alive);
    }

    public static void clearTracking() {
        prevEffects.clear();
    }

    public static void onPlayerDisconnect(UUID uuid) {
        prevEffects.remove(uuid);
    }
}
