package com.gba.nativeemu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gba.nativeemu.core.RomInfo
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RomPickerScreen(
    recentRoms: List<RomInfo>,
    onOpenRomPicker: () -> Unit,
    onSelectRecentRom: (RomInfo) -> Unit
) {
    val dateFormat = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D14))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // App Header Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF4A148C), Color(0xFF6A1B9A), Color(0xFF00B0FF))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VideogameAsset,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "GBA NATIVE",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Core mGBA Native C++ • 60 FPS Pacer (Mát máy) • AAudio Low Latency",
                    color = Color(0xDDFFFFFF),
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Open ROM Button
        Button(
            onClick = onOpenRomPicker,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("MỞ ROM GBA (.gba)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recents Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TRÒ CHƠI GẦN ĐÂY",
                color = Color(0xFFB0B0C0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                "${recentRoms.size} game",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (recentRoms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color(0xFF3A3A4E),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Chưa có trò chơi nào",
                        color = Color.Gray,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Nhấn nút bên trên để chọn file .gba và bắt đầu chơi",
                        color = Color(0xFF555566),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(recentRoms) { rom ->
                    val sizeMb = String.format(Locale.US, "%.1f MB", rom.fileSize / (1024.0 * 1024.0))
                    val dateStr = dateFormat.format(Date(rom.lastPlayed))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectRecentRom(rom) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181824)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2C2C3E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    rom.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    "$sizeMb • $dateStr",
                                    color = Color(0xFF888899),
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
