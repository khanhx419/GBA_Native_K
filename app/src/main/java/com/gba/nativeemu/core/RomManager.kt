package com.gba.nativeemu.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class RomInfo(
    val title: String,
    val filePath: String,
    val fileSize: Long,
    val lastPlayed: Long
)

class RomManager(private val context: Context) {
    companion object {
        private const val TAG = "RomManager"
        private const val PREFS_NAME = "gba_rom_prefs"
        private const val KEY_RECENTS = "recent_roms"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val romsDir = File(context.filesDir, "roms").apply { mkdirs() }

    fun getRecentRoms(): List<RomInfo> {
        val jsonStr = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        val list = mutableListOf<RomInfo>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.getString("path")
                val file = File(path)
                if (file.exists()) {
                    list.add(
                        RomInfo(
                            title = obj.getString("title"),
                            filePath = path,
                            fileSize = file.length(),
                            lastPlayed = obj.getLong("lastPlayed")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading recent ROMs", e)
        }
        return list.sortedByDescending { it.lastPlayed }
    }

    fun recordRomPlayed(info: RomInfo) {
        val current = getRecentRoms().filter { it.filePath != info.filePath }.toMutableList()
        current.add(0, info.copy(lastPlayed = System.currentTimeMillis()))

        val arr = JSONArray()
        for (item in current.take(15)) {
            val obj = JSONObject().apply {
                put("title", item.title)
                put("path", item.filePath)
                put("lastPlayed", item.lastPlayed)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }

    fun extractGbaTitle(file: File): String {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(0xC0)
                val read = input.read(header)
                if (read >= 0xAC) {
                    val titleBytes = header.copyOfRange(0xA0, 0xAC)
                    val rawTitle = String(titleBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                    if (rawTitle.isNotBlank()) rawTitle else file.nameWithoutExtension
                } else {
                    file.nameWithoutExtension
                }
            }
        } catch (e: Exception) {
            file.nameWithoutExtension
        }
    }

    fun importRomFromUri(uri: Uri): RomInfo? {
        var fileName = "game.gba"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    fileName = cursor.getString(nameIdx) ?: "game.gba"
                }
            }
        }

        val destFile = File(romsDir, fileName)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            val title = extractGbaTitle(destFile)
            val info = RomInfo(
                title = title,
                filePath = destFile.absolutePath,
                fileSize = destFile.length(),
                lastPlayed = System.currentTimeMillis()
            )
            recordRomPlayed(info)
            info
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import ROM from URI", e)
            null
        }
    }
}
