package com.example.game.engine

import com.example.game.model.AnimationState
import com.example.game.model.PlayerInputState
import com.example.game.model.PlayerState
import kotlin.math.abs
import kotlin.math.cos

/**
 * Translates PlayerInputState into physics movement, jumping, jetpack thrust, crouching,
 * grenade throwing, aiming, weapon actions, and procedural animation states.
 */
class PlayerController(
    private val weaponSystem: WeaponSystem,
    private val collisionSystem: CollisionSystem,
    private val projectileSystem: ProjectileSystem
) {
    private val moveSpeed = 10.8f
    private val crouchSpeed = 6.2f
    private val groundAccel = 68f
    private val airAccel = 44f
    private val jumpVelocity = 15.6f
    private val doubleJumpVelocity = 13.6f
    private val jetpackAccel = 56f
    private val maxJetpackVy = 12.2f
    private val gravity = 34f
    private val maxFallSpeed = -26f

    fun stepPlayer(
        player: PlayerState,
        input: PlayerInputState,
        dt: Float,
        fireRateMultiplier: Float = 1f,
        onFireResult: (FireResult) -> Unit = {},
        onJump: () -> Unit = {},
        onGrenadeThrown: () -> Unit = {}
    ) {
        player.animTime += dt

        if (player.spawnProtectTimer > 0f) {
            player.spawnProtectTimer = (player.spawnProtectTimer - dt).coerceAtLeast(0f)
        }
        if (player.hurtFlashTimer > 0f) {
            player.hurtFlashTimer = (player.hurtFlashTimer - dt).coerceAtLeast(0f)
        }

        if (!player.isAlive) {
            player.isJetpacking = false
            player.isCrouching = false
            player.vx *= 0.85f
            player.vy = (player.vy - gravity * dt).coerceAtLeast(maxFallSpeed)
            collisionSystem.resolvePlayerMovement(player, dt)
            player.animState = AnimationState.DEAD
            return
        }

        weaponSystem.updateTimers(player, dt)

        // Weapon switch & reload
        if (input.selectWeaponIndex >= 0) {
            weaponSystem.switchWeapon(player, explicitIndex = input.selectWeaponIndex)
        } else if (input.switchWeaponDelta != 0) {
            weaponSystem.switchWeapon(player, delta = input.switchWeaponDelta)
        }
        if (input.reloadRequested) {
            weaponSystem.startReload(player)
        }

        // Crouch stance
        player.isCrouching = input.crouchPressed && !input.jetpackHeld

        // Horizontal velocity
        val maxSpd = if (player.isCrouching) crouchSpeed else moveSpeed
        val targetVx = input.moveX.coerceIn(-1f, 1f) * maxSpd
        val accel = if (player.onGround) groundAccel else airAccel
        player.vx = moveToward(player.vx, targetVx, accel * dt)

        // Jump buffer + coyote time + double-jump
        if (input.jumpPressed) {
            player.jumpBufferTimer = 0.12f
        } else {
            player.jumpBufferTimer = (player.jumpBufferTimer - dt).coerceAtLeast(0f)
        }

        if (player.jumpBufferTimer > 0f) {
            if (player.coyoteTimer > 0f) {
                player.vy = jumpVelocity
                player.onGround = false
                player.coyoteTimer = 0f
                player.jumpBufferTimer = 0f
                player.jumpsUsed = 1
                projectileSystem.spawnDustPuff(player.x, player.y, player.z)
                onJump()
            } else if (player.jumpsUsed < 2) {
                player.vy = doubleJumpVelocity
                player.jumpBufferTimer = 0f
                player.jumpsUsed = 2
                projectileSystem.spawnDustPuff(player.x, player.y, player.z)
                onJump()
            }
        }

        // Jetpack thrust & fuel regeneration
        if (input.jetpackHeld && player.jetpackFuel > 1.5f) {
            player.isJetpacking = true
            player.onGround = false
            player.vy = (player.vy + jetpackAccel * dt).coerceAtMost(maxJetpackVy)
            player.jetpackFuel = (player.jetpackFuel - 34f * dt).coerceAtLeast(0f)
            projectileSystem.spawnJetpackFlame(
                player.x - player.facingDir * 0.28f,
                player.y + 0.72f,
                player.z
            )
        } else {
            player.isJetpacking = false
            val regenRate = if (player.onGround) 32f else 10f
            player.jetpackFuel = (player.jetpackFuel + regenRate * dt).coerceAtMost(100f)
        }

        // Gravity
        player.vy = (player.vy - gravity * dt).coerceAtLeast(maxFallSpeed)

        // Resolve collisions
        val wasOnGround = player.onGround
        collisionSystem.resolvePlayerMovement(player, dt)
        if (!wasOnGround && player.onGround) {
            projectileSystem.spawnDustPuff(player.x, player.y, player.z)
        }

        // Aim & Facing direction
        if (input.hasExplicitAim) {
            player.aimAngleRad = input.aimAngleRad
            val aimCos = cos(input.aimAngleRad)
            if (abs(aimCos) > 0.08f) {
                player.facingDir = if (aimCos >= 0f) 1f else -1f
            }
        } else {
            if (abs(input.moveX) > 0.12f) {
                player.facingDir = if (input.moveX > 0f) 1f else -1f
            }
            player.aimAngleRad = if (player.facingDir >= 0f) 0f else Math.PI.toFloat()
        }

        // Grenade throw
        if (input.grenadeRequested) {
            val g = weaponSystem.tryThrowGrenade(player, player.aimAngleRad)
            if (g != null) {
                projectileSystem.spawnGrenade(g)
                onGrenadeThrown()
            }
        }

        // Weapon firing
        if (input.isFiring) {
            val fireRes = weaponSystem.tryFire(player, player.aimAngleRad, fireRateMultiplier)
            if (fireRes is FireResult.Fired) {
                projectileSystem.spawnProjectiles(fireRes.projectiles)
                projectileSystem.spawnMuzzleParticles(
                    fireRes.muzzleX, fireRes.muzzleY, player.z,
                    player.aimAngleRad, fireRes.weaponType
                )
            }
            if (fireRes !is FireResult.None) {
                onFireResult(fireRes)
            }
        }

        // Animation state machine
        player.animState = when {
            player.hurtFlashTimer > 0.08f -> AnimationState.HURT
            player.muzzleFlashTimer > 0f -> AnimationState.SHOOTING
            player.isJetpacking -> AnimationState.JETPACKING
            player.isCrouching -> AnimationState.CROUCHING
            !player.onGround && player.vy > 0.4f -> AnimationState.JUMPING
            !player.onGround && player.vy <= 0.4f -> AnimationState.FALLING
            abs(player.vx) > 0.7f -> AnimationState.RUNNING
            else -> AnimationState.IDLE
        }
    }

    private fun moveToward(current: Float, target: Float, maxDelta: Float): Float {
        val diff = target - current
        return if (abs(diff) <= maxDelta) target else current + kotlin.math.sign(diff) * maxDelta
    }
}
