package dev.sharedrun.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class TimerHud {
    public static volatile int remainingSeconds = 5400;
    public static volatile int totalSeconds = 5400;
    public static volatile boolean running = true;
    public static volatile boolean everReceived = false;

    private TimerHud() {}

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        if (mc.player == null) return;

        TextRenderer font = mc.textRenderer;
        String text = formatTime(remainingSeconds);
        int textWidth = font.getWidth(text);
        int padding = 6;
        int rectW = textWidth + padding * 2;
        int rectH = font.fontHeight + padding * 2;
        int screenW = ctx.getScaledWindowWidth();
        int x = screenW - rectW - 6;
        int y = 6;

        ctx.fill(x, y, x + rectW, y + rectH, 0x80000000);

        int color = colorForRemaining(remainingSeconds);
        if (remainingSeconds <= 10 && remainingSeconds > 0) {
            long ms = System.currentTimeMillis();
            if ((ms / 333) % 2 == 0) color = 0xFFFFFFFF;
        }

        ctx.drawText(font, text, x + padding, y + padding, color, true);

        // Coordonnées juste sous le timer (sans fond)
        int px = (int) Math.floor(mc.player.getX());
        int py = (int) Math.floor(mc.player.getY());
        int pz = (int) Math.floor(mc.player.getZ());
        String coords = String.format("%d  %d  %d", px, py, pz);
        int coordsW = font.getWidth(coords);
        int coordsX = screenW - coordsW - 6 - padding;
        int coordsY = y + rectH + 4;

        ctx.drawText(font, coords, coordsX, coordsY, 0xFFFFFFFF, true);
    }

    private static String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    private static int colorForRemaining(int seconds) {
        if (seconds <= 600) return 0xFFFF5555;
        if (seconds <= 1800) return 0xFFFFFF55;
        return 0xFFFFFFFF;
    }
}
