package net.cloudclient.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.cloudclient.core.config.CloudConfig;

/**
 * Registry and lifecycle owner for all modules.
 *
 * <p>Modules are registered before {@link #initAll()} and never removed at
 * runtime, so the module list can be treated as immutable afterwards. That keeps
 * iteration allocation-free on hot paths.
 */
public final class ModuleManager {

    private static final Comparator<Module> BY_ORDER = new Comparator<Module>() {
        @Override
        public int compare(Module a, Module b) {
            return Integer.compare(a.order(), b.order());
        }
    };

    private final EventBus bus;
    private final CloudConfig config;
    private final Log log;
    private final Map<String, Module> byId = new LinkedHashMap<String, Module>();
    private final List<Module> ordered = new ArrayList<Module>();
    private boolean initialised;

    public ModuleManager(EventBus bus, CloudConfig config, Log log) {
        this.bus = bus;
        this.config = config;
        this.log = log;
    }

    public <T extends Module> T register(T module) {
        if (initialised) throw new IllegalStateException("Register modules before initAll()");
        Module existing = byId.put(module.id(), module);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
        ordered.add(module);
        return module;
    }

    public void initAll() {
        if (initialised) throw new IllegalStateException("Already initialised");
        initialised = true;
        Collections.sort(ordered, BY_ORDER);
        ModuleContext context = new ModuleContext(bus, config, log);
        for (Module module : ordered) {
            module.attach(context);
        }
        for (Module module : ordered) {
            module.initialise();
        }
        log.info("Initialised " + ordered.size() + " modules ("
                + enabledCount() + " enabled)");
    }

    public void shutdownAll() {
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ordered.get(i).shutdown();
        }
    }

    public int enabledCount() {
        int n = 0;
        for (Module m : ordered) if (m.isEnabled()) n++;
        return n;
    }

    public Module get(String id) {
        return byId.get(id);
    }

    public <T extends Module> T get(Class<T> type) {
        for (Module m : ordered) {
            if (type.isInstance(m)) return type.cast(m);
        }
        return null;
    }

    /** Immutable, order-stable view for UI listing. */
    public List<Module> modules() {
        return Collections.unmodifiableList(ordered);
    }

    public boolean setEnabled(String id, boolean enabled) {
        Module m = byId.get(id);
        if (m == null) return false;
        m.setEnabled(enabled);
        return true;
    }

    public boolean toggle(String id) {
        Module m = byId.get(id);
        if (m == null) return false;
        m.toggle();
        return true;
    }
}
