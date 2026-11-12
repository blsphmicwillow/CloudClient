package net.cloudclient.cosmetic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.cloudclient.cosmetic.cape.CapeParams;
import net.cloudclient.cosmetic.cape.CapeSimulation;
import net.cloudclient.cosmetic.cape.CapeState;
import net.cloudclient.cosmetic.halo.Halo;
import net.cloudclient.cosmetic.horns.HornSet;
import net.cloudclient.cosmetic.pet.Pet;
import net.cloudclient.cosmetic.pet.PetFlock;
import net.cloudclient.cosmetic.wings.WingRig;
import net.cloudclient.cosmetic.wings.WingState;
import net.cloudclient.math.FastMath;
import net.cloudclient.math.Vec3;

/**
 * Holds cosmetic simulation state for every player the client is tracking and
 * advances it once per client tick.
 *
 * <p>Deliberately free of Minecraft types: the caller extracts position, yaw and
 * state flags and passes plain values in. That keeps the simulation testable and
 * confines the Forge layer to data extraction.
 */
public final class CosmeticWorld {

    /** Per-player simulation bundle. */
    public static final class PlayerCosmetics {
        public final CapeSimulation cape = new CapeSimulation();
        public final WingRig wings = new WingRig();
        public final Halo halo = new Halo();
        public final HornSet horns = new HornSet();
        public final Vec3 position = new Vec3();
        public final Vec3 previousPosition = new Vec3();
        public final Vec3 velocity = new Vec3();
        public final Vec3 anchorLeft = new Vec3();
        public final Vec3 anchorRight = new Vec3();
        public final Vec3 backAnchor = new Vec3();
        public float bodyYaw;
        public float headYaw;
        public float headPitch;
        public boolean sneaking;
        public boolean onGround = true;
        public boolean gliding;
        public int lastSeenTick;
        public float ageSeconds;
        public boolean initialised;
    }

    private final Map<Integer, PlayerCosmetics> players = new HashMap<Integer, PlayerCosmetics>();
    private final AnchorSolver anchors = new AnchorSolver();
    private final PetFlock pets = new PetFlock();
    private final Vec3 wind = new Vec3();
    private CosmeticTuning tuning = new CosmeticTuning();
    private int tick;

    public PlayerCosmetics get(int entityId, int currentTick) {
        PlayerCosmetics state = players.get(entityId);
        if (state == null) {
            state = new PlayerCosmetics();
            players.put(entityId, state);
        }
        state.lastSeenTick = currentTick;
        return state;
    }

    public PetFlock pets() {
        return pets;
    }

    public CosmeticTuning tuning() {
        return tuning;
    }

    /** Applied to every player on the next update; values are clamped by the caller. */
    public void setTuning(CosmeticTuning tuning) {
        if (tuning != null) this.tuning = tuning;
    }

    public int tick() {
        return tick;
    }

    /**
     * Advances one player's cosmetics for a single client tick.
     *
     * @param dt seconds since the previous tick
     */
    public void updatePlayer(PlayerCosmetics pc, float dt, Vec3 position, float bodyYaw,
                             float headYaw, float headPitch, boolean onGround,
                             boolean sneaking, boolean gliding) {
        pc.previousPosition.set(pc.position);
        pc.position.set(position);
        if (!pc.initialised) {
            pc.previousPosition.set(position);
            pc.initialised = true;
        }
        pc.velocity.set(position.x - pc.previousPosition.x,
                position.y - pc.previousPosition.y,
                position.z - pc.previousPosition.z);
        pc.bodyYaw = bodyYaw;
        pc.headYaw = headYaw;
        pc.headPitch = headPitch;
        pc.onGround = onGround;
        pc.sneaking = sneaking;
        pc.gliding = gliding;
        pc.ageSeconds += dt;

        anchors.solve(pc.position, bodyYaw, headYaw, headPitch,
                AttachmentPoint.BACK, pc.backAnchor);
        solveCapeAnchors(pc);

        float speed = pc.velocity.horizontalLength();
        CapeState capeState = CapeState.derive(speed, onGround,
                !onGround && pc.velocity.y < 0.0F, gliding);
        CapeParams target = capeState.hidesCape() ? CapeParams.idle() : capeState.params();

        // Sliders scale the state preset, so a sprinting cape still trails more
        // than an idle one no matter where the stiffness slider sits.
        target.stiffness = FastMath.clamp(target.stiffness * tuning.capeStiffness, 0.05F, 1.0F);
        target.windScale *= tuning.capeWind;
        target.flutterAmp = FastMath.clamp(target.flutterAmp * tuning.capeFlutter, 0.0F, 2.0F);

        // No global weather wind yet; velocity-driven trail does the work.
        wind.set(0.0F, 0.0F, 0.0F);
        pc.cape.update(dt, pc.anchorLeft, pc.anchorRight, wind, pc.velocity, pc.position, target);

        pc.wings.setState(WingState.derive(speed, onGround, gliding));
        pc.wings.configure(tuning.wingSpread, tuning.wingFlap);
        pc.wings.update(dt, pc.backAnchor, bodyYaw);

        pc.halo.configure(tuning.haloSpin, 0.55F, tuning.haloPulse, 0.03F, 0.35F);
        pc.halo.update(dt);

        pc.horns.size(tuning.hornScale).angles(tuning.hornPitch, 6.0F);
    }

    /**
     * Cape corners sit slightly inboard of the shoulders and a touch behind the
     * back plane, so the cloth starts at the shoulder blades rather than
     * intersecting the torso.
     */
    private void solveCapeAnchors(PlayerCosmetics pc) {
        anchors.solve(pc.position, pc.bodyYaw, pc.headYaw, pc.headPitch,
                AttachmentPoint.SHOULDER_LEFT, pc.anchorLeft);
        anchors.solve(pc.position, pc.bodyYaw, pc.headYaw, pc.headPitch,
                AttachmentPoint.SHOULDER_RIGHT, pc.anchorRight);
        pc.anchorLeft.y -= 0.08F;
        pc.anchorRight.y -= 0.08F;
        pc.anchorLeft.z += 0.09F;
        pc.anchorRight.z += 0.09F;
    }

    public void updatePets(float dt, Vec3 ownerPos, float bodyYaw, boolean sneaking) {
        pets.update(dt, ownerPos, bodyYaw, sneaking);
    }

    /** Drops simulation state for players not seen for {@code maxAgeTicks}. */
    public void evict(int currentTick, int maxAgeTicks) {
        Iterator<Map.Entry<Integer, PlayerCosmetics>> it = players.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, PlayerCosmetics> entry = it.next();
            if (currentTick - entry.getValue().lastSeenTick > maxAgeTicks) {
                it.remove();
            }
        }
    }

    public void endTick() {
        tick++;
    }

    /** Drops all simulation state, e.g. on disconnect or world change. */
    public void clear() {
        players.clear();
        // pets() is an unmodifiable view; PetFlock.clear() is the mutation path.
        pets.clear();
    }

    public List<Pet> petsSnapshot() {
        return new ArrayList<Pet>(pets.pets());
    }
}
