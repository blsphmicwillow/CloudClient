package net.cloudclient.core.config;

/**
 * A typed, self-describing config entry.
 *
 * <p>The mod menu is built from these rather than from hand-written widgets per
 * module: a module declares its settings once and the UI renders sliders,
 * toggles and colour swatches automatically. That is what makes the menu
 * "fully customisable" without a bespoke screen for every feature.
 */
public final class Setting {

    public enum Type {
        BOOLEAN, INT, FLOAT, COLOR, KEY
    }

    public final String key;
    public final String label;
    public final String description;
    public final Type type;
    /** Inclusive bounds for numeric settings; unused for boolean/colour/key. */
    public final float min;
    public final float max;
    private final Object defaultValue;

    private Setting(String key, String label, String description, Type type,
                    Object defaultValue, float min, float max) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
    }

    public static Setting toggle(String key, String label, String description, boolean def) {
        return new Setting(key, label, description, Type.BOOLEAN, Boolean.valueOf(def), 0.0F, 1.0F);
    }

    public static Setting integer(String key, String label, String description,
                                  int def, int min, int max) {
        return new Setting(key, label, description, Type.INT, Integer.valueOf(def), min, max);
    }

    public static Setting number(String key, String label, String description,
                                 float def, float min, float max) {
        return new Setting(key, label, description, Type.FLOAT, Float.valueOf(def), min, max);
    }

    /**
     * Colour stored as a packed 0xRRGGBB int. Alpha is handled by the renderer,
     * because HUD text and HUD panels want different alpha treatment.
     */
    public static Setting colour(String key, String label, String description, int rgb) {
        return new Setting(key, label, description, Type.COLOR, Integer.valueOf(rgb),
                0.0F, 16777215.0F);
    }

    /** A keyboard key code (LWJGL key codes), captured by the UI. */
    public static Setting keybind(String key, String label, String description, int keyCode) {
        return new Setting(key, label, description, Type.KEY, Integer.valueOf(keyCode),
                0.0F, 255.0F);
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public boolean isNumeric() {
        return type == Type.INT || type == Type.FLOAT || type == Type.COLOR || type == Type.KEY;
    }

    // ------------------------------------------------------------------ get

    public boolean getBool(CloudConfig config) {
        return config.getBool(key, Boolean.TRUE.equals(defaultValue));
    }

    public int getInt(CloudConfig config) {
        int def = defaultValue instanceof Number ? ((Number) defaultValue).intValue() : 0;
        int value = config.getInt(key, def);
        return (int) clamp(value);
    }

    public float getFloat(CloudConfig config) {
        float def = defaultValue instanceof Number ? ((Number) defaultValue).floatValue() : 0.0F;
        return clamp((float) config.getDouble(key, def));
    }

    // ------------------------------------------------------------------ set

    public void set(CloudConfig config, Object value) {
        switch (type) {
            case BOOLEAN:
                config.set(key, Boolean.valueOf(Boolean.TRUE.equals(value)));
                break;
            case INT:
            case COLOR:
            case KEY:
                config.set(key, Integer.valueOf((int) clamp(toFloat(value))));
                break;
            case FLOAT:
                config.set(key, Float.valueOf(clamp(toFloat(value))));
                break;
            default:
                config.set(key, value);
        }
    }

    public void setFraction(CloudConfig config, float fraction) {
        float f = Math.max(0.0F, Math.min(1.0F, fraction));
        float value = min + (max - min) * f;
        if (type == Type.INT || type == Type.COLOR || type == Type.KEY) {
            config.set(key, Integer.valueOf(Math.round(value)));
        } else {
            config.set(key, Float.valueOf(value));
        }
    }

    /** Position of the current value within [min,max], for slider rendering. */
    public float fraction(CloudConfig config) {
        float range = max - min;
        if (range <= 0.0F) return 0.0F;
        return clamp((getFloat(config) - min) / range);
    }

    /** Human-readable current value, rendered next to the slider. */
    public String display(CloudConfig config) {
        switch (type) {
            case BOOLEAN:
                return getBool(config) ? "ON" : "OFF";
            case COLOR:
                return String.format("#%06X", getInt(config) & 0xFFFFFF);
            case KEY:
                return keyName(getInt(config));
            case FLOAT:
                return String.format("%.2f", getFloat(config));
            default:
                return String.valueOf(getInt(config));
        }
    }

    public void reset(CloudConfig config) {
        set(config, defaultValue);
    }

    private float clamp(float value) {
        return value < min ? min : (value > max ? max : value);
    }

    private static float toFloat(Object value) {
        if (value instanceof Number) return ((Number) value).floatValue();
        if (value instanceof Boolean) return ((Boolean) value).booleanValue() ? 1.0F : 0.0F;
        return 0.0F;
    }

    /** Minimal key-name table; the menu shows these next to keybind settings. */
    public static String keyName(int keyCode) {
        if (keyCode <= 0) return "NONE";
        switch (keyCode) {
            case 1: return "ESC";
            case 9: return "TAB";
            case 14: return "BACKSPACE";
            case 15: return "ENTER";
            case 28: return "ENTER";
            case 29: return "CTRL";
            case 42: return "SHIFT";
            case 56: return "ALT";
            case 57: return "SPACE";
            case 54: return "RSHIFT";
            case 199: return "HOME";
            case 200: return "UP";
            case 201: return "PGUP";
            case 203: return "LEFT";
            case 205: return "RIGHT";
            case 208: return "DOWN";
            case 211: return "DEL";
            default:
                // LWJGL 2 uses USB HID scan codes, which are not in alphabetical
                // order (A=30, B=48, Q=16), so letters need an explicit table.
                switch (keyCode) {
                    case 11: return "0";
                    case 30: return "A";
                    case 48: return "B";
                    case 46: return "C";
                    case 32: return "D";
                    case 18: return "E";
                    case 33: return "F";
                    case 34: return "G";
                    case 35: return "H";
                    case 23: return "I";
                    case 36: return "J";
                    case 37: return "K";
                    case 38: return "L";
                    case 50: return "M";
                    case 49: return "N";
                    case 24: return "O";
                    case 25: return "P";
                    case 16: return "Q";
                    case 19: return "R";
                    case 31: return "S";
                    case 20: return "T";
                    case 22: return "U";
                    case 47: return "V";
                    case 17: return "W";
                    case 45: return "X";
                    case 21: return "Y";
                    case 44: return "Z";
                    default:
                        break;
                }
                if (keyCode >= 2 && keyCode <= 10) return String.valueOf(keyCode - 1);
                if (keyCode >= 59 && keyCode <= 68) return "F" + (keyCode - 58);
                if (keyCode == 87) return "F11";
                if (keyCode == 88) return "F12";
                return "KEY" + keyCode;
        }
    }
}
