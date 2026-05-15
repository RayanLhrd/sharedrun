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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Écran cinématique de VICTOIRE après la mort de l'Ender Dragon.
 *
 * Phases (en secondes depuis l'ouverture) :
 *   0.0 - 0.5  : écran noir + setup
 *   0.5 - 2.5  : titre "🎉 VICTOIRE !" zoom-in doré + glow + confettis
 *   2.5 - 4.0  : sous-titre "L'Ender Dragon est vaincu en X:XX" fade-in
 *   4.0 - 10.0 : reveal cascade des skins de tous les participants (MVP highlighted)
 *   10.0 - 12.5: bandeau de stats en bas (temps / MVP / achievements / killer)
 *   12.5 - 14.0: fade out vers le RunSummaryScreen
 */
public class VictoryCinematicScreen extends Screen {

    private static final double TITLE_START = 0.5;
    private static final double SUBTITLE_START = 2.5;
    private static final double REVEAL_START = 4.0;
    private static final double STATS_START = 10.0;
    private static final double FADE_START = 12.5;
    private static final double FADE_END = 14.0;
    private static final double DURATION = FADE_END;

    private final RunSummary summary;
    private long startNanos = 0;
    private boolean transitioned = false;
    private boolean soundPlayed = false;
    // Sons cascadés "chime" déjà joués pour chaque skin (par index)
    private final boolean[] chimePlayed;

    public VictoryCinematicScreen(RunSummary summary) {
        super(Text.literal(""));
        this.summary = summary;
        int n = summary.participants != null ? summary.participants.size() : 0;
        this.chimePlayed = new boolean[Math.max(1, n)];
    }

    @Override
    protected void init() {
        super.init();
        if (startNanos == 0) startNanos = System.nanoTime();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        playVictoryThemeOnce();
        double t = (System.nanoTime() - startNanos) / 1_000_000_000.0;

        // Fond noir continu
        ctx.fill(0, 0, this.width, this.height, 0xFF000000);

        TextRenderer font = this.textRenderer;
        int cx = this.width / 2;

        if (t < TITLE_START) return;

        // === Phase 2 : titre VICTOIRE ===
        double titleT = clamp01((t - TITLE_START) / (SUBTITLE_START - TITLE_START));
        renderVictoryTitle(ctx, font, titleT, t);

        // Confettis dorés dérivent du haut, présents tout le long jusqu'au fade
        if (t < FADE_END) {
            renderConfetti(ctx, t);
        }

        // === Phase 3 : sous-titre ===
        if (t >= SUBTITLE_START) {
            double subT = clamp01((t - SUBTITLE_START) / 0.8);
            renderSubtitle(ctx, font, subT);
        }

        // === Phase 4 : reveal des skins ===
        if (t >= REVEAL_START) {
            renderTeamLineup(ctx, font, t);
        }

        // === Phase 5 : stats ===
        if (t >= STATS_START) {
            double statsT = clamp01((t - STATS_START) / 1.0);
            renderStatsBar(ctx, font, statsT);
        }

        // === Phase 6 : fade out vers RunSummaryScreen ===
        if (t >= FADE_START) {
            double fadeT = clamp01((t - FADE_START) / (FADE_END - FADE_START));
            int alpha = (int) (fadeT * 255);
            ctx.fill(0, 0, this.width, this.height, (alpha << 24) | 0x000000);
        }

        if (t >= DURATION && !transitioned) {
            transitioned = true;
            MinecraftClient mc = this.client;
            if (mc != null) {
                mc.setScreen(new RunSummaryScreen(summary));
            }
        }
    }

    private void playVictoryThemeOnce() {
        if (soundPlayed || this.client == null) return;
        soundPlayed = true;
        final MinecraftClient mc = this.client;
        mc.execute(() -> {
            try {
                mc.getSoundManager().play(
                        PositionedSoundInstance.ui(SharedRunSounds.VICTORY_THEME, 1.0f, 1.0f)
                );
            } catch (Throwable t) {
                System.err.println("[SharedRun] Victory theme sound failed: " + t.getMessage());
            }
        });
    }

    /**
     * Titre "🎉 VICTOIRE !" en gros, doré, zoom-in + glow + petit bob vertical.
     */
    private void renderVictoryTitle(DrawContext ctx, TextRenderer font, double t, double absoluteT) {
        float scale = (float) (easeOutExpo(t) * 1.85 + 0.05);
        float bob = (float) (Math.sin(absoluteT * 2.0) * 2.0); // wobble subtil
        int alpha = (int) (clamp01(t / 0.3) * 255);

        int titleY = (int) (this.height * 0.18);
        int cx = this.width / 2;

        Text title = Text.literal("🎉 VICTOIRE !");
        int textW = font.getWidth(title);

        // Glow : 3 passes derrière en couleurs dégradées (or → orange)
        for (int g = 3; g >= 1; g--) {
            int glowAlpha = (int) (alpha * 0.25);
            int color = (glowAlpha << 24) | 0xFFAA00;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx, titleY + bob);
            ctx.getMatrices().scale(scale * (1.0f + g * 0.03f), scale * (1.0f + g * 0.03f));
            ctx.drawText(font, title, -textW / 2, -font.fontHeight / 2, color, false);
            ctx.getMatrices().popMatrix();
        }

        // Titre principal en or
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, titleY + bob);
        ctx.getMatrices().scale(scale, scale);
        int mainColor = (alpha << 24) | 0xFFD700; // gold
        ctx.drawText(font, title, -textW / 2, -font.fontHeight / 2, mainColor, true);
        ctx.getMatrices().popMatrix();
    }

    /**
     * Sous-titre "L'Ender Dragon est vaincu en {time}" en jaune, fade-in.
     */
    private void renderSubtitle(DrawContext ctx, TextRenderer font, double t) {
        int alpha = (int) (easeOutCubic(t) * 255);
        if (alpha <= 0) return;

        String time = formatTime(summary.elapsedSeconds);
        Text sub = Text.literal("L'Ender Dragon est vaincu en §f" + time)
                .formatted(Formatting.YELLOW, Formatting.ITALIC);
        int w = font.getWidth(sub);
        int cx = this.width / 2;
        int y = (int) (this.height * 0.18) + 28;

        int color = (alpha << 24) | 0xFFFF55;
        ctx.drawText(font, sub, cx - w / 2, y, color, true);
    }

    /**
     * Lineup horizontal des skins de tous les participants.
     * Cascade : chaque skin apparaît avec un délai de 0.3s par rapport au précédent.
     * MVP highlighted (couronne dorée + glow + pseudo gold).
     */
    private void renderTeamLineup(DrawContext ctx, TextRenderer font, double absoluteT) {
        if (summary.participants == null || summary.participants.isEmpty()) return;

        List<String> names = summary.participants;
        int n = names.size();
        int cx = this.width / 2;
        int centerY = (int) (this.height * 0.52);

        // Layout horizontal : 80px wide par slot + 20px gap
        int slotW = 80;
        int gap = 20;
        int totalW = n * slotW + (n - 1) * gap;
        int startX = cx - totalW / 2 + slotW / 2; // center of first slot

        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            int slotCx = startX + i * (slotW + gap);

            // Délai cascade
            double delay = REVEAL_START + i * 0.3;
            if (absoluteT < delay) continue;
            double localT = clamp01((absoluteT - delay) / 0.6);
            float fade = (float) easeOutCubic(localT);
            int alpha = (int) (fade * 255);

            // Joue le son "chime" la première fois que ce skin apparaît
            if (!chimePlayed[i] && localT > 0.05) {
                chimePlayed[i] = true;
                final MinecraftClient mc = this.client;
                final float pitch = 1.0f + i * 0.1f;
                if (mc != null) {
                    mc.execute(() -> {
                        try {
                            mc.getSoundManager().play(PositionedSoundInstance.ui(
                                    net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                                    pitch, 0.4f));
                        } catch (Throwable ignored) {}
                    });
                }
            }

            boolean isMvp = name.equals(summary.mvp);
            boolean isKiller = name.equals(summary.dragonKilledBy);

            renderPlayerSlot(ctx, font, name, slotCx, centerY, fade, alpha, isMvp, isKiller, absoluteT, i);
        }
    }

    private void renderPlayerSlot(DrawContext ctx, TextRenderer font, String name,
                                   int slotCx, int centerY, float fade, int alpha,
                                   boolean isMvp, boolean isKiller, double absoluteT, int idx) {
        // Box pour le skin 3D
        int boxHalfW = 40;
        int boxHalfH = 80;
        int x1 = slotCx - boxHalfW;
        int y1 = centerY - boxHalfH;
        int x2 = slotCx + boxHalfW;
        int y2 = centerY + boxHalfH;

        // Glow MVP : rectangle gold derrière la box
        if (isMvp && fade > 0.5f) {
            float pulse = (float) (1.0 + Math.sin(absoluteT * 3.0) * 0.05);
            int glowAlpha = (int) (alpha * 0.4 * pulse);
            int glowColor = (glowAlpha << 24) | 0xFFD700;
            ctx.fill(x1 - 6, y1 - 6, x2 + 6, y2 + 6, glowColor);
        }

        // Skin 3D
        PlayerEntity entity = findPlayer(name);
        if (entity != null) {
            entity.setPose(EntityPose.STANDING);
            entity.setPitch(0);
            entity.setInvisible(false);
            try {
                ((LivingEntityAccessor) entity).sharedrun$setDeathTime(0);
            } catch (Throwable ignored) {}

            // Léger spin orbital pour le modèle (chaque slot avec offset différent)
            float orbitSpeed = 0.4f;
            float orbitRadius = 60f;
            float ox = (float) (Math.cos((absoluteT + idx * 0.4) * orbitSpeed) * orbitRadius);
            float oy = -15f;

            int size = (int) (45 + 10 * fade); // 45 à 55
            try {
                InventoryScreen.drawEntity(ctx, x1, y1, x2, y2, size, 0.0625f,
                        slotCx + ox, centerY + oy, (LivingEntity) entity);
            } catch (Exception ignored) {}
        } else {
            // Fallback : "?" centré
            Text placeholder = Text.literal("?").formatted(Formatting.DARK_GRAY, Formatting.BOLD);
            int pw = font.getWidth(placeholder);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(slotCx, centerY);
            ctx.getMatrices().scale(4.0f, 4.0f);
            int color = (alpha << 24) | 0x444444;
            ctx.drawText(font, placeholder, -pw / 2, -font.fontHeight / 2, color, true);
            ctx.getMatrices().popMatrix();
        }

        // Couronne MVP au-dessus
        if (isMvp) {
            Text crown = Text.literal("👑");
            int cw = font.getWidth(crown);
            int crownColor = (alpha << 24) | 0xFFD700;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(slotCx, y1 - 4);
            ctx.getMatrices().scale(1.6f, 1.6f);
            ctx.drawText(font, crown, -cw / 2, -font.fontHeight, crownColor, true);
            ctx.getMatrices().popMatrix();
        }

        // Pseudo en dessous
        Text pseudoText;
        int pseudoColor;
        if (isMvp) {
            pseudoText = Text.literal(name).formatted(Formatting.GOLD, Formatting.BOLD);
            pseudoColor = (alpha << 24) | 0xFFD700;
        } else if (isKiller) {
            pseudoText = Text.literal("🐲 ").formatted(Formatting.LIGHT_PURPLE)
                    .append(Text.literal(name).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
            pseudoColor = (alpha << 24) | 0xFF55FF;
        } else {
            pseudoText = Text.literal(name).formatted(Formatting.WHITE);
            pseudoColor = (alpha << 24) | 0xFFFFFF;
        }
        int pw = font.getWidth(pseudoText);
        ctx.drawText(font, pseudoText, slotCx - pw / 2, y2 + 6, pseudoColor, true);

        // Pour le killer non-MVP, ajouter un sous-label "Coup fatal"
        if (isKiller && !isMvp) {
            Text label = Text.literal("Coup fatal").formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC);
            int lw = font.getWidth(label);
            int lc = (alpha << 24) | 0xCC88FF;
            ctx.drawText(font, label, slotCx - lw / 2, y2 + 18, lc, true);
        }
    }

    /**
     * Bandeau de stats en bas : temps + MVP + achievements + killer.
     */
    private void renderStatsBar(DrawContext ctx, TextRenderer font, double t) {
        int alpha = (int) (easeOutCubic(t) * 255);
        if (alpha <= 0) return;

        int cx = this.width / 2;
        int yBase = (int) (this.height * 0.85);

        // Bandeau de fond
        int barAlpha = (int) (alpha * 0.6);
        ctx.fill(0, yBase - 6, this.width, yBase + 48, (barAlpha << 24) | 0x000000);

        // Ligne 1 : temps
        String timeStr = formatTime(summary.elapsedSeconds) + " §7/ §f" + formatTime(summary.totalSeconds);
        Text time = Text.literal("§e⏱ Temps : §f" + timeStr);
        int tw = font.getWidth(time);
        ctx.drawText(font, time, cx - tw / 2, yBase, (alpha << 24) | 0xFFFFFF, true);

        // Ligne 2 : MVP
        if (summary.mvp != null && !summary.mvp.isEmpty()) {
            Text mvp = Text.literal("§6⭐ MVP : §e§l" + summary.mvp
                    + " §r§7(" + summary.mvpContributions + " contributions)");
            int mw = font.getWidth(mvp);
            ctx.drawText(font, mvp, cx - mw / 2, yBase + 12, (alpha << 24) | 0xFFFFFF, true);
        }

        // Ligne 3 : achievements + killer
        int unlockedAch = summary.achievements != null ? summary.achievements.size() : 0;
        String achStr = unlockedAch + "/" + summary.totalAchievements;
        String killerStr = (summary.dragonKilledBy != null && !summary.dragonKilledBy.isEmpty())
                ? "   §5🐲 Coup fatal : §d" + summary.dragonKilledBy
                : "";
        Text line3 = Text.literal("§a🏆 Achievements : §f" + achStr + killerStr);
        int l3w = font.getWidth(line3);
        ctx.drawText(font, line3, cx - l3w / 2, yBase + 24, (alpha << 24) | 0xFFFFFF, true);
    }

    /**
     * Confettis dorés qui descendent depuis le haut de l'écran.
     * Pseudo-aléatoire stable basé sur l'index.
     */
    private void renderConfetti(DrawContext ctx, double t) {
        int count = 30;
        for (int i = 0; i < count; i++) {
            // Pseudo-random stable
            double seed = i * 7.31;
            double xSeed = (Math.sin(seed) * 10000) % 1.0;
            if (xSeed < 0) xSeed += 1.0;
            double speed = 30 + ((seed * 13) % 40); // pixels/sec
            double phase = (seed * 17) % 6.0;
            double localT = (t + phase) % 6.0;

            float x = (float) (xSeed * this.width);
            float y = (float) (localT * speed) - 20;
            if (y > this.height + 10) continue;

            // Sway latéral
            float sway = (float) (Math.sin(t * 1.5 + i) * 8);
            x += sway;

            // Couleur or/orange alternée
            int color = (i % 2 == 0) ? 0xFFFFD700 : 0xFFFFA500;
            ctx.fill((int) x, (int) y, (int) x + 3, (int) y + 5, color);
        }
    }

    // Cache pour les entités fantômes cross-dim (1 par participant max)
    private final Map<String, OtherClientPlayerEntity> dummyCache = new HashMap<>();

    /**
     * Trouve l'entité d'un joueur participant pour rendu skin 3D.
     * Cross-dim safe : construit une OtherClientPlayerEntity depuis le GameProfile
     * (récupérable via la player list même si le joueur est dans une autre dim).
     */
    private PlayerEntity findPlayer(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || name == null) return null;

        // 1. Cherche dans le monde courant
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.getName().getString().equals(name)) return p;
        }

        // 2. Cache des dummies déjà construits
        OtherClientPlayerEntity cached = dummyCache.get(name);
        if (cached != null) return cached;

        // 3. Cross-dim : construit dummy depuis GameProfile
        var netHandler = mc.getNetworkHandler();
        if (netHandler == null) return null;
        for (PlayerListEntry entry : netHandler.getPlayerList()) {
            GameProfile profile = entry.getProfile();
            if (profile != null && name.equals(profile.name())) {
                try {
                    OtherClientPlayerEntity dummy = new OtherClientPlayerEntity(mc.world, profile);
                    dummyCache.put(name, dummy);
                    return dummy;
                } catch (Throwable t) {
                    System.err.println("[SharedRun] Failed to create dummy entity for " + name + ": " + t.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    private static String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
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
