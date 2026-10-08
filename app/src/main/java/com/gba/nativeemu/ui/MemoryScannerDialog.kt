package com.gba.nativeemu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gba.nativeemu.cheat.CheatRepository
import com.gba.nativeemu.core.GbaBridge
import com.gba.nativeemu.scanner.GbaItemDatabase
import com.gba.nativeemu.scanner.MemoryScanner
import com.gba.nativeemu.scanner.ScanResult

@Composable
fun MemoryScannerDialog(
    gameTitle: String,
    memoryScanner: MemoryScanner,
    cheatRepository: CheatRepository,
    onDismiss: () -> Unit
) {
    var searchValueInput by remember { mutableStateOf("") }
    var selectedValType by remember { mutableStateOf(memoryScanner.currentValueType) }
    var selectedCompType by remember { mutableStateOf(memoryScanner.currentCompareType) }

    var results by remember { mutableStateOf(memoryScanner.results) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Dialog state for editing a single address
    var editingItem by remember { mutableStateOf<ScanResult?>(null) }
    var editNewValueInput by remember { mutableStateOf("") }

    // Dialog state for selecting an item from the database
    var showItemPickerForSearch by remember { mutableStateOf(false) }
    var showItemPickerForEdit by remember { mutableStateOf(false) }

    // Batch edit dialog
    var showBatchEditDialog by remember { mutableStateOf(false) }
    var batchEditValueInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Dò & Sửa Bộ Nhớ RAM",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = if (results.isNotEmpty()) Color(0xFF00E676).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${results.size} kết quả",
                        color = if (results.isNotEmpty()) Color(0xFF00E676) else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        color = Color(0xFF00E676),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // --- 1. NHẬP GIÁ TRỊ TÌM KIẾM ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchValueInput,
                        onValueChange = { searchValueInput = it },
                        label = { Text("Giá trị cần tìm") },
                        placeholder = { Text("Ví dụ: 68 hoặc 1", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            if (searchValueInput.isNotEmpty()) {
                                IconButton(onClick = { searchValueInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    Button(
                        onClick = { showItemPickerForSearch = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chọn Item", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // --- 2. CHỌN KIỂU DỮ LIỆU & SO SÁNH ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedValType == GbaBridge.SCAN_TYPE_U16,
                        onClick = { selectedValType = GbaBridge.SCAN_TYPE_U16 },
                        label = { Text("16-bit (Item/HP)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedValType == GbaBridge.SCAN_TYPE_U8,
                        onClick = { selectedValType = GbaBridge.SCAN_TYPE_U8 },
                        label = { Text("8-bit (Level)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedValType == GbaBridge.SCAN_TYPE_U32,
                        onClick = { selectedValType = GbaBridge.SCAN_TYPE_U32 },
                        label = { Text("32-bit (Tiền/EXP)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val comps = listOf(
                        "Bằng (=)" to GbaBridge.SCAN_COMPARE_EXACT,
                        "Lớn hơn (>)" to GbaBridge.SCAN_COMPARE_GREATER,
                        "Nhỏ hơn (<)" to GbaBridge.SCAN_COMPARE_LESS,
                        "Đã đổi (!=)" to GbaBridge.SCAN_COMPARE_CHANGED
                    )
                    comps.forEach { (title, mode) ->
                        FilterChip(
                            selected = selectedCompType == mode,
                            onClick = { selectedCompType = mode },
                            label = { Text(title, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // --- 3. NÚT HÀNH ĐỘNG TÌM KIẾM ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            val v = searchValueInput.trim().toIntOrNull() ?: 0
                            val res = memoryScanner.searchFirst(v, selectedValType, selectedCompType)
                            results = res
                            statusMessage = "🔍 Tìm lần đầu: Tìm thấy ${res.size} ô nhớ khớp!"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("TÌM LẦN ĐẦU", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val v = searchValueInput.trim().toIntOrNull() ?: 0
                            val res = memoryScanner.searchNext(v, selectedValType, selectedCompType)
                            results = res
                            statusMessage = "🎯 Đã lọc tiếp: Còn lại ${res.size} ô nhớ!"
                        },
                        enabled = results.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FilterAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("LỌC TIẾP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            memoryScanner.reset()
                            results = emptyList()
                            searchValueInput = ""
                            statusMessage = "Đã đặt lại bộ nhớ quét"
                        },
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("RESET", fontSize = 11.sp)
                    }
                }

                // --- 4. THAO TÁC HÀNG LOẠT (KHI CÓ KẾT QUẢ) ---
                if (results.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                batchEditValueInput = searchValueInput
                                showBatchEditDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sửa tất cả", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val codes = results.take(20).mapNotNull {
                                    memoryScanner.generateCheatCode(it.address, it.value, selectedValType)
                                }
                                if (codes.isNotEmpty()) {
                                    val cheatName = "Batch Scanner (${results.size} ô)"
                                    cheatRepository.addCheat(gameTitle, cheatName, codes.joinToString("\n"))
                                    statusMessage = "⚡ Đã tạo mã Cheat cho ${codes.size} ô nhớ!"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFFD600))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lưu Cheat", fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = Color(0x33FFFFFF), modifier = Modifier.padding(vertical = 2.dp))

                // --- 5. DANH SÁCH KẾT QUẢ QUÉT ĐƯỢC ---
                if (results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Chưa có kết quả quét nào.\nNhập số lượng/ID cần tìm rồi nhấn 'Tìm Lần Đầu'.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(results) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.formattedAddr,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF00E5FF),
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = GbaItemDatabase.formatValue(item.value, selectedValType),
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                editingItem = item
                                                editNewValueInput = item.value.toString()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Sửa", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = {
                                                val code = memoryScanner.generateCheatCode(item.address, item.value, selectedValType)
                                                if (code != null) {
                                                    val itemName = GbaItemDatabase.getItemName(item.value) ?: item.formattedAddr
                                                    val cheatName = "Cheat $itemName (${item.value})"
                                                    cheatRepository.addCheat(gameTitle, cheatName, code)
                                                    statusMessage = "⚡ Đã tạo mã Cheat: $cheatName"
                                                }
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = "Tạo Cheat",
                                                tint = Color(0xFFFFD600),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
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

    // --- DIALOG SỬA GIÁ TRỊ Ô NHỚ ĐƠN LẺ ---
    if (editingItem != null) {
        val target = editingItem!!
        val currentItemInfo = GbaItemDatabase.getItem(target.value)

        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = {
                Text("Sửa Giá Trị: ${target.formattedAddr}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Giá trị hiện tại: ${GbaItemDatabase.formatValue(target.value, selectedValType)}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    OutlinedTextField(
                        value = editNewValueInput,
                        onValueChange = { editNewValueInput = it },
                        label = { Text("Giá trị mới (hoặc ID item)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { showItemPickerForEdit = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chọn Vật Phẩm Từ Danh Sách...", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nv = editNewValueInput.trim().toIntOrNull()
                        if (nv != null) {
                            val ok = memoryScanner.editValue(target.address, nv, selectedValType)
                            if (ok) {
                                results = memoryScanner.results
                                statusMessage = "✅ Đã sửa ô nhớ ${target.formattedAddr} thành $nv"
                            }
                        }
                        editingItem = null
                    }
                ) {
                    Text("GHI ĐÈ")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    // --- DIALOG SỬA TẤT CẢ ---
    if (showBatchEditDialog) {
        AlertDialog(
            onDismissRequest = { showBatchEditDialog = false },
            title = {
                Text("Sửa Tất Cả ${results.size} Ô Nhớ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập giá trị mới để ghi đè vào toàn bộ các địa chỉ đang tìm được:", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = batchEditValueInput,
                        onValueChange = { batchEditValueInput = it },
                        label = { Text("Giá trị mới") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nv = batchEditValueInput.trim().toIntOrNull()
                        if (nv != null) {
                            val count = memoryScanner.editAll(nv, selectedValType)
                            results = memoryScanner.results
                            statusMessage = "✅ Đã sửa $count ô nhớ thành $nv!"
                        }
                        showBatchEditDialog = false
                    }
                ) {
                    Text("GHI ĐÈ TẤT CẢ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchEditDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // --- ITEM PICKER DIALOG (TRA CỨU VẬT PHẨM ĐẦY ĐỦ) ---
    if (showItemPickerForSearch || showItemPickerForEdit) {
        var searchQuery by remember { mutableStateOf("") }
        val allItems = remember(searchQuery) { GbaItemDatabase.searchItems(searchQuery) }

        AlertDialog(
            onDismissRequest = {
                showItemPickerForSearch = false
                showItemPickerForEdit = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFFFD600))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tra cứu & Chọn Vật Phẩm", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Tìm theo tên hoặc ID (ví dụ: Candy, Ball, 68)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(allItems) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (showItemPickerForSearch) {
                                            searchValueInput = item.id.toString()
                                            selectedValType = GbaBridge.SCAN_TYPE_U16
                                            showItemPickerForSearch = false
                                        } else if (showItemPickerForEdit) {
                                            editNewValueInput = item.id.toString()
                                            showItemPickerForEdit = false
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                                shape = RoundedCornerShape(8.dp)
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
                                            text = item.nameEn,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = item.nameVi,
                                            fontSize = 11.sp,
                                            color = Color(0xFF00E5FF)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "ID: ${item.id}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF00E676),
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = item.hexId,
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showItemPickerForSearch = false
                        showItemPickerForEdit = false
                    }
                ) {
                    Text("Đóng")
                }
            }
        )
    }
}
