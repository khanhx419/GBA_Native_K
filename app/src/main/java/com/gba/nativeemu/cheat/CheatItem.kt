package com.gba.nativeemu.cheat

import com.gba.nativeemu.core.GbaBridge

data class CheatItem(
    val id: String,
    val name: String,
    val code: String,
    val enabled: Boolean = true,
    val type: Int = GbaBridge.CHEAT_AUTODETECT
)
