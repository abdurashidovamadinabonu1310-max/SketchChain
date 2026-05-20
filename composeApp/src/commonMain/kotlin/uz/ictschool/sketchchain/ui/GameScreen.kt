package uz.ictschool.sketchchain.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uz.ictschool.sketchchain.shared.*

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun hexToColor(hex: String): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val full = if (cleaned.length == 6) "ff$cleaned" else cleaned
        val argb = full.toLong(16)
        Color(
            alpha = ((argb shr 24) and 0xFF).toFloat() / 255f,
            red   = ((argb shr 16) and 0xFF).toFloat() / 255f,
            green = ((argb shr 8)  and 0xFF).toFloat() / 255f,
            blue  = (argb          and 0xFF).toFloat() / 255f
        )
    } catch (e: Exception) { Color.Black }
}

private fun DrawScope.renderStrokes(strokes: List<DrawingStroke>, scaleX: Float = 1f, scaleY: Float = 1f) {
    strokes.forEach { stroke ->
        if (stroke.points.size < 2) return@forEach
        val path = Path()
        path.moveTo(stroke.points[0].x * scaleX, stroke.points[0].y * scaleY)
        stroke.points.drop(1).forEach { pt -> path.lineTo(pt.x * scaleX, pt.y * scaleY) }
        val color = if (stroke.isEraser) Color.White else hexToColor(stroke.colorHex)
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// Reusable composable that renders a saved DrawingData inside a white surface
@Composable
fun DrawingView(content: String, modifier: Modifier = Modifier) {
    val drawingData = remember(content) {
        try { Json.decodeFromString<DrawingData>(content) } catch (e: Exception) { null }
    }
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        if (drawingData == null || drawingData.strokes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("[ Empty drawing ]", color = Color.Gray)
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sx = if (drawingData.canvasWidth  > 0) size.width  / drawingData.canvasWidth  else 1f
                val sy = if (drawingData.canvasHeight > 0) size.height / drawingData.canvasHeight else 1f
                renderStrokes(drawingData.strokes, sx, sy)
            }
        }
    }
}

// ── Color palette definition ──────────────────────────────────────────────────
private data class PaletteColor(val hex: String, val color: Color)

private val PALETTE = listOf(
    PaletteColor("#FF000000", Color.Black),
    PaletteColor("#FFFFFFFF", Color.White),
    PaletteColor("#FFFF6B6B", Color(0xFFFF6B6B)),
    PaletteColor("#FFFF9800", Color(0xFFFF9800)),
    PaletteColor("#FFFFE135", Color(0xFFFFE135)),
    PaletteColor("#FF4CAF50", Color(0xFF4CAF50)),
    PaletteColor("#FF2196F3", Color(0xFF2196F3)),
    PaletteColor("#FF4ECDC4", Color(0xFF4ECDC4)),
    PaletteColor("#FFC792EA", Color(0xFFC792EA)),
    PaletteColor("#FF795548", Color(0xFF795548)),
)

// ── Main GameScreen ───────────────────────────────────────────────────────────
@Composable
fun GameScreen(
    myPlayerId: String,
    assignment: GameMessage.NextTurnAssignment?,
    onSubmitTurn: (ChainEntry) -> Unit
) {
    if (assignment == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Waiting for other players...",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Hang tight while everyone finishes their turn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header banner
        val isFirstRound = assignment.previousEntry == null
        val headerText = when {
            assignment.expectedType == EntryType.TEXT && isFirstRound -> "WRITE ANY SENTENCE"
            assignment.expectedType == EntryType.TEXT -> "DESCRIBE THE DRAWING"
            else -> "DRAW THIS SENTENCE"
        }

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(vertical = 14.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Previous entry card (only if not the very first round)
        assignment.previousEntry?.let { prev ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "PREVIOUS PLAYER'S ENTRY:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (prev.type == EntryType.TEXT) {
                        Text(
                            text = "\"${prev.content}\"",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Show the actual drawing from previous player
                        DrawingView(
                            content = prev.content,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── TEXT MODE ────────────────────────────────────────────────────────
        if (assignment.expectedType == EntryType.TEXT) {
            var textEntry by remember { mutableStateOf("") }

            OutlinedTextField(
                value = textEntry,
                onValueChange = { textEntry = it },
                label = {
                    Text(if (isFirstRound) "Write anything that comes to mind!" else "What do you see in the drawing?")
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onSubmitTurn(ChainEntry(myPlayerId, EntryType.TEXT, textEntry.trim()))
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(32.dp),
                enabled = textEntry.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("SUBMIT", style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black, letterSpacing = 2.sp
                ))
            }

        // ── DRAWING MODE ──────────────────────────────────────────────────────
        } else {
            // mutableStateListOf so add/remove immediately triggers recomposition
            val strokes = remember { mutableStateListOf<DrawingStroke>() }
            val currentPoints = remember { mutableStateListOf<DrawingPoint>() }
            var selectedColorHex by remember { mutableStateOf("#FF000000") }
            var isEraser by remember { mutableStateOf(false) }
            var canvasSize by remember { mutableStateOf(Size.Zero) }

            // White drawing surface
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                        .pointerInput(isEraser, selectedColorHex) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPoints.clear()
                                    currentPoints.add(DrawingPoint(offset.x, offset.y))
                                },
                                onDrag = { change, _ ->
                                    currentPoints.add(DrawingPoint(change.position.x, change.position.y))
                                },
                                onDragEnd = {
                                    if (currentPoints.isNotEmpty()) {
                                        strokes.add(
                                            DrawingStroke(
                                                points = currentPoints.toList(),
                                                colorHex = selectedColorHex,
                                                strokeWidth = if (isEraser) 40f else 8f,
                                                isEraser = isEraser
                                            )
                                        )
                                        currentPoints.clear()
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw all committed strokes
                        renderStrokes(strokes)
                        // Draw the stroke currently being drawn
                        if (currentPoints.size >= 2) {
                            val path = Path()
                            path.moveTo(currentPoints[0].x, currentPoints[0].y)
                            currentPoints.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                            val col = if (isEraser) Color.White else hexToColor(selectedColorHex)
                            drawPath(
                                path = path,
                                color = col,
                                style = Stroke(
                                    width = if (isEraser) 40f else 8f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Color palette ─────────────────────────────────────────────────
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(PALETTE) { pc ->
                    val isSelected = !isEraser && selectedColorHex == pc.hex
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 38.dp else 32.dp)
                            .clip(CircleShape)
                            .background(pc.color)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else if (pc.hex == "#FFFFFFFF") Modifier.border(1.dp, Color.LightGray, CircleShape)
                                else Modifier
                            )
                            .clickable {
                                selectedColorHex = pc.hex
                                isEraser = false
                            }
                    )
                }
                // Eraser swatch
                item {
                    val eraserSelected = isEraser
                    Surface(
                        shape = CircleShape,
                        color = if (eraserSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .size(if (eraserSelected) 38.dp else 32.dp)
                            .clickable { isEraser = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⌫", fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Undo / Clear row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                ) { Text("Undo") }

                OutlinedButton(
                    onClick = { strokes.clear() },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                ) { Text("Clear") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Submit drawing — serialize strokes as JSON
            Button(
                onClick = {
                    val drawingData = DrawingData(
                        strokes = strokes.toList(),
                        canvasWidth = canvasSize.width,
                        canvasHeight = canvasSize.height
                    )
                    val json = Json.encodeToString(drawingData)
                    onSubmitTurn(ChainEntry(myPlayerId, EntryType.IMAGE, json))
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("SUBMIT DRAWING", style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black, letterSpacing = 2.sp
                ))
            }
        }
    }
}