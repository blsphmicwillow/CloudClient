package net.cloudclient.client;

import net.cloudclient.CloudClient;
import net.cloudclient.CloudClientRuntime;
import net.minecraft.client.particle.EntityFX;

/**
 * Suppresses purely cosmetic particles at their source, before they enter the
 * render list.
 *
 * <p>Only visual effects are matched, by vanilla particle class name: crit
 * sparks, potion swirls and block-break dust. Nothing that carries gameplay
 * information (damage hearts, item pickup, portals) is touched.
 */
public final class ParticleFilter {

    private ParticleFilter() {
    }

    public static boolean shouldBlock(EntityFX particle) {
        if (particle == null) return false;
        CloudClientRuntime runtime = CloudClient.runtimeOrNull();
        if (runtime == null || !runtime.isEnabled("performance.pvp_particles")) return false;

        String name = particle.getClass().getSimpleName().toLowerCase();

        if (matches(runtime, "module.performance.pvp_particles.crit", true, name, "crit")) {
            return true;
        }
        if (matches(runtime, "module.performance.pvp_particles.potion", true, name,
                "spell", "potion")) {
            return true;
        }
        return matches(runtime, "module.performance.pvp_particles.block_break", true, name,
                "blockdust", "digging");
    }

    private static boolean matches(CloudClientRuntime runtime, String key, boolean def,
                                   String name, String... needles) {
        if (!runtime.config().getBool(key, def)) return false;
        for (String needle : needles) {
            if (name.contains(needle)) return true;
        }
        return false;
    }
}
