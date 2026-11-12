package net.cloudclient.module;

import java.util.ArrayList;
import java.util.List;

import net.cloudclient.CloudClientRuntime;
import net.cloudclient.core.Module;
import net.minecraft.client.Minecraft;

/**
 * Performance modules.
 *
 * <p>Defaults are deliberately conservative and fair-play safe:
 * <ul>
 *   <li>Entity culling is <b>off</b> by default and never culls players.</li>
 *   <li>Particle filtering is <b>off</b> by default and only ever suppresses
 *       cosmetic particle spawns, never entity hit detection.</li>
 *   <li>Dynamic render distance is <b>off</b> by default because changing video
 *       settings mid-fight surprises people.</li>
 * </ul>
 */
public final class PerformanceModules {

    private PerformanceModules() {
    }

    public static List<Module> create(final CloudClientRuntime runtime) {
        List<Module> modules = new ArrayList<Module>();
        modules.add(new CosmeticModules.Toggle("performance.entity_culling",
                "Entity Culling",
                "Skip rendering distant non-player entities", 500, false));
        modules.add(new CosmeticModules.Toggle("performance.pvp_particles",
                "PvP Particles",
                "Suppress crit/potion/block-break particles at the source", 510, false));
        modules.add(new DynamicRenderDistance());
        return modules;
    }

    /**
     * Lowers render distance when the frame rate is poor and restores it when
     * the frame rate recovers. Uses hysteresis so it cannot oscillate.
     */
    public static final class DynamicRenderDistance extends Module {
        private int baseline = -1;
        private int cooldown;

        @Override
        public String id() {
            return "performance.dynamic_render_distance";
        }

        @Override
        public String displayName() {
            return "Dynamic Render Distance";
        }

        @Override
        public String description() {
            return "Reduce render distance when FPS drops, restore when stable";
        }

        @Override
        public int order() {
            return 520;
        }

        @Override
        public boolean defaultEnabled() {
            return false;
        }

        @Override
        protected void onEnable() {
            Minecraft mc = Minecraft.getMinecraft();
            baseline = mc == null ? -1 : mc.gameSettings.renderDistanceChunks;
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && baseline > 0) {
                mc.gameSettings.renderDistanceChunks = baseline;
            }
        }

        /** Called from the client tick. */
        public void tick(Minecraft mc) {
            if (!isEnabled() || mc.gameSettings.hideGUI) return;
            if (baseline <= 0) baseline = mc.gameSettings.renderDistanceChunks;

            int fps = Minecraft.getDebugFPS();
            int min = config().getIntClamped(path("min_chunks"), 4, 2, 16);
            int current = mc.gameSettings.renderDistanceChunks;

            if (cooldown > 0) {
                cooldown--;
                return;
            }
            int lowThreshold = config().getInt(path("fps_low"), 45);
            int highThreshold = config().getInt(path("fps_high"), 75);

            if (fps < lowThreshold && current > min) {
                mc.gameSettings.renderDistanceChunks = current - 1;
                cooldown = 60;
            } else if (fps > highThreshold && current < baseline) {
                mc.gameSettings.renderDistanceChunks = current + 1;
                cooldown = 100;
            }
        }
    }
}
