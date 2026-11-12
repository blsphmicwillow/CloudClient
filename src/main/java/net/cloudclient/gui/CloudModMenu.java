package net.cloudclient.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.cloudclient.CloudClientRuntime;
import net.cloudclient.core.Module;
import net.cloudclient.core.config.Setting;
import net.cloudclient.core.gui.HudLayout;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The Cloud Client menu, opened with right-shift.
 *
 * <p>Layout: a searchable module list on the left, and the selected module's
 * settings on the right. Settings come from {@link Setting} descriptors, so the
 * screen renders sliders, toggles, colour swatches and keybind capture without
 * knowing anything about the module that declared them.
 *
 * <p>The HUD tab drags the real HUD elements: the boxes drawn there are at the
 * same GUI-scaled coordinates the HUD uses in game, so what you see is where it
 * lands.
 */
public final class CloudModMenu extends GuiScreen {

    private static final int PANEL_LEFT = 214;
    private static final int ROW_HEIGHT = 20;
    private static final int SETTING_ROW = 26;

    private static final int COLOUR_BG = 0xF0101014;
    private static final int COLOUR_PANEL = 0xFF16161C;
    private static final int COLOUR_ACCENT = 0xFF5AC8FA;
    private static final int COLOUR_TEXT = 0xFFE6E6E6;
    private static final int COLOUR_MUTED = 0xFF8A8A96;
    private static final int COLOUR_ON = 0xFF3DD68C;
    private static final int COLOUR_OFF = 0xFF6A6A75;

    private static final int[] PALETTE = {
            0xFFFFFF, 0x5AC8FA, 0x3DD68C, 0xFFD27A,
            0xFF6B6B, 0xB388FF, 0x4DD0E1, 0xFF8AC4
    };

    private enum Tab {
        MODULES, HUD
    }

    private final CloudClientRuntime runtime;
    private final List<Module> filtered = new ArrayList<Module>();
    private GuiTextField search;

    private Tab tab = Tab.MODULES;
    private Module selected;
    private String filter = "";
    private int scroll;
    private Setting draggingSlider;
    private Setting capturingKey;
    private HudLayout.Element draggingElement;
    private String toast = "";
    private long toastUntil;

    public CloudModMenu(CloudClientRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        search = new GuiTextField(0, fontRendererObj, 8, 32, PANEL_LEFT - 16, 16);
        search.setMaxStringLength(32);
        search.setText(filter);
        search.setFocused(true);

        runtime.hud().layout().load(runtime.config());
        rebuild();
        if (selected == null && !filtered.isEmpty()) selected = filtered.get(0);
    }

    private void rebuild() {
        filtered.clear();
        for (Module module : runtime.modules().modules()) {
            if (filter.isEmpty()
                    || module.id().toLowerCase().contains(filter)
                    || module.displayName().toLowerCase().contains(filter)) {
                filtered.add(module);
            }
        }
        scroll = 0;
    }

    // ------------------------------------------------------------- rendering

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Gui.drawRect(0, 0, width, height, COLOUR_BG);
        Gui.drawRect(0, 0, PANEL_LEFT, height, COLOUR_PANEL);

        // Header + tabs
        drawString("Cloud Client", 8, 8, COLOUR_ACCENT);
        drawTab("Modules", 8, 18, tab == Tab.MODULES, mouseX, mouseY);
        drawTab("HUD", 78, 18, tab == Tab.HUD, mouseX, mouseY);
        drawString(runtime.modules().enabledCount() + "/" + runtime.modules().modules().size(),
                PANEL_LEFT - 40, 8, COLOUR_MUTED);

        if (tab == Tab.MODULES) {
            drawModuleList(mouseX, mouseY);
            drawDetails(mouseX, mouseY);
        } else {
            drawHudEditor(mouseX, mouseY);
        }

        if (!toast.isEmpty() && System.currentTimeMillis() < toastUntil) {
            drawString(toast, width / 2 - fontRendererObj.getStringWidth(toast) / 2,
                    height - 16, COLOUR_ON);
        }

