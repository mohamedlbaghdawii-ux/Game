package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.LocalizedStrings
import com.example.ui.theme.ArenaBorderMuted
import com.example.ui.theme.ArenaGunmetal
import com.example.ui.theme.ArenaSlateDark
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * First-launch cinematic loading screen layered over the live 3D battlefield & hero soldier.
 */
@Composable
fun LoadingScreenOverlay(
    progress: Float,
    strings: LocalizedStrings,
    modifier: Modifier = Modifier
) {
    val pct = (progress.coerceIn(0f, 1f) * 100f).roundToInt()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ArenaSlateDark.copy(alpha = 0.72f),
                        Color.Transparent,
                        ArenaSlateDark.copy(alpha = 0.88f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp, vertical = 20.dp)
            .testTag("loading_screen")
    ) {
        // Subtle vignette framing around the live 3D soldier
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, ArenaSlateDark.copy(alpha = 0.52f)),
                    center = Offset(size.width * 0.5f, size.height * 0.45f),
                    radius = size.maxDimension * 0.58f
                )
            )
        }

        // Top Original Logo Badge
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ArenaGunmetal.copy(alpha = 0.82f))
                    .border(1.5.dp, TacticalAmber.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Mini Battle Arena Emblem",
                    tint = TacticalAmber,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "MINI BATTLE ARENA",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "TACTICAL 3D SHOOTER",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 10.sp,
                        color = CyberCyan
                    )
                }
            }
        }

        // Top-Right Developer Credits (Arabic + English)
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(10.dp))
                .background(ArenaGunmetal.copy(alpha = 0.78f))
                .border(1.dp, ArenaBorderMuted, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = "تم تطوير اللعبة من طرف يونس",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TacticalAmber
            )
            Text(
                text = "DEVELOPED BY YOUNES",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        // Bottom Animated Loading Bar + Percentage
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.loadingSystems,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.titleLarge,
                    color = TacticalAmber,
                    modifier = Modifier.testTag("loading_percentage")
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(ArenaGunmetal.copy(alpha = 0.92f))
                    .border(1.dp, TacticalAmber.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(CyberCyan, TacticalAmber)
                            )
                        )
                )
            }
        }
    }
}
