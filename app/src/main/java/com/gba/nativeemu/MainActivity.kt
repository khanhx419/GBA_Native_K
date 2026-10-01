package com.gba.nativeemu

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gba.nativeemu.core.GbaBridge
import com.gba.nativeemu.core.RomInfo
import com.gba.nativeemu.core.RomManager
import com.gba.nativeemu.storage.SaveRepository
import com.gba.nativeemu.ui.*
import com.gba.nativeemu.ui.theme.GbaNativeTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var romManager: RomManager
    private lateinit var saveRepository: SaveRepository
    private var currentGameTitle: String? = null
    private var physicalKeyMask = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during gameplay
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Hide system UI (Fullscreen Immersive)
        hideSystemUI()

        romManager = RomManager(this)
        saveRepository = SaveRepository(this)

        // Initialize Native Core & Audio
        GbaBridge.nativeInit(filesDir.absolutePath)

        // Check if opened via intent (e.g. user tapped a .gba file in file manager)
        handleIntent(intent)

        setContent {
            GbaNativeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    var isRomLoaded by remember { mutableStateOf(GbaBridge.nativeIsRomLoaded()) }
                    var currentTitle by remember { mutableStateOf(currentGameTitle ?: "GBA Game") }
                    val recentRoms = remember { mutableStateListOf<RomInfo>().apply { addAll(romManager.getRecentRoms()) } }
                    var settings by remember { mutableStateOf(EmulatorSettings()) }

                    // SAF Launcher for Opening ROM
                    val openRomLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            val info = romManager.importRomFromUri(uri)
                            if (info != null) {
                                loadGame(info)
                                currentTitle = info.title
                                isRomLoaded = true
                                recentRoms.clear()
                                recentRoms.addAll(romManager.getRecentRoms())
                            } else {
                                Toast.makeText(this@MainActivity, "Failed to load ROM file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // SAF Launcher for Exporting .sav
                    val exportSaveLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
                    ) { uri: Uri? ->
                        if (uri != null && currentGameTitle != null) {
                            val ok = saveRepository.exportBatterySaveToUri(currentGameTitle!!, uri)
                            Toast.makeText(
                                this@MainActivity,
                                if (ok) "Save file exported successfully!" else "Failed to export save",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    // SAF Launcher for Importing .sav
                    val importSaveLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        if (uri != null && currentGameTitle != null) {
                            val ok = saveRepository.importBatterySaveFromUri(currentGameTitle!!, uri)
                            Toast.makeText(
                                this@MainActivity,
                                if (ok) "Save file imported & restored!" else "Failed to import save",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    if (isRomLoaded) {
                        GameScreen(
                            gameTitle = currentTitle,
                            saveRepository = saveRepository,
                            settings = settings,
                            onSettingsChanged = { settings = it },
                            onResetGame = {
                                if (currentGameTitle != null) {
                                    saveRepository.loadBattery(currentGameTitle!!)
                                }
                            },
                            onCloseRom = {
                                currentGameTitle = null
                                isRomLoaded = false
                            },
                            onExportBattery = {
                                exportSaveLauncher.launch("${currentTitle}.sav")
                            },
                            onImportBattery = {
                                importSaveLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    } else {
                        RomPickerScreen(
                            recentRoms = recentRoms,
                            onOpenRomPicker = {
                                openRomLauncher.launch(arrayOf("*/*"))
                            },
                            onSelectRecentRom = { info ->
                                loadGame(info)
                                currentTitle = info.title
                                isRomLoaded = true
                            }
                        )
                    }
                }
            }
        }
    }

    private fun loadGame(info: RomInfo) {
        val file = File(info.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "ROM file not found: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }

        val success = GbaBridge.nativeLoadRomFile(file.absolutePath)
        if (success) {
            currentGameTitle = info.title
            romManager.recordRomPlayed(info)
            saveRepository.loadBattery(info.title)
            Toast.makeText(this, "Loaded: ${info.title}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Error loading ROM with mGBA core", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val info = romManager.importRomFromUri(uri)
        if (info != null) {
            loadGame(info)
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onPause() {
        super.onPause()
        currentGameTitle?.let { title ->
            saveRepository.saveBattery(title)
        }
        GbaBridge.nativeSetAudioMute(true)
    }

    override fun onResume() {
        super.onResume()
        GbaBridge.nativeSetAudioMute(false)
        hideSystemUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentGameTitle?.let { title ->
            saveRepository.saveBattery(title)
        }
        GbaBridge.nativeDestroy()
    }

    // Physical Bluetooth / USB Gamepad Controller Support
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val mask = mapKeycodeToGba(keyCode)
        if (mask != 0) {
            physicalKeyMask = physicalKeyMask or mask
            GbaBridge.nativeStepFrame(physicalKeyMask)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val mask = mapKeycodeToGba(keyCode)
        if (mask != 0) {
            physicalKeyMask = physicalKeyMask and mask.inv()
            GbaBridge.nativeStepFrame(physicalKeyMask)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun mapKeycodeToGba(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_X -> GbaBridge.KEY_A
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_Z -> GbaBridge.KEY_B
            KeyEvent.KEYCODE_BUTTON_SELECT -> GbaBridge.KEY_SELECT
            KeyEvent.KEYCODE_BUTTON_START -> GbaBridge.KEY_START
            KeyEvent.KEYCODE_DPAD_UP -> GbaBridge.KEY_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> GbaBridge.KEY_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> GbaBridge.KEY_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> GbaBridge.KEY_RIGHT
            KeyEvent.KEYCODE_BUTTON_L1 -> GbaBridge.KEY_L
            KeyEvent.KEYCODE_BUTTON_R1 -> GbaBridge.KEY_R
            else -> 0
        }
    }
}
