package dev.sharedrun.client;

import com.mojang.authlib.GameProfile;
import dev.sharedrun.endrun.RunSummary;
import dev.sharedrun.mixin.LivingEntityAccessor;
import dev.sharedrun.sound.SharedRunSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Séquence cinématique affichée juste après la mort de la team avant l'écran de récap.
 * Phases (en secondes depuis l'ouverture) :
 *   0.0 - 0.5  : écran noir
 *   0.5 - 2.8  : titre "VOUS ÊTES MORT" zoom in (ease-out exponential) + secousse
 *   2.8 - 4.8  : suspense — "À cause de" + dots animés qui apparaissent un à un
 *   4.8 - 7.8  : SKIN + PSEUDO du joueur mort (reveal big)
 *   7.8 - 10.5 : détail de la mort (vanilla death message) fade-in en bas
 *   10.5 - 11.5: fade out vers le récap
 *   11.5+      : transition vers RunSummaryScreen
 */
public class DeathCinematicScreen extends Screen {

    private static final double TITLE_START = 0.5;
    private static final double SUSPENSE_START = 2.8;
    private static final double REVEAL_START = 4.8;
    private static final double DEATH_DETAIL_START = 7.8;
    private static final double REVEAL_END = 10.5;
    private static final double FADE_END = 11.5;
    private static final double DURATION = FADE_END;

    private final RunSummary summary;
    private long startNanos = 0;
    private boolean transitioned = false;
    private boolean soundPlayed = false;

    public DeathCinematicScreen(RunSummary summary) {
        super(Text.literal(""));
        this.summary = summary;
    }

    @Override
    protected void init() {
        super.init();
        if (startNanos == 0) startNanos = System.nanoTime();
        // Note : la lecture du son est faite dans render() au 1er frame — plus fiable
        // que init() (qui peut être appelé avant que le SoundManager soit complètement prêt).
    }

    private void playGameOverSoundOnce() {
        if (soundPlayed || this.client == null) return;
        soundPlayed = true;
        final MinecraftClient mc = this.client;
        // Defer to next client tick → SoundManager garanti opérationnel (fix le bug
        // "premier lancement = pas de son" causé par un timing trop tôt après setScreen)
        mc.execute(() -> {
            try {
                // ui(sound, pitch, volume) : catégorie MASTER, AttenuationType.NONE
                // → son non-positionnel, plein volume où qu'on soit
                mc.getSoundManager().play(
                        PositionedSoundInstance.ui(SharedRunSounds.DEATH_GAMEOVER, 1.0f, 1.0f)
                );
            } catch (Throwable t) {
                System.err.println("[SharedRun] Game over sound failed: " + t.getMessage());
            }
        });
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // pas de skip
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Joue le son Game Over au tout premier frame (SoundManager garanti prêt à ce stade)
        playGameOverSoundOnce();

        double t = (System.nanoTime() - startNanos) / 1_000_000_000.0;

        // Fond noir continu
        ctx.fill(0, 0, this.width, this.height, 0xFF000000);

        TextRenderer font = this.textRenderer;

        // === Phase 1 : écran noir pur ===
        if (t < TITLE_START) {
            return;
        }

        // === Phase 2 : titre zoom + secousse ===
        double titleT = clamp01((t - TITLE_START) / (SUSPENSE_START - TITLE_START));
        renderDeathTitle(ctx, font, titleT, t);

        // === Phase 3 : "À cause de..." suspense (dots animés) ===
        if (t >= SUSPENSE_START) {
            // Le préfixe reste affiché jusqu'à la fin du reveal (puis fade)
            double phaseT = t - SUSPENSE_START;
            double fadeIn = clamp01(phaseT / 0.4); // entrée
            double fadeOut = t >= DEATH_DETAIL_START
                    ? clamp01((t - DEATH_DETAIL_START) / 0.6)
                    : 0.0; // sortie quand le détail arrive
            double alphaScale = fadeIn * (1.0 - fadeOut);
            renderCausePrefix(ctx, font, alphaScale, phaseT);
        }

        // === Phase 4 : skin + pseudo (reveal) ===
        if (t >= REVEAL_START) {
            double playerT = clamp01((t - REVEAL_START) / 0.9);
            renderPlayerInfo(ctx, font, playerT, t);
        }

        // === Phase 5 : détail mort (raison vanilla) en bas ===
        if (t >= DEATH_DETAIL_START) {
            double detailT = clamp01((t - DEATH_DETAIL_START) / 1.0);
            renderDeathDetail(ctx, font, detailT);
        }

        // === Phase 6 : fade to black ===
        if (t >= REVEAL_END) {
            double fadeT = clamp01((t - REVEAL_END) / (FADE_END - REVEAL_END));
            int alpha = (int) (fadeT * 255);
            ctx.fill(0, 0, this.width, this.height, (alpha << 24) | 0x000000);
        }

        // === Phase 7 : transition vers RunSummaryScreen ===
        if (t >= DURATION && !transitioned) {
            transitioned = true;
            MinecraftClient mc = this.client;
            if (mc != null) {
                mc.setScreen(new RunSummaryScreen(summary));
            }
        }
    }

    private void renderDeathTitle(DrawContext ctx, TextRenderer font, double t, double absoluteT) {
        float scale = (float) (easeOutExpo(t) * 1.75 + 0.05);
        float shakeIntensity = (float) (Math.max(0, 1 - t * 1.5));
        float shakeX = (float) (Math.sin(absoluteT * 50) * shakeIntensity * 3);
        float shakeY = (float) (Math.cos(absoluteT * 47) * shakeIntensity * 2);

        int alpha = (int) (clamp01(t / 0.3) * 255);

        int titleY = (int) (this.height * 0.22);
        int cx = this.width / 2;

        Text title = Text.literal("💀 VOUS ÊTES MORT");
        int textW = font.getWidth(title);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx + shakeX, titleY + shakeY);
        ctx.getMatrices().scale(scale, scale);
        int color = (alpha << 24) | 0xAA0000; // rouge sang
        ctx.drawText(font, title, -textW / 2, -font.fontHeight / 2, color, true);
        ctx.getMatrices().popMatrix();
    }

    /**
     * Phase suspense : "À cause de" avec 1 à 3 points qui s'allument cycliquement.
     * Affiché juste sous le titre, sans la raison réelle (elle viendra plus tard en bas).
     */
    private void renderCausePrefix(DrawContext ctx, TextRenderer font, double alphaScale, double phaseT) {
        if (alphaScale <= 0) return;
        int alpha = (int) (alphaScale * 255);
        if (alpha <= 0) return;

        int cx = this.width / 2;
        int y = (int) (this.height * 0.22) + 28;

        // Animation des points : 0 -> 1 -> 2 -> 3 -> 0 toutes les 0.5s
        int dotCount = ((int) (phaseT / 0.45)) % 4;
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < dotCount; i++) dots.append(".");

        Text label = Text.literal("À cause de" + dots).formatted(Formatting.GRAY, Formatting.ITALIC);
        int labelW = font.getWidth(label);
        int color = (alpha << 24) | 0xCCCCCC;

        // Léger pulse pour donner du dynamisme (très subtil)
        float pulse = (float) (1.0 + Math.sin(phaseT * 3.0) * 0.04);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, y);
        ctx.getMatrices().scale(pulse, pulse);
        ctx.drawText(font, label, -labelW / 2, 0, color, true);
        ctx.getMatrices().popMatrix();
    }

    private void renderPlayerInfo(DrawContext ctx, TextRenderer font, double t, double absoluteT) {
        if (summary.deadPlayerName == null || summary.deadPlayerName.isEmpty()) return;

        float fade = (float) easeOutCubic(t);

        int cx = this.width / 2;
        // Skin un peu plus haut pour laisser place au texte de détail en bas
        int cy = (int) (this.height * 0.55);

        // 3D player render
        PlayerEntity entity = findPlayer(summary.deadPlayerName);
        if (entity != null) {
            // Force pose normale (sinon le joueur mort apparaît allongé)
            entity.setPose(EntityPose.STANDING);
            entity.setPitch(0);
            // Force visible : si le joueur est en spectator côté serveur (race condition
            // ou reconnexion), le renderer le ferait apparaître transparent / sans corps.
            entity.setInvisible(false);
            try {
                ((LivingEntityAccessor) entity).sharedrun$setDeathTime(0);
            } catch (Throwable ignored) {
                // Si le cast/mixin échoue, on laisse tomber silencieusement
            }

            // Rotation lente du modèle via mouseX/mouseY simulés
            float orbitRadius = 70f;
            float orbitSpeed = 0.35f;
            float ox = (float) (Math.cos((absoluteT - REVEAL_START) * orbitSpeed) * orbitRadius);
            float oy = -20f;

            // Taille réduite + box agrandie pour éviter de couper les cheveux / hat layer
            int size = (int) (50 + 15 * fade); // 50 à 65 max
            int boxHalfW = 55;
            int boxHalfH = 95; // 190 px de haut — large marge au-dessus du modèle
            int x1 = cx - boxHalfW;
            int y1 = cy - boxHalfH;
            int x2 = cx + boxHalfW;
            int y2 = cy + boxHalfH;

            try {
                InventoryScreen.drawEntity(ctx, x1, y1, x2, y2, size, 0.0625f,
                        cx + ox, cy + oy, (net.minecraft.entity.LivingEntity) entity);
            } catch (Exception ignored) {
                // Si le rendu 3D foire (entité non chargée etc), on skip
            }
        } else {
            // Pas de PlayerEntity trouvable côté client (typiquement : le mort est dans
            // une autre dimension que toi). On affiche un placeholder skull à la place
            // du skin 3D — meilleur que d'afficher rien ou (pire) le skin du joueur local.
            Text skull = Text.literal("💀").formatted(Formatting.DARK_RED);
            int sw = font.getWidth(skull);
            float pulse = (float) (1.0 + Math.sin(absoluteT * 2.0) * 0.05);
            int alpha = (int) (fade * 255);
            int color = (alpha << 24) | 0xAA0000;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx, cy - 20);
            ctx.getMatrices().scale(6.0f * pulse, 6.0f * pulse);
            ctx.drawText(font, skull, -sw / 2, -font.fontHeight / 2, color, true);
            ctx.getMatrices().popMatrix();
        }

        // Pseudo en dessous du modèle, fade-in simultané
        int pseudoY = cy + 80;
        Text pseudo = Text.literal(summary.deadPlayerName).formatted(Formatting.WHITE, Formatting.BOLD);
        int pseudoW = font.getWidth(pseudo);
        int alpha = (int) (fade * 255);
        int color = (alpha << 24) | 0xFFFFFF;

        float pseudoScale = 0.8f + 0.6f * fade;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, pseudoY);
        ctx.getMatrices().scale(pseudoScale, pseudoScale);
        ctx.drawText(font, pseudo, -pseudoW / 2, 0, color, true);
        ctx.getMatrices().popMatrix();
    }

    /**
     * Affiche le détail de la mort en bas, après le reveal du pseudo.
     * Strip le nom du joueur en préfixe (la vanilla message commence par "PlayerName ...").
     */
    private void renderDeathDetail(DrawContext ctx, TextRenderer font, double t) {
        if (summary.deathReason == null || summary.deathReason.isEmpty()) return;

        int alpha = (int) (easeOutCubic(t) * 255);
        if (alpha <= 0) return;

        String detail = stripPlayerName(summary.deathReason, summary.deadPlayerName);
        if (detail.isEmpty()) detail = summary.deathReason;

        int cx = this.width / 2;
        int y = (int) (this.height * 0.88);

        // Petit chevron au-dessus pour souligner que c'est la raison
        Text arrow = Text.literal("▾").formatted(Formatting.DARK_RED);
        int arrowW = font.getWidth(arrow);
        ctx.drawText(font, arrow, cx - arrowW / 2, y - 12, (alpha << 24) | 0xAA0000, true);

        // Le message principal, en italic rouge clair
        Text msg = Text.literal(detail).formatted(Formatting.RED, Formatting.ITALIC);
        int msgW = font.getWidth(msg);
        ctx.drawText(font, msg, cx - msgW / 2, y, (alpha << 24) | 0xFF6666, true);
    }

    /**
     * "Rayan was slain by a Skeleton" -> "Was slain by a Skeleton" (capitalize first char).
     * "Rayan a été tué par un Squelette" -> "A été tué par un Squelette".
     */
    private static String stripPlayerName(String raw, String playerName) {
        if (raw == null) return "";
        String s = raw.trim();
        if (playerName != null && !playerName.isEmpty() && s.regionMatches(0, playerName, 0, playerName.length())) {
            s = s.substring(playerName.length()).trim();
            if (!s.isEmpty()) {
                s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        }
        return s;
    }

    // Cache pour ne pas reconstruire l'entité fantôme à chaque frame (render = 60fps)
    private OtherClientPlayerEntity cachedDummyEntity;
    private String cachedDummyName;

    /**
     * Trouve l'entité du joueur mort pour rendu via InventoryScreen.drawEntity.
     *
     * 1) Si le joueur est dans la même dim que le client local → retourne l'entité réelle
     * 2) Sinon (cross-dim), construit une OtherClientPlayerEntity depuis le GameProfile
     *    du joueur (accessible via la player list même si dim différente). Le skin est
     *    récupéré depuis le cache textures du client (player profiles sont sync via login).
     *
     * Garantit que le bon skin s'affiche même si la mort se passe dans une autre dim.
     */
    private PlayerEntity findPlayer(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || name == null) return null;

        // 1. Cherche dans le monde courant (même dim)
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.getName().getString().equals(name)) {
                return p;
            }
        }

        // 2. Cache d'entité fantôme (évite recréation à chaque frame)
        if (cachedDummyEntity != null && name.equals(cachedDummyName)) {
            return cachedDummyEntity;
        }

        // 3. Cross-dim : construit une fake OtherClientPlayerEntity depuis le GameProfile
        var netHandler = mc.getNetworkHandler();
        if (netHandler == null) return null;
        for (PlayerListEntry entry : netHandler.getPlayerList()) {
            GameProfile profile = entry.getProfile();
            if (profile != null && name.equals(profile.name())) {
                try {
                    cachedDummyEntity = new OtherClientPlayerEntity(mc.world, profile);
                    cachedDummyName = name;
                    return cachedDummyEntity;
                } catch (Throwable t) {
                    System.err.println("[SharedRun] Failed to create dummy entity for " + name + ": " + t.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static double easeOutExpo(double t) {
        return t >= 1.0 ? 1.0 : 1.0 - Math.pow(2, -10 * t);
    }

    private static double easeOutCubic(double t) {
        return 1.0 - Math.pow(1 - t, 3);
    }
}
