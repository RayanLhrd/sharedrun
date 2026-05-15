package dev.sharedrun.timer;

import dev.sharedrun.achievement.AchievementTracker;
import dev.sharedrun.endrun.EndRunHandler;
import dev.sharedrun.lobby.LobbyManager;
import dev.sharedrun.network.TimerSyncPayload;
import dev.sharedrun.progress.ProgressTracker;
import dev.sharedrun.state.SharedRunState;
import dev.sharedrun.stats.HeartsCausedTracker;
import dev.sharedrun.sync.EffectsSyncManager;
import dev.sharedrun.sync.HpSyncManager;
import dev.sharedrun.sync.HungerSyncManager;
import net.minecraft.entity.player.HungerManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import net.minecraft.world.rule.GameRules;

public final class GameTimer {
    private static final int[] CHECKPOINT_SECONDS = { 1800, 900, 300, 60 };

    /**
     * Délai avant de déclencher la cinematic de victoire (en ticks).
     * Laisse l'animation d'explosion vanilla du dragon (beams + XP orbs) se jouer en entier.
     * Le dragon vanilla joue sa death animation pendant 200 ticks (10s).
     */
    public static final int VICTORY_TRIGGER_DELAY_TICKS = 200;

    /** Si > 0, à ce tick on appelle triggerVictory (programmé par scheduleVictoryTrigger). */
    private static volatile long pendingVictoryTriggerTick = -1;

    private GameTimer() {}

    /**
     * Programme le déclenchement de la cinematic de victoire pour dans `delayTicks` ticks.
     * Idempotent : si déjà programmé, ne fait rien.
     */
    public static void scheduleVictoryTrigger(MinecraftServer server, int delayTicks) {
        if (pendingVictoryTriggerTick > 0) return;
        if (SharedRunState.victoryTriggered) return;
        pendingVictoryTriggerTick = server.getOverworld().getTime() + delayTicks;
    }

    public static void tick(MinecraftServer server) {
        // Pending victory trigger (après animation d'explosion du dragon)
        if (pendingVictoryTriggerTick > 0
                && server.getOverworld().getTime() >= pendingVictoryTriggerTick) {
            pendingVictoryTriggerTick = -1;
            triggerVictory(server);
            return;
        }

        if (SharedRunState.startCountdownTicks > 0) {
            tickStartCountdown(server);
            if (server.getTicks() % 10 == 0) broadcast(server);
            return;
        }

        if (SharedRunState.victoryTriggered) {
            if (server.getTicks() % 10 == 0) broadcast(server);
            return;
        }

        if (SharedRunState.timerRunning && SharedRunState.remainingTicks > 0) {
            SharedRunState.remainingTicks--;
            checkCheckpoints(server);
            if (SharedRunState.remainingTicks <= 0) {
                SharedRunState.remainingTicks = 0;
                SharedRunState.timerRunning = false;
                triggerEnd(server);
            }
        }

        if (server.getTicks() % 10 == 0) {
            broadcast(server);
        }
    }

    private static void checkCheckpoints(MinecraftServer server) {
        int remainingSeconds = SharedRunState.remainingTicks / 20;
        for (int i = 0; i < CHECKPOINT_SECONDS.length; i++) {
            int bit = 1 << i;
            if ((SharedRunState.checkpointMask & bit) != 0) continue;
            if (remainingSeconds <= CHECKPOINT_SECONDS[i]) {
                SharedRunState.checkpointMask |= bit;
                announceCheckpoint(server, CHECKPOINT_SECONDS[i]);
            }
        }
    }

    public static void seedCheckpointsFromRemaining() {
        int remainingSeconds = SharedRunState.remainingTicks / 20;
        int mask = 0;
        for (int i = 0; i < CHECKPOINT_SECONDS.length; i++) {
            if (remainingSeconds <= CHECKPOINT_SECONDS[i]) mask |= (1 << i);
        }
        SharedRunState.checkpointMask = Math.max(SharedRunState.checkpointMask, mask);
    }

