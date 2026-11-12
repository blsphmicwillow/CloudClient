package net.cloudclient.render;

import net.cloudclient.CloudClientRuntime;
import net.cloudclient.cosmetic.CosmeticTuning;
import net.cloudclient.cosmetic.CosmeticType;
import net.cloudclient.cosmetic.CosmeticWorld;
import net.cloudclient.cosmetic.asset.AnimatedTexture;
import net.cloudclient.cosmetic.cape.CapeSimulation;
import net.cloudclient.cosmetic.halo.Halo;
import net.cloudclient.cosmetic.horns.HornGeometry;
import net.cloudclient.cosmetic.pet.Pet;
import net.cloudclient.cosmetic.wings.WingRig;
import net.cloudclient.math.Mat4;
import net.cloudclient.math.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Draws cosmetics for the local player.
 *
 * <p>Rendering happens in {@code RenderWorldLastEvent}, not inside the entity
 * renderer. That hook gives a known GL state and camera position, so cosmetics
 * never fight the player model's matrix stack, and depth/z-fighting behaviour is
 * predictable.
 *
 * <p>Batching: each cosmetic category issues one Tessellator draw for all of its
 * geometry. A cape is 32 quads in a single call; wings are 8; the halo is 24
 * triangles. Nothing here binds a texture more than once per player per frame.
 *
 * <p>1.8.9 note: {@code WorldRenderer}/{@code Tessellator} method names differ
 * between MCP mappings snapshots. If your snapshot disagrees (for example
 * {@code startDrawing} vs {@code begin}), only this file needs adjusting.
 */
public final class CosmeticRenderer {

    private static final ResourceLocation CAPE_TEXTURE =
            new ResourceLocation("cloudclient", "textures/cosmetics/cape_cloud.png");
    private static final ResourceLocation WING_TEXTURE =
            new ResourceLocation("cloudclient", "textures/cosmetics/wings_ember.png");
    private static final ResourceLocation HALO_TEXTURE =
            new ResourceLocation("cloudclient", "textures/cosmetics/halo_gold.png");
    private static final ResourceLocation HORN_TEXTURE =
            new ResourceLocation("cloudclient", "textures/cosmetics/horns_obsidian.png");
    private static final ResourceLocation PET_TEXTURE =
            new ResourceLocation("cloudclient", "textures/cosmetics/pet_spark.png");

    private final AnimatedTexture capeFrames = new AnimatedTexture(4, 10.0F);
    private final Halo.Mesh haloMesh = Halo.buildRing(0.10F, 0.16F, 24);
    private final Mat4 headMatrix = new Mat4();
    private final Mat4 hornMatrix = new Mat4();
    private final Mat4 mountMatrix = new Mat4();
    private final net.cloudclient.cosmetic.AnchorSolver anchorSolver =
            new net.cloudclient.cosmetic.AnchorSolver();
    private final Vec3 vertex = new Vec3();

    /** World-space origin offset applied to every cosmetic this frame. */
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private float animationSeconds;

