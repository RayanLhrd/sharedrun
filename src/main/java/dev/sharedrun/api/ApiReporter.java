package dev.sharedrun.api;

import com.google.gson.Gson;
import dev.sharedrun.endrun.RunSummary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class ApiReporter {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Gson GSON = new Gson();

    private ApiReporter() {}

    /**
     * Envoie les données de fin de run à l'API de manière asynchrone.
     * Ne bloque pas le thread serveur.
     */
    public static void reportRunEnd(RunSummary summary) {
        if (!ApiConfig.isConfigured()) return;

        // Capture les valeurs avant de quitter le thread serveur
        final String url   = ApiConfig.apiUrl;
        final String token = ApiConfig.apiToken;
        final String json  = GSON.toJson(summary);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Source", "sharedrun-mod")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    System.out.println("[SharedRun] Event envoyé à l'API (HTTP " + response.statusCode() + ")");
                } else {
                    System.err.println("[SharedRun] API a retourné HTTP " + response.statusCode()
                            + " — " + response.body());
                }
            } catch (Exception e) {
                System.err.println("[SharedRun] Échec envoi API : " + e.getMessage());
            }
        });
    }
}
