package net.cloudclient.core.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.cloudclient.core.config.CloudConfig;

/**
 * Positions and drag state for HUD elements.
 *
 * <p>Pure geometry: no Minecraft types, so the hit-testing and clamping can be
 * tested directly. The screen passes in mouse coordinates and its own
 * dimensions; this class owns the rest.
 *
 * <p>Coordinates are stored in GUI-scaled pixels, which is what 1.8.9's
 * {@code ScaledResolution} gives the HUD, so a layout survives resolution and
 * GUI-scale changes.
 */
public final class HudLayout {

    /** One HUD element. Mutable by design: this is drag state. */
    public static final class Element {
        public final String id;
        public final String label;
        public float x;
        public float y;
        public final float width;
        public final float height;
        public boolean visible = true;

        Element(String id, String label, float x, float y, float width, float height) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean contains(float px, float py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    private final Map<String, Element> elements = new LinkedHashMap<String, Element>();
    private Element dragging;
    private float grabOffsetX;
    private float grabOffsetY;

    /**
     * Declares an element with its default position, then applies any saved
     * position. Safe to call every frame: existing elements are returned as-is.
     */
    public Element declare(String id, String label, float defaultX, float defaultY,
                           float width, float height) {
        Element existing = elements.get(id);
        if (existing != null) return existing;
        Element created = new Element(id, label, defaultX, defaultY, width, height);
        elements.put(id, created);
        return created;
    }

    public Element get(String id) {
        return elements.get(id);
    }

    public List<Element> all() {
        return new ArrayList<Element>(elements.values());
    }

    /** Topmost element under the cursor, or null. */
    public Element hitTest(float mouseX, float mouseY) {
        List<Element> list = all();
        for (int i = list.size() - 1; i >= 0; i--) {
            Element element = list.get(i);
            if (element.visible && element.contains(mouseX, mouseY)) return element;
        }
        return null;
    }

    public boolean beginDrag(float mouseX, float mouseY) {
        Element element = hitTest(mouseX, mouseY);
        if (element == null) return false;
        dragging = element;
        grabOffsetX = mouseX - element.x;
        grabOffsetY = mouseY - element.y;
        return true;
    }

    public boolean isDragging() {
        return dragging != null;
    }

    public Element dragging() {
        return dragging;
    }

    /** Moves the grabbed element, clamped inside the screen. */
    public void dragTo(float mouseX, float mouseY, float screenWidth, float screenHeight) {
        if (dragging == null) return;
        float targetX = mouseX - grabOffsetX;
        float targetY = mouseY - grabOffsetY;
        dragging.x = clamp(targetX, 0.0F, Math.max(0.0F, screenWidth - dragging.width));
        dragging.y = clamp(targetY, 0.0F, Math.max(0.0F, screenHeight - dragging.height));
    }

    public void endDrag() {
        dragging = null;
    }

    public void save(CloudConfig config) {
        for (Element element : elements.values()) {
            config.set(prefix(element.id) + "x", Float.valueOf(element.x));
            config.set(prefix(element.id) + "y", Float.valueOf(element.y));
            config.set(prefix(element.id) + "visible", Boolean.valueOf(element.visible));
        }
    }

    public void load(CloudConfig config) {
        for (Element element : elements.values()) {
            element.x = (float) config.getDouble(prefix(element.id) + "x", element.x);
            element.y = (float) config.getDouble(prefix(element.id) + "y", element.y);
            element.visible = config.getBool(prefix(element.id) + "visible", true);
        }
    }

    /** Restores default positions; called by the menu's Reset button. */
    public void resetPositions(Map<String, float[]> defaults) {
        for (Element element : elements.values()) {
            float[] def = defaults.get(element.id);
            if (def == null) continue;
            element.x = def[0];
            element.y = def[1];
            element.visible = true;
        }
    }

    private static String prefix(String id) {
        return "hud.layout." + id + ".";
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }
}
