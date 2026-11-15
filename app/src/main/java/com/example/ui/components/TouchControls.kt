package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ArenaGunmetal
import com.example.ui.theme.CombatCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VitalityEmerald
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Multi-touch mobile arcade shooter controls:
 * - LEFT: Virtual movement joystick (move left/right) + Crouch toggle
 * - RIGHT: Dedicated 360 Aim stick, Fire trigger, Jump, Jetpack (hold), Grenade, Weapon Swap, Reload
 */
@Composable
fun TouchControlsOverlay(
    controlSensitivity: Float,
    grenadeCount: Int,
    isCrouching: Boolean,
    onMoveChange: (Float) -> Unit,
    onCrouchChange: (Boolean) -> Unit,
    onJetpackChange: (Boolean) -> Unit,
    onAimChange: (angleRad: Float, isActive: Boolean, autoFire: Boolean) -> Unit,
    onShootChange: (Boolean) -> Unit,
    onJump: () -> Unit,
    onGrenade: () -> Unit,
    onSwitchWeapon: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // === LEFT SIDE: VIRTUAL MOVEMENT JOYSTICK + CROUCH ===
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 4.dp, bottom = 4.dp)
        ) {
            VirtualMoveJoystick(
                sensitivity = controlSensitivity,
                onMoveChange = onMoveChange
            )
            TacticalToggleButton(
                label = "CROUCH",
                active = isCrouching,
                icon = {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Crouch",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                accentColor = CyberCyan,
                sizeDp = 50,
                onClick = { onCrouchChange(!isCrouching) },
                modifier = Modifier.testTag("crouch_button")
            )
        }

        // === RIGHT SIDE: UTILITY ROW + PRIMARY COMBAT CLUSTER ===
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 4.dp)
        ) {
            // Top-right tactical utilities: Grenade + Reload + Weapon Switch
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TacticalActionButton(
                    label = "NADE($grenadeCount)",
                    icon = {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = "Throw grenade",
                            tint = if (grenadeCount > 0) TacticalAmber else TextPrimary.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    accentColor = VitalityEmerald,
                    sizeDp = 50,
                    onPressStart = onGrenade,
                    onPressEnd = {},
                    modifier = Modifier.testTag("grenade_button")
                )

                TacticalActionButton(
                    label = "RELOAD",
                    icon = {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reload weapon",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    accentColor = CyberCyan,
                    sizeDp = 50,
                    onPressStart = onReload,
                    onPressEnd = {},
                    modifier = Modifier.testTag("reload_button")
                )

                TacticalActionButton(
                    label = "SWAP",
                    icon = {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Switch weapon",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    accentColor = TacticalAmber,
                    sizeDp = 52,
                    onPressStart = onSwitchWeapon,
                    onPressEnd = {},
                    modifier = Modifier.testTag("switch_weapon_button")
                )
            }

            // Bottom-right cluster: Jetpack + Jump + 360 Aim Pad + Shoot Trigger
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Holdable JETPACK thrust button
                    TacticalActionButton(
                        label = "JET",
                        icon = {
                            Icon(
                                Icons.Default.RocketLaunch,
                                contentDescription = "Jetpack boost",
                                tint = TacticalAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        accentColor = TacticalAmber,
                        sizeDp = 52,
                        onPressStart = { onJetpackChange(true) },
                        onPressEnd = { onJetpackChange(false) },
                        modifier = Modifier.testTag("jetpack_button")
                    )

                    // JUMP button
                    TacticalActionButton(
                        label = "JUMP",
                        icon = {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Jump",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        accentColor = VitalityEmerald,
                        sizeDp = 54,
                        onPressStart = onJump,
                        onPressEnd = {},
                        modifier = Modifier.testTag("jump_button")
                    )
                }

                // AIM CONTROL (360 directional virtual stick; outer ring also fires)
                VirtualAimStick(
                    sensitivity = controlSensitivity,
                    onAimChange = onAimChange,
                    modifier = Modifier.testTag("aim_control_pad")
                )

                // DEDICATED SHOOT TRIGGER
                TacticalActionButton(
                    label = "FIRE",
                    icon = {
                        Icon(
                            Icons.Default.GpsFixed,
                            contentDescription = "Shoot weapon",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    accentColor = CombatCrimson,
                    sizeDp = 74,
                    onPressStart = { onShootChange(true) },
                    onPressEnd = { onShootChange(false) },
                    modifier = Modifier.testTag("shoot_button")
                )
            }
        }
    }
}

@Composable
private fun VirtualMoveJoystick(
    sensitivity: Float,
    onMoveChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val sizeDp = 122.dp
    val maxRadiusPx = with(LocalDensity.current) { 40.dp.toPx() }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp)
            .testTag("virtual_move_joystick")
            .semantics { contentDescription = "Movement virtual joystick" }
            .pointerInput(sensitivity) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.type == PointerType.Mouse) return@awaitEachGesture
                    down.consume()
                    active = true
                    val center = Offset(size.width / 2f, size.height / 2f)

                    fun updateFromPos(pos: Offset) {
                        val delta = (pos - center) * sensitivity.coerceIn(0.6f, 1.8f)
                        val dist = hypot(delta.x, delta.y)
                        val clamped = if (dist > maxRadiusPx && dist > 0.001f) {
                            delta * (maxRadiusPx / dist)
                        } else delta
                        knobOffset = clamped
                        val normX = (clamped.x / maxRadiusPx).coerceIn(-1f, 1f)
                        onMoveChange(if (abs(normX) < 0.14f) 0f else normX)
                    }

                    updateFromPos(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                        updateFromPos(change.position)
                    }
                    active = false
                    knobOffset = Offset.Zero
                    onMoveChange(0f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = ArenaGunmetal.copy(alpha = 0.54f),
                radius = size.minDimension * 0.48f,
                center = c
            )
            drawCircle(
                color = (if (active) CyberCyan else Color.White).copy(alpha = if (active) 0.76f else 0.28f),
                radius = size.minDimension * 0.48f,
                center = c,
                style = Stroke(width = 2.8.dp.toPx())
            )
            drawCircle(
                color = (if (active) CyberCyan else Color.White).copy(alpha = 0.85f),
                radius = 21.dp.toPx(),
                center = c + knobOffset
            )
            drawCircle(
                color = ArenaGunmetal,
                radius = 9.dp.toPx(),
                center = c + knobOffset
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = TextPrimary.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextPrimary.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun VirtualAimStick(
    sensitivity: Float,
    onAimChange: (angleRad: Float, isActive: Boolean, autoFire: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val sizeDp = 112.dp
    val maxRadiusPx = with(LocalDensity.current) { 36.dp.toPx() }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    var currentAngle by remember { mutableFloatStateOf(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp)
            .semantics { contentDescription = "Aim control pad" }
            .pointerInput(sensitivity) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.type == PointerType.Mouse) return@awaitEachGesture
                    down.consume()
                    active = true
                    val center = Offset(size.width / 2f, size.height / 2f)

                    fun update(pos: Offset) {
                        val raw = (pos - center) * sensitivity.coerceIn(0.6f, 1.8f)
                        val dist = hypot(raw.x, raw.y)
                        val clamped = if (dist > maxRadiusPx && dist > 0.001f) {
                            raw * (maxRadiusPx / dist)
                        } else raw
                        knobOffset = clamped
                        if (dist > 8f) {
                            val angle = atan2(-clamped.y, clamped.x)
                            currentAngle = angle
                            val autoFire = (dist / maxRadiusPx) > 0.78f
                            onAimChange(angle, true, autoFire)
                        } else {
                            onAimChange(currentAngle, true, false)
                        }
                    }

                    update(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                        update(change.position)
                    }
                    active = false
                    knobOffset = Offset.Zero
                    onAimChange(currentAngle, false, false)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = ArenaGunmetal.copy(alpha = 0.56f),
                radius = size.minDimension * 0.48f,
                center = c
            )
            drawCircle(
                color = (if (active) TacticalAmber else Color.White).copy(alpha = if (active) 0.85f else 0.32f),
                radius = size.minDimension * 0.48f,
                center = c,
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                color = CombatCrimson.copy(alpha = if (active) 0.45f else 0.16f),
                radius = size.minDimension * 0.36f,
                center = c,
                style = Stroke(width = 1.5.dp.toPx())
            )
            if (active) {
                val rayEnd = c + Offset(cos(currentAngle) * 30.dp.toPx(), -sin(currentAngle) * 30.dp.toPx())
                drawLine(TacticalAmber, c, rayEnd, strokeWidth = 2.dp.toPx())
            }
            drawCircle(
                color = (if (active) TacticalAmber else Color.White).copy(alpha = 0.88f),
                radius = 17.dp.toPx(),
                center = c + knobOffset
            )
        }
        if (!active) {
            Text(
                text = "AIM",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ArenaGunmetal
            )
        }
    }
}

@Composable
private fun TacticalActionButton(
    label: String,
    icon: @Composable () -> Unit,
    accentColor: Color,
    sizeDp: Int,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(
                if (pressed) accentColor.copy(alpha = 0.78f)
                else ArenaGunmetal.copy(alpha = 0.72f)
            )
            .border(
                width = if (pressed) 2.5.dp else 1.8.dp,
                color = accentColor.copy(alpha = if (pressed) 1f else 0.75f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    onPressStart()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                    }
                    pressed = false
                    onPressEnd()
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun TacticalToggleButton(
    label: String,
    active: Boolean,
    icon: @Composable () -> Unit,
    accentColor: Color,
    sizeDp: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(
                if (active) accentColor.copy(alpha = 0.72f)
                else ArenaGunmetal.copy(alpha = 0.72f)
            )
            .border(
                width = if (active) 2.5.dp else 1.6.dp,
                color = accentColor.copy(alpha = if (active) 1f else 0.7f),
                shape = CircleShape
            )
            .pointerInput(active) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            onClick()
                            break
                        }
                        change.consume()
                    }
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}
