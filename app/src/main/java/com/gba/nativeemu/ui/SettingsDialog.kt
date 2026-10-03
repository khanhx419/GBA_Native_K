package com.gba.nativeemu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gba.nativeemu.core.GbaBridge
import com.gba.nativeemu.storage.EmulatorSettings

@Composable
fun SettingsDialog(
    settings: EmulatorSettings,
    isFastForward: Boolean = false,
    onToggleFastForward: () -> Unit = {},
    onSettingsChanged: (EmulatorSettings) -> Unit,
    onOpenSaveStates: () -> Unit,
    onOpenCheats: () -> Unit,
    onOpenLayoutEditor: () -> Unit,
    onResetGame: () -> Unit,
    onCloseRom: () -> Unit,
    onDismiss: () -> Unit
) {
    var aspect by remember { mutableStateOf(settings.aspectMode) }
    var speed by remember { mutableStateOf(settings.fastForwardSpeed) }
    var vol by remember { mutableStateOf(settings.volume) }
    var muted by remember { mutableStateOf(settings.isMuted) }
    var opacity by remember { mutableStateOf(settings.gamepadOpacity) }
    var orientation by remember { mutableStateOf(settings.orientationMode) }
    var hideControls by remember { mutableStateOf(settings.hideGamepad) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF7C4DFF))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cài đặt & Tùy chọn", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- PHÍM TẮT NHANH TÍNH NĂNG ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenSaveStates,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C3E)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save States", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onOpenCheats,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C3E)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cheats", fontSize = 12.sp)
                    }
                }

                // --- TÙY CHỈNH VỊ TRÍ PHÍM BẤM ---
                Button(
                    onClick = onOpenLayoutEditor,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🎮 Chỉnh vị trí & kích thước phím", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = Color(0x33FFFFFF))

                // --- HƯỚNG MÀN HÌNH (XOAY TỰ DO / KHÓA DỌC / KHÓA NGANG) ---
                Text("Hướng màn hình", style = MaterialTheme.typography.titleSmall, color = Color(0xFF00E5FF))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val orientations = listOf(
                        "🔄 Tự do" to EmulatorSettings.ORIENTATION_AUTO,
                        "📱 Cố định Dọc" to EmulatorSettings.ORIENTATION_PORTRAIT,
                        "🖥️ Cố định Ngang" to EmulatorSettings.ORIENTATION_LANDSCAPE
                    )
                    orientations.forEach { (name, value) ->
                        FilterChip(
                            selected = orientation == value,
                            onClick = {
                                orientation = value
                                onSettingsChanged(settings.copy(orientationMode = value))
                            },
                            label = { Text(name, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // --- CHẾ ĐỘ TOÀN MÀN HÌNH (FULL SCREEN) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chế độ Toàn màn hình (Full Screen)", style = MaterialTheme.typography.titleSmall, color = Color(0xFF00E5FF))
                        Text("Kéo dãn tràn viền toàn bộ màn hình", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                    }
                    Switch(
                        checked = aspect == GbaBridge.ASPECT_STRETCH,
                        onCheckedChange = { isFull ->
                            val newAspect = if (isFull) GbaBridge.ASPECT_STRETCH else GbaBridge.ASPECT_FIT
                            aspect = newAspect
                            onSettingsChanged(settings.copy(aspectMode = newAspect))
                        }
                    )
                }

                // --- TỈ LỆ KHUNG HÌNH (ASPECT RATIO) ---
                Text("Tỉ lệ khung hình game", style = MaterialTheme.typography.titleSmall, color = Color(0xFF00E5FF))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val aspects = listOf(
                        "Gốc 3:2" to GbaBridge.ASPECT_FIT,
                        "Toàn màn hình" to GbaBridge.ASPECT_STRETCH,
                        "1x" to GbaBridge.ASPECT_1X,
                        "2x" to GbaBridge.ASPECT_2X
                    )
                    aspects.forEach { (name, value) ->
                        FilterChip(
                            selected = aspect == value,
                            onClick = {
                                aspect = value
                                onSettingsChanged(settings.copy(aspectMode = value))
                            },
                            label = { Text(name, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // --- TỐC ĐỘ TUA NHANH ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tua nhanh (${speed.toInt()}x)", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFF9100))
                    Switch(
                        checked = isFastForward,
                        onCheckedChange = { onToggleFastForward() }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(1.5f, 2.0f, 3.0f, 4.0f, 8.0f).forEach { s ->
                        FilterChip(
                            selected = speed == s,
                            onClick = {
                                speed = s
                                onSettingsChanged(settings.copy(fastForwardSpeed = s))
                            },
                            label = { Text("${s}x", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // --- ÂM LƯỢNG & TẮT TIẾNG ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Âm lượng: ${(vol * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = !muted,
                        onCheckedChange = { checked ->
                            muted = !checked
                            onSettingsChanged(settings.copy(isMuted = !checked))
                        }
                    )
                }
                Slider(
                    value = vol,
                    onValueChange = {
                        vol = it
                        onSettingsChanged(settings.copy(volume = it))
                    },
                    valueRange = 0f..1f
                )

                // --- ĐỘ MỜ PHÍM ẢO ---
                Text("Độ mờ phím ảo: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = opacity,
                    onValueChange = {
                        opacity = it
                        onSettingsChanged(settings.copy(gamepadOpacity = it))
                    },
                    valueRange = 0.2f..1.0f
                )

                // --- ẨN PHÍM ẢO (TOÀN MÀN HÌNH GAME) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ẩn phím ảo (Toàn màn hình game)", style = MaterialTheme.typography.titleSmall)
                        Text("Dành cho tay cầm Bluetooth hoặc hiển thị 100% video", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                    }
                    Switch(
                        checked = hideControls,
                        onCheckedChange = { checked ->
                            hideControls = checked
                            onSettingsChanged(settings.copy(hideGamepad = checked))
                        }
                    )
                }

                HorizontalDivider(color = Color(0x33FFFFFF))

                // --- NÚT HỆ THỐNG (RESET & THOÁT) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onResetGame,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Game", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onCloseRom,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đóng ROM", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Xong", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            }
        }
    )
}
