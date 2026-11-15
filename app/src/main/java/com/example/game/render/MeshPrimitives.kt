package com.example.game.render

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPU VBO-backed 3D primitive mesh container (position xyz + normal xyz = 6 floats per vertex).
 */
class VboMesh(vertices: FloatArray) {
    val vertexCount: Int = vertices.size / 6
    private val vboId: Int

    init {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vboId = ids[0]

        val buf = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, buf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun draw(aPosLoc: Int, aNormalLoc: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glEnableVertexAttribArray(aNormalLoc)
        GLES20.glVertexAttribPointer(aNormalLoc, 3, GLES20.GL_FLOAT, false, 24, 12)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}

class MeshLibrary {
    val cube = VboMesh(buildUnitCube())
    val cylinder = VboMesh(buildCylinder(segments = 14))
    val sphere = VboMesh(buildSphere(rings = 8, sectors = 12))
    val pyramid = VboMesh(buildPyramid())
    val disc = VboMesh(buildDisc(segments = 18))

    val skyQuadBuffer = ByteBuffer.allocateDirect(12 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(
                floatArrayOf(
                    -1f, -1f, 1f, -1f, 1f, 1f,
                    -1f, -1f, 1f, 1f, -1f, 1f
                )
            )
            position(0)
        }

    private fun buildUnitCube(): FloatArray {
        val out = ArrayList<Float>(36 * 6)
        fun addTri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, nx: Float, ny: Float, nz: Float) {
            out.add(ax); out.add(ay); out.add(az); out.add(nx); out.add(ny); out.add(nz)
            out.add(bx); out.add(by); out.add(bz); out.add(nx); out.add(ny); out.add(nz)
            out.add(cx); out.add(cy); out.add(cz); out.add(nx); out.add(ny); out.add(nz)
        }
        fun addFace(
            p0x: Float, p0y: Float, p0z: Float,
            p1x: Float, p1y: Float, p1z: Float,
            p2x: Float, p2y: Float, p2z: Float,
            p3x: Float, p3y: Float, p3z: Float,
            nx: Float, ny: Float, nz: Float
        ) {
            addTri(p0x, p0y, p0z, p1x, p1y, p1z, p2x, p2y, p2z, nx, ny, nz)
            addTri(p0x, p0y, p0z, p2x, p2y, p2z, p3x, p3y, p3z, nx, ny, nz)
        }
        val h = 0.5f
        // Front (+Z)
        addFace(-h, -h, h, h, -h, h, h, h, h, -h, h, h, 0f, 0f, 1f)
        // Back (-Z)
        addFace(h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0f, 0f, -1f)
        // Top (+Y)
        addFace(-h, h, h, h, h, h, h, h, -h, -h, h, -h, 0f, 1f, 0f)
        // Bottom (-Y)
        addFace(-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, 0f, -1f, 0f)
        // Right (+X)
        addFace(h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1f, 0f, 0f)
        // Left (-X)
        addFace(-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1f, 0f, 0f)
        return out.toFloatArray()
    }

    private fun buildCylinder(segments: Int): FloatArray {
        val out = ArrayList<Float>(segments * 12 * 6)
        val r = 0.5f
        val h = 0.5f
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val x0 = cos(a0) * r; val z0 = sin(a0) * r
            val x1 = cos(a1) * r; val z1 = sin(a1) * r
            val nx0 = cos(a0); val nz0 = sin(a0)
            val nx1 = cos(a1); val nz1 = sin(a1)

            // Side quad (2 tris)
            fun push(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float) {
                out.add(px); out.add(py); out.add(pz); out.add(nx); out.add(ny); out.add(nz)
            }
            push(x0, -h, z0, nx0, 0f, nz0)
            push(x1, -h, z1, nx1, 0f, nz1)
            push(x1, h, z1, nx1, 0f, nz1)
            push(x0, -h, z0, nx0, 0f, nz0)
            push(x1, h, z1, nx1, 0f, nz1)
            push(x0, h, z0, nx0, 0f, nz0)

            // Top cap
            push(0f, h, 0f, 0f, 1f, 0f)
            push(x0, h, z0, 0f, 1f, 0f)
            push(x1, h, z1, 0f, 1f, 0f)

            // Bottom cap
            push(0f, -h, 0f, 0f, -1f, 0f)
            push(x1, -h, z1, 0f, -1f, 0f)
            push(x0, -h, z0, 0f, -1f, 0f)
        }
        return out.toFloatArray()
    }

    private fun buildSphere(rings: Int, sectors: Int): FloatArray {
        val out = ArrayList<Float>(rings * sectors * 6 * 6)
        val r = 0.5f
        fun vert(lat: Float, lon: Float): Triple<Float, Float, Float> {
            val cl = cos(lat)
            return Triple(cl * cos(lon), sin(lat), cl * sin(lon))
        }
        fun push(nx: Float, ny: Float, nz: Float) {
            out.add(nx * r); out.add(ny * r); out.add(nz * r)
            out.add(nx); out.add(ny); out.add(nz)
        }
        for (i in 0 until rings) {
            val lat0 = (-PI / 2 + PI * i / rings).toFloat()
            val lat1 = (-PI / 2 + PI * (i + 1) / rings).toFloat()
            for (j in 0 until sectors) {
                val lon0 = (2 * PI * j / sectors).toFloat()
                val lon1 = (2 * PI * (j + 1) / sectors).toFloat()
                val p00 = vert(lat0, lon0)
                val p10 = vert(lat0, lon1)
                val p11 = vert(lat1, lon1)
                val p01 = vert(lat1, lon0)

                push(p00.first, p00.second, p00.third)
                push(p10.first, p10.second, p10.third)
                push(p11.first, p11.second, p11.third)

                push(p00.first, p00.second, p00.third)
                push(p11.first, p11.second, p11.third)
                push(p01.first, p01.second, p01.third)
            }
        }
        return out.toFloatArray()
    }

    private fun buildPyramid(): FloatArray {
        val out = ArrayList<Float>()
        fun addTri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float) {
            val ux = bx - ax; val uy = by - ay; val uz = bz - az
            val vx = cx - ax; val vy = cy - ay; val vz = cz - az
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
            nx /= len; ny /= len; nz /= len
            out.add(ax); out.add(ay); out.add(az); out.add(nx); out.add(ny); out.add(nz)
            out.add(bx); out.add(by); out.add(bz); out.add(nx); out.add(ny); out.add(nz)
            out.add(cx); out.add(cy); out.add(cz); out.add(nx); out.add(ny); out.add(nz)
        }
        val h = 0.5f
        addTri(-h, 0f, h, h, 0f, h, 0f, 1f, 0f)
        addTri(h, 0f, h, h, 0f, -h, 0f, 1f, 0f)
        addTri(h, 0f, -h, -h, 0f, -h, 0f, 1f, 0f)
        addTri(-h, 0f, -h, -h, 0f, h, 0f, 1f, 0f)
        return out.toFloatArray()
    }

    private fun buildDisc(segments: Int): FloatArray {
        val out = ArrayList<Float>(segments * 3 * 6)
        val r = 0.5f
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            out.add(0f); out.add(0f); out.add(0f); out.add(0f); out.add(1f); out.add(0f)
            out.add(cos(a1) * r); out.add(0f); out.add(sin(a1) * r); out.add(0f); out.add(1f); out.add(0f)
            out.add(cos(a0) * r); out.add(0f); out.add(sin(a0) * r); out.add(0f); out.add(1f); out.add(0f)
        }
        return out.toFloatArray()
    }
}
