package net.cloudclient.cosmetic.cape;

/**
 * Cape animation states.
 *
 * <p>{@link #ELYTRA} is a visibility flag rather than a simulation preset: a
 * player wearing an elytra must not also render a cape, or the two meshes
 * intersect. The renderer checks {@link #hidesCape()} before drawing.
 */
public enum CapeState {

    IDLE(CapeParams.idle(), false),
    WALKING(CapeParams.walking(), false),
    SPRINTING(CapeParams.sprinting(), false),
    FALLING(CapeParams.falling(), false),
    ELYTRA(null, true);

    private final CapeParams params;
    private final boolean hidesCape;

    CapeState(CapeParams params, boolean hidesCape) {
        this.params = params;
        this.hidesCape = hidesCape;
    }

    public CapeParams params() {
        return params;
    }

    public boolean hidesCape() {
        return hidesCape;
    }

    /**
     * Derives state from movement inputs. Kept pure so the renderer never embeds
     * gameplay logic in a mixin.
     *
     * @param horizontalSpeed blocks per tick (0.13 walking, 0.16+ sprinting)
     */
    public static CapeState derive(float horizontalSpeed, boolean onGround,
                                   boolean falling, boolean hasElytraFlying) {
        if (hasElytraFlying) return ELYTRA;
        if (!onGround) return FALLING;
        if (horizontalSpeed > 0.155F) return SPRINTING;
        if (horizontalSpeed > 0.02F) return WALKING;
        return IDLE;
    }
}
