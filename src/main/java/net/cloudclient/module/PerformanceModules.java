package net.cloudclient.module;

import java.util.ArrayList;
import java.util.List;

import net.cloudclient.CloudClientRuntime;
import net.cloudclient.core.Module;
import net.minecraft.client.Minecraft;

/**
 * Performance modules.
 *
 * <p>Defaults are conservative and fair-play safe: entity culling is off and
 * never touches players or dropped items, and dynamic render distance is off
 * because changing video settings mid-fight surprises people.
 */
public final class PerformanceModules {

    private PerformanceModules() {
    }

    public static List<Module> create(final CloudClientRuntime runtime) {
        List<Module> modules = new ArrayList<Module>();

        CosmeticModules.Toggle culling = new CosmeticModules.Toggle("performance.entity_culling",
                "Entity Culling", "Skip rendering distant non-player mobs", 500, false);
        culling.settingInt("distance", "Distance", "Cull mobs beyond this many blocks",
                64, 16, 160);
        modules.add(culling);

        modules.add(new DynamicRenderDistance());
        return modules;
    }

    /**
     * Lowers render distance when the frame rate is poor and restores it when the
     * frame rate recovers. Cooldowns plus separate low/high thresholds prevent
     * oscillation.
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
        protected void onInit(net.cloudclient.core.ModuleContext context) {
            settingInt("min_chunks", "Minimum Chunks", "Never go below this render distance",
                    4, 2, 16);
            settingInt("fps_low", "Drop Below FPS", "Lower render distance under this frame rate",
                    45, 15, 120);
            settingInt("fps_high", "Restore Above FPS", "Raise render distance above this frame rate",
                    75, 30, 240);
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
            if (!isEnabled()) return;
            if (baseline <= 0) baseline = mc.gameSettings.renderDistanceChunks;

            int min = config().getIntClamped(path("min_chunks"), 4, 2, 16);
            int current = mc.gameSettings.renderDistanceChunks;

            if (cooldown > 0) {
                cooldown--;
                return;
            }
            int low = config().getIntClamped(path("fps_low"), 45, 15, 120);
            int high = config().getIntClamped(path("fps_high"), 75, 30, 240);
            if (high <= low) high = low + 10;

            int fps = Minecraft.getDebugFPS();
            if (fps < low && current > min) {
                mc.gameSettings.renderDistanceChunks = current - 1;
                cooldown = 60;
            } else if (fps > high && current < baseline) {
                mc.gameSettings.renderDistanceChunks = current + 1;
                cooldown = 100;
            }
        }
    }
}
