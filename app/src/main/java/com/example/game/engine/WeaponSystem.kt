package com.example.game.engine

import com.example.game.model.GrenadeState
import com.example.game.model.PlayerState
import com.example.game.model.ProjectileState
import com.example.game.model.WeaponType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

sealed class FireResult {
    data object None : FireResult()
    data class Fired(
        val weaponType: WeaponType,
        val projectiles: List<ProjectileState>,
        val muzzleX: Float,
        val muzzleY: Float
    ) : FireResult()
    data object ReloadStarted : FireResult()
    data object EmptyClick : FireResult()
}

/**
 * Manages all 6 firearm classes (Pistol, Assault Rifle, Shotgun, Sniper, Machine Gun, Rocket Launcher)
 * plus throwable Frag Grenades.
 */
class WeaponSystem(private val rng: Random = Random.Default) {

    private var nextProjectileId = 1L
    private var nextGrenadeId = 1L
    private var emptyClickCooldown = 0f

    fun updateTimers(player: PlayerState, dt: Float) {
        if (emptyClickCooldown > 0f) emptyClickCooldown -= dt
        if (player.grenadeCooldown > 0f) {
            player.grenadeCooldown = (player.grenadeCooldown - dt).coerceAtLeast(0f)
        }
        val slot = player.activeWeaponSlot
        if (slot.fireCooldown > 0f) {
            slot.fireCooldown = (slot.fireCooldown - dt).coerceAtLeast(0f)
        }
        if (slot.isReloading) {
            slot.reloadTimer -= dt
            if (slot.reloadTimer <= 0f) {
                completeReload(player)
            }
        }
        if (player.muzzleFlashTimer > 0f) {
            player.muzzleFlashTimer = (player.muzzleFlashTimer - dt).coerceAtLeast(0f)
        }
        if (player.recoilOffset > 0f) {
            player.recoilOffset = (player.recoilOffset - dt * 2.6f).coerceAtLeast(0f)
        }
    }

    fun switchWeapon(player: PlayerState, delta: Int = 1, explicitIndex: Int = -1): Boolean {
        if (!player.isAlive) return false
        val count = player.weapons.size
        val targetIndex = if (explicitIndex in 0 until count) {
            explicitIndex
        } else {
            ((player.currentWeaponIndex + delta) % count + count) % count
        }
        if (targetIndex == player.currentWeaponIndex) return false
        player.activeWeaponSlot.isReloading = false
        player.activeWeaponSlot.reloadTimer = 0f
        player.currentWeaponIndex = targetIndex
        return true
    }

    fun startReload(player: PlayerState): Boolean {
        if (!player.isAlive) return false
        val slot = player.activeWeaponSlot
        val spec = slot.weapon.spec
        if (slot.isReloading || slot.ammoInMag >= spec.magazineSize || slot.reserveAmmo <= 0) {
            return false
        }
        slot.isReloading = true
        slot.reloadTimer = spec.reloadDurationSec
        return true
    }

    fun tryThrowGrenade(player: PlayerState, aimAngleRad: Float): GrenadeState? {
        if (!player.isAlive || player.grenades <= 0 || player.grenadeCooldown > 0f) return null
        player.grenades -= 1
        player.grenadeCooldown = 0.85f
        val shoulderY = player.y + (if (player.isCrouching) 0.90f else 1.32f)
        val speed = 17.5f
        return GrenadeState(
            id = nextGrenadeId++,
            ownerId = player.id,
            ownerTeam = player.teamIndex,
            x = player.x + cos(aimAngleRad) * 0.55f,
            y = shoulderY + sin(aimAngleRad) * 0.4f,
            z = player.z,
            vx = cos(aimAngleRad) * speed,
            vy = sin(aimAngleRad) * speed + 4.8f,
            fuseTimer = 1.65f
        )
    }

