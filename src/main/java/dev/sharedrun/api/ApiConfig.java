package dev.sharedrun.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class ApiConfig {

    private static final String CONFIG_PATH = "config/sharedrun-api.properties";

    public static String apiUrl   = "";
    public static String apiToken = "";

    private ApiConfig() {}

    public static void load() {
        Path path = Paths.get(CONFIG_PATH);

        if (!Files.exists(path)) {
            createDefault(path);
            System.out.println("[SharedRun] Fichier de config API créé : " + path.toAbsolutePath());
            System.out.println("[SharedRun] Remplis config/sharedrun-api.properties pour activer les events API.");
            return;
        }

        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(path)) {
            props.load(is);
        } catch (IOException e) {
            System.err.println("[SharedRun] Erreur lecture config API : " + e.getMessage());
            return;
        }

        apiUrl   = props.getProperty("api_url",   "").trim();
        apiToken = props.getProperty("api_token", "").trim();

        if (apiUrl.isEmpty()) {
            System.out.println("[SharedRun] api_url non configuré — les events de run ne seront pas envoyés.");
        } else {
            // On ne log PAS le token ni l'URL complète pour éviter les fuites dans les logs partagés
            System.out.println("[SharedRun] API reporter activé.");
        }
    }

    public static boolean isConfigured() {
        return !apiUrl.isEmpty() && !apiToken.isEmpty();
    }

    private static void createDefault(Path path) {
        try {
            Files.createDirectories(path.getParent());
            String content =
                "# Configuration API SharedRun\n" +
                "# Ce fichier est SERVER-SIDE ONLY — jamais envoyé aux clients.\n" +
                "# Ne le commitez PAS dans votre dépôt git (.gitignore recommandé).\n" +
                "#\n" +
                "# URL complète de votre endpoint (ex: https://mon-api.exemple.com/events/run)\n" +
                "api_url=\n" +
                "#\n" +
                "# Token d'authentification Bearer (gardez-le secret)\n" +
                "api_token=\n";
            Files.writeString(path, content);
        } catch (IOException e) {
            System.err.println("[SharedRun] Impossible de créer le fichier de config API : " + e.getMessage());
        }
    }
}
