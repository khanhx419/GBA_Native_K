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
        return success && file.exists() && file.length() > 0
    }

    fun loadState(gameName: String, slot: Int): Boolean {
        val file = getStateFile(gameName, slot)
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "loadState slot $slot failed: file not found or corrupted (${file.length()} bytes)")
            return false
        }
        val success = GbaBridge.nativeLoadState(slot, file.absolutePath)
        Log.i(TAG, "loadState slot $slot for $gameName: success=$success")
        return success
    }

    fun stateExists(gameName: String, slot: Int): Boolean {
        val file = getStateFile(gameName, slot)
        return file.exists() && file.length() > 0
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
            // Ensure any recent in-game SRAM / Flash progress is flushed to disk
            saveBattery(gameName)

            val root = JSONObject()
            root.put("app", "GBA_Native_K")
            root.put("version", "1.5")
            root.put("gameTitle", gameName)
            root.put("romTitle", gameName) // For compatibility with companion web app
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
            } else {
                root.put("batterySaveBase64", JSONObject.NULL)
            }

            // Save States (1..5)
            val statesArr = JSONArray()
            for (slot in 1..5) {
                val sf = getStateFile(gameName, slot)
                if (sf.exists() && sf.length() > 1000) {
                    val sBytes = sf.readBytes()
                    val b64 = Base64.encodeToString(sBytes, Base64.NO_WRAP)
                    val sObj = JSONObject().apply {
                        put("slot", slot)
                        put("sizeBytes", sf.length())
                        put("lastModified", sf.lastModified())
                        put("dataBase64", b64)
                        put("stateBase64", b64) // For companion app compatibility
                    }
                    statesArr.put(sObj)
                }
            }
            root.put("saveStates", statesArr)
            root.put("states", statesArr) // For companion app compatibility

            if (cheatsJsonArray != null) {
                root.put("cheats", cheatsJsonArray)
            }

            context.contentResolver.openOutputStream(destUri, "wt")?.use { out ->
                out.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(root.toString())
                    writer.flush()
                }
            } ?: return false
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
        val importedCheatsArray: JSONArray? = null,
        val errorMessage: String? = null
    )

    fun importAllDataFromJsonUri(gameName: String, sourceUri: Uri): JsonImportResult {
        return try {
            val rawJson = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } ?: return JsonImportResult(false, null, null, null, false, 0, null, "Không mở được tệp")

            // Remove UTF-8 BOM if present and trim whitespace
            val jsonString = rawJson.trim().removePrefix("\uFEFF").trim()
            if (!jsonString.startsWith("{")) {
                return JsonImportResult(false, null, null, null, false, 0, null, "Định dạng không phải là tệp JSON")
            }

            val root = JSONObject(jsonString)

            var importedSettings: EmulatorSettings? = null
            if (root.has("settings") && !root.isNull("settings")) {
                val sObj = root.optJSONObject("settings")
                if (sObj != null) {
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
            val b64 = root.optString("batterySaveBase64", "").trim()
            if (b64.isNotEmpty() && b64 != "null") {
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    if (bytes.isNotEmpty()) {
                        val bFile = getBatterySaveFile(gameName)
                        bFile.writeBytes(bytes)
                        loadBattery(gameName)
                        batteryRestored = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Lỗi giải mã batterySaveBase64", e)
                }
            }

            var statesRestoredCount = 0
            // Check both saveStates and states (companion app schema)
            val statesArr = root.optJSONArray("saveStates") ?: root.optJSONArray("states")
            if (statesArr != null) {
                for (i in 0 until statesArr.length()) {
                    val sObj = statesArr.optJSONObject(i) ?: continue
                    val slot = sObj.optInt("slot", -1)
                    val dataB64 = sObj.optString("dataBase64", "").ifEmpty {
                        sObj.optString("stateBase64", "")
                    }.trim()
                    if (slot in 1..5 && dataB64.isNotEmpty() && dataB64 != "null") {
                        try {
                            val sBytes = Base64.decode(dataB64, Base64.DEFAULT)
                            if (sBytes.isNotEmpty()) {
                                val sf = getStateFile(gameName, slot)
                                sf.writeBytes(sBytes)
                                statesRestoredCount++
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Lỗi giải mã save state slot $slot", e)
                        }
                    }
                }
            }

            // Universal cheats parsing (handles JSONArray, JSON string, or raw text)
            var importedCheats: JSONArray? = null
            if (root.has("cheats") && !root.isNull("cheats")) {
                val cheatsVal = root.get("cheats")
                if (cheatsVal is JSONArray) {
                    importedCheats = cheatsVal
                } else if (cheatsVal is String) {
                    val str = cheatsVal.trim()
                    if (str.startsWith("[")) {
                        try {
                            importedCheats = JSONArray(str)
                        } catch (_: Exception) {}
                    } else if (str.isNotBlank() && str != "null") {
                        val arr = JSONArray()
                        str.split(Regex("[\\r\\n]+")).forEachIndexed { idx, line ->
                            val cleanLine = line.trim()
                            if (cleanLine.isNotBlank()) {
                                val cObj = JSONObject().apply {
                                    put("id", java.util.UUID.randomUUID().toString())
                                    put("name", "Cheat #${idx + 1}")
                                    put("code", cleanLine)
                                    put("enabled", true)
                                    put("type", GbaBridge.CHEAT_AUTODETECT)
                                }
                                arr.put(cObj)
                            }
                        }
                        if (arr.length() > 0) importedCheats = arr
                    }
                }
            }

            JsonImportResult(
                success = true,
                newSettings = importedSettings,
                newLayoutPortrait = importedPortrait,
                newLayoutLandscape = importedLandscape,
                batteryRestored = batteryRestored,
                statesRestoredCount = statesRestoredCount,
                importedCheatsArray = importedCheats,
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import data from JSON", e)
            JsonImportResult(false, null, null, null, false, 0, null, e.localizedMessage ?: e.message)
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
