package dev.sharedrun;

import dev.sharedrun.achievement.AchievementTracker;
import dev.sharedrun.command.SharedRunCommands;
import dev.sharedrun.endrun.EndRunHandler;
import dev.sharedrun.lobby.LobbyManager;
import dev.sharedrun.network.DebriefTpPayload;
import dev.sharedrun.network.ProgressSyncPayload;
import dev.sharedrun.network.RunSummaryPayload;
import dev.sharedrun.network.TimerSyncPayload;
import dev.sharedrun.progress.ProgressTracker;
import dev.sharedrun.sound.SharedRunSounds;
import dev.sharedrun.state.SharedRunPersistence;
import dev.sharedrun.state.SharedRunState;
import dev.sharedrun.stats.HeartsCausedTracker;
import dev.sharedrun.sync.EffectsSyncManager;
import dev.sharedrun.sync.HpSyncManager;
import dev.sharedrun.sync.HungerSyncManager;
import dev.sharedrun.swap.PlayerSwapper;
import dev.sharedrun.timer.GameTimer;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.rule.GameRules;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SharedRun implements ModInitializer {
    public static final String MOD_ID = "sharedrun";
    private static final int HURT_BROADCAST_COOLDOWN_TICKS = 10;
    private static final Map<UUID, Long> lastHurtBroadcastTick = new HashMap<>();
    private static final Map<UUID, Long> lastKnockbackTick = new HashMap<>();

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        dev.sharedrun.api.ApiConfig.load();
        SharedRunSounds.init(); // Register custom sound events
        PayloadTypeRegistry.playS2C().register(TimerSyncPayload.TYPE, TimerSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ProgressSyncPayload.TYPE, ProgressSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RunSummaryPayload.TYPE, RunSummaryPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DebriefTpPayload.TYPE, DebriefTpPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(DebriefTpPayload.TYPE, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            int dest = payload.destination();
            ctx.server().execute(() -> EndRunHandler.handleDebriefTp(ctx.server(), player, dest));
        });

        Placeholders.register(id("hearts_caused"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player");
            float h = HeartsCausedTracker.getHearts(ctx.player().getUuid());
            return PlaceholderResult.value(net.minecraft.text.Text.literal(String.format("%.1f", h)));
        });

        ServerTickEvents.END_SERVER_TICK.register(HpSyncManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(HungerSyncManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(EffectsSyncManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(GameTimer::tick);
        ServerTickEvents.END_SERVER_TICK.register(PlayerSwapper::tick);
        ServerTickEvents.END_SERVER_TICK.register(LobbyManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(ProgressTracker::tick);
        ServerTickEvents.END_SERVER_TICK.register(AchievementTracker::tick);
        ServerTickEvents.END_SERVER_TICK.register(EndRunHandler::tick);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SharedRunPersistence.load(server);
            GameTimer.seedCheckpointsFromRemaining();
            if (SharedRunState.preGame) {
                LobbyManager.applyLobbyBorder(server);
            } else {
                LobbyManager.restoreBorder(server);
            }
            server.getOverworld().getGameRules().setValue(GameRules.ANNOUNCE_ADVANCEMENTS, false, server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(SharedRunPersistence::save);
        ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> SharedRunPersistence.save(server));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            HpSyncManager.onPlayerJoin(handler.player);
            HungerSyncManager.onPlayerJoin(handler.player);
            LobbyManager.onPlayerJoin(handler.player);
            GameTimer.broadcast(server);
            ProgressTracker.broadcastTo(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUuid();
            lastHurtBroadcastTick.remove(uuid);
            lastKnockbackTick.remove(uuid);
            HpSyncManager.onPlayerDisconnect(uuid);
            HungerSyncManager.onPlayerDisconnect(uuid);
            dev.sharedrun.sync.EffectsSyncManager.onPlayerDisconnect(uuid);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (SharedRunState.preGame) {
                if (entity instanceof ServerPlayerEntity) return false;
                if (source.getAttacker() instanceof ServerPlayerEntity) return false;
            }
            return true;
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!SharedRunState.hpSyncEnabled) return;
            if (blocked) return;
            if (damageTaken <= 0f) return;
            if (!(entity instanceof ServerPlayerEntity damaged)) return;
            MinecraftServer server = damaged.getEntityWorld().getServer();
            if (server == null) return;

            long now = server.getOverworld().getTime();

            Long lastHurt = lastHurtBroadcastTick.get(damaged.getUuid());
            boolean broadcastHurt = lastHurt == null || now - lastHurt >= HURT_BROADCAST_COOLDOWN_TICKS;
            if (broadcastHurt) lastHurtBroadcastTick.put(damaged.getUuid(), now);

            boolean doKnockback = false;
            if (SharedRunState.knockbackEnabled) {
                Long lastKb = lastKnockbackTick.get(damaged.getUuid());
                if (lastKb == null || now - lastKb >= SharedRunState.knockbackCooldownTicks) {
                    doKnockback = true;
                    lastKnockbackTick.put(damaged.getUuid(), now);
                }
            }

            if (!broadcastHurt && !doKnockback) return;

            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                if (other == damaged) continue;
                if (other.isSpectator() || other.isDead()) continue;
                if (broadcastHurt) {
                    other.networkHandler.sendPacket(new EntityDamageS2CPacket(other, source));
                }
                if (doKnockback) {
                    double angle = Math.random() * Math.PI * 2.0;
                    double dirX = Math.cos(angle);
                    double dirZ = Math.sin(angle);
                    net.minecraft.util.math.Vec3d v = other.getVelocity();
                    double kbStrength = SharedRunState.knockbackHorizontal;
                    double kbVertical = SharedRunState.knockbackVertical;
                    double vy = other.isOnGround() ? kbVertical : Math.max(v.y, Math.min(0.1, kbVertical));
                    other.setVelocity(v.x - dirX * kbStrength, vy, v.z - dirZ * kbStrength);
                    other.velocityDirty = true;
                    other.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(other));
                }
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity p) {
                AchievementTracker.onPlayerDeath(p);
            }
            if (entity instanceof EnderDragonEntity) {
                ServerPlayerEntity killer = (source.getAttacker() instanceof ServerPlayerEntity sp) ? sp : null;
                ProgressTracker.onDragonKilled(killer);
                // Capture pour la VictoryCinematicScreen
                EndRunHandler.setPendingDragonKiller(killer != null ? killer.getName().getString() : null);
                if (!SharedRunState.victoryTriggered
                        && !SharedRunState.preGame
                        && SharedRunState.timerRunning) {
                    // On "pause" la run immédiatement pour 2 raisons :
                    // 1) Empêche la cascade ALL_DEAD si un joueur meurt durant les 10s
                    //    d'animation d'explosion du dragon (chute dans le void, etc.)
                    // 2) Évite que d'autres ticks fassent quoi que ce soit avec la run active
                    SharedRunState.timerRunning = false;
                    SharedRunState.hpSyncEnabled = false;
                    MinecraftServer s = entity.getEntityWorld().getServer();
                    if (s != null) {
                        // Délai = durée de l'animation vanilla d'explosion du dragon (200 ticks)
                        // → laisse les beams + XP orbs se jouer en entier avant la cinematic
                        GameTimer.scheduleVictoryTrigger(s, GameTimer.VICTORY_TRIGGER_DELAY_TICKS);
                    }
                }
            }
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            ProgressTracker.onDimensionChanged(player, destination);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!SharedRunState.hpSyncEnabled) return;
            if (!SharedRunState.timerRunning) return;
            if (SharedRunState.runEnded) return;
            if (!(entity instanceof ServerPlayerEntity dead)) return;
            MinecraftServer server = dead.getEntityWorld().getServer();
            if (server == null) return;
            net.minecraft.text.Text deathMsg = source.getDeathMessage(dead);
            String deadName = dead.getName().getString();
            server.execute(() -> EndRunHandler.endRun(server, EndRunHandler.EndReason.ALL_DEAD, deadName, deathMsg));
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                SharedRunCommands.register(dispatcher));
    }
}
