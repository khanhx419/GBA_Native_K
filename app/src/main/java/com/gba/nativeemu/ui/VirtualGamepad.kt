package com.gba.nativeemu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gba.nativeemu.core.GbaBridge
import com.gba.nativeemu.storage.CustomLayoutState
import com.gba.nativeemu.storage.ElementLayout
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
fun VirtualGamepad(
    modifier: Modifier = Modifier,
    opacity: Float = 0.85f,
    fpsText: String,
    isFastForward: Boolean,
    isLandscape: Boolean = false,
    layoutState: CustomLayoutState = CustomLayoutState(),
    isEditingLayout: Boolean = false,
    selectedElementId: String = "dpad",
    isFullScreen: Boolean = false,
    hideGamepad: Boolean = false,
    onToggleFullScreen: () -> Unit = {},
    onSelectElement: (String) -> Unit = {},
    onMoveElement: (String, Float, Float) -> Unit = { _, _, _ -> },
    onMenuClick: () -> Unit,
    onKeyMaskChanged: (Int) -> Unit
) {
    var dpadMask by remember { mutableStateOf(0) }
    var actionMask by remember { mutableStateOf(0) }

    LaunchedEffect(dpadMask, actionMask) {
        if (!isEditingLayout) {
            onKeyMaskChanged(dpadMask or actionMask)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(if (isEditingLayout) 1.0f else opacity)
    ) {
        if (!isLandscape) {
            // ==================== PORTRAIT MODE ====================
            // 1. Top Toolbar (FPS on left, Select & Start & Menu on right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: FPS & Thermal Status Badge
                Row(
                    modifier = Modifier
                        .background(Color(0xCC14141E), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (isFastForward) Color(0xFFFF9100) else Color(0xFF00E676), CircleShape)
                    )
                    Text(
                        text = fpsText,
                        color = if (isFastForward) Color(0xFFFFB74D) else Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Right: SELECT, START, and Hamburger Menu (☰)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Select & Start Group (Editable & Draggable)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.layoutElementModifier(
                            layout = layoutState.selectStart,
                            elementId = "select_start",
                            isEditing = isEditingLayout,
                            isSelected = selectedElementId == "select_start",
                            shape = RoundedCornerShape(14.dp),
                            onSelect = onSelectElement,
                            onMove = onMoveElement
                        )
                    ) {
                        PillButton(
                            text = "SELECT",
                            isEditing = isEditingLayout,
                            onPressState = { pressed ->
                                actionMask = if (pressed) actionMask or GbaBridge.KEY_SELECT else actionMask and GbaBridge.KEY_SELECT.inv()
                            }
                        )

                        PillButton(
                            text = "START",
                            isEditing = isEditingLayout,
                            onPressState = { pressed ->
                                actionMask = if (pressed) actionMask or GbaBridge.KEY_START else actionMask and GbaBridge.KEY_START.inv()
                            }
                        )
                    }

                    // Full Screen Toggle Button (⛶)
                    IconButton(
                        onClick = onToggleFullScreen,
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (isFullScreen) Color(0xCC00838F) else Color(0x992C2C3E),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullScreen) "Thu nhỏ 3:2" else "Toàn màn hình",
                            tint = if (isFullScreen) Color(0xFF00E5FF) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Hamburger Menu Button (☰ - Fixed)
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0x992C2C3E), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (!hideGamepad || isEditingLayout) {
                // 2. Shoulder Buttons (L & R - Positioned above DPad / Action buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 175.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // L Button
                GamepadButton(
                    text = "L",
                    modifier = Modifier
                        .width(72.dp)
                        .height(36.dp)
                        .layoutElementModifier(
                            layout = layoutState.shoulderL,
                            elementId = "shoulder_l",
                            isEditing = isEditingLayout,
                            isSelected = selectedElementId == "shoulder_l",
                            shape = RoundedCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
                            onSelect = onSelectElement,
                            onMove = onMoveElement
                        ),
                    shape = RoundedCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
                    color = Color(0xFF2C2C3E),
                    fontSize = 15.sp,
                    isEditing = isEditingLayout,
                    onPressState = { pressed ->
                        actionMask = if (pressed) actionMask or GbaBridge.KEY_L else actionMask and GbaBridge.KEY_L.inv()
                    }
                )

                // R Button
                GamepadButton(
                    text = "R",
                    modifier = Modifier
                        .width(72.dp)
                        .height(36.dp)
                        .layoutElementModifier(
                            layout = layoutState.shoulderR,
                            elementId = "shoulder_r",
                            isEditing = isEditingLayout,
                            isSelected = selectedElementId == "shoulder_r",
                            shape = RoundedCornerShape(topEnd = 14.dp, bottomStart = 14.dp),
                            onSelect = onSelectElement,
                            onMove = onMoveElement
                        ),
                    shape = RoundedCornerShape(topEnd = 14.dp, bottomStart = 14.dp),
                    color = Color(0xFF2C2C3E),
                    fontSize = 15.sp,
                    isEditing = isEditingLayout,
                    onPressState = { pressed ->
                        actionMask = if (pressed) actionMask or GbaBridge.KEY_R else actionMask and GbaBridge.KEY_R.inv()
                    }
                )
            }

            // 3. Lower Controls (D-Pad & Action Cluster)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                // Left: Circular Precision D-Pad
                CircularDPad(
                    modifier = Modifier
                        .size(140.dp)
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 6.dp)
                        .layoutElementModifier(
                            layout = layoutState.dpad,
                            elementId = "dpad",
                            isEditing = isEditingLayout,
                            isSelected = selectedElementId == "dpad",
                            shape = CircleShape,
                            onSelect = onSelectElement,
                            onMove = onMoveElement
                        ),
                    isEditing = isEditingLayout,
                    onDirectionChanged = { mask ->
                        dpadMask = mask
                    }
                )

                // Right: Action Buttons Cluster (B, A, Turbo B, Turbo A, Combo A+B)
                ActionButtonsCluster(
                    modifier = Modifier
                        .size(width = 140.dp, height = 155.dp)
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 6.dp)
                        .layoutElementModifier(
                            layout = layoutState.actionCluster,
                            elementId = "actions",
                            isEditing = isEditingLayout,
                            isSelected = selectedElementId == "actions",
                            shape = RoundedCornerShape(20.dp),
                            onSelect = onSelectElement,
                            onMove = onMoveElement
                        ),
                    isEditing = isEditingLayout,
                    onMaskChanged = { mask ->
                        actionMask = (actionMask and (GbaBridge.KEY_L or GbaBridge.KEY_R or GbaBridge.KEY_START or GbaBridge.KEY_SELECT)) or mask
                    }
                )
            }
        }
    } else {
        // ==================== LANDSCAPE MODE ====================
        // 1. Top Toolbar (FPS on left, Select & Start in middle, Menu on right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: FPS & Thermal Status Badge
            Row(
                modifier = Modifier
                    .background(Color(0xCC14141E), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isFastForward) Color(0xFFFF9100) else Color(0xFF00E676), CircleShape)
                )
                Text(
                    text = fpsText,
                    color = if (isFastForward) Color(0xFFFFB74D) else Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Center: SELECT & START (Draggable & Editable)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.layoutElementModifier(
                    layout = layoutState.selectStart,
                    elementId = "select_start",
                    isEditing = isEditingLayout,
                    isSelected = selectedElementId == "select_start",
                    shape = RoundedCornerShape(14.dp),
                    onSelect = onSelectElement,
                    onMove = onMoveElement
                )
            ) {
                PillButton(
                    text = "SELECT",
                    isEditing = isEditingLayout,
                    onPressState = { pressed ->
                        actionMask = if (pressed) actionMask or GbaBridge.KEY_SELECT else actionMask and GbaBridge.KEY_SELECT.inv()
                    }
                )

                PillButton(
                    text = "START",
                    isEditing = isEditingLayout,
                    onPressState = { pressed ->
                        actionMask = if (pressed) actionMask or GbaBridge.KEY_START else actionMask and GbaBridge.KEY_START.inv()
                    }
                )
            }

            // Right: Fullscreen Toggle and Hamburger Menu Button (☰)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Full Screen Toggle Button (⛶)
                IconButton(
                    onClick = onToggleFullScreen,
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            if (isFullScreen) Color(0xCC00838F) else Color(0x992C2C3E),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullScreen) "Thu nhỏ 3:2" else "Toàn màn hình",
                        tint = if (isFullScreen) Color(0xFF00E5FF) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0x992C2C3E), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (!hideGamepad || isEditingLayout) {
            // 2. Shoulder L Button (Top-Left in Landscape)
            GamepadButton(
                text = "L",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 46.dp)
                    .width(76.dp)
                    .height(40.dp)
                    .layoutElementModifier(
                        layout = layoutState.shoulderL,
                        elementId = "shoulder_l",
                        isEditing = isEditingLayout,
                        isSelected = selectedElementId == "shoulder_l",
                        shape = RoundedCornerShape(12.dp),
                        onSelect = onSelectElement,
                        onMove = onMoveElement
                    ),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2C2C3E),
                fontSize = 16.sp,
                isEditing = isEditingLayout,
                onPressState = { pressed ->
                    actionMask = if (pressed) actionMask or GbaBridge.KEY_L else actionMask and GbaBridge.KEY_L.inv()
                }
            )

            // 3. Shoulder R Button (Top-Right in Landscape)
            GamepadButton(
                text = "R",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 46.dp)
                    .width(76.dp)
                    .height(40.dp)
                    .layoutElementModifier(
                        layout = layoutState.shoulderR,
                        elementId = "shoulder_r",
                        isEditing = isEditingLayout,
                        isSelected = selectedElementId == "shoulder_r",
                        shape = RoundedCornerShape(12.dp),
                        onSelect = onSelectElement,
                        onMove = onMoveElement
                    ),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2C2C3E),
                fontSize = 16.sp,
                isEditing = isEditingLayout,
                onPressState = { pressed ->
                    actionMask = if (pressed) actionMask or GbaBridge.KEY_R else actionMask and GbaBridge.KEY_R.inv()
                }
            )

            // 4. D-Pad (Bottom-Left in Landscape)
            CircularDPad(
                modifier = Modifier
                    .size(145.dp)
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
                    .layoutElementModifier(
                        layout = layoutState.dpad,
                        elementId = "dpad",
                        isEditing = isEditingLayout,
                        isSelected = selectedElementId == "dpad",
                        shape = CircleShape,
                        onSelect = onSelectElement,
                        onMove = onMoveElement
                    ),
                isEditing = isEditingLayout,
                onDirectionChanged = { mask ->
                    dpadMask = mask
                }
            )

            // 5. Action Buttons Cluster (Bottom-Right in Landscape)
            ActionButtonsCluster(
                modifier = Modifier
                    .size(width = 145.dp, height = 155.dp)
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .layoutElementModifier(
                        layout = layoutState.actionCluster,
                        elementId = "actions",
                        isEditing = isEditingLayout,
                        isSelected = selectedElementId == "actions",
                        shape = RoundedCornerShape(20.dp),
                        onSelect = onSelectElement,
                        onMove = onMoveElement
                    ),
                isEditing = isEditingLayout,
                onMaskChanged = { mask ->
                    actionMask = (actionMask and (GbaBridge.KEY_L or GbaBridge.KEY_R or GbaBridge.KEY_START or GbaBridge.KEY_SELECT)) or mask
                }
            )
        }
    }
}
}

