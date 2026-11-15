package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.BackpackStyle
import com.example.game.model.CharacterAppearance
import com.example.game.model.HeadgearStyle
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
 * 3D Character Customization screen with interactive drag-to-rotate live 3D soldier preview
 * on the left, and Nickname / Headgear / Backpack / Clothing Color controls on the right.
 */
@Composable
fun CharacterCustomizeOverlay(
    profile: PlayerProfile,
    strings: LocalizedStrings,
    onUpdateNickname: (String) -> Unit,
    onUpdateAppearance: (CharacterAppearance) -> Unit,
    onDragRotatePreview: (Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var nameInput by remember(profile.nickname) { mutableStateOf(profile.nickname) }
    val app = profile.appearance

    val shirtColors = listOf(0xFF3A523E, 0xFFA68A5B, 0xFF1E242D, 0xFF1D4ED8, 0xFFB91C1C, 0xFF475569)
    val pantsColors = listOf(0xFF2B3530, 0xFF7C6542, 0xFF14181F, 0xFF1E3A8A, 0xFF7F1D1D, 0xFF1E293B)
    val bootsColors = listOf(0xFF1E2024, 0xFF443624, 0xFF0E1015, 0xFF334155)
    val visorColors = listOf(0xFF38BDF8, 0xFFFF9F1C, 0xFF22C55E, 0xFFEF4444, 0xFFFACC15, 0xFFA855F7)

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        ArenaSlateDark.copy(alpha = 0.45f),
                        ArenaSlateDark.copy(alpha = 0.94f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
            .testTag("character_customize_screen")
    ) {
        // LEFT: Interactive drag-to-rotate window over the live 3D soldier
        Box(
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        onDragRotatePreview(dragAmount)
                    }
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.TopStart)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ArenaGunmetal.copy(alpha = 0.85f))
                        .testTag("character_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Text(strings.character, style = MaterialTheme.typography.headlineMedium, color = TacticalAmber)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ArenaGunmetal.copy(alpha = 0.78f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text("DRAG TO ROTATE 3D SOLDIER", style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = CyberCyan)
            }
        }

        // RIGHT: Customization Controls Panel
        Column(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(ArenaGunmetal.copy(alpha = 0.95f))
                .border(1.dp, ArenaBorderMuted, RoundedCornerShape(16.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Nickname row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(14) },
                    label = { Text("Player Nickname", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nickname_input")
                )
                Button(
                    onClick = { onUpdateNickname(nameInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = VitalityEmerald, contentColor = ArenaSlateDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("save_nickname_button")
                ) {
                    Text("SAVE", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Headgear Style (6 options)
            Text("HEADGEAR", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(HeadgearStyle.entries) { style ->
                    val sel = app.headgearStyle == style
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) TacticalAmber else ArenaPanelSurface)
                            .border(1.dp, if (sel) TacticalAmber else ArenaBorderMuted, RoundedCornerShape(8.dp))
                            .clickable { onUpdateAppearance(app.copy(headgearStyle = style)) }
                            .padding(horizontal = 10.dp)
                    ) {
                        Text(style.displayName, style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = if (sel) ArenaSlateDark else TextPrimary)
                    }
                }
            }

            // Backpack Style (4 options)
            Text("BACKPACK", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(BackpackStyle.entries) { style ->
                    val sel = app.backpackStyle == style
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) CyberCyan else ArenaPanelSurface)
                            .border(1.dp, if (sel) CyberCyan else ArenaBorderMuted, RoundedCornerShape(8.dp))
                            .clickable { onUpdateAppearance(app.copy(backpackStyle = style)) }
                            .padding(horizontal = 10.dp)
                    ) {
                        Text(style.displayName, style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = if (sel) ArenaSlateDark else TextPrimary)
                    }
                }
            }

            ColorSwatchRow(
                title = "SHIRT / ARMOR COLOR",
                colors = shirtColors,
                selectedHex = app.shirtHex,
                onSelect = { onUpdateAppearance(app.copy(shirtHex = it)) }
            )
            ColorSwatchRow(
                title = "PANTS COLOR",
                colors = pantsColors,
                selectedHex = app.pantsHex,
                onSelect = { onUpdateAppearance(app.copy(pantsHex = it)) }
            )
            ColorSwatchRow(
                title = "BOOTS COLOR",
                colors = bootsColors,
                selectedHex = app.bootsHex,
                onSelect = { onUpdateAppearance(app.copy(bootsHex = it)) }
            )
            ColorSwatchRow(
                title = "VISOR / ACCENT COLOR",
                colors = visorColors,
                selectedHex = app.visorHex,
                onSelect = { onUpdateAppearance(app.copy(visorHex = it)) }
            )
        }
    }
}

@Composable
private fun ColorSwatchRow(
    title: String,
    colors: List<Long>,
    selectedHex: Long,
    onSelect: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEach { hex ->
                val sel = hex == selectedHex
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(hex))
                        .border(if (sel) 3.dp else 1.dp, if (sel) TacticalAmber else ArenaBorderMuted, CircleShape)
                        .clickable { onSelect(hex) }
                )
            }
        }
    }
}
