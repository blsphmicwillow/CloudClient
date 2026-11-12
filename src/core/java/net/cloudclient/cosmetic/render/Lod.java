package net.cloudclient.cosmetic.render;

import net.cloudclient.math.FastMath;

/**
 * Distance-based LOD for cosmetics, with a crossfade so the swap is invisible.
 *
 * <p>{@link #blend(float)} returns a continuous position across three LOD
 * levels (0 = full mesh, 1 = medium, 2 = proxy). The renderer draws each level
 * with the alpha from {@link #alphaFor(int, float)}; those alphas sum to 1 for
 * any distance, so there is never a frame where a cosmetic disappears.
 */
public final class Lod {

    public static final float FULL_DISTANCE = 32.0F;
    public static final float PROXY_DISTANCE = 64.0F;
    /** Total width of each crossfade band, in blocks. */
    public static final float BAND = 4.0F;

    public static final int LEVEL_FULL = 0;
    public static final int LEVEL_MEDIUM = 1;
    public static final int LEVEL_PROXY = 2;

    private Lod() {
    }

    /** Continuous LOD coordinate in [0, 2]. */
    public static float blend(float distance) {
        if (distance <= FULL_DISTANCE - BAND * 0.5F) return 0.0F;
        if (distance >= PROXY_DISTANCE + BAND * 0.5F) return 2.0F;

        float halfBand = BAND * 0.5F;
        if (distance < FULL_DISTANCE + halfBand) {
            return FastMath.smoothstep(
                    (distance - (FULL_DISTANCE - halfBand)) / BAND);
        }
        if (distance > PROXY_DISTANCE - halfBand) {
            return 1.0F + FastMath.smoothstep(
                    (distance - (PROXY_DISTANCE - halfBand)) / BAND);
        }
        return 1.0F;
    }

    /** Render alpha for one level at a given distance; alphas sum to 1. */
    public static float alphaFor(int level, float distance) {
        float b = blend(distance);
        return FastMath.clamp(1.0F - Math.abs(b - level), 0.0F, 1.0F);
    }

    /** Cheapest level worth drawing at this distance. */
    public static int dominantLevel(float distance) {
        return Math.round(blend(distance));
    }

    /** True when a cosmetic should be skipped entirely (hysteresis-free hard cap). */
    public static boolean culled(float distance, float maxDistance) {
        return distance > maxDistance;
    }
}
