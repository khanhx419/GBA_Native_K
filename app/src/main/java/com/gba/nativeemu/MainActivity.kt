package com.gba.nativeemu

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gba.nativeemu.core.GbaBridge
import com.gba.nativeemu.core.RomInfo
import com.gba.nativeemu.cheat.CheatRepository
import com.gba.nativeemu.core.RomManager
import com.gba.nativeemu.storage.EmulatorSettings
import com.gba.nativeemu.storage.SaveRepository
import com.gba.nativeemu.storage.SettingsManager
import com.gba.nativeemu.ui.*
import com.gba.nativeemu.ui.theme.GbaNativeTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var romManager: RomManager
    private lateinit var saveRepository: SaveRepository
    private lateinit var cheatRepository: CheatRepository
    private lateinit var settingsManager: SettingsManager
    private var currentGameTitle: String? = null
    private var physicalKeyMask = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Keep screen on during gameplay
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Safe display: never extend into camera notch / punch hole area
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            romManager = RomManager(this)
            saveRepository = SaveRepository(this)
            cheatRepository = CheatRepository(this)
            settingsManager = SettingsManager(this)

            // Initialize Native Core & Audio safely
            if (GbaBridge.isLibraryLoaded) {
                try {
                    GbaBridge.nativeInit(filesDir.absolutePath)
                } catch (t: Throwable) {
                    android.util.Log.e("MainActivity", "Error in nativeInit", t)
                }
            }

            // Check if opened via intent (e.g. user tapped a .gba file in file manager)
            handleIntent(intent)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Error during onCreate setup", t)
        }

        setContent {
            GbaNativeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    if (!GbaBridge.isLibraryLoaded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "⚠️ Lỗi khởi tạo Engine Native",
                                    color = Color(0xFFFF5252),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    GbaBridge.loadError ?: "Không thể nạp libgba_native.so",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        return@Surface
                    }

                    var isRomLoaded by remember { mutableStateOf(GbaBridge.nativeIsRomLoaded()) }
                    var currentTitle by remember { mutableStateOf(currentGameTitle ?: "GBA Game") }
                    val recentRoms = remember { mutableStateListOf<RomInfo>().apply { addAll(romManager.getRecentRoms()) } }
                    var settings by remember { mutableStateOf(settingsManager.loadSettings()) }

                    // Apply requested screen orientation whenever orientationMode changes
                    LaunchedEffect(settings.orientationMode) {
                        requestedOrientation = when (settings.orientationMode) {
                            EmulatorSettings.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            EmulatorSettings.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }

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

                    // SAF Launcher for Exporting .json
                    val exportJsonLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/json")
                    ) { uri: Uri? ->
                        val title = currentGameTitle
                        if (uri != null && title != null) {
                            val layoutPortrait = settingsManager.loadLayout(false)
                            val layoutLandscape = settingsManager.loadLayout(true)
                            val cheatsArr = cheatRepository.cheatsToJsonArray(cheatRepository.getCheats(title))
                            val ok = saveRepository.exportAllDataToJsonUri(title, uri, settings, layoutPortrait, layoutLandscape, cheatsArr)
                            Toast.makeText(
                                this@MainActivity,
                                if (ok) "✅ Đã xuất dữ liệu JSON thành công!" else "❌ Lỗi xuất file JSON",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    // SAF Launcher for Importing .json
                    val importJsonLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        val title = currentGameTitle
                        if (uri != null && title != null) {
                            val res = saveRepository.importAllDataFromJsonUri(title, uri)
                            if (res.success) {
                                res.newSettings?.let {
                                    settings = it
                                    settingsManager.saveSettings(it)
                                }
                                res.newLayoutPortrait?.let { settingsManager.saveLayout(false, it) }
                                res.newLayoutLandscape?.let { settingsManager.saveLayout(true, it) }
                                res.importedCheatsArray?.let { arr ->
                                    val cheats = cheatRepository.jsonArrayToCheats(arr)
                                    cheatRepository.saveCheats(title, cheats)
                                    cheatRepository.applyActiveCheats(title)
                                }
                                val statusMsg = buildString {
                                    append("✅ Đã khôi phục dữ liệu từ JSON!")
                                    if (res.batteryRestored) append(" (Có file Pin .sav)")
                                    if (res.statesRestoredCount > 0) append(" (${res.statesRestoredCount} Save slot)")
                                    if (res.importedCheatsArray != null && res.importedCheatsArray.length() > 0) {
                                        append(" (${res.importedCheatsArray.length()} Cheat)")
                                    }
                                }
                                Toast.makeText(this@MainActivity, statusMsg, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@MainActivity, "❌ Lỗi nhập file JSON", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    if (isRomLoaded) {
                        GameScreen(
                            gameTitle = currentTitle,
                            saveRepository = saveRepository,
                            cheatRepository = cheatRepository,
                            settingsManager = settingsManager,
                            settings = settings,
                            onSettingsChanged = { newSettings ->
                                settings = newSettings
                                settingsManager.saveSettings(newSettings)
                            },
                            onResetGame = {
                                if (currentGameTitle != null) {
                                    saveRepository.loadBattery(currentGameTitle!!)
                                    cheatRepository.applyActiveCheats(currentGameTitle!!)
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
                            },
                            onExportJson = {
                                exportJsonLauncher.launch("${currentTitle}_backup.json")
                            },
                            onImportJson = {
                                importJsonLauncher.launch(arrayOf("application/json", "*/*"))
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
            cheatRepository.applyActiveCheats(info.title)
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
        try {
            val win = this.window ?: return
            val decorView = win.peekDecorView() ?: win.decorView ?: return
            WindowCompat.setDecorFitsSystemWindows(win, false)
            val controller = WindowCompat.getInsetsController(win, decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Error in hideSystemUI: ${e.message}")
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
        if (GbaBridge.isLibraryLoaded) {
            currentGameTitle?.let { title ->
                saveRepository.saveBattery(title)
            }
            GbaBridge.nativeSetAudioMute(true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (GbaBridge.isLibraryLoaded) {
            GbaBridge.nativeSetAudioMute(false)
        }
        hideSystemUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (GbaBridge.isLibraryLoaded) {
            currentGameTitle?.let { title ->
                saveRepository.saveBattery(title)
            }
            GbaBridge.nativeDestroy()
        }
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
