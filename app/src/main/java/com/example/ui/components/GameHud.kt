package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.FlagStatus
import com.example.game.model.GameMode
import com.example.game.model.HudSnapshot
import com.example.game.model.WeaponType
import com.example.ui.FloatingHealthTag
import com.example.ui.theme.ArenaBorderMuted
import com.example.ui.theme.ArenaGunmetal
import com.example.ui.theme.ArenaPanelSurface
import com.example.ui.theme.CombatCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VitalityEmerald
import kotlin.math.roundToInt

/**
 * Full-featured shooter HUD displaying Player HP, Jetpack Fuel, Grenade Count,
 * Weapon & Ammo, Team / CTF / Point Control objective banners, 6-Weapon Quick Selector,
 * Minimap Radar, Kill Feed, Floating Soldier Bars, and Pause button.
 */
@Composable
fun GameHudOverlay(
    hud: HudSnapshot,
    floatingTags: List<FloatingHealthTag>,
    onPauseClick: () -> Unit,
    onSelectWeapon: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Damage screen-edge vignette
        if (hud.damageVignetteAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                CombatCrimson.copy(alpha = hud.damageVignetteAlpha * 0.55f)
                            )
                        )
                    )
            )
        }

        // 2. Floating world-projected health bars over players
        for (tag in floatingTags) {
            val tagWidthPx = with(density) { 68.dp.toPx() }
            val tagHeightPx = with(density) { 26.dp.toPx() }
            val barColor = when {
                tag.isLocal -> CyberCyan
                hud.gameMode != GameMode.DEATHMATCH && tag.teamIndex == 0 -> Color(0xFF38BDF8)
                hud.gameMode != GameMode.DEATHMATCH && tag.teamIndex == 1 -> CombatCrimson
                tag.teamIndex == 1 -> CombatCrimson
                tag.teamIndex == 2 -> TacticalAmber
                else -> Color(0xFFA855F7)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (tag.screenX - tagWidthPx * 0.5f).roundToInt(),
                            (tag.screenY - tagHeightPx).roundToInt()
                        )
                    }
                    .width(68.dp)
            ) {
                Text(
                    text = tag.callsign,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = barColor
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ArenaGunmetal.copy(alpha = 0.88f))
                        .border(0.7.dp, Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(tag.healthFraction)
                            .fillMaxHeight()
                            .background(barColor)
                    )
                }
            }
        }

        // 3. Hitmarker reticle pop
        if (hud.hitMarkerAlpha > 0.04f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val col = TacticalAmber.copy(alpha = hud.hitMarkerAlpha)
                    val s = size.minDimension
                    drawLine(col, Offset(0f, 0f), Offset(s * 0.34f, s * 0.34f), strokeWidth = 3.dp.toPx())
                    drawLine(col, Offset(s, 0f), Offset(s * 0.66f, s * 0.34f), strokeWidth = 3.dp.toPx())
                    drawLine(col, Offset(0f, s), Offset(s * 0.34f, s * 0.66f), strokeWidth = 3.dp.toPx())
                    drawLine(col, Offset(s, s), Offset(s * 0.66f, s * 0.66f), strokeWidth = 3.dp.toPx())
                }
            }
        }

        // 4. Top HUD Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: HP + Jetpack Fuel + Active Weapon
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                HealthAndFuelPanel(
                    health = hud.localHealth,
                    maxHealth = hud.localMaxHealth,
                    jetpackFuel = hud.localJetpackFuel
                )
                WeaponStatusCard(hud = hud)
            }

            // Center: Mode Objective / Timer + Quick Weapon Strip
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                MatchTimerAndScorePill(hud = hud)
                if (hud.gameMode == GameMode.CAPTURE_THE_FLAG) {
                    CtfStatusBanner(hud = hud)
                } else if (hud.gameMode == GameMode.POINT_CONTROL) {
                    PointControlBanner(hud = hud)
                }
                WeaponQuickSelectorStrip(
                    selected = hud.currentWeapon,
                    onSelect = onSelectWeapon
                )
            }

            // Right: Minimap + Pause + Kill Feed
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hud.showMinimap) {
                        TacticalMinimap(hud = hud)
                    }
                    IconButton(
                        onClick = onPauseClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ArenaPanelSurface.copy(alpha = 0.86f))
                            .border(1.dp, ArenaBorderMuted, RoundedCornerShape(10.dp))
                            .testTag("pause_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause game",
                            tint = TextPrimary
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    hud.killFeed.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ArenaGunmetal.copy(alpha = 0.80f))
                                .border(0.8.dp, ArenaBorderMuted.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.killerName,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TacticalAmber
                            )
                            Text(
                                text = "[${entry.weaponShort}]",
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 9.sp,
                                color = TextSecondary
                            )
                            if (entry.victimName.isNotEmpty()) {
                                Text(
                                    text = entry.victimName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CombatCrimson
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Respawn countdown banner when eliminated
        if (!hud.localIsAlive) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ArenaGunmetal.copy(alpha = 0.90f))
                    .border(1.5.dp, CombatCrimson, RoundedCornerShape(14.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ELIMINATED",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CombatCrimson
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "RESPAWNING IN ${String.format("%.1f", hud.respawnSecondsLeft.coerceAtLeast(0f))}s",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthAndFuelPanel(health: Float, maxHealth: Float, jetpackFuel: Float) {
    val frac = (health / maxHealth).coerceIn(0f, 1f)
    val hpColor = when {
        frac > 0.55f -> VitalityEmerald
        frac > 0.28f -> TacticalAmber
        else -> CombatCrimson
    }
    val fuelFrac = (jetpackFuel / 100f).coerceIn(0f, 1f)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ArenaPanelSurface.copy(alpha = 0.85f))
            .border(1.dp, ArenaBorderMuted, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, contentDescription = "Player health", tint = hpColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.width(126.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("HP", style = MaterialTheme.typography.labelMedium, fontSize = 9.sp, color = TextSecondary)
                    Text("${health.roundToInt()} / ${maxHealth.roundToInt()}", style = MaterialTheme.typography.labelLarge, fontSize = 11.sp, color = TextPrimary)
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ArenaGunmetal)
                ) {
                    Box(modifier = Modifier.fillMaxWidth(frac).fillMaxHeight().background(hpColor))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.RocketLaunch, contentDescription = "Jetpack fuel", tint = TacticalAmber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.width(126.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("JETPACK", style = MaterialTheme.typography.labelMedium, fontSize = 9.sp, color = TextSecondary)
                    Text("${jetpackFuel.roundToInt()}%", style = MaterialTheme.typography.labelLarge, fontSize = 10.sp, color = TacticalAmber)
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ArenaGunmetal)
                ) {
                    Box(modifier = Modifier.fillMaxWidth(fuelFrac).fillMaxHeight().background(TacticalAmber))
                }
            }
        }
    }
}

