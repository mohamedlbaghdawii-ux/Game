package com.example.game.engine

import com.example.game.model.PlayerState
import com.example.game.model.ScreenMode
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Orthographic side-view camera supporting gameplay tracking, menu/loading showcase,
 * and interactive 3D Character/Armory inspection framing.
 */
class CameraController(private val map: MapManager) {

    var camX: Float = 0f
        private set
    var camY: Float = 4.5f
        private set
    var halfHeight: Float = 8.4f
        private set
    var halfWidth: Float = 15.0f
        private set

    var previewYawOffsetDeg: Float = 0f

    private var shakeIntensity: Float = 0f
    private var shakeX: Float = 0f
    private var shakeY: Float = 0f
    private var orbitTime: Float = 0f

    val viewMatrix = FloatArray(16)
    val projMatrix = FloatArray(16)
    val vpMatrix = FloatArray(16)

    fun reset(x: Float, y: Float) {
        camX = x
        camY = (y + 2.2f).coerceAtLeast(4.2f)
        shakeIntensity = 0f
        shakeX = 0f
        shakeY = 0f
    }

    fun addShake(amount: Float) {
        shakeIntensity = (shakeIntensity + amount).coerceAtMost(0.75f)
    }

    fun update(
        target: PlayerState?,
        dt: Float,
        aspectRatio: Float,
        cameraSensitivity: Float,
        screenMode: ScreenMode
    ) {
        orbitTime += dt

        if (screenMode == ScreenMode.LOADING ||
            screenMode == ScreenMode.CHARACTER_CUSTOMIZE ||
            screenMode == ScreenMode.ARMORY ||
            screenMode == ScreenMode.MENU
        ) {
            // Frame the showcase soldier cleanly on screen
            val showcaseHeight = when (screenMode) {
                ScreenMode.LOADING -> 2.3f
                ScreenMode.CHARACTER_CUSTOMIZE, ScreenMode.ARMORY -> 2.1f
                else -> 4.6f
            }
            halfHeight = showcaseHeight
            halfWidth = showcaseHeight * aspectRatio.coerceAtLeast(0.5f)

            val focusX = target?.x ?: 0f
            val focusY = (target?.y ?: 0f) + 1.05f
            val offsetX = when (screenMode) {
                ScreenMode.MENU -> -0.55f
                ScreenMode.CHARACTER_CUSTOMIZE, ScreenMode.ARMORY -> 1.1f
                else -> 0f
            }
            camX = focusX + offsetX
            camY = focusY

            setOrthoM(projMatrix, -halfWidth, halfWidth, -halfHeight, halfHeight, 1f, 95f)
            val orbitRad = if (screenMode == ScreenMode.MENU || screenMode == ScreenMode.LOADING) {
                sin(orbitTime * 0.55f) * 0.25f
            } else 0f
            val eyeZ = 22f * cos(orbitRad)
            val eyeX = camX + 22f * sin(orbitRad)
            setLookAtM(viewMatrix, eyeX, camY + 0.35f, eyeZ, camX, camY, 0f, 0f, 1f, 0f)
            multiplyMM(vpMatrix, projMatrix, viewMatrix)
            return
        }

        // Gameplay side-view camera
        val baseHalfHeight = 8.4f
        halfHeight = baseHalfHeight
        halfWidth = baseHalfHeight * aspectRatio.coerceAtLeast(0.5f)

        if (target != null) {
            val lookScale = cameraSensitivity.coerceIn(0.6f, 1.5f)
            val lookAheadX = (cos(target.aimAngleRad) * 2.5f + target.vx * 0.14f) * lookScale
            val desiredX = target.x + lookAheadX
            val desiredY = target.y + 2.3f + (target.vy * 0.05f)

            val lerpRate = 1f - exp(-7.8f * dt)
            camX += (desiredX - camX) * lerpRate
            camY += (desiredY - camY) * lerpRate
        }

        val minCamX = (map.minX + halfWidth * 0.65f).coerceAtMost(0f)
        val maxCamX = (map.maxX - halfWidth * 0.65f).coerceAtLeast(0f)
        camX = camX.coerceIn(minCamX, maxCamX)
        camY = camY.coerceIn(halfHeight * 0.62f, map.maxY - halfHeight * 0.45f)

        if (shakeIntensity > 0.002f) {
            shakeX = (Random.nextFloat() - 0.5f) * 2f * shakeIntensity
            shakeY = (Random.nextFloat() - 0.5f) * 2f * shakeIntensity
            shakeIntensity *= exp(-16f * dt)
        } else {
            shakeIntensity = 0f
            shakeX = 0f
            shakeY = 0f
        }

        val cx = camX + shakeX
        val cy = camY + shakeY

        setOrthoM(projMatrix, -halfWidth, halfWidth, -halfHeight, halfHeight, 1f, 95f)
        setLookAtM(viewMatrix, cx, cy + 1.45f, 28f, cx, cy, 0f, 0f, 1f, 0f)
        multiplyMM(vpMatrix, projMatrix, viewMatrix)
    }

