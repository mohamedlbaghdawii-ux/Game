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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.data.ArmoryCatalog
import com.example.game.data.ArmoryCategory
import com.example.game.data.ArmoryItem
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

/**
 * Armory screen with Characters, Clothing, Headgear, and Weapons categories,
 * live 3D soldier or rotating 3D weapon preview, coin balance, and buy/unlock/equip flow.
 */
@Composable
fun ArmoryOverlay(
    profile: PlayerProfile,
    strings: LocalizedStrings,
    onSelectCategory: (ArmoryCategory) -> Unit,
    onPreviewWeapon: (WeaponType) -> Unit,
    onBuyOrEquip: (ArmoryItem) -> String?,
    onDragRotatePreview: (Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedCategory by remember { mutableStateOf(ArmoryCategory.WEAPONS) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    val filteredItems = ArmoryCatalog.items.filter { it.category == selectedCategory }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        ArenaSlateDark.copy(alpha = 0.42f),
                        ArenaSlateDark.copy(alpha = 0.94f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
            .testTag("armory_screen")
    ) {
        // LEFT: Live 3D Showcase Area (Soldier OR Rotating 3D Weapon)
        Box(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        onDragRotatePreview(dragAmount)
                    }
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ArenaGunmetal.copy(alpha = 0.85f))
                        .testTag("armory_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                }
                Text(strings.armory, style = MaterialTheme.typography.headlineMedium, color = TacticalAmber)
            }

            // Coins Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ArenaGunmetal.copy(alpha = 0.88f))
                    .border(1.dp, TacticalAmber.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.MonetizationOn, contentDescription = "Coins", tint = TacticalAmber, modifier = Modifier.size(18.dp))
                Text("${profile.coins} COINS", style = MaterialTheme.typography.labelLarge, color = TacticalAmber)
            }
        }

        // RIGHT: Armory Categories & Items
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(ArenaGunmetal.copy(alpha = 0.96f))
                .border(1.dp, ArenaBorderMuted, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ArmoryCategory.entries.forEach { cat ->
                    val sel = selectedCategory == cat
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) TacticalAmber else ArenaPanelSurface)
                            .border(1.dp, if (sel) TacticalAmber else ArenaBorderMuted, RoundedCornerShape(8.dp))
                            .clickable {
                                selectedCategory = cat
                                feedbackMessage = null
                                onSelectCategory(cat)
                            }
                            .testTag("armory_tab_${cat.name.lowercase()}")
                    ) {
                        Text(
                            text = cat.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sel) ArenaSlateDark else TextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }

            if (feedbackMessage != null) {
                Text(
                    text = feedbackMessage!!,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 11.sp,
                    color = CombatCrimson
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val owned = profile.unlockedItemIds.contains(item.id) || item.price == 0
                    val equipped = isItemEquipped(profile, item)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (equipped) CyberCyan.copy(alpha = 0.14f) else ArenaPanelSurface)
                            .border(
                                width = if (equipped) 1.5.dp else 1.dp,
                                color = if (equipped) CyberCyan else ArenaBorderMuted,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (item.weaponType != null) onPreviewWeapon(item.weaponType)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, fontSize = 13.sp, color = TextPrimary)
                            Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, fontSize = 11.sp, color = TextSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val err = onBuyOrEquip(item)
                                feedbackMessage = err
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    equipped -> VitalityEmerald
                                    owned -> CyberCyan
                                    else -> TacticalAmber
                                },
                                contentColor = ArenaSlateDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("armory_action_${item.id}")
                        ) {
                            val btnText = when {
                                equipped -> "EQUIPPED"
                                owned -> "EQUIP"
                                else -> "${item.price} C"
                            }
                            Text(btnText, style = MaterialTheme.typography.labelLarge, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun isItemEquipped(profile: PlayerProfile, item: ArmoryItem): Boolean {
    return when (item.category) {
        ArmoryCategory.CHARACTERS -> profile.appearance.characterId == item.id
        ArmoryCategory.CLOTHING -> profile.appearance.clothingId == item.id
        ArmoryCategory.HEADGEAR -> profile.appearance.headgearId == item.id
        ArmoryCategory.WEAPONS -> profile.equippedWeapon == item.weaponType
    }
}
