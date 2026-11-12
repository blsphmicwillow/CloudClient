package net.cloudclient.cosmetic;

import net.cloudclient.math.FastMath;

/**
 * User-tunable cosmetic parameters, filled from config once per client tick.
 *
 * <p>Kept as plain values in the core rather than read from config inside the
 * simulation: the physics stays testable with no config file, and the defaults
 * here are the same numbers a fresh install writes.
 *
 * <p>Multipliers are 1.0 by default so a state preset (sprinting, falling) keeps
 * its own character; the sliders scale that preset instead of replacing it.
 */
public final class CosmeticTuning {

    public float capeStiffness = 1.0F;
    public float capeWind = 1.0F;
    public float capeFlutter = 1.0F;

    public float haloSpin = 45.0F;
    public float haloPulse = 0.18F;
    public int haloColour = 0xFFD27A;

    public float hornScale = 1.0F;
    public float hornPitch = 18.0F;

    public float wingSpread = 1.0F;
    public float wingFlap = 1.0F;
    public boolean wingParticles = true;

    public float petScale = 1.0F;

    /** Clamps everything into ranges the simulation can safely integrate. */
    public CosmeticTuning sanitise() {
        capeStiffness = FastMath.clamp(capeStiffness, 0.30F, 1.60F);
        capeWind = FastMath.clamp(capeWind, 0.0F, 2.50F);
        capeFlutter = FastMath.clamp(capeFlutter, 0.0F, 2.00F);
        haloSpin = FastMath.clamp(haloSpin, 0.0F, 360.0F);
        haloPulse = FastMath.clamp(haloPulse, 0.0F, 0.90F);
        hornScale = FastMath.clamp(hornScale, 0.20F, 3.00F);
        hornPitch = FastMath.clamp(hornPitch, -60.0F, 75.0F);
        wingSpread = FastMath.clamp(wingSpread, 0.20F, 2.00F);
        wingFlap = FastMath.clamp(wingFlap, 0.10F, 3.00F);
        petScale = FastMath.clamp(petScale, 0.30F, 2.50F);
        return this;
    }

    public float haloRed() {
        return ((haloColour >> 16) & 0xFF) / 255.0F;
    }

    public float haloGreen() {
        return ((haloColour >> 8) & 0xFF) / 255.0F;
    }

    public float haloBlue() {
        return (haloColour & 0xFF) / 255.0F;
    }
}
