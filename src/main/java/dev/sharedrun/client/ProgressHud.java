package dev.sharedrun.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

public final class ProgressHud {
    public static volatile int flags = 0;
    public static volatile int foodCount = 0;
    public static volatile int ironIngots = 0;
    public static volatile int diamonds = 0;
    public static volatile int blazeRods = 0;
    public static volatile int enderPearls = 0;
    public static volatile int eyesOfEnder = 0;

    private static final Item[] ICONS = {
            Items.BREAD,
            Items.IRON_INGOT,
            Items.DIAMOND,
            Items.NETHERRACK,
            Items.BLAZE_ROD,
            Items.ENDER_PEARL,
            Items.ENDER_EYE,
            Items.END_PORTAL_FRAME,
            Items.DRAGON_HEAD
    };

    private static final Identifier SLOT_TEXTURE = Identifier.of("sharedrun", "textures/gui/slot.png");
    private static final Identifier GOLD_TEXTURE = Identifier.of("sharedrun", "textures/gui/gold.png");
    private static final int DRAGON_INDEX = 8;

    private static final int TEXTURE_SIZE = 26;
    private static final int ICON_SIZE = 16;
    private static final int SLOT_SIZE = 22;
    private static final int SPACING = 24;
    private static final int RIGHT_MARGIN = 6;

    private static final int COLOR_FULL = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xB0FFFFFF;
    private static final int OVERLAY_DARK = 0x60000000;

    private ProgressHud() {}

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null) return;

        TextRenderer font = mc.textRenderer;
        int screenW = ctx.getScaledWindowWidth();
        int screenH = ctx.getScaledWindowHeight();

        int slotX = screenW - SLOT_SIZE - RIGHT_MARGIN;
        int iconOffset = (SLOT_SIZE - ICON_SIZE) / 2;
        int iconX = slotX + iconOffset;

        int totalSpan = (ICONS.length - 1) * SPACING + SLOT_SIZE;
        int slotTop = (screenH - totalSpan) / 2;

        boolean[] achieved = {
                (flags & 1) != 0,    // food
                (flags & 2) != 0,    // iron
                (flags & 4) != 0,    // diamond
                (flags & 8) != 0,    // nether
                (flags & 16) != 0,   // blaze
                (flags & 32) != 0,   // pearl
                (flags & 64) != 0,   // eye
                (flags & 128) != 0,  // end
                (flags & 256) != 0   // dragon
        };
        int[] counts = { foodCount, ironIngots, diamonds, -1, blazeRods, enderPearls, eyesOfEnder, -1, -1 };

        for (int i = 0; i < ICONS.length; i++) {
            int slotY = slotTop + (ICONS.length - 1 - i) * SPACING;
            int iconY = slotY + iconOffset;
            boolean isAchieved = achieved[i];

            Identifier slotTex = (i == DRAGON_INDEX) ? GOLD_TEXTURE : SLOT_TEXTURE;
            int slotColor = isAchieved ? COLOR_FULL : COLOR_DIM;
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, slotTex,
                    slotX, slotY,
                    0.0f, 0.0f,
                    SLOT_SIZE, SLOT_SIZE,
                    TEXTURE_SIZE, TEXTURE_SIZE,
                    TEXTURE_SIZE, TEXTURE_SIZE,
                    slotColor);

            ItemStack stack = new ItemStack(ICONS[i]);
            ctx.drawItem(stack, iconX, iconY);

            if (!isAchieved) {
                ctx.fill(iconX + 1, iconY, iconX + ICON_SIZE - 1, iconY + ICON_SIZE, OVERLAY_DARK);
                ctx.fill(iconX, iconY + 1, iconX + ICON_SIZE, iconY + ICON_SIZE - 1, OVERLAY_DARK);
            }

            if (counts[i] > 0) {
                String text = String.valueOf(counts[i]);
                int textW = font.getWidth(text);
                int tx = iconX + ICON_SIZE - textW + 1;
                int ty = iconY + ICON_SIZE - font.fontHeight + 2;
                ctx.drawText(font, text, tx, ty, 0xFFFFFFFF, true);
            }
        }
    }
}
