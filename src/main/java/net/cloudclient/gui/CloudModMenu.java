package net.cloudclient.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.cloudclient.CloudClientRuntime;
import net.cloudclient.core.Module;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

/**
 * The module menu (right-shift). Lists every module with a toggle and filters
 * through a search box.
 *
 * <p>Kept intentionally plain: a settings UI is where clients accumulate
 * hundreds of lines of layout code, and this one only has to prove the module
 * kernel is reachable from in-game.
 */
public final class CloudModMenu extends GuiScreen {

    private static final int ROW_HEIGHT = 22;
    private static final int COLUMNS = 2;

    private final CloudClientRuntime runtime;
    private final List<Module> visibleModules = new ArrayList<Module>();
    private GuiTextField search;
    private String filter = "";

    public CloudModMenu(CloudClientRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        visibleModules.clear();

        search = new GuiTextField(0, fontRendererObj, width / 2 - 120, 28, 240, 18);
        search.setMaxStringLength(32);
        search.setFocused(true);
        search.setText(filter);

        rebuild();
    }

    private void rebuild() {
        buttonList.clear();
        visibleModules.clear();

        for (Module module : runtime.modules().modules()) {
            if (filter.isEmpty()
                    || module.id().toLowerCase().contains(filter)
                    || module.displayName().toLowerCase().contains(filter)) {
                visibleModules.add(module);
            }
        }
        List<Module> visible = visibleModules;

        int columnWidth = 200;
        int perColumn = Math.max(1, (height - 90) / ROW_HEIGHT);
        int index = 0;
        for (Module module : visible) {
            int column = index / perColumn;
            int row = index % perColumn;
            if (column >= COLUMNS) break;
            int x = width / 2 - (COLUMNS * columnWidth) / 2 + column * columnWidth;
            int y = 56 + row * ROW_HEIGHT;
            GuiButton button = new GuiButton(index, x, y, columnWidth - 8, 20,
                    label(module));
            button.enabled = true;
            buttonList.add(button);
            index++;
        }

        buttonList.add(new GuiButton(9000, width / 2 - 40, height - 30, 80, 20, "Done"));
    }

    private String label(Module module) {
        return module.displayName() + ": " + (module.isEnabled() ? "ON" : "OFF");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 9000) {
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id < 0 || button.id >= visibleModules.size()) return;

        Module module = visibleModules.get(button.id);
        module.toggle();
        button.displayString = label(module);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // escape
            mc.displayGuiScreen(null);
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

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        search.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCentred("Cloud Client", 12, 0xFFFFFF);
        drawCentred("Modules: " + runtime.modules().enabledCount() + "/"
                + runtime.modules().modules().size(), 44, 0xAAAAAA);
        search.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (visibleModules.isEmpty()) {
            drawCentred("No modules match \"" + filter + "\"", height / 2, 0xFF6666);
        }
    }

    private void drawCentred(String text, int y, int colour) {
        fontRendererObj.drawStringWithShadow(text,
                width / 2 - fontRendererObj.getStringWidth(text) / 2, y, colour);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
