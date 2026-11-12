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
    public static final String VERSION = "0.1.1";

    private static CloudClientRuntime runtime;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Printed before anything else: when a crash report is pasted, this line
        // says which build produced it. A stale jar in mods/ was mistaken for a
        // failed fix once already.
        event.getModLog().info("Cloud Client " + VERSION + " (build " + buildStamp() + ")");
        runtime = new CloudClientRuntime(event.getSuggestedConfigurationFile());
    }

    /**
     * Git hash and build time, written by Gradle's buildStamp task. Returns
     * "dev" when running from an unpacked IDE output, where the stamp is absent.
     */
    public static String buildStamp() {
        java.io.InputStream in = CloudClient.class
                .getResourceAsStream("/cloudclient-build.properties");
        if (in == null) return "dev";
        try {
            java.util.Properties properties = new java.util.Properties();
            properties.load(in);
            String hash = properties.getProperty("build", "dev");
            String time = properties.getProperty("time", "");
            return time.isEmpty() ? hash : hash + " " + time;
        } catch (Exception e) {
            return "dev";
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
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
