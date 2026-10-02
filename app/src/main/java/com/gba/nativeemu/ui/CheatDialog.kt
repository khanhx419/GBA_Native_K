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

data class CheatItem(
    val name: String,
    val code: String,
    val enabled: Boolean = true
)

@Composable
fun CheatDialog(
    cheats: List<CheatItem>,
    onAddCheat: (name: String, code: String) -> Boolean,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var cheatName by remember { mutableStateOf("") }
    var cheatCode by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mã Gian Lận (Cheats)", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Hỗ trợ mã GameShark, Action Replay v3, CodeBreaker và VBA raw.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                if (errorMsg != null) {
                    Text(errorMsg!!, color = Color(0xFFFF5252), fontSize = 12.sp)
                }
                if (successMsg != null) {
                    Text(successMsg!!, color = Color(0xFF00E676), fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = cheatName,
                    onValueChange = { cheatName = it },
                    label = { Text("Tên mô tả (vd: Master Ball, Kẹo Rare Candy)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = cheatCode,
                    onValueChange = { cheatCode = it },
                    label = { Text("Đoạn mã (vd: 82003884 0001)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Button(
                    onClick = {
                        errorMsg = null
                        successMsg = null
                        if (cheatName.isBlank() || cheatCode.isBlank()) {
                            errorMsg = "Vui lòng nhập đầy đủ tên và đoạn mã cheat."
                            return@Button
                        }
                        val ok = onAddCheat(cheatName.trim(), cheatCode.trim())
                        if (ok) {
                            successMsg = "Đã thêm và kích hoạt mã gian lận!"
                            cheatName = ""
                            cheatCode = ""
                        } else {
                            errorMsg = "Cú pháp mã không hợp lệ. Vui lòng kiểm tra lại."
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Thêm mã gian lận")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Danh sách mã đang kích hoạt (${cheats.size})", style = MaterialTheme.typography.titleMedium)

                if (cheats.isEmpty()) {
                    Text("Chưa có mã gian lận nào.", color = Color.Gray, fontSize = 13.sp)
                } else {
                    cheats.forEach { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(item.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 13.sp)
                                Text(item.code, color = Color(0xFF00E5FF), fontSize = 11.sp)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onClearAll,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Xóa toàn bộ mã gian lận")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
