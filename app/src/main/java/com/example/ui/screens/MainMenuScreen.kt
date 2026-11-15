package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.data.ArmoryCatalog
import com.example.game.model.GameSettings
import com.example.game.model.PlayerProfile
import com.example.ui.LocalizedStrings
import com.example.ui.theme.ArenaBorderMuted
import com.example.ui.theme.ArenaGunmetal
import com.example.ui.theme.ArenaPanelSurface
import com.example.ui.theme.ArenaSlateDark
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VitalityEmerald

/**
 * Main Menu inspired by Mini Militia:
 * Top player profile bar (nickname, level, coins, character, audio toggles),
 * live 3D animated soldier in the center, and 5 primary navigation buttons.
 */
@Composable
fun MainMenuOverlay(
    profile: PlayerProfile,
    settings: GameSettings,
    strings: LocalizedStrings,
    onPlayOffline: () -> Unit,
    onPlayWithFriends: () -> Unit,
    onCharacter: () -> Unit,
    onArmory: () -> Unit,
    onSettings: () -> Unit,
    onToggleSfx: () -> Unit,
    onToggleMusic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCharName = ArmoryCatalog.items
        .firstOrNull { it.id == profile.appearance.characterId }?.name ?: "STRIKER-01"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        ArenaSlateDark.copy(alpha = 0.82f),
                        ArenaSlateDark.copy(alpha = 0.16f),
                        ArenaSlateDark.copy(alpha = 0.72f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("main_menu")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // === TOP BAR: PROFILE + LEVEL + COINS + AUDIO TOGGLES ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ArenaGunmetal.copy(alpha = 0.88f))
                        .border(1.dp, ArenaBorderMuted, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = TacticalAmber, modifier = Modifier.size(24.dp))
                    Column {
                        Text(
                            text = profile.nickname,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${strings.levelPrefix} ${profile.level}  •  $selectedCharName",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 11.sp,
                            color = CyberCyan
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ArenaPanelSurface)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = "Coins", tint = TacticalAmber, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${profile.coins}",
                            style = MaterialTheme.typography.labelLarge,
                            color = TacticalAmber
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleSfx,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ArenaGunmetal.copy(alpha = 0.86f))
                            .border(1.dp, ArenaBorderMuted, RoundedCornerShape(10.dp))
                            .testTag("toggle_sfx_button")
                    ) {
                        Icon(
                            imageVector = if (settings.sfxVolume > 0.05f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Toggle SFX",
                            tint = if (settings.sfxVolume > 0.05f) CyberCyan else TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onToggleMusic,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ArenaGunmetal.copy(alpha = 0.86f))
                            .border(1.dp, ArenaBorderMuted, RoundedCornerShape(10.dp))
                            .testTag("toggle_music_button")
                    ) {
                        Icon(
                            imageVector = if (settings.musicVolume > 0.05f) Icons.Default.MusicNote else Icons.Default.MusicOff,
                            contentDescription = "Toggle Music",
                            tint = if (settings.musicVolume > 0.05f) TacticalAmber else TextSecondary
                        )
                    }
                }
            }

            // === MIDDLE: LEFT COMMAND BUTTONS + CLEAR CENTER FOR 3D SOLDIER + RIGHT GEAR SUMMARY ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left 5 Large Action Buttons
                Column(
                    modifier = Modifier
                        .widthIn(max = 285.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "MINI BATTLE ARENA",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TacticalAmber
                    )
                    Text(
                        text = "BY YOUNES  •  ${profile.equippedWeapon.spec.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = onPlayOffline,
                        colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber, contentColor = ArenaSlateDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("menu_play_offline")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.playOffline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = onPlayWithFriends,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ArenaSlateDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("menu_play_friends")
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.playWithFriends, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        MenuSecondaryButton(
                            label = strings.character,
                            icon = Icons.Default.Person,
                            tint = VitalityEmerald,
                            onClick = onCharacter,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("menu_character")
                        )
                        MenuSecondaryButton(
                            label = strings.armory,
                            icon = Icons.Default.ShoppingBag,
                            tint = TacticalAmber,
                            onClick = onArmory,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("menu_armory")
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    MenuSecondaryButton(
                        label = strings.settings,
                        icon = Icons.Default.Settings,
                        tint = CyberCyan,
                        onClick = onSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("menu_settings_button")
                    )
                }

                // Right Compact Combat Record Badge (keeps center clear for live 3D soldier!)
                Column(
                    modifier = Modifier
                        .widthIn(max = 210.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ArenaGunmetal.copy(alpha = 0.82f))
                        .border(1.dp, ArenaBorderMuted, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("COMBAT RECORD", style = MaterialTheme.typography.labelLarge, fontSize = 11.sp, color = CyberCyan)
                    StatLine("Matches", "${profile.matchesPlayed}")
                    StatLine("Wins", "${profile.totalWins}")
                    StatLine("Total Kills", "${profile.totalKills}")
                    StatLine("Headgear", profile.appearance.headgearStyle.displayName.take(15))
                }
            }
        }
    }
}

@Composable
private fun MenuSecondaryButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(48.dp)
            .background(ArenaGunmetal.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 11.sp,
            color = TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.labelLarge, fontSize = 11.sp, color = TextPrimary)
    }
}
