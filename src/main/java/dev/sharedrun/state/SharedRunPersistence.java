package dev.sharedrun.state;

import dev.sharedrun.achievement.AchievementTracker;
import dev.sharedrun.progress.ProgressTracker;
import dev.sharedrun.stats.HeartsCausedTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class SharedRunPersistence {
    private static final String FILE = "sharedrun_state.dat";

    private SharedRunPersistence() {}

    public static void load(MinecraftServer server) {
        Path path = server.getSavePath(WorldSavePath.ROOT).resolve(FILE);
        if (!Files.exists(path)) {
            // Nouveau monde : reset complet du state JVM-static (sinon il garde le state du monde précédent)
            SharedRunState.resetToDefaults();
            HeartsCausedTracker.resetAll();
            ProgressTracker.reset();
            AchievementTracker.reset();
            dev.sharedrun.stats.FoodEatTracker.reset();
            return;
        }
        try {
            NbtCompound nbt = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
            if (nbt != null) readState(nbt);
        } catch (IOException e) {
            System.err.println("[SharedRun] Load failed: " + e.getMessage());
        }
    }

    public static void save(MinecraftServer server) {
        Path path = server.getSavePath(WorldSavePath.ROOT).resolve(FILE);
        try {
            Files.createDirectories(path.getParent());
            NbtCompound nbt = new NbtCompound();
            writeState(nbt);
            NbtIo.writeCompressed(nbt, path);
        } catch (IOException e) {
            System.err.println("[SharedRun] Save failed: " + e.getMessage());
        }
    }

    private static void writeState(NbtCompound nbt) {
        nbt.putFloat("sharedHealth", SharedRunState.sharedHealth);
        nbt.putBoolean("sharedHealthInit", SharedRunState.sharedHealthInitialized);

        nbt.putInt("totalSeconds", SharedRunState.totalSeconds);
        nbt.putInt("remainingTicks", SharedRunState.remainingTicks);
        nbt.putBoolean("timerRunning", SharedRunState.timerRunning);

        nbt.putBoolean("hpSyncEnabled", SharedRunState.hpSyncEnabled);
        nbt.putBoolean("hungerSyncEnabled", SharedRunState.hungerSyncEnabled);
        nbt.putBoolean("effectsSyncEnabled", SharedRunState.effectsSyncEnabled);

        nbt.putInt("sharedFoodLevel", SharedRunState.sharedFoodLevel);
        nbt.putFloat("sharedSaturation", SharedRunState.sharedSaturation);
        nbt.putBoolean("hungerInit", SharedRunState.hungerInitialized);

        nbt.putBoolean("swapEnabled", SharedRunState.swapEnabled);
        nbt.putInt("swapMinMinutes", SharedRunState.swapMinMinutes);
        nbt.putInt("swapMaxMinutes", SharedRunState.swapMaxMinutes);
        nbt.putInt("swapActivationRemainingSeconds", SharedRunState.swapActivationRemainingSeconds);
        nbt.putBoolean("swapActivationNotified", SharedRunState.swapActivationNotified);

        nbt.putInt("checkpointMask", SharedRunState.checkpointMask);
        nbt.putBoolean("victoryTriggered", SharedRunState.victoryTriggered);
        nbt.putBoolean("preGame", SharedRunState.preGame);
        nbt.putDouble("pregameRadius", SharedRunState.pregameRadius);
        nbt.putBoolean("knockbackEnabled", SharedRunState.knockbackEnabled);
        nbt.putBoolean("swapIntervalOverride", SharedRunState.swapIntervalOverride);
        nbt.putBoolean("anyPlayerWasInEnd", SharedRunState.anyPlayerWasInEnd);
        nbt.putInt("swapCount", SharedRunState.swapCount);
        nbt.putBoolean("runEnded", SharedRunState.runEnded);
        nbt.putLong("strongholdPos", SharedRunState.strongholdPos);
        nbt.putLong("netherEntryPos", SharedRunState.netherEntryPos);
        nbt.putLong("endEntryPos", SharedRunState.endEntryPos);
        nbt.putLong("fortressPos", SharedRunState.fortressPos);
        nbt.putDouble("knockbackHorizontal", SharedRunState.knockbackHorizontal);
        nbt.putDouble("knockbackVertical", SharedRunState.knockbackVertical);
        nbt.putInt("knockbackCooldownTicks", SharedRunState.knockbackCooldownTicks);

        NbtList heartsList = new NbtList();
        for (HeartsCausedTracker.Entry e : HeartsCausedTracker.sortedLeaderboard()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("uuid", e.uuid().toString());
            entry.putString("name", e.name());
            entry.putFloat("hearts", e.hearts());
            heartsList.add(entry);
        }
        nbt.put("hearts", heartsList);

        ProgressTracker.writeNbt(nbt);
        AchievementTracker.writeNbt(nbt);
        dev.sharedrun.stats.FoodEatTracker.writeNbt(nbt);
    }

    private static void readState(NbtCompound nbt) {
        SharedRunState.sharedHealth = nbt.getFloat("sharedHealth").orElse(SharedRunState.DEFAULT_MAX_HEALTH);
        SharedRunState.sharedHealthInitialized = nbt.getBoolean("sharedHealthInit").orElse(false);

        SharedRunState.totalSeconds = nbt.getInt("totalSeconds").orElse(SharedRunState.DEFAULT_TIMER_SECONDS);
        SharedRunState.remainingTicks = nbt.getInt("remainingTicks").orElse(SharedRunState.DEFAULT_TIMER_SECONDS * 20);
        SharedRunState.timerRunning = nbt.getBoolean("timerRunning").orElse(true);

        SharedRunState.hpSyncEnabled = nbt.getBoolean("hpSyncEnabled").orElse(true);
        SharedRunState.hungerSyncEnabled = nbt.getBoolean("hungerSyncEnabled").orElse(false);
        SharedRunState.effectsSyncEnabled = nbt.getBoolean("effectsSyncEnabled").orElse(true);

        SharedRunState.sharedFoodLevel = nbt.getInt("sharedFoodLevel").orElse(20);
        SharedRunState.sharedSaturation = nbt.getFloat("sharedSaturation").orElse(5f);
        SharedRunState.hungerInitialized = nbt.getBoolean("hungerInit").orElse(false);

        SharedRunState.swapEnabled = nbt.getBoolean("swapEnabled").orElse(true);
        SharedRunState.swapMinMinutes = nbt.getInt("swapMinMinutes").orElse(4);
        SharedRunState.swapMaxMinutes = nbt.getInt("swapMaxMinutes").orElse(7);
        SharedRunState.swapActivationRemainingSeconds = nbt.getInt("swapActivationRemainingSeconds")
                .orElse(SharedRunState.DEFAULT_SWAP_ACTIVATION_REMAINING_SECONDS);
        SharedRunState.swapActivationNotified = nbt.getBoolean("swapActivationNotified").orElse(false);
        SharedRunState.nextSwapTick = -1;
        SharedRunState.swapCountdownTicks = -1;

        SharedRunState.checkpointMask = nbt.getInt("checkpointMask").orElse(0);
        SharedRunState.victoryTriggered = nbt.getBoolean("victoryTriggered").orElse(false);
        SharedRunState.preGame = nbt.getBoolean("preGame").orElse(true);
        SharedRunState.pregameRadius = nbt.getDouble("pregameRadius").orElse(18.0);
        SharedRunState.startCountdownTicks = 0;
        SharedRunState.knockbackEnabled = nbt.getBoolean("knockbackEnabled").orElse(false);
        SharedRunState.swapIntervalOverride = nbt.getBoolean("swapIntervalOverride").orElse(false);
        SharedRunState.anyPlayerWasInEnd = nbt.getBoolean("anyPlayerWasInEnd").orElse(false);
        SharedRunState.swapCount = nbt.getInt("swapCount").orElse(0);
        SharedRunState.runEnded = nbt.getBoolean("runEnded").orElse(false);
        SharedRunState.strongholdPos = nbt.getLong("strongholdPos").orElse(Long.MIN_VALUE);
        SharedRunState.netherEntryPos = nbt.getLong("netherEntryPos").orElse(Long.MIN_VALUE);
        SharedRunState.endEntryPos = nbt.getLong("endEntryPos").orElse(Long.MIN_VALUE);
        SharedRunState.fortressPos = nbt.getLong("fortressPos").orElse(Long.MIN_VALUE);
        SharedRunState.knockbackHorizontal = nbt.getDouble("knockbackHorizontal").orElse(0.2);
        SharedRunState.knockbackVertical = nbt.getDouble("knockbackVertical").orElse(0.2);
        SharedRunState.knockbackCooldownTicks = nbt.getInt("knockbackCooldownTicks").orElse(30);

        ProgressTracker.readNbt(nbt);
        AchievementTracker.readNbt(nbt);
        dev.sharedrun.stats.FoodEatTracker.readNbt(nbt);

        HeartsCausedTracker.resetAll();
        nbt.getList("hearts").ifPresent(list -> {
            for (int i = 0; i < list.size(); i++) {
                NbtElement el = list.get(i);
                if (!(el instanceof NbtCompound entry)) continue;
                String uuidStr = entry.getString("uuid").orElse(null);
                String name = entry.getString("name").orElse("?");
                float hearts = entry.getFloat("hearts").orElse(0f);
                if (uuidStr == null || hearts <= 0f) continue;
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    HeartsCausedTracker.addHpDamage(uuid, name, hearts * 2f);
                } catch (IllegalArgumentException ignored) {}
            }
        });
    }
}
