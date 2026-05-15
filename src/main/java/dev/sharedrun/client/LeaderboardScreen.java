package dev.sharedrun.client;

import dev.sharedrun.endrun.LeaderboardEntry;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.glfw.GLFW;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class LeaderboardScreen extends Screen {

    private static final int ROW_H       = 36;
    private static final int PADDING     = 8;
    private static final int HEADER_H    = 30;
    private static final int FOOTER_H    = 30;
    private static final int SCROLL_SPEED = 12;

    private final List<LeaderboardEntry> entries;
    private int scrollOffset  = 0;
    private int hoveredIndex  = -1;
    /** Tracks previous LMB state to fire the open only on press (not hold). */
    private boolean prevMouseDown = true; // true = prevent spurious click on screen open

    public LeaderboardScreen(List<LeaderboardEntry> entries) {
        super(Text.literal("Leaderboard"));
        this.entries = entries;
    }

    @Override
    protected void init() {
        super.init();
        prevMouseDown = true; // prevent spurious click on (re-)open
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§7✕ Fermer"),
                btn -> this.close()
        ).dimensions(this.width / 2 - 50, this.height - FOOTER_H + 5, 100, 20).build());
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, entries.size() * ROW_H - listHeight());
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * SCROLL_SPEED));
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Recompute hovered index every frame so it stays correct after scrolling.
        hoveredIndex = entryIndexAt(mouseY);

        // Click detection via GLFW polling — immune to MC API version changes.
        boolean mouseDown = GLFW.glfwGetMouseButton(
                this.client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
        if (mouseDown && !prevMouseDown && hoveredIndex >= 0 && hoveredIndex < entries.size()) {
            int idx = hoveredIndex;
            this.client.setScreen(new RunSummaryScreen(entries.get(idx).toRunSummary(), this));
            prevMouseDown = true;
            return; // screen replaced, skip rendering this frame
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

        // === Scissor list zone ===
        int listTop    = HEADER_H + 2;
        int listBottom = this.height - FOOTER_H;
        ctx.enableScissor(0, listTop, this.width, listBottom);

        if (entries.isEmpty()) {
            ctx.drawCenteredTextWithShadow(font,
                    Text.literal("Aucune run enregistrée.").formatted(Formatting.GRAY),
                    cx, listTop + 20, 0xFFAAAAAA);
        } else {
            int y = listTop - scrollOffset;
            for (int i = 0; i < entries.size(); i++) {
                drawEntry(ctx, font, entries.get(i), i, y, i == hoveredIndex);
                y += ROW_H;
            }
        }

        ctx.disableScissor();

        // === Footer divider ===
        ctx.fill(0, listBottom, this.width, listBottom + 1, 0xFF333333);

        drawScrollbar(ctx, listTop, listBottom);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawEntry(DrawContext ctx, TextRenderer font, LeaderboardEntry e, int index, int y, boolean hovered) {
        int rowY = y + 2;
        int bgColor = hovered ? 0x44FFDD55 : ((index % 2 == 0) ? 0x22FFFFFF : 0x11FFFFFF);
        ctx.fill(PADDING, rowY, this.width - PADDING, rowY + ROW_H - 2, bgColor);
        if (hovered) {
            ctx.fill(PADDING,                rowY,             this.width - PADDING, rowY + 1,         0x88FFDD55);
            ctx.fill(PADDING,                rowY + ROW_H - 3, this.width - PADDING, rowY + ROW_H - 2, 0x88FFDD55);
        }

        int textX    = PADDING + 6;
        int rightEdge = this.width - PADDING - 6;

        // === Line 1: result + time + players + date ===
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

        // === Line 2: milestone icons + HP lost + seed ===
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
        ctx.drawTextWithShadow(font, "§c❤ -" + String.format("%.1f", e.hpLost), textX + 230, rowY + 14, 0xFFFFFFFF);
        if (e.seed != null && !e.seed.isEmpty()) {
            String seedStr = "§8seed: §7" + e.seed;
            int seedW = font.getWidth(seedStr);
            ctx.drawTextWithShadow(font, seedStr, rightEdge - seedW, rowY + 14, 0xFF666666);
        }

        // === Line 3: player names + MVP ===
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

    private void drawScrollbar(DrawContext ctx, int listTop, int listBottom) {
        int totalContent = entries.size() * ROW_H;
        int listH = listHeight();
        if (totalContent <= listH) return;
        int scrollbarX = this.width - 5;
        int trackH     = listBottom - listTop;
        float thumbFraction = (float) listH / totalContent;
        int thumbH     = Math.max(20, (int) (trackH * thumbFraction));
        float scrollFraction = (float) scrollOffset / (totalContent - listH);
        int thumbY     = listTop + (int) ((trackH - thumbH) * scrollFraction);
        ctx.fill(scrollbarX, listTop,  scrollbarX + 3, listBottom,       0xFF222222);
        ctx.fill(scrollbarX, thumbY,   scrollbarX + 3, thumbY + thumbH,  0xFF888888);
    }

    private int entryIndexAt(int mouseY) {
        int listTop    = HEADER_H + 2;
        int listBottom = this.height - FOOTER_H;
        if (mouseY < listTop || mouseY >= listBottom) return -1;
        int idx = (mouseY - listTop + scrollOffset) / ROW_H;
        return (idx >= 0 && idx < entries.size()) ? idx : -1;
    }

    private int listHeight() {
        return this.height - HEADER_H - 2 - FOOTER_H;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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
            // "2024-11-15T22:30:00.000Z" → "2024-11-15 22:30"
            String s = iso.replace("T", " ");
            int colon2 = s.indexOf(':', s.indexOf(':') + 1);
            if (colon2 > 0) s = s.substring(0, colon2);
            return s;
        } catch (Exception e) {
            return iso;
        }
    }
}
