package com.gba.nativeemu.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gba.nativeemu.core.GbaBridge
import com.gba.nativeemu.storage.SaveRepository
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
fun GameScreen(
    gameTitle: String,
    saveRepository: SaveRepository,
    settings: EmulatorSettings,
    onSettingsChanged: (EmulatorSettings) -> Unit,
    onResetGame: () -> Unit,
    onCloseRom: () -> Unit,
    onExportBattery: () -> Unit,
    onImportBattery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    var currentKeyMask by remember { mutableStateOf(0) }
    var isFastForward by remember { mutableStateOf(false) }
    var fpsText by remember { mutableStateOf("60 FPS • Mát máy") }

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
                    GbaBridge.nativeSetFilter(settings.filterType)
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
                    if (deltaNs > 100_000_000L) {
                        deltaNs = 100_000_000L
                    }

                    val ff = isFastForward
                    val speed = if (ff) settings.fastForwardSpeed else 1.0f
                    accumulatorNs += (deltaNs * speed).toLong()

                    var rendered = false
                    var steps = 0
                    val maxSteps = if (ff) 8 else 3

                    while (accumulatorNs >= GBA_FRAME_TIME_NS && steps < maxSteps) {
                        accumulatorNs -= GBA_FRAME_TIME_NS
                        steps++
                        if (accumulatorNs < GBA_FRAME_TIME_NS || steps == maxSteps) {
                            // Last step: render video and feed audio
                            GbaBridge.nativeRenderFrame(currentKeyMask)
                            rendered = true
                            gbaFramesCount++
                        } else {
                            // Intermediate step (during fast forward): advance emulation without updating GL texture
                            GbaBridge.nativeStepFrame(currentKeyMask)
                            gbaFramesCount++
                        }
                    }

                    // Reset accumulator if it got way too far ahead
                    if (accumulatorNs > GBA_FRAME_TIME_NS * 2) {
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

        // --- 2. TOP FPS & STATUS OVERLAY ---
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .background(Color(0xCC12121A), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isFastForward) Color(0xFFFF9100) else Color(0xFF00E676), RoundedCornerShape(4.dp))
            )
            Text(
                fpsText,
                color = if (isFastForward) Color(0xFFFFB74D) else Color(0xFF00E676),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text("•", color = Color.Gray, fontSize = 11.sp)
            Text(
                gameTitle,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium
            )
        }

        // --- 3. VIRTUAL GAMEPAD OVERLAY ---
        VirtualGamepad(
            modifier = Modifier.fillMaxSize(),
            opacity = settings.gamepadOpacity,
            hapticEnabled = settings.hapticFeedback,
            vibrator = vibrator,
            isFastForward = isFastForward,
            onFastForwardToggle = {
                isFastForward = !isFastForward
                GbaBridge.nativeSetFastForward(if (isFastForward) settings.fastForwardSpeed else 1.0f)
                val status = if (isFastForward) "Tua nhanh ${settings.fastForwardSpeed.toInt()}x" else "Tốc độ chuẩn 1.0x"
                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
            },
            onMenuClick = { showSettingsDialog = true },
            onQuickSave = {
                val ok = saveRepository.saveState(gameTitle, 1)
                Toast.makeText(context, if (ok) "💾 Đã Lưu nhanh (Slot 1)" else "❌ Lỗi lưu Slot 1", Toast.LENGTH_SHORT).show()
            },
            onQuickLoad = {
                if (saveRepository.stateExists(gameTitle, 1)) {
                    val ok = saveRepository.loadState(gameTitle, 1)
                    Toast.makeText(context, if (ok) "⚡ Đã Tải nhanh (Slot 1)" else "❌ Lỗi tải Slot 1", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Slot 1 chưa có dữ liệu lưu", Toast.LENGTH_SHORT).show()
                }
            },
            onKeyMaskChanged = { mask ->
                currentKeyMask = mask
            }
        )

        // --- 4. DIALOGS ---
        if (showSettingsDialog) {
            SettingsDialog(
                settings = settings,
                onSettingsChanged = { newSettings ->
                    onSettingsChanged(newSettings)
                    GbaBridge.nativeSetFilter(newSettings.filterType)
                    GbaBridge.nativeSetAspectRatio(newSettings.aspectMode)
                    GbaBridge.nativeSetAudioVolume(newSettings.volume)
                    GbaBridge.nativeSetAudioMute(newSettings.isMuted)
                },
                onOpenSaveStates = {
                    showSettingsDialog = false
                    showSaveStateDialog = true
                },
                onOpenCheats = {
                    showSettingsDialog = false
                    showCheatDialog = true
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
