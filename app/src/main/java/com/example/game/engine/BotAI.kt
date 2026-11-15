package com.example.game.engine

import com.example.game.model.BotDifficulty
import com.example.game.model.FlagStatus
import com.example.game.model.GameMode
import com.example.game.model.MatchConfig
import com.example.game.model.PlayerId
import com.example.game.model.PlayerInputState
import com.example.game.model.PlayerState
import com.example.game.model.WeaponType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

private enum class BotTactic {
    PATROL,
    OBJECTIVE,
    CHASE,
    COMBAT
}

private data class BotMind(
    var tactic: BotTactic = BotTactic.PATROL,
    var patrolDir: Float = 1f,
    var patrolTimer: Float = 2f,
    var strafeDir: Float = 1f,
    var strafeTimer: Float = 1.1f,
    var jumpCooldown: Float = 0f,
    var jetpackBurstTimer: Float = 0f,
    var grenadeCooldown: Float = 4f,
    var reactionTimer: Float = 0f,
    var burstTimer: Float = 0f,
    var aimOffsetRad: Float = 0f,
    var targetDetected: Boolean = false
)

/**
 * Tactical AI controller supporting up to 11 bots across 4 difficulty tiers (EASY..EXPERT)
 * and all 4 game modes (Deathmatch, Team Deathmatch, Capture The Flag, Point Control).
 */
