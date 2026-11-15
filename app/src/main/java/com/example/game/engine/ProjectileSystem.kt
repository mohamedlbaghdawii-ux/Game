package com.example.game.engine

import com.example.game.model.ExplosionEffect
import com.example.game.model.GrenadeState
import com.example.game.model.ParticleState
import com.example.game.model.PlayerId
import com.example.game.model.PlayerState
import com.example.game.model.ProjectileState
import com.example.game.model.WeaponType
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

data class DamageEvent(
    val attackerId: PlayerId?,
    val victimId: PlayerId,
    val hitX: Float,
    val hitY: Float,
    val damage: Float,
    val weaponShort: String
)

/**
 * Simulates projectiles, rockets, bouncing grenades, explosions, and 3D particle effects.
 */
class ProjectileSystem(private val rng: Random = Random.Default) {

    val projectiles = mutableListOf<ProjectileState>()
    val grenades = mutableListOf<GrenadeState>()
    val explosions = mutableListOf<ExplosionEffect>()
    val particles = mutableListOf<ParticleState>()

    var particleScale: Float = 1f

    fun clear() {
        projectiles.clear()
        grenades.clear()
        explosions.clear()
        particles.clear()
    }

    fun spawnProjectiles(list: List<ProjectileState>) {
        projectiles.addAll(list)
    }

    fun spawnGrenade(g: GrenadeState) {
        grenades.add(g)
    }

    fun spawnMuzzleParticles(x: Float, y: Float, z: Float, aimAngleRad: Float, weapon: WeaponType) {
        val baseCount = if (weapon == WeaponType.SHOTGUN) 8 else 4
        val count = (baseCount * particleScale).toInt().coerceAtLeast(1)
        val cosA = cos(aimAngleRad)
        val sinA = sin(aimAngleRad)
        for (i in 0 until count) {
            val spread = (rng.nextFloat() - 0.5f) * 0.8f
            val speed = 4f + rng.nextFloat() * 7f
            val vx = (cosA * cos(spread) - sinA * sin(spread)) * speed
            val vy = (sinA * cos(spread) + cosA * sin(spread)) * speed
            particles.add(
                ParticleState(
                    x = x, y = y, z = z + (rng.nextFloat() - 0.5f) * 0.16f,
                    vx = vx, vy = vy, vz = (rng.nextFloat() - 0.5f) * 2.5f,
                    size = 0.08f + rng.nextFloat() * 0.07f,
                    life = 0.12f + rng.nextFloat() * 0.10f,
                    maxLife = 0.22f,
                    r = weapon.spec.colorR, g = weapon.spec.colorG, b = weapon.spec.colorB,
                    gravityScale = 0.2f
                )
            )
        }
        if (weapon != WeaponType.ROCKET_LAUNCHER && particleScale > 0.5f) {
            particles.add(
                ParticleState(
                    x = x - cosA * 0.35f, y = y - sinA * 0.35f, z = z + 0.14f,
                    vx = -cosA * (1.2f + rng.nextFloat() * 1.4f),
                    vy = 2.6f + rng.nextFloat() * 1.5f,
                    vz = 0.8f + rng.nextFloat() * 1.2f,
                    size = 0.06f, life = 0.5f, maxLife = 0.5f,
                    r = 0.92f, g = 0.78f, b = 0.25f, gravityScale = 1.1f
                )
            )
        }
    }

