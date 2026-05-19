package uz.ictschool.sketchchain.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

@Composable
fun HomeScreen(
    onJoinRoom: (roomId: String, playerName: String) -> Unit,
    isLoading: Boolean
) {
    var playerName by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SketchChain", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = playerName,
            onValueChange = { playerName = it },
            label = { Text("Your Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = roomId,
            onValueChange = { roomId = it },
            label = { Text("Room Code (leave empty to create new)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val finalRoomId = if (roomId.isBlank()) generateRandomRoomId() else roomId
                onJoinRoom(finalRoomId, playerName.ifBlank { "Player" })
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isLoading && playerName.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text(if (roomId.isBlank()) "Create New Room" else "Join Room")
            }
        }
    }
}

private fun generateRandomRoomId(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..6).map { chars.random() }.joinToString("")
}