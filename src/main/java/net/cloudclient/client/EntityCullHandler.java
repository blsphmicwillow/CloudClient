package net.cloudclient.client;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Skips rendering distant living entities.
 *
 * <p>Implemented with {@link RenderLivingEvent.Pre} rather than a Mixin: the
 * event is cancellable in 1.8.9, which is exactly the hook needed, and it keeps
 * the mod free of a Mixin dependency (no tweaker, no extra jar to install, and
 * nothing to break when mappings change).
 *
 * <p>Players are filtered out inside {@link CullingFilter}, so this can never
 * hide another player.
 */
@SideOnly(Side.CLIENT)
public final class EntityCullHandler {

    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Pre event) {
        EntityLivingBase entity = event.entity;
        if (entity == null) return;
        if (CullingFilter.shouldCull(entity)) {
            event.setCanceled(true);
        }
    }
}
