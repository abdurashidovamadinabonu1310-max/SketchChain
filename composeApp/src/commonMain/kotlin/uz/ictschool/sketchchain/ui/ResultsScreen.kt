package uz.ictschool.sketchchain.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.ictschool.sketchchain.shared.EntryType
import uz.ictschool.sketchchain.shared.Game
import uz.ictschool.sketchchain.shared.Room

@Composable
fun ResultsScreen(
    game: Game?,
    room: Room?,
    onBackToHome: () -> Unit
) {
    if (game == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Loading results...", style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }

    // Build a quick playerId → playerName lookup from the room
    val playerNames = remember(room) {
        room?.players?.associate { it.id to it.name } ?: emptyMap()
    }

    fun nameFor(id: String) = playerNames[id] ?: id

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = "🎉 THE REVEAL",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.tertiary
            )
        )
        Text(
            text = "See how the story twisted!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(game.chains) { chain ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Chain title
                        Text(
                            text = "${nameFor(chain.startingPlayerId)}'s chain",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )

                        // Each entry in the chain
                        chain.entries.forEachIndexed { index, entry ->
                            val author = nameFor(entry.playerId)
                            val stepLabel = when (index) {
                                0    -> "1st"
                                1    -> "2nd"
                                2    -> "3rd"
                                else -> "${index + 1}th"
                            }

                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                // Step header
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (entry.type == EntryType.TEXT)
                                            MaterialTheme.colorScheme.secondary
                                        else
                                            MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = if (entry.type == EntryType.TEXT) "✏️" else "🎨",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "$author  ·  $stepLabel",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                if (entry.type == EntryType.TEXT) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.background,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "\"${entry.content}\"",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                } else {
                                    // Render the actual drawing
                                    DrawingView(
                                        content = entry.content,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                    )
                                }
                            }

                            if (index < chain.entries.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onBackToHome,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                "PLAY AGAIN",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            )
        }
    }
}
