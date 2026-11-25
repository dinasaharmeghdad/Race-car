package com.example.engine3d

import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }
    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )
}

fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun lerpVec3(a: Vec3, b: Vec3, t: Float): Vec3 = Vec3(
    lerpFloat(a.x, b.x, t),
    lerpFloat(a.y, b.y, t),
    lerpFloat(a.z, b.z, t)
)

fun catmullRomClosed(points: List<Vec3>, tNormalized: Float): Vec3 {
    val n = points.size
    val scaled = ((tNormalized % 1f) + 1f) % 1f * n
    val i1 = scaled.toInt() % n
    val u = scaled - scaled.toInt()
    val p0 = points[(i1 - 1 + n) % n]
    val p1 = points[i1]
    val p2 = points[(i1 + 1) % n]
    val p3 = points[(i1 + 2) % n]

    val u2 = u * u
    val u3 = u2 * u

    val x = 0.5f * (
        (2f * p1.x) +
        (-p0.x + p2.x) * u +
        (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * u2 +
        (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * u3
    )
    val y = 0.5f * (
        (2f * p1.y) +
        (-p0.y + p2.y) * u +
        (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * u2 +
        (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * u3
    )
    val z = 0.5f * (
        (2f * p1.z) +
        (-p0.z + p2.z) * u +
        (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z) * u2 +
        (-p0.z + 3f * p1.z - 3f * p2.z + p3.z) * u3
    )
    return Vec3(x, y, z)
}
