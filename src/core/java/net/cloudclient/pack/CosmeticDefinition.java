package net.cloudclient.pack;

import java.util.Map;

import net.cloudclient.core.json.Json;
import net.cloudclient.cosmetic.CosmeticType;

/** One cosmetic declared by a pack manifest. */
public final class CosmeticDefinition {

    /** Ids are used in file paths and config keys, so the charset is strict. */
    private static final String ID_PATTERN = "[a-z0-9][a-z0-9_.-]{0,63}";

    public final String id;
    public final String displayName;
    public final CosmeticType type;
    public final String texture;
    public final String model;
    public final int animationFrames;
    public final float animationFps;
    public final int declaredTriangles;
    public final float scale;
    public final float stiffness;
    public final float windScale;
    public final float flutterAmp;
    public final float flutterFreq;

    CosmeticDefinition(String id, String displayName, CosmeticType type, String texture,
                       String model, int animationFrames, float animationFps,
                       int declaredTriangles, float scale, float stiffness,
                       float windScale, float flutterAmp, float flutterFreq) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.texture = texture;
        this.model = model;
        this.animationFrames = animationFrames;
        this.animationFps = animationFps;
        this.declaredTriangles = declaredTriangles;
        this.scale = scale;
        this.stiffness = stiffness;
        this.windScale = windScale;
        this.flutterAmp = flutterAmp;
        this.flutterFreq = flutterFreq;
    }

    public boolean isAnimated() {
        return animationFrames > 1;
    }

    public static CosmeticDefinition parse(Map<String, Object> in) throws PackFormatException {
        String id = Json.getString(in, "id", "");
        if (!id.matches(ID_PATTERN)) {
            throw new PackFormatException("Invalid cosmetic id '" + id + "'");
        }
        String name = Json.getString(in, "name", id);
        String typeId = Json.getString(in, "type", "");
        CosmeticType type = CosmeticType.byId(typeId);
        if (type == null) {
            throw new PackFormatException("cosmetic '" + id + "' has unknown type '" + typeId + "'");
        }
        String texture = Json.getString(in, "texture", "");
        Map<String, Object> animation = Json.child(in, "animation");
        int frames = animation == null ? 1 : Json.getInt(animation, "frames", 1);
        float fps = animation == null ? 12.0F : (float) Json.getDouble(animation, "fps", 12.0D);

        Map<String, Object> physics = Json.child(in, "physics");
        float stiffness = physics == null ? 0.90F : (float) Json.getDouble(physics, "stiffness", 0.90D);
        float windScale = physics == null ? 1.0F : (float) Json.getDouble(physics, "windScale", 1.0D);
        float flutterAmp = physics == null ? 0.40F : (float) Json.getDouble(physics, "flutterAmp", 0.40D);
        float flutterFreq = physics == null ? 1.10F : (float) Json.getDouble(physics, "flutterFreq", 1.10D);

        return new CosmeticDefinition(id, name, type, texture,
                Json.getString(in, "model", null),
                frames, fps,
                Json.getInt(in, "triangles", 0),
                (float) Json.getDouble(in, "scale", 1.0D),
                stiffness, windScale, flutterAmp, flutterFreq);
    }
}
