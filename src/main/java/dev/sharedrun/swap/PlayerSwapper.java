package dev.sharedrun.swap;

import dev.sharedrun.state.SharedRunState;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PlayerSwapper {
    private static final Random RNG = new Random();

    private PlayerSwapper() {}

    public static void tick(MinecraftServer server) {
        if (!SharedRunState.swapEnabled) return;

        boolean nowInEnd = SharedRunState.anyPlayerWasInEnd;
        if (server.getTicks() % 10 == 0) {
            nowInEnd = anyPlayerInEnd(server);
            if (nowInEnd != SharedRunState.anyPlayerWasInEnd) {
                SharedRunState.anyPlayerWasInEnd = nowInEnd;
                if (nowInEnd) {
                    announceEndEntered(server);
                    SharedRunState.swapActivationNotified = true;
                    if (SharedRunState.timerRunning) {
                        SharedRunState.swapCountdownTicks = -1;
                        scheduleNext(server.getTicks(), server);
                    }
                } else {
                    announceEndLeft(server);
                    if (SharedRunState.timerRunning) {
                        SharedRunState.swapCountdownTicks = -1;
                        scheduleNext(server.getTicks(), server);
                    }
                }
            }
        }

        int activationTicks = SharedRunState.swapActivationRemainingSeconds * 20;
        boolean activeWindow = SharedRunState.timerRunning
                && SharedRunState.remainingTicks > 0
                && (SharedRunState.remainingTicks <= activationTicks || nowInEnd);

        if (!activeWindow) {
            if (SharedRunState.swapCountdownTicks >= 0) {
                SharedRunState.swapCountdownTicks = -1;
            }
            return;
        }

        long now = server.getTicks();

        if (!SharedRunState.swapActivationNotified) {
            SharedRunState.swapActivationNotified = true;
            announceActivation(server);
            scheduleNext(now, server);
            return;
        }

        if (SharedRunState.swapCountdownTicks > 0) {
            SharedRunState.swapCountdownTicks--;
            int phase = SharedRunState.swapCountdownTicks;
            if (phase == 40 || phase == 20 || phase == 0) {
                int n = phase == 40 ? 3 : (phase == 20 ? 2 : 1);
                broadcastCountdownTitle(server, n);
            }
            if (phase == 0) {
                doSwap(server);
                scheduleNext(now, server);
                SharedRunState.swapCountdownTicks = -1;
            }
            return;
        }

        if (SharedRunState.nextSwapTick < 0) {
            scheduleNext(now, server);
            return;
        }
        if (now < SharedRunState.nextSwapTick) return;

        SharedRunState.swapCountdownTicks = 60;
    }

    private static void announceActivation(MinecraftServer server) {
        Text title = Text.translatable("sharedrun.swap.active.title").formatted(Formatting.GOLD, Formatting.BOLD);
        Text subtitle = Text.translatable("sharedrun.swap.active.subtitle").formatted(Formatting.YELLOW);
        Text chat = Text.translatable("sharedrun.swap.active.chat").formatted(Formatting.GOLD, Formatting.BOLD);
        TitleS2CPacket titlePacket = new TitleS2CPacket(title);
        SubtitleS2CPacket subtitlePacket = new SubtitleS2CPacket(subtitle);
        TitleFadeS2CPacket animPacket = new TitleFadeS2CPacket(10, 70, 20);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(animPacket);
            p.networkHandler.sendPacket(subtitlePacket);
            p.networkHandler.sendPacket(titlePacket);
            p.sendMessage(chat, false);
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 0.35f, 1.6f);
        }
    }

    private static void announceEndEntered(MinecraftServer server) {
        Text title = Text.translatable("sharedrun.end.entered.title").formatted(Formatting.DARK_PURPLE, Formatting.BOLD);
        Text subtitle = Text.translatable("sharedrun.end.entered.subtitle").formatted(Formatting.LIGHT_PURPLE);
        Text chat = Text.translatable("sharedrun.end.entered.chat").formatted(Formatting.DARK_PURPLE, Formatting.BOLD);
        TitleS2CPacket titlePacket = new TitleS2CPacket(title);
        SubtitleS2CPacket subtitlePacket = new SubtitleS2CPacket(subtitle);
        TitleFadeS2CPacket animPacket = new TitleFadeS2CPacket(10, 80, 20);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(animPacket);
            p.networkHandler.sendPacket(subtitlePacket);
            p.networkHandler.sendPacket(titlePacket);
            p.sendMessage(chat, false);
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 0.6f, 1.3f);
        }
    }

    private static void announceEndLeft(MinecraftServer server) {
        Text title = Text.translatable("sharedrun.end.left.title").formatted(Formatting.GRAY);
        Text chat = Text.translatable("sharedrun.end.left.chat").formatted(Formatting.GRAY);
        TitleS2CPacket titlePacket = new TitleS2CPacket(title);
        TitleFadeS2CPacket animPacket = new TitleFadeS2CPacket(8, 40, 12);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(animPacket);
            p.networkHandler.sendPacket(titlePacket);
            p.sendMessage(chat, false);
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 0.3f, 1.5f);
        }
    }

    private static void broadcastCountdownTitle(MinecraftServer server, int n) {
        Text title = Text.literal(String.valueOf(n))
                .formatted(Formatting.DARK_RED, Formatting.BOLD);
        TitleS2CPacket titlePacket = new TitleS2CPacket(title);
        TitleFadeS2CPacket animPacket = new TitleFadeS2CPacket(2, 16, 4);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(animPacket);
            p.networkHandler.sendPacket(titlePacket);
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.MASTER, 1.0f, 0.5f);
        }
    }

    public static void scheduleNext(long now) {
        scheduleNext(now, null);
    }

    public static void scheduleNext(long now, net.minecraft.server.MinecraftServer server) {
        int[] range = computeCurrentRangeSeconds(server);
        int minTicks = range[0] * 20;
        int maxTicks = range[1] * 20;
        int span = Math.max(20, maxTicks - minTicks);
        SharedRunState.nextSwapTick = now + minTicks + RNG.nextInt(span);
    }

    public static int[] computeCurrentRangeSeconds(net.minecraft.server.MinecraftServer server) {
        if (SharedRunState.swapIntervalOverride) {
            int min = SharedRunState.swapMinMinutes * 60;
            int max = Math.max(min, SharedRunState.swapMaxMinutes * 60);
            return new int[] { min, max };
        }
        if (server != null && anyPlayerInEnd(server)) {
            return new int[] { 30, 60 };
        }
        int activationSec = Math.max(1, SharedRunState.swapActivationRemainingSeconds);
        int remainingSec = SharedRunState.remainingTicks / 20;
        double progress = (double) remainingSec / (double) activationSec;
        if (progress < 0.0) progress = 0.0;
        if (progress > 1.0) progress = 1.0;
        int minSec = (int) Math.round(120 + 480 * progress);
        int maxSec = (int) Math.round(180 + 720 * progress);
        return new int[] { minSec, maxSec };
    }

    private static boolean anyPlayerInEnd(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            if (((ServerWorld) p.getEntityWorld()).getRegistryKey() == World.END) return true;
        }
        return false;
    }

    public static void doSwap(MinecraftServer server) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.isDead()) continue;
            players.add(p);
        }
        if (players.size() < 2) return;
        SharedRunState.swapCount++;

        record Snap(ServerWorld world, Vec3d pos, float yaw, float pitch) {}
        List<Snap> snaps = new ArrayList<>();
        for (ServerPlayerEntity p : players) {
            snaps.add(new Snap(
                    (ServerWorld) p.getEntityWorld(),
                    new Vec3d(p.getX(), p.getY(), p.getZ()),
                    p.getYaw(), p.getPitch()
            ));
        }

        int[] perm = derangement(players.size());

        Text title = Text.literal("TÉLÉPORTATION").formatted(Formatting.AQUA, Formatting.BOLD);
        TitleS2CPacket titlePacket = new TitleS2CPacket(title);
        TitleFadeS2CPacket animPacket = new TitleFadeS2CPacket(2, 30, 10);

        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity p = players.get(i);
            Snap dest = snaps.get(perm[i]);

            TeleportTarget target = new TeleportTarget(
                    dest.world(), dest.pos(), Vec3d.ZERO,
                    dest.yaw(), dest.pitch(),
                    TeleportTarget.NO_OP
            );
            p.teleportTo(target);
            p.networkHandler.sendPacket(animPacket);
            p.networkHandler.sendPacket(titlePacket);
            p.getEntityWorld().playSound(null, dest.pos().x, dest.pos().y, dest.pos().z,
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    private static int[] derangement(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        for (int attempt = 0; attempt < 50; attempt++) {
            shuffle(a);
            boolean ok = true;
            for (int i = 0; i < n; i++) if (a[i] == i) { ok = false; break; }
            if (ok) return a;
        }
        for (int i = 0; i < n - 1; i += 2) {
            int tmp = a[i]; a[i] = a[i + 1]; a[i + 1] = tmp;
        }
        if (n % 2 == 1 && n >= 3) {
            int tmp = a[n - 1]; a[n - 1] = a[n - 2]; a[n - 2] = tmp;
        }
        return a;
    }

    private static void shuffle(int[] a) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            int tmp = a[i]; a[i] = a[j]; a[j] = tmp;
        }
    }

    public static int secondsUntilNextSwap(MinecraftServer server) {
        if (SharedRunState.nextSwapTick < 0) return -1;
        long diff = SharedRunState.nextSwapTick - server.getTicks();
        if (diff <= 0) return 0;
        return (int) (diff / 20);
    }

    public static int secondsUntilActivation() {
        int diff = SharedRunState.remainingTicks - SharedRunState.swapActivationRemainingSeconds * 20;
        if (diff <= 0) return 0;
        return (diff + 19) / 20;
    }
}
