package net.cloudclient.module;

import java.util.ArrayList;
import java.util.List;

import net.cloudclient.CloudClientRuntime;
import net.cloudclient.core.Module;

/**
 * Cosmetic modules and the settings each one exposes.
 *
 * <p>Settings are declared here rather than in the menu: the menu renders
 * whatever a module declares, so adding a slider never means touching the UI.
 */
public final class CosmeticModules {

    private CosmeticModules() {
    }

    public static List<Module> create(CloudClientRuntime runtime) {
        List<Module> modules = new ArrayList<Module>();

        modules.add(new Toggle("cosmetic.core", "Cosmetic Engine",
                "Master switch for the cosmetic renderer", 10, true));

        Toggle cape = new Toggle("cosmetic.cape", "Animated Capes",
                "Verlet cloth capes with animation states", 20, true);
        cape.settingNumber("stiffness", "Stiffness", "How firmly the cloth holds its shape",
                0.90F, 0.50F, 1.00F);
        cape.settingNumber("wind", "Wind", "Strength of ambient wind on the cloth",
                1.00F, 0.00F, 2.50F);
        cape.settingNumber("flutter", "Flutter", "Travelling-wave amplitude",
                0.40F, 0.00F, 1.50F);
        modules.add(cape);

        Toggle wings = new Toggle("cosmetic.wings", "Wings",
                "Four-bone rigged wings with procedural flapping", 30, true);
        wings.settingNumber("flap_speed", "Flap Speed", "Multiplier on the flap frequency",
                1.00F, 0.20F, 2.50F);
        wings.settingNumber("spread", "Spread", "How far the wings sit from the back",
                1.00F, 0.40F, 2.00F);
        wings.settingToggle("particles", "Tip Particles", "Emit trail particles from the wing tips",
                true);
        modules.add(wings);

        Toggle halo = new Toggle("cosmetic.halo", "Halos",
                "Emissive spinning halos", 40, true);
        halo.settingNumber("spin", "Spin", "Degrees per second", 45.0F, 0.0F, 360.0F);
        halo.settingNumber("pulse", "Pulse", "Brightness oscillation depth", 0.18F, 0.0F, 0.90F);
        halo.settingColour("colour", "Colour", "Halo tint", 0xFFD27A);
        modules.add(halo);

        Toggle horns = new Toggle("cosmetic.horns", "Horns",
                "Paired rigid or curved horns", 50, true);
        horns.settingNumber("scale", "Scale", "Horn size multiplier", 1.00F, 0.30F, 2.50F);
        horns.settingNumber("pitch", "Pitch", "Forward/backward tilt in degrees", 18.0F, -45.0F, 60.0F);
        modules.add(horns);

        Toggle pets = new Toggle("cosmetic.pets", "Local Pets",
                "Client-only companion pets", 60, true);
        pets.settingNumber("scale", "Pet Scale", "Size multiplier", 1.00F, 0.40F, 2.00F);
        pets.settingToggle("shoulder", "Shoulder Riding", "Perch pets on the shoulder", false);
        modules.add(pets);

        return modules;
    }

    /** A module carrying an on/off flag plus declarative settings. */
    static class Toggle extends Module {
        private final String id;
        private final String name;
        private final String description;
        private final int order;
        private final boolean defaultEnabled;

        Toggle(String id, String name, String description, int order, boolean defaultEnabled) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.order = order;
            this.defaultEnabled = defaultEnabled;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public boolean defaultEnabled() {
            return defaultEnabled;
        }
    }
}
