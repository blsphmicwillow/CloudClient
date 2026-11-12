package net.cloudclient.client;

import net.cloudclient.CloudClient;
import net.cloudclient.CloudClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Decides whether an entity's <em>rendering</em> can be skipped.
 *
 * <p>Fair-play rules encoded here:
 * <ul>
 *   <li>Players are never culled. Hiding a player you could otherwise see is a
 *       competitive advantage, so it stays off the table entirely.</li>
 *   <li>Dropped items are never culled; missing loot is a gameplay cost.</li>
 *   <li>Only non-player entities beyond the configured distance are skipped.</li>
 * </ul>
 * Culling is render-only: the entity still ticks, still exists, and hit
 * detection is untouched.
 */
public final class CullingFilter {

    private CullingFilter() {
    }

    public static boolean shouldCull(Entity entity) {
        if (entity == null) return false;
        CloudClientRuntime runtime = CloudClient.runtimeOrNull();
        if (runtime == null || !runtime.isEnabled("performance.entity_culling")) return false;

        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null || entity == view) return false;

        if (entity instanceof EntityPlayer) return false;
        if (entity instanceof EntityItem) return false;

        int limit = runtime.config().getIntClamped(
                "module.performance.entity_culling.distance", 64, 16, 160);
        double limitSq = (double) limit * (double) limit;
        return entity.getDistanceSqToEntity(view) > limitSq;
    }
}
