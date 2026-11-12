package net.cloudclient;

import java.io.File;
import java.io.IOException;

import net.cloudclient.core.EventBus;
import net.cloudclient.core.Log;
import net.cloudclient.core.Module;
import net.cloudclient.core.ModuleManager;
import net.cloudclient.core.config.CloudConfig;
import net.cloudclient.cosmetic.CosmeticProfile;
import net.cloudclient.client.CosmeticTicker;
import net.cloudclient.cosmetic.CosmeticWorld;
import net.cloudclient.gui.CloudModMenu;
import net.cloudclient.hud.Hud;
import net.cloudclient.module.CosmeticModules;
import net.cloudclient.module.HudModules;
import net.cloudclient.module.PerformanceModules;
import net.cloudclient.render.CosmeticRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * Owns long-lived client state and bridges core modules to Forge events.
 *
 * <p>Threading: all handlers here run on the client thread. The chunk-mesh
 * worker pool never touches GL; it publishes finished buffers through a queue
 * drained on the render thread.
 */
@SideOnly(Side.CLIENT)
public final class CloudClientRuntime {

    private final File configFile;
    private final EventBus bus = new EventBus();
    private final Log log = new ForgeLog();
    private final CloudConfig config;
    private final ModuleManager modules;
    private final CosmeticWorld cosmetics = new CosmeticWorld();
    private final CosmeticRenderer renderer = new CosmeticRenderer();
    private final Hud hud = new Hud();
    private final CosmeticTicker ticker = new CosmeticTicker();

    private final KeyBinding menuKey = new KeyBinding(
            "key.cloudclient.menu", Keyboard.KEY_RSHIFT, "key.categories.cloudclient");

    private CosmeticProfile profile = CosmeticProfile.starter();
    private boolean initialised;
    private int tickCounter;

    public CloudClientRuntime(File configFile) {
        this.configFile = configFile;
        this.config = new CloudConfig(configFile);
        this.modules = new ModuleManager(bus, config, log);
    }

    public void init() {
        if (initialised) return;
        initialised = true;

        try {
            config.load();
        } catch (IOException | RuntimeException e) {
            // A corrupt config must never stop the client from starting; the old
            // file is left untouched and rewritten on exit.
            log.error("Could not read " + configFile + "; starting with defaults", e);
        }

        for (Module module : CosmeticModules.create(this)) modules.register(module);
        for (Module module : HudModules.create(this)) modules.register(module);
        for (Module module : PerformanceModules.create(this)) modules.register(module);
        modules.initAll();

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new net.cloudclient.client.EntityCullHandler());
        ClientRegistry.registerKeyBinding(menuKey);

        log.info(CloudClient.NAME + " " + CloudClient.VERSION + " ready ("
                + modules.enabledCount() + "/" + modules.modules().size() + " modules)");
    }

    // ------------------------------------------------------------- accessors

    public EventBus bus() {
        return bus;
    }

    public CloudConfig config() {
        return config;
    }

    public ModuleManager modules() {
        return modules;
    }

    public CosmeticWorld cosmetics() {
        return cosmetics;
    }

    public CosmeticRenderer renderer() {
        return renderer;
    }

    public Hud hud() {
        return hud;
    }

    public CosmeticProfile profile() {
        return profile;
    }

    public void setProfile(CosmeticProfile profile) {
        this.profile = profile;
    }

    public boolean isEnabled(String moduleId) {
        Module module = modules.get(moduleId);
        return module != null && module.isEnabled();
    }

    // ---------------------------------------------------------- forge events

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            cosmetics.clear();
            return;
        }

        while (menuKey.isPressed()) {
            mc.displayGuiScreen(new CloudModMenu(this));
        }

        ticker.tick(cosmetics, mc, profile);
        net.cloudclient.module.PerformanceModules.DynamicRenderDistance dynamic =
                modules.get(net.cloudclient.module.PerformanceModules.DynamicRenderDistance.class);
        if (dynamic != null) dynamic.tick(mc);
        cosmetics.endTick();
        if ((tickCounter++ & 0x3F) == 0) {
            cosmetics.evict(cosmetics.tick(), 200);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;
        renderer.renderWorld(this, mc, event.partialTicks);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        hud.render(this, Minecraft.getMinecraft());
    }

    /** Persists config; safe to call from UI actions. */
    public void saveConfig() {
        try {
            config.save();
        } catch (IOException e) {
            log.error("Could not save config", e);
        }
    }

    public void shutdown() {
        saveConfig();
        modules.shutdownAll();
    }

    /** Bridges core logging onto the console/Forge log stream. */
    private static final class ForgeLog implements Log {
        @Override
        public void info(String message) {
            org.apache.logging.log4j.LogManager.getLogger("CloudClient").info(message);
        }

        @Override
        public void warn(String message) {
            org.apache.logging.log4j.LogManager.getLogger("CloudClient").warn(message);
        }

        @Override
        public void error(String message, Throwable cause) {
            org.apache.logging.log4j.LogManager.getLogger("CloudClient").error(message, cause);
        }
    }
}
