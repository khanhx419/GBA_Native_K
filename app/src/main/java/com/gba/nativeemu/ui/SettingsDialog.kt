package com.gba.nativeemu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gba.nativeemu.core.GbaBridge

data class EmulatorSettings(
    val filterType: Int = GbaBridge.FILTER_NEAREST,
    val aspectMode: Int = GbaBridge.ASPECT_FIT,
    val fastForwardSpeed: Float = 2.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val gamepadOpacity: Float = 0.85f,
    val hapticFeedback: Boolean = true
)

@Composable
fun SettingsDialog(
    settings: EmulatorSettings,
    onSettingsChanged: (EmulatorSettings) -> Unit,
    onResetGame: () -> Unit,
    onCloseRom: () -> Unit,
    onDismiss: () -> Unit
) {
    var filter by remember { mutableStateOf(settings.filterType) }
    var aspect by remember { mutableStateOf(settings.aspectMode) }
    var speed by remember { mutableStateOf(settings.fastForwardSpeed) }
    var vol by remember { mutableStateOf(settings.volume) }
    var muted by remember { mutableStateOf(settings.isMuted) }
    var opacity by remember { mutableStateOf(settings.gamepadOpacity) }
    var haptic by remember { mutableStateOf(settings.hapticFeedback) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings & Emulation", fontSize = 20.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- VIDEO SHADER ---
                Text("Display Shader / Filter", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val filters = listOf(
                        "Pixel" to GbaBridge.FILTER_NEAREST,
                        "Smooth" to GbaBridge.FILTER_BILINEAR,
                        "LCD" to GbaBridge.FILTER_LCD,
                        "CRT" to GbaBridge.FILTER_CRT
                    )
                    filters.forEach { (label, value) ->
                        FilterChip(
                            selected = filter == value,
                            onClick = {
                                filter = value
                                GbaBridge.nativeSetFilter(value)
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                // --- ASPECT RATIO ---
                Text("Aspect Ratio", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val aspects = listOf(
                        "3:2 Fit" to GbaBridge.ASPECT_FIT,
                        "Stretch" to GbaBridge.ASPECT_STRETCH,
                        "1x" to GbaBridge.ASPECT_1X,
                        "2x" to GbaBridge.ASPECT_2X
                    )
                    aspects.forEach { (label, value) ->
                        FilterChip(
                            selected = aspect == value,
                            onClick = {
                                aspect = value
                                GbaBridge.nativeSetAspectRatio(value)
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                // --- FAST FORWARD SPEED ---
                Text("Fast Forward Speed: ${speed}x", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 1.5f..8.0f,
                    steps = 12
                )

                // --- AUDIO VOLUME ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mute Sound", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = muted,
                        onCheckedChange = {
                            muted = it
                            GbaBridge.nativeSetAudioMute(it)
                        }
                    )
                }

                if (!muted) {
                    Text("Volume: ${(vol * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = vol,
                        onValueChange = {
                            vol = it
                            GbaBridge.nativeSetAudioVolume(it)
                        },
                        valueRange = 0.0f..1.0f
                    )
                }

                // --- CONTROLLER OPACITY & HAPTIC ---
                Text("Gamepad Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.2f..1.0f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vibration / Haptic", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = haptic, onCheckedChange = { haptic = it })
                }

                HorizontalDivider()

                // --- GAME ACTIONS ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onResetGame()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Game", color = Color(0xFFFF9100), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            onCloseRom()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Exit Game", color = Color(0xFFFF5252), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSettingsChanged(
                    settings.copy(
                        filterType = filter,
                        aspectMode = aspect,
                        fastForwardSpeed = speed,
                        volume = vol,
                        isMuted = muted,
                        gamepadOpacity = opacity,
                        hapticFeedback = haptic
                    )
                )
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
