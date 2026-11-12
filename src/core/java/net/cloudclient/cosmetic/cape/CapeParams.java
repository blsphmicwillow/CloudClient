package net.cloudclient.cosmetic.cape;

import net.cloudclient.math.FastMath;

/**
 * Tunable cape physics constants.
 *
 * <p>Animation states are not separate simulations: each state maps to one of
 * these parameter sets and the live instance is damped toward its target over
 * {@link #BLEND_SECONDS}. That is what makes idle -> sprint transitions look
 * continuous instead of snapping.
 */
public final class CapeParams {

    /** State blend time; the brief asks for 5-10 ticks (0.25-0.5s). */
    public static final float BLEND_SECONDS = 0.3F;

    public float gravity = -24.0F;
    public float drag = 0.985F;
    public float stiffness = 0.90F;
    public float windScale = 1.0F;
    public float flutterAmp = 0.40F;
    public float flutterFreq = 1.10F;
    public float trailScale = 0.0F;
    /** Extra upward lift, used by the falling state so the cape flaps up. */
    public float lift = 0.0F;

    public CapeParams set(float gravity, float drag, float stiffness, float windScale,
                          float flutterAmp, float flutterFreq, float trailScale, float lift) {
        this.gravity = gravity;
        this.drag = drag;
        this.stiffness = stiffness;
        this.windScale = windScale;
        this.flutterAmp = flutterAmp;
        this.flutterFreq = flutterFreq;
        this.trailScale = trailScale;
        this.lift = lift;
        return this;
    }

    public CapeParams copyFrom(CapeParams other) {
        return set(other.gravity, other.drag, other.stiffness, other.windScale,
                other.flutterAmp, other.flutterFreq, other.trailScale, other.lift);
    }

    public CapeParams copy() {
        return new CapeParams().copyFrom(this);
    }

    /**
     * Exponential damping toward {@code target}. Frame-rate independent, so a
     * 240 FPS client and a 60 FPS client blend identically.
     */
    public CapeParams blendToward(CapeParams target, float dt) {
        float t = BLEND_SECONDS <= 0.0F
                ? 1.0F
                : 1.0F - (float) Math.pow(0.5D, dt / BLEND_SECONDS);
        gravity = FastMath.lerp(gravity, target.gravity, t);
        drag = FastMath.lerp(drag, target.drag, t);
        stiffness = FastMath.lerp(stiffness, target.stiffness, t);
        windScale = FastMath.lerp(windScale, target.windScale, t);
        flutterAmp = FastMath.lerp(flutterAmp, target.flutterAmp, t);
        flutterFreq = FastMath.lerp(flutterFreq, target.flutterFreq, t);
        trailScale = FastMath.lerp(trailScale, target.trailScale, t);
        lift = FastMath.lerp(lift, target.lift, t);
        return this;
    }

    /** Presets; see {@link CapeState}. */
    public static CapeParams idle() {
        return new CapeParams().set(-24.0F, 0.985F, 0.90F, 1.0F, 0.40F, 1.10F, 0.00F, 0.0F);
    }

    public static CapeParams walking() {
        return new CapeParams().set(-24.0F, 0.984F, 0.88F, 1.05F, 0.50F, 1.35F, 0.18F, 0.0F);
    }

    public static CapeParams sprinting() {
        return new CapeParams().set(-21.0F, 0.980F, 0.80F, 1.20F, 0.80F, 2.20F, 0.95F, 0.0F);
    }

    public static CapeParams falling() {
        // Low gravity + lift + loose constraints = the cape rides up while falling.
        return new CapeParams().set(-9.0F, 0.982F, 0.55F, 1.35F, 1.10F, 3.00F, 0.25F, 14.0F);
    }
}
