package com.gba.nativeemu.storage

import android.content.Context
import android.content.SharedPreferences
import com.gba.nativeemu.core.GbaBridge

data class ElementLayout(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1.0f
)

data class CustomLayoutState(
    val dpad: ElementLayout = ElementLayout(),
    val buttonA: ElementLayout = ElementLayout(),
    val buttonB: ElementLayout = ElementLayout(),
    val buttonTA: ElementLayout = ElementLayout(),
    val buttonTB: ElementLayout = ElementLayout(),
    val shoulderL: ElementLayout = ElementLayout(),
    val shoulderR: ElementLayout = ElementLayout(),
    val selectStart: ElementLayout = ElementLayout()
) {
    fun updateElement(id: String, transform: (ElementLayout) -> ElementLayout): CustomLayoutState {
        return when (id) {
            "dpad" -> copy(dpad = transform(dpad))
            "btn_a" -> copy(buttonA = transform(buttonA))
            "btn_b" -> copy(buttonB = transform(buttonB))
            "btn_ta" -> copy(buttonTA = transform(buttonTA))
            "btn_tb" -> copy(buttonTB = transform(buttonTB))
            "shoulder_l" -> copy(shoulderL = transform(shoulderL))
            "shoulder_r" -> copy(shoulderR = transform(shoulderR))
            "select_start" -> copy(selectStart = transform(selectStart))
            else -> this
        }
    }

    fun getElement(id: String): ElementLayout {
        return when (id) {
            "dpad" -> dpad
            "btn_a" -> buttonA
            "btn_b" -> buttonB
            "btn_ta" -> buttonTA
            "btn_tb" -> buttonTB
            "shoulder_l" -> shoulderL
            "shoulder_r" -> shoulderR
            "select_start" -> selectStart
            else -> ElementLayout()
        }
    }
}

data class EmulatorSettings(
    val aspectMode: Int = GbaBridge.ASPECT_FIT,
    val fastForwardSpeed: Float = 2.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val gamepadOpacity: Float = 0.85f,
    val orientationMode: Int = ORIENTATION_AUTO,
    val hideGamepad: Boolean = false,
    val movementMode: Int = MOVEMENT_DPAD
) {
    companion object {
        const val ORIENTATION_AUTO = 0
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2

        const val MOVEMENT_DPAD = 0
        const val MOVEMENT_JOYSTICK_FIXED = 1
        const val MOVEMENT_JOYSTICK_FLOATING = 2
    }
}

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gba_native_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ASPECT = "setting_aspect_mode"
        private const val KEY_FF_SPEED = "setting_fast_forward_speed"
        private const val KEY_VOLUME = "setting_volume"
        private const val KEY_MUTED = "setting_is_muted"
        private const val KEY_OPACITY = "setting_gamepad_opacity"
        private const val KEY_ORIENTATION = "setting_orientation_mode"
        private const val KEY_HIDE_GAMEPAD = "setting_hide_gamepad"
        private const val KEY_MOVEMENT_MODE = "setting_movement_mode"
    }

    fun loadSettings(): EmulatorSettings {
        return EmulatorSettings(
            aspectMode = prefs.getInt(KEY_ASPECT, GbaBridge.ASPECT_FIT),
            fastForwardSpeed = prefs.getFloat(KEY_FF_SPEED, 2.0f),
            volume = prefs.getFloat(KEY_VOLUME, 1.0f),
            isMuted = prefs.getBoolean(KEY_MUTED, false),
            gamepadOpacity = prefs.getFloat(KEY_OPACITY, 0.85f),
            orientationMode = prefs.getInt(KEY_ORIENTATION, EmulatorSettings.ORIENTATION_AUTO),
            hideGamepad = prefs.getBoolean(KEY_HIDE_GAMEPAD, false),
            movementMode = prefs.getInt(KEY_MOVEMENT_MODE, EmulatorSettings.MOVEMENT_DPAD)
        )
    }

    fun saveSettings(settings: EmulatorSettings) {
        prefs.edit()
            .putInt(KEY_ASPECT, settings.aspectMode)
            .putFloat(KEY_FF_SPEED, settings.fastForwardSpeed)
            .putFloat(KEY_VOLUME, settings.volume)
            .putBoolean(KEY_MUTED, settings.isMuted)
            .putFloat(KEY_OPACITY, settings.gamepadOpacity)
            .putInt(KEY_ORIENTATION, settings.orientationMode)
            .putBoolean(KEY_HIDE_GAMEPAD, settings.hideGamepad)
            .putInt(KEY_MOVEMENT_MODE, settings.movementMode)
            .apply()
    }

    fun loadLayout(isLandscape: Boolean): CustomLayoutState {
        val suffix = if (isLandscape) "_landscape" else "_portrait"
        return CustomLayoutState(
            dpad = loadElement("dpad$suffix"),
            buttonA = loadElement("btn_a$suffix"),
            buttonB = loadElement("btn_b$suffix"),
            buttonTA = loadElement("btn_ta$suffix"),
            buttonTB = loadElement("btn_tb$suffix"),
            shoulderL = loadElement("shoulder_l$suffix"),
            shoulderR = loadElement("shoulder_r$suffix"),
            selectStart = loadElement("select_start$suffix")
        )
    }

    fun saveLayout(isLandscape: Boolean, layout: CustomLayoutState) {
        val suffix = if (isLandscape) "_landscape" else "_portrait"
        val editor = prefs.edit()
        saveElement(editor, "dpad$suffix", layout.dpad)
        saveElement(editor, "btn_a$suffix", layout.buttonA)
        saveElement(editor, "btn_b$suffix", layout.buttonB)
        saveElement(editor, "btn_ta$suffix", layout.buttonTA)
        saveElement(editor, "btn_tb$suffix", layout.buttonTB)
        saveElement(editor, "shoulder_l$suffix", layout.shoulderL)
        saveElement(editor, "shoulder_r$suffix", layout.shoulderR)
        saveElement(editor, "select_start$suffix", layout.selectStart)
        editor.apply()
    }

    fun resetLayout(isLandscape: Boolean) {
        saveLayout(isLandscape, CustomLayoutState())
    }

    private fun loadElement(keyPrefix: String): ElementLayout {
        val x = prefs.getFloat("${keyPrefix}_x", 0f)
        val y = prefs.getFloat("${keyPrefix}_y", 0f)
        val s = prefs.getFloat("${keyPrefix}_scale", 1.0f)
        return ElementLayout(x, y, s)
    }

    private fun saveElement(editor: SharedPreferences.Editor, keyPrefix: String, el: ElementLayout) {
        editor.putFloat("${keyPrefix}_x", el.offsetX)
        editor.putFloat("${keyPrefix}_y", el.offsetY)
        editor.putFloat("${keyPrefix}_scale", el.scale)
    }
}
