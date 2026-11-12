package net.cloudclient.core;

import net.cloudclient.core.config.CloudConfig;

/** Services handed to every module on init. */
public final class ModuleContext {

    private final EventBus bus;
    private final CloudConfig config;
    private final Log log;

    public ModuleContext(EventBus bus, CloudConfig config, Log log) {
        this.bus = bus;
        this.config = config;
        this.log = log;
    }

    public EventBus bus() {
        return bus;
    }

    public CloudConfig config() {
        return config;
    }

    public Log log() {
        return log;
    }
}