        search.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTab(String label, int x, int y, boolean active, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, x + 64, y + 12);
        Gui.drawRect(x, y + 11, x + 64, y + 12, active ? COLOUR_ACCENT
                : (hover ? COLOUR_TEXT : COLOUR_MUTED));
        drawString(label, x + 2, y, active ? COLOUR_TEXT : COLOUR_MUTED);
    }

    private void drawModuleList(int mouseX, int mouseY) {
        int listTop = 54;
        int listBottom = height - 26;
        int rowCount = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, filtered.size() - rowCount);
        if (scroll > maxScroll) scroll = maxScroll;

        for (int i = 0; i < rowCount; i++) {
            int index = i + scroll;
            if (index >= filtered.size()) break;
            Module module = filtered.get(index);
            int y = listTop + i * ROW_HEIGHT;
            boolean hover = inside(mouseX, mouseY, 0, y, PANEL_LEFT, y + ROW_HEIGHT);

            if (module == selected) {
                Gui.drawRect(0, y, PANEL_LEFT, y + ROW_HEIGHT, 0xFF23232E);
                Gui.drawRect(0, y, 2, y + ROW_HEIGHT, COLOUR_ACCENT);
            } else if (hover) {
                Gui.drawRect(0, y, PANEL_LEFT, y + ROW_HEIGHT, 0xFF1C1C24);
            }

            int dotColour = module.isEnabled() ? COLOUR_ON : COLOUR_OFF;
            Gui.drawRect(10, y + 8, 16, y + 14, dotColour);
            drawString(module.displayName(), 22, y + 6,
                    module.isEnabled() ? COLOUR_TEXT : COLOUR_MUTED);
        }

        if (filtered.isEmpty()) {
            drawString("no matches", 10, listTop + 4, COLOUR_MUTED);
        }
        if (maxScroll > 0) {
            drawString((scroll + 1) + "-" + Math.min(filtered.size(), scroll + rowCount),
                    8, listBottom + 4, COLOUR_MUTED);
        }
    }

    private void drawDetails(int mouseX, int mouseY) {
        if (selected == null) {
            drawString("Select a module", PANEL_LEFT + 12, 52, COLOUR_MUTED);
            return;
        }
        int x = PANEL_LEFT + 12;
        int y = 52;

        drawString(selected.displayName(), x, y, COLOUR_TEXT);
        y += 14;
        y = wrap(selected.description(), x, y, width - x - 12, COLOUR_MUTED) + 6;

        // Enable switch
        drawSwitch(x, y, selected.isEnabled(), mouseX, mouseY);
        drawString(selected.isEnabled() ? "Enabled" : "Disabled", x + 40, y + 3,
                selected.isEnabled() ? COLOUR_ON : COLOUR_MUTED);
        y += 22;

        Gui.drawRect(x, y, width - 12, y + 1, 0xFF23232E);
        y += 8;

        if (selected.settings().isEmpty()) {
            drawString("This module has no settings.", x, y, COLOUR_MUTED);
            return;
        }

        for (Setting setting : selected.settings()) {
            if (y > height - 40) {
                drawString("…", x, y, COLOUR_MUTED);
                break;
            }
            drawSetting(setting, x, y, width - 12, mouseX, mouseY);
            y += SETTING_ROW;
        }

        // Reset
        drawButton("Reset to defaults", x, height - 26, 110, mouseX, mouseY);
    }

    private void drawSetting(Setting setting, int x, int y, int right, int mouseX, int mouseY) {
        drawString(setting.label, x, y, COLOUR_TEXT);
        drawString(setting.display(runtime.config()), right - 52, y, COLOUR_MUTED);

        int controlY = y + 11;
        if (setting.type == Setting.Type.BOOLEAN) {
            drawSwitch(x, controlY, setting.getBool(runtime.config()), mouseX, mouseY);
            return;
        }
        if (setting.type == Setting.Type.KEY) {
            boolean capturing = capturingKey == setting;
            Gui.drawRect(x, controlY, x + 70, controlY + 10,
                    capturing ? 0xFF2C2C3A : 0xFF1F1F27);
            drawString(capturing ? "press a key…" : Setting.keyName(setting.getInt(runtime.config())),
                    x + 3, controlY + 1, capturing ? COLOUR_ACCENT : COLOUR_TEXT);
            return;
        }
        if (setting.type == Setting.Type.COLOR) {
            int colour = setting.getInt(runtime.config()) & 0xFFFFFF;
            Gui.drawRect(x, controlY, x + 34, controlY + 9, 0xFF000000 | colour);
            Gui.drawRect(x + 34, controlY, x + 70, controlY + 9, 0xFF1F1F27);
            drawString("click to cycle", x + 37, controlY + 1, COLOUR_MUTED);
            return;
        }

        // Slider
        int trackLeft = x;
        int trackRight = Math.min(right - 60, x + 260);
        Gui.drawRect(trackLeft, controlY + 3, trackRight, controlY + 6, 0xFF1F1F27);
        int knobX = trackLeft + Math.round((trackRight - trackLeft) * setting.fraction(runtime.config()));
        Gui.drawRect(trackLeft, controlY + 3, knobX, controlY + 6, COLOUR_ACCENT);
        Gui.drawRect(knobX - 1, controlY, knobX + 2, controlY + 9, COLOUR_TEXT);
    }

    private void drawHudEditor(int mouseX, int mouseY) {
        drawString("HUD Layout", PANEL_LEFT + 12, 52, COLOUR_TEXT);
        drawString("Drag elements to reposition them.", PANEL_LEFT + 12, 66, COLOUR_MUTED);

        HudLayout layout = runtime.hud().layout();
        for (HudLayout.Element element : layout.all()) {
            int ex = Math.round(element.x) + PANEL_LEFT;
            int ey = Math.round(element.y) + 40;
            int ew = Math.round(element.width);
            int eh = Math.round(element.height);
            boolean hover = inside(mouseX, mouseY, ex, ey, ex + ew, ey + eh);

            Gui.drawRect(ex, ey, ex + ew, ey + eh,
                    hover || element == draggingElement ? 0x405AC8FA : 0x30FFFFFF);
            Gui.drawRect(ex, ey, ex + ew, ey + 1, COLOUR_ACCENT);
            Gui.drawRect(ex, ey + eh - 1, ex + ew, ey + eh, COLOUR_ACCENT);
            Gui.drawRect(ex, ey, ex + 1, ey + eh, COLOUR_ACCENT);
            Gui.drawRect(ex + ew - 1, ey, ex + ew, ey + eh, COLOUR_ACCENT);
            drawString(element.label, ex + 2, ey + 2, COLOUR_TEXT);
        }

        drawButton("Reset layout", PANEL_LEFT + 12, height - 26, 110, mouseX, mouseY);
        drawString("Changes save when you close this screen.",
                PANEL_LEFT + 130, height - 22, COLOUR_MUTED);
    }

    private void drawSwitch(int x, int y, boolean on, int mouseX, int mouseY) {
        Gui.drawRect(x, y, x + 30, y + 12, on ? 0xFF1E5C43 : 0xFF26262F);
        Gui.drawRect(on ? x + 16 : x + 2, y + 2, on ? x + 28 : x + 14, y + 10,
                on ? COLOUR_ON : COLOUR_OFF);
    }

    private void drawButton(String label, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, x + w, y + 16);
        Gui.drawRect(x, y, x + w, y + 16, hover ? 0xFF2C2C3A : 0xFF1F1F27);
        drawString(label, x + 5, y + 4, hover ? COLOUR_TEXT : COLOUR_MUTED);
    }

    private void drawString(String text, int x, int y, int colour) {
        fontRendererObj.drawStringWithShadow(text, x, y, colour);
    }

    /** Wraps text and returns the y coordinate below the last line. */
    private int wrap(String text, int x, int y, int maxWidth, int colour) {
        if (text == null || text.isEmpty()) return y;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fontRendererObj.getStringWidth(candidate) > maxWidth && line.length() > 0) {
                drawString(line.toString(), x, y, colour);
                y += 10;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            drawString(line.toString(), x, y, colour);
            y += 10;
        }
        return y;
    }

    // ------------------------------------------------------------- interaction

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        search.mouseClicked(mouseX, mouseY, mouseButton);

        if (inside(mouseX, mouseY, 8, 18, 72, 30)) { tab = Tab.MODULES; return; }
        if (inside(mouseX, mouseY, 78, 18, 142, 30)) { tab = Tab.HUD; return; }

        if (tab == Tab.HUD) {
            handleHudClick(mouseX, mouseY);
            return;
        }

        handleModuleClick(mouseX, mouseY);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void handleModuleClick(int mouseX, int mouseY) {
        int listTop = 54;
        int listBottom = height - 26;
        if (mouseX < PANEL_LEFT && mouseY >= listTop && mouseY <= listBottom) {
            int index = scroll + (mouseY - listTop) / ROW_HEIGHT;
            if (index >= 0 && index < filtered.size()) {
                Module module = filtered.get(index);
                // Clicking the dot toggles; clicking the row selects.
                if (mouseX >= 8 && mouseX <= 18) {
                    module.toggle();
                    toast(module.displayName() + ": " + (module.isEnabled() ? "ON" : "OFF"));
                } else {
                    selected = module;
                    capturingKey = null;
                }
            }
            return;
        }

        if (selected == null) return;

        // Enable switch
        int detailsY = 52;
        detailsY += 14;
        detailsY += wrappedHeight(selected.description(), width - (PANEL_LEFT + 12) - 12) + 6;
        if (inside(mouseX, mouseY, PANEL_LEFT + 12, detailsY, PANEL_LEFT + 42, detailsY + 12)) {
            selected.toggle();
            toast(selected.displayName() + ": " + (selected.isEnabled() ? "ON" : "OFF"));
            return;
        }

        // Reset button
        if (inside(mouseX, mouseY, PANEL_LEFT + 12, height - 26,
                PANEL_LEFT + 122, height - 10)) {
            selected.resetSettings();
            toast("Settings reset");
            return;
        }

        // Settings
        int y = detailsY + 22 + 8;
        int x = PANEL_LEFT + 12;
        for (Setting setting : selected.settings()) {
            if (y > height - 40) break;
            int controlY = y + 11;
            if (mouseY >= controlY - 2 && mouseY <= controlY + 14) {
                switch (setting.type) {
                    case BOOLEAN:
                        setting.set(runtime.config(), Boolean.valueOf(
                                !setting.getBool(runtime.config())));
                        return;
                    case COLOR:
                        setting.set(runtime.config(), Integer.valueOf(
                                nextPalette(setting.getInt(runtime.config()))));
                        return;
                    case KEY:
                        capturingKey = setting;
                        return;
                    default:
                        draggingSlider = setting;
                        applySlider(setting, mouseX, x, Math.min(width - 12 - 60, x + 260));
                        return;
                }
            }
            y += SETTING_ROW;
        }
    }

    private void handleHudClick(int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, PANEL_LEFT + 12, height - 26,
                PANEL_LEFT + 122, height - 10)) {
            runtime.hud().layout().resetPositions(runtime.hud().defaults());
            toast("HUD layout reset");
            return;
        }

        // The editor draws elements offset by the panel so they stay visible.
        float localX = mouseX - PANEL_LEFT;
        float localY = mouseY - 40;
        HudLayout layout = runtime.hud().layout();
        if (layout.beginDrag(localX, localY)) {
            draggingElement = layout.dragging();
        }
    }

    private void applySlider(Setting setting, int mouseX, int trackLeft, int trackRight) {
        int span = Math.max(1, trackRight - trackLeft);
        float fraction = (mouseX - trackLeft) / (float) span;
        setting.setFraction(runtime.config(), fraction);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingSlider = null;
        draggingElement = null;
        runtime.hud().layout().endDrag();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingSlider != null) {
            int x = PANEL_LEFT + 12;
            applySlider(draggingSlider, mouseX, x, Math.min(width - 12 - 60, x + 260));
        }
        if (draggingElement != null) {
            runtime.hud().layout().dragTo(mouseX - PANEL_LEFT, mouseY - 40,
                    width - PANEL_LEFT, height - 60);
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (tab == Tab.MODULES && wheel != 0 && mc.thePlayer != null) {
            scroll -= wheel > 0 ? 1 : -1;
            int rowCount = Math.max(1, (height - 26 - 54) / ROW_HEIGHT);
            int maxScroll = Math.max(0, filtered.size() - rowCount);
            if (scroll < 0) scroll = 0;
            if (scroll > maxScroll) scroll = maxScroll;
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (capturingKey != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                capturingKey = null;
            } else {
                capturingKey.set(runtime.config(), Integer.valueOf(keyCode));
                capturingKey = null;
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT) {
            close();
            return;
        }
        if (search.textboxKeyTyped(typedChar, keyCode)) {
            String text = search.getText();
            if (!text.equals(filter)) {
                filter = text;
                rebuild();
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void close() {
        runtime.hud().layout().save(runtime.config());
        runtime.saveConfig();
        mc.displayGuiScreen(null);
    }

    @Override
    public void onGuiClosed() {
        runtime.hud().layout().save(runtime.config());
        runtime.saveConfig();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ----------------------------------------------------------------- helpers

    private static boolean inside(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    private int wrappedHeight(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return 0;
        int lines = 0;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fontRendererObj.getStringWidth(candidate) > maxWidth && line.length() > 0) {
                lines++;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        return (lines + 1) * 10;
    }

    private static int nextPalette(int current) {
        int rgb = current & 0xFFFFFF;
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == rgb) return PALETTE[(i + 1) % PALETTE.length];
        }
        return PALETTE[0];
    }

    private void toast(String message) {
        this.toast = message;
        this.toastUntil = System.currentTimeMillis() + 1500L;
    }
}
