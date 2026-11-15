package com.example.game.engine

import com.example.game.model.GrenadeState
import com.example.game.model.PlayerState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class RayHit(
    val t: Float,
    val hitX: Float,
    val hitY: Float
)

/**
 * Deterministic AABB, swept segment, crouching hitbox, and bouncing grenade physics collision system.
 */
class CollisionSystem(private val map: MapManager) {

    fun resolvePlayerMovement(player: PlayerState, dt: Float) {
        val prevBottom = player.y
        val halfW = player.width * 0.5f
        val pHeight = player.effectiveHeight

        player.x += player.vx * dt
        if (player.x - halfW < map.minX) {
            player.x = map.minX + halfW
            player.vx = 0f
        } else if (player.x + halfW > map.maxX) {
            player.x = map.maxX - halfW
            player.vx = 0f
        }

        for (prop in map.solidColliders) {
            if (prop.isOneWayPlatform) continue
            if (overlapsY(player.y, player.y + pHeight, prop.minY + 0.04f, prop.maxY - 0.04f)) {
                if (overlapsX(player.x - halfW, player.x + halfW, prop.minX, prop.maxX)) {
                    if (player.vx > 0f) {
                        player.x = prop.minX - halfW
                    } else if (player.vx < 0f) {
                        player.x = prop.maxX + halfW
                    }
                    player.vx = 0f
                }
            }
        }

        player.y += player.vy * dt
        var grounded = false

        for (prop in map.solidColliders) {
            if (!overlapsX(player.x - halfW + 0.03f, player.x + halfW - 0.03f, prop.minX, prop.maxX)) {
                continue
            }

            if (prop.isOneWayPlatform) {
                if (player.vy <= 0f && prevBottom >= prop.maxY - 0.22f && player.y <= prop.maxY) {
                    player.y = prop.maxY
                    player.vy = 0f
                    grounded = true
                }
            } else {
                if (overlapsY(player.y, player.y + pHeight, prop.minY, prop.maxY)) {
                    if (player.vy <= 0f && prevBottom >= prop.maxY - 0.26f) {
                        player.y = prop.maxY
                        player.vy = 0f
                        grounded = true
                    } else if (player.vy > 0f && prevBottom + pHeight <= prop.minY + 0.26f) {
                        player.y = prop.minY - pHeight
                        player.vy = 0f
                    }
                }
            }
        }

        if (player.y < map.minY) {
            player.y = map.minY
            player.vy = 0f
            grounded = true
        }
        if (player.y + pHeight > map.maxY) {
            player.y = map.maxY - pHeight
            if (player.vy > 0f) player.vy = 0f
        }

        player.onGround = grounded
        if (grounded) {
            player.coyoteTimer = 0.11f
            player.jumpsUsed = 0
        } else {
            player.coyoteTimer = max(0f, player.coyoteTimer - dt)
        }
    }

    fun stepBouncingGrenade(g: GrenadeState, dt: Float) {
        val r = 0.16f
        val prevY = g.y
        g.vy -= 26f * dt

        // Horizontal step
        g.x += g.vx * dt
        if (g.x - r < map.minX) {
            g.x = map.minX + r
            g.vx = -g.vx * 0.58f
        } else if (g.x + r > map.maxX) {
            g.x = map.maxX - r
            g.vx = -g.vx * 0.58f
        }
        for (prop in map.solidColliders) {
            if (prop.isOneWayPlatform) continue
            if (overlapsY(g.y - r, g.y + r, prop.minY, prop.maxY) &&
                overlapsX(g.x - r, g.x + r, prop.minX, prop.maxX)
            ) {
                g.x = if (g.vx > 0f) prop.minX - r else prop.maxX + r
                g.vx = -g.vx * 0.58f
            }
        }

        // Vertical step
        g.y += g.vy * dt
        if (g.y - r < map.minY) {
            g.y = map.minY + r
            g.vy = abs(g.vy) * 0.48f
            g.vx *= 0.80f
        }
        for (prop in map.solidColliders) {
            if (!overlapsX(g.x - r, g.x + r, prop.minX, prop.maxX)) continue
            if (prop.isOneWayPlatform) {
                if (g.vy < 0f && prevY - r >= prop.maxY - 0.16f && g.y - r <= prop.maxY) {
                    g.y = prop.maxY + r
                    g.vy = abs(g.vy) * 0.45f
                    g.vx *= 0.82f
                }
            } else if (overlapsY(g.y - r, g.y + r, prop.minY, prop.maxY)) {
                if (g.vy <= 0f) {
                    g.y = prop.maxY + r
                    g.vy = abs(g.vy) * 0.48f
                    g.vx *= 0.82f
                } else {
                    g.y = prop.minY - r
                    g.vy = -abs(g.vy) * 0.45f
                }
            }
        }
    }

