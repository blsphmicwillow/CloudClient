package net.cloudclient.hud;

import java.util.LinkedHashMap;
import java.util.Map;

import net.cloudclient.CloudClientRuntime;
import net.cloudclient.core.gui.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

/**
 * Minimal HUD: FPS, CPS, keystrokes and armour durability.
 *
 * <p>Positions live in {@link HudLayout}, which the menu edits by dragging. The
 * HUD never writes game state; it only reads it.
 */
public final class Hud {

    public static final String ID_FPS = "fps";
    public static final String ID_CPS = "cps";
    public static final String ID_KEYSTROKES = "keystrokes";
    public static final String ID_ARMOR = "armor";

    private static final int KEY_SIZE = 14;
    private static final int KEY_GAP = 2;

    /** W on top, A/S/D beneath: (column, row) per key, in W A S D order. */
    private static final int[][] KEY_LAYOUT = {
            {1, 0}, {0, 1}, {1, 1}, {2, 1}
    };

    private final HudLayout layout = new HudLayout();
    private final CpsTracker cps = new CpsTracker();

    public HudLayout layout() {
        return layout;
    }

    /** Default positions, used by the menu's "Reset layout" button. */
    public Map<String, float[]> defaults() {
        Map<String, float[]> map = new LinkedHashMap<String, float[]>();
        map.put(ID_FPS, new float[]{4, 4});
        map.put(ID_CPS, new float[]{4, 16});
        map.put(ID_KEYSTROKES, new float[]{4, 30});
        map.put(ID_ARMOR, new float[]{4, 66});
        return map;
    }

    private void declareElements() {
        int keysWidth = 3 * (KEY_SIZE + KEY_GAP);
        int keysHeight = 2 * (KEY_SIZE + KEY_GAP);
        layout.declare(ID_FPS, "FPS", 4, 4, 40, 10);
        layout.declare(ID_CPS, "CPS", 4, 16, 60, 10);
        layout.declare(ID_KEYSTROKES, "Keys", 4, 30, keysWidth, keysHeight);
        layout.declare(ID_ARMOR, "Armor", 4, 66, 40, 40);
    }

    public void render(CloudClientRuntime runtime, Minecraft mc) {
        if (mc.thePlayer == null || mc.gameSettings.hideGUI) return;
        declareElements();
        cps.sample();

        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        HudLayout.Element fps = layout.get(ID_FPS);
        if (runtime.isEnabled("hud.fps") && fps != null && fps.visible) {
            font(mc).drawStringWithShadow("FPS " + Minecraft.getDebugFPS(),
                    Math.round(fps.x), Math.round(fps.y), 0xFFFFFF);
        }

        HudLayout.Element cpsElement = layout.get(ID_CPS);
        if (runtime.isEnabled("hud.cps") && cpsElement != null && cpsElement.visible) {
            font(mc).drawStringWithShadow("CPS " + cps.left() + "/" + cps.right(),
                    Math.round(cpsElement.x), Math.round(cpsElement.y), 0xFFE0A0);
        }

        HudLayout.Element keys = layout.get(ID_KEYSTROKES);
        if (runtime.isEnabled("hud.keystrokes") && keys != null && keys.visible) {
            drawKeystrokes(mc, Math.round(keys.x), Math.round(keys.y));
        }

        HudLayout.Element armor = layout.get(ID_ARMOR);
        if (runtime.isEnabled("hud.armor") && armor != null && armor.visible) {
            drawArmor(mc, Math.round(armor.x), Math.round(armor.y));
        }

        GlStateManager.disableBlend();
    }

    private FontRenderer font(Minecraft mc) {
        return mc.fontRendererObj;
    }

    private void drawKeystrokes(Minecraft mc, int baseX, int baseY) {
        GameSettings settings = mc.gameSettings;
        // 1.8.9 KeyBinding exposes isKeyDown(); getIsKeyPressed() is a later name.
        boolean[] pressed = {
                settings.keyBindForward.isKeyDown(),
                settings.keyBindLeft.isKeyDown(),
                settings.keyBindBack.isKeyDown(),
                settings.keyBindRight.isKeyDown()
        };
        String[] labels = {"W", "A", "S", "D"};
        FontRenderer font = font(mc);

        for (int i = 0; i < 4; i++) {
            int x = baseX + KEY_LAYOUT[i][0] * (KEY_SIZE + KEY_GAP);
            int y = baseY + KEY_LAYOUT[i][1] * (KEY_SIZE + KEY_GAP);
            Gui.drawRect(x, y, x + KEY_SIZE, y + KEY_SIZE,
                    pressed[i] ? 0x80FFFFFF : 0x60000000);
            font.drawStringWithShadow(labels[i],
                    x + (KEY_SIZE - font.getStringWidth(labels[i])) / 2,
                    y + (KEY_SIZE - 8) / 2,
                    pressed[i] ? 0x000000 : 0xFFFFFF);
        }
    }

    private void drawArmor(Minecraft mc, int x, int y) {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;
        // 1.8.9 stores armor as a fixed array, not a List (that changed in 1.9).
        ItemStack[] armor = player.inventory.armorInventory;
        FontRenderer font = font(mc);

        int count = 0;
        for (int i = 0; i < armor.length && i < 4; i++) {
            ItemStack stack = armor[i];
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
     * Samples the mouse at frame rate to derive clicks per second, catching
     * bursts a 20 Hz tick sample would miss.
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