class BotAI(
    private val map: MapManager,
    private val collisionSystem: CollisionSystem,
    private val modeManager: GameModeManager,
    private val rng: Random = Random.Default
) {
    private val minds = mutableMapOf<PlayerId, BotMind>()

    fun reset() {
        minds.clear()
    }

    fun computeInput(
        bot: PlayerState,
        allPlayers: Collection<PlayerState>,
        config: MatchConfig,
        dt: Float
    ): PlayerInputState {
        if (!bot.isAlive) {
            minds.remove(bot.id)
            return PlayerInputState()
        }

        val difficulty = config.botDifficulty
        val mind = minds.getOrPut(bot.id) {
            BotMind(
                patrolDir = if (rng.nextBoolean()) 1f else -1f,
                patrolTimer = 1.2f + rng.nextFloat() * 2.2f,
                grenadeCooldown = 4f + rng.nextFloat() * 6f
            )
        }

        mind.jumpCooldown = (mind.jumpCooldown - dt).coerceAtLeast(0f)
        mind.jetpackBurstTimer = (mind.jetpackBurstTimer - dt).coerceAtLeast(0f)
        mind.grenadeCooldown = (mind.grenadeCooldown - dt).coerceAtLeast(0f)
        mind.strafeTimer -= dt
        if (mind.strafeTimer <= 0f) {
            mind.strafeDir = if (rng.nextBoolean()) 1f else -1f
            mind.strafeTimer = 0.8f + rng.nextFloat() * 1.2f
        }

        // Determine if bot has an objective goal (CTF flag carrier returning home / stealing flag, or Point Control)
        val objectiveTarget = computeObjectiveTarget(bot, config)
        val enemy = findNearestOpposingPlayer(bot, allPlayers, config)

        if (enemy == null) {
            return if (objectiveTarget != null) {
                moveToObjectiveInput(bot, mind, objectiveTarget.first, objectiveTarget.second, difficulty, dt)
            } else {
                patrolInput(bot, mind, dt)
            }
        }

        val dx = enemy.x - bot.x
        val dy = (enemy.y + enemy.effectiveHeight * 0.55f) - (bot.y + 1.15f)
        val dist = hypot(dx, dy)
        val hasLos = dist < 35f && collisionSystem.hasLineOfSight(
            bot.x, bot.y + 1.25f,
            enemy.x, enemy.y + 1.0f
        )

        if (hasLos || dist < 11f) {
            if (!mind.targetDetected) {
                mind.targetDetected = true
                mind.reactionTimer = difficulty.reactionSec
            }
            mind.tactic = if (hasLos && dist < 25f) BotTactic.COMBAT else BotTactic.CHASE
        } else if (objectiveTarget != null && (dist > 14f || isCarryingFlag(bot))) {
            mind.tactic = BotTactic.OBJECTIVE
        } else if (dist > 34f) {
            mind.targetDetected = false
            mind.tactic = BotTactic.PATROL
        } else if (mind.targetDetected) {
            mind.tactic = BotTactic.CHASE
        } else {
            return patrolInput(bot, mind, dt)
        }

        if (mind.reactionTimer > 0f) {
            mind.reactionTimer = (mind.reactionTimer - dt).coerceAtLeast(0f)
        }

        var moveX = 0f
        var wantJump = false
        var wantJetpack = false
        var wantFire = false
        var wantGrenade = false
        var selectWeapon = -1

        when (mind.tactic) {
            BotTactic.PATROL -> return patrolInput(bot, mind, dt)
            BotTactic.OBJECTIVE -> {
                val obj = objectiveTarget ?: return patrolInput(bot, mind, dt)
                return moveToObjectiveInput(bot, mind, obj.first, obj.second, difficulty, dt)
            }
            BotTactic.CHASE -> {
                moveX = if (dx >= 0f) 0.88f else -0.88f
                if (dy > 2.2f && mind.jumpCooldown <= 0f) {
                    wantJump = true
                    mind.jumpCooldown = 0.9f
                }
                if (dy > 4.2f && bot.jetpackFuel > 35f && rng.nextFloat() < difficulty.jetpackChance * 0.25f) {
                    mind.jetpackBurstTimer = 0.55f
                }
            }
            BotTactic.COMBAT -> {
                val preferredRange = when (bot.activeWeaponSlot.weapon) {
                    WeaponType.SHOTGUN -> 6.5f
                    WeaponType.PISTOL -> 12.0f
                    WeaponType.ASSAULT_RIFLE -> 14.5f
                    WeaponType.MACHINE_GUN -> 14.0f
                    WeaponType.SNIPER_RIFLE -> 22.0f
                    WeaponType.ROCKET_LAUNCHER -> 17.0f
                }
                moveX = when {
                    dist > preferredRange + 3f -> if (dx >= 0f) 0.80f else -0.80f
                    dist < preferredRange - 3.5f -> if (dx >= 0f) -0.68f else 0.68f
                    else -> mind.strafeDir * 0.60f
                }

                if ((dy > 2.4f || (difficulty != BotDifficulty.EASY && rng.nextFloat() < 0.018f)) && mind.jumpCooldown <= 0f) {
                    wantJump = true
                    mind.jumpCooldown = 1.3f + rng.nextFloat() * 0.9f
                }
                if (dy > 4.5f && bot.jetpackFuel > 35f && rng.nextFloat() < difficulty.jetpackChance * 0.05f) {
                    mind.jetpackBurstTimer = 0.45f
                }

                // Weapon selection for range
                if (dist < 7.5f && bot.activeWeaponSlot.weapon == WeaponType.SNIPER_RIFLE) {
                    selectWeapon = WeaponType.SHOTGUN.ordinal
                } else if (dist > 19f && bot.activeWeaponSlot.weapon == WeaponType.SHOTGUN) {
                    selectWeapon = WeaponType.ASSAULT_RIFLE.ordinal
                }

                if (hasLos && mind.reactionTimer <= 0f) {
                    mind.burstTimer -= dt
                    if (mind.burstTimer <= -0.32f) {
                        mind.burstTimer = 0.65f + rng.nextFloat() * 0.7f
                        mind.aimOffsetRad = (rng.nextFloat() - 0.5f) * 2f * difficulty.aimJitterRad
                    }
                    wantFire = mind.burstTimer > 0f || bot.activeWeaponSlot.weapon == WeaponType.ASSAULT_RIFLE || bot.activeWeaponSlot.weapon == WeaponType.MACHINE_GUN
                    if (difficulty >= BotDifficulty.HARD && dist in 7f..18f && mind.grenadeCooldown <= 0f && bot.grenades > 0 && rng.nextFloat() < 0.015f) {
                        wantGrenade = true
                        mind.grenadeCooldown = 8f
                    }
                }
            }
        }

        if (mind.jetpackBurstTimer > 0f && bot.jetpackFuel > 8f) {
            wantJetpack = true
        }

        if (abs(moveX) > 0.1f && mind.jumpCooldown <= 0f) {
            val moveDir = if (moveX > 0f) 1f else -1f
            if (collisionSystem.hasSolidAhead(bot.x, bot.y, moveDir, 1.35f)) {
                wantJump = true
                mind.jumpCooldown = 0.75f
            }
        }

        val aimAngle = atan2(dy, dx) + mind.aimOffsetRad
        return PlayerInputState(
            moveX = moveX,
            jumpPressed = wantJump,
            jetpackHeld = wantJetpack,
            aimAngleRad = aimAngle,
            hasExplicitAim = true,
            isFiring = wantFire,
            grenadeRequested = wantGrenade,
            selectWeaponIndex = selectWeapon
        )
    }

    private fun computeObjectiveTarget(bot: PlayerState, config: MatchConfig): Pair<Float, Float>? {
        return when (config.gameMode) {
            GameMode.CAPTURE_THE_FLAG -> {
                val ownFlag = if (bot.teamIndex == 0) modeManager.blueFlag else modeManager.redFlag
                val enemyFlag = if (bot.teamIndex == 0) modeManager.redFlag else modeManager.blueFlag
                when {
                    enemyFlag.carrierId == bot.id -> ownFlag.baseX to ownFlag.baseY
                    ownFlag.status == FlagStatus.DROPPED -> ownFlag.x to ownFlag.y
                    enemyFlag.status != FlagStatus.CARRIED -> enemyFlag.x to enemyFlag.y
                    else -> null
                }
            }
            GameMode.POINT_CONTROL -> {
                val cp = modeManager.controlPoint
                if (cp.ownerTeam != bot.teamIndex || cp.contested) cp.x to cp.y else null
            }
            else -> null
        }
    }

    private fun isCarryingFlag(bot: PlayerState): Boolean {
        return modeManager.blueFlag.carrierId == bot.id || modeManager.redFlag.carrierId == bot.id
    }

    private fun moveToObjectiveInput(
        bot: PlayerState,
        mind: BotMind,
        tx: Float,
        ty: Float,
        difficulty: BotDifficulty,
        dt: Float
    ): PlayerInputState {
        val dx = tx - bot.x
        val dy = ty - bot.y
        val moveX = if (abs(dx) < 0.7f) 0f else if (dx > 0f) 0.9f else -0.9f
        var wantJump = false
        var wantJetpack = false
        if (dy > 2.2f && mind.jumpCooldown <= 0f) {
            wantJump = true
            mind.jumpCooldown = 0.9f
        }
        if (dy > 4.2f && bot.jetpackFuel > 35f && rng.nextFloat() < difficulty.jetpackChance * 0.08f) {
            mind.jetpackBurstTimer = 0.5f
        }
        if (mind.jetpackBurstTimer > 0f) wantJetpack = true
        if (abs(moveX) > 0.1f && mind.jumpCooldown <= 0f) {
            val dir = if (moveX > 0f) 1f else -1f
            if (collisionSystem.hasSolidAhead(bot.x, bot.y, dir, 1.3f)) {
                wantJump = true
                mind.jumpCooldown = 0.75f
            }
        }
        return PlayerInputState(
            moveX = moveX,
            jumpPressed = wantJump,
            jetpackHeld = wantJetpack,
            aimAngleRad = if (moveX >= 0f) 0f else Math.PI.toFloat(),
            hasExplicitAim = false
        )
    }

    private fun patrolInput(bot: PlayerState, mind: BotMind, dt: Float): PlayerInputState {
        mind.patrolTimer -= dt
        if (mind.patrolTimer <= 0f ||
            bot.x <= map.minX + 3.5f && mind.patrolDir < 0f ||
            bot.x >= map.maxX - 3.5f && mind.patrolDir > 0f
        ) {
            mind.patrolDir = -mind.patrolDir
            mind.patrolTimer = 1.8f + rng.nextFloat() * 2.8f
        }

        var wantJump = false
        if (mind.jumpCooldown <= 0f && collisionSystem.hasSolidAhead(bot.x, bot.y, mind.patrolDir, 1.3f)) {
            wantJump = true
            mind.jumpCooldown = 0.85f
        }

        return PlayerInputState(
            moveX = mind.patrolDir * 0.65f,
            jumpPressed = wantJump,
            aimAngleRad = if (mind.patrolDir >= 0f) 0f else Math.PI.toFloat(),
            hasExplicitAim = false
        )
    }

    private fun findNearestOpposingPlayer(
        bot: PlayerState,
        allPlayers: Collection<PlayerState>,
        config: MatchConfig
    ): PlayerState? {
        var best: PlayerState? = null
        var bestDist = Float.MAX_VALUE
        for (candidate in allPlayers) {
            if (candidate.id == bot.id || !candidate.isAlive) continue
            if (modeManager.isFriendlyFire(config, bot, candidate)) continue
            val d = hypot(candidate.x - bot.x, candidate.y - bot.y) * (if (!candidate.isBot) 0.88f else 1.15f)
            if (d < bestDist) {
                bestDist = d
                best = candidate
            }
        }
        return best
    }
}
