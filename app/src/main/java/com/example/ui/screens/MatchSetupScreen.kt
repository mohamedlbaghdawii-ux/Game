package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.data.ArmoryCatalog
import com.example.game.model.BotDifficulty
import com.example.game.model.GameMode
import com.example.game.model.MapId
import com.example.game.model.MatchConfig
import com.example.game.model.PlayerProfile
import com.example.game.model.WeaponType
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

/**
 * Offline vs Bots Match Setup:
 * 4 Bot Difficulties, 1-11 Bots, 4 Game Modes, 6 Playable Maps with custom preview cards,
 * Duration, Score Limit, Team, and Starting Weapon.
 */
@Composable
fun MatchSetupOverlay(
    config: MatchConfig,
    profile: PlayerProfile,
    strings: LocalizedStrings,
    onUpdateConfig: (MatchConfig) -> Unit,
    onStartMatch: (MatchConfig) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val unlockedWeapons = WeaponType.entries.filter { wp ->
        val armoryId = ArmoryCatalog.items.firstOrNull { it.weaponType == wp }?.id ?: ""
        profile.unlockedItemIds.contains(armoryId) || wp.ordinal <= 3
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ArenaSlateDark.copy(alpha = 0.92f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
            .testTag("offline_setup_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(ArenaGunmetal)
                .border(1.dp, ArenaBorderMuted, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            // Header + Deploy Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("offline_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                    }
                    Text(strings.playOffline, style = MaterialTheme.typography.headlineMedium, color = TacticalAmber)
                }

                Button(
                    onClick = { onStartMatch(config) },
                    colors = ButtonDefaults.buttonColors(containerColor = VitalityEmerald, contentColor = ArenaSlateDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("start_offline_match_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(strings.deployMatch, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 6 MAPS WITH STYLIZED PREVIEW CARDS
                Text(strings.selectMap, style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(MapId.entries) { map ->
                        MapPreviewCard(
                            mapId = map,
                            selected = config.mapId == map,
                            onClick = { onUpdateConfig(config.copy(mapId = map)) }
                        )
                    }
                }

                // 2. 4 GAME MODES
                Text(strings.gameMode, style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GameMode.entries.forEach { mode ->
                        val sel = config.gameMode == mode
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) TacticalAmber else ArenaPanelSurface)
                                .border(1.dp, if (sel) TacticalAmber else ArenaBorderMuted, RoundedCornerShape(8.dp))
                                .clickable { onUpdateConfig(config.copy(gameMode = mode)) }
                                .padding(horizontal = 6.dp)
                                .testTag("mode_${mode.name.lowercase()}")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(mode.shortTitle, style = MaterialTheme.typography.labelLarge, fontSize = 12.sp, color = if (sel) ArenaSlateDark else TextPrimary)
                                Text(mode.title, style = MaterialTheme.typography.labelMedium, fontSize = 8.sp, maxLines = 1, color = if (sel) ArenaSlateDark else TextSecondary)
                            }
                        }
                    }
                }

                // 3. BOT DIFFICULTY + BOT COUNT (UP TO 11 BOTS = 12 SOLDIERS)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.botDifficulty, style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BotDifficulty.entries.forEach { diff ->
                                val sel = config.botDifficulty == diff
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) TacticalAmber else ArenaPanelSurface)
                                        .border(1.dp, if (sel) TacticalAmber else ArenaBorderMuted, RoundedCornerShape(8.dp))
                                        .clickable { onUpdateConfig(config.copy(botDifficulty = diff)) }
                                        .testTag("bot_diff_${diff.name.lowercase()}")
                                ) {
                                    Text(diff.label, style = MaterialTheme.typography.labelLarge, fontSize = 11.sp, color = if (sel) ArenaSlateDark else TextPrimary)
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.botCount, style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                            Text("${config.botCount} Bots (${config.botCount + 1} Total)", style = MaterialTheme.typography.labelLarge, color = TacticalAmber)
                        }
                        Slider(
                            value = config.botCount.toFloat(),
                            onValueChange = { onUpdateConfig(config.copy(botCount = it.roundToInt().coerceIn(1, 11))) },
                            valueRange = 1f..11f,
                            steps = 9,
                            colors = SliderDefaults.colors(thumbColor = TacticalAmber, activeTrackColor = TacticalAmber)
                        )
                    }
                }

                // 4. DURATION + SCORE LIMIT + TEAM (IF TEAM MODE)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.matchDuration, style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 5, 10, 15, 0).forEach { mins ->
                                val sel = config.durationMinutes == mins
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(if (sel) CyberCyan else ArenaPanelSurface)
                                        .clickable { onUpdateConfig(config.copy(durationMinutes = mins)) }
                                ) {
                                    Text(
                                        text = if (mins == 0) "∞" else "${mins}m",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontSize = 11.sp,
                                        color = if (sel) ArenaSlateDark else TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.scoreLimit, style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(10, 20, 30, 50).forEach { lim ->
                                val sel = config.scoreLimit == lim
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(if (sel) CyberCyan else ArenaPanelSurface)
                                        .clickable { onUpdateConfig(config.copy(scoreLimit = lim)) }
                                ) {
                                    Text("$lim", style = MaterialTheme.typography.labelLarge, fontSize = 11.sp, color = if (sel) ArenaSlateDark else TextPrimary)
                                }
                            }
                        }
                    }
                }

                // 5. STARTING WEAPON + TEAM
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.startingWeapon, style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(unlockedWeapons) { wp ->
                                val sel = config.startingWeapon == wp
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) TacticalAmber else ArenaPanelSurface)
                                        .border(1.dp, if (sel) TacticalAmber else ArenaBorderMuted, RoundedCornerShape(8.dp))
                                        .clickable { onUpdateConfig(config.copy(startingWeapon = wp)) }
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Text(wp.spec.displayName, style = MaterialTheme.typography.labelLarge, fontSize = 11.sp, color = if (sel) ArenaSlateDark else TextPrimary)
                                }
                            }
                        }
                    }

                    if (config.gameMode != GameMode.DEATHMATCH) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(strings.teamLabel, style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0 to "BLUE", 1 to "RED").forEach { (idx, name) ->
                                    val sel = config.localTeam == idx
                                    val col = if (idx == 0) CyberCyan else CombatCrimson
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (sel) col else ArenaPanelSurface)
                                            .clickable { onUpdateConfig(config.copy(localTeam = idx)) }
                                    ) {
                                        Text(name, style = MaterialTheme.typography.labelLarge, fontSize = 11.sp, color = if (sel) ArenaSlateDark else TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MapPreviewCard(
    mapId: MapId,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = Color(mapId.accentHex)
    Column(
        modifier = Modifier
            .width(164.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) ArenaPanelSurface else ArenaSlateDark)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else ArenaBorderMuted, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag("map_card_${mapId.name.lowercase()}")
    ) {
        // Procedural stylized terrain & sky thumbnail per map
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            val topCol = when (mapId) {
                MapId.MILITARY_BASE -> Color(0xFF14243B)
                MapId.DESERT_WAR -> Color(0xFF4C2B14)
                MapId.JUNGLE_ARENA -> Color(0xFF0E3024)
                MapId.FACTORY -> Color(0xFF281915)
                MapId.SNOW_MOUNTAIN -> Color(0xFF1B3654)
                MapId.CITY_RUINS -> Color(0xFF1A162E)
            }
            drawRect(brush = Brush.verticalGradient(listOf(topCol, accent.copy(alpha = 0.35f))))
            drawRect(color = accent.copy(alpha = 0.7f), topLeft = Offset(0f, size.height * 0.75f), size = Size(size.width, size.height * 0.25f))
            drawRect(color = Color.White.copy(alpha = 0.28f), topLeft = Offset(size.width * 0.16f, size.height * 0.48f), size = Size(size.width * 0.24f, 6.dp.toPx()))
            drawRect(color = Color.White.copy(alpha = 0.28f), topLeft = Offset(size.width * 0.56f, size.height * 0.42f), size = Size(size.width * 0.26f, 6.dp.toPx()))
        }
        Spacer(Modifier.height(6.dp))
        Text(mapId.title, style = MaterialTheme.typography.labelLarge, fontSize = 11.sp, color = if (selected) accent else TextPrimary, maxLines = 1)
        Text(mapId.subtitle, style = MaterialTheme.typography.labelMedium, fontSize = 9.sp, color = TextSecondary, maxLines = 1)
    }
}
