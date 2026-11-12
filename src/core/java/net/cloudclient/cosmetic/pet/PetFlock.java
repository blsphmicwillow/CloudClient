package net.cloudclient.cosmetic.pet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.cloudclient.math.FastMath;
import net.cloudclient.math.Vec3;

/**
 * Client-side flock of pets that follow their owner.
 *
 * <p>Two behaviours, deliberately separated:
 * <ul>
 *   <li>A critically-damped spring pulls each pet to its slot around the owner.
 *       Damping is tuned so the pet settles without orbiting.</li>
 *   <li>Pairwise separation keeps pets from occupying the same space. This is
 *       the only O(n^2) loop in the client and n is capped at
 *       {@link #MAX_PETS}, so it is 6 comparisons worst case.</li>
 * </ul>
 *
 * <p>Pets are purely local: no entity is spawned, no packet is sent.
 */
public final class PetFlock {

    public static final int MAX_PETS = 4;

    private static final float SPRING = 26.0F;
    private static final float DAMPING = 8.5F;
    private static final float MAX_DT = 0.05F;

    private final List<Pet> pets = new ArrayList<Pet>();
    private final Vec3 desired = new Vec3();
    private final Vec3 delta = new Vec3();
    private final Vec3 push = new Vec3();
    private float clock;

    public Pet add(PetType type, String name) {
        if (pets.size() >= MAX_PETS) {
            throw new IllegalStateException("Pet limit is " + MAX_PETS);
        }
        Pet pet = new Pet(type, name);
        pet.bobPhase(pets.size() * 37);
        // Deterministic spread so pets do not spawn stacked on the owner.
        float angle = (float) (Math.PI * 2.0D * pets.size() / MAX_PETS);
        pet.targetOffset().set((float) Math.cos(angle) * 0.75F, 0.15F,
                (float) Math.sin(angle) * 0.75F);
        pets.add(pet);
        return pet;
    }

    public boolean remove(Pet pet) {
        return pets.remove(pet);
    }

    public List<Pet> pets() {
        return Collections.unmodifiableList(pets);
    }

    public int size() {
        return pets.size();
    }

    /**
     * @param ownerPos      owner feet position
     * @param bodyYawDeg    owner body yaw
     * @param ownerSneaking lowers the follow height when crouched
     */
    public void update(float dt, Vec3 ownerPos, float bodyYawDeg, boolean ownerSneaking) {
        float step = FastMath.clamp(dt, 0.0F, MAX_DT);
        if (step <= 0.0F) return;
        clock += step;

        // Matches AnchorSolver: local +x is right, +z is behind.
        float yawRad = (float) Math.toRadians(180.0D - bodyYawDeg);
        float cos = (float) StrictMath.cos(yawRad);
        float sin = (float) StrictMath.sin(yawRad);

        for (Pet pet : pets) {
            Vec3 offset = pet.targetOffset();
            if (pet.shoulderRiding()) {
                // Mounted on the shoulder: snap hard, no wandering.
                offset.set(-0.26F, ownerSneaking ? 1.10F : 1.36F, 0.02F);
            } else if (ownerSneaking) {
                offset.y = 0.05F;
            }
            // Rotate the local offset into world space.
            float wx = offset.x * cos + offset.z * sin;
            float wz = -offset.x * sin + offset.z * cos;
            desired.set(ownerPos.x + wx, ownerPos.y + offset.y, ownerPos.z + wz);

            delta.set(desired.x - pet.position().x,
                    desired.y - pet.position().y,
                    desired.z - pet.position().z);

            float stiffness = pet.shoulderRiding() ? SPRING * 3.0F : SPRING;
            float damping = pet.shoulderRiding() ? DAMPING * 2.2F : DAMPING;

            Vec3 vel = pet.velocity();
            vel.addScaled(delta, stiffness * step);
            vel.addScaled(vel, -damping * step);
            if (vel.lengthSq() > 400.0F) {
                vel.normalise().scale(20.0F);
            }
            Vec3 pos = pet.position();
            pos.addScaled(vel, step);
            if (!pos.isFinite()) {
                pos.set(desired);
                vel.set(0.0F, 0.0F, 0.0F);
            }
        }

        separate();
    }

    private void separate() {
        for (int i = 0; i < pets.size(); i++) {
            for (int j = i + 1; j < pets.size(); j++) {
                Pet a = pets.get(i);
                Pet b = pets.get(j);
                float minDist = 0.42F * (a.type().restingSize() + b.type().restingSize());
                float dx = b.position().x - a.position().x;
                float dy = b.position().y - a.position().y;
                float dz = b.position().z - a.position().z;
                float distSq = dx * dx + dy * dy + dz * dz;
                if (distSq >= minDist * minDist || distSq < 1.0E-8F) continue;
                float dist = (float) StrictMath.sqrt(distSq);
                float overlap = (minDist - dist) / dist * 0.5F;
                push.set(dx * overlap, dy * overlap * 0.35F, dz * overlap);
                a.position().sub(push);
                b.position().add(push);
            }
        }
    }

    /** Vertical idle bob for rendering only; keeps the physics deterministic. */
    public float bob(Pet pet) {
        return (float) StrictMath.sin(clock * 2.4F + pet.bobPhase() * 0.17F) * 0.035F;
    }
}
