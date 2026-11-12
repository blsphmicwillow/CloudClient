package net.cloudclient.cosmetic.cape;

import net.cloudclient.math.Vec3;

/**
 * Verlet cloth simulation for a cape: a {@code 8 x 4} segment ribbon
 * (9 x 5 vertices) pinned along its top edge to the player's back anchors.
 *
 * <p>Design notes that matter for stability:
 * <ul>
 *   <li>Fixed 1/120s substeps. Variable-step Verlet explodes at low frame rates
 *       because the constraint solver cannot keep up with large displacements.</li>
 *   <li>Velocity is clamped per substep, so a teleport or a lag spike cannot
 *       fling the cape into NaN.</li>
 *   <li>Non-finite points are repaired to the anchor line instead of being
 *       rendered; a broken cosmetic must never break a frame.</li>
 * </ul>
 *
 * <p>Coordinates are world-space blocks. The renderer consumes
 * {@link #positions()} directly as a {@code float[]}.
 */
public final class CapeSimulation {

    public static final int COLS = 8;
    public static final int ROWS = 4;
    public static final int POINT_COUNT = (COLS + 1) * (ROWS + 1);

    private static final float FIXED_DT = 1.0F / 120.0F;
    private static final int MAX_SUBSTEPS = 8;
    private static final float MAX_STEP_DISTANCE = 0.30F;
    private static final int SOLVER_ITERATIONS = 6;
    private static final float BODY_RADIUS = 0.24F;
    private static final float BODY_HEIGHT = 1.85F;

    private final Vec3[] current = new Vec3[POINT_COUNT];
    private final Vec3[] previous = new Vec3[POINT_COUNT];
    private final float[] positions = new float[POINT_COUNT * 3];
    private final float[] uvs = new float[POINT_COUNT * 2];

    private final float width;
    private final float length;
    private final float restW;
    private final float restH;

    private final CapeParams params = CapeParams.idle();

    private float accumulator;
    private float clock;
    private boolean initialised;

    // Reused scratch, so update() allocates nothing.
    private final Vec3 across = new Vec3();
    private final Vec3 down = new Vec3();
    private final Vec3 normal = new Vec3();
    private final Vec3 accel = new Vec3();
    private final Vec3 tmp = new Vec3();
    private final Vec3 anchorL = new Vec3();
    private final Vec3 anchorR = new Vec3();

    public CapeSimulation() {
        this(0.62F, 0.98F);
    }

    public CapeSimulation(float width, float length) {
        this.width = width;
        this.length = length;
        this.restW = width / COLS;
        this.restH = length / ROWS;
        for (int i = 0; i < POINT_COUNT; i++) {
            current[i] = new Vec3();
            previous[i] = new Vec3();
        }
        for (int row = 0; row <= ROWS; row++) {
            for (int col = 0; col <= COLS; col++) {
                int i = index(col, row);
                uvs[i * 2] = (float) col / COLS;
                uvs[i * 2 + 1] = (float) row / ROWS;
            }
        }
    }

    public static int index(int col, int row) {
        return row * (COLS + 1) + col;
    }

    public float width() {
        return width;
    }

    public float length() {
        return length;
    }

    public CapeParams params() {
        return params;
    }

    public boolean isInitialised() {
        return initialised;
    }

    /** Read-only view of the live vertex positions, {@code x,y,z} interleaved. */
    public float[] positions() {
        return positions;
    }

    /** Read-only UVs, stable for the life of the simulation. */
    public float[] uvs() {
        return uvs;
    }

    /** Places the cape at rest, hanging straight down from the anchors. */
    public void reset(Vec3 topLeft, Vec3 topRight) {
        for (int row = 0; row <= ROWS; row++) {
            for (int col = 0; col <= COLS; col++) {
                int i = index(col, row);
                float t = (float) col / COLS;
                Vec3 p = current[i];
                p.set(topLeft.x + (topRight.x - topLeft.x) * t,
                        topLeft.y + (topRight.y - topLeft.y) * t,
                        topLeft.z + (topRight.z - topLeft.z) * t);
                p.y -= restH * row;
                previous[i].set(p);
            }
        }
        accumulator = 0.0F;
        clock = 0.0F;
        initialised = true;
    }

