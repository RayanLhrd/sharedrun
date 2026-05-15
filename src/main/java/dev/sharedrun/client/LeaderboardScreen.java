package dev.sharedrun.client;

import dev.sharedrun.endrun.LeaderboardEntry;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class LeaderboardScreen extends Screen {

    private static final int ROW_H       = 36;
    private static final int PADDING     = 8;
    private static final int HEADER_H    = 28;
    private static final int FOOTER_H    = 30;
    private static final int SCROLL_SPEED = 12;

    private final List<LeaderboardEntry> entries;
    private int scrollOffset = 0;

    public LeaderboardScreen(List<LeaderboardEntry> entries) {
        super(Text.literal("Leaderboard"));
        this.entries = entries;
    }

    @Override
    protected void init() {
        super.init();
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
        ctx.fill(0, 0, this.width, this.height, 0xD8101010);

        TextRenderer font = this.textRenderer;
        int cx = this.width / 2;

        // === Header ===
        ctx.fill(0, 0, this.width, HEADER_H, 0xFF1A1A1A);
        ctx.drawCenteredTextWithShadow(font,
                Text.literal("🏆 Leaderboard — Runs récentes").formatted(Formatting.GOLD, Formatting.BOLD),
                cx, 8, 0xFFFFD700);

        // === Scissor list zone ===
        int listTop = HEADER_H + 2;
        int listBottom = this.height - FOOTER_H;
        ctx.enableScissor(0, listTop, this.width, listBottom);

        if (entries.isEmpty()) {
            ctx.drawCenteredTextWithShadow(font,
                    Text.literal("Aucune run enregistrée.").formatted(Formatting.GRAY),
                    cx, listTop + 20 - scrollOffset, 0xFFAAAAAA);
        } else {
            int y = listTop - scrollOffset;
            for (int i = 0; i < entries.size(); i++) {
                drawEntry(ctx, font, entries.get(i), i, y);
                y += ROW_H;
            }
        }

        ctx.disableScissor();

        // === Footer divider ===
        ctx.fill(0, listBottom, this.width, listBottom + 1, 0xFF333333);

        // scrollbar
        drawScrollbar(ctx, listTop, listBottom);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawEntry(DrawContext ctx, TextRenderer font, LeaderboardEntry e, int index, int y) {
        int rowY = y + 2;
        boolean even = (index % 2 == 0);
        ctx.fill(PADDING, rowY, this.width - PADDING, rowY + ROW_H - 2, even ? 0x22FFFFFF : 0x11FFFFFF);

        int textX = PADDING + 6;
        int rightEdge = this.width - PADDING - 6;

        // === Line 1: result badge + time + players + date ===
        String resultBadge = switch (e.endReason) {
            case 0 -> "§a✓ VICTOIRE";
            case 1 -> "§e⏱ TIMEOUT";
            case 2 -> "§c✗ DÉFAITE";
            default -> "§8?";
        };
        String timeStr = formatTime(e.elapsedSeconds) + " §7/ §f" + formatTime(e.totalSeconds);
        String playerStr = e.playerCount + " joueur" + (e.playerCount > 1 ? "s" : "");
        String dateStr = formatDate(e.eventTimestamp);

        ctx.drawTextWithShadow(font, resultBadge, textX, rowY + 2, 0xFFFFFFFF);
        ctx.drawTextWithShadow(font, "§f⏱ " + timeStr, textX + 90, rowY + 2, 0xFFFFFFFF);
        ctx.drawTextWithShadow(font, "§b👥 " + playerStr, textX + 230, rowY + 2, 0xFFFFFFFF);

        // date right-aligned
        int dateW = font.getWidth(dateStr);
        ctx.drawTextWithShadow(font, dateStr, rightEdge - dateW, rowY + 2, 0xFF888888);

        // === Line 2: milestones + HP lost + seed ===
        StringBuilder milestones = new StringBuilder("§7");
        if (e.milestones != null) {
            String[] keys = {"food","iron","diamond","nether","blaze","pearl","eye","end","dragon"};
            String[] icons = {"🍞","⚙","💎","🔥","🔥","🌀","👁","🌀","🐲"};
            for (int i = 0; i < keys.length; i++) {
                boolean reached = false;
                for (LeaderboardEntry.Milestone m : e.milestones) {
                    if (keys[i].equals(m.key)) { reached = m.reached; break; }
                }
                milestones.append(reached ? "§a" : "§8").append(icons[i]).append(" ");
            }
        }
        ctx.drawTextWithShadow(font, milestones.toString(), textX, rowY + 14, 0xFFFFFFFF);

        String hpStr = "§c❤ -" + String.format("%.1f", e.hpLost);
        ctx.drawTextWithShadow(font, hpStr, textX + 230, rowY + 14, 0xFFFFFFFF);

        if (e.seed != null && !e.seed.isEmpty()) {
            String seedStr = "§8seed: §7" + e.seed;
            int seedW = font.getWidth(seedStr);
            ctx.drawTextWithShadow(font, seedStr, rightEdge - seedW, rowY + 14, 0xFF666666);
        }

        // === Line 3 (compact): player names + MVP ===
        String names = e.participants != null && !e.participants.isEmpty()
                ? String.join("§7, §f", e.participants)
                : "§8inconnu";
        String line3 = "§f" + names;
        if (e.mvp != null && !e.mvp.isEmpty() && e.playerCount > 1) {
            line3 += "  §e⭐" + e.mvp;
        }
        // truncate if too long
        String line3Rendered = line3;
        int maxLineW = rightEdge - textX - 10;
        while (font.getWidth(line3Rendered) > maxLineW && line3Rendered.length() > 6) {
            line3Rendered = line3Rendered.substring(0, line3Rendered.length() - 1);
        }
        ctx.drawTextWithShadow(font, line3Rendered, textX, rowY + 24, 0xFFCCCCCC);
    }

    private void drawScrollbar(DrawContext ctx, int listTop, int listBottom) {
        int totalContent = entries.size() * ROW_H;
        int listH = listHeight();
        if (totalContent <= listH) return;

        int scrollbarX = this.width - 5;
        int trackH = listBottom - listTop;
        float thumbFraction = (float) listH / totalContent;
        int thumbH = Math.max(20, (int) (trackH * thumbFraction));
        float scrollFraction = (float) scrollOffset / (totalContent - listH);
        int thumbY = listTop + (int) ((trackH - thumbH) * scrollFraction);

        ctx.fill(scrollbarX, listTop, scrollbarX + 3, listBottom, 0xFF222222);
        ctx.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbH, 0xFF888888);
    }

    private int listHeight() {
        return this.height - HEADER_H - 2 - FOOTER_H;
    }

    private static String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format("%dh%02dm%02ds", h, m, s);
        return String.format("%dm%02ds", m, s);
    }

    /** Extrait "YYYY-MM-DD HH:mm" depuis une ISO 8601 string ou retourne la string brute. */
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
