package com.gba.nativeemu.cheat

import android.content.Context
import android.util.Log
import com.gba.nativeemu.core.GbaBridge
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class CheatRepository(private val context: Context) {
    companion object {
        private const val TAG = "CheatRepository"

        fun sanitizeFileName(name: String): String {
            return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        }

        fun normalizeCheatLine(rawLine: String): String {
            if (rawLine.isBlank()) return ""
            val clean = rawLine.trim().uppercase()
            val hexOnly = clean.replace(Regex("[^0-9A-F]"), "")

            // 1. Exactly 12 hex chars: e.g. 82025840 0001 or 02025840:0001 -> CodeBreaker 16-bit
            if (hexOnly.length == 12) {
                var addr = hexOnly.substring(0, 8)
                val value = hexOnly.substring(8, 12)
                if (addr.startsWith("02")) addr = "82" + addr.substring(2)
                else if (addr.startsWith("03")) addr = "83" + addr.substring(2)
                return "$addr $value"
            }

            // 2. Exactly 10 hex chars: e.g. 02025840 44 or 32025840 44 -> CodeBreaker 8-bit
            if (hexOnly.length == 10) {
                var addr = hexOnly.substring(0, 8)
                val value = hexOnly.substring(8, 10).padStart(4, '0')
                if (addr.startsWith("02")) addr = "32" + addr.substring(2)
                else if (addr.startsWith("03")) addr = "33" + addr.substring(2)
                else if (!addr.startsWith("32") && !addr.startsWith("33")) addr = "32" + addr.substring(2)
                return "$addr $value"
            }

            // 3. Exactly 16 hex chars: e.g. GameShark v3 / AR MAX
            if (hexOnly.length == 16) {
                return "${hexOnly.substring(0, 8)} ${hexOnly.substring(8, 16)}"
            }

            // 4. Split by whitespace or colon / equal
            val parts = clean.split(Regex("[\\s:=]+")).filter { it.isNotBlank() }
            if (parts.size == 2) {
                var p1 = parts[0]
                var p2 = parts[1]
                if (p1.length == 8 && p2.length <= 4) {
                    val isByte = p2.length <= 2
                    p2 = p2.padStart(4, '0')
                    if (p1.startsWith("02")) {
                        p1 = if (isByte) "32" + p1.substring(2) else "82" + p1.substring(2)
                    } else if (p1.startsWith("03")) {
                        p1 = if (isByte) "33" + p1.substring(2) else "83" + p1.substring(2)
                    }
                    return "$p1 $p2"
                }
                if (p1.length == 8 && p2.length == 8) {
                    return "$p1 $p2"
                }
            }

            return clean
        }

        fun normalizeCheatCode(rawCode: String): String {
            return rawCode.trim().split(Regex("[\\r\\n]+"))
                .map { normalizeCheatLine(it) }
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }
    }

    private val cheatsDir = File(context.filesDir, "cheats").apply { mkdirs() }

    private fun getCheatFile(gameTitle: String): File {
        val sanitized = sanitizeFileName(gameTitle)
        return File(cheatsDir, "${sanitized}_cheats.json")
    }

    fun getCheats(gameTitle: String): List<CheatItem> {
        val file = getCheatFile(gameTitle)
        if (!file.exists() || file.length() == 0L) return emptyList()

        return try {
            val content = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            jsonArrayToCheats(jsonArray)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cheats for $gameTitle", e)
            emptyList()
        }
    }

    fun saveCheats(gameTitle: String, cheats: List<CheatItem>) {
        try {
            val file = getCheatFile(gameTitle)
            val jsonArray = cheatsToJsonArray(cheats)
            file.writeText(jsonArray.toString(2), Charsets.UTF_8)
            Log.i(TAG, "Saved ${cheats.size} cheats for $gameTitle")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cheats for $gameTitle", e)
        }
    }

    fun addCheat(
        gameTitle: String,
        name: String,
        code: String,
        type: Int = GbaBridge.CHEAT_AUTODETECT
    ): CheatItem {
        val current = getCheats(gameTitle).toMutableList()
        val normalizedCode = normalizeCheatCode(code)
        val newItem = CheatItem(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Cheat #${current.size + 1}" },
            code = normalizedCode,
            enabled = true,
            type = type
        )
        current.add(newItem)
        saveCheats(gameTitle, current)
        applyActiveCheats(gameTitle)
        return newItem
    }

    fun toggleCheat(gameTitle: String, id: String, enabled: Boolean) {
        val current = getCheats(gameTitle).map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        saveCheats(gameTitle, current)
        applyActiveCheats(gameTitle)
    }

    fun deleteCheat(gameTitle: String, id: String) {
        val current = getCheats(gameTitle).filterNot { it.id == id }
        saveCheats(gameTitle, current)
        applyActiveCheats(gameTitle)
    }

    fun applyActiveCheats(gameTitle: String) {
        if (!GbaBridge.isLibraryLoaded || !GbaBridge.nativeIsRomLoaded()) {
            return
        }

        try {
            GbaBridge.nativeClearCheats()
            val active = getCheats(gameTitle).filter { it.enabled && it.code.isNotBlank() }
            var appliedCount = 0

            for (cheat in active) {
                val ok = GbaBridge.nativeAddCheat(cheat.name, cheat.code, cheat.type)
                if (ok) appliedCount++
            }
            Log.i(TAG, "Applied $appliedCount / ${active.size} active cheats to engine for $gameTitle")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying cheats to native engine", e)
        }
    }

    fun cheatsToJsonArray(cheats: List<CheatItem>): JSONArray {
        val array = JSONArray()
        for (c in cheats) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("code", c.code)
                put("enabled", c.enabled)
                put("type", c.type)
            }
            array.put(obj)
        }
        return array
    }

    fun jsonArrayToCheats(jsonArray: JSONArray): List<CheatItem> {
        val list = mutableListOf<CheatItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            list.add(
                CheatItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "Cheat"),
                    code = obj.optString("code", ""),
                    enabled = obj.optBoolean("enabled", true),
                    type = obj.optInt("type", GbaBridge.CHEAT_AUTODETECT)
                )
            )
        }
        return list
    }
}
