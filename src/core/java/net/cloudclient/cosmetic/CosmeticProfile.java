package net.cloudclient.cosmetic;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.cloudclient.cosmetic.cape.CapeSimulation;
import net.cloudclient.core.json.Json;

/**
 * What the local player currently has equipped, per cosmetic slot.
 *
 * <p>This is a local file. Nothing in it is transmitted: the client never sends
 * cosmetic state to a server or to another player, which is what keeps it
 * compliant with anti-cheat and with server rules.
 */
public final class CosmeticProfile {

    /** One equipped item. {@code packId} is {@code builtin} for stock content. */
    public static final class Entry {
        public String packId;
        public String itemId;
        public boolean visible = true;
        public float r = 1.0F;
        public float g = 1.0F;
        public float b = 1.0F;

        public Entry() {
        }

        public Entry(String packId, String itemId) {
            this.packId = packId;
            this.itemId = itemId;
        }

        public Map<String, Object> toJson() {
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("pack", packId);
            out.put("item", itemId);
            out.put("visible", Boolean.valueOf(visible));
            out.put("color", java.util.Arrays.<Object>asList(
                    Float.valueOf(r), Float.valueOf(g), Float.valueOf(b)));
            return out;
        }

        public static Entry fromJson(Map<String, Object> in) {
            Entry e = new Entry(Json.getString(in, "pack", "builtin"),
                    Json.getString(in, "item", ""));
            e.visible = Json.getBool(in, "visible", true);
            List<Object> color = Json.children(in, "color");
            if (color != null && color.size() >= 3) {
                e.r = clamp01(color.get(0));
                e.g = clamp01(color.get(1));
                e.b = clamp01(color.get(2));
            }
            return e;
        }

        private static float clamp01(Object o) {
            double d = o instanceof Number ? ((Number) o).doubleValue() : 1.0D;
            return (float) Math.max(0.0D, Math.min(1.0D, d));
        }
    }

    private final EnumMap<CosmeticType, Entry> equipped = new EnumMap<CosmeticType, Entry>(CosmeticType.class);

    public Entry equip(CosmeticType type, String packId, String itemId) {
        Entry e = new Entry(packId, itemId);
        equipped.put(type, e);
        return e;
    }

    public Entry get(CosmeticType type) {
        return equipped.get(type);
    }

    public boolean isEquipped(CosmeticType type) {
        return equipped.containsKey(type);
    }

    public void unequip(CosmeticType type) {
        equipped.remove(type);
    }

    public void clear() {
        equipped.clear();
    }

    /** A sane first-run loadout so the locker is not empty. */
    public static CosmeticProfile starter() {
        CosmeticProfile p = new CosmeticProfile();
        p.equip(CosmeticType.CAPE, "builtin", "cloud_cape");
        return p;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Map.Entry<CosmeticType, Entry> e : equipped.entrySet()) {
            out.put(e.getKey().id(), e.getValue().toJson());
        }
        return out;
    }

    public static CosmeticProfile fromJson(Map<String, Object> in) {
        CosmeticProfile profile = new CosmeticProfile();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            CosmeticType type = CosmeticType.byId(e.getKey());
            if (type == null) continue;
            if (!(e.getValue() instanceof Map)) continue;
            profile.equipped.put(type, Entry.fromJson(Json.asObject(e.getValue())));
        }
        return profile;
    }

    /**
     * Cape tessellation is fixed by the spec (8x4), so this exposes the derived
     * vertex count for the locker preview and for pack validation.
     */
    public static int capeVertexCount() {
        return CapeSimulation.POINT_COUNT;
    }
}
