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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gba.nativeemu.core.GbaBridge
import com.gba.nativeemu.storage.CustomLayoutState
import com.gba.nativeemu.storage.ElementLayout
import com.gba.nativeemu.storage.EmulatorSettings
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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
    movementMode: Int = EmulatorSettings.MOVEMENT_DPAD,
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

    // Turbo button states
    var isTurboAPressed by remember { mutableStateOf(false) }
    var isTurboBPressed by remember { mutableStateOf(false) }
    var turboMask by remember { mutableStateOf(0) }

    LaunchedEffect(isTurboAPressed) {
        if (isTurboAPressed && !isEditingLayout) {
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
        if (isTurboBPressed && !isEditingLayout) {
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

    LaunchedEffect(dpadMask, actionMask, turboMask) {
        if (!isEditingLayout) {
            onKeyMaskChanged(dpadMask or actionMask or turboMask)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .displayCutoutPadding()
            .alpha(if (isEditingLayout) 1.0f else opacity)
    ) {
        if (!isLandscape) {
            // ==================== PORTRAIT MODE ====================
            // 1. Top Toolbar (FPS on left, Select & Start & Fullscreen & Menu on right)
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

                // Right: SELECT, START, Fullscreen, and Hamburger Menu (☰)
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

                // 3. Movement Control (Bottom-Left: D-Pad / Fixed Joystick / Floating Joystick)
                VirtualMovementControl(
                    modifier = Modifier
                        .size(if (movementMode == EmulatorSettings.MOVEMENT_JOYSTICK_FLOATING) 180.dp else 140.dp)
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 8.dp)
                        .layoutElementModifier(
                            layout = layoutState.dpad,
                            elementId = "dpad",
                            isEditing = isEditingLayout,
                            isSelected = selectedElementId == "dpad",
                            shape = if (movementMode == EmulatorSettings.MOVEMENT_JOYSTICK_FLOATING) RoundedCornerShape(16.dp) else CircleShape,
                            onSelect = onSelectElement,
                            onMove = onMoveElement
                        ),
                    movementMode = movementMode,
                    isEditing = isEditingLayout,
                    onDirectionChanged = { mask -> dpadMask = mask }
                )

                // 4. Action Buttons (Individually Draggable: B, A, TB, TA - With Wide Ergonomic Spacing)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxSize()
                ) {
                    // Button B (Left / Lower)
                    GamepadButton(
                        text = "B",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 84.dp, bottom = 22.dp)
                            .size(54.dp)
                            .layoutElementModifier(
                                layout = layoutState.buttonB,
                                elementId = "btn_b",
                                isEditing = isEditingLayout,
                                isSelected = selectedElementId == "btn_b",
                                shape = CircleShape,
                                onSelect = onSelectElement,
                                onMove = onMoveElement
                            ),
                        shape = CircleShape,
                        color = Color(0xFFB71C1C),
                        fontSize = 21.sp,
                        isEditing = isEditingLayout,
                        onPressState = { pressed ->
                            actionMask = if (pressed) actionMask or GbaBridge.KEY_B else actionMask and GbaBridge.KEY_B.inv()
                        }
                    )

                    // Button A (Right / Higher - Comfortable spacing from B)
                    GamepadButton(
                        text = "A",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 48.dp)
                            .size(54.dp)
                            .layoutElementModifier(
                                layout = layoutState.buttonA,
                                elementId = "btn_a",
                                isEditing = isEditingLayout,
                                isSelected = selectedElementId == "btn_a",
                                shape = CircleShape,
                                onSelect = onSelectElement,
                                onMove = onMoveElement
                            ),
                        shape = CircleShape,
                        color = Color(0xFF1B5E20),
                        fontSize = 21.sp,
                        isEditing = isEditingLayout,
                        onPressState = { pressed ->
                            actionMask = if (pressed) actionMask or GbaBridge.KEY_A else actionMask and GbaBridge.KEY_A.inv()
                        }
                    )

                    // Turbo B Button (Above B)
                    GamepadButton(
                        text = "TB",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 84.dp, bottom = 86.dp)
                            .size(42.dp)
                            .layoutElementModifier(
                                layout = layoutState.buttonTB,
                                elementId = "btn_tb",
                                isEditing = isEditingLayout,
                                isSelected = selectedElementId == "btn_tb",
                                shape = CircleShape,
                                onSelect = onSelectElement,
                                onMove = onMoveElement
                            ),
                        shape = CircleShape,
                        color = Color(0xFFE65100),
                        fontSize = 13.sp,
                        isEditing = isEditingLayout,
                        onPressState = { pressed ->
                            isTurboBPressed = pressed
                        }
                    )

                    // Turbo A Button (Above A)
                    GamepadButton(
                        text = "TA",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 112.dp)
                            .size(42.dp)
                            .layoutElementModifier(
                                layout = layoutState.buttonTA,
                                elementId = "btn_ta",
                                isEditing = isEditingLayout,
                                isSelected = selectedElementId == "btn_ta",
                                shape = CircleShape,
                                onSelect = onSelectElement,
                                onMove = onMoveElement
                            ),
                        shape = CircleShape,
                        color = Color(0xFF00838F),
                        fontSize = 13.sp,
                        isEditing = isEditingLayout,
                        onPressState = { pressed ->
                            isTurboAPressed = pressed
                        }
                    )
                }
            }
        } else {
            // ==================== LANDSCAPE MODE ====================
            // 1. Top Toolbar (FPS on left, Select & Start in middle, Fullscreen & Menu on right)
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

                // 4. Movement Control (Bottom-Left in Landscape)
                VirtualMovementControl(
                    modifier = Modifier
                        .size(if (movementMode == EmulatorSettings.MOVEMENT_JOYSTICK_FLOATING) 200.dp else 145.dp)
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
                        .layoutElementModifier(
                            layout = layoutState.dpad,
                            elementId = "dpad",
                            isEditing = isEditingLayout,
                            isSelected = selectedElementId == "dpad",
                            shape = if (movementMode == EmulatorSettings.MOVEMENT_JOYSTICK_FLOATING) RoundedCornerShape(16.dp) else CircleShape,
                            onSelect = onSelectElement,
                            onMove = onMoveElement
                        ),
                    movementMode = movementMode,
                    isEditing = isEditingLayout,
                    onDirectionChanged = { mask -> dpadMask = mask }
                )

                // 5. Action Buttons (Bottom-Right in Landscape - Individually Draggable: B, A, TB, TA)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxSize()
                ) {
                    // Button B (Left / Lower)
                    GamepadButton(
                        text = "B",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 96.dp, bottom = 24.dp)
                            .size(58.dp)
                            .layoutElementModifier(
                                layout = layoutState.buttonB,
                                elementId = "btn_b",
                                isEditing = isEditingLayout,
                                isSelected = selectedElementId == "btn_b",
                                shape = CircleShape,
                                onSelect = onSelectElement,
                                onMove = onMoveElement
                            ),
                        shape = CircleShape,
                        color = Color(0xFFB71C1C),
                        fontSize = 22.sp,
                        isEditing = isEditingLayout,
                        onPressState = { pressed ->
                            actionMask = if (pressed) actionMask or GbaBridge.KEY_B else actionMask and GbaBridge.KEY_B.inv()
                        }
                    )

                    // Button A (Right / Higher)
                    GamepadButton(
                        text = "A",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 54.dp)
                            .size(58.dp)
                            .layoutElementModifier(
                                layout = layoutState.buttonA,
                                elementId = "btn_a",
                                isEditing = isEditingLayout,
                                isSelected = selectedElementId == "btn_a",
                                shape = CircleShape,
                                onSelect = onSelectElement,
                                onMove = onMoveElement
                            ),
                        shape = CircleShape,
                        color = Color(0xFF1B5E20),
                        fontSize = 22.sp,
                        isEditing = isEditingLayout,
                        onPressState = { pressed ->
                            actionMask = if (pressed) actionMask or GbaBridge.KEY_A else actionMask and GbaBridge.KEY_A.inv()
                        }
                    )

                    // Turbo B Button
                    GamepadButton(
                        text = "TB",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 96.dp, bottom = 92.dp)
                            .size(44.dp)
                            .layoutElementModifier(
                                layout = layoutState.buttonTB,
                                elementId = "btn_tb",
                                isEditing = isEditingLayout,
                                isSelected = selectedElementId == "btn_tb",
                                shape = CircleShape,
                                onSelect = onSelectElement,
                                onMove = onMoveElement
                            ),
                        shape = CircleShape,
                        color = Color(0xFFE65100),
                        fontSize = 14.sp,
                        isEditing = isEditingLayout,
                        onPressState = { pressed ->
                            isTurboBPressed = pressed
                        }
                    )

                    // Turbo A Button
                    GamepadButton(
                        text = "TA",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 122.dp)
                            .size(44.dp)
                            .layoutElementModifier(
                                layout = layoutState.buttonTA,
                                elementId = "btn_ta",
                                isEditing = isEditingLayout,
                                isSelected = selectedElementId == "btn_ta",
                                shape = CircleShape,
                                onSelect = onSelectElement,
                                onMove = onMoveElement
                            ),
                        shape = CircleShape,
                        color = Color(0xFF00838F),
                        fontSize = 14.sp,
                        isEditing = isEditingLayout,
                        onPressState = { pressed ->
                            isTurboAPressed = pressed
                        }
                    )
                }
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
fun VirtualMovementControl(
    modifier: Modifier = Modifier,
    movementMode: Int,
    isEditing: Boolean = false,
    onDirectionChanged: (Int) -> Unit
) {
    when (movementMode) {
        EmulatorSettings.MOVEMENT_JOYSTICK_FIXED -> {
            FixedJoystick(
                modifier = modifier,
                isEditing = isEditing,
                onDirectionChanged = onDirectionChanged
            )
        }
        EmulatorSettings.MOVEMENT_JOYSTICK_FLOATING -> {
            FloatingJoystick(
                modifier = modifier,
                isEditing = isEditing,
                onDirectionChanged = onDirectionChanged
            )
        }
        else -> {
            CircularDPad(
                modifier = modifier,
                isEditing = isEditing,
                onDirectionChanged = onDirectionChanged
            )
        }
    }
}

