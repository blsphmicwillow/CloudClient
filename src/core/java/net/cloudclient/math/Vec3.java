package net.cloudclient.math;

/** Mutable 3-component vector. Reused everywhere to keep the sim GC-free. */
public final class Vec3 {

    public float x;
    public float y;
    public float z;

    public Vec3() {
    }

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vec3 other) {
        set(other);
    }

    public Vec3 set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vec3 set(Vec3 other) {
        return set(other.x, other.y, other.z);
    }

    public Vec3 add(float dx, float dy, float dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
        return this;
    }

    public Vec3 add(Vec3 other) {
        return add(other.x, other.y, other.z);
    }

    public Vec3 addScaled(Vec3 other, float scale) {
        return add(other.x * scale, other.y * scale, other.z * scale);
    }

    public Vec3 sub(Vec3 other) {
        return add(-other.x, -other.y, -other.z);
    }

    public Vec3 scale(float s) {
        this.x *= s;
        this.y *= s;
        this.z *= s;
        return this;
    }

    public float lengthSq() {
        return x * x + y * y + z * z;
    }

    public float length() {
        return (float) StrictMath.sqrt(lengthSq());
    }

    /** Horizontal (XZ) length, which is what cape drag and pet flocking use. */
    public float horizontalLength() {
        return (float) StrictMath.sqrt(x * x + z * z);
    }

    public Vec3 normalise() {
        float len = length();
        if (len > 1.0E-6F) {
            scale(1.0F / len);
        }
        return this;
    }

    public Vec3 lerp(Vec3 target, float t) {
        this.x += (target.x - this.x) * t;
        this.y += (target.y - this.y) * t;
        this.z += (target.z - this.z) * t;
        return this;
    }

    public float distanceTo(Vec3 other) {
        float dx = x - other.x;
        float dy = y - other.y;
        float dz = z - other.z;
        return (float) StrictMath.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public float distanceSqTo(Vec3 other) {
        float dx = x - other.x;
        float dy = y - other.y;
        float dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean isFinite() {
        return !(Float.isNaN(x) || Float.isInfinite(x)
                || Float.isNaN(y) || Float.isInfinite(y)
                || Float.isNaN(z) || Float.isInfinite(z));
    }

    public Vec3 copy() {
        return new Vec3(this);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
