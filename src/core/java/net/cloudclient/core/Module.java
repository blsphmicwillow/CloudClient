package net.cloudclient.core;

import net.cloudclient.core.config.CloudConfig;

/**
 * A single feature: FPS counter, cape renderer, pet AI, entity culling.
 *
 * <p>Contract for implementers:
 * <ul>
 *   <li>{@link #onInit} runs exactly once. Subscribe to the bus here, once.</li>
 *   <li>Handlers must check {@link #isEnabled()} before doing work. This keeps
 *       toggling free (one boolean branch) instead of churning bus
 *       subscriptions every time a user flips a switch in the mod menu.</li>
 *   <li>Never post events or touch GL from a worker thread; hop back to the
 *       main thread first.</li>
 * </ul>
 */
public abstract class Module {

    private ModuleContext context;
    private boolean enabled;

    /** Stable, unique, config-safe id (lowercase, dot-separated). */
    public abstract String id();

    public String displayName() {
        return id();
    }

    public String description() {
        return "";
    }

    /** Enabled on a fresh install? Expensive cosmetic modules default off. */
    public boolean defaultEnabled() {
        return true;
    }

    /** Lower runs first during init. Use for dependency ordering only. */
    public int order() {
        return 100;
    }

    protected void onInit(ModuleContext context) {
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onShutdown() {
    }

    public final ModuleContext context() {
        if (context == null) {
            throw new IllegalStateException("Module " + id() + " used before init");
        }
        return context;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean value) {
        if (this.enabled == value) return;
        this.enabled = value;
        if (context == null) return;
        context.config().set(configKey(), value);
        if (value) {
            guard("enable", new Runnable() {
                @Override
                public void run() {
                    onEnable();
                }
            });
        } else {
            guard("disable", new Runnable() {
                @Override
                public void run() {
                    onDisable();
                }
            });
        }
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    final void attach(ModuleContext context) {
        this.context = context;
    }

    final void initialise() {
        boolean want = context.config().getBool(configKey(), defaultEnabled());
        // Assign directly: setEnabled() would write the same value back.
        this.enabled = want;
        onInit(context);
        if (enabled) {
            guard("enable", new Runnable() {
                @Override
                public void run() {
                    onEnable();
                }
            });
        }
    }

    final void shutdown() {
        guard("shutdown", new Runnable() {
            @Override
            public void run() {
                onShutdown();
            }
        });
    }

    /** Config key holding this module's on/off state. */
    public final String configKey() {
        return "module." + id() + ".enabled";
    }

    /**
     * One misbehaving module must not take the client down with it, so lifecycle
     * callbacks are wrapped and reported. The exception is still logged loudly.
     */
    private void guard(String phase, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            Log log = context == null ? Log.CONSOLE : context.log();
            log.error("Module '" + id() + "' threw during " + phase, t);
        }
    }

    /** Convenience: a module-scoped config path, e.g. {@code cape.physics.stiffness}. */
    protected final String path(String suffix) {
        return "module." + id() + "." + suffix;
    }

    protected final CloudConfig config() {
        return context().config();
    }
}
