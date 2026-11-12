package net.cloudclient.mixin;

import net.cloudclient.client.ParticleFilter;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rejects cosmetic particles at spawn time instead of culling them during
 * render, so they never enter the particle list and never tick.
 *
 * <p>Target signature (1.8.9 MCP): {@code addEffect(EntityFX)}.
 */
@Mixin(EffectRenderer.class)
public final class MixinEffectRenderer {

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void cloudclient$filterParticle(EntityFX particle, CallbackInfo ci) {
        if (ParticleFilter.shouldBlock(particle)) {
            ci.cancel();
        }
    }
}