    fun spawnJetpackFlame(x: Float, y: Float, z: Float) {
        val count = (3 * particleScale).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            particles.add(
                ParticleState(
                    x = x + (rng.nextFloat() - 0.5f) * 0.22f,
                    y = y,
                    z = z + (rng.nextFloat() - 0.5f) * 0.18f,
                    vx = (rng.nextFloat() - 0.5f) * 1.6f,
                    vy = -3.8f - rng.nextFloat() * 2.8f,
                    vz = (rng.nextFloat() - 0.5f) * 1.2f,
                    size = 0.14f + rng.nextFloat() * 0.08f,
                    life = 0.18f + rng.nextFloat() * 0.12f,
                    maxLife = 0.30f,
                    r = 1.0f, g = 0.58f + rng.nextFloat() * 0.32f, b = 0.12f,
                    gravityScale = -0.1f
                )
            )
        }
    }

    fun spawnHitSparks(x: Float, y: Float, z: Float, isPlayerHit: Boolean) {
        val count = ((if (isPlayerHit) 8 else 5) * particleScale).toInt().coerceAtLeast(2)
        for (i in 0 until count) {
            val angle = rng.nextFloat() * 6.28318f
            val spd = 2.5f + rng.nextFloat() * 6.5f
            particles.add(
                ParticleState(
                    x = x, y = y, z = z + (rng.nextFloat() - 0.5f) * 0.25f,
                    vx = cos(angle) * spd, vy = sin(angle) * spd + 1.5f,
                    vz = (rng.nextFloat() - 0.5f) * 3.5f,
                    size = 0.08f + rng.nextFloat() * 0.08f,
                    life = 0.22f + rng.nextFloat() * 0.16f, maxLife = 0.38f,
                    r = if (isPlayerHit) 0.98f else 0.95f,
                    g = if (isPlayerHit) 0.22f else 0.76f,
                    b = if (isPlayerHit) 0.18f else 0.35f,
                    gravityScale = 0.85f
                )
            )
        }
    }

    fun spawnExplosionVisuals(x: Float, y: Float, z: Float, radius: Float) {
        explosions.add(ExplosionEffect(x, y, z, radius, life = 0.38f, maxLife = 0.38f))
        val count = (22 * particleScale).toInt().coerceAtLeast(8)
        for (i in 0 until count) {
            val angle = rng.nextFloat() * 6.28318f
            val spd = 4f + rng.nextFloat() * 11f
            val isSmoke = i % 3 == 0
            particles.add(
                ParticleState(
                    x = x, y = y, z = z + (rng.nextFloat() - 0.5f) * 0.6f,
                    vx = cos(angle) * spd, vy = sin(angle) * spd + 2.5f,
                    vz = (rng.nextFloat() - 0.5f) * 5f,
                    size = if (isSmoke) 0.28f else 0.18f + rng.nextFloat() * 0.12f,
                    life = if (isSmoke) 0.55f else 0.34f,
                    maxLife = 0.55f,
                    r = if (isSmoke) 0.35f else 1.0f,
                    g = if (isSmoke) 0.35f else 0.48f + rng.nextFloat() * 0.35f,
                    b = if (isSmoke) 0.38f else 0.10f,
                    gravityScale = if (isSmoke) -0.2f else 0.65f
                )
            )
        }
    }

    fun spawnDustPuff(x: Float, y: Float, z: Float) {
        val count = (4 * particleScale).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            particles.add(
                ParticleState(
                    x = x + (rng.nextFloat() - 0.5f) * 0.4f, y = y + 0.08f,
                    z = z + (rng.nextFloat() - 0.5f) * 0.3f,
                    vx = (rng.nextFloat() - 0.5f) * 2.6f, vy = 0.8f + rng.nextFloat() * 1.4f,
                    vz = (rng.nextFloat() - 0.5f) * 1.6f,
                    size = 0.14f + rng.nextFloat() * 0.08f,
                    life = 0.26f, maxLife = 0.26f,
                    r = 0.78f, g = 0.78f, b = 0.76f, gravityScale = -0.15f
                )
            )
        }
    }

    fun step(
        dt: Float,
        players: Collection<PlayerState>,
        collisionSystem: CollisionSystem,
        canDamage: (ownerId: PlayerId, victim: PlayerState) -> Boolean,
        onDamage: (DamageEvent) -> Unit,
        onExplosion: (x: Float, y: Float) -> Unit
    ) {
        // 1. Projectiles (including rockets)
        val pIter = projectiles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            if (!p.active) { pIter.remove(); continue }

            // Rocket smoke trail
            if (p.weaponType == WeaponType.ROCKET_LAUNCHER && particleScale > 0.4f) {
                particles.add(
                    ParticleState(
                        x = p.x, y = p.y, z = p.z,
                        vx = (rng.nextFloat() - 0.5f) * 0.8f, vy = 0.5f, vz = 0f,
                        size = 0.16f, life = 0.25f, maxLife = 0.25f,
                        r = 0.75f, g = 0.75f, b = 0.78f, gravityScale = -0.15f
                    )
                )
            }

            val nextX = p.x + p.vx * dt
            val nextY = p.y + p.vy * dt
            val stepDist = hypot(nextX - p.x, nextY - p.y)

            val worldHit = collisionSystem.sweepSegmentVsWorld(p.x, p.y, nextX, nextY, p.radius)
            var earliestT = worldHit?.t ?: Float.MAX_VALUE
            var hitPlayer: PlayerState? = null
            var hitPlayerPos: RayHit? = null

            for (candidate in players) {
                if (candidate.id == p.ownerId || !candidate.isAlive || candidate.spawnProtectTimer > 0f) continue
                if (!canDamage(p.ownerId, candidate)) continue
                val pHit = collisionSystem.sweepSegmentVsPlayer(p.x, p.y, nextX, nextY, p.radius, candidate)
                if (pHit != null && pHit.t < earliestT) {
                    earliestT = pHit.t
                    hitPlayer = candidate
                    hitPlayerPos = pHit
                }
            }

            if (hitPlayer != null && hitPlayerPos != null) {
                p.active = false
                if (p.explosionRadius > 0f) {
                    triggerExplosion(
                        hitPlayerPos.hitX, hitPlayerPos.hitY, p.z,
                        p.explosionRadius, p.damage, p.ownerId, p.weaponType.spec.shortLabel,
                        players, canDamage, onDamage, onExplosion
                    )
                } else {
                    spawnHitSparks(hitPlayerPos.hitX, hitPlayerPos.hitY, hitPlayer.z, true)
                    onDamage(DamageEvent(p.ownerId, hitPlayer.id, hitPlayerPos.hitX, hitPlayerPos.hitY, p.damage, p.weaponType.spec.shortLabel))
                }
                pIter.remove()
                continue
            }

            if (worldHit != null) {
                p.active = false
                if (p.explosionRadius > 0f) {
                    triggerExplosion(
                        worldHit.hitX, worldHit.hitY, p.z,
                        p.explosionRadius, p.damage, p.ownerId, p.weaponType.spec.shortLabel,
                        players, canDamage, onDamage, onExplosion
                    )
                } else {
                    spawnHitSparks(worldHit.hitX, worldHit.hitY, p.z, false)
                }
                pIter.remove()
                continue
            }

            p.x = nextX
            p.y = nextY
            p.distanceTraveled += stepDist
            if (p.distanceTraveled >= p.maxRange) {
                p.active = false
                pIter.remove()
            }
        }

        // 2. Bouncing Grenades
        val gIter = grenades.iterator()
        while (gIter.hasNext()) {
            val g = gIter.next()
            if (!g.active) { gIter.remove(); continue }
            collisionSystem.stepBouncingGrenade(g, dt)
            g.fuseTimer -= dt
            if (g.fuseTimer <= 0f) {
                g.active = false
                triggerExplosion(
                    g.x, g.y, g.z,
                    radius = 4.6f, maxDamage = 92f,
                    ownerId = g.ownerId, weaponShort = "FRAG",
                    players = players, canDamage = canDamage,
                    onDamage = onDamage, onExplosion = onExplosion
                )
                gIter.remove()
            }
        }

        // 3. Explosion spheres
        val eIter = explosions.iterator()
        while (eIter.hasNext()) {
            val ex = eIter.next()
            ex.life -= dt
            if (ex.life <= 0f) eIter.remove()
        }

        // 4. Particles
        val ptIter = particles.iterator()
        while (ptIter.hasNext()) {
            val pt = ptIter.next()
            pt.life -= dt
            if (pt.life <= 0f) { ptIter.remove(); continue }
            pt.vy -= 18f * pt.gravityScale * dt
            pt.x += pt.vx * dt
            pt.y += pt.vy * dt
            pt.z += pt.vz * dt
        }
    }

    private fun triggerExplosion(
        ex: Float, ey: Float, ez: Float,
        radius: Float, maxDamage: Float,
        ownerId: PlayerId, weaponShort: String,
        players: Collection<PlayerState>,
        canDamage: (PlayerId, PlayerState) -> Boolean,
        onDamage: (DamageEvent) -> Unit,
        onExplosion: (Float, Float) -> Unit
    ) {
        spawnExplosionVisuals(ex, ey, ez, radius)
        onExplosion(ex, ey)
        for (target in players) {
            if (!target.isAlive || target.spawnProtectTimer > 0f) continue
            if (target.id != ownerId && !canDamage(ownerId, target)) continue
            val dist = hypot(target.x - ex, (target.y + target.effectiveHeight * 0.5f) - ey)
            if (dist <= radius) {
                val falloff = (1f - dist / radius).coerceIn(0.2f, 1f)
                val selfScale = if (target.id == ownerId) 0.45f else 1f
                onDamage(DamageEvent(ownerId, target.id, target.x, target.y + 0.9f, maxDamage * falloff * selfScale, weaponShort))
            }
        }
    }
}
