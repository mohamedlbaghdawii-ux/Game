package com.example.game.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.engine.GameEngine
import com.example.game.model.ArenaProp
import com.example.game.model.FlagStatus
import com.example.game.model.GameMode
import com.example.game.model.PropKind
import com.example.game.model.ScreenMode
import com.example.game.model.WeaponType
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin

/**
 * 60 FPS OpenGL ES 3D Renderer for MINI BATTLE ARENA.
 * Renders all 6 themed 3D arenas, animated elevators, CTF flags, Point Control zones,
 * grenades, explosion shockwaves, customizable 3D soldiers, and Armory weapon showcases.
 */
class ArenaRenderer(
    private val engine: GameEngine,
    private val onViewportChanged: (Int, Int) -> Unit,
    private val onFrameStepped: () -> Unit
) : GLSurfaceView.Renderer {

    private lateinit var litShader: LitShaderProgram
    private lateinit var skyShader: SkyShaderProgram
    private lateinit var meshes: MeshLibrary
    private lateinit var soldierRenderer: SoldierMeshRenderer

    private var lastFrameNs = System.nanoTime()
    private var viewportW = 1280
    private var viewportH = 720
    private var radarSpinDeg = 0f
    private var animClock = 0f

    @Volatile
    var armoryPreviewWeapon: WeaponType? = null

    private val modelMat = FloatArray(16)
    private val mvpMat = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(0.08f, 0.12f, 0.18f, 1f)

        litShader = LitShaderProgram()
        skyShader = SkyShaderProgram()
        meshes = MeshLibrary()
        soldierRenderer = SoldierMeshRenderer(meshes)
        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width.coerceAtLeast(1)
        viewportH = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportW, viewportH)
        onViewportChanged(viewportW, viewportH)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now

        radarSpinDeg = (radarSpinDeg + dt * 42f) % 360f
        animClock += dt

        val aspect = viewportW.toFloat() / viewportH.toFloat()
        engine.step(dt, aspect)
        onFrameStepped()

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 1. Sky Gradient per current Map theme
        renderSky()

        // 2. Lit 3D Shader setup
        litShader.use()
        val theme = engine.mapManager.themeColors
        GLES20.glUniform3f(litShader.uLightDirLoc, 0.48f, 0.78f, 0.40f)
        GLES20.glUniform3f(litShader.uFogColorLoc, theme.fogR, theme.fogG, theme.fogB)

        val vp = engine.cameraController.vpMatrix
        val screen = engine.screenMode
        val showDecor = engine.settings.graphicsQuality.decorEnabled

        // 3. Distant 3D Mountains
        GLES20.glUniform1f(litShader.uFogAmountLoc, 0.42f)
        for (m in engine.mapManager.mountains) {
            drawMesh(meshes.pyramid, vp, m.x, m.baseY, m.z, 0f, 0f, 0f, m.width, m.height, m.depth, m.r, m.g, m.b, 1f)
        }

        // 4. Arena Props
        for (prop in engine.mapManager.props) {
            if (!prop.isSolid && !showDecor) continue
            val fog = if (prop.z < -2f) 0.25f else 0.02f
            GLES20.glUniform1f(litShader.uFogAmountLoc, fog)
            renderProp(prop, vp)
        }

        // Loading screen atmospheric smoke puffs around soldier
        if (screen == ScreenMode.LOADING) {
            GLES20.glUniform1f(litShader.uFogAmountLoc, 0f)
            for (i in 0 until 6) {
                val ox = sin(animClock * 0.7f + i * 1.1f) * 2.2f
                val oy = 0.25f + ((animClock * 0.4f + i * 0.5f) % 2.4f)
                drawMesh(meshes.sphere, vp, ox, oy, -1.2f, 0f, 0f, 0f, 1.1f, 0.6f, 0.8f, 0.55f, 0.60f, 0.68f, 0.16f)
            }
        }

        // 5. Game Mode 3D Objectives (CTF Flags & Bases, Point Control Zone)
        if (screen == ScreenMode.PLAYING || screen == ScreenMode.PAUSED) {
            GLES20.glUniform1f(litShader.uFogAmountLoc, 0f)
            if (engine.matchConfig.gameMode == GameMode.CAPTURE_THE_FLAG) {
                renderCtfFlag(engine.gameModeManager.blueFlag, 0.20f, 0.68f, 1.0f, vp)
                renderCtfFlag(engine.gameModeManager.redFlag, 0.96f, 0.22f, 0.22f, vp)
            } else if (engine.matchConfig.gameMode == GameMode.POINT_CONTROL) {
                renderControlPoint(vp)
            }
        }

        // 6. Pickups
        GLES20.glUniform1f(litShader.uFogAmountLoc, 0f)
        if (screen == ScreenMode.PLAYING || screen == ScreenMode.PAUSED) {
            for (pickup in engine.pickups) {
                if (!pickup.active) continue
                val bobY = pickup.y + sin(animClock * 3.5f) * 0.14f
                val spin = (animClock * 90f) % 360f
                when {
                    pickup.isHealthPack -> {
                        drawMesh(meshes.cube, vp, pickup.x, bobY, pickup.z, 0f, spin, 0f, 0.65f, 0.48f, 0.42f, 0.94f, 0.96f, 0.98f, 1f, 0.08f, 0.22f, 0.10f)
                        drawMesh(meshes.cube, vp, pickup.x, bobY, pickup.z, 0f, spin, 0f, 0.32f, 0.12f, 0.46f, 0.15f, 0.88f, 0.42f, 1f, 0.10f, 0.45f, 0.15f)
                        drawMesh(meshes.cube, vp, pickup.x, bobY, pickup.z, 0f, spin, 0f, 0.12f, 0.32f, 0.46f, 0.15f, 0.88f, 0.42f, 1f, 0.10f, 0.45f, 0.15f)
                    }
                    pickup.isGrenadePack -> {
                        drawMesh(meshes.sphere, vp, pickup.x, bobY, pickup.z, 0f, spin, 0f, 0.46f, 0.54f, 0.46f, 0.22f, 0.42f, 0.20f, 1f, 0.06f, 0.14f, 0.05f)
                        drawMesh(meshes.cube, vp, pickup.x, bobY + 0.28f, pickup.z, 0f, spin, 0f, 0.18f, 0.14f, 0.18f, 0.96f, 0.28f, 0.15f, 1f, 0.4f, 0.1f, 0f)
                    }
                    else -> {
                        drawMesh(meshes.cube, vp, pickup.x, bobY, pickup.z, 0f, spin, 0f, 0.68f, 0.44f, 0.44f, 0.25f, 0.32f, 0.22f, 1f, 0.05f, 0.05f, 0f)
                        drawMesh(meshes.cube, vp, pickup.x, bobY + 0.05f, pickup.z, 0f, spin, 0f, 0.72f, 0.10f, 0.48f, 0.96f, 0.68f, 0.14f, 1f, 0.35f, 0.22f, 0f)
                    }
                }
            }
        }

        // 7. Soldiers OR Armory Standalone Weapon Preview
        GLES20.glUniform1f(litShader.uFogAmountLoc, 0f)
        val previewWp = if (screen == ScreenMode.ARMORY) armoryPreviewWeapon else null
        if (previewWp != null) {
            soldierRenderer.renderStandaloneWeapon(previewWp, (animClock * 45f) % 360f, vp, litShader)
        } else {
            val yawOffset = if (screen == ScreenMode.CHARACTER_CUSTOMIZE || screen == ScreenMode.ARMORY) {
                engine.cameraController.previewYawOffsetDeg
            } else 0f
            for (player in engine.players.values) {
                val carriedFlagTeam = when {
                    screen != ScreenMode.PLAYING && screen != ScreenMode.PAUSED -> null
                    engine.matchConfig.gameMode != GameMode.CAPTURE_THE_FLAG -> null
                    engine.gameModeManager.blueFlag.carrierId == player.id -> 0
                    engine.gameModeManager.redFlag.carrierId == player.id -> 1
                    else -> null
                }
                soldierRenderer.renderSoldier(
                    player = player,
                    vpMatrix = vp,
                    shader = litShader,
                    mapManager = engine.mapManager,
                    carryingFlagTeam = carriedFlagTeam,
                    previewYawOffsetDeg = yawOffset,
                    showGroundShadow = screen != ScreenMode.LOADING
                )
            }
        }

        // 8. Projectiles & Grenades
        for (proj in engine.projectileSystem.projectiles) {
            val angleDeg = atan2(proj.vy, proj.vx) * 180f / PI.toFloat()
            drawMesh(
                meshes.cube, vp,
                proj.x, proj.y, proj.z, 0f, 0f, angleDeg,
                proj.tracerLength, proj.radius * 1.4f, proj.radius * 1.4f,
                proj.r, proj.g, proj.b, 1f, proj.r * 0.85f, proj.g * 0.85f, proj.b * 0.85f
            )
        }
        for (g in engine.projectileSystem.grenades) {
            val blink = if ((g.fuseTimer * 10f).toInt() % 2 == 0) 0.7f else 0f
            drawMesh(meshes.sphere, vp, g.x, g.y, g.z, 0f, animClock * 240f, 0f, 0.30f, 0.34f, 0.30f, 0.24f, 0.42f, 0.20f, 1f, blink, 0.08f, 0.05f)
        }

        // 9. Explosion Shockwaves & Particles
        for (ex in engine.projectileSystem.explosions) {
            val frac = 1f - (ex.life / ex.maxLife).coerceIn(0f, 1f)
            val rad = ex.maxRadius * (0.3f + 0.85f * frac) * 1.6f
            val alpha = (1f - frac) * 0.65f
            drawMesh(meshes.sphere, vp, ex.x, ex.y, ex.z, 0f, 0f, 0f, rad, rad, rad, 1f, 0.55f, 0.12f, alpha, 1f, 0.42f, 0.08f)
        }
        for (pt in engine.projectileSystem.particles) {
            val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
            val s = pt.size * (0.5f + 0.5f * alpha)
            drawMesh(meshes.cube, vp, pt.x, pt.y, pt.z, pt.life * 360f, pt.life * 220f, 0f, s, s, s, pt.r, pt.g, pt.b, alpha, pt.r * 0.4f, pt.g * 0.4f, pt.b * 0.4f)
        }
    }

    private fun renderCtfFlag(flag: com.example.game.model.FlagState, r: Float, g: Float, b: Float, vp: FloatArray) {
        // Base pedestal ring
        drawMesh(meshes.cylinder, vp, flag.baseX, flag.baseY, 0f, 0f, 0f, 0f, 2.2f, 0.14f, 2.2f, r, g, b, 0.85f, r * 0.3f, g * 0.3f, b * 0.3f)
        if (flag.status != FlagStatus.CARRIED) {
            drawMesh(meshes.cylinder, vp, flag.x, flag.y + 1.2f, 0f, 0f, 0f, 0f, 0.10f, 2.4f, 0.10f, 0.88f, 0.88f, 0.92f, 1f)
            val wave = sin(animClock * 5f) * 6f
            drawMesh(meshes.cube, vp, flag.x + 0.45f, flag.y + 2.0f, 0f, 0f, wave, 0f, 0.85f, 0.52f, 0.08f, r, g, b, 1f, r * 0.35f, g * 0.35f, b * 0.35f)
        }
    }

    private fun renderControlPoint(vp: FloatArray) {
        val cp = engine.gameModeManager.controlPoint
        val (r, g, b) = when (cp.ownerTeam) {
            0 -> floatArrayOf(0.20f, 0.72f, 1.0f)
            1 -> floatArrayOf(0.96f, 0.24f, 0.24f)
            else -> floatArrayOf(0.96f, 0.72f, 0.18f)
        }
        drawMesh(meshes.cylinder, vp, cp.x, cp.y - 0.35f, 0f, 0f, 0f, 0f, cp.radius * 2f, 0.12f, 2.8f, r, g, b, 0.45f, r * 0.4f, g * 0.4f, b * 0.4f)
        drawMesh(meshes.cylinder, vp, cp.x, cp.y + 1.1f, -1.2f, 0f, radarSpinDeg, 0f, 0.28f, 2.6f, 0.28f, r, g, b, 0.9f, r * 0.5f, g * 0.5f, b * 0.5f)
    }

    private fun renderSky() {
        val t = engine.mapManager.themeColors
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        skyShader.use()
        GLES20.glUniform3f(skyShader.uTopColorLoc, t.skyTopR, t.skyTopG, t.skyTopB)
        GLES20.glUniform3f(skyShader.uMidColorLoc, t.skyMidR, t.skyMidG, t.skyMidB)
        GLES20.glUniform3f(skyShader.uBotColorLoc, t.skyBotR, t.skyBotG, t.skyBotB)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glEnableVertexAttribArray(skyShader.aPosLoc)
        GLES20.glVertexAttribPointer(skyShader.aPosLoc, 2, GLES20.GL_FLOAT, false, 0, meshes.skyQuadBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(skyShader.aPosLoc)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun renderProp(prop: ArenaProp, vp: FloatArray) {
        val cy = prop.y + prop.height * 0.5f
        when (prop.kind) {
            PropKind.GROUND -> {
                drawMesh(meshes.cube, vp, prop.x, cy, prop.z, 0f, 0f, 0f, prop.width, prop.height, prop.depth, prop.colorR, prop.colorG, prop.colorB, 1f)
                drawMesh(meshes.cube, vp, prop.x, -0.08f, prop.depth * 0.48f, 0f, 0f, 0f, prop.width, 0.16f, 0.25f, prop.accentR, prop.accentG, prop.accentB, 1f)
            }
            PropKind.PLATFORM, PropKind.ELEVATOR -> {
                drawMesh(meshes.cube, vp, prop.x, cy, prop.z, 0f, 0f, 0f, prop.width, prop.height, prop.depth, prop.colorR, prop.colorG, prop.colorB, 1f)
                val glow = if (prop.kind == PropKind.ELEVATOR) 0.35f else 0f
                drawMesh(meshes.cube, vp, prop.x, prop.maxY - 0.06f, prop.depth * 0.5f + 0.03f, 0f, 0f, 0f, prop.width, 0.12f, 0.12f, prop.accentR, prop.accentG, prop.accentB, 1f, prop.accentR * glow, prop.accentG * glow, prop.accentB * glow)
                if (prop.kind == PropKind.PLATFORM) {
                    drawMesh(meshes.cube, vp, prop.minX + 0.35f, prop.y - 0.45f, -0.9f, 0f, 0f, 0f, 0.25f, 0.9f, 0.25f, 0.22f, 0.25f, 0.30f, 1f)
                    drawMesh(meshes.cube, vp, prop.maxX - 0.35f, prop.y - 0.45f, -0.9f, 0f, 0f, 0f, 0.25f, 0.9f, 0.25f, 0.22f, 0.25f, 0.30f, 1f)
                }
            }
            PropKind.CONTAINER -> {
                drawMesh(meshes.cube, vp, prop.x, cy, prop.z, 0f, 0f, 0f, prop.width, prop.height, prop.depth, prop.colorR, prop.colorG, prop.colorB, 1f)
                val ribCount = (prop.width / 0.85f).toInt().coerceAtLeast(3)
                for (i in 0 until ribCount) {
                    val rx = prop.minX + 0.45f + i * (prop.width - 0.9f) / (ribCount - 1).coerceAtLeast(1)
                    drawMesh(meshes.cube, vp, rx, cy, prop.z, 0f, 0f, 0f, 0.18f, prop.height - 0.24f, prop.depth + 0.08f, prop.accentR, prop.accentG, prop.accentB, 1f)
                }
            }
            PropKind.CONCRETE_WALL -> {
                drawMesh(meshes.cube, vp, prop.x, cy, prop.z, 0f, 0f, 0f, prop.width, prop.height, prop.depth, prop.colorR, prop.colorG, prop.colorB, 1f)
                drawMesh(meshes.cube, vp, prop.x, prop.y + 0.25f, prop.z, 0f, 0f, 0f, prop.width + 0.08f, 0.22f, prop.depth + 0.08f, prop.accentR, prop.accentG, prop.accentB, 1f)
            }
            PropKind.BARRIER -> {
                drawMesh(meshes.cube, vp, prop.x, prop.y + 0.28f, prop.z, 0f, 0f, 0f, prop.width, 0.56f, prop.depth, prop.colorR, prop.colorG, prop.colorB, 1f)
                drawMesh(meshes.cube, vp, prop.x, prop.y + 0.72f, prop.z, 0f, 0f, 0f, prop.width * 0.72f, 0.42f, prop.depth * 0.96f, prop.colorR * 0.92f, prop.colorG * 0.92f, prop.colorB * 0.92f, 1f)
                drawMesh(meshes.cube, vp, prop.x, prop.maxY - 0.08f, prop.z, 0f, 0f, 0f, prop.width * 0.76f, 0.12f, prop.depth, prop.accentR, prop.accentG, prop.accentB, 1f)
            }
            PropKind.CRATE -> {
                drawMesh(meshes.cube, vp, prop.x, cy, prop.z, 0f, 0f, 0f, prop.width, prop.height, prop.depth, prop.colorR, prop.colorG, prop.colorB, 1f)
                drawMesh(meshes.cube, vp, prop.x, prop.maxY - 0.08f, prop.z, 0f, 0f, 0f, prop.width + 0.06f, 0.14f, prop.depth + 0.06f, prop.accentR, prop.accentG, prop.accentB, 1f)
            }
            PropKind.TOWER -> {
                drawMesh(meshes.cube, vp, prop.x, cy, prop.z, 0f, 0f, 0f, prop.width * 0.45f, prop.height, prop.depth * 0.45f, prop.colorR, prop.colorG, prop.colorB, 1f)
                drawMesh(meshes.cube, vp, prop.x, prop.maxY - 1.2f, prop.z, 0f, 0f, 0f, prop.width, 0.9f, prop.depth, prop.colorR * 1.1f, prop.colorG * 1.1f, prop.colorB * 1.1f, 1f)
                drawMesh(meshes.cube, vp, prop.x, prop.maxY + 0.6f, prop.z, 18f, radarSpinDeg, 0f, 2.8f, 0.95f, 0.28f, prop.accentR, prop.accentG, prop.accentB, 1f, 0.15f, 0.05f, 0f)
            }
            PropKind.SILO -> {
                drawMesh(meshes.cylinder, vp, prop.x, cy, prop.z, 0f, 0f, 0f, prop.width, prop.height, prop.depth, prop.colorR, prop.colorG, prop.colorB, 1f)
                drawMesh(meshes.sphere, vp, prop.x, prop.maxY, prop.z, 0f, 0f, 0f, prop.width * 0.98f, 1.8f, prop.depth * 0.98f, prop.colorR * 1.1f, prop.colorG * 1.1f, prop.colorB * 1.1f, 1f)
                drawMesh(meshes.cylinder, vp, prop.x, prop.maxY - 1.6f, prop.z, 0f, 0f, 0f, prop.width + 0.12f, 0.65f, prop.depth + 0.12f, prop.accentR, prop.accentG, prop.accentB, 1f)
            }
            PropKind.LAMP_POST -> {
                drawMesh(meshes.cylinder, vp, prop.x, cy, prop.z, 0f, 0f, 0f, prop.width, prop.height, prop.depth, prop.colorR, prop.colorG, prop.colorB, 1f)
                drawMesh(meshes.cube, vp, prop.x, prop.maxY, prop.z + 0.4f, 0f, 0f, 0f, 1.4f, 0.42f, 0.65f, prop.accentR, prop.accentG, prop.accentB, 1f, 0.75f, 0.65f, 0.35f)
            }
            PropKind.VEHICLE -> {
                // Armored truck hull, cab, and 4 tires
                drawMesh(meshes.cube, vp, prop.x - prop.width * 0.12f, prop.y + prop.height * 0.56f, prop.z, 0f, 0f, 0f, prop.width * 0.72f, prop.height * 0.68f, prop.depth, prop.colorR, prop.colorG, prop.colorB, 1f)
                drawMesh(meshes.cube, vp, prop.x + prop.width * 0.32f, prop.y + prop.height * 0.44f, prop.z, 0f, 0f, 0f, prop.width * 0.34f, prop.height * 0.50f, prop.depth * 0.92f, prop.colorR * 0.9f, prop.colorG * 0.9f, prop.colorB * 0.9f, 1f)
                drawMesh(meshes.cylinder, vp, prop.x - prop.width * 0.28f, prop.y + 0.45f, prop.z + prop.depth * 0.42f, 90f, 0f, 0f, 0.9f, 0.36f, 0.9f, prop.accentR, prop.accentG, prop.accentB, 1f)
                drawMesh(meshes.cylinder, vp, prop.x + prop.width * 0.28f, prop.y + 0.45f, prop.z + prop.depth * 0.42f, 90f, 0f, 0f, 0.9f, 0.36f, 0.9f, prop.accentR, prop.accentG, prop.accentB, 1f)
            }
            PropKind.TREE -> {
                drawMesh(meshes.cylinder, vp, prop.x, cy, prop.z, 0f, 0f, 0f, prop.width * 0.5f, prop.height, prop.depth * 0.5f, prop.colorR, prop.colorG, prop.colorB, 1f)
                drawMesh(meshes.sphere, vp, prop.x, prop.maxY, prop.z, 0f, 0f, 0f, prop.width * 2.8f, prop.height * 0.42f, prop.depth * 2.8f, prop.accentR, prop.accentG, prop.accentB, 1f)
                drawMesh(meshes.sphere, vp, prop.x - prop.width * 0.7f, prop.maxY - 1.2f, prop.z + 0.4f, 0f, 0f, 0f, prop.width * 2.0f, prop.height * 0.32f, prop.depth * 2.0f, prop.accentR * 0.88f, prop.accentG * 0.88f, prop.accentB * 0.88f, 1f)
            }
            PropKind.ROCK -> {
                drawMesh(meshes.cube, vp, prop.x, cy, prop.z, 0f, 0f, 0f, prop.width, prop.height, prop.depth, prop.colorR, prop.colorG, prop.colorB, 1f)
                drawMesh(meshes.pyramid, vp, prop.x, prop.y + prop.height * 0.35f, prop.z, 0f, 18f, 0f, prop.width * 0.92f, prop.height * 0.85f, prop.depth * 0.92f, prop.accentR, prop.accentG, prop.accentB, 1f)
            }
            PropKind.BUILDING -> {
                drawMesh(meshes.cube, vp, prop.x, cy, prop.z, 0f, 0f, 0f, prop.width, prop.height, prop.depth, prop.colorR, prop.colorG, prop.colorB, 1f)
                // Lit apartment windows
                for (row in 1..4) {
                    val wy = prop.y + row * (prop.height / 5.5f)
                    drawMesh(meshes.cube, vp, prop.x - prop.width * 0.22f, wy, prop.z + prop.depth * 0.51f, 0f, 0f, 0f, 1.3f, 0.9f, 0.1f, prop.accentR, prop.accentG, prop.accentB, 0.9f, prop.accentR * 0.4f, prop.accentG * 0.4f, prop.accentB * 0.4f)
                    drawMesh(meshes.cube, vp, prop.x + prop.width * 0.22f, wy, prop.z + prop.depth * 0.51f, 0f, 0f, 0f, 1.3f, 0.9f, 0.1f, prop.accentR, prop.accentG, prop.accentB, 0.9f, prop.accentR * 0.4f, prop.accentG * 0.4f, prop.accentB * 0.4f)
                }
            }
        }
    }

    private fun drawMesh(
        mesh: VboMesh, vpMatrix: FloatArray,
        tx: Float, ty: Float, tz: Float, rx: Float, ry: Float, rz: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float,
        er: Float = 0f, eg: Float = 0f, eb: Float = 0f
    ) {
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, tx, ty, tz)
        if (rz != 0f) Matrix.rotateM(modelMat, 0, rz, 0f, 0f, 1f)
        if (ry != 0f) Matrix.rotateM(modelMat, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(modelMat, 0, rx, 1f, 0f, 0f)
        Matrix.scaleM(modelMat, 0, sx, sy, sz)

        Matrix.multiplyMM(mvpMat, 0, vpMatrix, 0, modelMat, 0)
        GLES20.glUniformMatrix4fv(litShader.uModelLoc, 1, false, modelMat, 0)
        GLES20.glUniformMatrix4fv(litShader.uMvpLoc, 1, false, mvpMat, 0)
        GLES20.glUniform4f(litShader.uColorLoc, r, g, b, a)
        GLES20.glUniform3f(litShader.uEmissiveLoc, er, eg, eb)
        mesh.draw(litShader.aPosLoc, litShader.aNormalLoc)
    }
}
