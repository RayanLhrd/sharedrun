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
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final Gson GSON = new Gson();

    /** Délais entre tentatives en secondes : 5s, 20s, 45s */
    private static final int[] RETRY_DELAYS_SEC = {5, 20, 45};
    private static final int MAX_ATTEMPTS = 1 + RETRY_DELAYS_SEC.length;

    private ApiReporter() {}

    /**
     * Envoie les données de fin de run à l'API de manière asynchrone.
     * Retente jusqu'à 3 fois avec backoff pour absorber les cold-starts Render.
     */
    public static void reportRunEnd(RunSummary summary) {
        if (!ApiConfig.isConfigured()) return;

        final String url   = ApiConfig.apiUrl;
        final String token = ApiConfig.apiToken;
        final String json  = GSON.toJson(summary);

        CompletableFuture.runAsync(() -> sendWithRetry(url, token, json, 1));
    }

    private static void sendWithRetry(String url, String token, String json, int attempt) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Source", "sharedrun-mod")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                System.out.println("[SharedRun] API OK (tentative " + attempt + ", HTTP " + status + ")");
                return;
            }

            // 4xx = erreur permanente (payload invalide, token wrong…) → pas la peine de retenter
            if (status >= 400 && status < 500) {
                System.err.println("[SharedRun] API erreur permanente HTTP " + status
                        + " — " + response.body());
                return;
            }

            // 5xx ou autre → transitoire, on peut retenter
            System.err.println("[SharedRun] API HTTP " + status + " (tentative " + attempt + ")"
                    + " — " + response.body());

        } catch (Exception e) {
            System.err.println("[SharedRun] Échec envoi API (tentative " + attempt + ") : " + e.getMessage());
        }

        if (attempt >= MAX_ATTEMPTS) {
            System.err.println("[SharedRun] API : abandon après " + attempt + " tentatives.");
            return;
        }

        int delaySec = RETRY_DELAYS_SEC[attempt - 1];
        System.out.println("[SharedRun] API : nouvelle tentative dans " + delaySec + "s (tentative "
                + (attempt + 1) + "/" + MAX_ATTEMPTS + ")");
        try {
            Thread.sleep(delaySec * 1000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
        }

        sendWithRetry(url, token, json, attempt + 1);
    }
}
