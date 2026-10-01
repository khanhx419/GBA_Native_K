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
import com.gba.nativeemu.storage.SaveRepository

@Composable
fun SaveStateDialog(
    gameName: String,
    saveRepository: SaveRepository,
    onSaveState: (Int) -> Unit,
    onLoadState: (Int) -> Unit,
    onExportBattery: () -> Unit,
    onImportBattery: () -> Unit,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save States & Data", fontSize = 20.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (message != null) {
                    Text(message!!, color = Color(0xFF00E676), fontSize = 13.sp)
                }

                Text("Save State Slots", style = MaterialTheme.typography.titleMedium)

                (1..5).forEach { slot ->
                    val exists = saveRepository.stateExists(gameName, slot)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Slot $slot", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(
                                    if (exists) "Saved" else "Empty",
                                    color = if (exists) Color(0xFF81C784) else Color.Gray,
                                    fontSize = 11.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onSaveState(slot)
                                        message = "Saved to Slot $slot"
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("SAVE", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        onLoadState(slot)
                                        message = "Loaded Slot $slot"
                                    },
                                    enabled = exists,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("LOAD", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Battery Save (.sav)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Export .sav file to backup to your PC / other emulators, or import an existing save.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportBattery,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export .sav", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onImportBattery,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import .sav", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
