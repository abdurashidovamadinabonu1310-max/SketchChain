package uz.ictschool.sketchchain.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import uz.ictschool.sketchchain.shared.ChainEntry
import uz.ictschool.sketchchain.shared.EntryType
import uz.ictschool.sketchchain.shared.GameMessage

@Composable
fun GameScreen(
    myPlayerId: String,
    assignment: GameMessage.NextTurnAssignment?,
    onSubmitTurn: (ChainEntry) -> Unit
) {
    if (assignment == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Waiting for your turn...")
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (assignment.expectedType == EntryType.TEXT)
                "Write a sentence!"
            else
                "Draw this!",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Show previous entry
        assignment.previousEntry?.let { prev ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Previous:", style = MaterialTheme.typography.labelMedium)
                    if (prev.type == EntryType.TEXT) {
                        Text(prev.content, style = MaterialTheme.typography.titleLarge)
                    } else {
                        Text("[Drawing from previous player]", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (assignment.expectedType == EntryType.TEXT) {
            var textEntry by remember { mutableStateOf("") }

            OutlinedTextField(
                value = textEntry,
                onValueChange = { textEntry = it },
                label = { Text("Your sentence") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    onSubmitTurn(ChainEntry(myPlayerId, EntryType.TEXT, textEntry))
                    textEntry = ""
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = textEntry.isNotBlank()
            ) {
                Text("Submit Sentence")
            }
        } else {
            // === DRAWING AREA ===
            var paths by remember { mutableStateOf(mutableListOf<Path>()) }
            var currentPath by remember { mutableStateOf<Path?>(null) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath = Path().apply { moveTo(offset.x, offset.y) }
                            },
                            onDrag = { change, _ ->
                                currentPath?.lineTo(change.position.x, change.position.y)
                            },
                            onDragEnd = {
                                currentPath?.let { paths.add(it) }
                                currentPath = null
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw finished paths
                    paths.forEach { path ->
                        drawPath(
                            path = path,
                            color = Color.Black,
                            style = Stroke(
                                width = 8f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    // Draw current path
                    currentPath?.let { path ->
                        drawPath(
                            path = path,
                            color = Color.Black,
                            style = Stroke(
                                width = 8f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = { if (paths.isNotEmpty()) paths.removeAt(paths.lastIndex) }) {
                    Text("Undo")
                }
                Button(onClick = { paths.clear() }) {
                    Text("Clear")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // TODO: Later improve to send real image (Base64)
                    val fakeImageData = "drawing_$$   {paths.size}_strokes_   $${System.currentTimeMillis()}"
                    onSubmitTurn(ChainEntry(myPlayerId, EntryType.IMAGE, fakeImageData))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Submit Drawing")
            }
        }
    }
}