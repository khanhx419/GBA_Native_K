package com.gba.nativeemu.core

import android.util.Log

object GbaBridge {
    var isLibraryLoaded: Boolean = false
        private set
    var loadError: String? = null
        private set

    init {
        try {
            System.loadLibrary("gba_native")
            isLibraryLoaded = true
            Log.i("GbaBridge", "libgba_native.so loaded successfully")
        } catch (t: Throwable) {
            isLibraryLoaded = false
            loadError = t.message ?: t.toString()
            Log.e("GbaBridge", "FATAL: Failed to load libgba_native.so: $loadError", t)
        }
    }

    // GBA Key bitmasks
    const val KEY_A = 1 shl 0
    const val KEY_B = 1 shl 1
    const val KEY_SELECT = 1 shl 2
    const val KEY_START = 1 shl 3
    const val KEY_RIGHT = 1 shl 4
    const val KEY_LEFT = 1 shl 5
    const val KEY_UP = 1 shl 6
    const val KEY_DOWN = 1 shl 7
    const val KEY_R = 1 shl 8
    const val KEY_L = 1 shl 9

    // Video Filter modes
    const val FILTER_NEAREST = 0
    const val FILTER_BILINEAR = 1
    const val FILTER_LCD = 2
    const val FILTER_CRT = 3

    // Aspect Ratio modes
    const val ASPECT_FIT = 0
    const val ASPECT_STRETCH = 1
    const val ASPECT_1X = 2
    const val ASPECT_2X = 3
    const val ASPECT_3X = 4

    // Cheat formats
    const val CHEAT_AUTODETECT = 0
    const val CHEAT_CODEBREAKER = 1
    const val CHEAT_GAMESHARK = 2
    const val CHEAT_ACTION_REPLAY = 3

    external fun nativeInit(internalDir: String): Boolean
    external fun nativeLoadRom(romBytes: ByteArray, romSize: Int): Boolean
    external fun nativeLoadRomFile(romPath: String): Boolean
    external fun nativeUnloadRom()
    external fun nativeIsRomLoaded(): Boolean
    external fun nativeStepFrame(keyMask: Int)
    external fun nativeGetVideoBuffer(): IntArray
    external fun nativeSaveState(slot: Int, path: String): Boolean
    external fun nativeLoadState(slot: Int, path: String): Boolean
    external fun nativeSaveBattery(path: String): Boolean
    external fun nativeLoadBattery(path: String): Boolean
    external fun nativeAddCheat(name: String, code: String, type: Int): Boolean
    external fun nativeClearCheats()
    external fun nativeSetFastForward(ratio: Float)
    external fun nativeSetAudioMute(muted: Boolean)
    external fun nativeSetAudioVolume(volume: Float)
    external fun nativeReset()
    external fun nativeDestroy()

    // OpenGL ES Renderer callbacks
    external fun nativeSurfaceCreated()
    external fun nativeSurfaceChanged(width: Int, height: Int)
    external fun nativeRenderFrame(keyMask: Int)
    external fun nativeSetFilter(filterType: Int)
    external fun nativeSetAspectRatio(mode: Int)
}
