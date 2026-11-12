package net.cloudclient.cosmetic.asset;

/**
 * Frame selection for vertically stacked animated textures.
 *
 * <p>Animated capes use one texture with N frames stacked top to bottom. Instead
 * of uploading each frame and re-binding per frame (bind spam, which is what
 * hurts frame time when several players wear animated capes), the client binds
 * the sheet once and moves the V coordinate. That is the same idea as an atlas
 * packer without the runtime packing cost.
 */
public final class AnimatedTexture {

    private final int frames;
    private final float fps;

    public AnimatedTexture(int frames, float fps) {
        this.frames = Math.max(1, frames);
        this.fps = fps <= 0.0F ? 1.0F : fps;
    }

    public int frames() {
        return frames;
    }

    public boolean isAnimated() {
        return frames > 1;
    }

    /** Frame index for a wall-clock time, wrapping forever. */
    public int frameAt(float seconds) {
        if (frames == 1) return 0;
        float t = seconds < 0.0F ? 0.0F : seconds;
        int frame = (int) (t * fps);
        return frame % frames;
    }

    /** Height of one frame in texture space (1.0 = the whole sheet). */
    public float frameHeight() {
        return 1.0F / frames;
    }

    /**
     * V coordinate of the top edge of a frame. Frames are stacked from the top
     * of the image downward, matching how artists export sprite sheets.
     */
    public float v0(int frame) {
        return (float) (frame % frames) / frames;
    }

    public float v1(int frame) {
        return v0(frame) + frameHeight();
    }
}
