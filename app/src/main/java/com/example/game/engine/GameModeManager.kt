package com.example.game.engine

import com.example.game.model.ControlPointState
import com.example.game.model.FlagState
import com.example.game.model.FlagStatus
import com.example.game.model.GameMode
import com.example.game.model.MatchConfig
import com.example.game.model.PlayerId
import com.example.game.model.PlayerState
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Manages rules, team scoring, Capture The Flag bases/carriers, and Point Control capture zones
 * for all 4 game modes (Deathmatch, Team Deathmatch, Capture The Flag, Point Control).
 */
class GameModeManager {

    var blueTeamScore: Int = 0
        private set
    var redTeamScore: Int = 0
        private set

    lateinit var blueFlag: FlagState
        private set
    lateinit var redFlag: FlagState
        private set
    lateinit var controlPoint: ControlPointState
        private set

    private var controlPointTickAccum = 0f

    fun resetForMatch(mapManager: MapManager) {
        blueTeamScore = 0
        redTeamScore = 0
        controlPointTickAccum = 0f
        blueFlag = FlagState(
            teamIndex = 0,
            baseX = mapManager.blueFlagBase.first,
            baseY = mapManager.blueFlagBase.second
        )
        redFlag = FlagState(
            teamIndex = 1,
            baseX = mapManager.redFlagBase.first,
            baseY = mapManager.redFlagBase.second
        )
        controlPoint = ControlPointState(
            x = mapManager.controlZonePos.first,
            y = mapManager.controlZonePos.second
        )
    }

    fun isFriendlyFire(config: MatchConfig, a: PlayerState, b: PlayerState): Boolean {
        if (config.gameMode == GameMode.DEATHMATCH) return false
        return a.teamIndex == b.teamIndex
    }

    fun onPlayerEliminated(
        config: MatchConfig,
        attacker: PlayerState?,
        victim: PlayerState
    ) {
        if (attacker != null && attacker.id != victim.id) {
            attacker.kills += 1
            if (config.gameMode == GameMode.DEATHMATCH || config.gameMode == GameMode.TEAM_DEATHMATCH) {
                attacker.score += 1
            }
            if (config.gameMode == GameMode.TEAM_DEATHMATCH) {
                if (attacker.teamIndex == 0) blueTeamScore += 1 else redTeamScore += 1
            }
        }

        // Drop carried flag if victim was carrying in CTF
        if (config.gameMode == GameMode.CAPTURE_THE_FLAG) {
            if (blueFlag.carrierId == victim.id) {
                blueFlag.status = FlagStatus.DROPPED
                blueFlag.carrierId = null
                blueFlag.x = victim.x
                blueFlag.y = victim.y + 0.2f
                blueFlag.dropTimer = 14f
            }
            if (redFlag.carrierId == victim.id) {
                redFlag.status = FlagStatus.DROPPED
                redFlag.carrierId = null
                redFlag.x = victim.x
                redFlag.y = victim.y + 0.2f
                redFlag.dropTimer = 14f
            }
        }
    }

    fun stepModeObjectives(
        config: MatchConfig,
        players: Map<PlayerId, PlayerState>,
        dt: Float,
        onFlagEvent: (String) -> Unit
    ) {
        when (config.gameMode) {
            GameMode.CAPTURE_THE_FLAG -> stepCtf(players, dt, onFlagEvent)
            GameMode.POINT_CONTROL -> stepPointControl(players, dt)
            else -> Unit
        }
    }

