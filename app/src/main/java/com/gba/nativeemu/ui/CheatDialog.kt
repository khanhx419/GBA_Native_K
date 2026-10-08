package com.gba.nativeemu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gba.nativeemu.cheat.CheatItem
import com.gba.nativeemu.cheat.CheatRepository

@Composable
fun CheatDialog(
    gameTitle: String,
    cheatRepository: CheatRepository,
    onDismiss: () -> Unit
) {
    var cheats by remember { mutableStateOf(cheatRepository.getCheats(gameTitle)) }
    var cheatNameInput by remember { mutableStateOf("") }
    var cheatCodeInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val activeCount = cheats.count { it.enabled }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFFFFD600),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Mã Gian Lận (Cheat Codes)",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                if (activeCount > 0) {
                    Surface(
                        color = Color(0xFF00E676).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "$activeCount bật",
                            color = Color(0xFF00E676),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        color = Color(0xFF00E676),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // --- 1. Danh sách mã Cheat đã lưu ---
                Text(
                    "Danh sách mã Cheat",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF00E5FF)
                )

                if (cheats.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Chưa có mã Cheat nào được lưu.\nHãy thêm mã mới ở bên dưới để sử dụng.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    cheats.forEach { cheat ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (cheat.enabled) Color(0xFF1E1E2E) else Color(0xFF16161E)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cheat.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (cheat.enabled) Color.White else Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = cheat.code.lines().firstOrNull()?.let {
                                            if (cheat.code.lines().size > 1) "$it (+${cheat.code.lines().size - 1} dòng)" else it
                                        } ?: "",
                                        color = if (cheat.enabled) Color(0xFF00E5FF) else Color(0xFF757575),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Switch(
                                        checked = cheat.enabled,
                                        onCheckedChange = { newState ->
                                            cheatRepository.toggleCheat(gameTitle, cheat.id, newState)
                                            cheats = cheatRepository.getCheats(gameTitle)
                                            statusMessage = if (newState) "⚡ Đã bật: ${cheat.name}" else "⏸️ Đã tắt: ${cheat.name}"
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF00E676),
                                            checkedTrackColor = Color(0xFF1B5E20)
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )

                                    IconButton(
                                        onClick = {
                                            cheatRepository.deleteCheat(gameTitle, cheat.id)
                                            cheats = cheatRepository.getCheats(gameTitle)
                                            statusMessage = "🗑️ Đã xóa mã ${cheat.name}"
                                        },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFEF5350))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Xóa mã cheat",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0x33FFFFFF), modifier = Modifier.padding(vertical = 4.dp))

                // --- 2. Thêm mã Cheat mới ---
                Text(
                    "Thêm mã Cheat mới",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFFF9100)
                )

                OutlinedTextField(
                    value = cheatNameInput,
                    onValueChange = { cheatNameInput = it },
                    label = { Text("Tên mã (ví dụ: Master Ball, Max Tiền)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = cheatCodeInput,
                    onValueChange = { cheatCodeInput = it },
                    label = { Text("Mã Cheat (CodeBreaker, Raw RAM, GameShark)") },
                    placeholder = {
                        Text(
                            "82025840 0001\n82025842 0063",
                            fontFamily = FontFamily.Monospace,
                            color = Color.DarkGray
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                )

                Text(
                    "💡 Tự động nhận diện & chuẩn hóa các định dạng:\n" +
                    "• CodeBreaker: 82xxxxxx yyyy (16-bit), 32xxxxxx yy (8-bit)\n" +
                    "• Raw RAM: 02xxxxxx:yyyy hoặc 02xxxxxx yyyy\n" +
                    "• GameShark / Action Replay: xxxxxxxx yyyyyyyy\n" +
                    "• Hỗ trợ nhập nhiều dòng cùng lúc.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Button(
                    onClick = {
                        if (cheatCodeInput.isNotBlank()) {
                            val name = cheatNameInput.ifBlank { "Cheat #${cheats.size + 1}" }
                            cheatRepository.addCheat(gameTitle, name, cheatCodeInput)
                            cheats = cheatRepository.getCheats(gameTitle)
                            cheatNameInput = ""
                            cheatCodeInput = ""
                            statusMessage = "✅ Đã thêm và kích hoạt: $name"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = cheatCodeInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("THÊM & KÍCH HOẠT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
            }
        }
    )
}