    fun screenToWorld(
        screenX: Float,
        screenY: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): Pair<Float, Float> {
        if (viewportWidth <= 0f || viewportHeight <= 0f) return camX to camY
        val ndcX = (screenX / viewportWidth) * 2f - 1f
        val ndcY = 1f - (screenY / viewportHeight) * 2f
        return (camX + ndcX * halfWidth) to (camY + ndcY * halfHeight)
    }

    fun worldToScreen(
        worldX: Float,
        worldY: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): Pair<Float, Float> {
        val ndcX = (worldX - camX) / halfWidth
        val ndcY = (worldY - camY) / halfHeight
        return ((ndcX + 1f) * 0.5f * viewportWidth) to ((1f - ndcY) * 0.5f * viewportHeight)
    }

    private fun setOrthoM(
        m: FloatArray,
        left: Float, right: Float,
        bottom: Float, top: Float,
        near: Float, far: Float
    ) {
        val rWidth = 1f / (right - left)
        val rHeight = 1f / (top - bottom)
        val rDepth = 1f / (far - near)
        m.fill(0f)
        m[0] = 2f * rWidth
        m[5] = 2f * rHeight
        m[10] = -2f * rDepth
        m[12] = -(right + left) * rWidth
        m[13] = -(top + bottom) * rHeight
        m[14] = -(far + near) * rDepth
        m[15] = 1f
    }

    private fun setLookAtM(
        rm: FloatArray,
        eyeX: Float, eyeY: Float, eyeZ: Float,
        centerX: Float, centerY: Float, centerZ: Float,
        upX: Float, upY: Float, upZ: Float
    ) {
        var fx = centerX - eyeX
        var fy = centerY - eyeY
        var fz = centerZ - eyeZ
        val rlf = 1f / sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-6f)
        fx *= rlf; fy *= rlf; fz *= rlf

        var sx = fy * upZ - fz * upY
        var sy = fz * upX - fx * upZ
        var sz = fx * upY - fy * upX
        val rls = 1f / sqrt(sx * sx + sy * sy + sz * sz).coerceAtLeast(1e-6f)
        sx *= rls; sy *= rls; sz *= rls

        val ux = sy * fz - sz * fy
        val uy = sz * fx - sx * fz
        val uz = sx * fy - sy * fx

        rm[0] = sx; rm[4] = sy; rm[8] = sz; rm[12] = -(sx * eyeX + sy * eyeY + sz * eyeZ)
        rm[1] = ux; rm[5] = uy; rm[9] = uz; rm[13] = -(ux * eyeX + uy * eyeY + uz * eyeZ)
        rm[2] = -fx; rm[6] = -fy; rm[10] = -fz; rm[14] = (fx * eyeX + fy * eyeY + fz * eyeZ)
        rm[3] = 0f; rm[7] = 0f; rm[11] = 0f; rm[15] = 1f
    }

    private fun multiplyMM(result: FloatArray, lhs: FloatArray, rhs: FloatArray) {
        for (col in 0 until 4) {
            val c4 = col * 4
            val r0 = rhs[c4]
            val r1 = rhs[c4 + 1]
            val r2 = rhs[c4 + 2]
            val r3 = rhs[c4 + 3]
            result[c4] = lhs[0] * r0 + lhs[4] * r1 + lhs[8] * r2 + lhs[12] * r3
            result[c4 + 1] = lhs[1] * r0 + lhs[5] * r1 + lhs[9] * r2 + lhs[13] * r3
            result[c4 + 2] = lhs[2] * r0 + lhs[6] * r1 + lhs[10] * r2 + lhs[14] * r3
            result[c4 + 3] = lhs[3] * r0 + lhs[7] * r1 + lhs[11] * r2 + lhs[15] * r3
        }
    }
}
