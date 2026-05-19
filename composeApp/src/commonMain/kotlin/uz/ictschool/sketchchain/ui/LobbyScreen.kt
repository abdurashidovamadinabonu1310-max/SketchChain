package uz.ictschool.sketchchain.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uz.ictschool.sketchchain.shared.Player
import uz.ictschool.sketchchain.shared.Room

@Composable
fun LobbyScreen(
    room: Room?,
    myPlayerId: String,
    onStartGame: () -> Unit
) {
    if (room == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Room Code: ${room.id}", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Players (${room.players.size})", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        
        room.players.forEach { player ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(player.name, style = MaterialTheme.typography.bodyLarge)
                    if (player.id == room.hostId) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("(Host)", color = MaterialTheme.colorScheme.primary)
                    }
                    if (player.id == myPlayerId) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("(You)", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (myPlayerId == room.hostId) {
            Button(
                onClick = onStartGame,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = room.players.size >= 2
            ) {
                Text("Start Game")
            }
            if (room.players.size < 2) {
                Text("Waiting for more players...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        } else {
            Text("Waiting for host to start...", style = MaterialTheme.typography.titleMedium)
        }
    }
}
