package net.cloudclient.cosmetic.wings;

/** Wing animation states and their procedural flap parameters. */
public enum WingState {

    IDLE(0.80F, 11.0F, 8.0F),
    WALKING(1.20F, 14.0F, 10.0F),
    SPRINTING(3.20F, 28.0F, 16.0F),
    FALLING(1.10F, 8.0F, 34.0F),
    GLIDING(0.45F, 5.0F, 40.0F);

    private final float frequencyHz;
    private final float amplitudeDeg;
    private final float spreadDeg;

    WingState(float frequencyHz, float amplitudeDeg, float spreadDeg) {
        this.frequencyHz = frequencyHz;
        this.amplitudeDeg = amplitudeDeg;
        this.spreadDeg = spreadDeg;
    }

    public float frequencyHz() {
        return frequencyHz;
    }

    public float amplitudeDeg() {
        return amplitudeDeg;
    }

    /** Baseline outward/upward spread, so wings never hang flat against the back. */
    public float spreadDeg() {
        return spreadDeg;
    }

    public static WingState derive(float horizontalSpeed, boolean onGround, boolean gliding) {
        if (gliding) return GLIDING;
        if (!onGround) return FALLING;
        if (horizontalSpeed > 0.155F) return SPRINTING;
        if (horizontalSpeed > 0.02F) return WALKING;
        return IDLE;
    }
}