    fun tryFire(
        player: PlayerState,
        aimAngleRad: Float,
        fireRateMultiplier: Float = 1f
    ): FireResult {
        if (!player.isAlive) return FireResult.None
        val slot = player.activeWeaponSlot
        val spec = slot.weapon.spec

        if (slot.isReloading) return FireResult.None
        if (slot.fireCooldown > 0f) return FireResult.None

        if (slot.ammoInMag <= 0) {
            if (slot.reserveAmmo > 0) {
                startReload(player)
                return FireResult.ReloadStarted
            }
            if (!player.isBot && emptyClickCooldown <= 0f) {
                emptyClickCooldown = 0.35f
                return FireResult.EmptyClick
            }
            return FireResult.None
        }

        slot.ammoInMag -= 1
        slot.fireCooldown = spec.fireIntervalSec * fireRateMultiplier
        player.muzzleFlashTimer = 0.065f
        player.recoilOffset = spec.recoilKick

        val shoulderX = player.x + player.facingDir * 0.08f
        val shoulderY = player.y + (if (player.isCrouching) 0.84f else 1.24f)
        val barrelLen = when (slot.weapon) {
            WeaponType.PISTOL -> 0.72f
            WeaponType.ASSAULT_RIFLE -> 1.05f
            WeaponType.SHOTGUN -> 1.02f
            WeaponType.SNIPER_RIFLE -> 1.38f
            WeaponType.MACHINE_GUN -> 1.22f
            WeaponType.ROCKET_LAUNCHER -> 1.26f
        }
        val muzzleX = shoulderX + cos(aimAngleRad) * barrelLen
        val muzzleY = shoulderY + sin(aimAngleRad) * barrelLen

        val spawned = ArrayList<ProjectileState>(spec.pellets)
        for (i in 0 until spec.pellets) {
            val spreadOffset = (rng.nextFloat() - 0.5f) * 2f * spec.spreadRad
            val angle = aimAngleRad + spreadOffset
            spawned.add(
                ProjectileState(
                    id = nextProjectileId++,
                    ownerId = player.id,
                    ownerTeam = player.teamIndex,
                    weaponType = slot.weapon,
                    x = muzzleX,
                    y = muzzleY,
                    z = player.z,
                    vx = cos(angle) * spec.projectileSpeed,
                    vy = sin(angle) * spec.projectileSpeed,
                    damage = spec.damage,
                    radius = spec.projectileRadius,
                    explosionRadius = spec.explosionRadius,
                    maxRange = spec.maxRange,
                    tracerLength = spec.tracerLength,
                    r = spec.colorR,
                    g = spec.colorG,
                    b = spec.colorB
                )
            )
        }

        if (slot.ammoInMag == 0 && slot.reserveAmmo > 0) {
            slot.isReloading = true
            slot.reloadTimer = spec.reloadDurationSec
        }

        return FireResult.Fired(slot.weapon, spawned, muzzleX, muzzleY)
    }

    fun refillAllAmmo(player: PlayerState) {
        for (slot in player.weapons) {
            slot.ammoInMag = slot.weapon.spec.magazineSize
            slot.reserveAmmo = slot.weapon.spec.maxReserve
            slot.isReloading = false
            slot.reloadTimer = 0f
            slot.fireCooldown = 0f
        }
        player.grenades = 3
        player.grenadeCooldown = 0f
    }

    fun addReserveAmmo(player: PlayerState) {
        for (slot in player.weapons) {
            val maxRes = slot.weapon.spec.maxReserve
            slot.reserveAmmo = (slot.reserveAmmo + slot.weapon.spec.magazineSize * 2).coerceAtMost(maxRes)
        }
    }

    private fun completeReload(player: PlayerState) {
        val slot = player.activeWeaponSlot
        val needed = slot.weapon.spec.magazineSize - slot.ammoInMag
        val transfer = needed.coerceAtMost(slot.reserveAmmo)
        slot.ammoInMag += transfer
        slot.reserveAmmo -= transfer
        slot.isReloading = false
        slot.reloadTimer = 0f
    }
}
