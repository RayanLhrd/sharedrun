package dev.sharedrun.client;

import dev.sharedrun.endrun.LeaderboardEntry;
import dev.sharedrun.network.LeaderboardRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.glfw.GLFW;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LeaderboardScreen extends Screen {

    private enum SortMode { DATE, OBJECTIVES, DAMAGE, TIME, FURTHEST }

    private static final int ROW_H        = 36;
    private static final int PADDING      = 8;
    private static final int HEADER_H     = 30;
    private static final int CONTROLS_H   = 22;
    private static final int FOOTER_H     = 30;
    private static final int SCROLL_SPEED = 12;

    private static final String[] MILESTONE_ORDER =
            {"food", "iron", "diamond", "nether", "blaze", "pearl", "eye", "end", "dragon"};

    private final List<LeaderboardEntry> allEntries;
    private List<LeaderboardEntry> displayEntries;
    private boolean showOnlyVictories = false;
    private SortMode sortMode = SortMode.DATE;

    private int scrollOffset  = 0;
    private int hoveredIndex  = -1;
    private boolean prevMouseDown = true;

    public LeaderboardScreen(List<LeaderboardEntry> entries) {
        super(Text.literal("Leaderboard"));
        this.allEntries     = entries;
        this.displayEntries = new ArrayList<>(entries);
    }

    // ── Display list ───────────────────────────────────────────────────────────

    private void rebuildDisplay() {
        List<LeaderboardEntry> filtered = new ArrayList<>(allEntries);
        if (showOnlyVictories) {
            filtered.removeIf(e -> e.endReason != 0);
        }
        switch (sortMode) {
            case OBJECTIVES -> filtered.sort(
                    Comparator.comparingInt((LeaderboardEntry e) -> countReachedMilestones(e)).reversed());
            case DAMAGE -> filtered.sort(
                    Comparator.comparingDouble((LeaderboardEntry e) -> (double) totalHearts(e)).reversed());
            case TIME -> filtered.sort(
                    Comparator.comparingInt((LeaderboardEntry e) -> e.elapsedSeconds));
            case FURTHEST -> filtered.sort(
                    Comparator.comparingInt((LeaderboardEntry e) -> furthestMilestone(e)).reversed());
            default -> {} // DATE: keep API order
        }
        displayEntries = filtered;
        scrollOffset   = 0;
    }

    private static int countReachedMilestones(LeaderboardEntry e) {
        if (e.milestones == null) return 0;
        int count = 0;
        for (LeaderboardEntry.Milestone m : e.milestones) {
            if (m.reached) count++;
        }
        return count;
    }

    private static int furthestMilestone(LeaderboardEntry e) {
        if (e.milestones == null) return -1;
        int furthest = -1;
        for (int i = 0; i < MILESTONE_ORDER.length; i++) {
            for (LeaderboardEntry.Milestone m : e.milestones) {
                if (MILESTONE_ORDER[i].equals(m.key) && m.reached) {
                    furthest = i;
                    break;
                }
            }
        }
        return furthest;
    }

    // ── Screen lifecycle ───────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        prevMouseDown = true;
        rebuildDisplay();

        int btnY = HEADER_H + 2;
        int btnH = CONTROLS_H - 4;
        int btnW = 72;
        int gap  = 3;

        // Sort buttons
        SortMode[] modes  = SortMode.values();
        String[]   labels = {"📅 Date", "🎯 Objectifs", "❤ Dégâts", "⏱ Temps", "📍 Plus loin"};
        for (int i = 0; i < modes.length; i++) {
            final SortMode mode  = modes[i];
            final String   label = labels[i];
            boolean active = sortMode == mode;
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(active ? "§e§l" + label : "§7" + label),
                    btn -> {
                        sortMode = mode;
                        rebuildDisplay();
                        this.init(this.width, this.height);
                    }
            ).dimensions(PADDING + i * (btnW + gap), btnY, btnW, btnH).build());
        }

        // Victory-only toggle
        int toggleW = 118;
        int toggleX = this.width - PADDING - toggleW;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(showOnlyVictories ? "§a✓ Victoires seul." : "§7○ Victoires seul."),
                btn -> {
                    showOnlyVictories = !showOnlyVictories;
                    rebuildDisplay();
                    this.init(this.width, this.height);
                }
        ).dimensions(toggleX, btnY, toggleW, btnH).build());

        // Refresh + Close buttons
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§b🔄 Actualiser"),
                btn -> {
                    ClientPlayNetworking.send(new LeaderboardRequestPayload());
                    this.close();
                }
        ).dimensions(this.width / 2 - 107, this.height - FOOTER_H + 5, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§7✕ Fermer"),
                btn -> this.close()
        ).dimensions(this.width / 2 + 7, this.height - FOOTER_H + 5, 100, 20).build());
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, displayEntries.size() * ROW_H - listHeight());
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * SCROLL_SPEED));
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hoveredIndex = entryIndexAt(mouseY);

        boolean mouseDown = GLFW.glfwGetMouseButton(
                this.client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
        if (mouseDown && !prevMouseDown && hoveredIndex >= 0 && hoveredIndex < displayEntries.size()) {
            this.client.setScreen(new RunSummaryScreen(displayEntries.get(hoveredIndex).toRunSummary(), this));
            prevMouseDown = true;
            return;
        }
        prevMouseDown = mouseDown;

        ctx.fill(0, 0, this.width, this.height, 0xD8101010);

        TextRenderer font = this.textRenderer;
        int cx = this.width / 2;

        // === Header ===
        ctx.fill(0, 0, this.width, HEADER_H, 0xFF1A1A1A);
        ctx.drawCenteredTextWithShadow(font,
                Text.literal("🏆 Leaderboard — Runs récentes").formatted(Formatting.GOLD, Formatting.BOLD),
                cx, 5, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(font,
                Text.literal("Clique sur une run pour voir le détail").formatted(Formatting.DARK_GRAY),
                cx, 17, 0xFF555555);

        // === Controls bar background ===
        ctx.fill(0, HEADER_H, this.width, HEADER_H + CONTROLS_H, 0xFF141414);

        // === Scissored list zone ===
        int listTop    = HEADER_H + CONTROLS_H + 2;
        int listBottom = this.height - FOOTER_H;
        ctx.enableScissor(0, listTop, this.width, listBottom);

        if (displayEntries.isEmpty()) {
            ctx.drawCenteredTextWithShadow(font,
                    Text.literal(showOnlyVictories
                            ? "Aucune victoire enregistrée."
                            : "Aucune run enregistrée.").formatted(Formatting.GRAY),
                    cx, listTop + 20, 0xFFAAAAAA);
        } else {
            int y = listTop - scrollOffset;
            for (int i = 0; i < displayEntries.size(); i++) {
                drawEntry(ctx, font, displayEntries.get(i), i, y, i == hoveredIndex);
                y += ROW_H;
            }
        }

        ctx.disableScissor();

        // === Footer divider ===
        ctx.fill(0, listBottom, this.width, listBottom + 1, 0xFF333333);

        drawScrollbar(ctx, listTop, listBottom);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ── Row rendering ──────────────────────────────────────────────────────────

    private void drawEntry(DrawContext ctx, TextRenderer font, LeaderboardEntry e, int index, int y, boolean hovered) {
        int rowY = y + 2;
        int bgColor = hovered ? 0x44FFDD55 : ((index % 2 == 0) ? 0x22FFFFFF : 0x11FFFFFF);
        ctx.fill(PADDING, rowY, this.width - PADDING, rowY + ROW_H - 2, bgColor);
        if (hovered) {
            ctx.fill(PADDING, rowY,             this.width - PADDING, rowY + 1,         0x88FFDD55);
            ctx.fill(PADDING, rowY + ROW_H - 3, this.width - PADDING, rowY + ROW_H - 2, 0x88FFDD55);
        }

        int textX     = PADDING + 6;
        int rightEdge = this.width - PADDING - 6;

        // Line 1: result | time | players | date
        String resultBadge = switch (e.endReason) {
            case 0 -> "§a✓ VICTOIRE";
            case 1 -> "§e⏱ TIMEOUT";
            case 2 -> "§c✗ DÉFAITE";
            default -> "§8?";
        };
        String timeStr   = "§f⏱ " + formatTime(e.elapsedSeconds) + " §7/ §f" + formatTime(e.totalSeconds);
        String playerStr = "§b👥 " + e.playerCount + " joueur" + (e.playerCount > 1 ? "s" : "");
        String dateStr   = "§8" + formatDate(e.eventTimestamp);

        ctx.drawTextWithShadow(font, resultBadge,  textX,       rowY + 2, 0xFFFFFFFF);
        ctx.drawTextWithShadow(font, timeStr,       textX + 90,  rowY + 2, 0xFFFFFFFF);
        ctx.drawTextWithShadow(font, playerStr,     textX + 230, rowY + 2, 0xFFFFFFFF);
        int dateW = font.getWidth(dateStr);
        ctx.drawTextWithShadow(font, dateStr, rightEdge - dateW, rowY + 2, 0xFF888888);

        // Line 2: milestone icons | hearts lost | seed
        StringBuilder milestones = new StringBuilder();
        if (e.milestones != null) {
            String[] keys  = {"food", "iron", "diamond", "nether", "blaze", "pearl", "eye", "end", "dragon"};
            String[] icons = {"🍞", "⚙", "💎", "🔥", "🔥", "🌀", "👁", "🌀", "🐲"};
            for (int i = 0; i < keys.length; i++) {
                boolean reached = false;
                for (LeaderboardEntry.Milestone m : e.milestones) {
                    if (keys[i].equals(m.key)) { reached = m.reached; break; }
                }
                milestones.append(reached ? "§a" : "§8").append(icons[i]).append(" ");
            }
        }
        ctx.drawTextWithShadow(font, milestones.toString(), textX, rowY + 14, 0xFFFFFFFF);
        // Use cumulative per-player hearts (same metric as RunSummaryScreen "Top dégâts pris")
        ctx.drawTextWithShadow(font, "§c❤ -" + String.format("%.1f", totalHearts(e)), textX + 230, rowY + 14, 0xFFFFFFFF);
        if (e.seed != null && !e.seed.isEmpty()) {
            String seedStr = "§8seed: §7" + e.seed;
            int seedW = font.getWidth(seedStr);
            ctx.drawTextWithShadow(font, seedStr, rightEdge - seedW, rowY + 14, 0xFF666666);
        }

        // Line 3: player names + MVP
        String names = e.participants != null && !e.participants.isEmpty()
                ? String.join("§7, §f", e.participants) : "§8inconnu";
        String line3 = "§f" + names;
        if (e.mvp != null && !e.mvp.isEmpty() && e.playerCount > 1) {
            line3 += "  §e⭐" + e.mvp;
        }
        int maxLineW = rightEdge - textX - 10;
        while (font.getWidth(line3) > maxLineW && line3.length() > 6) {
            line3 = line3.substring(0, line3.length() - 1);
        }
        ctx.drawTextWithShadow(font, line3, textX, rowY + 24, 0xFFCCCCCC);
    }

    // ── Scrollbar ─────────────────────────────────────────────────────────────

    private void drawScrollbar(DrawContext ctx, int listTop, int listBottom) {
        int totalContent = displayEntries.size() * ROW_H;
        int listH = listHeight();
        if (totalContent <= listH) return;
        int scrollbarX = this.width - 5;
        int trackH     = listBottom - listTop;
        float thumbFraction  = (float) listH / totalContent;
        int   thumbH         = Math.max(20, (int) (trackH * thumbFraction));
        float scrollFraction = (float) scrollOffset / (totalContent - listH);
        int   thumbY         = listTop + (int) ((trackH - thumbH) * scrollFraction);
        ctx.fill(scrollbarX, listTop, scrollbarX + 3, listBottom,      0xFF222222);
        ctx.fill(scrollbarX, thumbY,  scrollbarX + 3, thumbY + thumbH, 0xFF888888);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static float totalHearts(LeaderboardEntry e) {
        if (e.leaderboard == null || e.leaderboard.isEmpty()) return 0f;
        float sum = 0f;
        for (LeaderboardEntry.LeaderEntry le : e.leaderboard) {
            sum += le.hearts;
        }
        return sum;
    }

    private int entryIndexAt(int mouseY) {
        int listTop    = HEADER_H + CONTROLS_H + 2;
        int listBottom = this.height - FOOTER_H;
        if (mouseY < listTop || mouseY >= listBottom) return -1;
        int idx = (mouseY - listTop + scrollOffset) / ROW_H;
        return (idx >= 0 && idx < displayEntries.size()) ? idx : -1;
    }

    private int listHeight() {
        return this.height - HEADER_H - CONTROLS_H - 2 - FOOTER_H;
    }

    private static String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format("%dh%02dm%02ds", h, m, s);
        return String.format("%dm%02ds", m, s);
    }

    private static String formatDate(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        try {
            String s = iso.replace("T", " ");
            int colon2 = s.indexOf(':', s.indexOf(':') + 1);
            if (colon2 > 0) s = s.substring(0, colon2);
            return s;
        } catch (Exception e) {
            return iso;
        }
    }
}
