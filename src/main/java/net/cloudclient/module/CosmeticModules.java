package net.cloudclient.module;

import java.util.ArrayList;
import java.util.List;

import net.cloudclient.CloudClientRuntime;
import net.cloudclient.core.Module;

/**
 * Cosmetic modules.
 *
 * <p>These are pure feature gates: the renderer asks
 * {@code runtime.isEnabled("cosmetic.cape")} before drawing. Keeping them
 * separate from the renderer means a user can disable the heavy cape cloth
 * without touching the wings rig, exactly as the brief requires.
 */
public final class CosmeticModules {

    private CosmeticModules() {
    }

    public static List<Module> create(CloudClientRuntime runtime) {
        List<Module> modules = new ArrayList<Module>();
        modules.add(new Toggle("cosmetic.core", "Cosmetic Engine",
                "Master switch for the cosmetic renderer", 10, true));
        modules.add(new Toggle("cosmetic.cape", "Animated Capes",
                "Verlet cloth capes with animation states", 20, true));
        modules.add(new Toggle("cosmetic.wings", "Wings",
                "Four-bone rigged wings with procedural flapping", 30, true));
        modules.add(new Toggle("cosmetic.halo", "Halos",
                "Emissive spinning halos", 40, true));
        modules.add(new Toggle("cosmetic.horns", "Horns",
                "Paired rigid or curved horns", 50, true));
        modules.add(new Toggle("cosmetic.pets", "Local Pets",
                "Client-only companion pets", 60, true));
        return modules;
    }

    /** A module whose only job is to carry an on/off flag. */
    static final class Toggle extends Module {
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
