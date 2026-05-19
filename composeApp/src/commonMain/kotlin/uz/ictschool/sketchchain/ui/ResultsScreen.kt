package uz.ictschool.sketchchain.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uz.ictschool.sketchchain.shared.EntryType
import uz.ictschool.sketchchain.shared.Game

@Composable
fun ResultsScreen(game: Game?) {
    if (game == null) return

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Results Time!", style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Simple MVP visualization: list all chains
        game.chains.forEach { chain ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Chain started by: ${chain.startingPlayerId}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    chain.entries.forEachIndexed { index, entry ->
                        if (entry.type == EntryType.TEXT) {
                            Text("${index + 1}. ${entry.content}")
                        } else {
                            Text("${index + 1}. [Drawing by ${entry.playerId}]")
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { /* Return to Home */ },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Back to Home")
        }
    }
}
