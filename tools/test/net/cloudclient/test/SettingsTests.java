package net.cloudclient.test;

import java.util.LinkedHashMap;
import java.util.Map;

import net.cloudclient.core.config.Setting;
import net.cloudclient.core.gui.HudLayout;
import net.cloudclient.cosmetic.CosmeticTuning;
import net.cloudclient.cosmetic.CosmeticWorld;
import net.cloudclient.cosmetic.wings.WingRig;
import net.cloudclient.math.Vec3;

/**
 * Tests for the HUD layout maths and the settings helpers that do not need a
 * config file. Run alongside {@link CoreTests}.
 */
public final class SettingsTests {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        hudDefaults();
        hudDragClamping();
        hudResetAndVisibility();
        keyNames();
        cosmeticWiring();
        tuningClamps();
        wingTuning();

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void hudDefaults() {
        HudLayout layout = new HudLayout();
        layout.declare("fps", "FPS", 4, 4, 40, 10);
        HudLayout.Element element = layout.get("fps");
        check("declare stores position", element != null && element.x == 4 && element.y == 4);
        check("declare stores size", element.width == 40 && element.height == 10);
        check("unknown id is null", layout.get("nope") == null);
        check("all() lists elements", layout.all().size() == 1);
    }

    private static void hudDragClamping() {
        HudLayout layout = new HudLayout();
        layout.declare("cps", "CPS", 10, 20, 30, 12);

        check("beginDrag inside element", layout.beginDrag(15, 25));
        check("beginDrag exposes element", layout.dragging() != null);
        // Drag keeps the grab offset, so grabbing 5px into the element and moving
        // the cursor to (100,200) puts the origin at (95,195).
        layout.dragTo(100, 200, 320, 240);
        HudLayout.Element element = layout.get("cps");
        check("drag follows grab offset", element.x == 95 && element.y == 195);

        layout.dragTo(-500, -500, 320, 240);
        check("drag clamps to left/top", element.x == 0 && element.y == 0);

        layout.dragTo(1000, 1000, 320, 240);
        check("drag clamps to right", element.x == 320 - 30);
        check("drag clamps to bottom", element.y == 240 - 12);

        layout.endDrag();
        check("endDrag clears drag", layout.dragging() == null);
        check("beginDrag outside element", !layout.beginDrag(-10, -10));
    }

    private static void hudResetAndVisibility() {
        HudLayout layout = new HudLayout();
        layout.declare("fps", "FPS", 4, 4, 40, 10);
        layout.get("fps").x = 99;

        Map<String, float[]> defaults = new LinkedHashMap<String, float[]>();
        defaults.put("fps", new float[]{4, 4});
        layout.resetPositions(defaults);
        check("reset restores default position", layout.get("fps").x == 4 && layout.get("fps").y == 4);

        layout.get("fps").visible = false;
        check("visibility can be toggled", !layout.get("fps").visible);
    }

    private static void keyNames() {
        check("keyName escape", "ESC".equals(Setting.keyName(1)));
        // LWJGL 2 scan codes: A=30, S=31, W=17 — not alphabetical.
        check("keyName A", "A".equals(Setting.keyName(30)));
        check("keyName S", "S".equals(Setting.keyName(31)));
        check("keyName W", "W".equals(Setting.keyName(17)));
        check("keyName RSHIFT", "RSHIFT".equals(Setting.keyName(54)));
        check("keyName zero", "0".equals(Setting.keyName(11)));
        check("keyName F5", "F5".equals(Setting.keyName(63)));
        check("keyName unknown", Setting.keyName(9999) != null);
    }

    /**
     * Guards the reported boot crash: clear() used to call clear() on the
     * unmodifiable view from PetFlock.pets().
     */
    private static void cosmeticWiring() {
        CosmeticWorld world = new CosmeticWorld();
        CosmeticWorld.PlayerCosmetics state = world.get(1, 0);
        world.setTuning(new CosmeticTuning().sanitise());

        world.updatePlayer(state, 0.05F, new Vec3(0.0F, 64.0F, 0.0F),
                0.0F, 0.0F, 0.0F, true, false, false);
        world.updatePlayer(state, 0.05F, new Vec3(0.2F, 64.0F, 0.0F),
                0.0F, 0.0F, 0.0F, true, false, false);
        check("player simulation initialises", state.initialised);

        world.clear();
        check("clear() does not throw", world.pets().size() == 0);
        check("clear() empties tracked players", world.get(2, 1) != null);
    }

    private static void tuningClamps() {
        CosmeticTuning tuning = new CosmeticTuning();
        tuning.capeStiffness = 99.0F;
        tuning.haloPulse = -3.0F;
        tuning.hornScale = 0.0F;
        tuning.wingFlap = 0.0F;
        tuning.petScale = 40.0F;
        tuning.sanitise();

        check("stiffness clamped high", tuning.capeStiffness <= 1.60F);
        check("halo pulse clamped low", tuning.haloPulse >= 0.0F);
        check("horn scale clamped low", tuning.hornScale >= 0.20F);
        check("wing flap clamped low", tuning.wingFlap >= 0.10F);
        check("pet scale clamped high", tuning.petScale <= 2.50F);
        check("colour splits to red", Math.abs(tuning.haloRed() - 1.0F) < 0.01F);
    }

    private static void wingTuning() {
        WingRig rig = new WingRig();
        check("wing defaults are neutral", rig.spreadScale() == 1.0F && rig.flapScale() == 1.0F);
        rig.configure(50.0F, 0.0F);
        check("wing spread clamps high", rig.spreadScale() == 2.0F);
        check("wing flap clamps low", rig.flapScale() == 0.1F);
    }

    private static void check(String label, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + label);
        }
    }
}
