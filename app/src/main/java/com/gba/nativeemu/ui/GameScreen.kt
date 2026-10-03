package com.gba.nativeemu.ui

import android.content.Context
import android.content.res.Configuration
import android.opengl.GLSurfaceView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gba.nativeemu.core.GbaBridge
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
    settingsManager: SettingsManager,
    settings: EmulatorSettings,
    onSettingsChanged: (EmulatorSettings) -> Unit,
    onResetGame: () -> Unit,
    onCloseRom: () -> Unit,
    onExportBattery: () -> Unit,
    onImportBattery: () -> Unit
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
    val activeCheats = remember { mutableStateListOf<CheatItem>() }

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
                onSelectElement = { selectedElementId = it },
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
                onOpenLayoutEditor = {
                    showSettingsDialog = false
                    isEditingLayout = true
                },
                onResetGame = {
                    GbaBridge.nativeReset()
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
                onExportBattery = onExportBattery,
                onImportBattery = onImportBattery,
                onDismiss = { showSaveStateDialog = false }
            )
        }

        if (showCheatDialog) {
            CheatDialog(
                cheats = activeCheats,
                onAddCheat = { name, code ->
                    val ok = GbaBridge.nativeAddCheat(name, code, GbaBridge.CHEAT_AUTODETECT)
                    if (ok) {
                        activeCheats.add(CheatItem(name, code, true))
                        Toast.makeText(context, "✅ Đã thêm mã gian lận", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "❌ Mã không hợp lệ", Toast.LENGTH_SHORT).show()
                    }
                    ok
                },
                onClearAll = {
                    GbaBridge.nativeClearCheats()
                    activeCheats.clear()
                    Toast.makeText(context, "Đã xóa toàn bộ mã gian lận", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showCheatDialog = false }
            )
        }
    }
}

@Composable
fun LayoutEditorOverlay(
    isLandscape: Boolean,
    selectedElementId: String,
    currentScale: Float,
    onSelectElement: (String) -> Unit,
    onDecreaseScale: () -> Unit,
    onIncreaseScale: () -> Unit,
    onResetLayout: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveLayout: () -> Unit
) {
    // Dock at bottom by default in landscape so top controls (L, R, Select, Start) are never blocked!
    var isDockedAtTop by remember { mutableStateOf(!isLandscape) }
    var isMinimized by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = if (isDockedAtTop) (if (isLandscape) 6.dp else 36.dp) else 0.dp,
                bottom = if (!isDockedAtTop) (if (isLandscape) 6.dp else 24.dp) else 0.dp,
                start = 10.dp,
                end = 10.dp
            ),
        contentAlignment = if (isDockedAtTop) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        if (isMinimized) {
            // Minimized Floating Pill - Zero visual obstruction while dragging buttons
            Surface(
                onClick = { isMinimized = false },
                color = Color(0xD910101C),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🎮 Đang chỉnh phím (Kéo tự do)", fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2C2C3E), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Mở rộng ↗", fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.85f else 1.0f)
                    .wrapContentHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xD9141422)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.2.dp, Color(0xFF00E5FF))
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: Header with Dock Flip & Minimize
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎮 BỐ CỤC PHÍM",
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "👆 Kéo phím trên màn",
                                color = Color(0xFFFFD54F),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Dock Flip button: Lên / Xuống
                            IconButton(
                                onClick = { isDockedAtTop = !isDockedAtTop },
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFF2C2C3E), RoundedCornerShape(5.dp))
                            ) {
                                Text(
                                    text = if (isDockedAtTop) "⬇" else "⬆",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Minimize button
                            IconButton(
                                onClick = { isMinimized = true },
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFF2C2C3E), RoundedCornerShape(5.dp))
                            ) {
                                Text("—", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Row 2: Element Selector Chips (Horizontally Scrollable)
                    val elements = listOf(
                        "dpad" to "D-Pad / Joy",
                        "btn_a" to "Nút A",
                        "btn_b" to "Nút B",
                        "btn_ta" to "Nút TA",
                        "btn_tb" to "Nút TB",
                        "shoulder_l" to "Nút L",
                        "shoulder_r" to "Nút R",
                        "select_start" to "Select/Start"
                    )
                    val chipScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(chipScrollState),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        elements.forEach { (id, label) ->
                            FilterChip(
                                selected = selectedElementId == id,
                                onClick = { onSelectElement(id) },
                                label = {
                                    Text(
                                        label,
                                        fontSize = 10.sp,
                                        fontWeight = if (selectedElementId == id) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00B0FF),
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    // Row 3: Scale Adjustment and Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Scale Controls: [-] 100% [+]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Cỡ:", fontSize = 11.sp, color = Color.White)
                            IconButton(
                                onClick = onDecreaseScale,
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(Color(0xFF2C2C3E), RoundedCornerShape(5.dp))
                            ) {
                                Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF1E1E28), RoundedCornerShape(5.dp))
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
                                    .size(26.dp)
                                    .background(Color(0xFF2C2C3E), RoundedCornerShape(5.dp))
                            ) {
                                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }

                        // Action Buttons: Đặt lại, Hủy, Lưu
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            OutlinedButton(
                                onClick = onResetLayout,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Đặt lại", fontSize = 10.sp, color = Color(0xFFFFB74D))
                            }

                            OutlinedButton(
                                onClick = onCancelEditing,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Hủy", fontSize = 10.sp, color = Color.White)
                            }

                            Button(
                                onClick = onSaveLayout,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("LƯU", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
