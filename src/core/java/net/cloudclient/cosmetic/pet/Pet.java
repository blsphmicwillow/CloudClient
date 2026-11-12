package net.cloudclient.cosmetic.pet;

import net.cloudclient.math.Vec3;

/** One local, client-only pet. Never has a server entity id. */
public final class Pet {

    private final PetType type;
    private final Vec3 position = new Vec3();
    private final Vec3 velocity = new Vec3();
    private final Vec3 targetOffset = new Vec3();
    private String name;
    private float scale = 1.0F;
    private boolean shoulderRiding;
    private int bobPhase;
    private float r = 1.0F;
    private float g = 1.0F;
    private float b = 1.0F;

    public Pet(PetType type, String name) {
        this.type = type;
        this.name = name;
    }

    public PetType type() {
        return type;
    }

    public Vec3 position() {
        return position;
    }

    public Vec3 velocity() {
        return velocity;
    }

    /** Offset from the owner, in owner-local space (x right, y up, z behind). */
    public Vec3 targetOffset() {
        return targetOffset;
    }

    public String name() {
        return name;
    }

    public Pet rename(String name) {
        this.name = name == null || name.trim().isEmpty() ? type.id() : name.trim();
        return this;
    }

    public float scale() {
        return scale;
    }

    public Pet scale(float scale) {
        this.scale = Math.max(0.3F, Math.min(2.5F, scale));
        return this;
    }

    public boolean shoulderRiding() {
        return shoulderRiding;
    }

    public Pet setShoulderRiding(boolean riding) {
        this.shoulderRiding = riding;
        return this;
    }

    public int bobPhase() {
        return bobPhase;
    }

    public Pet bobPhase(int phase) {
        this.bobPhase = phase;
        return this;
    }

    public Pet tint(float r, float g, float b) {
        this.r = r;
        this.g = g;
        this.b = b;
        return this;
    }

    public float tintR() {
        return r;
    }

    public float tintG() {
        return g;
    }

    public float tintB() {
        return b;
    }
}
