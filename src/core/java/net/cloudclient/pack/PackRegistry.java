package net.cloudclient.pack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.cloudclient.cosmetic.CosmeticType;

/**
 * Installed packs, keyed by pack id, with O(1) cosmetic lookup.
 *
 * <p>Ids are namespaced as {@code packId:cosmeticId} so two packs can each ship
 * a "dragon" without colliding. Installing a pack with an existing id replaces
 * it, which is how updates work without breaking equipped cosmetics: the
 * profile stores ids, not object references.
 */
public final class PackRegistry {

    private final Map<String, CloudPack> packs = new LinkedHashMap<String, CloudPack>();
    private final Map<String, CosmeticDefinition> cosmetics = new LinkedHashMap<String, CosmeticDefinition>();

    /** Installs when valid; returns the load result either way. */
    public PackLoader.Loaded install(byte[] archive, String source) {
        PackLoader.Loaded loaded = PackLoader.load(archive, source);
        if (loaded.ok()) {
            install(loaded.pack);
        }
        return loaded;
    }

    public void install(CloudPack pack) {
        uninstall(pack.manifest().id);
        packs.put(pack.manifest().id, pack);
        for (CosmeticDefinition c : pack.manifest().cosmetics) {
            cosmetics.put(key(pack.manifest().id, c.id), c);
        }
    }

    public boolean uninstall(String packId) {
        CloudPack removed = packs.remove(packId);
        if (removed == null) return false;
        for (CosmeticDefinition c : removed.manifest().cosmetics) {
            cosmetics.remove(key(packId, c.id));
        }
        return true;
    }

    public CloudPack pack(String packId) {
        return packs.get(packId);
    }

    public Collection<CloudPack> packs() {
        return Collections.unmodifiableCollection(packs.values());
    }

    public CosmeticDefinition find(String packId, String cosmeticId) {
        return cosmetics.get(key(packId, cosmeticId));
    }

    /** All cosmetics of a given slot type, for the locker's category tabs. */
    public List<CosmeticDefinition> byType(CosmeticType type) {
        List<CosmeticDefinition> out = new ArrayList<CosmeticDefinition>();
        for (CosmeticDefinition c : cosmetics.values()) {
            if (c.type == type) out.add(c);
        }
        return out;
    }

    public int size() {
        return packs.size();
    }

    public static String key(String packId, String cosmeticId) {
        return packId + ":" + cosmeticId;
    }
}
