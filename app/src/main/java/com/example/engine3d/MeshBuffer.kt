package com.example.engine3d

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interleaved GPU mesh buffer:
 * [x, y, z, nx, ny, nz, r, g, b, emissive] -> 10 floats (40 bytes) per vertex.
 */
class MeshGpuBuffer(vertices: FloatArray) {
    val vertexCount: Int = vertices.size / FLOATS_PER_VERTEX
    private var vboId: Int = 0

    init {
        if (vertexCount > 0) {
            val buffers = IntArray(1)
            GLES20.glGenBuffers(1, buffers, 0)
            vboId = buffers[0]
            val fb: FloatBuffer = ByteBuffer
                .allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertices)
            fb.position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                vertices.size * 4,
                fb,
                GLES20.GL_STATIC_DRAW
            )
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        }
    }

    fun draw(
        posLoc: Int,
        normLoc: Int,
        colorLoc: Int,
        emissiveLoc: Int,
        mode: Int = GLES20.GL_TRIANGLES
    ) {
        if (vertexCount == 0 || vboId == 0) return
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)

        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)

        if (normLoc >= 0) {
            GLES20.glEnableVertexAttribArray(normLoc)
            GLES20.glVertexAttribPointer(normLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 12)
        }

        if (colorLoc >= 0) {
            GLES20.glEnableVertexAttribArray(colorLoc)
            GLES20.glVertexAttribPointer(colorLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 24)
        }

        if (emissiveLoc >= 0) {
            GLES20.glEnableVertexAttribArray(emissiveLoc)
            GLES20.glVertexAttribPointer(emissiveLoc, 1, GLES20.GL_FLOAT, false, STRIDE_BYTES, 36)
        }

        GLES20.glDrawArrays(mode, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(posLoc)
        if (normLoc >= 0) GLES20.glDisableVertexAttribArray(normLoc)
        if (colorLoc >= 0) GLES20.glDisableVertexAttribArray(colorLoc)
        if (emissiveLoc >= 0) GLES20.glDisableVertexAttribArray(emissiveLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun release() {
        if (vboId != 0) {
            GLES20.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
    }

    companion object {
        const val FLOATS_PER_VERTEX = 10
        const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4
    }
}

class MeshDataBuilder(initialCapacityFloats: Int = 8192) {
    private var data = FloatArray(initialCapacityFloats)
    private var count = 0

    private fun ensureCapacity(extra: Int) {
        if (count + extra > data.size) {
            data = data.copyOf(maxOf(data.size * 2, count + extra))
        }
    }

    fun addVertex(
        x: Float, y: Float, z: Float,
        nx: Float, ny: Float, nz: Float,
        r: Float, g: Float, b: Float,
        emissive: Float = 0f
    ) {
        ensureCapacity(10)
        data[count++] = x
        data[count++] = y
        data[count++] = z
        data[count++] = nx
        data[count++] = ny
        data[count++] = nz
        data[count++] = r
        data[count++] = g
        data[count++] = b
        data[count++] = emissive
    }

    fun addTriangle(
        p0: Vec3, p1: Vec3, p2: Vec3,
        rgb: FloatArray,
        emissive: Float = 0f,
        n0: Vec3? = null, n1: Vec3? = null, n2: Vec3? = null
    ) {
        val faceN = if (n0 == null || n1 == null || n2 == null) {
            (p1 - p0).cross(p2 - p0).normalized()
        } else Vec3(0f, 1f, 0f)
        val vn0 = n0 ?: faceN
        val vn1 = n1 ?: faceN
        val vn2 = n2 ?: faceN
        addVertex(p0.x, p0.y, p0.z, vn0.x, vn0.y, vn0.z, rgb[0], rgb[1], rgb[2], emissive)
        addVertex(p1.x, p1.y, p1.z, vn1.x, vn1.y, vn1.z, rgb[0], rgb[1], rgb[2], emissive)
        addVertex(p2.x, p2.y, p2.z, vn2.x, vn2.y, vn2.z, rgb[0], rgb[1], rgb[2], emissive)
    }

    fun addQuad(
        p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3,
        rgb: FloatArray,
        emissive: Float = 0f,
        smoothNormal: Vec3? = null
    ) {
        addTriangle(p0, p1, p2, rgb, emissive, smoothNormal, smoothNormal, smoothNormal)
        addTriangle(p0, p2, p3, rgb, emissive, smoothNormal, smoothNormal, smoothNormal)
    }

    fun addBox(
        cx: Float, cy: Float, cz: Float,
        halfW: Float, halfH: Float, halfL: Float,
        rgb: FloatArray,
        emissive: Float = 0f
    ) {
        val x0 = cx - halfW; val x1 = cx + halfW
        val y0 = cy - halfH; val y1 = cy + halfH
        val z0 = cz - halfL; val z1 = cz + halfL

        val p000 = Vec3(x0, y0, z0); val p100 = Vec3(x1, y0, z0)
        val p110 = Vec3(x1, y1, z0); val p010 = Vec3(x0, y1, z0)
        val p001 = Vec3(x0, y0, z1); val p101 = Vec3(x1, y0, z1)
        val p111 = Vec3(x1, y1, z1); val p011 = Vec3(x0, y1, z1)

        // Front (+Z)
        addQuad(p001, p101, p111, p011, rgb, emissive)
        // Back (-Z)
        addQuad(p100, p000, p010, p110, rgb, emissive)
        // Top (+Y)
        addQuad(p011, p111, p110, p010, rgb, emissive)
        // Bottom (-Y)
        addQuad(p000, p100, p101, p001, rgb, emissive)
        // Right (+X)
        addQuad(p101, p100, p110, p111, rgb, emissive)
        // Left (-X)
        addQuad(p000, p001, p011, p010, rgb, emissive)
    }

    /**
     * Adds a cylinder aligned along the X axis (ideal for wheels and axles).
     */
    fun addCylinderX(
        cx: Float, cy: Float, cz: Float,
        radius: Float,
        width: Float,
        segments: Int,
        rgb: FloatArray,
        emissive: Float = 0f
    ) {
        val halfW = width * 0.5f
        val xL = cx - halfW
        val xR = cx + halfW
        val cL = Vec3(xL, cy, cz)
        val cR = Vec3(xR, cy, cz)

        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * (2f * PI.toFloat())
            val a1 = ((i + 1).toFloat() / segments) * (2f * PI.toFloat())
            val ny0 = cos(a0); val nz0 = sin(a0)
            val ny1 = cos(a1); val nz1 = sin(a1)
            val y0 = cy + ny0 * radius; val z0 = cz + nz0 * radius
            val y1 = cy + ny1 * radius; val z1 = cz + nz1 * radius

            val pL0 = Vec3(xL, y0, z0); val pL1 = Vec3(xL, y1, z1)
            val pR0 = Vec3(xR, y0, z0); val pR1 = Vec3(xR, y1, z1)
            val n0 = Vec3(0f, ny0, nz0)
            val n1 = Vec3(0f, ny1, nz1)

            // Tread wall
            addTriangle(pL0, pR0, pR1, rgb, emissive, n0, n0, n1)
            addTriangle(pL0, pR1, pL1, rgb, emissive, n0, n1, n1)

            // Side caps
            addTriangle(cL, pL1, pL0, rgb, emissive, Vec3(-1f, 0f, 0f), Vec3(-1f, 0f, 0f), Vec3(-1f, 0f, 0f))
            addTriangle(cR, pR0, pR1, rgb, emissive, Vec3(1f, 0f, 0f), Vec3(1f, 0f, 0f), Vec3(1f, 0f, 0f))
        }
    }

    /**
     * Adds a vertical cylinder or cone aligned along Y axis (trees, pillars, cones, flames).
     */
    fun addConeOrCylinderY(
        cx: Float, yBottom: Float, cz: Float,
        radiusBottom: Float,
        radiusTop: Float,
        height: Float,
        segments: Int,
        rgb: FloatArray,
        emissive: Float = 0f
    ) {
        val yTop = yBottom + height
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * (2f * PI.toFloat())
            val a1 = ((i + 1).toFloat() / segments) * (2f * PI.toFloat())
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)

            val b0 = Vec3(cx + c0 * radiusBottom, yBottom, cz + s0 * radiusBottom)
            val b1 = Vec3(cx + c1 * radiusBottom, yBottom, cz + s1 * radiusBottom)
            val t0 = Vec3(cx + c0 * radiusTop, yTop, cz + s0 * radiusTop)
            val t1 = Vec3(cx + c1 * radiusTop, yTop, cz + s1 * radiusTop)

            if (radiusTop > 0.001f) {
                addQuad(b0, b1, t1, t0, rgb, emissive)
            } else {
                addTriangle(b0, b1, Vec3(cx, yTop, cz), rgb, emissive)
            }
        }
    }

    fun toFloatArray(): FloatArray = data.copyOf(count)
    fun buildGpuBuffer(): MeshGpuBuffer = MeshGpuBuffer(toFloatArray())
}
