package com.example.game.engine

import com.example.game.model.GameMode
import com.example.game.model.MatchConfig
import com.example.game.model.PlayerId
import com.example.game.model.PlayerProfile
import com.example.game.model.PlayerState

data class MatchOutcome(
    val winnerLabel: String,
    val mvpCallsign: String,
    val localWon: Boolean,
    val earnedCoins: Int,
    val earnedXp: Int
)

/**
 * Evaluates match completion, MVP ("Best Player"), coin/XP rewards, and player level progression.
 */
class MatchManager {

    fun computeOutcome(
        config: MatchConfig,
        players: Collection<PlayerState>,
        localPlayerId: PlayerId,
        modeManager: GameModeManager
    ): MatchOutcome {
        val local = players.firstOrNull { it.id == localPlayerId }
        val mvp = players.maxWithOrNull(
            compareBy<PlayerState> { it.kills }.thenBy { it.score }.thenBy { -it.deaths }
        )
        val mvpLabel = mvp?.let { "${it.callsign} (${it.kills}K)" } ?: "STRIKER"

        val (winnerLabel, localWon) = when (config.gameMode) {
            GameMode.DEATHMATCH -> {
                val top = players.maxWithOrNull(compareBy<PlayerState> { it.kills }.thenBy { -it.deaths })
                val won = top?.id == localPlayerId
                (top?.callsign ?: "STRIKER") to won
            }
            GameMode.TEAM_DEATHMATCH, GameMode.CAPTURE_THE_FLAG, GameMode.POINT_CONTROL -> {
                val blue = modeManager.blueTeamScore
                val red = modeManager.redTeamScore
                val winTeam = when {
                    blue > red -> 0
                    red > blue -> 1
                    else -> -1
                }
                val label = when (winTeam) {
                    0 -> "BLUE TEAM ($blue - $red)"
                    1 -> "RED TEAM ($red - $blue)"
                    else -> "DRAW ($blue - $red)"
                }
                val won = local != null && winTeam == local.teamIndex
                label to won
            }
        }

        val kills = local?.kills ?: 0
        val earnedCoins = 30 + kills * 18 + (if (localWon) 75 else 0)
        val earnedXp = 40 + kills * 25 + (if (localWon) 90 else 0)

        return MatchOutcome(
            winnerLabel = winnerLabel,
            mvpCallsign = mvpLabel,
            localWon = localWon,
            earnedCoins = earnedCoins,
            earnedXp = earnedXp
        )
    }

    fun applyOutcomeToProfile(
        profile: PlayerProfile,
        outcome: MatchOutcome,
        localKills: Int,
        localDeaths: Int
    ): PlayerProfile {
        var xp = profile.xp + outcome.earnedXp
        var level = profile.level
        while (xp >= xpRequiredForLevel(level)) {
            xp -= xpRequiredForLevel(level)
            level += 1
        }
        return profile.copy(
            coins = profile.coins + outcome.earnedCoins,
            xp = xp,
            level = level,
            totalKills = profile.totalKills + localKills,
            totalDeaths = profile.totalDeaths + localDeaths,
            totalWins = profile.totalWins + if (outcome.localWon) 1 else 0,
            matchesPlayed = profile.matchesPlayed + 1
        )
    }

    fun xpRequiredForLevel(level: Int): Int = 150 + (level - 1) * 85
}
