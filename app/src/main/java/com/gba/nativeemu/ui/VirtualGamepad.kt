package com.gba.nativeemu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gba.nativeemu.core.GbaBridge
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun VirtualGamepad(
    modifier: Modifier = Modifier,
    opacity: Float = 0.85f,
    isFastForward: Boolean,
    onFastForwardToggle: () -> Unit,
    onMenuClick: () -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onKeyMaskChanged: (Int) -> Unit
) {
    var dpadMask by remember { mutableStateOf(0) }
    var actionMask by remember { mutableStateOf(0) }

    LaunchedEffect(dpadMask, actionMask) {
        onKeyMaskChanged(dpadMask or actionMask)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(opacity)
    ) {
        // --- TOP SHOULDER BUTTONS (L & R) & QUICK ACTIONS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // L Button
            GamepadButton(
                text = "L",
                modifier = Modifier
                    .width(72.dp)
                    .height(38.dp),
                shape = RoundedCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
                color = Color(0xFF2C2C3E),
                onPressState = { pressed ->
                    actionMask = if (pressed) actionMask or GbaBridge.KEY_L else actionMask and GbaBridge.KEY_L.inv()
                }
            )

            // Center Quick Action Toolbar
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onFastForwardToggle,
                    modifier = Modifier
                        .size(36.dp)
                        .background(if (isFastForward) Color(0xFFFF9100) else Color(0x66000000), CircleShape)
                ) {
                    Icon(
                        Icons.Default.FastForward,
                        contentDescription = "Fast Forward",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onQuickSave,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x66000000), CircleShape)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Quick Save", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = onQuickLoad,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x66000000), CircleShape)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = "Quick Load", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x66000000), CircleShape)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // R Button
            GamepadButton(
                text = "R",
                modifier = Modifier
                    .width(72.dp)
                    .height(38.dp),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomStart = 16.dp),
                color = Color(0xFF2C2C3E),
                onPressState = { pressed ->
                    actionMask = if (pressed) actionMask or GbaBridge.KEY_R else actionMask and GbaBridge.KEY_R.inv()
                }
            )
        }

        // --- BOTTOM CONTROLS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Left: Circular Precision D-Pad
            CircularDPad(
                modifier = Modifier.size(170.dp),
                onDirectionChanged = { mask ->
                    dpadMask = mask
                }
            )

            // Center: Select and Start Buttons
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillButton(
                    text = "SELECT",
                    onPressState = { pressed ->
                        actionMask = if (pressed) actionMask or GbaBridge.KEY_SELECT else actionMask and GbaBridge.KEY_SELECT.inv()
                    }
                )

                PillButton(
                    text = "START",
                    onPressState = { pressed ->
                        actionMask = if (pressed) actionMask or GbaBridge.KEY_START else actionMask and GbaBridge.KEY_START.inv()
                    }
                )
            }

            // Right: Action Buttons Cluster (B, A, Turbo B, Turbo A, Combo A+B)
            ActionButtonsCluster(
                modifier = Modifier.size(180.dp),
                onMaskChanged = { mask ->
                    actionMask = (actionMask and (GbaBridge.KEY_L or GbaBridge.KEY_R or GbaBridge.KEY_START or GbaBridge.KEY_SELECT)) or mask
                }
            )
        }
    }
}

