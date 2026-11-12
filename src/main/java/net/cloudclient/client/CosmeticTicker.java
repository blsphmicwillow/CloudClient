package net.cloudclient.client;

import java.util.List;

import net.cloudclient.cosmetic.CosmeticProfile;
import net.cloudclient.cosmetic.CosmeticWorld;
import net.cloudclient.cosmetic.CosmeticType;
import net.cloudclient.math.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

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

    @SuppressWarnings("unchecked")
    public void tick(CosmeticWorld world, Minecraft mc, CosmeticProfile profile) {
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
    }
}
