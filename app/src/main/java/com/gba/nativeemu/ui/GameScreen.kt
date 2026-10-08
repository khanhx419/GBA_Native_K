package com.gba.nativeemu.ui

import android.content.Context
import android.content.res.Configuration
import android.opengl.GLSurfaceView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gba.nativeemu.cheat.CheatRepository
import com.gba.nativeemu.core.GbaBridge
import com.gba.nativeemu.scanner.MemoryScanner
import com.gba.nativeemu.storage.CustomLayoutState
import com.gba.nativeemu.storage.EmulatorSettings
import com.gba.nativeemu.storage.SaveRepository
import com.gba.nativeemu.storage.SettingsManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

@Composable
fun GameScreen(
    gameTitle: String,
    saveRepository: SaveRepository,
    cheatRepository: CheatRepository,
    settingsManager: SettingsManager,
    settings: EmulatorSettings,
    onSettingsChanged: (EmulatorSettings) -> Unit,
    onResetGame: () -> Unit,
    onCloseRom: () -> Unit,
    onExportBattery: () -> Unit,
    onImportBattery: () -> Unit,
    onExportJson: () -> Unit = {},
    onImportJson: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Thread-safe / volatile holders for GLRenderer thread
    val fastForwardSpeedRef = remember { AtomicReference(settings.fastForwardSpeed) }
    val isFastForwardRef = remember { AtomicBoolean(false) }
    val currentKeyMaskRef = remember { AtomicInteger(0) }

    // Keep fastForwardSpeedRef synced when settings change
    LaunchedEffect(settings.fastForwardSpeed) {
        fastForwardSpeedRef.set(settings.fastForwardSpeed)
        if (isFastForwardRef.get()) {
            GbaBridge.nativeSetFastForward(settings.fastForwardSpeed)
        }
    }

    var isFastForward by remember { mutableStateOf(false) }
    var fpsText by remember { mutableStateOf("60 FPS • Mát máy") }

    // Layout Customization State
    var isEditingLayout by remember { mutableStateOf(false) }
    var selectedElementId by remember { mutableStateOf("dpad") }
    var layoutState by remember(isLandscape) {
        mutableStateOf(settingsManager.loadLayout(isLandscape))
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSaveStateDialog by remember { mutableStateOf(false) }
    var showCheatDialog by remember { mutableStateOf(false) }
    var showMemoryScannerDialog by remember { mutableStateOf(false) }
    val memoryScanner = remember { MemoryScanner() }

    LaunchedEffect(gameTitle) {
        cheatRepository.applyActiveCheats(gameTitle)
    }

    // GLSurfaceView with 59.73 FPS Accumulator Pacer (Ice Cool / Anti-Overheating)
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true

            setRenderer(object : GLSurfaceView.Renderer {
                override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                    GbaBridge.nativeSurfaceCreated()
                    GbaBridge.nativeSetFilter(GbaBridge.FILTER_NEAREST)
                    GbaBridge.nativeSetAspectRatio(settings.aspectMode)
                }

                override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                    GbaBridge.nativeSurfaceChanged(width, height)
                }

                private var lastNanoTime = 0L
                private var accumulatorNs = 0L
                private val GBA_FRAME_TIME_NS = 16_742_706L // 1,000,000,000 / 59.7275 FPS
                private var lastFpsTime = System.currentTimeMillis()
                private var gbaFramesCount = 0

                override fun onDrawFrame(gl: GL10?) {
                    val now = System.nanoTime()
                    if (lastNanoTime == 0L) {
                        lastNanoTime = now
                    }

                    var deltaNs = now - lastNanoTime
                    lastNanoTime = now

                    // Prevent spiral of death if app was backgrounded/stalled
                    if (deltaNs > 150_000_000L) {
                        deltaNs = 150_000_000L
                    }

                    val ff = isFastForwardRef.get()
                    val speed = if (ff) fastForwardSpeedRef.get() else 1.0f
                    accumulatorNs += (deltaNs * speed).toLong()

                    var rendered = false
                    var steps = 0
                    // Scale steps to fast-forward multiplier (e.g. up to 24-30 steps for 8x)
                    val maxSteps = if (ff) (speed * 3f).toInt().coerceIn(8, 32) else 4
                    val keyMask = currentKeyMaskRef.get()

                    while (accumulatorNs >= GBA_FRAME_TIME_NS && steps < maxSteps) {
                        accumulatorNs -= GBA_FRAME_TIME_NS
                        steps++
                        if (accumulatorNs < GBA_FRAME_TIME_NS || steps == maxSteps) {
                            // Last step: render video and feed audio
                            GbaBridge.nativeRenderFrame(keyMask)
                            rendered = true
                            gbaFramesCount++
                        } else {
                            // Intermediate step (during fast forward): advance emulation without updating GL texture
                            GbaBridge.nativeStepFrame(keyMask)
                            gbaFramesCount++
                        }
                    }

                    // Reset accumulator only if it got way too far ahead (scaled to fast-forward speed)
                    val maxLagNs = if (ff) (GBA_FRAME_TIME_NS * speed * 2.5).toLong().coerceAtLeast(250_000_000L) else (GBA_FRAME_TIME_NS * 3)
                    if (accumulatorNs > maxLagNs) {
                        accumulatorNs = 0L
                    }

                    // On 90Hz/120Hz/144Hz displays when no new GBA frame is due:
                    // Just redraw existing quad in 0.05ms with 0 GBA emulation CPU load!
                    if (!rendered) {
                        GbaBridge.nativeRedrawFrame()
                    }

                    // Update FPS display once per second
                    val currentMs = System.currentTimeMillis()
                    if (currentMs - lastFpsTime >= 1000) {
                        val fps = gbaFramesCount
                        gbaFramesCount = 0
                        lastFpsTime = currentMs
                        val modeText = if (ff) "FF ${speed.toInt()}x" else "Mát máy"
                        fpsText = "$fps FPS • $modeText"
                    }
                }
            })
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    // Lifecycle Observer: Stop OpenGL rendering when paused or backgrounded (Saves 100% battery)
    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onPause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- 1. OPENGL GAME SURFACE ---
        AndroidView(
            factory = { glView },
            modifier = Modifier.fillMaxSize()
        )

        // --- 2. VIRTUAL GAMEPAD OVERLAY ---
        VirtualGamepad(
            modifier = Modifier.fillMaxSize(),
            opacity = settings.gamepadOpacity,
            fpsText = fpsText,
            isFastForward = isFastForward,
            isLandscape = isLandscape,
            layoutState = layoutState,
            movementMode = settings.movementMode,
            isEditingLayout = isEditingLayout,
            isFullScreen = settings.aspectMode == GbaBridge.ASPECT_STRETCH || settings.aspectMode == GbaBridge.ASPECT_FULL,
            hideGamepad = settings.hideGamepad,
            onToggleFullScreen = {
                val newAspect = if (settings.aspectMode == GbaBridge.ASPECT_STRETCH || settings.aspectMode == GbaBridge.ASPECT_FULL) {
                    GbaBridge.ASPECT_FIT
                } else {
                    GbaBridge.ASPECT_STRETCH
                }
                val newSettings = settings.copy(aspectMode = newAspect)
                onSettingsChanged(newSettings)
                GbaBridge.nativeSetAspectRatio(newAspect)
                val msg = if (newAspect == GbaBridge.ASPECT_STRETCH) {
                    "🖥️ Toàn màn hình (Thu hẹp 2 bên, tránh tai thỏ)"
                } else {
                    "📱 Chế độ Tỉ lệ chuẩn (3:2)"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            onSelectElement = { id -> selectedElementId = id },
            onMoveElement = { id, dx, dy ->
                layoutState = layoutState.updateElement(id) {
                    it.copy(offsetX = it.offsetX + dx, offsetY = it.offsetY + dy)
                }
            },
            onMenuClick = { showSettingsDialog = true },
            onKeyMaskChanged = { mask ->
                currentKeyMaskRef.set(mask)
            }
        )

        // --- 3. LAYOUT EDITOR HUD OVERLAY ---
        if (isEditingLayout) {
            LayoutEditorOverlay(
                isLandscape = isLandscape,
                selectedElementId = selectedElementId,
                currentScale = layoutState.getElement(selectedElementId).scale,
                onDecreaseScale = {
                    val current = layoutState.getElement(selectedElementId)
                    val newScale = ((current.scale - 0.1f) * 10f).roundToInt() / 10f
                    val clampedScale = newScale.coerceIn(0.6f, 1.6f)
                    layoutState = layoutState.updateElement(selectedElementId) { it.copy(scale = clampedScale) }
                },
                onIncreaseScale = {
                    val current = layoutState.getElement(selectedElementId)
                    val newScale = ((current.scale + 0.1f) * 10f).roundToInt() / 10f
                    val clampedScale = newScale.coerceIn(0.6f, 1.6f)
                    layoutState = layoutState.updateElement(selectedElementId) { it.copy(scale = clampedScale) }
                },
                onResetLayout = {
                    layoutState = CustomLayoutState()
                    Toast.makeText(context, "Đã đặt lại vị trí & cỡ mặc định", Toast.LENGTH_SHORT).show()
                },
                onCancelEditing = {
                    layoutState = settingsManager.loadLayout(isLandscape)
                    isEditingLayout = false
                },
                onSaveLayout = {
                    settingsManager.saveLayout(isLandscape, layoutState)
                    isEditingLayout = false
                    Toast.makeText(context, "✅ Đã lưu bố cục phím!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // --- 4. DIALOGS ---
        if (showSettingsDialog) {
            SettingsDialog(
                settings = settings,
                isFastForward = isFastForward,
                onToggleFastForward = {
                    val nextFf = !isFastForward
                    isFastForward = nextFf
                    isFastForwardRef.set(nextFf)
                    val currentSpeed = fastForwardSpeedRef.get()
                    GbaBridge.nativeSetFastForward(if (nextFf) currentSpeed else 1.0f)
                    val status = if (nextFf) "⚡ Tua nhanh ${currentSpeed.toInt()}x" else "⏳ Tốc độ chuẩn 1.0x"
                    Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                },
                onSettingsChanged = { newSettings ->
                    fastForwardSpeedRef.set(newSettings.fastForwardSpeed)
                    onSettingsChanged(newSettings)
                    GbaBridge.nativeSetAspectRatio(newSettings.aspectMode)
                    GbaBridge.nativeSetAudioVolume(newSettings.volume)
                    GbaBridge.nativeSetAudioMute(newSettings.isMuted)
                    GbaBridge.nativeSetFastForward(if (isFastForwardRef.get()) newSettings.fastForwardSpeed else 1.0f)
                },
                onOpenSaveStates = {
                    showSettingsDialog = false
                    showSaveStateDialog = true
                },
                onOpenCheats = {
                    showSettingsDialog = false
                    showCheatDialog = true
                },
                onOpenScanner = {
                    showSettingsDialog = false
                    showMemoryScannerDialog = true
                },
                onOpenLayoutEditor = {
                    showSettingsDialog = false
                    isEditingLayout = true
                },
                onResetGame = {
                    GbaBridge.nativeReset()
                    cheatRepository.applyActiveCheats(gameTitle)
                    onResetGame()
                    Toast.makeText(context, "🔄 Đã khởi động lại game", Toast.LENGTH_SHORT).show()
                },
                onCloseRom = {
                    saveRepository.saveBattery(gameTitle)
                    GbaBridge.nativeUnloadRom()
                    onCloseRom()
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (showSaveStateDialog) {
            SaveStateDialog(
                gameName = gameTitle,
                saveRepository = saveRepository,
                onSaveState = { slot ->
                    val ok = saveRepository.saveState(gameTitle, slot)
                    Toast.makeText(context, if (ok) "💾 Đã lưu Slot $slot" else "❌ Lỗi lưu", Toast.LENGTH_SHORT).show()
                },
                onLoadState = { slot ->
                    val ok = saveRepository.loadState(gameTitle, slot)
                    Toast.makeText(context, if (ok) "⚡ Đã tải Slot $slot" else "❌ Lỗi tải", Toast.LENGTH_SHORT).show()
                },
                onDeleteState = { slot ->
                    val ok = saveRepository.deleteState(gameTitle, slot)
                    Toast.makeText(context, if (ok) "🗑️ Đã xóa Slot $slot" else "❌ Lỗi xóa", Toast.LENGTH_SHORT).show()
                },
                onExportBattery = onExportBattery,
                onImportBattery = onImportBattery,
                onExportJson = onExportJson,
                onImportJson = onImportJson,
                onDismiss = { showSaveStateDialog = false }
            )
        }

        if (showCheatDialog) {
            CheatDialog(
                gameTitle = gameTitle,
                cheatRepository = cheatRepository,
                onDismiss = { showCheatDialog = false }
            )
        }

        if (showMemoryScannerDialog) {
            MemoryScannerDialog(
                gameTitle = gameTitle,
                memoryScanner = memoryScanner,
                cheatRepository = cheatRepository,
                onDismiss = { showMemoryScannerDialog = false }
            )
        }
    }
}

@Composable
fun LayoutEditorOverlay(
    isLandscape: Boolean,
    selectedElementId: String,
    currentScale: Float,
    onDecreaseScale: () -> Unit,
    onIncreaseScale: () -> Unit,
    onResetLayout: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveLayout: () -> Unit
) {
    // Draggable position state for the editor menu
    var menuOffset by remember { mutableStateOf(Offset.Zero) }

    val elementName = when (selectedElementId) {
        "dpad" -> "🎛️ D-Pad / Joystick"
        "btn_a" -> "🟢 Nút A"
        "btn_b" -> "🔴 Nút B"
        "btn_ta" -> "⚡ Nút TA (Turbo A)"
        "btn_tb" -> "⚡ Nút TB (Turbo B)"
        "shoulder_l" -> "🛡️ Nút L"
        "shoulder_r" -> "🛡️ Nút R"
        "select_start" -> "⏸️ Select / Start"
        else -> "🎮 Phím"
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .offset { IntOffset(menuOffset.x.roundToInt(), menuOffset.y.roundToInt()) }
                .padding(top = if (isLandscape) 12.dp else 42.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth(if (isLandscape) 0.65f else 0.95f)
                .wrapContentHeight()
                .shadow(12.dp, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        menuOffset += dragAmount
                    }
                },
            colors = CardDefaults.cardColors(containerColor = Color(0xE6141424)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, Color(0xFF00E5FF))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Drag handle + Selected Button Name + Scale Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selected button indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⠿", color = Color(0xFF00E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = elementName,
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Scale controls: [-] 100% [+]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Cỡ:", fontSize = 11.sp, color = Color.White)
                        IconButton(
                            onClick = onDecreaseScale,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFF2C2C3E), RoundedCornerShape(6.dp))
                        ) {
                            Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1E1E28), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val scalePercent = (currentScale * 100).roundToInt()
                            Text(
                                "$scalePercent%",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = onIncreaseScale,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFF2C2C3E), RoundedCornerShape(6.dp))
                        ) {
                            Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                // Row 2: Touch hint + Action buttons (Đặt lại, Hủy, LƯU)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👆 Kéo phím trên màn • Kéo bảng để dời",
                        color = Color(0xFFFFD54F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onResetLayout,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Đặt lại", fontSize = 11.sp, color = Color(0xFFFFB74D))
                        }

                        OutlinedButton(
                            onClick = onCancelEditing,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Hủy", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = onSaveLayout,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("LƯU", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
