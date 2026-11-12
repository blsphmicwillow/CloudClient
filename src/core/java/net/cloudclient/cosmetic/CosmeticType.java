package net.cloudclient.cosmetic;

/** Cosmetic categories, each bound to a primary attachment anchor. */
public enum CosmeticType {

    CAPE("cape", AttachmentPoint.BACK),
    WINGS("wings", AttachmentPoint.BACK),
    HALO("halo", AttachmentPoint.HEAD_TOP),
    HORNS("horns", AttachmentPoint.HEAD_SIDE_RIGHT),
    PET("pet", AttachmentPoint.SHOULDER_RIGHT),
    HAT("hat", AttachmentPoint.HEAD_TOP),
    AURA("aura", AttachmentPoint.BACK);

    private final String id;
    private final AttachmentPoint primaryAnchor;

    CosmeticType(String id, AttachmentPoint primaryAnchor) {
        this.id = id;
        this.primaryAnchor = primaryAnchor;
    }

    public String id() {
        return id;
    }

    public AttachmentPoint primaryAnchor() {
        return primaryAnchor;
    }

    public static CosmeticType byId(String id) {
        for (CosmeticType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        return null;
    }
}
