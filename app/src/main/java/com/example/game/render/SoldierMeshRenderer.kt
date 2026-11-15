package com.example.game.render

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.game.engine.MapManager
import com.example.game.model.AnimationState
import com.example.game.model.BackpackStyle
import com.example.game.model.HeadgearStyle
import com.example.game.model.PlayerState
import com.example.game.model.WeaponType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Renders stylized customizable 3D soldiers with 6 headgear styles, 4 backpack styles,
 * custom uniform colors, crouching/jetpacking animations, 6 detailed 3D weapons, and carried CTF flags.
 */
class SoldierMeshRenderer(private val meshes: MeshLibrary) {

    private val modelMatrix = FloatArray(16)
    private val partMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private data class WeaponBoxDraw(val localMat: FloatArray, val r: Float, val g: Float, val b: Float)
    private val pendingWeaponParts = ArrayList<WeaponBoxDraw>(10)

    private fun hexR(hex: Long): Float = ((hex shr 16) and 0xFF) / 255f
    private fun hexG(hex: Long): Float = ((hex shr 8) and 0xFF) / 255f
    private fun hexB(hex: Long): Float = (hex and 0xFF) / 255f

    fun renderSoldier(
        player: PlayerState,
        vpMatrix: FloatArray,
        shader: LitShaderProgram,
        mapManager: MapManager,
        carryingFlagTeam: Int? = null,
        previewYawOffsetDeg: Float = 0f,
        showGroundShadow: Boolean = true
    ) {
        val app = player.appearance
        val shirtR = hexR(app.shirtHex); val shirtG = hexG(app.shirtHex); val shirtB = hexB(app.shirtHex)
        val pantsR = hexR(app.pantsHex); val pantsG = hexG(app.pantsHex); val pantsB = hexB(app.pantsHex)
        val bootsR = hexR(app.bootsHex); val bootsG = hexG(app.bootsHex); val bootsB = hexB(app.bootsHex)
        val packR = hexR(app.backpackHex); val packG = hexG(app.backpackHex); val packB = hexB(app.backpackHex)
        val visorR = hexR(app.visorHex); val visorG = hexG(app.visorHex); val visorB = hexB(app.visorHex)

        val hurtFlash = if (player.hurtFlashTimer > 0f) 0.55f else 0f
        val spawnShimmer = if (player.spawnProtectTimer > 0f && ((player.animTime * 12f).toInt() % 2 == 0)) 0.25f else 0f
        val hitR = hurtFlash * 1.0f + spawnShimmer * 0.2f
        val hitG = hurtFlash * 0.22f + spawnShimmer * 0.7f
        val hitB = hurtFlash * 0.18f + spawnShimmer * 0.9f

        if (showGroundShadow) {
            val shadowY = mapManager.getShadowGroundY(player.x, player.y)
            val heightAbove = (player.y - shadowY).coerceIn(0f, 8f)
            val shadowScale = (1.15f - heightAbove * 0.08f).coerceIn(0.4f, 1.15f)
            drawWorldPart(
                meshes.disc, vpMatrix, shader,
                player.x, shadowY + 0.025f, player.z,
                0f, 0f, 0f,
                shadowScale, 1f, shadowScale * 0.68f,
                0.04f, 0.05f, 0.07f, 0.55f
            )
        }

        val t = player.animTime
        var bodyBob = 0f
        var bodyTiltZ = 0f
        var legSwing = 0f
        var crouchDrop = 0f
        var deathCollapse = 0f

        when (player.animState) {
            AnimationState.IDLE -> bodyBob = sin(t * 3.0f) * 0.032f
            AnimationState.RUNNING -> {
                val stride = t * 14f
                bodyBob = abs(sin(stride)) * 0.08f
                legSwing = sin(stride) * 44f
                bodyTiltZ = -player.facingDir * 9f
            }
            AnimationState.JUMPING -> {
                legSwing = 26f
                bodyTiltZ = -player.facingDir * 6f
            }
            AnimationState.FALLING -> {
                legSwing = -18f
                bodyTiltZ = player.facingDir * 6f
            }
            AnimationState.CROUCHING -> {
                crouchDrop = 0.42f
                bodyTiltZ = -player.facingDir * 10f
            }
            AnimationState.JETPACKING -> {
                legSwing = -14f
                bodyTiltZ = -player.facingDir * 12f
                bodyBob = sin(t * 16f) * 0.025f
            }
            AnimationState.SHOOTING -> {
                bodyBob = sin(t * 4f) * 0.02f
                bodyTiltZ = player.facingDir * (player.recoilOffset * 11f)
            }
            AnimationState.HURT -> bodyTiltZ = player.facingDir * 16f
            AnimationState.DEAD -> {
                val frac = (t / 0.42f).coerceIn(0f, 1f)
                deathCollapse = frac * 88f
            }
        }

        val rootX = player.x
        val rootY = if (player.animState == AnimationState.DEAD) player.y + 0.22f else player.y + bodyBob
        val rootZ = player.z
        val dir = player.facingDir
        val yawDeg = (if (dir >= 0f) 24f else 156f) + previewYawOffsetDeg

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, rootX, rootY, rootZ)
        if (deathCollapse > 0f) {
            Matrix.rotateM(modelMatrix, 0, -dir * deathCollapse, 0f, 0f, 1f)
        } else {
            Matrix.rotateM(modelMatrix, 0, bodyTiltZ, 0f, 0f, 1f)
        }
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)

        // === LEGS & BOOTS ===
        val legScaleY = if (crouchDrop > 0f) 0.65f else 1.0f
        drawLeg(vpMatrix, shader, true, legSwing, legScaleY, pantsR, pantsG, pantsB, bootsR, bootsG, bootsB, hitR, hitG, hitB)
        drawLeg(vpMatrix, shader, false, -legSwing, legScaleY, pantsR, pantsG, pantsB, bootsR, bootsG, bootsB, hitR, hitG, hitB)

        val upperOffset = -crouchDrop

        // === TORSO + COMBAT VEST ===
        drawChild(meshes.cube, vpMatrix, shader, 0f, 1.06f + upperOffset, 0f, 0f, 0f, 0f, 0.56f, 0.68f, 0.40f,
            shirtR, shirtG, shirtB, hitR, hitG, hitB)
        drawChild(meshes.cube, vpMatrix, shader, 0.04f, 1.09f + upperOffset, 0f, 0f, 0f, 0f, 0.58f, 0.44f, 0.44f,
            shirtR * 0.72f, shirtG * 0.72f, shirtB * 0.72f, hitR, hitG, hitB)
        drawChild(meshes.cube, vpMatrix, shader, 0f, 0.74f + upperOffset, 0f, 0f, 0f, 0f, 0.58f, 0.12f, 0.42f,
            0.16f, 0.18f, 0.20f, hitR, hitG, hitB)

        // === BACKPACK ===
        drawBackpack(vpMatrix, shader, app.backpackStyle, upperOffset, packR, packG, packB, hitR, hitG, hitB, player.isJetpacking)

        // === CARRIED CTF FLAG ON BACK ===
        if (carryingFlagTeam != null) {
            val fR = if (carryingFlagTeam == 0) 0.20f else 0.96f
            val fG = if (carryingFlagTeam == 0) 0.68f else 0.22f
            val fB = if (carryingFlagTeam == 0) 1.0f else 0.22f
            drawChild(meshes.cylinder, vpMatrix, shader, -0.36f, 1.85f + upperOffset, 0f, 0f, 0f, 10f, 0.06f, 1.5f, 0.06f, 0.85f, 0.85f, 0.88f, 0f, 0f, 0f)
            drawChild(meshes.cube, vpMatrix, shader, -0.68f, 2.35f + upperOffset, 0f, 0f, 0f, 10f, 0.66f, 0.40f, 0.06f, fR, fG, fB, fR * 0.35f, fG * 0.35f, fB * 0.35f)
        }

        // === HEAD & CUSTOM HEADGEAR ===
        drawChild(meshes.sphere, vpMatrix, shader, 0.02f, 1.58f + upperOffset, 0f, 0f, 0f, 0f, 0.46f, 0.46f, 0.44f,
            0.88f, 0.72f, 0.58f, hitR, hitG, hitB)
        drawHeadgear(vpMatrix, shader, app.headgearStyle, upperOffset, shirtR, shirtG, shirtB, visorR, visorG, visorB, hitR, hitG, hitB)

        // === AIMING ARM & WEAPON ===
        val worldAimDeg = (player.aimAngleRad * 180f / PI.toFloat())
        var localPitchDeg = if (dir >= 0f) worldAimDeg else (180f - worldAimDeg)
        while (localPitchDeg > 180f) localPitchDeg -= 360f
        while (localPitchDeg < -180f) localPitchDeg += 360f

        drawAimingArmAndWeapon(
            vpMatrix = vpMatrix, shader = shader,
            shoulderY = 1.24f + upperOffset,
            pitchDeg = localPitchDeg,
            recoilShift = -player.recoilOffset * 0.22f,
            weapon = player.activeWeaponSlot.weapon,
            showMuzzleFlash = player.muzzleFlashTimer > 0f && player.isAlive,
            shirtR = shirtR, shirtG = shirtG, shirtB = shirtB,
            hitR = hitR, hitG = hitG, hitB = hitB
        )

        val backArmSwing = if (player.animState == AnimationState.RUNNING) -legSwing * 0.5f else localPitchDeg * 0.45f
        drawChild(meshes.cube, vpMatrix, shader, 0.05f, 1.15f + upperOffset, -0.25f, 0f, 0f, backArmSwing, 0.34f, 0.15f, 0.15f,
            shirtR * 0.85f, shirtG * 0.85f, shirtB * 0.85f, hitR, hitG, hitB)
    }

    fun renderStandaloneWeapon(
        weapon: WeaponType,
        yawDeg: Float,
        vpMatrix: FloatArray,
        shader: LitShaderProgram
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 1.0f, 1.05f, 0f)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, 2.2f, 2.2f, 2.2f)
        Matrix.setIdentityM(partMatrix, 0)
        Matrix.translateM(partMatrix, 0, -0.62f, 0f, 0f)
        queueWeaponParts(weapon)
        flushWeaponParts(vpMatrix, shader, 0.04f, 0.04f, 0.04f)
    }

    private fun drawBackpack(
        vpMatrix: FloatArray, shader: LitShaderProgram,
        style: BackpackStyle, upperOffset: Float,
        r: Float, g: Float, b: Float, hitR: Float, hitG: Float, hitB: Float,
        jetActive: Boolean
    ) {
        val y = 1.10f + upperOffset
        drawChild(meshes.cube, vpMatrix, shader, -0.34f, y, 0f, 0f, 0f, 0f, 0.26f, 0.50f, 0.34f, r, g, b, hitR, hitG, hitB)
        when (style) {
            BackpackStyle.FIELD_PACK -> {
                drawChild(meshes.cube, vpMatrix, shader, -0.44f, y - 0.10f, 0f, 0f, 0f, 0f, 0.12f, 0.24f, 0.30f, r * 0.78f, g * 0.78f, b * 0.78f, hitR, hitG, hitB)
            }
            BackpackStyle.RADIO_PACK -> {
                drawChild(meshes.cylinder, vpMatrix, shader, -0.40f, y + 0.38f, -0.10f, 0f, 0f, 0f, 0.04f, 0.42f, 0.04f, 0.18f, 0.20f, 0.22f, hitR, hitG, hitB)
            }
            BackpackStyle.JET_THRUSTER -> {
                val thrustGlowR = if (jetActive) 1.0f else 0.15f
                val thrustGlowG = if (jetActive) 0.55f else 0.15f
                drawChild(meshes.cylinder, vpMatrix, shader, -0.40f, y - 0.16f, 0.11f, 0f, 0f, -10f, 0.12f, 0.34f, 0.12f, 0.24f, 0.27f, 0.32f, thrustGlowR, thrustGlowG, 0f)
                drawChild(meshes.cylinder, vpMatrix, shader, -0.40f, y - 0.16f, -0.11f, 0f, 0f, -10f, 0.12f, 0.34f, 0.12f, 0.24f, 0.27f, 0.32f, thrustGlowR, thrustGlowG, 0f)
            }
            BackpackStyle.MUNITIONS_PACK -> {
                drawChild(meshes.cube, vpMatrix, shader, -0.42f, y + 0.08f, 0f, 0f, 0f, 0f, 0.18f, 0.44f, 0.40f, 0.85f, 0.62f, 0.14f, hitR, hitG, hitB)
            }
        }
    }

    private fun drawHeadgear(
        vpMatrix: FloatArray, shader: LitShaderProgram,
        style: HeadgearStyle, upperOffset: Float,
        shirtR: Float, shirtG: Float, shirtB: Float,
        visorR: Float, visorG: Float, visorB: Float,
        hitR: Float, hitG: Float, hitB: Float
    ) {
        val hy = 1.66f + upperOffset
        when (style) {
            HeadgearStyle.M1_HELMET -> {
                drawChild(meshes.sphere, vpMatrix, shader, 0f, hy, 0f, 0f, 0f, 0f, 0.54f, 0.44f, 0.52f, shirtR * 0.9f, shirtG * 0.9f, shirtB * 0.9f, hitR, hitG, hitB)
                drawChild(meshes.cube, vpMatrix, shader, 0.14f, hy + 0.01f, 0f, 0f, 0f, 0f, 0.34f, 0.08f, 0.48f, shirtR * 0.65f, shirtG * 0.65f, shirtB * 0.65f, hitR, hitG, hitB)
                drawChild(meshes.cube, vpMatrix, shader, 0.21f, hy - 0.08f, 0f, 0f, 0f, 0f, 0.18f, 0.14f, 0.38f, visorR, visorG, visorB, visorR * 0.5f + hitR, visorG * 0.5f + hitG, visorB * 0.5f + hitB)
            }
            HeadgearStyle.TACTICAL_VISOR -> {
                drawChild(meshes.sphere, vpMatrix, shader, 0f, hy, 0f, 0f, 0f, 0f, 0.55f, 0.46f, 0.54f, 0.20f, 0.24f, 0.30f, hitR, hitG, hitB)
                drawChild(meshes.cube, vpMatrix, shader, 0.20f, hy - 0.06f, 0f, 0f, 0f, 0f, 0.22f, 0.20f, 0.44f, visorR, visorG, visorB, visorR * 0.7f + hitR, visorG * 0.7f + hitG, visorB * 0.7f + hitB)
            }
            HeadgearStyle.DESERT_CAP -> {
                drawChild(meshes.cylinder, vpMatrix, shader, 0f, hy + 0.04f, 0f, 0f, 0f, 0f, 0.48f, 0.20f, 0.46f, 0.78f, 0.64f, 0.42f, hitR, hitG, hitB)
                drawChild(meshes.cube, vpMatrix, shader, 0.22f, hy - 0.02f, 0f, 0f, 0f, -8f, 0.32f, 0.06f, 0.40f, 0.62f, 0.48f, 0.30f, hitR, hitG, hitB)
                drawChild(meshes.cube, vpMatrix, shader, 0.21f, hy - 0.09f, 0f, 0f, 0f, 0f, 0.14f, 0.10f, 0.34f, visorR, visorG, visorB, visorR * 0.4f + hitR, visorG * 0.4f + hitG, visorB * 0.4f + hitB)
            }
            HeadgearStyle.SPEC_OPS_MASK -> {
                drawChild(meshes.sphere, vpMatrix, shader, 0f, hy - 0.04f, 0f, 0f, 0f, 0f, 0.50f, 0.50f, 0.48f, 0.14f, 0.16f, 0.20f, hitR, hitG, hitB)
                drawChild(meshes.cylinder, vpMatrix, shader, 0.23f, hy - 0.06f, 0.10f, 0f, 0f, 90f, 0.12f, 0.14f, 0.12f, visorR, visorG, visorB, visorR * 0.8f + hitR, visorG * 0.8f + hitG, visorB * 0.8f + hitB)
                drawChild(meshes.cylinder, vpMatrix, shader, 0.23f, hy - 0.06f, -0.10f, 0f, 0f, 90f, 0.12f, 0.14f, 0.12f, visorR, visorG, visorB, visorR * 0.8f + hitR, visorG * 0.8f + hitG, visorB * 0.8f + hitB)
            }
            HeadgearStyle.HEAVY_JUGGERNAUT -> {
                drawChild(meshes.cube, vpMatrix, shader, 0f, hy - 0.02f, 0f, 0f, 0f, 0f, 0.58f, 0.54f, 0.56f, 0.30f, 0.35f, 0.42f, hitR, hitG, hitB)
                drawChild(meshes.cube, vpMatrix, shader, 0.26f, hy - 0.05f, 0f, 0f, 0f, 0f, 0.16f, 0.12f, 0.42f, visorR, visorG, visorB, visorR * 0.7f + hitR, visorG * 0.7f + hitG, visorB * 0.7f + hitB)
            }
            HeadgearStyle.COMMANDO_BERET -> {
                drawChild(meshes.sphere, vpMatrix, shader, -0.04f, hy + 0.08f, 0.02f, 0f, 0f, 14f, 0.56f, 0.26f, 0.52f, 0.78f, 0.14f, 0.16f, hitR, hitG, hitB)
                drawChild(meshes.cube, vpMatrix, shader, 0.21f, hy - 0.08f, 0f, 0f, 0f, 0f, 0.16f, 0.10f, 0.36f, visorR, visorG, visorB, visorR * 0.45f + hitR, visorG * 0.45f + hitG, visorB * 0.45f + hitB)
            }
        }
    }

    private fun drawLeg(
        vpMatrix: FloatArray, shader: LitShaderProgram, isLeft: Boolean,
        swingDeg: Float, scaleY: Float,
        pantsR: Float, pantsG: Float, pantsB: Float,
        bootsR: Float, bootsG: Float, bootsB: Float,
        hitR: Float, hitG: Float, hitB: Float
    ) {
        val zOff = if (isLeft) 0.13f else -0.13f
        Matrix.setIdentityM(partMatrix, 0)
        Matrix.translateM(partMatrix, 0, 0f, 0.70f * scaleY, zOff)
        Matrix.rotateM(partMatrix, 0, swingDeg, 0f, 0f, 1f)

        val legMat = FloatArray(16)
        Matrix.translateM(legMat, 0, partMatrix, 0, 0f, -0.28f * scaleY, 0f)
        Matrix.scaleM(legMat, 0, 0.20f, 0.48f * scaleY, 0.20f)
        drawWithLocalMat(meshes.cube, vpMatrix, shader, legMat, pantsR, pantsG, pantsB, 1f, hitR, hitG, hitB)

        val bootMat = FloatArray(16)
        Matrix.translateM(bootMat, 0, partMatrix, 0, 0.05f, -0.58f * scaleY, 0f)
        Matrix.scaleM(bootMat, 0, 0.30f, 0.16f, 0.22f)
        drawWithLocalMat(meshes.cube, vpMatrix, shader, bootMat, bootsR, bootsG, bootsB, 1f, hitR, hitG, hitB)
    }

    private fun drawAimingArmAndWeapon(
        vpMatrix: FloatArray, shader: LitShaderProgram,
        shoulderY: Float, pitchDeg: Float, recoilShift: Float,
        weapon: WeaponType, showMuzzleFlash: Boolean,
        shirtR: Float, shirtG: Float, shirtB: Float,
        hitR: Float, hitG: Float, hitB: Float
    ) {
        Matrix.setIdentityM(partMatrix, 0)
        Matrix.translateM(partMatrix, 0, 0.06f, shoulderY, 0.24f)
        Matrix.rotateM(partMatrix, 0, pitchDeg, 0f, 0f, 1f)
        Matrix.translateM(partMatrix, 0, recoilShift, 0f, 0f)

        val armMat = FloatArray(16)
        Matrix.translateM(armMat, 0, partMatrix, 0, 0.22f, -0.02f, 0f)
        Matrix.scaleM(armMat, 0, 0.40f, 0.15f, 0.15f)
        drawWithLocalMat(meshes.cube, vpMatrix, shader, armMat, shirtR * 0.9f, shirtG * 0.9f, shirtB * 0.9f, 1f, hitR, hitG, hitB)

        val muzzleTipX = queueWeaponParts(weapon)
        flushWeaponParts(vpMatrix, shader, hitR, hitG, hitB)

        if (showMuzzleFlash) {
            val flashMat = FloatArray(16)
            Matrix.translateM(flashMat, 0, partMatrix, 0, muzzleTipX, 0.06f, -0.06f)
            Matrix.scaleM(flashMat, 0, 0.38f, 0.26f, 0.26f)
            drawWithLocalMat(meshes.sphere, vpMatrix, shader, flashMat, 1f, 0.92f, 0.45f, 1f, 1f, 0.85f, 0.30f)
        }
    }

    private fun queueWeaponParts(weapon: WeaponType): Float {
        return when (weapon) {
            WeaponType.PISTOL -> {
                drawWeaponPart(0.46f, 0.06f, -0.04f, 0.34f, 0.11f, 0.09f, 0.20f, 0.23f, 0.27f)
                drawWeaponPart(0.37f, -0.04f, -0.04f, 0.11f, 0.16f, 0.08f, 0.13f, 0.14f, 0.16f)
                0.68f
            }
            WeaponType.ASSAULT_RIFLE -> {
                drawWeaponPart(0.56f, 0.05f, -0.06f, 0.62f, 0.13f, 0.11f, 0.18f, 0.21f, 0.25f)
                drawWeaponPart(0.24f, 0.03f, -0.06f, 0.22f, 0.12f, 0.09f, 0.14f, 0.15f, 0.17f)
                drawWeaponPart(0.95f, 0.06f, -0.06f, 0.34f, 0.06f, 0.06f, 0.30f, 0.33f, 0.38f)
                drawWeaponPart(0.62f, -0.10f, -0.06f, 0.11f, 0.22f, 0.08f, 0.90f, 0.62f, 0.14f)
                drawWeaponPart(0.54f, 0.15f, -0.06f, 0.16f, 0.08f, 0.08f, 0.12f, 0.72f, 0.92f)
                1.14f
            }
            WeaponType.SHOTGUN -> {
                drawWeaponPart(0.54f, 0.04f, -0.06f, 0.54f, 0.14f, 0.12f, 0.36f, 0.22f, 0.14f)
                drawWeaponPart(0.86f, 0.08f, -0.06f, 0.44f, 0.07f, 0.07f, 0.24f, 0.26f, 0.30f)
                drawWeaponPart(0.80f, 0.01f, -0.06f, 0.34f, 0.08f, 0.09f, 0.16f, 0.17f, 0.19f)
                1.10f
            }
            WeaponType.SNIPER_RIFLE -> {
                drawWeaponPart(0.58f, 0.04f, -0.06f, 0.66f, 0.12f, 0.11f, 0.22f, 0.28f, 0.24f)
                drawWeaponPart(1.12f, 0.06f, -0.06f, 0.60f, 0.05f, 0.05f, 0.18f, 0.20f, 0.22f)
                drawWeaponPart(1.42f, 0.06f, -0.06f, 0.11f, 0.08f, 0.08f, 0.28f, 0.85f, 1.0f)
                drawWeaponPart(0.62f, 0.16f, -0.06f, 0.32f, 0.09f, 0.09f, 0.14f, 0.16f, 0.20f)
                1.50f
            }
            WeaponType.MACHINE_GUN -> {
                drawWeaponPart(0.58f, 0.05f, -0.06f, 0.72f, 0.16f, 0.14f, 0.20f, 0.24f, 0.22f)
                drawWeaponPart(1.06f, 0.06f, -0.06f, 0.42f, 0.08f, 0.08f, 0.34f, 0.36f, 0.40f)
                drawWeaponPart(0.62f, -0.14f, -0.06f, 0.24f, 0.22f, 0.24f, 0.26f, 0.38f, 0.22f) // Ammo box drum
                1.30f
            }
            WeaponType.ROCKET_LAUNCHER -> {
                drawWeaponPart(0.64f, 0.12f, -0.06f, 1.08f, 0.22f, 0.22f, 0.25f, 0.36f, 0.24f) // Launch tube
                drawWeaponPart(0.18f, 0.12f, -0.06f, 0.16f, 0.26f, 0.26f, 0.92f, 0.56f, 0.12f) // Rear bell
                drawWeaponPart(1.16f, 0.12f, -0.06f, 0.18f, 0.18f, 0.18f, 0.90f, 0.22f, 0.14f) // Warhead tip
                1.34f
            }
        }
    }

    private fun drawWeaponPart(ox: Float, oy: Float, oz: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float) {
        val m = FloatArray(16)
        Matrix.translateM(m, 0, partMatrix, 0, ox, oy, oz)
        Matrix.scaleM(m, 0, sx, sy, sz)
        pendingWeaponParts.add(WeaponBoxDraw(m, r, g, b))
    }

    private fun flushWeaponParts(vpMatrix: FloatArray, shader: LitShaderProgram, hitR: Float, hitG: Float, hitB: Float) {
        for (wp in pendingWeaponParts) {
            drawWithLocalMat(meshes.cube, vpMatrix, shader, wp.localMat, wp.r, wp.g, wp.b, 1f, hitR, hitG, hitB)
        }
        pendingWeaponParts.clear()
    }

    private fun drawWithLocalMat(
        mesh: VboMesh, vpMatrix: FloatArray, shader: LitShaderProgram,
        localMat: FloatArray, r: Float, g: Float, b: Float, a: Float,
        er: Float, eg: Float, eb: Float
    ) {
        val finalModel = FloatArray(16)
        Matrix.multiplyMM(finalModel, 0, modelMatrix, 0, localMat, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, finalModel, 0)
        GLES20.glUniformMatrix4fv(shader.uModelLoc, 1, false, finalModel, 0)
        GLES20.glUniformMatrix4fv(shader.uMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(shader.uColorLoc, r, g, b, a)
        GLES20.glUniform3f(shader.uEmissiveLoc, er, eg, eb)
        mesh.draw(shader.aPosLoc, shader.aNormalLoc)
    }

    private fun drawChild(
        mesh: VboMesh, vpMatrix: FloatArray, shader: LitShaderProgram,
        tx: Float, ty: Float, tz: Float, rx: Float, ry: Float, rz: Float,
        sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float,
        er: Float, eg: Float, eb: Float
    ) {
        val local = FloatArray(16)
        Matrix.setIdentityM(local, 0)
        Matrix.translateM(local, 0, tx, ty, tz)
        if (rz != 0f) Matrix.rotateM(local, 0, rz, 0f, 0f, 1f)
        if (ry != 0f) Matrix.rotateM(local, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(local, 0, rx, 1f, 0f, 0f)
        Matrix.scaleM(local, 0, sx, sy, sz)
        drawWithLocalMat(mesh, vpMatrix, shader, local, r, g, b, 1f, er, eg, eb)
    }

    private fun drawWorldPart(
        mesh: VboMesh, vpMatrix: FloatArray, shader: LitShaderProgram,
        tx: Float, ty: Float, tz: Float, rx: Float, ry: Float, rz: Float,
        sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float, a: Float
    ) {
        val worldMat = FloatArray(16)
        Matrix.setIdentityM(worldMat, 0)
        Matrix.translateM(worldMat, 0, tx, ty, tz)
        if (rz != 0f) Matrix.rotateM(worldMat, 0, rz, 0f, 0f, 1f)
        if (ry != 0f) Matrix.rotateM(worldMat, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(worldMat, 0, rx, 1f, 0f, 0f)
        Matrix.scaleM(worldMat, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, worldMat, 0)
        GLES20.glUniformMatrix4fv(shader.uModelLoc, 1, false, worldMat, 0)
        GLES20.glUniformMatrix4fv(shader.uMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(shader.uColorLoc, r, g, b, a)
        GLES20.glUniform3f(shader.uEmissiveLoc, 0f, 0f, 0f)
        mesh.draw(shader.aPosLoc, shader.aNormalLoc)
    }
}