@Composable
fun FixedJoystick(
    modifier: Modifier = Modifier,
    isEditing: Boolean = false,
    onDirectionChanged: (Int) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }

    val pointerMod = if (isEditing) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.width * 0.38f

                val updateKnob = { pos: Offset ->
                    val dx = pos.x - center.x
                    val dy = pos.y - center.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val clampedDist = dist.coerceAtMost(maxRadius)
                    val angle = atan2(dy.toDouble(), dx.toDouble())
                    val clampedDx = (cos(angle) * clampedDist).toFloat()
                    val clampedDy = (sin(angle) * clampedDist).toFloat()
                    knobOffset = Offset(clampedDx, clampedDy)
                    updateDirectionFromDelta(dx, dy, maxRadius, onDirectionChanged)
                }

                updateKnob(down.position)

                do {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.find { it.id == down.id }
                    if (pointer != null && pointer.pressed) {
                        updateKnob(pointer.position)
                    } else {
                        knobOffset = Offset.Zero
                        onDirectionChanged(0)
                        break
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    }

    Box(
        modifier = modifier.then(pointerMod),
        contentAlignment = Alignment.Center
    ) {
        // Base Circle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f

            drawCircle(color = Color(0xAA1E1E28), radius = radius, center = center)
            drawCircle(color = Color(0xFF00E5FF).copy(alpha = 0.5f), radius = radius, center = center, style = Stroke(width = 2.5.dp.toPx()))
            drawCircle(color = Color(0x44FFFFFF), radius = radius * 0.55f, center = center, style = Stroke(width = 1.5.dp.toPx()))

            val crossLen = radius * 0.2f
            drawLine(Color(0x8800E5FF), Offset(center.x, center.y - radius + 4.dp.toPx()), Offset(center.x, center.y - radius + 4.dp.toPx() + crossLen), strokeWidth = 2.dp.toPx())
            drawLine(Color(0x8800E5FF), Offset(center.x, center.y + radius - 4.dp.toPx()), Offset(center.x, center.y + radius - 4.dp.toPx() - crossLen), strokeWidth = 2.dp.toPx())
            drawLine(Color(0x8800E5FF), Offset(center.x - radius + 4.dp.toPx(), center.y), Offset(center.x - radius + 4.dp.toPx() + crossLen, center.y), strokeWidth = 2.dp.toPx())
            drawLine(Color(0x8800E5FF), Offset(center.x + radius - 4.dp.toPx(), center.y), Offset(center.x + radius - 4.dp.toPx() - crossLen, center.y), strokeWidth = 2.dp.toPx())
        }

        // Thumbstick Knob
        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
                .size(52.dp)
                .background(
                    if (knobOffset != Offset.Zero) Color(0xFF00E5FF) else Color(0xFF2C2C3E),
                    CircleShape
                )
                .border(2.dp, Color(0xFF80D8FF), CircleShape)
        )
    }
}

