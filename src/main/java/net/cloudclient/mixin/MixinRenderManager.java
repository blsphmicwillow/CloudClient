package net.cloudclient.mixin;

import net.cloudclient.client.CullingFilter;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips rendering for culled entities.
 *
 * <p>Cancelling at HEAD of {@code doRenderEntity} prevents the model from being
 * submitted at all, which is the only way culling actually saves frame time.
 * The entity still ticks and still exists server-side.
 *
 * <p>Target signature (1.8.9 MCP):
 * {@code doRenderEntity(Entity, double, double, double, float, float, boolean)}.
 * Verify against your mappings snapshot if the injection fails to apply.
 */
@Mixin(RenderManager.class)
public final class MixinRenderManager {

    @Inject(method = "doRenderEntity", at = @At("HEAD"), cancellable = true)
    private void cloudclient$cullEntity(Entity entity, double x, double y, double z,
                                        float entityYaw, float partialTicks,
                                        boolean showName, CallbackInfo ci) {
        if (CullingFilter.shouldCull(entity)) {
            ci.cancel();
        }
    }
}