    public void renderWorld(CloudClientRuntime runtime, Minecraft mc, float partialTicks) {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;
        if (!runtime.isEnabled("cosmetic.core")) return;

        CosmeticWorld world = runtime.cosmetics();
        CosmeticWorld.PlayerCosmetics state = world.get(player.getEntityId(), world.tick());
        if (!state.initialised) return;

        this.animationSeconds = player.ticksExisted / 20.0F;

        // Smooth the 20 Hz simulation across frames: the cloth updates on tick,
        // but the whole cosmetic is translated by the sub-tick delta.
        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;

        double smoothX = state.previousPosition.x
                + (state.position.x - state.previousPosition.x) * partialTicks;
        double smoothY = state.previousPosition.y
                + (state.position.y - state.previousPosition.y) * partialTicks;
        double smoothZ = state.previousPosition.z
                + (state.position.z - state.previousPosition.z) * partialTicks;
        offsetX = smoothX - state.position.x - camX;
        offsetY = smoothY - state.position.y - camY;
        offsetZ = smoothZ - state.position.z - camZ;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        // Local player only: cosmetics are never replicated (see FAIRPLAY.md).
        if (runtime.profile().isEquipped(CosmeticType.CAPE)
                && runtime.isEnabled("cosmetic.cape")) {
            drawCape(mc, state.cape);
        }
        if (runtime.profile().isEquipped(CosmeticType.WINGS)
                && runtime.isEnabled("cosmetic.wings")) {
            drawWings(mc, state);
        }
        if (runtime.profile().isEquipped(CosmeticType.HALO)
                && runtime.isEnabled("cosmetic.halo")) {
            drawHalo(mc, state, runtime);
        }
        if (runtime.profile().isEquipped(CosmeticType.HORNS)
                && runtime.isEnabled("cosmetic.horns")) {
            drawHorns(mc, state);
        }
        if (runtime.profile().isEquipped(CosmeticType.PET)
                && runtime.isEnabled("cosmetic.pets")) {
            drawPets(mc, runtime);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ cape

    private void drawCape(Minecraft mc, CapeSimulation cape) {
        mc.getTextureManager().bindTexture(CAPE_TEXTURE);
        float[] positions = cape.positions();
        float[] uvs = cape.uvs();
        int frame = capeFrames.frameAt(animationSeconds);
        float baseV = capeFrames.v0(frame);
        float frameHeight = capeFrames.frameHeight();

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        for (int row = 0; row < CapeSimulation.ROWS; row++) {
            for (int col = 0; col < CapeSimulation.COLS; col++) {
                emitVertex(renderer, positions, uvs, col, row, baseV, frameHeight);
                emitVertex(renderer, positions, uvs, col, row + 1, baseV, frameHeight);
                emitVertex(renderer, positions, uvs, col + 1, row + 1, baseV, frameHeight);
                emitVertex(renderer, positions, uvs, col + 1, row, baseV, frameHeight);
            }
        }
        tessellator.draw();
    }

    private void emitVertex(WorldRenderer renderer, float[] positions, float[] uvs,
                            int col, int row, float baseV, float frameHeight) {
        int point = CapeSimulation.index(col, row);
        double x = positions[point * 3] + offsetX;
        double y = positions[point * 3 + 1] + offsetY;
        double z = positions[point * 3 + 2] + offsetZ;
        float u = uvs[point * 2];
        float v = baseV + uvs[point * 2 + 1] * frameHeight;
        renderer.pos(x, y, z).tex(u, v).endVertex();
    }

    // ----------------------------------------------------------------- wings

    private void drawWings(Minecraft mc, CosmeticWorld.PlayerCosmetics state) {
        mc.getTextureManager().bindTexture(WING_TEXTURE);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        emitWing(renderer, state, false);
        emitWing(renderer, state, true);

        tessellator.draw();
    }

    private void emitWing(WorldRenderer renderer, CosmeticWorld.PlayerCosmetics state,
                          boolean mirrored) {
        WingRig rig = state.wings;
        for (int bone = 0; bone < WingRig.POINTS - 1; bone++) {
            bonePoint(rig, state, bone, mirrored, vertex);
            double ax = vertex.x;
            double ay = vertex.y;
            double az = vertex.z;
            bonePoint(rig, state, bone + 1, mirrored, vertex);
            double bx = vertex.x;
            double by = vertex.y;
            double bz = vertex.z;

            float widthA = 0.17F * (1.0F - bone * 0.22F);
            float widthB = 0.17F * (1.0F - (bone + 1) * 0.22F);
            float vA = (float) bone / (WingRig.POINTS - 1);
            float vB = (float) (bone + 1) / (WingRig.POINTS - 1);

            renderer.pos(ax + offsetX, ay + widthA + offsetY, az + offsetZ).tex(0.0F, vA).endVertex();
            renderer.pos(ax + offsetX, ay - widthA + offsetY, az + offsetZ).tex(0.0F, vA).endVertex();
            renderer.pos(bx + offsetX, by - widthB + offsetY, bz + offsetZ).tex(1.0F, vB).endVertex();
            renderer.pos(bx + offsetX, by + widthB + offsetY, bz + offsetZ).tex(1.0F, vB).endVertex();
        }
    }

    private void bonePoint(WingRig rig, CosmeticWorld.PlayerCosmetics state, int index,
                           boolean mirrored, Vec3 out) {
        if (mirrored) {
            rig.mirroredPoint(index, state.backAnchor, state.bodyYaw, out);
        } else {
            rig.point(index, out);
        }
    }

    // ------------------------------------------------------------------ halo

    private void drawHalo(Minecraft mc, CosmeticWorld.PlayerCosmetics state,
                          CloudClientRuntime runtime) {
        mc.getTextureManager().bindTexture(HALO_TEXTURE);

        // Head space, then lift to the crown and apply hover + spin.
        buildHeadMatrix(state, headMatrix);
        headMatrix.translate(0.0F, 1.78F + state.halo.hoverOffset(), 0.0F);
        headMatrix.rotateY(state.halo.angleDegrees());

        float brightness = 0.55F + state.halo.brightness() * 0.45F;
        CosmeticTuning tuning = runtime.tuning();
        GlStateManager.disableCull();
        // Tint: the texture is greyscale-ish, so multiplying by the configured
        // colour is enough to recolour the halo without a second texture.
        GlStateManager.color(brightness * tuning.haloRed(),
                brightness * tuning.haloGreen(), brightness * tuning.haloBlue(), 1.0F);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);

        float[] positions = haloMesh.positions;
        int[] indices = haloMesh.indices;
        for (int i = 0; i < indices.length; i++) {
            int base = indices[i] * 3;
            headMatrix.transformPoint(positions[base], positions[base + 1], positions[base + 2],
                    vertex);
            float u = (i % 3 == 0) ? 0.0F : (i % 3 == 1 ? 0.5F : 1.0F);
            renderer.pos(vertex.x + offsetX, vertex.y + offsetY, vertex.z + offsetZ)
                    .tex(u, 0.5F).endVertex();
        }
        tessellator.draw();

        GlStateManager.enableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ----------------------------------------------------------------- horns

    private void drawHorns(Minecraft mc, CosmeticWorld.PlayerCosmetics state) {
        mc.getTextureManager().bindTexture(HORN_TEXTURE);
        buildHeadMatrix(state, headMatrix);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);

        emitHorn(renderer, state, true);
        emitHorn(renderer, state, false);

        tessellator.draw();
    }

    private void emitHorn(WorldRenderer renderer, CosmeticWorld.PlayerCosmetics state,
                          boolean left) {
        // Never accumulate into headMatrix: both horns share it as the parent.
        state.horns.mountMatrix(left, mountMatrix);
        hornMatrix.copyFrom(headMatrix).multiply(mountMatrix);

        HornGeometry.Mesh mesh = state.horns.mesh(left);
        int[] indices = mesh.indices;
        for (int i = 0; i < indices.length; i++) {
            int base = indices[i] * 3;
            vertex.set(mesh.positions[base], mesh.positions[base + 1], mesh.positions[base + 2]);
            hornMatrix.transformPoint(vertex.x, vertex.y, vertex.z, vertex);
            float u = (i % 3 == 0) ? 0.0F : (i % 3 == 1 ? 0.5F : 1.0F);
            renderer.pos(vertex.x + offsetX, vertex.y + offsetY, vertex.z + offsetZ)
                    .tex(u, 0.5F).endVertex();
        }
    }

    // ------------------------------------------------------------------ pets

    private void drawPets(Minecraft mc, CloudClientRuntime runtime) {
        java.util.List<Pet> pets = runtime.cosmetics().petsSnapshot();
        if (pets.isEmpty()) return;
        mc.getTextureManager().bindTexture(PET_TEXTURE);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        for (Pet pet : pets) {
            float size = pet.type().restingSize() * pet.scale()
                    * runtime.tuning().petScale;
            double px = pet.position().x - mc.getRenderManager().viewerPosX;
            double py = pet.position().y - mc.getRenderManager().viewerPosY
                    + 0.35D + runtime.cosmetics().pets().bob(pet);
            double pz = pet.position().z - mc.getRenderManager().viewerPosZ;
            GlStateManager.color(pet.tintR(), pet.tintG(), pet.tintB(), 1.0F);
            renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            emitCube(renderer, px, py, pz, size);
            tessellator.draw();
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Axis-aligned cube, six quads, UVs stretched over the pet texture. */
    private void emitCube(WorldRenderer renderer, double x, double y, double z, float size) {
        double s = size;
        // +X, -X
        quad(renderer, x + s, y - s, z - s, x + s, y + s, z - s, x + s, y + s, z + s, x + s, y - s, z + s);
        quad(renderer, x - s, y - s, z + s, x - s, y + s, z + s, x - s, y + s, z - s, x - s, y - s, z - s);
        // +Z, -Z
        quad(renderer, x - s, y - s, z + s, x - s, y + s, z + s, x + s, y + s, z + s, x + s, y - s, z + s);
        quad(renderer, x + s, y - s, z - s, x + s, y + s, z - s, x - s, y + s, z - s, x - s, y - s, z - s);
        // +Y, -Y
        quad(renderer, x - s, y + s, z - s, x - s, y + s, z + s, x + s, y + s, z + s, x + s, y + s, z - s);
        quad(renderer, x - s, y - s, z + s, x - s, y - s, z - s, x + s, y - s, z - s, x + s, y - s, z + s);
    }

    private void quad(WorldRenderer renderer,
                      double x1, double y1, double z1, double x2, double y2, double z2,
                      double x3, double y3, double z3, double x4, double y4, double z4) {
        renderer.pos(x1, y1, z1).tex(0.0F, 0.0F).endVertex();
        renderer.pos(x2, y2, z2).tex(0.0F, 1.0F).endVertex();
        renderer.pos(x3, y3, z3).tex(1.0F, 1.0F).endVertex();
        renderer.pos(x4, y4, z4).tex(1.0F, 0.0F).endVertex();
    }

    // --------------------------------------------------------------- helpers

    private void buildHeadMatrix(CosmeticWorld.PlayerCosmetics state, Mat4 out) {
        anchorSolver.matrix(state.position, state.bodyYaw, state.headYaw,
                state.headPitch, true, out);
    }
}