@Composable
private fun WeaponStatusCard(hud: HudSnapshot) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ArenaPanelSurface.copy(alpha = 0.85f))
            .border(1.dp, TacticalAmber.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = hud.currentWeapon.spec.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 11.sp,
                color = TacticalAmber
            )
            Text(
                text = "${hud.ammoInMag}/${hud.reserveAmmo}",
                style = MaterialTheme.typography.labelLarge,
                fontSize = 13.sp,
                color = if (hud.ammoInMag <= 2) CombatCrimson else TextPrimary
            )
            Text(
                text = "G:${hud.localGrenades}",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 10.sp,
                color = VitalityEmerald
            )
        }
        if (hud.isReloading) {
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { hud.reloadProgress },
                modifier = Modifier
                    .width(148.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = CyberCyan,
                trackColor = ArenaGunmetal
            )
            Text("RELOADING...", style = MaterialTheme.typography.labelMedium, fontSize = 9.sp, color = CyberCyan)
        }
    }
}

@Composable
private fun MatchTimerAndScorePill(hud: HudSnapshot) {
    val totalSec = hud.matchSecondsLeft.roundToInt().coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    val timerText = if (hud.isUnlimitedTime) "∞" else String.format("%02d:%02d", mins, secs)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ArenaPanelSurface.copy(alpha = 0.88f))
            .border(1.dp, ArenaBorderMuted, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        if (hud.gameMode != GameMode.DEATHMATCH) {
            Text(
                text = "BLUE ${hud.blueTeamScore}",
                style = MaterialTheme.typography.labelLarge,
                fontSize = 12.sp,
                color = CyberCyan
            )
        }
        Text(
            text = timerText,
            style = MaterialTheme.typography.titleLarge,
            fontSize = 15.sp,
            color = if (!hud.isUnlimitedTime && totalSec <= 15) CombatCrimson else TextPrimary
        )
        if (hud.gameMode != GameMode.DEATHMATCH) {
            Text(
                text = "RED ${hud.redTeamScore}",
                style = MaterialTheme.typography.labelLarge,
                fontSize = 12.sp,
                color = CombatCrimson
            )
        } else {
            Text(
                text = "K ${hud.localKills} • D ${hud.localDeaths}",
                style = MaterialTheme.typography.labelLarge,
                fontSize = 11.sp,
                color = TacticalAmber
            )
        }
        Text(
            text = "${hud.fps} FPS",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 10.sp,
            color = VitalityEmerald
        )
    }
}