    private fun stepCtf(
        players: Map<PlayerId, PlayerState>,
        dt: Float,
        onFlagEvent: (String) -> Unit
    ) {
        val flags = listOf(blueFlag, redFlag)
        for (flag in flags) {
            when (flag.status) {
                FlagStatus.CARRIED -> {
                    val carrier = flag.carrierId?.let { players[it] }
                    if (carrier == null || !carrier.isAlive) {
                        flag.status = FlagStatus.DROPPED
                        flag.carrierId = null
                        flag.dropTimer = 14f
                    } else {
                        flag.x = carrier.x
                        flag.y = carrier.y + 1.4f
                    }
                }
                FlagStatus.DROPPED -> {
                    flag.dropTimer -= dt
                    if (flag.dropTimer <= 0f) {
                        flag.x = flag.baseX
                        flag.y = flag.baseY
                        flag.status = FlagStatus.AT_BASE
                        onFlagEvent("FLAG RETURNED")
                    }
                }
                FlagStatus.AT_BASE -> {
                    flag.x = flag.baseX
                    flag.y = flag.baseY
                }
            }
        }

        for (p in players.values) {
            if (!p.isAlive) continue
            val enemyFlag = if (p.teamIndex == 0) redFlag else blueFlag
            val ownFlag = if (p.teamIndex == 0) blueFlag else redFlag

            // Steal enemy flag if at base or dropped
            if (enemyFlag.status != FlagStatus.CARRIED &&
                hypot(p.x - enemyFlag.x, (p.y + 0.9f) - enemyFlag.y) < 1.35f
            ) {
                enemyFlag.status = FlagStatus.CARRIED
                enemyFlag.carrierId = p.id
                onFlagEvent("${p.callsign} TOOK FLAG")
            }

            // Return own dropped flag on touch
            if (ownFlag.status == FlagStatus.DROPPED &&
                hypot(p.x - ownFlag.x, (p.y + 0.9f) - ownFlag.y) < 1.35f
            ) {
                ownFlag.status = FlagStatus.AT_BASE
                ownFlag.x = ownFlag.baseX
                ownFlag.y = ownFlag.baseY
                onFlagEvent("${p.callsign} RETURNED FLAG")
            }

            // Score capture if carrying enemy flag and touching own base while own flag is at base
            if (enemyFlag.status == FlagStatus.CARRIED &&
                enemyFlag.carrierId == p.id &&
                ownFlag.status == FlagStatus.AT_BASE &&
                hypot(p.x - ownFlag.baseX, (p.y + 0.9f) - ownFlag.baseY) < 1.5f
            ) {
                enemyFlag.status = FlagStatus.AT_BASE
                enemyFlag.carrierId = null
                enemyFlag.x = enemyFlag.baseX
                enemyFlag.y = enemyFlag.baseY
                p.score += 5
                if (p.teamIndex == 0) blueTeamScore += 1 else redTeamScore += 1
                onFlagEvent("${p.callsign} SCORED FLAG!")
            }
        }
    }

    private fun stepPointControl(players: Map<PlayerId, PlayerState>, dt: Float) {
        var blueInside = 0
        var redInside = 0
        for (p in players.values) {
            if (!p.isAlive) continue
            if (abs(p.x - controlPoint.x) <= controlPoint.radius &&
                abs((p.y + 0.8f) - controlPoint.y) <= 2.2f
            ) {
                if (p.teamIndex == 0) blueInside++ else redInside++
            }
        }

        controlPoint.contested = blueInside > 0 && redInside > 0
        if (!controlPoint.contested) {
            if (blueInside > 0) {
                controlPoint.captureProgress = (controlPoint.captureProgress + dt * 0.45f * blueInside).coerceAtMost(1f)
                if (controlPoint.captureProgress >= 0.85f) controlPoint.ownerTeam = 0
            } else if (redInside > 0) {
                controlPoint.captureProgress = (controlPoint.captureProgress - dt * 0.45f * redInside).coerceAtLeast(-1f)
                if (controlPoint.captureProgress <= -0.85f) controlPoint.ownerTeam = 1
            }
        }

        if (controlPoint.ownerTeam != -1 && !controlPoint.contested) {
            controlPointTickAccum += dt
            if (controlPointTickAccum >= 1.0f) {
                controlPointTickAccum -= 1.0f
                if (controlPoint.ownerTeam == 0) blueTeamScore += 1 else redTeamScore += 1
            }
        }
    }

    fun checkScoreWinner(config: MatchConfig, players: Collection<PlayerState>): String? {
        val limit = config.scoreLimit
        if (limit <= 0) return null
        return when (config.gameMode) {
            GameMode.DEATHMATCH -> {
                val top = players.maxByOrNull { it.kills }
                if (top != null && top.kills >= limit) top.callsign else null
            }
            GameMode.TEAM_DEATHMATCH, GameMode.POINT_CONTROL -> {
                when {
                    blueTeamScore >= limit -> "BLUE TEAM"
                    redTeamScore >= limit -> "RED TEAM"
                    else -> null
                }
            }
            GameMode.CAPTURE_THE_FLAG -> {
                val flagLimit = (limit / 5).coerceIn(3, 10)
                when {
                    blueTeamScore >= flagLimit -> "BLUE TEAM"
                    redTeamScore >= flagLimit -> "RED TEAM"
                    else -> null
                }
            }
        }
    }
}