@Composable
fun CircularDPad(
    modifier: Modifier = Modifier,
    onDirectionChanged: (Int) -> Unit
) {
    var touchPos by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                touchPos = down.position
                updateDirection(down.position, size.width.toFloat(), onDirectionChanged)

                do {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.find { it.id == down.id }
                    if (pointer != null && pointer.pressed) {
                        touchPos = pointer.position
                        updateDirection(pointer.position, size.width.toFloat(), onDirectionChanged)
                    } else {
                        touchPos = null
                        onDirectionChanged(0)
                        break
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f

        // Outer rim
        drawCircle(
            color = Color(0xFF1E1E28),
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color(0xFF3A3A4E),
            radius = radius,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )

        // Cross D-Pad Shape
        val crossWidth = radius * 0.7f
        val crossThickness = radius * 0.44f

        drawRoundRect(
            color = Color(0xFF2C2C3E),
            topLeft = Offset(center.x - crossThickness / 2, center.y - crossWidth),
            size = androidx.compose.ui.geometry.Size(crossThickness, crossWidth * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF2C2C3E),
            topLeft = Offset(center.x - crossWidth, center.y - crossThickness / 2),
            size = androidx.compose.ui.geometry.Size(crossWidth * 2, crossThickness),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
        )

        // Center Thumb Dimple / Indicator
        val activeCenter = touchPos?.let { pos ->
            val dx = pos.x - center.x
            val dy = pos.y - center.y
            val dist = sqrt(dx * dx + dy * dy)
            val maxDist = radius * 0.5f
            if (dist > maxDist) {
                Offset(center.x + (dx / dist) * maxDist, center.y + (dy / dist) * maxDist)
            } else {
                pos
            }
        } ?: center

        drawCircle(
            color = if (touchPos != null) Color(0xFF00E5FF) else Color(0xFF16161E),
            radius = radius * 0.22f,
            center = activeCenter
        )
    }
}

private fun updateDirection(pos: Offset, viewSize: Float, onDirectionChanged: (Int) -> Unit) {
    val cx = viewSize / 2f
    val cy = viewSize / 2f
    val dx = pos.x - cx
    val dy = pos.y - cy
    val dist = sqrt(dx * dx + dy * dy)

    val deadZone = viewSize * 0.08f
    if (dist < deadZone) {
        onDirectionChanged(0)
        return
    }

    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (angle < 0) angle += 360f

    var mask = 0
    when {
        angle in 337.5f..360f || angle in 0.0f..22.5f -> mask = GbaBridge.KEY_RIGHT
        angle in 22.5f..67.5f -> mask = GbaBridge.KEY_RIGHT or GbaBridge.KEY_DOWN
        angle in 67.5f..112.5f -> mask = GbaBridge.KEY_DOWN
        angle in 112.5f..157.5f -> mask = GbaBridge.KEY_LEFT or GbaBridge.KEY_DOWN
        angle in 157.5f..202.5f -> mask = GbaBridge.KEY_LEFT
        angle in 202.5f..247.5f -> mask = GbaBridge.KEY_LEFT or GbaBridge.KEY_UP
        angle in 247.5f..292.5f -> mask = GbaBridge.KEY_UP
        angle in 292.5f..337.5f -> mask = GbaBridge.KEY_RIGHT or GbaBridge.KEY_UP
    }
    onDirectionChanged(mask)
}

@Composable
fun ActionButtonsCluster(
    modifier: Modifier = Modifier,
    onMaskChanged: (Int) -> Unit
) {
    var pressedMask by remember { mutableStateOf(0) }
    var isTurboAPressed by remember { mutableStateOf(false) }
    var isTurboBPressed by remember { mutableStateOf(false) }
    var turboMask by remember { mutableStateOf(0) }

    LaunchedEffect(isTurboAPressed) {
        if (isTurboAPressed) {
            while (isTurboAPressed) {
                turboMask = turboMask or GbaBridge.KEY_A
                kotlinx.coroutines.delay(40)
                turboMask = turboMask and GbaBridge.KEY_A.inv()
                kotlinx.coroutines.delay(40)
            }
        } else {
            turboMask = turboMask and GbaBridge.KEY_A.inv()
        }
    }

    LaunchedEffect(isTurboBPressed) {
        if (isTurboBPressed) {
            while (isTurboBPressed) {
                turboMask = turboMask or GbaBridge.KEY_B
                kotlinx.coroutines.delay(40)
                turboMask = turboMask and GbaBridge.KEY_B.inv()
                kotlinx.coroutines.delay(40)
            }
        } else {
            turboMask = turboMask and GbaBridge.KEY_B.inv()
        }
    }

    LaunchedEffect(pressedMask, turboMask) {
        onMaskChanged(pressedMask or turboMask)
    }

    Box(modifier = modifier) {
        // Combo A+B Button (Top Center of Action Cluster)
        GamepadButton(
            text = "A+B",
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.TopCenter),
            shape = CircleShape,
            color = Color(0xFF4A148C),
            fontSize = 12.sp,
            onPressState = { pressed ->
                pressedMask = if (pressed) pressedMask or (GbaBridge.KEY_A or GbaBridge.KEY_B) else pressedMask and (GbaBridge.KEY_A or GbaBridge.KEY_B).inv()
            }
        )

        // Turbo B Button
        GamepadButton(
            text = "TB",
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.CenterStart)
                .padding(bottom = 24.dp),
            shape = CircleShape,
            color = Color(0xFFE65100),
            fontSize = 13.sp,
            onPressState = { pressed ->
                isTurboBPressed = pressed
            }
        )

        // Turbo A Button
        GamepadButton(
            text = "TA",
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.CenterEnd)
                .padding(bottom = 24.dp),
            shape = CircleShape,
            color = Color(0xFF00838F),
            fontSize = 13.sp,
            onPressState = { pressed ->
                isTurboAPressed = pressed
            }
        )

        // B Button (Left / Lower)
        GamepadButton(
            text = "B",
            modifier = Modifier
                .size(62.dp)
                .align(Alignment.BottomStart)
                .padding(bottom = 8.dp),
            shape = CircleShape,
            color = Color(0xFFB71C1C),
            fontSize = 22.sp,
            onPressState = { pressed ->
                pressedMask = if (pressed) pressedMask or GbaBridge.KEY_B else pressedMask and GbaBridge.KEY_B.inv()
            }
        )

        // A Button (Right / Higher)
        GamepadButton(
            text = "A",
            modifier = Modifier
                .size(62.dp)
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp),
            shape = CircleShape,
            color = Color(0xFF1B5E20),
            fontSize = 22.sp,
            onPressState = { pressed ->
                pressedMask = if (pressed) pressedMask or GbaBridge.KEY_A else pressedMask and GbaBridge.KEY_A.inv()
            }
        )
    }
}

@Composable
fun GamepadButton(
    text: String,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    color: Color = Color(0xFF2C2C3E),
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    onPressState: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isPressed) color.copy(alpha = 1.0f) else color.copy(alpha = 0.75f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    onPressState(true)

                    eventStreamHasUpOrCancel(down.id)
                    isPressed = false
                    onPressState(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize
        )
    }
}

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    onPressState: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = modifier
                .width(48.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isPressed) Color(0xFF9E9E9E) else Color(0xFF424242))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        isPressed = true
                        onPressState(true)

                        eventStreamHasUpOrCancel(down.id)
                        isPressed = false
                        onPressState(false)
                    }
                }
        )
        Text(
            text = text,
            color = Color.Gray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.eventStreamHasUpOrCancel(pointerId: androidx.compose.ui.input.pointer.PointerId): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.find { it.id == pointerId } ?: return false
        if (!change.pressed) {
            return true
        }
    }
}
