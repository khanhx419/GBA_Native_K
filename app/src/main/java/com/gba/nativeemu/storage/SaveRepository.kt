package com.gba.nativeemu.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gba.nativeemu.core.GbaBridge
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
        return GbaBridge.nativeSaveState(slot, file.absolutePath)
    }

    fun loadState(gameName: String, slot: Int): Boolean {
        val file = getStateFile(gameName, slot)
        if (!file.exists()) return false
        return GbaBridge.nativeLoadState(slot, file.absolutePath)
    }

    fun stateExists(gameName: String, slot: Int): Boolean {
        return getStateFile(gameName, slot).exists()
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