@Composable
fun Modifier.layoutElementModifier(
    layout: ElementLayout,
    elementId: String,
    isEditing: Boolean,
    isSelected: Boolean,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    onSelect: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit
): Modifier {
    val density = LocalDensity.current
    val base = this
        .offset(x = layout.offsetX.dp, y = layout.offsetY.dp)
        .graphicsLayer {
            scaleX = layout.scale
            scaleY = layout.scale
            transformOrigin = TransformOrigin.Center
        }

    return if (!isEditing) {
        base
    } else {
        base
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) Color(0xFF00E5FF) else Color(0x66FFFFFF),
                shape = shape
            )
            .background(
                color = if (isSelected) Color(0x2200E5FF) else Color(0x11FFFFFF),
                shape = shape
            )
            .pointerInput(elementId, isSelected) {
                detectDragGestures(
                    onDragStart = { onSelect(elementId) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dxDp = dragAmount.x / density.density
                        val dyDp = dragAmount.y / density.density
                        onMove(elementId, dxDp, dyDp)
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onSelect(elementId)
            }
    }
}

@Composable
fun CircularDPad(
    modifier: Modifier = Modifier,
    isEditing: Boolean = false,
    onDirectionChanged: (Int) -> Unit
) {
    var touchPos by remember { mutableStateOf<Offset?>(null) }

    val pointerMod = if (isEditing) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
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
    }

    Canvas(modifier = modifier.then(pointerMod)) {
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
    isEditing: Boolean = false,
    onMaskChanged: (Int) -> Unit
) {
    var pressedMask by remember { mutableStateOf(0) }
    var isTurboAPressed by remember { mutableStateOf(false) }
    var isTurboBPressed by remember { mutableStateOf(false) }
    var turboMask by remember { mutableStateOf(0) }

    LaunchedEffect(isTurboAPressed) {
        if (isTurboAPressed && !isEditing) {
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
        if (isTurboBPressed && !isEditing) {
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
        if (!isEditing) {
            onMaskChanged(pressedMask or turboMask)
        }
    }

    Box(modifier = modifier) {
        // Combo A+B Button (Top Center of Action Cluster)
        GamepadButton(
            text = "A+B",
            modifier = Modifier
                .size(38.dp)
                .align(Alignment.TopCenter),
            shape = CircleShape,
            color = Color(0xFF4A148C),
            fontSize = 11.sp,
            isEditing = isEditing,
            onPressState = { pressed ->
                pressedMask = if (pressed) pressedMask or (GbaBridge.KEY_A or GbaBridge.KEY_B) else pressedMask and (GbaBridge.KEY_A or GbaBridge.KEY_B).inv()
            }
        )

        // Turbo B Button (Above B)
        GamepadButton(
            text = "TB",
            modifier = Modifier
                .size(38.dp)
                .align(Alignment.TopStart)
                .offset(x = 8.dp, y = 46.dp),
            shape = CircleShape,
            color = Color(0xFFE65100),
            fontSize = 12.sp,
            isEditing = isEditing,
            onPressState = { pressed ->
                isTurboBPressed = pressed
            }
        )

        // Turbo A Button (Above A)
        GamepadButton(
            text = "TA",
            modifier = Modifier
                .size(38.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = 32.dp),
            shape = CircleShape,
            color = Color(0xFF00838F),
            fontSize = 12.sp,
            isEditing = isEditing,
            onPressState = { pressed ->
                isTurboAPressed = pressed
            }
        )

        // B Button (Left / Lower)
        GamepadButton(
            text = "B",
            modifier = Modifier
                .size(50.dp)
                .align(Alignment.BottomStart)
                .offset(x = 6.dp, y = (-2).dp),
            shape = CircleShape,
            color = Color(0xFFB71C1C),
            fontSize = 20.sp,
            isEditing = isEditing,
            onPressState = { pressed ->
                pressedMask = if (pressed) pressedMask or GbaBridge.KEY_B else pressedMask and GbaBridge.KEY_B.inv()
            }
        )

        // A Button (Right / Higher - Standard GBA Ergonomic Diagonal)
        GamepadButton(
            text = "A",
            modifier = Modifier
                .size(50.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = (-18).dp),
            shape = CircleShape,
            color = Color(0xFF1B5E20),
            fontSize = 20.sp,
            isEditing = isEditing,
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
    isEditing: Boolean = false,
    onPressState: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val pointerMod = if (isEditing) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                isPressed = true
                onPressState(true)

                eventStreamHasUpOrCancel(down.id)
                isPressed = false
                onPressState(false)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isPressed) color.copy(alpha = 1.0f) else color.copy(alpha = 0.75f))
            .then(pointerMod),
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
    isEditing: Boolean = false,
    onPressState: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val pointerMod = if (isEditing) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                isPressed = true
                onPressState(true)

                eventStreamHasUpOrCancel(down.id)
                isPressed = false
                onPressState(false)
            }
        }
    }

    Box(
        modifier = modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPressed) Color(0xFF00E5FF) else Color(0x992C2C3E))
            .then(pointerMod),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isPressed) Color.Black else Color(0xFFD0D0E0),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
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