    fun sweepSegmentVsWorld(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): RayHit? {
        var bestT = Float.MAX_VALUE
        var bestHit: RayHit? = null

        for (prop in map.solidColliders) {
            if (prop.isOneWayPlatform) continue
            val t = intersectSegmentAabb(
                x0, y0, x1, y1,
                prop.minX - radius, prop.minY - radius,
                prop.maxX + radius, prop.maxY + radius
            )
            if (t != null && t < bestT) {
                bestT = t
                bestHit = RayHit(t, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
            }
        }

        if (x1 < map.minX - 1f || x1 > map.maxX + 1f || y1 < -1f || y1 > map.maxY + 2f) {
            return bestHit ?: RayHit(1f, x1, y1)
        }
        return bestHit
    }

    fun sweepSegmentVsPlayer(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        target: PlayerState
    ): RayHit? {
        if (!target.isAlive) return null
        val halfW = target.width * 0.5f
        val t = intersectSegmentAabb(
            x0, y0, x1, y1,
            target.x - halfW - radius,
            target.y - radius,
            target.x + halfW + radius,
            target.y + target.effectiveHeight + radius
        ) ?: return null
        return RayHit(t, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
    }

    fun hasLineOfSight(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        return sweepSegmentVsWorld(x0, y0, x1, y1, radius = 0.04f) == null
    }

    fun hasSolidAhead(x: Float, y: Float, dir: Float, dist: Float): Boolean {
        val probeX = x + dir * dist
        for (prop in map.solidColliders) {
            if (prop.isOneWayPlatform) continue
            if (probeX >= prop.minX && probeX <= prop.maxX &&
                y + 0.35f >= prop.minY && y + 0.35f <= prop.maxY
            ) {
                return true
            }
        }
        return false
    }

    private fun overlapsX(aMin: Float, aMax: Float, bMin: Float, bMax: Float): Boolean =
        aMax > bMin && aMin < bMax

    private fun overlapsY(aMin: Float, aMax: Float, bMin: Float, bMax: Float): Boolean =
        aMax > bMin && aMin < bMax

    fun intersectSegmentAabb(
        x0: Float, y0: Float, x1: Float, y1: Float,
        minX: Float, minY: Float, maxX: Float, maxY: Float
    ): Float? {
        val dx = x1 - x0
        val dy = y1 - y0
        var tMin = 0f
        var tMax = 1f

        if (abs(dx) < 1e-6f) {
            if (x0 < minX || x0 > maxX) return null
        } else {
            val invD = 1f / dx
            var t1 = (minX - x0) * invD
            var t2 = (maxX - x0) * invD
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            tMin = max(tMin, t1)
            tMax = min(tMax, t2)
            if (tMin > tMax) return null
        }

        if (abs(dy) < 1e-6f) {
            if (y0 < minY || y0 > maxY) return null
        } else {
            val invD = 1f / dy
            var t1 = (minY - y0) * invD
            var t2 = (maxY - y0) * invD
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            tMin = max(tMin, t1)
            tMax = min(tMax, t2)
            if (tMin > tMax) return null
        }

        return tMin
    }
}
