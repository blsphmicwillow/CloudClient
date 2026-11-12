package net.cloudclient.math;

/**
 * Row-major 4x4 matrix, {@code index = row * 4 + column}, column-vector
 * convention ({@code v' = M * v}).
 *
 * <p>Deliberately not {@code com.mojang.math.Matrix4f} / LWJGL: the rig maths
 * must be unit-testable without an OpenGL context, and it must not allocate a
 * direct buffer per bone per frame.
 */
public final class Mat4 {

    public final float[] m = new float[16];

    public Mat4() {
        identity();
    }

    public Mat4 identity() {
        for (int i = 0; i < 16; i++) m[i] = 0.0F;
        m[0] = 1.0F;
        m[5] = 1.0F;
        m[10] = 1.0F;
        m[15] = 1.0F;
        return this;
    }

    public Mat4 copyFrom(Mat4 other) {
        System.arraycopy(other.m, 0, m, 0, 16);
        return this;
    }

    public Mat4 copy() {
        Mat4 out = new Mat4();
        out.copyFrom(this);
        return out;
    }

    public Mat4 translate(float tx, float ty, float tz) {
        m[3] += m[0] * tx + m[1] * ty + m[2] * tz;
        m[7] += m[4] * tx + m[5] * ty + m[6] * tz;
        m[11] += m[8] * tx + m[9] * ty + m[10] * tz;
        m[15] += m[12] * tx + m[13] * ty + m[14] * tz;
        return this;
    }

    public Mat4 scale(float sx, float sy, float sz) {
        for (int c = 0; c < 4; c++) {
            m[c] *= sx;
            m[4 + c] *= sy;
            m[8 + c] *= sz;
        }
        return this;
    }

    /** Right-handed rotation about X, angles in degrees. */
    public Mat4 rotateX(float degrees) {
        float rad = (float) Math.toRadians(degrees);
        float c = (float) StrictMath.cos(rad);
        float s = (float) StrictMath.sin(rad);
        float m1 = m[1], m2 = m[2];
        float m5 = m[5], m6 = m[6];
        float m9 = m[9], m10 = m[10];
        float m13 = m[13], m14 = m[14];
        m[1] = m1 * c + m2 * s;
        m[2] = -m1 * s + m2 * c;
        m[5] = m5 * c + m6 * s;
        m[6] = -m5 * s + m6 * c;
        m[9] = m9 * c + m10 * s;
        m[10] = -m9 * s + m10 * c;
        m[13] = m13 * c + m14 * s;
        m[14] = -m13 * s + m14 * c;
        return this;
    }

    /** Right-handed rotation about Y, angles in degrees. */
    public Mat4 rotateY(float degrees) {
        float rad = (float) Math.toRadians(degrees);
        float c = (float) StrictMath.cos(rad);
        float s = (float) StrictMath.sin(rad);
        float m0 = m[0], m2 = m[2];
        float m4 = m[4], m6 = m[6];
        float m8 = m[8], m10 = m[10];
        float m12 = m[12], m14 = m[14];
        m[0] = m0 * c - m2 * s;
        m[2] = m0 * s + m2 * c;
        m[4] = m4 * c - m6 * s;
        m[6] = m4 * s + m6 * c;
        m[8] = m8 * c - m10 * s;
        m[10] = m8 * s + m10 * c;
        m[12] = m12 * c - m14 * s;
        m[14] = m12 * s + m14 * c;
        return this;
    }

    /** Right-handed rotation about Z, angles in degrees. */
    public Mat4 rotateZ(float degrees) {
        float rad = (float) Math.toRadians(degrees);
        float c = (float) StrictMath.cos(rad);
        float s = (float) StrictMath.sin(rad);
        float m0 = m[0], m1 = m[1];
        float m4 = m[4], m5 = m[5];
        float m8 = m[8], m9 = m[9];
        float m12 = m[12], m13 = m[13];
        m[0] = m0 * c + m1 * s;
        m[1] = -m0 * s + m1 * c;
        m[4] = m4 * c + m5 * s;
        m[5] = -m4 * s + m5 * c;
        m[8] = m8 * c + m9 * s;
        m[9] = -m8 * s + m9 * c;
        m[12] = m12 * c + m13 * s;
        m[13] = -m12 * s + m13 * c;
        return this;
    }

    /** this = this * other (i.e. apply {@code other} first, then {@code this}). */
    public Mat4 multiply(Mat4 other) {
        float[] a = m;
        float[] b = other.m;
        float[] out = new float[16];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                float sum = 0.0F;
                for (int k = 0; k < 4; k++) {
                    sum += a[r * 4 + k] * b[k * 4 + c];
                }
                out[r * 4 + c] = sum;
            }
        }
        System.arraycopy(out, 0, m, 0, 16);
        return this;
    }

    public void transformPoint(float x, float y, float z, Vec3 out) {
        out.set(
                m[0] * x + m[1] * y + m[2] * z + m[3],
                m[4] * x + m[5] * y + m[6] * z + m[7],
                m[8] * x + m[9] * y + m[10] * z + m[11]);
    }

    /** Ignores translation; use for normals and bone directions. */
    public void transformDirection(float x, float y, float z, Vec3 out) {
        out.set(
                m[0] * x + m[1] * y + m[2] * z,
                m[4] * x + m[5] * y + m[6] * z,
                m[8] * x + m[9] * y + m[10] * z);
    }

    /** Builds a yaw/pitch/roll transform in Minecraft's convention (Z up = -Y). */
    public static Mat4 fromEuler(float yawDeg, float pitchDeg, float rollDeg) {
        return new Mat4().rotateY(-yawDeg).rotateX(pitchDeg).rotateZ(rollDeg);
    }
}
