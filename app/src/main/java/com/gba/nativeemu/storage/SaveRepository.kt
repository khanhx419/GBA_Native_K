package com.gba.nativeemu.storage

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.gba.nativeemu.core.GbaBridge
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaveRepository(private val context: Context) {
    companion object {
        private const val TAG = "SaveRepository"
    }

    private val savesDir = File(context.filesDir, "saves").apply { mkdirs() }
    private val statesDir = File(context.filesDir, "states").apply { mkdirs() }

    fun getBatterySaveFile(gameName: String): File {
        val sanitized = sanitizeFileName(gameName)
        return File(savesDir, "$sanitized.sav")
    }

    fun getStateFile(gameName: String, slot: Int): File {
        val sanitized = sanitizeFileName(gameName)
        return File(statesDir, "${sanitized}_slot_${slot}.ss")
    }

    fun saveBattery(gameName: String): Boolean {
        val file = getBatterySaveFile(gameName)
        val success = GbaBridge.nativeSaveBattery(file.absolutePath)
        Log.i(TAG, "saveBattery $gameName: $success (${file.length()} bytes)")
        return success
    }

    fun loadBattery(gameName: String): Boolean {
        val file = getBatterySaveFile(gameName)
        if (!file.exists()) return false
        val success = GbaBridge.nativeLoadBattery(file.absolutePath)
        Log.i(TAG, "loadBattery $gameName: $success")
        return success
    }

    fun saveState(gameName: String, slot: Int): Boolean {
        val file = getStateFile(gameName, slot)
        val success = GbaBridge.nativeSaveState(slot, file.absolutePath)
        Log.i(TAG, "saveState slot $slot for $gameName: success=$success, size=${file.length()}")
        return success && file.exists() && file.length() > 1000
    }

    fun loadState(gameName: String, slot: Int): Boolean {
        val file = getStateFile(gameName, slot)
        if (!file.exists() || file.length() < 1000) {
            Log.w(TAG, "loadState slot $slot failed: file not found or corrupted (${file.length()} bytes)")
            return false
        }
        val success = GbaBridge.nativeLoadState(slot, file.absolutePath)
        Log.i(TAG, "loadState slot $slot for $gameName: success=$success")
        return success
    }

    fun stateExists(gameName: String, slot: Int): Boolean {
        val file = getStateFile(gameName, slot)
        return file.exists() && file.length() > 1000
    }

    fun deleteState(gameName: String, slot: Int): Boolean {
        val file = getStateFile(gameName, slot)
        val success = if (file.exists()) file.delete() else true
        Log.i(TAG, "deleteState slot $slot for $gameName: success=$success")
        return success
    }

    fun exportAllDataToJsonUri(
        gameName: String,
        destUri: Uri,
        settings: EmulatorSettings,
        layoutPortrait: CustomLayoutState,
        layoutLandscape: CustomLayoutState,
        cheatsJsonArray: JSONArray? = null
    ): Boolean {
        return try {
            val root = JSONObject()
            root.put("version", "1.4")
            root.put("gameTitle", gameName)
            root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

            // Settings
            val settingsObj = JSONObject().apply {
                put("aspectMode", settings.aspectMode)
                put("fastForwardSpeed", settings.fastForwardSpeed.toDouble())
                put("volume", settings.volume.toDouble())
                put("isMuted", settings.isMuted)
                put("gamepadOpacity", settings.gamepadOpacity.toDouble())
                put("orientationMode", settings.orientationMode)
                put("hideGamepad", settings.hideGamepad)
                put("movementMode", settings.movementMode)
            }
            root.put("settings", settingsObj)

            // Layout helper
            fun layoutToJson(layout: CustomLayoutState): JSONObject {
                fun elementToJson(e: ElementLayout): JSONObject = JSONObject().apply {
                    put("offsetX", e.offsetX.toDouble())
                    put("offsetY", e.offsetY.toDouble())
                    put("scale", e.scale.toDouble())
                }
                return JSONObject().apply {
                    put("dpad", elementToJson(layout.dpad))
                    put("buttonA", elementToJson(layout.buttonA))
                    put("buttonB", elementToJson(layout.buttonB))
                    put("buttonTA", elementToJson(layout.buttonTA))
                    put("buttonTB", elementToJson(layout.buttonTB))
                    put("shoulderL", elementToJson(layout.shoulderL))
                    put("shoulderR", elementToJson(layout.shoulderR))
                    put("selectStart", elementToJson(layout.selectStart))
                }
            }

            root.put("layoutPortrait", layoutToJson(layoutPortrait))
            root.put("layoutLandscape", layoutToJson(layoutLandscape))

            // Battery Save (.sav) Base64
            val batteryFile = getBatterySaveFile(gameName)
            if (batteryFile.exists() && batteryFile.length() > 0) {
                val b64 = Base64.encodeToString(batteryFile.readBytes(), Base64.NO_WRAP)
                root.put("batterySaveBase64", b64)
            }

            // Save States (1..5)
            val statesArr = JSONArray()
            for (slot in 1..5) {
                val sf = getStateFile(gameName, slot)
                if (sf.exists() && sf.length() > 1000) {
                    val sObj = JSONObject().apply {
                        put("slot", slot)
                        put("sizeBytes", sf.length())
                        put("lastModified", sf.lastModified())
                        val sBytes = sf.readBytes()
                        put("dataBase64", Base64.encodeToString(sBytes, Base64.NO_WRAP))
                    }
                    statesArr.put(sObj)
                }
            }
            root.put("saveStates", statesArr)

            if (cheatsJsonArray != null) {
                root.put("cheats", cheatsJsonArray)
            }

            context.contentResolver.openOutputStream(destUri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export data to JSON", e)
            false
        }
    }

    data class JsonImportResult(
        val success: Boolean,
        val newSettings: EmulatorSettings?,
        val newLayoutPortrait: CustomLayoutState?,
        val newLayoutLandscape: CustomLayoutState?,
        val batteryRestored: Boolean,
        val statesRestoredCount: Int,
        val importedCheatsArray: JSONArray? = null
    )

    fun importAllDataFromJsonUri(gameName: String, sourceUri: Uri): JsonImportResult {
        return try {
            val jsonString = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                input.bufferedReader().use { it.readText() }
            } ?: return JsonImportResult(false, null, null, null, false, 0)

            val root = JSONObject(jsonString)

            var importedSettings: EmulatorSettings? = null
            if (root.has("settings")) {
                val sObj = root.getJSONObject("settings")
                importedSettings = EmulatorSettings(
                    aspectMode = sObj.optInt("aspectMode", GbaBridge.ASPECT_FIT),
                    fastForwardSpeed = sObj.optDouble("fastForwardSpeed", 2.0).toFloat(),
                    volume = sObj.optDouble("volume", 1.0).toFloat(),
                    isMuted = sObj.optBoolean("isMuted", false),
                    gamepadOpacity = sObj.optDouble("gamepadOpacity", 0.85).toFloat(),
                    orientationMode = sObj.optInt("orientationMode", EmulatorSettings.ORIENTATION_AUTO),
                    hideGamepad = sObj.optBoolean("hideGamepad", false),
                    movementMode = sObj.optInt("movementMode", EmulatorSettings.MOVEMENT_DPAD)
                )
            }

            fun jsonToLayout(obj: JSONObject?): CustomLayoutState? {
                if (obj == null) return null
                fun jsonToElement(eObj: JSONObject?): ElementLayout {
                    if (eObj == null) return ElementLayout()
                    return ElementLayout(
                        offsetX = eObj.optDouble("offsetX", 0.0).toFloat(),
                        offsetY = eObj.optDouble("offsetY", 0.0).toFloat(),
                        scale = eObj.optDouble("scale", 1.0).toFloat()
                    )
                }
                return CustomLayoutState(
                    dpad = jsonToElement(obj.optJSONObject("dpad")),
                    buttonA = jsonToElement(obj.optJSONObject("buttonA")),
                    buttonB = jsonToElement(obj.optJSONObject("buttonB")),
                    buttonTA = jsonToElement(obj.optJSONObject("buttonTA")),
                    buttonTB = jsonToElement(obj.optJSONObject("buttonTB")),
                    shoulderL = jsonToElement(obj.optJSONObject("shoulderL")),
                    shoulderR = jsonToElement(obj.optJSONObject("shoulderR")),
                    selectStart = jsonToElement(obj.optJSONObject("selectStart"))
                )
            }

            val importedPortrait = jsonToLayout(root.optJSONObject("layoutPortrait"))
            val importedLandscape = jsonToLayout(root.optJSONObject("layoutLandscape"))

            var batteryRestored = false
            if (root.has("batterySaveBase64")) {
                val b64 = root.getString("batterySaveBase64")
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val bFile = getBatterySaveFile(gameName)
                bFile.writeBytes(bytes)
                loadBattery(gameName)
                batteryRestored = true
            }

            var statesRestoredCount = 0
            if (root.has("saveStates")) {
                val statesArr = root.getJSONArray("saveStates")
                for (i in 0 until statesArr.length()) {
                    val sObj = statesArr.getJSONObject(i)
                    val slot = sObj.optInt("slot", -1)
                    val dataB64 = sObj.optString("dataBase64", "")
                    if (slot in 1..5 && dataB64.isNotEmpty()) {
                        val sBytes = Base64.decode(dataB64, Base64.DEFAULT)
                        val sf = getStateFile(gameName, slot)
                        sf.writeBytes(sBytes)
                        statesRestoredCount++
                    }
                }
            }

            val importedCheats = if (root.has("cheats")) root.getJSONArray("cheats") else null

            JsonImportResult(
                success = true,
                newSettings = importedSettings,
                newLayoutPortrait = importedPortrait,
                newLayoutLandscape = importedLandscape,
                batteryRestored = batteryRestored,
                statesRestoredCount = statesRestoredCount,
                importedCheatsArray = importedCheats
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import data from JSON", e)
            JsonImportResult(false, null, null, null, false, 0)
        }
    }

    fun exportBatterySaveToUri(gameName: String, destUri: Uri): Boolean {
        val source = getBatterySaveFile(gameName)
        if (!source.exists()) return false
        return try {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                FileInputStream(source).use { input ->
                    input.copyTo(out)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            false
        }
    }

    fun importBatterySaveFromUri(gameName: String, sourceUri: Uri): Boolean {
        val dest = getBatterySaveFile(gameName)
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(dest).use { out ->
                    input.copyTo(out)
                }
            }
            loadBattery(gameName)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            false
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
