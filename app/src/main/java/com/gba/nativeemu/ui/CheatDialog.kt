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
        title = { Text("Cheat Codes", fontSize = 20.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Supports GameShark, Action Replay v3, CodeBreaker, and VBA raw cheat formats.",
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
                    label = { Text("Cheat Description (e.g. Master Ball)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = cheatCode,
                    onValueChange = { cheatCode = it },
                    label = { Text("Code (e.g. 82003884 0001)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Button(
                    onClick = {
                        errorMsg = null
                        successMsg = null
                        if (cheatName.isBlank() || cheatCode.isBlank()) {
                            errorMsg = "Please enter both description and cheat code."
                            return@Button
                        }
                        val ok = onAddCheat(cheatName.trim(), cheatCode.trim())
                        if (ok) {
                            successMsg = "Cheat added & activated!"
                            cheatName = ""
                            cheatCode = ""
                        } else {
                            errorMsg = "Failed to parse code. Check syntax."
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Add Cheat")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Active Cheats (${cheats.size})", style = MaterialTheme.typography.titleMedium)

                if (cheats.isEmpty()) {
                    Text("No active cheats.", color = Color.Gray, fontSize = 13.sp)
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
                        Text("Clear All Cheats")
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
