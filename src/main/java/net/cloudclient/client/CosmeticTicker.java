package net.cloudclient.client;

import java.util.List;

import net.cloudclient.CloudClientRuntime;
import net.cloudclient.cosmetic.CosmeticProfile;
import net.cloudclient.cosmetic.CosmeticType;
import net.cloudclient.cosmetic.CosmeticWorld;
import net.cloudclient.cosmetic.wings.WingRig;
import net.cloudclient.math.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;

/**
 * Extracts per-player transform data from Minecraft and feeds the core
 * simulation. This class contains no physics and no GL calls.
 *
 * <p>1.8.9 has no elytra, so {@code gliding} is always false here. The field is
 * kept in the core API because the wing and cape states already model it.
 */
public final class CosmeticTicker {

    private static final float TICK_SECONDS = 0.05F;
    private static final int MAX_TRACKED_PLAYERS = 64;

    private final Vec3 position = new Vec3();
    private final Vec3 wingTip = new Vec3();
    private final WingRig.TipEmitter emitter = new WingRig.TipEmitter();

    public void tick(CloudClientRuntime runtime, CosmeticWorld world, Minecraft mc,
                     CosmeticProfile profile) {
        EntityPlayer self = mc.thePlayer;
        if (self == null || mc.theWorld == null) return;

        List<EntityPlayer> players = mc.theWorld.playerEntities;
        int limit = Math.min(players.size(), MAX_TRACKED_PLAYERS);
        for (int i = 0; i < limit; i++) {
            EntityPlayer player = players.get(i);
            if (player == null) continue;

            position.set((float) player.posX, (float) player.posY, (float) player.posZ);
            CosmeticWorld.PlayerCosmetics state =
                    world.get(player.getEntityId(), world.tick());

            world.updatePlayer(state, TICK_SECONDS, position,
                    player.renderYawOffset,
                    player.rotationYawHead,
                    player.rotationPitch,
                    player.onGround,
                    player.isSneaking(),
                    false);
        }

        // Pets belong to the local player only; they are not replicated.
        if (profile.isEquipped(CosmeticType.PET)) {
            position.set((float) self.posX, (float) self.posY, (float) self.posZ);
            world.updatePets(TICK_SECONDS, position, self.renderYawOffset, self.isSneaking());
        }

        emitWingParticles(runtime, world, mc, self);
    }

    /**
     * Wing-tip trail. Local-only particles: they exist in this client's
     * EffectRenderer and vanish when the player leaves, so nothing about them is
     * visible to a server or to other players.
     */
    private void emitWingParticles(CloudClientRuntime runtime, CosmeticWorld world,
                                   Minecraft mc, EntityPlayer self) {
        if (!runtime.tuning().wingParticles) return;
        if (!runtime.profile().isEquipped(CosmeticType.WINGS)) return;
        if (!runtime.isEnabled("cosmetic.wings")) return;

        CosmeticWorld.PlayerCosmetics state = world.get(self.getEntityId(), world.tick());
        if (!state.initialised) return;

        int tipIndex = WingRig.POINTS - 1;
        for (int side = 0; side < 2; side++) {
            if (!emitter.tryEmit(world.tick())) return;
            if (side == 0) {
                state.wings.point(tipIndex, wingTip);
            } else {
                state.wings.mirroredPoint(tipIndex, state.backAnchor, state.bodyYaw, wingTip);
            }
            mc.effectRenderer.spawnEffectParticle(
                    EnumParticleTypes.SPELL_INSTANT.getParticleID(),
                    wingTip.x, wingTip.y, wingTip.z, 0.0D, 0.01D, 0.0D);
        }
    }
}
