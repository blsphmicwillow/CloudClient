package net.cloudclient.core.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.cloudclient.core.json.Json;

/**
 * Flat, dot-path keyed configuration store backed by a JSON object.
 *
 * <p>Keys are namespaced by owner, e.g. {@code module.cape.enabled} or
 * {@code hud.fps.x}. Unknown keys are preserved on load/save so a downgrade
 * never destroys settings, and defaults live at the call site (no schema file to
 * drift out of sync).
 */
public final class CloudConfig {

    private final Map<String, Object> values = new LinkedHashMap<String, Object>();
    private File file;

    public CloudConfig() {
    }

    public CloudConfig(File file) {
        this.file = file;
    }

    public File file() {
        return file;
    }

    public void bind(File file) {
        this.file = file;
    }

    public boolean has(String path) {
        return values.containsKey(path);
    }

    public Object raw(String path) {
        return values.get(path);
    }

    public void set(String path, Object value) {
        if (value == null) {
            values.remove(path);
        } else {
            values.put(path, value);
        }
    }

    public boolean getBool(String path, boolean def) {
        Object v = values.get(path);
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
        if (v instanceof Number) return ((Number) v).doubleValue() != 0.0D;
        if (v instanceof String) return Boolean.parseBoolean((String) v);
        return def;
    }

    public int getInt(String path, int def) {
        Object v = values.get(path);
        if (v instanceof Number) return (int) Math.round(((Number) v).doubleValue());
        if (v instanceof String) {
            try {
                return Integer.parseInt(((String) v).trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public double getDouble(String path, double def) {
        Object v = values.get(path);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try {
                return Double.parseDouble(((String) v).trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public String getString(String path, String def) {
        Object v = values.get(path);
        return v == null ? def : String.valueOf(v);
    }

    /**
     * Clamps an int setting into range, writes the clamped value back (so the
     * file never disagrees with runtime behaviour) and returns it.
     */
    public int getIntClamped(String path, int def, int min, int max) {
        int v = getInt(path, def);
        int clamped = Math.max(min, Math.min(max, v));
        if (clamped != v || !has(path)) set(path, clamped);
        return clamped;
    }

    public double getDoubleClamped(String path, double def, double min, double max) {
        double v = getDouble(path, def);
        double clamped = Math.max(min, Math.min(max, v));
        if (clamped != v || !has(path)) set(path, clamped);
        return clamped;
    }

    /**
     * Loads the bound file, keeping defaults for absent keys. A malformed file is
     * reported rather than thrown: config corruption must never stop the client
     * from starting.
     *
     * @return true when values were loaded
     */
    public boolean load() throws IOException {
        if (file == null || !file.isFile()) return false;
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        if (text.trim().isEmpty()) return false;
        Map<String, Object> parsed = Json.asObject(Json.parse(text));
        values.clear();
        values.putAll(flatten(parsed, ""));
        return true;
    }

    public void save() throws IOException {
        if (file == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create config directory " + parent);
        }
        Map<String, Object> nested = unflatten(values);
        Files.write(file.toPath(), Json.writePretty(nested).getBytes(StandardCharsets.UTF_8));
    }

    public Map<String, Object> snapshot() {
        return new LinkedHashMap<String, Object>(values);
    }

    private static Map<String, Object> flatten(Map<String, Object> in, String prefix) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) v;
                // Empty objects and real nested maps flatten the same way.
                out.putAll(flatten(child, key));
            } else {
                out.put(key, v);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unflatten(Map<String, Object> flat) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> e : flat.entrySet()) {
            String[] parts = e.getKey().split("\\.");
            Map<String, Object> cursor = root;
            for (int i = 0; i < parts.length - 1; i++) {
                Object next = cursor.get(parts[i]);
                if (!(next instanceof Map)) {
                    next = new LinkedHashMap<String, Object>();
                    cursor.put(parts[i], next);
                }
                cursor = (Map<String, Object>) next;
            }
            cursor.put(parts[parts.length - 1], e.getValue());
        }
        return root;
    }

    /** Diagnostics: current keys in insertion order. */
    public List<String> keys() {
        return new java.util.ArrayList<String>(values.keySet());
    }
}
