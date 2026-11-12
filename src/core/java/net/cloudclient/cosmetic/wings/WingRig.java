package net.cloudclient.cosmetic.wings;

import net.cloudclient.math.FastMath;
import net.cloudclient.math.Mat4;
import net.cloudclient.math.Vec3;

/**
 * Four-bone procedural wing rig: root, inner joint, outer joint, tip.
 *
 * <p>Each bone rotates about Z (flap up/down) and X (sweep), with the phase
 * delayed per bone. Delaying the phase down the chain is what produces the whip
 * in the wing tip instead of a rigid paddle. The right wing is the mirror of the
 * left, driven from the same phase so both wings stay in sync.
 *
 * <p>All maths is in pure Java so the rig can be tested without an OpenGL
 * context; the renderer only reads {@link #point(int, Vec3)}.
 */
public final class WingRig {

    /** Segment lengths in blocks: root, inner, outer, tip. */
    private static final float[] BONE_LENGTHS = {0.14F, 0.30F, 0.26F, 0.16F};
    /** Phase lag per bone, in radians. */
    private static final float[] PHASE_LAG = {0.0F, 0.35F, 0.70F, 1.05F};
    /** Flap contribution per bone (tip moves most). */
    private static final float[] FLAP_WEIGHT = {0.45F, 0.70F, 0.75F, 0.60F};

    /** Five points: root plus one per bone. */
    public static final int POINTS = BONE_LENGTHS.length + 1;

    private final Vec3[] points = new Vec3[POINTS];
    private final Mat4[] chain = new Mat4[POINTS];
    private final Mat4 scratch = new Mat4();
    private final Mat4 cursor = new Mat4();
    private final Mat4 boneRot = new Mat4();
    private final Vec3 mirrorLocal = new Vec3();
    private final Mat4 toLocal = new Mat4();
    private final Mat4 toWorld = new Mat4();

    private WingState state = WingState.IDLE;
    private float phase;
    private float currentSpread = WingState.IDLE.spreadDeg();
    private float currentAmplitude = WingState.IDLE.amplitudeDeg();
    private float currentFrequency = WingState.IDLE.frequencyHz();
    private float flapDegrees;

    public WingRig() {
        for (int i = 0; i < POINTS; i++) {
            points[i] = new Vec3();
            chain[i] = new Mat4();
        }
    }

    public WingState state() {
        return state;
    }

    public void setState(WingState state) {
        this.state = state;
    }

    /** Current root flap angle, exposed for tests and for HUD debug. */
    public float flapDegrees() {
        return flapDegrees;
    }

    public Vec3 point(int index, Vec3 out) {
        return out.set(points[index]);
    }

    /**
     * Poses both wings.
     *
     * @param dt       frame delta, seconds
     * @param anchor   world position of the BACK anchor
     * @param yawDeg   player body yaw in degrees
     */
    public void update(float dt, Vec3 anchor, float yawDeg) {
        if (dt < 0.0F) dt = 0.0F;
        // Smooth state transitions so sprinting does not snap the wings.
        currentSpread = FastMath.damp(currentSpread, state.spreadDeg(), 0.18F, dt);
        currentAmplitude = FastMath.damp(currentAmplitude, state.amplitudeDeg(), 0.18F, dt);
        currentFrequency = FastMath.damp(currentFrequency, state.frequencyHz(), 0.25F, dt);

        phase += dt * currentFrequency * 6.2831855F;
        if (phase > 6.2831855F * 1024.0F) phase -= 6.2831855F * 1024.0F;

        float rootFlap = currentSpread * 0.35F
                + (float) StrictMath.sin(phase) * currentAmplitude;
        flapDegrees = rootFlap;

        // Body transform: yaw only; wings are symmetric about the body plane.
        // Same convention as AnchorSolver: local +x is the player's right.
        // Translate first, then rotate the local frame (see AnchorSolver).
        scratch.identity().translate(anchor.x, anchor.y, anchor.z)
                .rotateY(180.0F - yawDeg);
        chain[0].copyFrom(scratch);
        points[0].set(anchor.x, anchor.y, anchor.z);

        cursor.copyFrom(scratch);
        for (int bone = 0; bone < BONE_LENGTHS.length; bone++) {
            float flap = (float) StrictMath.sin(phase - PHASE_LAG[bone])
                    * currentAmplitude * FLAP_WEIGHT[bone]
                    - currentSpread * 0.55F;
            // Each bone hinges at the previous joint.
            boneRot.identity().rotateY(-12.0F * (bone + 1)).rotateZ(flap);
            cursor.multiply(boneRot);
            cursor.translate(BONE_LENGTHS[bone], 0.0F, 0.0F);
            cursor.transformPoint(0.0F, 0.0F, 0.0F, points[bone + 1]);
            chain[bone + 1].copyFrom(cursor);
        }
    }

    /**
     * Mirrors a solved point to the opposite wing by reflecting across the
     * player's local YZ plane. The renderer calls this for the second wing, so
     * the sim only ever runs once.
     */
    public void mirroredPoint(int index, Vec3 anchor, float yawDeg, Vec3 out) {
        float theta = 180.0F - yawDeg;
        mirrorLocal.set(points[index].x - anchor.x,
                points[index].y - anchor.y,
                points[index].z - anchor.z);
        // world -> local, reflect across the body plane, local -> world.
        toLocal.identity().rotateY(-theta);
        toWorld.identity().rotateY(theta);
        toLocal.transformPoint(mirrorLocal.x, mirrorLocal.y, mirrorLocal.z, mirrorLocal);
        mirrorLocal.x = -mirrorLocal.x;
        toWorld.transformPoint(mirrorLocal.x, mirrorLocal.y, mirrorLocal.z, mirrorLocal);
        out.set(anchor.x + mirrorLocal.x, anchor.y + mirrorLocal.y, anchor.z + mirrorLocal.z);
    }

    /** Emission budget: never more than five particles per tick per wing. */
    public static final class TipEmitter {
        public static final int BUDGET_PER_TICK = 5;
        private int emittedThisTick;
        private int lastTick = -1;

        public boolean tryEmit(int currentTick) {
            if (currentTick != lastTick) {
                lastTick = currentTick;
                emittedThisTick = 0;
            }
            if (emittedThisTick >= BUDGET_PER_TICK) return false;
            emittedThisTick++;
            return true;
        }
    }
}
