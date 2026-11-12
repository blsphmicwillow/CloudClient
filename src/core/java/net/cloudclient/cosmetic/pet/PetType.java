package net.cloudclient.cosmetic.pet;

/** Built-in pets. Mob-based pets reuse the vanilla model; geometric pets are meshes. */
public enum PetType {

    BABY_ZOMBIE("baby_zombie", Kind.MOB, 0.45F),
    WOLF("wolf", Kind.MOB, 0.40F),
    CAT("cat", Kind.MOB, 0.35F),
    PARROT("parrot", Kind.MOB, 0.30F),
    ORB("orb", Kind.GEOMETRIC, 0.18F),
    MINI_DRAGON("mini_dragon", Kind.GEOMETRIC, 0.30F),
    CUBE_COMPANION("cube_companion", Kind.GEOMETRIC, 0.22F);

    public enum Kind {
        MOB, GEOMETRIC
    }

    private final String id;
    private final Kind kind;
    private final float restingSize;

    PetType(String id, Kind kind, float restingSize) {
        this.id = id;
        this.kind = kind;
        this.restingSize = restingSize;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    /** Approximate half-extent in blocks; used for flock separation radius. */
    public float restingSize() {
        return restingSize;
    }

    public static PetType byId(String id) {
        for (PetType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        return null;
    }
}
