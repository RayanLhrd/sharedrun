package dev.sharedrun.client;

import dev.sharedrun.endrun.RunSummary;
import dev.sharedrun.network.DebriefTpPayload;
import dev.sharedrun.network.LeaderboardRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public class RunSummaryScreen extends Screen {

    private static final Map<String, Item> ACHIEVEMENT_ICONS = new LinkedHashMap<>();
    static {
        ACHIEVEMENT_ICONS.put("first_obsidian", Items.OBSIDIAN);
        ACHIEVEMENT_ICONS.put("first_flint_steel", Items.FLINT_AND_STEEL);
        ACHIEVEMENT_ICONS.put("first_iron_pickaxe", Items.IRON_PICKAXE);
        ACHIEVEMENT_ICONS.put("first_iron_armor", Items.IRON_CHESTPLATE);
        ACHIEVEMENT_ICONS.put("first_diamond_pickaxe", Items.DIAMOND_PICKAXE);
        ACHIEVEMENT_ICONS.put("first_diamond_sword", Items.DIAMOND_SWORD);
        ACHIEVEMENT_ICONS.put("first_gold_ingot", Items.GOLD_INGOT);
        ACHIEVEMENT_ICONS.put("first_golden_apple", Items.GOLDEN_APPLE);
        ACHIEVEMENT_ICONS.put("first_bed", Items.RED_BED);
        ACHIEVEMENT_ICONS.put("first_wither_skull", Items.WITHER_SKELETON_SKULL);
        ACHIEVEMENT_ICONS.put("first_nether_portal_built", Items.OBSIDIAN);
        ACHIEVEMENT_ICONS.put("first_stronghold", Items.END_PORTAL_FRAME);
        ACHIEVEMENT_ICONS.put("first_warped_forest", Items.WARPED_NYLIUM);
        ACHIEVEMENT_ICONS.put("first_death", Items.SKELETON_SKULL);
    }

    private final RunSummary summary;

    // Layout constants
    private static final int HEADER_HEIGHT = 70;
    private static final int OBJ_ROW_H = 16;
    private static final int ACH_ROW_H = 17;
    private static final int LEADER_ROW_H = 10;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_BOTTOM_MARGIN = 8;

    public RunSummaryScreen(RunSummary summary) {
        super(Text.literal("Run Summary"));
        this.summary = summary;
    }

    @Override
    protected void init() {
        super.init();

        // Rangée de 6 boutons en bas : 5 TP debrief + 1 leaderboard
        int buttonW = 80;
        int gap = 5;
        int totalW = 6 * buttonW + 5 * gap;
        int startX = this.width / 2 - totalW / 2;
        int y = this.height - BUTTON_H - BUTTON_BOTTOM_MARGIN;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§a🌳 Overworld"),
                btn -> sendTp(1)
        ).dimensions(startX, y, buttonW, BUTTON_H).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§c🔥 Nether"),
                btn -> sendTp(2)
        ).dimensions(startX + (buttonW + gap), y, buttonW, BUTTON_H).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§4🏰 Forteresse"),
                btn -> sendTp(5)
        ).dimensions(startX + 2 * (buttonW + gap), y, buttonW, BUTTON_H).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§6🏛 Stronghold"),
                btn -> sendTp(3)
        ).dimensions(startX + 3 * (buttonW + gap), y, buttonW, BUTTON_H).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§5🐲 End"),
                btn -> sendTp(4)
        ).dimensions(startX + 4 * (buttonW + gap), y, buttonW, BUTTON_H).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§e🏆 Leaderboard"),
                btn -> {
                    ClientPlayNetworking.send(new LeaderboardRequestPayload());
                    this.close();
                }
        ).dimensions(startX + 5 * (buttonW + gap), y, buttonW, BUTTON_H).build());
    }

    private void sendTp(int dest) {
        ClientPlayNetworking.send(new DebriefTpPayload(dest));
        this.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xD0000000);

        super.render(ctx, mouseX, mouseY, delta);

        TextRenderer font = this.textRenderer;
        int cx = this.width / 2;
        int y = 8;

        // === Header ===
        ctx.drawCenteredTextWithShadow(font,
                Text.literal("🏁 FIN DE LA RUN").formatted(Formatting.GOLD, Formatting.BOLD),
                cx, y, 0xFFFFD700);
        y += 14;

        String resultText;
        int resultColor;
        switch (summary.endReason) {
            case 0 -> { resultText = "🎉 VICTOIRE !"; resultColor = 0xFFFFD700; }
            case 1 -> { resultText = "⏱ TEMPS ÉCOULÉ"; resultColor = 0xFFFF5555; }
            case 2 -> { resultText = "💀 DÉFAITE"; resultColor = 0xFFAA0000; }
            default -> { resultText = "?"; resultColor = 0xFFAAAAAA; }
        }
        ctx.drawCenteredTextWithShadow(font, resultText, cx, y, resultColor);
        y += 14;

        // Chrono : 2 lignes
        ctx.drawCenteredTextWithShadow(font,
                "§e⏱ Écoulé : §f" + formatTime(summary.elapsedSeconds)
                + " §7/ §f" + formatTime(summary.totalSeconds),
                cx, y, 0xFFFFFFFF);
        y += 10;

        int remaining = summary.totalSeconds - summary.elapsedSeconds;
        if (remaining < 0) remaining = 0;
        String remainingLabel;
        int remainingColor;
        if (summary.endReason == 0) {
            // Victory: remaining = time saved
            remainingLabel = "§a✨ Temps gagné : §f" + formatTime(remaining);
            remainingColor = 0xFF55FF55;
        } else {
            remainingLabel = "§e⌛ Restant : §f" + formatTime(remaining);
            remainingColor = 0xFFFFFFFF;
        }
        ctx.drawCenteredTextWithShadow(font, remainingLabel, cx, y, remainingColor);

        // === Columns ===
        int contentTop = HEADER_HEIGHT;
        int leftX = 16;
        int rightX = cx + 8;
        int rightW = this.width - rightX - 16;

        int milestoneCount = (int) summary.milestones.stream().filter(m -> m.reached).count();
        int totalMilestones = summary.milestones.size();
        ctx.drawTextWithShadow(font,
                Text.literal("§6§lObjectifs §7(" + milestoneCount + "/" + totalMilestones + ")"),
                leftX, contentTop, 0xFFFFD700);

        int unlockedAch = summary.achievements.size();
        ctx.drawTextWithShadow(font,
                Text.literal("§6§lAchievements §7(" + unlockedAch + "/" + summary.totalAchievements + ")"),
                rightX, contentTop, 0xFFFFD700);

        // Left column: objectives
        int objY = contentTop + 12;
        for (RunSummary.Milestone m : summary.milestones) {
            drawMilestoneRow(ctx, font, m, leftX, objY);
            objY += OBJ_ROW_H;
        }

        // Right zone: 2 sub-columns achievements
        int subColGap = 4;
        int subColW = (rightW - subColGap) / 2;
        int subCol1X = rightX;
        int subCol2X = rightX + subColW + subColGap;
        int achY1 = contentTop + 12;
        int achY2 = contentTop + 12;

        Map<String, String> unlockedMap = new LinkedHashMap<>();
        for (RunSummary.Achievement a : summary.achievements) {
            unlockedMap.put(a.key, a.by);
        }
        int idx = 0;
        for (var entry : ACHIEVEMENT_ICONS.entrySet()) {
            String key = entry.getKey();
            Item icon = entry.getValue();
            boolean unlocked = unlockedMap.containsKey(key);
            String by = unlocked ? unlockedMap.get(key) : null;
            int subX = (idx < 7) ? subCol1X : subCol2X;
            int subY = (idx < 7) ? achY1 : achY2;
            drawAchievementRow(ctx, font, icon, key, unlocked, by, subX, subY, subColW);
            if (idx < 7) achY1 += ACH_ROW_H;
            else        achY2 += ACH_ROW_H;
            idx++;
        }

        // === Bottom : leaderboard + grille stats 2 colonnes ===
        int columnsBottom = Math.max(objY, Math.max(achY1, achY2)) + 4;
        int buttonTop = this.height - BUTTON_H - BUTTON_BOTTOM_MARGIN;
        // Hauteur calculée : header(12) + leaderboard(N*10) + gap(6) + stats grid(3*12) + 4
        int leaderboardLines = Math.max(1, summary.leaderboard.size());
        int bottomBlockHeight = 12 + leaderboardLines * LEADER_ROW_H + 8 + 3 * 12 + 4;
        int bottomTop = Math.min(columnsBottom, buttonTop - bottomBlockHeight - 4);

        int by = bottomTop;
        ctx.drawCenteredTextWithShadow(font,
                Text.literal("━━ Top dégâts pris ━━").formatted(Formatting.GOLD, Formatting.BOLD),
                cx, by, 0xFFFFD700);
        by += 12;
        if (summary.leaderboard.isEmpty()) {
            ctx.drawCenteredTextWithShadow(font,
                    Text.literal("Aucun dégât pris").formatted(Formatting.GRAY),
                    cx, by, 0xFFAAAAAA);
            by += LEADER_ROW_H;
        } else {
            int rank = 1;
            for (RunSummary.LeaderEntry e : summary.leaderboard) {
                String medal = switch (rank) {
                    case 1 -> "§e🥇";
                    case 2 -> "§7🥈";
                    case 3 -> "§6🥉";
                    default -> "§8" + rank + ".";
                };
                ctx.drawCenteredTextWithShadow(font,
                        Text.literal(medal + " §f§l" + e.name + " §8— §c"
                                + String.format("%.1f", e.hearts) + " ❤"),
                        cx, by, 0xFFFFFFFF);
                by += LEADER_ROW_H;
                rank++;
            }
        }
        by += 6;

        // === Grille 2 colonnes : Vertu (gauche) | Vice (droite) ===
        // Layout : chaque ligne a 2 cellules. Cellule gauche right-aligned au cx-gap,
        // cellule droite left-aligned au cx+gap → effet "tableau symétrique".
        int colGap = 10;
        int rowH = 12;
        String[][] grid = {
                {
                        summary.mvp != null && !summary.mvp.isEmpty()
                                ? "§e⭐ MVP : §6§l" + summary.mvp + " §r§7(" + summary.mvpContributions + ")"
                                : null,
                        summary.dragonKilledBy != null && !summary.dragonKilledBy.isEmpty()
                                ? "§5🐲 Coup fatal : §d§l" + summary.dragonKilledBy
                                : null
                },
                {
                        summary.topChef != null && !summary.topChef.isEmpty()
                                ? "§a🍗 Top chef : §f§l" + summary.topChef + " §r§7(" + summary.topChefCount + " aliments)"
                                : null,
                        summary.topRotten != null && !summary.topRotten.isEmpty()
                                ? "§2🤢 Chair putride : §f§l" + summary.topRotten + " §r§7(" + summary.topRottenCount + " mangées)"
                                : null
                },
                {
                        "§e🔀 Swaps : §f" + summary.swapCount,
                        "§e👥 Joueurs : §f" + summary.playerCount
                }
        };
        for (String[] row : grid) {
            if (row[0] != null) {
                Text t = Text.literal(row[0]);
                int w = font.getWidth(t);
                ctx.drawText(font, t, cx - colGap - w, by, 0xFFFFFFFF, true);
            }
            if (row[1] != null) {
                Text t = Text.literal(row[1]);
                ctx.drawText(font, t, cx + colGap, by, 0xFFFFFFFF, true);
            }
            by += rowH;
        }
    }

    private void drawMilestoneRow(DrawContext ctx, TextRenderer font, RunSummary.Milestone m,
                                  int x, int y) {
        Identifier id = Identifier.tryParse(m.iconItem);
        Item item = id != null ? net.minecraft.registry.Registries.ITEM.get(id) : Items.AIR;
        ItemStack stack = new ItemStack(item);
        ctx.drawItem(stack, x, y - 1);
        if (!m.reached) {
            ctx.fill(x + 1, y, x + 15, y + 14, 0xC0000000);
            ctx.fill(x, y + 1, x + 16, y + 13, 0xC0000000);
        }

        String labelText = switch (m.key) {
            case "food" -> "Nourriture";
            case "iron" -> "Lingot fer";
            case "diamond" -> "Diamant";
            case "nether" -> "Nether";
            case "blaze" -> "Bâton blaze";
            case "pearl" -> "Perle Ender";
            case "eye" -> "Œil Ender";
            case "end" -> "Œil dans portail";
            case "dragon" -> "Ender Dragon";
            default -> m.key;
        };

        String stateText;
        int color;
        if (m.reached) {
            stateText = " §a✓ §f" + labelText + " §7- §e" + (m.by != null ? m.by : "?");
            color = 0xFFFFFFFF;
        } else {
            stateText = " §c✘ §8" + labelText;
            color = 0xFFAAAAAA;
        }
        ctx.drawTextWithShadow(font, stateText, x + 20, y + 4, color);
    }

    private void drawAchievementRow(DrawContext ctx, TextRenderer font, Item icon, String key,
                                    boolean unlocked, String by, int x, int y, int maxWidth) {
        ItemStack stack = new ItemStack(icon);
        ctx.drawItem(stack, x, y);
        if (!unlocked) {
            ctx.fill(x + 1, y + 1, x + 15, y + 15, 0xC0000000);
            ctx.fill(x, y + 2, x + 16, y + 14, 0xC0000000);
        }

        String label = getAchievementLabel(key);
        String stateText;
        if (unlocked) {
            stateText = " §a✓ §f" + label + (by != null ? " §7- §e" + by : "");
        } else {
            stateText = " §c✘ §8" + label;
        }

        String trimmed = stateText;
        int textMaxWidth = maxWidth - 18;
        while (font.getWidth(trimmed) > textMaxWidth && trimmed.length() > 5) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        ctx.drawTextWithShadow(font, trimmed, x + 18, y + 5, 0xFFFFFFFF);
    }

    private static String getAchievementLabel(String key) {
        return switch (key) {
            case "first_obsidian" -> "Obsidienne";
            case "first_flint_steel" -> "Briquet";
            case "first_iron_pickaxe" -> "Pioche fer";
            case "first_iron_armor" -> "Set fer complet";
            case "first_diamond_pickaxe" -> "Pioche diamant";
            case "first_diamond_sword" -> "Épée diamant";
            case "first_gold_ingot" -> "Lingot d'or";
            case "first_golden_apple" -> "Pomme dorée";
            case "first_bed" -> "Lit";
            case "first_wither_skull" -> "Crâne wither";
            case "first_nether_portal_built" -> "Portail Nether";
            case "first_stronghold" -> "Stronghold";
            case "first_warped_forest" -> "Forêt Warped";
            case "first_death" -> "Premier mort";
            default -> key;
        };
    }

    private static String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        return String.format("%dm %02ds", m, s);
    }
}
