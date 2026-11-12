package net.cloudclient.cosmetic;

import net.cloudclient.math.Vec3;

/**
 * Standard anchors on the player model that cosmetics mount to.
 *
 * <p>Offsets are in blocks, in <em>player-local</em> space:
 * <ul>
 *   <li>{@code +x} = player's right</li>
 *   <li>{@code +y} = up, origin at the feet</li>
 *   <li>{@code +z} = behind the player</li>
 * </ul>
 * Keeping this documented is what makes the solver testable; the renderer only
 * ever consumes world-space results.
 */
public enum AttachmentPoint {

    HEAD_TOP(0.0F, 1.72F, 0.0F, true),
    HEAD_SIDE_LEFT(-0.20F, 1.62F, 0.0F, true),
    HEAD_SIDE_RIGHT(0.20F, 1.62F, 0.0F, true),

    BACK(0.0F, 1.42F, 0.13F, false),

    SHOULDER_LEFT(-0.24F, 1.36F, 0.0F, false),
    SHOULDER_RIGHT(0.24F, 1.36F, 0.0F, false),

    HAND_RIGHT(0.36F, 0.95F, -0.05F, false);

    /** Neck pivot the head rotates around, in the same local space. */
    public static final float NECK_Y = 1.50F;

    private final float x;
    private final float y;
    private final float z;
    private final boolean headBone;

    AttachmentPoint(float x, float y, float z, boolean headBone) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.headBone = headBone;
    }

    public float localX() {
        return x;
    }

    public float localY() {
        return y;
    }

    public float localZ() {
        return z;
    }

    /** True when the anchor must follow head pitch and head yaw. */
    public boolean followsHead() {
        return headBone;
    }

    /** Convenience for tests and docs. */
    public Vec3 localOffset(Vec3 out) {
        return out.set(x, y, z);
    }
}
