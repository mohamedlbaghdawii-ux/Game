package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.AppLanguage
import com.example.game.model.GameMode
import com.example.game.model.GameSettings
import com.example.game.model.GraphicsQuality
import com.example.game.model.HudPlayerSummary
import com.example.game.model.HudSnapshot
import com.example.ui.LocalizedStrings
import com.example.ui.theme.ArenaBorderMuted
import com.example.ui.theme.ArenaGunmetal
import com.example.ui.theme.ArenaPanelSurface
import com.example.ui.theme.ArenaSlateDark
import com.example.ui.theme.CombatCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VitalityEmerald
import kotlin.math.roundToInt

@Composable
fun SettingsScreenOverlay(
    settings: GameSettings,
    strings: LocalizedStrings,
    onUpdate: (GameSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(ArenaSlateDark.copy(alpha = 0.90f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
            .testTag("settings_screen")
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ArenaGunmetal)
                .border(1.dp, ArenaBorderMuted, RoundedCornerShape(16.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("settings_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                }
                Text(strings.settings, style = MaterialTheme.typography.headlineMedium, color = TacticalAmber)
            }

            SettingSliderRow(
                title = strings.musicVolume,
                valueLabel = "${(settings.musicVolume * 100).roundToInt()}%",
                value = settings.musicVolume,
                range = 0f..1f,
                onValueChange = { onUpdate(settings.copy(musicVolume = it)) }
            )

            SettingSliderRow(
                title = strings.sfxVolume,
                valueLabel = "${(settings.sfxVolume * 100).roundToInt()}%",
                value = settings.sfxVolume,
                range = 0f..1f,
                onValueChange = { onUpdate(settings.copy(sfxVolume = it)) }
            )

            SettingSliderRow(
                title = strings.controlSensitivity,
                valueLabel = String.format("%.2fx", settings.controlSensitivity),
                value = settings.controlSensitivity,
                range = 0.6f..1.6f,
                onValueChange = { onUpdate(settings.copy(controlSensitivity = it)) }
            )

            SettingSliderRow(
                title = strings.cameraSensitivity,
                valueLabel = String.format("%.2fx", settings.cameraSensitivity),
                value = settings.cameraSensitivity,
                range = 0.6f..1.5f,
                onValueChange = { onUpdate(settings.copy(cameraSensitivity = it)) }
            )

            // Graphics Quality
            Text(strings.graphicsQuality, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GraphicsQuality.entries.forEach { q ->
                    val sel = settings.graphicsQuality == q
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) TacticalAmber else ArenaPanelSurface)
                            .border(1.dp, if (sel) TacticalAmber else ArenaBorderMuted, RoundedCornerShape(8.dp))
                            .clickable { onUpdate(settings.copy(graphicsQuality = q)) }
                            .testTag("gfx_${q.name.lowercase()}")
                    ) {
                        Text(q.label, style = MaterialTheme.typography.labelLarge, color = if (sel) ArenaSlateDark else TextPrimary)
                    }
                }
            }

            // Language
            Text(strings.language, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    val sel = settings.language == lang
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) CyberCyan else ArenaPanelSurface)
                            .border(1.dp, if (sel) CyberCyan else ArenaBorderMuted, RoundedCornerShape(8.dp))
                            .clickable { onUpdate(settings.copy(language = lang)) }
                            .testTag("lang_${lang.code}")
                    ) {
                        Text(lang.displayName, style = MaterialTheme.typography.labelLarge, color = if (sel) ArenaSlateDark else TextPrimary)
                    }
                }
            }

            // Vibration & Minimap switches
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(strings.vibration, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Switch(
                    checked = settings.vibrationEnabled,
                    onCheckedChange = { onUpdate(settings.copy(vibrationEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = ArenaSlateDark, checkedTrackColor = VitalityEmerald)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(strings.minimap, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Switch(
                    checked = settings.showMinimap,
                    onCheckedChange = { onUpdate(settings.copy(showMinimap = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = ArenaSlateDark, checkedTrackColor = CyberCyan)
                )
            }
        }
    }
}

@Composable
private fun SettingSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = CyberCyan)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = TacticalAmber, activeTrackColor = TacticalAmber)
        )
    }
}

@Composable
fun PauseOverlay(
    strings: LocalizedStrings,
    isMultiplayer: Boolean,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onSettings: () -> Unit,
    onExitToMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onResume)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(ArenaSlateDark.copy(alpha = 0.80f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ArenaGunmetal)
                .border(1.5.dp, TacticalAmber.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(strings.paused, style = MaterialTheme.typography.headlineMedium, color = TacticalAmber)
            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber, contentColor = ArenaSlateDark),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pause_resume_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.resume, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (!isMultiplayer) {
                OutlinedButton(
                    onClick = onRestart,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pause_restart_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = CyberCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.restartMatch, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                }
            }

            OutlinedButton(
                onClick = onSettings,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pause_settings_button")
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = TextSecondary)
                Spacer(Modifier.width(8.dp))
                Text(strings.settings, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            }

            OutlinedButton(
                onClick = onExitToMenu,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pause_exit_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = CombatCrimson)
                Spacer(Modifier.width(8.dp))
                Text(strings.mainMenu, style = MaterialTheme.typography.labelLarge, color = CombatCrimson)
            }
        }
    }
}

@Composable
fun MatchOverOverlay(
    hud: HudSnapshot,
    strings: LocalizedStrings,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onMainMenu)

    val sorted = hud.players.sortedWith(
        compareByDescending<HudPlayerSummary> { it.kills }.thenBy { it.deaths }
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(ArenaSlateDark.copy(alpha = 0.88f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ArenaGunmetal)
                .border(1.5.dp, TacticalAmber, RoundedCornerShape(16.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = TacticalAmber)
                Text(
                    text = strings.matchComplete,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TacticalAmber
                )
            }

            Text(
                text = "WINNER: ${hud.winnerLabel.ifBlank { sorted.firstOrNull()?.callsign ?: "STRIKER" }}",
                style = MaterialTheme.typography.titleLarge,
                color = VitalityEmerald
            )

            if (hud.gameMode != GameMode.DEATHMATCH) {
                Text(
                    text = "BLUE ${hud.blueTeamScore}  —  RED ${hud.redTeamScore}",
                    style = MaterialTheme.typography.labelLarge,
                    color = CyberCyan
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ArenaPanelSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "MVP: ${hud.mvpLabel}",
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 11.sp,
                    color = TacticalAmber,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "+${hud.lastEarnedCoins} COINS  •  +${hud.lastEarnedXp} XP",
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 11.sp,
                    color = VitalityEmerald
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ArenaPanelSurface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sorted.forEachIndexed { idx, p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!p.isBot) CyberCyan.copy(alpha = 0.14f) else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${idx + 1}  ${p.callsign}",
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 12.sp,
                            color = if (!p.isBot) CyberCyan else TextPrimary
                        )
                        Text(
                            text = "${p.kills} K  /  ${p.deaths} D",
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 11.sp,
                            color = TacticalAmber
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlayAgain,
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber, contentColor = ArenaSlateDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("match_over_play_again")
                ) {
                    Text(strings.playAgain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onMainMenu,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("match_over_menu")
                ) {
                    Text(strings.mainMenu, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                }
            }
        }
    }
}
