package net.cloudclient.math;

/**
 * Lookup-table trigonometry for per-frame render maths.
 *
 * <p>Used only for values that feed geometry and animation, never for physics
 * integration or anything the server can observe. Error is bounded by table
 * resolution plus linear interpolation; {@code FastMathTest} asserts the bound,
 * so a future table-size change cannot silently degrade cape motion.
 */
public final class FastMath {

    /** 16k entries: 22.5 PM of input resolution per step before interpolation. */
    private static final int BITS = 14;
    private static final int SIZE = 1 << BITS;
    private static final int MASK = SIZE - 1;
    private static final float TWO_PI = (float) (Math.PI * 2.0D);
    private static final float STEP = TWO_PI / SIZE;
    private static final float[] SIN = new float[SIZE + 1];

    static {
        for (int i = 0; i <= SIZE; i++) {
            SIN[i] = (float) StrictMath.sin(i * (double) STEP);
        }
    }

    private FastMath() {
    }

    /** Radians. Max absolute error is below 1e-4 with this table size. */
    public static float sin(float radians) {
        float wrapped = radians % TWO_PI;
        if (wrapped < 0.0F) wrapped += TWO_PI;
        float scaled = wrapped / STEP;
        int i = (int) scaled;
        float frac = scaled - i;
        int a = i & MASK;
        int b = (i + 1) & MASK;
        return SIN[a] + (SIN[b] - SIN[a]) * frac;
    }

    public static float cos(float radians) {
        return sin(radians + (float) (Math.PI * 0.5D));
    }

    public static float sinDeg(float degrees) {
        return sin((float) Math.toRadians(degrees));
    }

    public static float cosDeg(float degrees) {
        return cos((float) Math.toRadians(degrees));
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Frame-rate independent smoothing: approaches {@code target} with a
     * half-life of {@code halfLifeSeconds}, so physics looks identical at 60 and
     * 240 FPS.
     */
    public static float damp(float current, float target, float halfLifeSeconds, float dt) {
        if (halfLifeSeconds <= 0.0F) return target;
        float factor = 1.0F - (float) Math.pow(0.5D, dt / halfLifeSeconds);
        return current + (target - current) * factor;
    }

    public static float smoothstep(float t) {
        float x = clamp(t, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** Wraps degrees into [0, 360). */
    public static float wrapDegrees(float degrees) {
        float d = degrees % 360.0F;
        if (d < 0.0F) d += 360.0F;
        return d;
    }

    /** Shortest signed angular delta from {@code from} to {@code to}, in degrees. */
    public static float angleDelta(float from, float to) {
        float d = wrapDegrees(to - from);
        if (d > 180.0F) d -= 360.0F;
        return d;
    }
}
