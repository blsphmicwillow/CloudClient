package net.cloudclient.module;

import java.util.ArrayList;
import java.util.List;

import net.cloudclient.CloudClientRuntime;
import net.cloudclient.core.Module;

/**
 * HUD module toggles.
 *
 * <p>These ids must match the ones {@code Hud} queries ({@code hud.fps},
 * {@code hud.cps}, {@code hud.keystrokes}, {@code hud.armor}); a mismatch would
 * silently disable an overlay, which is why they live next to each other in the
 * module list rather than as strings scattered through the renderer.
 */
public final class HudModules {

    private HudModules() {
    }

    public static List<Module> create(CloudClientRuntime runtime) {
        List<Module> modules = new ArrayList<Module>();
        modules.add(new CosmeticModules.Toggle("hud.fps", "FPS Counter",
                "Frame rate readout", 200, true));
        modules.add(new CosmeticModules.Toggle("hud.cps", "CPS Counter",
                "Clicks per second readout", 210, true));
        modules.add(new CosmeticModules.Toggle("hud.keystrokes", "Keystrokes",
                "WASD key display with press states", 220, true));
        modules.add(new CosmeticModules.Toggle("hud.armor", "Armor Status",
                "Armour durability percentages", 230, true));
        return modules;
    }
}