    /**
     * Advances the simulation.
     *
     * @param dt           frame delta in seconds (clamped internally)
     * @param topLeft      world position of the left cape anchor
     * @param topRight     world position of the right cape anchor
     * @param wind         wind acceleration in blocks/s^2 (world space, replaces)
     * @param playerVel    player velocity in blocks per tick
     * @param playerPos    player feet position, used for body collision
     * @param target       parameter set for the current animation state
     */
    public void update(float dt, Vec3 topLeft, Vec3 topRight, Vec3 wind,
                       Vec3 playerVel, Vec3 playerPos, CapeParams target) {
        if (!initialised) reset(topLeft, topRight);
        if (dt <= 0.0F) return;

        anchorL.set(topLeft);
        anchorR.set(topRight);
        params.blendToward(target, dt);
        clock += dt;

        accumulator += Math.min(dt, 0.10F);
        int steps = 0;
        while (accumulator >= FIXED_DT && steps < MAX_SUBSTEPS) {
            substep(wind, playerVel, playerPos);
            accumulator -= FIXED_DT;
            steps++;
        }
        // Drop leftovers rather than accumulating debt across frames.
        if (accumulator > FIXED_DT * MAX_SUBSTEPS) accumulator = 0.0F;
        writePositions();
    }

    private void substep(Vec3 wind, Vec3 playerVel, Vec3 playerPos) {
        computePanelBasis();
        pinTopRow();

        float dt2 = FIXED_DT * FIXED_DT;
        for (int row = 1; row <= ROWS; row++) {
            for (int col = 0; col <= COLS; col++) {
                int i = index(col, row);
                Vec3 p = current[i];
                Vec3 prev = previous[i];

                accel.set(0.0F, params.gravity + params.lift, 0.0F);
                accel.addScaled(wind, params.windScale);

                // Sprint trail: push opposite to travel, scaled by speed.
                accel.addScaled(playerVel, -params.trailScale * 20.0F * 12.0F);

                // Row-phase flutter along the panel normal gives the cloth its
                // travelling wave instead of a uniform wobble.
                float phase = clock * params.flutterFreq * 6.2831855F + col * 0.55F + row * 0.30F;
                float flutter = (float) StrictMath.sin(phase) * params.flutterAmp * 9.0F;
                accel.addScaled(normal, flutter);

                tmp.set(
                        p.x + (p.x - prev.x) * params.drag + accel.x * dt2,
                        p.y + (p.y - prev.y) * params.drag + accel.y * dt2,
                        p.z + (p.z - prev.z) * params.drag + accel.z * dt2);

                Vec3 delta = tmp;
                float stepLen = delta.distanceTo(p);
                if (stepLen > MAX_STEP_DISTANCE) {
                    float s = MAX_STEP_DISTANCE / stepLen;
                    delta.set(p.x + (delta.x - p.x) * s,
                            p.y + (delta.y - p.y) * s,
                            p.z + (delta.z - p.z) * s);
                }

                if (!delta.isFinite()) {
                    delta.set(p);
                }

                prev.set(p);
                p.set(delta);
            }
        }

        for (int iteration = 0; iteration < SOLVER_ITERATIONS; iteration++) {
            solveConstraints();
            collideWithBody(playerPos);
            pinTopRow();
        }
    }

    private void solveConstraints() {
        for (int row = 0; row <= ROWS; row++) {
            for (int col = 0; col <= COLS; col++) {
                int i = index(col, row);
                if (col < COLS) constrain(i, index(col + 1, row), restW);
                if (row < ROWS) constrain(i, index(col, row + 1), restH);
                if (col < COLS - 1) constrain(i, index(col + 2, row), restW * 2.0F);
                if (row < ROWS - 1) constrain(i, index(col, row + 2), restH * 2.0F);
            }
        }
    }

    /** Symmetric distance constraint; pinned points act as infinitely heavy. */
    private void constrain(int ai, int bi, float rest) {
        Vec3 a = current[ai];
        Vec3 b = current[bi];
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float dz = b.z - a.z;
        float distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < 1.0E-10F) return;
        float dist = (float) StrictMath.sqrt(distSq);
        float diff = (dist - rest) / dist;
        float k = params.stiffness;

