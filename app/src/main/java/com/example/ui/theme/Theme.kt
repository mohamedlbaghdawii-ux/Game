package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TacticalDarkColorScheme = darkColorScheme(
    primary = TacticalAmber,
    onPrimary = ArenaSlateDark,
    primaryContainer = ArenaPanelSurface,
    onPrimaryContainer = TacticalAmberGlow,
    secondary = CyberCyan,
    onSecondary = ArenaSlateDark,
    secondaryContainer = ArenaGunmetal,
    onSecondaryContainer = TextPrimary,
    tertiary = VitalityEmerald,
    onTertiary = ArenaSlateDark,
    background = ArenaSlateDark,
    onBackground = TextPrimary,
    surface = ArenaGunmetal,
    onSurface = TextPrimary,
    surfaceVariant = ArenaPanelSurface,
    onSurfaceVariant = TextSecondary,
    error = CombatCrimson,
    onError = TextPrimary,
    outline = ArenaBorderMuted
)

@Composable
fun MiniBattleArenaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TacticalDarkColorScheme,
        typography = Typography,
        content = content
    )
}
