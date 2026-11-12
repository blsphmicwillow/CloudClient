package net.cloudclient.cosmetic.horns;

import net.cloudclient.math.FastMath;

/**
 * Procedural horn mesh: a tapered tube swept along a curved path.
 *
 * <p>Rigid horns use {@code curvature = 0} (a straight bone-like spike); soft
 * horns bend along an arc. Generating the mesh at load time means horns cost one
 * vertex buffer each, not a per-frame tessellation.
 */
public final class HornGeometry {

    public static final class Mesh {
        public final float[] positions;
        public final float[] normals;
        public final int[] indices;

        Mesh(float[] positions, float[] normals, int[] indices) {
            this.positions = positions;
            this.normals = normals;
            this.indices = indices;
        }

        public int vertexCount() {
            return positions.length / 3;
        }

        public int triangleCount() {
            return indices.length / 3;
        }
    }

    private HornGeometry() {
    }

    /**
     * @param segments      rings along the horn (>= 2)
     * @param sides         vertices per ring (>= 3)
     * @param baseRadius    radius at the skull, blocks
     * @param length        path length along the horn, blocks
     * @param curvatureDeg  total bend over the whole horn; 0 = straight
     * @param tipRadius     radius at the tip, blocks (may be small but > 0)
     */
    public static Mesh build(int segments, int sides, float baseRadius, float length,
                             float curvatureDeg, float tipRadius) {
        int rings = Math.max(2, segments) + 1;
        int n = Math.max(3, sides);

        float[] positions = new float[rings * n * 3];
        float[] normals = new float[rings * n * 3];
        int[] indices = new int[(rings - 1) * n * 6];

        float step = length / (rings - 1);
        float bendPerStep = (float) Math.toRadians(curvatureDeg) / (rings - 1);

        // Sweep direction starts pointing up and bends about +X.
        double dirX = 0.0D;
        double dirY = 1.0D;
        double dirZ = 0.0D;
        double cx = 0.0D;
        double cy = 0.0D;
        double cz = 0.0D;

        for (int r = 0; r < rings; r++) {
            float t = (float) r / (rings - 1);
            // Smooth taper; the exponent shapes a horn rather than a cone.
            float radius = FastMath.lerp(baseRadius, tipRadius, (float) Math.pow(t, 0.85D));

            // Ring basis perpendicular to the sweep: X stays lateral, and the
            // second axis is rotated with the bend so rings never shear.
            double zpx = 0.0D;
            double zpy = -dirZ;
            double zpz = dirY;

            for (int s = 0; s < n; s++) {
                float a = (float) (Math.PI * 2.0D * s / n);
                double cos = Math.cos(a);
                double sin = Math.sin(a);

                double nx = cos;
                double ny = sin * zpy;
                double nz = sin * zpz;

                int o = (r * n + s) * 3;
                positions[o] = (float) (cx + nx * radius);
                positions[o + 1] = (float) (cy + ny * radius);
                positions[o + 2] = (float) (cz + nz * radius);
                normals[o] = (float) nx;
                normals[o + 1] = (float) ny;
                normals[o + 2] = (float) nz;
            }

            // Advance along the bend.
            double cosB = Math.cos(bendPerStep);
            double sinB = Math.sin(bendPerStep);
            double newY = dirY * cosB - dirZ * sinB;
            double newZ = dirY * sinB + dirZ * cosB;
            dirY = newY;
            dirZ = newZ;
            // Renormalise to absorb drift over long horns.
            double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (len > 1.0E-9D) {
                dirX /= len;
                dirY /= len;
                dirZ /= len;
            }
            cx += dirX * step;
            cy += dirY * step;
            cz += dirZ * step;
        }

        int w = 0;
        for (int r = 0; r < rings - 1; r++) {
            for (int s = 0; s < n; s++) {
                int next = (s + 1) % n;
                int a = r * n + s;
                int b = r * n + next;
                int c = (r + 1) * n + next;
                int d = (r + 1) * n + s;
                indices[w++] = a;
                indices[w++] = b;
                indices[w++] = c;
                indices[w++] = a;
                indices[w++] = c;
                indices[w++] = d;
            }
        }
        return new Mesh(positions, normals, indices);
    }
}
