package net.cloudclient;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Cloud Client entry point.
 *
 * <p>{@code clientSideOnly = true} means Forge refuses to load this mod on a
 * dedicated server, which is the first structural guarantee that cosmetic and
 * HUD modules can never become a server-side advantage.
 */
@Mod(
        modid = CloudClient.MOD_ID,
        name = CloudClient.NAME,
        version = CloudClient.VERSION,
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]"
)
public final class CloudClient {

    public static final String MOD_ID = "cloudclient";
    public static final String NAME = "Cloud Client";
    public static final String VERSION = "0.1.0";

    private static CloudClientRuntime runtime;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        runtime = new CloudClientRuntime(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        runtime.init();
    }

    public static CloudClientRuntime runtime() {
        if (runtime == null) {
            throw new IllegalStateException("Cloud Client runtime accessed before preInit");
        }
        return runtime;
    }

    /**
     * Null-safe accessor for code that can run before {@code preInit} (mixins are
     * applied and some render hooks can fire earlier than the mod's own setup).
     */
    public static CloudClientRuntime runtimeOrNull() {
        return runtime;
    }
}
