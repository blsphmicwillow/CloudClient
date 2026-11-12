package net.cloudclient.hud;

import net.cloudclient.CloudClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

/**
 * Minimal HUD: FPS, CPS, keystrokes and armor durability.
 *
 * <p>All elements are pure overlays drawn after vanilla; none of them read or
 * write game state, so they cannot change gameplay.
 */
public final class Hud {

    /** W on top, A/S/D beneath: (column, row) per key, in W A S D order. */
    private static final int[][] KEY_LAYOUT = {
            {1, 0}, {0, 1}, {1, 1}, {2, 1}
    };

    private final CpsTracker cps = new CpsTracker();

    public void render(CloudClientRuntime runtime, Minecraft mc) {
        if (mc.thePlayer == null || mc.gameSettings.hideGUI) return;
        cps.sample();

        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (runtime.isEnabled("hud.fps")) drawFps(runtime, mc);
        if (runtime.isEnabled("hud.cps")) drawCps(runtime, mc);
        if (runtime.isEnabled("hud.keystrokes")) drawKeystrokes(runtime, mc);
        if (runtime.isEnabled("hud.armor")) drawArmor(runtime, mc);

        GlStateManager.disableBlend();
    }

    private void drawFps(CloudClientRuntime runtime, Minecraft mc) {
        FontRenderer font = mc.fontRendererObj;
        int x = runtime.config().getInt("hud.fps.x", 4);
        int y = runtime.config().getInt("hud.fps.y", 4);
        String text = "FPS " + Minecraft.getDebugFPS();
        font.drawStringWithShadow(text, x, y, 0xFFFFFF);
    }

    private void drawCps(CloudClientRuntime runtime, Minecraft mc) {
        FontRenderer font = mc.fontRendererObj;
        int x = runtime.config().getInt("hud.cps.x", 4);
        int y = runtime.config().getInt("hud.cps.y", 16);
        String text = "CPS " + cps.left() + "/" + cps.right();
        font.drawStringWithShadow(text, x, y, 0xFFE0A0);
    }

    private void drawKeystrokes(CloudClientRuntime runtime, Minecraft mc) {
        GameSettings settings = mc.gameSettings;
        int baseX = runtime.config().getInt("hud.keystrokes.x", 4);
        int baseY = runtime.config().getInt("hud.keystrokes.y", 60);
        int size = runtime.config().getIntClamped("hud.keystrokes.size", 14, 10, 28);
        int gap = 2;

        boolean[] pressed = {
                settings.keyBindForward.getIsKeyPressed(),
                settings.keyBindLeft.getIsKeyPressed(),
                settings.keyBindBack.getIsKeyPressed(),
                settings.keyBindRight.getIsKeyPressed()
        };
        String[] labels = {"W", "A", "S", "D"};

        FontRenderer font = mc.fontRendererObj;
        for (int i = 0; i < 4; i++) {
            int col = KEY_LAYOUT[i][0];
            int row = KEY_LAYOUT[i][1];
            int x = baseX + col * (size + gap);
            int y = baseY + row * (size + gap);
            int background = pressed[i] ? 0x80FFFFFF : 0x60000000;
            Gui.drawRect(x, y, x + size, y + size, background);
            int colour = pressed[i] ? 0x000000 : 0xFFFFFF;
            font.drawStringWithShadow(labels[i],
                    x + (size - font.getStringWidth(labels[i])) / 2,
                    y + (size - 8) / 2, colour);
        }
    }

    private void drawArmor(CloudClientRuntime runtime, Minecraft mc) {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;
        // 1.8.9 stores armor as a fixed array, not a List (that changed in 1.9).
        ItemStack[] armor = player.inventory.armorInventory;

        int x = runtime.config().getInt("hud.armor.x", 4);
        int y = runtime.config().getInt("hud.armor.y", 100);
        FontRenderer font = mc.fontRendererObj;

        int count = 0;
        for (int i = 0; i < armor.length && i < 4; i++) {
            ItemStack stack = armor.get(i);
            if (stack == null) continue;
            int max = stack.getMaxDamage();
            if (max <= 0) continue;
            int remaining = max - stack.getItemDamage();
            int percent = (int) Math.round(remaining * 100.0D / max);
            int colour = percent > 50 ? 0x55FF55 : (percent > 20 ? 0xFFAA00 : 0xFF5555);
            font.drawStringWithShadow(percent + "%", x, y + count * 10, colour);
            count++;
        }
        if (count == 0) {
            font.drawStringWithShadow("no armor", x, y, 0x888888);
        }
    }

    /**
     * Samples the mouse at frame rate to derive clicks per second.
     *
     * <p>Polling at frame rate (rather than per tick) catches click bursts that
     * a 20 Hz sample would miss; the counters roll over on a one second window.
     */
    static final class CpsTracker {
        private static final long WINDOW_MS = 1000L;

        private boolean lastLeft;
        private boolean lastRight;
        private int leftClicks;
        private int rightClicks;
        private long windowStart = System.currentTimeMillis();

        void sample() {
            boolean left = Mouse.isButtonDown(0);
            boolean right = Mouse.isButtonDown(1);
            if (left && !lastLeft) leftClicks++;
            if (right && !lastRight) rightClicks++;
            lastLeft = left;
            lastRight = right;

            long now = System.currentTimeMillis();
            if (now - windowStart >= WINDOW_MS) {
                // Keep the last second's count by decaying instead of clearing,
                // which avoids a flashing zero between windows.
                windowStart = now;
                leftClicks = Math.max(0, leftClicks - Math.max(1, leftClicks / 2));
                rightClicks = Math.max(0, rightClicks - Math.max(1, rightClicks / 2));
            }
        }

        int left() {
            return leftClicks;
        }

        int right() {
            return rightClicks;
        }
    }
}