    private static void announceCheckpoint(MinecraftServer server, int seconds) {
        String key;
        Formatting color;
        float pitch;
        if (seconds >= 1800) { key = "sharedrun.checkpoint.30min"; color = Formatting.YELLOW; pitch = 1.0f; }
        else if (seconds >= 900) { key = "sharedrun.checkpoint.15min"; color = Formatting.GOLD; pitch = 1.1f; }
        else if (seconds >= 300) { key = "sharedrun.checkpoint.5min"; color = Formatting.RED; pitch = 1.3f; }
        else { key = "sharedrun.checkpoint.1min"; color = Formatting.DARK_RED; pitch = 1.6f; }

        Text title = Text.translatable(key).formatted(color, Formatting.BOLD);
        Text subtitle = Text.translatable("sharedrun.checkpoint.subtitle").formatted(Formatting.WHITE);
        Text chat = Text.literal("⏰ ").append(Text.translatable(key)).formatted(color, Formatting.BOLD);
        TitleS2CPacket titlePacket = new TitleS2CPacket(title);
        net.minecraft.network.packet.s2c.play.SubtitleS2CPacket subtitlePacket =
                new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(subtitle);
        TitleFadeS2CPacket animPacket = new TitleFadeS2CPacket(8, 50, 12);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(animPacket);
            p.networkHandler.sendPacket(subtitlePacket);
            p.networkHandler.sendPacket(titlePacket);
            p.sendMessage(chat, false);
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.MASTER, 0.8f, pitch);
        }
    }

    public static void triggerVictory(MinecraftServer server) {
        if (SharedRunState.victoryTriggered) return;
        if (SharedRunState.runEnded) return; // fix race dragon-kill vs timer-0
        SharedRunState.victoryTriggered = true;
        SharedRunState.timerRunning = false;
        SharedRunState.hpSyncEnabled = false;
        HpSyncManager.clearTracking();

        // Le Title vanilla et les sons sont retirés : la VictoryCinematicScreen (côté client)
        // prend complètement la main visuelle/audio dès l'ouverture du RunSummaryPayload.
        // Garder ces éléments créait un double-affichage et un conflit audio.

        broadcast(server);
        EndRunHandler.endRun(server, EndRunHandler.EndReason.VICTORY);
    }

    private static String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    public static void broadcast(MinecraftServer server) {
        int remainingSeconds = (SharedRunState.remainingTicks + 19) / 20;
        int totalSeconds = SharedRunState.totalSeconds;
        boolean running = SharedRunState.timerRunning;
        TimerSyncPayload payload = new TimerSyncPayload(remainingSeconds, totalSeconds, running);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void setSeconds(int seconds) {
        SharedRunState.totalSeconds = seconds;
        SharedRunState.remainingTicks = seconds * 20;
        SharedRunState.swapActivationNotified = false;
        SharedRunState.nextSwapTick = -1;
        SharedRunState.swapCountdownTicks = -1;
        SharedRunState.checkpointMask = 0;
        SharedRunState.victoryTriggered = false;
    }

    public static void start(MinecraftServer server) {
        SharedRunState.remainingTicks = SharedRunState.totalSeconds * 20;
        SharedRunState.victoryTriggered = false;
        SharedRunState.checkpointMask = 0;
        SharedRunState.swapActivationNotified = false;
        SharedRunState.nextSwapTick = -1;
        SharedRunState.swapCountdownTicks = -1;
        SharedRunState.sharedHealthInitialized = false;
        SharedRunState.hungerInitialized = false;
        SharedRunState.timerRunning = false;
        SharedRunState.preGame = true;
        SharedRunState.startCountdownTicks = 100;
        SharedRunState.hpSyncEnabled = true;
        SharedRunState.effectsSyncEnabled = true;

        HeartsCausedTracker.resetAll();
        HpSyncManager.clearTracking();
        HungerSyncManager.clearTracking();
        EffectsSyncManager.clearTracking();
        ProgressTracker.reset();
        AchievementTracker.reset();
        dev.sharedrun.stats.FoodEatTracker.reset();
        SharedRunState.swapCount = 0;
        SharedRunState.runEnded = false;
        SharedRunState.strongholdPos = Long.MIN_VALUE;
        SharedRunState.netherEntryPos = Long.MIN_VALUE;
        pendingVictoryTriggerTick = -1; // reset le pending victory pour une nouvelle run
        SharedRunState.endEntryPos = Long.MIN_VALUE;
        EndRunHandler.lastSummaryJson = null;

        // Rapatrie tous les joueurs au lobby (peu importe leur dimension/gamemode)
        LobbyManager.gatherAll(server);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.removeStatusEffect(StatusEffects.SLOWNESS);
            p.removeStatusEffect(StatusEffects.MINING_FATIGUE);
            p.removeStatusEffect(StatusEffects.WEAKNESS);
            p.removeStatusEffect(StatusEffects.JUMP_BOOST);
            // Forcer adventure pour tout le monde sauf creative (inclut les spectators ex-debrief)
            if (p.interactionManager.getGameMode() != GameMode.CREATIVE) {
                p.changeGameMode(GameMode.ADVENTURE);
            }
            p.setHealth(p.getMaxHealth());
            HungerManager hm = p.getHungerManager();
            hm.setFoodLevel(20);
            hm.setSaturationLevel(5f);
        }

        ServerWorld overworld = server.getOverworld();
        overworld.getGameRules().setValue(GameRules.ADVANCE_TIME, false, server);
        overworld.getGameRules().setValue(GameRules.SHOW_DEATH_MESSAGES, false, server);
        overworld.setTimeOfDay(6000L);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, true, false, false));
        }

        LobbyManager.applyLobbyBorder(server);
        announceCountdownStart(server);
    }

    private static void announceCountdownStart(MinecraftServer server) {
        Text chat = Text.translatable("sharedrun.start.countdown.chat").formatted(Formatting.GREEN, Formatting.BOLD);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(chat, false);
        }
    }

    private static void tickStartCountdown(MinecraftServer server) {
        int before = SharedRunState.startCountdownTicks;
        SharedRunState.startCountdownTicks--;

        if (before % 20 == 0 && before > 0) {
            int secondsLeft = before / 20;
            broadcastCountdownNumber(server, secondsLeft);
        }

        if (before == 50) {
            server.getOverworld().setTimeOfDay(0L);
        }

        if (SharedRunState.startCountdownTicks == 0) {
            ServerWorld overworld = server.getOverworld();
            overworld.getGameRules().setValue(GameRules.ADVANCE_TIME, true, server);

            broadcastGo(server);
            SharedRunState.preGame = false;
            SharedRunState.timerRunning = true;
            LobbyManager.restoreBorder(server);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.removeStatusEffect(StatusEffects.DARKNESS);
                p.removeStatusEffect(StatusEffects.BLINDNESS);
                if (p.isSpectator()) continue;
                if (p.interactionManager.getGameMode() == GameMode.ADVENTURE) {
                    p.changeGameMode(GameMode.SURVIVAL);
                }
            }
        }
    }

    private static void broadcastCountdownNumber(MinecraftServer server, int n) {
        Formatting color = n <= 1 ? Formatting.DARK_RED : (n <= 2 ? Formatting.RED : (n <= 3 ? Formatting.GOLD : Formatting.YELLOW));
        Text title = Text.literal(String.valueOf(n)).formatted(color, Formatting.BOLD);
        TitleS2CPacket titlePacket = new TitleS2CPacket(title);
        TitleFadeS2CPacket animPacket = new TitleFadeS2CPacket(2, 18, 4);
        float pitch = 0.7f + (5 - n) * 0.15f;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(animPacket);
            p.networkHandler.sendPacket(titlePacket);
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.MASTER, 1.0f, pitch);
        }
    }

    private static void broadcastGo(MinecraftServer server) {
        Text title = Text.translatable("sharedrun.start.go.title").formatted(Formatting.GREEN, Formatting.BOLD);
        Text subtitle = Text.translatable("sharedrun.start.go.subtitle").formatted(Formatting.WHITE);
        Text chat = Text.translatable("sharedrun.start.go.chat").formatted(Formatting.GREEN, Formatting.BOLD);
        TitleS2CPacket titlePacket = new TitleS2CPacket(title);
        net.minecraft.network.packet.s2c.play.SubtitleS2CPacket subtitlePacket =
                new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(subtitle);
        TitleFadeS2CPacket animPacket = new TitleFadeS2CPacket(4, 40, 12);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(animPacket);
            p.networkHandler.sendPacket(subtitlePacket);
            p.networkHandler.sendPacket(titlePacket);
            p.sendMessage(chat, false);
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.8f, 1.3f);
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 0.4f, 1.6f);
        }
    }

    public static void pause() {
        SharedRunState.timerRunning = false;
    }

    public static void resume() {
        if (SharedRunState.remainingTicks > 0) {
            SharedRunState.timerRunning = true;
        }
    }

    public static void stop() {
        SharedRunState.timerRunning = false;
        SharedRunState.remainingTicks = 0;
    }

    public static void reset() {
        SharedRunState.remainingTicks = SharedRunState.totalSeconds * 20;
        SharedRunState.timerRunning = false;
        SharedRunState.swapActivationNotified = false;
        SharedRunState.nextSwapTick = -1;
        SharedRunState.swapCountdownTicks = -1;
        SharedRunState.checkpointMask = 0;
        SharedRunState.victoryTriggered = false;
    }

    private static void triggerEnd(MinecraftServer server) {
        Text title = Text.translatable("sharedrun.gameover").formatted(Formatting.RED, Formatting.BOLD);
        TitleS2CPacket titlePacket = new TitleS2CPacket(title);
        TitleFadeS2CPacket animPacket = new TitleFadeS2CPacket(10, 80, 20);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(animPacket);
            p.networkHandler.sendPacket(titlePacket);
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.MASTER, 0.6f, 1.0f);
        }

        broadcast(server);
        EndRunHandler.endRun(server, EndRunHandler.EndReason.TIME_OUT);
    }
}
