package dev.sharedrun.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.sharedrun.endrun.LeaderboardEntry;
import dev.sharedrun.endrun.RunSummary;
import dev.sharedrun.network.LeaderboardPayload;
import dev.sharedrun.network.ProgressSyncPayload;
import dev.sharedrun.network.RunSummaryPayload;
import dev.sharedrun.network.TimerSyncPayload;
import dev.sharedrun.sound.SharedRunSounds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.sound.PositionedSoundInstance;

public class SharedRunClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(TimerSyncPayload.TYPE, (payload, ctx) -> {
            TimerHud.remainingSeconds = payload.remainingSeconds();
            TimerHud.totalSeconds = payload.totalSeconds();
            TimerHud.running = payload.running();
            TimerHud.everReceived = true;
        });

        ClientPlayNetworking.registerGlobalReceiver(ProgressSyncPayload.TYPE, (payload, ctx) -> {
            ProgressHud.flags = payload.flags();
            ProgressHud.foodCount = payload.foodCount();
            ProgressHud.ironIngots = payload.ironIngots();
            ProgressHud.diamonds = payload.diamonds();
            ProgressHud.blazeRods = payload.blazeRods();
            ProgressHud.enderPearls = payload.enderPearls();
            ProgressHud.eyesOfEnder = payload.eyesOfEnder();
        });

        ClientPlayNetworking.registerGlobalReceiver(RunSummaryPayload.TYPE, (payload, ctx) -> {
            try {
                RunSummary summary = new Gson().fromJson(payload.json(), RunSummary.class);
                if (summary.endReason == 0) {
                    // VICTORY → écran cinématique de victoire avec lineup des skins
                    ctx.client().setScreen(new VictoryCinematicScreen(summary));
                } else if (summary.endReason == 2
                        && summary.deadPlayerName != null
                        && !summary.deadPlayerName.isEmpty()) {
                    // ALL_DEAD → cinématique de mort
                    ctx.client().setScreen(new DeathCinematicScreen(summary));
                } else {
                    // TIME_OUT (ou fallback) → récap direct
                    ctx.client().setScreen(new RunSummaryScreen(summary));
                }
            } catch (Exception e) {
                System.err.println("[SharedRun] Failed to parse run summary: " + e.getMessage());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(LeaderboardPayload.TYPE, (payload, ctx) -> {
            try {
                java.util.List<LeaderboardEntry> entries = new Gson().fromJson(
                        payload.json(),
                        new TypeToken<java.util.List<LeaderboardEntry>>() {}.getType()
                );
                ctx.client().execute(() -> ctx.client().setScreen(new LeaderboardScreen(entries)));
            } catch (Exception e) {
                System.err.println("[SharedRun] Failed to parse leaderboard: " + e.getMessage());
            }
        });

        HudRenderCallback.EVENT.register(TimerHud::render);
        HudRenderCallback.EVENT.register(ProgressHud::render);

        // Pré-load le son Game Over Mario au join : silent play (volume ~0)
        // → le fichier OGG est chargé en cache, le 1er vrai play à la cinématique
        //   est instantané et fiable (sinon le premier death = parfois pas de son).
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Pré-load les sons custom à volume quasi-nul → 1er vrai play instantané
            client.execute(() -> {
                try {
                    client.getSoundManager().play(
                            PositionedSoundInstance.ui(SharedRunSounds.DEATH_GAMEOVER, 1.0f, 0.0001f)
                    );
                    client.getSoundManager().play(
                            PositionedSoundInstance.ui(SharedRunSounds.VICTORY_THEME, 1.0f, 0.0001f)
                    );
                } catch (Throwable ignored) {}
            });
        });
    }
}
