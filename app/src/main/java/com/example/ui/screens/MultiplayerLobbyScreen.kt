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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.GameMode
import com.example.game.model.MapId
import com.example.game.model.MatchConfig
import com.example.game.network.LanLobbyState
import com.example.game.network.LanRole
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

/**
 * Real Offline Wi-Fi / Android Hotspot LAN Multiplayer Lobby (up to 12 humans, zero internet).
 */
@Composable
fun MultiplayerLobbyOverlay(
    lobbyState: LanLobbyState,
    matchConfig: MatchConfig,
    strings: LocalizedStrings,
    onHostRoom: () -> Unit,
    onJoinByCodeOrIp: (String) -> Unit,
    onScanRooms: () -> Unit,
    onUpdateHostConfig: (MatchConfig) -> Unit,
    onToggleTeam: () -> Unit,
    onHostStartMatch: () -> Unit,
    onLeaveLobby: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onLeaveLobby)

    var selectedTab by remember { mutableIntStateOf(if (lobbyState.role == LanRole.HOST) 0 else 1) }
    var codeInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ArenaSlateDark.copy(alpha = 0.92f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
            .testTag("multiplayer_lobby_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(ArenaGunmetal)
                .border(1.dp, ArenaBorderMuted, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onLeaveLobby,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lan_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                    }
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = CyberCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.playWithFriends, style = MaterialTheme.typography.headlineMedium, color = CyberCyan)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabPill(
                        label = strings.hostGame,
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("lan_tab_host")
                    )
                    TabPill(
                        label = strings.joinGame,
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; onScanRooms() },
                        modifier = Modifier.testTag("lan_tab_join")
                    )
                }
            }

            // Honest connection status / error banner
            val statusColor = if (lobbyState.errorMessage != null) CombatCrimson else VitalityEmerald
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.14f))
                    .border(1.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = lobbyState.errorMessage ?: lobbyState.statusMessage,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 11.sp,
                    color = statusColor
                )
            }

            // Main Split Body
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // LEFT: Host Controls OR Join Discovery / Room Code Input
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ArenaPanelSurface)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedTab == 0) {
                        if (lobbyState.role != LanRole.HOST) {
                            Text(
                                text = "Create a private offline room over Wi-Fi or Android Hotspot (up to 12 players).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Button(
                                onClick = onHostRoom,
                                colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber, contentColor = ArenaSlateDark),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("lan_create_room_button")
                            ) {
                                Text("CREATE PRIVATE ROOM", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("ROOM CODE", style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = TextSecondary)
                                    Text(lobbyState.roomCode, style = MaterialTheme.typography.headlineMedium, color = TacticalAmber)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("LOCAL IP", style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = TextSecondary)
                                    Text(lobbyState.hostIp, style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                                }
                            }

                            // Host Map & Mode selection
                            Text(strings.selectMap, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                MapId.entries.take(3).forEach { m ->
                                    val sel = lobbyState.matchConfig.mapId == m
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (sel) CyberCyan else ArenaGunmetal)
                                            .clickable { onUpdateHostConfig(matchConfig.copy(mapId = m)) }
                                    ) {
                                        Text(m.title.split(" ").first(), style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = if (sel) ArenaSlateDark else TextPrimary)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                MapId.entries.drop(3).forEach { m ->
                                    val sel = lobbyState.matchConfig.mapId == m
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (sel) CyberCyan else ArenaGunmetal)
                                            .clickable { onUpdateHostConfig(matchConfig.copy(mapId = m)) }
                                    ) {
                                        Text(m.title.split(" ").first(), style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = if (sel) ArenaSlateDark else TextPrimary)
                                    }
                                }
                            }

                            Text(strings.gameMode, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                GameMode.entries.forEach { gm ->
                                    val sel = lobbyState.matchConfig.gameMode == gm
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (sel) TacticalAmber else ArenaGunmetal)
                                            .clickable { onUpdateHostConfig(matchConfig.copy(gameMode = gm)) }
                                    ) {
                                        Text(gm.shortTitle, style = MaterialTheme.typography.labelLarge, fontSize = 10.sp, color = if (sel) ArenaSlateDark else TextPrimary)
                                    }
                                }
                            }

                            Button(
                                onClick = onHostStartMatch,
                                colors = ButtonDefaults.buttonColors(containerColor = VitalityEmerald, contentColor = ArenaSlateDark),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("lan_start_match_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(strings.startMatch, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // JOIN GAME TAB
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = codeInput,
                                onValueChange = { codeInput = it.uppercase().take(18) },
                                label = { Text("Room Code or Host IP", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("lan_code_input")
                            )
                            Button(
                                onClick = { onJoinByCodeOrIp(codeInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ArenaSlateDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("lan_connect_button")
                            ) {
                                Text(strings.connect, style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("DISCOVERED LOCAL ROOMS", style = MaterialTheme.typography.labelLarge, fontSize = 11.sp, color = TacticalAmber)
                            OutlinedButton(
                                onClick = onScanRooms,
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("lan_scan_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = CyberCyan)
                                Spacer(Modifier.width(4.dp))
                                Text(strings.scanLan, style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = TextPrimary)
                            }
                        }

                        if (lobbyState.discoveredRooms.isEmpty()) {
                            Text(
                                text = "No rooms found on local Wi-Fi / Hotspot yet. Ask the host to open Host Game or enter their Room Code / IP.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        } else {
                            lobbyState.discoveredRooms.forEach { room ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ArenaGunmetal)
                                        .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .clickable { onJoinByCodeOrIp(room.hostIp) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${room.hostName} • ${room.roomCode}", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                        Text("${room.mapId.title} • ${room.gameMode.shortTitle} • ${room.hostIp}", style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = TextSecondary)
                                    }
                                    Text("${room.playerCount}/${room.maxPlayers}", style = MaterialTheme.typography.labelLarge, color = VitalityEmerald)
                                }
                            }
                        }
                    }
                }

                // RIGHT: CONNECTED PLAYERS ROSTER
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ArenaPanelSurface)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PLAYERS (${lobbyState.peers.size}/12)",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyberCyan
                        )
                        if (lobbyState.isConnected) {
                            OutlinedButton(
                                onClick = onToggleTeam,
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("SWAP TEAM", style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = TacticalAmber)
                            }
                        }
                    }

                    if (lobbyState.peers.isEmpty()) {
                        Text(
                            text = "Not in a room yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        lobbyState.peers.forEach { peer ->
                            val teamColor = if (peer.teamIndex == 0) CyberCyan else CombatCrimson
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ArenaGunmetal)
                                    .border(1.dp, teamColor.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = peer.callsign + if (peer.isHost) " [HOST]" else "",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (peer.teamIndex == 0) "BLUE" else "RED",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = teamColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) TacticalAmber else ArenaPanelSurface)
            .border(1.dp, if (selected) TacticalAmber else ArenaBorderMuted, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 11.sp,
            color = if (selected) ArenaSlateDark else TextPrimary
        )
    }
}
