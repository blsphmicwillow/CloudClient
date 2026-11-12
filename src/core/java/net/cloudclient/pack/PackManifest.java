package net.cloudclient.pack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.cloudclient.core.json.Json;

/** Root {@code manifest.json} of a {@code .cloudpack}. */
public final class PackManifest {

    /** Pack API version this client understands. */
    public static final int API_VERSION = 1;

    public final String id;
    public final String name;
    public final String version;
    public final String author;
    public final String description;
    public final int api;
    public final List<CosmeticDefinition> cosmetics;

    private PackManifest(String id, String name, String version, String author,
                         String description, int api, List<CosmeticDefinition> cosmetics) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.api = api;
        this.cosmetics = Collections.unmodifiableList(cosmetics);
    }

    public static PackManifest parse(Map<String, Object> in) throws PackFormatException {
        String id = Json.getString(in, "id", "");
        String name = Json.getString(in, "name", "");
        String version = Json.getString(in, "version", "");
        String author = Json.getString(in, "author", "unknown");
        String description = Json.getString(in, "description", "");
        int api = Json.getInt(in, "api", 0);

        List<Object> rawCosmetics = Json.children(in, "cosmetics");
        List<CosmeticDefinition> cosmetics = new ArrayList<CosmeticDefinition>();
        if (rawCosmetics != null) {
            for (Object o : rawCosmetics) {
                if (!(o instanceof Map)) {
                    throw new PackFormatException("cosmetics[] must contain objects");
                }
                cosmetics.add(CosmeticDefinition.parse(Json.asObject(o)));
            }
        }
        return new PackManifest(id, name, version, author, description, api, cosmetics);
    }

    public CosmeticDefinition find(String cosmeticId) {
        for (CosmeticDefinition c : cosmetics) {
            if (c.id.equals(cosmeticId)) return c;
        }
        return null;
    }
}
