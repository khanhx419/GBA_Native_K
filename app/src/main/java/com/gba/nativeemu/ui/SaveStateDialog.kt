package com.gba.nativeemu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gba.nativeemu.storage.SaveRepository
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SaveStateDialog(
    gameName: String,
    saveRepository: SaveRepository,
    onSaveState: (Int) -> Boolean,
    onLoadState: (Int) -> Boolean,
    onDeleteState: (Int) -> Boolean,
    onExportBattery: () -> Unit,
    onImportBattery: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()) }

    // Auto-refresh slots when returning to the app from external SAF file pickers
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Quản lý Lưu / Tải Game", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (message != null) {
                    Text(
                        message!!,
                        color = if (isErrorMessage) Color(0xFFFF5252) else Color(0xFF00E676),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text("Các vị trí lưu nhanh (Save Slots)", style = MaterialTheme.typography.titleSmall, color = Color(0xFF00E5FF))

                (1..5).forEach { slot ->
                    val file = remember(slot, refreshTrigger) { saveRepository.getStateFile(gameName, slot) }
                    val exists = file.exists() && file.length() > 0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Vị trí $slot", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (exists) {
                                    val dateStr = dateFormat.format(Date(file.lastModified()))
                                    val sizeKb = (file.length() + 1023) / 1024
                                    Text(
                                        "$dateStr (${sizeKb} KB)",
                                        color = Color(0xFF81C784),
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text(
                                        "Trống",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val ok = onSaveState(slot)
                                        refreshTrigger++
                                        if (ok) {
                                            message = "💾 Đã lưu vào Vị trí $slot"
                                            isErrorMessage = false
                                        } else {
                                            message = "❌ Lưu Vị trí $slot thất bại"
                                            isErrorMessage = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("LƯU", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val ok = onLoadState(slot)
                                        if (ok) {
                                            message = "⚡ Đã tải Vị trí $slot thành công"
                                            isErrorMessage = false
                                        } else {
                                            message = "❌ Tải Vị trí $slot thất bại"
                                            isErrorMessage = true
                                        }
                                    },
                                    enabled = exists,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("TẢI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                if (exists) {
                                    IconButton(
                                        onClick = {
                                            val ok = onDeleteState(slot)
                                            refreshTrigger++
                                            if (ok) {
                                                message = "🗑️ Đã xóa Vị trí $slot"
                                                isErrorMessage = false
                                            } else {
                                                message = "❌ Xóa Vị trí $slot thất bại"
                                                isErrorMessage = true
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFEF5350))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Xóa slot $slot",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0x33FFFFFF), modifier = Modifier.padding(vertical = 4.dp))

                Text("Bộ nhớ Pin (.sav)", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFF9100))
                Text(
                    "Xuất file .sav để sao lưu sang PC hoặc giả lập khác, hoặc nhập file .sav có sẵn.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportBattery,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xuất .sav", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onImportBattery,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nhập .sav", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = Color(0x33FFFFFF), modifier = Modifier.padding(vertical = 4.dp))

                Text("Sao lưu toàn diện dạng JSON (.json)", style = MaterialTheme.typography.titleSmall, color = Color(0xFF00E5FF))
                Text(
                    "Xuất trọn gói dữ liệu game: bố cục phím (dọc & ngang), cài đặt, file save pin và toàn bộ các slot save state vào 1 file JSON duy nhất.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExportJson,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xuất .json", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onImportJson,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nhập .json", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
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
