package net.cloudclient.cosmetic.horns;

import net.cloudclient.math.Mat4;
import net.cloudclient.math.Vec3;

/**
 * A matched pair of horns with user-adjustable size, pitch and yaw.
 *
 * <p>The mount transform is applied in head space so horns inherit head
 * rotation exactly: the renderer pushes the head bone transform, then this
 * transform, then the mesh.
 */
public final class HornSet {

    public enum Style {
        RIGID, SOFT
    }

    private Style style = Style.SOFT;
    private float scale = 1.0F;
    private float pitchDegrees = 18.0F;
    private float yawDegrees = 6.0F;
    private float curvatureDegrees = 40.0F;
    private float baseRadius = 0.045F;
    private float length = 0.30F;
    private float tipRadius = 0.006F;

    private HornGeometry.Mesh leftMesh;
    private HornGeometry.Mesh rightMesh;
    private final Mat4 tipMount = new Mat4();
    private final Mat4 tipScratch = new Mat4();

    public HornSet style(Style style) {
        this.style = style;
        rebuild();
        return this;
    }

    public HornSet size(float scale) {
        this.scale = Math.max(0.2F, Math.min(3.0F, scale));
        return this;
    }

    public HornSet angles(float pitchDegrees, float yawDegrees) {
        this.pitchDegrees = pitchDegrees;
        this.yawDegrees = yawDegrees;
        return this;
    }

    public HornSet shape(float curvatureDegrees, float baseRadius, float length, float tipRadius) {
        this.curvatureDegrees = style == Style.RIGID ? 0.0F : curvatureDegrees;
        this.baseRadius = baseRadius;
        this.length = length;
        this.tipRadius = tipRadius;
        rebuild();
        return this;
    }

    public void rebuild() {
        float curvature = style == Style.RIGID ? 0.0F : curvatureDegrees;
        leftMesh = HornGeometry.build(8, 8, baseRadius, length, curvature, tipRadius);
        rightMesh = HornGeometry.build(8, 8, baseRadius, length, curvature, tipRadius);
    }

    public HornGeometry.Mesh mesh(boolean left) {
        if (left ? leftMesh == null : rightMesh == null) rebuild();
        return left ? leftMesh : rightMesh;
    }

    /**
     * Builds the horn's model matrix in head space.
     *
     * @param left true for the player's left horn
     * @param out  destination matrix, overwritten
     */
    public void mountMatrix(boolean left, Mat4 out) {
        // Head space follows AnchorSolver: local +x is the player's right.
        float sign = left ? -1.0F : 1.0F;
        out.identity()
                .translate(sign * 0.075F, 0.055F, 0.0F)
                .rotateZ(sign * -yawDegrees)
                .rotateX(pitchDegrees)
                .scale(scale, scale, scale);
    }

    /**
     * Tip position in head-local space. Does not mutate {@code headSpace}: the
     * renderer passes the same head matrix to both horns.
     */
    public void tipLocal(boolean left, Mat4 headSpace, Vec3 out) {
        mountMatrix(left, tipMount);
        tipScratch.copyFrom(headSpace).multiply(tipMount);
        tipScratch.transformPoint(0.0F, length, 0.0F, out);
    }

    public Style style() {
        return style;
    }

    public float scale() {
        return scale;
    }
}
