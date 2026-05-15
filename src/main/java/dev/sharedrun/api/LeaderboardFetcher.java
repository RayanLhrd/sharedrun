package dev.sharedrun.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.sharedrun.endrun.LeaderboardEntry;
import dev.sharedrun.network.LeaderboardPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class LeaderboardFetcher {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new Gson();

    private LeaderboardFetcher() {}

    public static void fetchAndSend(ServerPlayerEntity player) {
        if (!ApiConfig.isConfigured()) {
            player.sendMessage(Text.literal("§c[SharedRun] Leaderboard non disponible (API non configurée)."), false);
            return;
        }

        final String url    = ApiConfig.apiUrl;
        final String token  = ApiConfig.apiToken;
        final MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        player.sendMessage(Text.literal("§7⏳ Chargement du leaderboard..."), false);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + token)
                        .header("X-Source", "sharedrun-mod")
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    List<LeaderboardEntry> entries = GSON.fromJson(
                            response.body(),
                            new TypeToken<List<LeaderboardEntry>>() {}.getType()
                    );
                    String json = GSON.toJson(entries);
                    server.execute(() -> ServerPlayNetworking.send(player, new LeaderboardPayload(json)));
                } else {
                    System.err.println("[SharedRun] Leaderboard API HTTP " + response.statusCode());
                    final int status = response.statusCode();
                    server.execute(() -> player.sendMessage(Text.literal(
                            "§c[SharedRun] Erreur leaderboard : HTTP " + status), false));
                }
            } catch (Exception e) {
                System.err.println("[SharedRun] Leaderboard fetch failed: " + e.getMessage());
                server.execute(() -> player.sendMessage(
                        Text.literal("§c[SharedRun] Impossible de charger le leaderboard."), false));
            }
        });
    }
}
