package net.cloudclient.cosmetic;

import net.cloudclient.math.Mat4;
import net.cloudclient.math.Vec3;

/**
 * Converts player transforms into world-space anchors and head/torso matrices.
 *
 * <p>Convention (matching Minecraft, and asserted by the unit tests):
 * <ul>
 *   <li>yaw 0 faces +Z, increasing yaw turns toward -X;</li>
 *   <li>in player-local space {@code +x} is the player's right, {@code +y} is up
 *       from the feet, {@code +z} points behind the player;</li>
 *   <li>the local-to-world mapping is therefore {@code rotateY(180 - yaw)}.</li>
 * </ul>
 *
 * <p>Head-mounted cosmetics are transformed about the neck pivot by the head's
 * yaw and pitch before the body yaw is applied.
 */
public final class AnchorSolver {

    private final Mat4 headTransform = new Mat4();
    private final Mat4 bodyTransform = new Mat4();
    private final Vec3 tmp = new Vec3();

    /**
     * Builds the local-to-world matrix for a player.
     *
     * @param headSpace true to include the head transform (for halos, horns,
     *                  hats); false for torso/back attachments
     * @param dest      matrix written into; returned for chaining
     */
    public Mat4 matrix(Vec3 pos, float bodyYawDeg, float headYawDeg, float headPitchDeg,
                       boolean headSpace, Mat4 dest) {
        // Order matters: place the player in the world first, then rotate the
        // local frame. rotate-then-translate would rotate the translation and
        // put anchors in a mirrored world position.
        bodyTransform.identity()
                .translate(pos.x, pos.y, pos.z)
                .rotateY(180.0F - bodyYawDeg);
        dest.copyFrom(bodyTransform);

        if (headSpace) {
            headTransform.identity()
                    .translate(0.0F, -AttachmentPoint.NECK_Y, 0.0F)
                    .rotateY(-(headYawDeg - bodyYawDeg))
                    .rotateX(headPitchDeg)
                    .translate(0.0F, AttachmentPoint.NECK_Y, 0.0F);
            dest.multiply(headTransform);
        }
        return dest;
    }

    public Vec3 solve(Vec3 pos, float bodyYawDeg, float headYawDeg, float headPitchDeg,
                      AttachmentPoint point, Vec3 out) {
        matrix(pos, bodyYawDeg, headYawDeg, headPitchDeg, point.followsHead(), bodyTransform);
        bodyTransform.transformPoint(point.localX(), point.localY(), point.localZ(), tmp);
        return out.set(tmp);
    }

    /** Convenience overload that allocates; use the {@link Vec3} form in hot paths. */
    public Vec3 solve(Vec3 pos, float bodyYawDeg, float headYawDeg, float headPitchDeg,
                      AttachmentPoint point) {
        return solve(pos, bodyYawDeg, headYawDeg, headPitchDeg, point, new Vec3());
    }
}
