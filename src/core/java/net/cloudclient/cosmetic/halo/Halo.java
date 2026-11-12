package net.cloudclient.cosmetic.halo;

import net.cloudclient.math.FastMath;

/**
 * Emissive halo: spin, brightness pulse and optional hover.
 *
 * <p>The mesh is a flat annulus generated once and reused; only the model
 * matrix and emissive colour change per frame, which keeps halos effectively
 * free on the GPU.
 */
public final class Halo {

    private float angleDegrees;
    private float brightness = 1.0F;
    private float hoverOffset;

    private float spinSpeed = 45.0F;
    private float pulseHz = 0.55F;
    private float pulseDepth = 0.18F;
    private float hoverAmplitude = 0.03F;
    private float hoverHz = 0.35F;
    private float time;

    public Halo configure(float spinDegPerSecond, float pulseHz, float pulseDepth,
                          float hoverAmplitude, float hoverHz) {
        this.spinSpeed = spinDegPerSecond;
        this.pulseHz = pulseHz;
        this.pulseDepth = FastMath.clamp(pulseDepth, 0.0F, 0.9F);
        this.hoverAmplitude = hoverAmplitude;
        this.hoverHz = hoverHz;
        return this;
    }

    public void update(float dt) {
        if (dt < 0.0F) dt = 0.0F;
        time += dt;
        angleDegrees = FastMath.wrapDegrees(angleDegrees + spinSpeed * dt);
        brightness = FastMath.clamp(
                1.0F - pulseDepth * 0.5F + (float) StrictMath.sin(time * pulseHz * 6.2831855F) * pulseDepth * 0.5F,
                0.0F, 1.0F);
        hoverOffset = (float) StrictMath.sin(time * hoverHz * 6.2831855F) * hoverAmplitude;
    }

    public float angleDegrees() {
        return angleDegrees;
    }

    public float brightness() {
        return brightness;
    }

    public float hoverOffset() {
        return hoverOffset;
    }

    /**
     * Static ring mesh in local space, lying flat in the XZ plane.
     *
     * @return {@code {positions, indices}} with positions {@code x,y,z}
     *         interleaved and indices as triangle list
     */
    public static Mesh buildRing(float innerRadius, float outerRadius, int segments) {
        int n = Math.max(3, segments);
        float[] positions = new float[(n + 1) * 2 * 3];
        int[] indices = new int[n * 6];

        for (int i = 0; i <= n; i++) {
            float a = (float) (Math.PI * 2.0D * i / n);
            float cos = (float) StrictMath.cos(a);
            float sin = (float) StrictMath.sin(a);
            int base = i * 6;
            positions[base] = cos * innerRadius;
            positions[base + 1] = 0.0F;
            positions[base + 2] = sin * innerRadius;
            positions[base + 3] = cos * outerRadius;
            positions[base + 4] = 0.0F;
            positions[base + 5] = sin * outerRadius;
        }

        for (int i = 0; i < n; i++) {
            int v = i * 2;
            int o = i * 6;
            // inner_i, outer_i, outer_i+1, inner_i+1
            indices[o] = v;
            indices[o + 1] = v + 1;
            indices[o + 2] = v + 3;
            indices[o + 3] = v;
            indices[o + 4] = v + 3;
            indices[o + 5] = v + 2;
        }
        return new Mesh(positions, indices);
    }

    /** Immutable mesh payload shared by the halo and horn builders. */
    public static final class Mesh {
        public final float[] positions;
        public final int[] indices;

        public Mesh(float[] positions, int[] indices) {
            this.positions = positions;
            this.indices = indices;
        }

        public int vertexCount() {
            return positions.length / 3;
        }

        public int triangleCount() {
            return indices.length / 3;
        }
    }
}