        boolean pinA = isPinned(ai);
        boolean pinB = isPinned(bi);
        if (pinA && pinB) return;
        if (pinA) {
            b.x -= dx * diff * k;
            b.y -= dy * diff * k;
            b.z -= dz * diff * k;
        } else if (pinB) {
            a.x += dx * diff * k;
            a.y += dy * diff * k;
            a.z += dz * diff * k;
        } else {
            float h = 0.5F * diff * k;
            a.x += dx * h;
            a.y += dy * h;
            a.z += dz * h;
            b.x -= dx * h;
            b.y -= dy * h;
            b.z -= dz * h;
        }
    }

    private boolean isPinned(int index) {
        return index / (COLS + 1) == 0;
    }

    private void pinTopRow() {
        for (int col = 0; col <= COLS; col++) {
            int i = index(col, 0);
            float t = (float) col / COLS;
            Vec3 p = current[i];
            p.set(anchorL.x + (anchorR.x - anchorL.x) * t,
                    anchorL.y + (anchorR.y - anchorL.y) * t,
                    anchorL.z + (anchorR.z - anchorL.z) * t);
            previous[i].set(p);
        }
    }

    /** Keeps cloth out of the player's torso cylinder. */
    private void collideWithBody(Vec3 playerPos) {
        for (int row = 1; row <= ROWS; row++) {
            for (int col = 0; col <= COLS; col++) {
                Vec3 p = current[index(col, row)];
                float ry = p.y - playerPos.y;
                if (ry < -0.2F || ry > BODY_HEIGHT) continue;
                float dx = p.x - playerPos.x;
                float dz = p.z - playerPos.z;
                float distSq = dx * dx + dz * dz;
                if (distSq >= BODY_RADIUS * BODY_RADIUS || distSq < 1.0E-8F) continue;
                float dist = (float) StrictMath.sqrt(distSq);
                float push = (BODY_RADIUS - dist) / dist;
                p.x += dx * push;
                p.z += dz * push;
            }
        }
    }

    /** Approximate panel normal from pinned edge and current hang direction. */
    private void computePanelBasis() {
        across.set(anchorR.x - anchorL.x, anchorR.y - anchorL.y, anchorR.z - anchorL.z);
        if (across.lengthSq() < 1.0E-8F) across.set(1.0F, 0.0F, 0.0F);
        across.normalise();

        int bottomLeft = index(0, ROWS);
        int topLeft = index(0, 0);
        down.set(current[bottomLeft].x - current[topLeft].x,
                current[bottomLeft].y - current[topLeft].y,
                current[bottomLeft].z - current[topLeft].z);
        if (down.lengthSq() < 1.0E-8F) down.set(0.0F, -1.0F, 0.0F);
        down.normalise();

        // normal = across x down
        normal.set(
                across.y * down.z - across.z * down.y,
                across.z * down.x - across.x * down.z,
                across.x * down.y - across.y * down.x);
        if (normal.lengthSq() < 1.0E-8F) normal.set(0.0F, 0.0F, 1.0F);
        normal.normalise();
    }

    private void writePositions() {
        for (int i = 0; i < POINT_COUNT; i++) {
            Vec3 p = current[i];
            positions[i * 3] = p.x;
            positions[i * 3 + 1] = p.y;
            positions[i * 3 + 2] = p.z;
        }
    }

    // ------------------------------------------------------------------ probes

    public Vec3 point(int col, int row, Vec3 out) {
        return out.set(current[index(col, row)]);
    }

    public void meanRow(int row, Vec3 out) {
        out.set(0.0F, 0.0F, 0.0F);
        for (int col = 0; col <= COLS; col++) {
            out.add(current[index(col, row)]);
        }
        out.scale(1.0F / (COLS + 1));
    }

    /** True when every vertex is a usable float. */
    public boolean isFinite() {
        for (Vec3 p : current) {
            if (!p.isFinite()) return false;
        }
        return true;
    }
}
