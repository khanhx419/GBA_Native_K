package com.gba.nativeemu.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    var fpsText by remember { mutableStateOf("60 FPS") }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSaveStateDialog by remember { mutableStateOf(false) }
    var showCheatDialog by remember { mutableStateOf(false) }
    val activeCheats = remember { mutableStateListOf<CheatItem>() }

    // GLSurfaceView Holder
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true

            var lastFpsTime = System.currentTimeMillis()
            var frameCount = 0

            setRenderer(object : GLSurfaceView.Renderer {
                override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                    GbaBridge.nativeSurfaceCreated()
                    GbaBridge.nativeSetFilter(settings.filterType)
                    GbaBridge.nativeSetAspectRatio(settings.aspectMode)
                }

                override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                    GbaBridge.nativeSurfaceChanged(width, height)
                }

                override fun onDrawFrame(gl: GL10?) {
                    val ff = isFastForward
                    val speed = if (ff) settings.fastForwardSpeed else 1.0f

                    if (speed > 1.5f) {
                        val extraSteps = (speed - 1.0f).toInt()
                        for (i in 0 until extraSteps) {
                            GbaBridge.nativeStepFrame(currentKeyMask)
                        }
                    }

                    GbaBridge.nativeRenderFrame(currentKeyMask)

                    frameCount++
                    val now = System.currentTimeMillis()
                    if (now - lastFpsTime >= 1000) {
                        val fps = frameCount
                        frameCount = 0
                        lastFpsTime = now
                        fpsText = "$fps FPS"
                    }
                }
            })
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
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
                .background(Color(0x99000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(fpsText, color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            if (isFastForward) {
                Text("${settings.fastForwardSpeed}x", color = Color(0xFFFF9100), fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Text(gameTitle, color = Color.White, fontSize = 11.sp, maxLines = 1)
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
            },
            onMenuClick = { showSettingsDialog = true },
            onQuickSave = { showSaveStateDialog = true },
            onQuickLoad = {
                saveRepository.loadState(gameTitle, 1)
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
                onResetGame = {
                    GbaBridge.nativeReset()
                    onResetGame()
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
                    saveRepository.saveState(gameTitle, slot)
                },
                onLoadState = { slot ->
                    saveRepository.loadState(gameTitle, slot)
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
                    }
                    ok
                },
                onClearAll = {
                    GbaBridge.nativeClearCheats()
                    activeCheats.clear()
                },
                onDismiss = { showCheatDialog = false }
            )
        }
    }
}