@Composable
private fun CtfStatusBanner(hud: HudSnapshot) {
    fun statusText(status: FlagStatus, carrier: String?): String = when (status) {
        FlagStatus.AT_BASE -> "BASE"
        FlagStatus.CARRIED -> carrier ?: "CARRIED"
        FlagStatus.DROPPED -> "DROPPED"
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ArenaGunmetal.copy(alpha = 0.85f))
            .border(1.dp, ArenaBorderMuted, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(13.dp))
            Text(statusText(hud.blueFlagStatus, hud.blueFlagCarrier), style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = CyberCyan)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = CombatCrimson, modifier = Modifier.size(13.dp))
            Text(statusText(hud.redFlagStatus, hud.redFlagCarrier), style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = CombatCrimson)
        }
    }
}

@Composable
private fun PointControlBanner(hud: HudSnapshot) {
    val (label, col) = when {
        hud.controlPointContested -> "CONTESTED!" to TacticalAmber
        hud.controlPointOwner == 0 -> "BLUE CONTROLS POINT" to CyberCyan
        hud.controlPointOwner == 1 -> "RED CONTROLS POINT" to CombatCrimson
        else -> "NEUTRAL CONTROL ZONE" to TextSecondary
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ArenaGunmetal.copy(alpha = 0.85f))
            .border(1.dp, col.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = col)
    }
}

@Composable
private fun WeaponQuickSelectorStrip(
    selected: WeaponType,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WeaponType.entries.forEachIndexed { idx, w ->
            val isSelected = w == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (isSelected) TacticalAmber.copy(alpha = 0.28f)
                        else ArenaGunmetal.copy(alpha = 0.74f)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 0.8.dp,
                        color = if (isSelected) TacticalAmber else ArenaBorderMuted,
                        shape = RoundedCornerShape(7.dp)
                    )
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("weapon_chip_${w.spec.id}")
            ) {
                Text(
                    text = "${idx + 1} ${w.spec.shortLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) TacticalAmber else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun TacticalMinimap(hud: HudSnapshot) {
    Box(
        modifier = Modifier
            .width(126.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ArenaGunmetal.copy(alpha = 0.85f))
            .border(1.dp, ArenaBorderMuted, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = ArenaBorderMuted,
                start = Offset(0f, size.height * 0.92f),
                end = Offset(size.width, size.height * 0.92f),
                strokeWidth = 1.5.dp.toPx()
            )
            drawRect(
                color = ArenaBorderMuted.copy(alpha = 0.55f),
                topLeft = Offset(size.width * 0.42f, size.height * 0.65f),
                size = Size(size.width * 0.16f, 2.dp.toPx())
            )
            drawRect(
                color = ArenaBorderMuted.copy(alpha = 0.55f),
                topLeft = Offset(size.width * 0.20f, size.height * 0.62f),
                size = Size(size.width * 0.12f, 2.dp.toPx())
            )
            drawRect(
                color = ArenaBorderMuted.copy(alpha = 0.55f),
                topLeft = Offset(size.width * 0.65f, size.height * 0.66f),
                size = Size(size.width * 0.14f, 2.dp.toPx())
            )
            for (dot in hud.minimapDots) {
                if (!dot.isAlive) continue
                val cx = dot.nx * size.width
                val cy = (1f - dot.ny) * size.height * 0.88f + size.height * 0.04f
                val col = when {
                    dot.isLocal -> CyberCyan
                    hud.gameMode != GameMode.DEATHMATCH && dot.teamIndex == 0 -> Color(0xFF60A5FA)
                    else -> CombatCrimson
                }
                drawCircle(
                    color = col,
                    radius = if (dot.isLocal) 4.dp.toPx() else 3.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }
    }
}