@Composable
fun FloatingJoystick(
    modifier: Modifier = Modifier,
    isEditing: Boolean = false,
    onDirectionChanged: (Int) -> Unit
) {
    var touchCenter by remember { mutableStateOf<Offset?>(null) }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }

    val pointerMod = if (isEditing) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                touchCenter = down.position
                knobOffset = Offset.Zero
                val maxRadius = 55.dp.toPx()

                val updateFromPos = { currentPos: Offset ->
                    val center = touchCenter ?: down.position
                    val dx = currentPos.x - center.x
                    val dy = currentPos.y - center.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val clampedDist = dist.coerceAtMost(maxRadius)
                    val angle = atan2(dy.toDouble(), dx.toDouble())
                    val clampedDx = (cos(angle) * clampedDist).toFloat()
                    val clampedDy = (sin(angle) * clampedDist).toFloat()
                    knobOffset = Offset(clampedDx, clampedDy)
                    updateDirectionFromDelta(dx, dy, maxRadius, onDirectionChanged)
                }

                updateFromPos(down.position)

                do {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.find { it.id == down.id }
                    if (pointer != null && pointer.pressed) {
                        updateFromPos(pointer.position)
                    } else {
                        touchCenter = null
                        knobOffset = Offset.Zero
                        onDirectionChanged(0)
                        break
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    }

    Box(
        modifier = modifier.then(pointerMod)
    ) {
        if (isEditing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(16.dp))
                    .background(Color(0x1500E5FF), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                FixedJoystickPreview(modifier = Modifier.size(110.dp))
            }
        } else if (touchCenter != null) {
            val center = touchCenter!!
            val baseRadius = 55.dp
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (center.x - baseRadius.toPx()).roundToInt(),
                            (center.y - baseRadius.toPx()).roundToInt()
                        )
                    }
                    .size(baseRadius * 2),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.width / 2f
                    drawCircle(Color(0xBB1E1E28), radius = r, center = c)
                    drawCircle(Color(0xFF00E5FF), radius = r, center = c, style = Stroke(2.5.dp.toPx()))
                    drawCircle(Color(0x44FFFFFF), radius = r * 0.55f, center = c, style = Stroke(1.5.dp.toPx()))
                }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
                        .size(46.dp)
                        .background(Color(0xFF00E5FF), CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chạm để di chuyển",
                    color = Color(0x55FFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun FixedJoystickPreview(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.width / 2f
            drawCircle(Color(0xAA1E1E28), radius = r, center = c)
            drawCircle(Color(0xFF00E5FF), radius = r, center = c, style = Stroke(2.dp.toPx()))
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFF00E5FF), CircleShape)
                .border(2.dp, Color.White, CircleShape)
        )
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

fun updateDirectionFromDelta(dx: Float, dy: Float, maxRadius: Float, onDirectionChanged: (Int) -> Unit) {
    val dist = sqrt(dx * dx + dy * dy)
    val deadZone = maxRadius * 0.16f
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
            .background(if (isPressed) color.copy(alpha = 1.0f) else color.copy(alpha = 0.80f))
            .border(1.5.dp, Color(0x55FFFFFF), shape)
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
