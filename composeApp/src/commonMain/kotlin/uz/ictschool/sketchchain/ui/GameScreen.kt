package uz.ictschool.sketchchain.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

// ── Colour helpers ────────────────────────────────────────────────────────────

private fun hexToColor(hex: String): Color = try {
    val c = hex.removePrefix("#")
    val full = if (c.length == 6) "ff$c" else c
    val v = full.toLong(16)
    Color(
        alpha = ((v shr 24) and 0xFF).toFloat() / 255f,
        red   = ((v shr 16) and 0xFF).toFloat() / 255f,
        green = ((v shr  8) and 0xFF).toFloat() / 255f,
        blue  = ( v         and 0xFF).toFloat() / 255f
    )
} catch (_: Exception) { Color.Black }

private fun DrawScope.renderStrokes(
    strokes: List<DrawingStroke>,
    scaleX: Float = 1f,
    scaleY: Float = 1f
) {
    strokes.forEach { stroke ->
        if (stroke.points.size < 2) return@forEach
        val path = Path()
        path.moveTo(stroke.points[0].x * scaleX, stroke.points[0].y * scaleY)
        stroke.points.drop(1).forEach { pt -> path.lineTo(pt.x * scaleX, pt.y * scaleY) }
        drawPath(
            path  = path,
            color = if (stroke.isEraser) Color.White else hexToColor(stroke.colorHex),
            style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** Renders a previously-saved [DrawingData] JSON string. Used both in-game and on results screen. */
@Composable
fun DrawingView(content: String, modifier: Modifier = Modifier) {
    val data = remember(content) {
        try { Json.decodeFromString<DrawingData>(content) } catch (_: Exception) { null }
    }
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        if (data == null || data.strokes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("[ Empty drawing ]", color = Color.LightGray)
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val sx = if (data.canvasWidth  > 0) size.width  / data.canvasWidth  else 1f
                val sy = if (data.canvasHeight > 0) size.height / data.canvasHeight else 1f
                renderStrokes(data.strokes, sx, sy)
            }
        }
    }
}

// ── Palette ───────────────────────────────────────────────────────────────────

private data class Swatch(val hex: String, val color: Color)

private val PALETTE = listOf(
    Swatch("#FF000000", Color.Black),
    Swatch("#FFFFFFFF", Color.White),
    Swatch("#FFFF6B6B", Color(0xFFFF6B6B)),
    Swatch("#FFFF9800", Color(0xFFFF9800)),
    Swatch("#FFFFE135", Color(0xFFFFE135)),
    Swatch("#FF4CAF50", Color(0xFF4CAF50)),
    Swatch("#FF2196F3", Color(0xFF2196F3)),
    Swatch("#FF4ECDC4", Color(0xFF4ECDC4)),
    Swatch("#FFC792EA", Color(0xFFC792EA)),
    Swatch("#FF795548", Color(0xFF795548)),
)

// ── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun GameScreen(
    myPlayerId: String,
    assignment: GameMessage.NextTurnAssignment?,
    onSubmitTurn: (ChainEntry) -> Unit
) {
    // Waiting state
    if (assignment == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp))
                Text(
                    "Waiting for other players…",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Hang tight while everyone finishes their turn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
        return
    }

    val isFirstRound = assignment.previousEntry == null
    val isTextTurn   = assignment.expectedType == EntryType.TEXT

    val headerText = when {
        isTextTurn && isFirstRound -> "Write any sentence"
        isTextTurn                 -> "Describe the drawing"
        else                       -> "Draw this sentence"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        // Header
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(vertical = 14.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(14.dp))

        // Previous entry card
        assignment.previousEntry?.let { prev ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "PREVIOUS ENTRY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            letterSpacing = 1.5.sp
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    if (prev.type == EntryType.TEXT) {
                        Text(
                            "\"${prev.content}\"",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        DrawingView(
                            content = prev.content,
                            modifier = Modifier.fillMaxWidth().height(150.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── TEXT mode ─────────────────────────────────────────────────────────
        if (isTextTurn) {
            var text by remember { mutableStateOf("") }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = {
                    Text(if (isFirstRound) "Write anything…" else "What do you see?")
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .imePadding(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onSubmitTurn(ChainEntry(myPlayerId, EntryType.TEXT, text.trim())) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Submit", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }

        // ── DRAWING mode ──────────────────────────────────────────────────────
        } else {
            val strokes       = remember { mutableStateListOf<DrawingStroke>() }
            val currentPoints = remember { mutableStateListOf<DrawingPoint>() }
            var selectedHex   by remember { mutableStateOf("#FF000000") }
            var isEraser      by remember { mutableStateOf(false) }
            var canvasSize    by remember { mutableStateOf(Size.Zero) }

            // Drawing surface
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                        .pointerInput(isEraser, selectedHex) {
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
                                        strokes.add(DrawingStroke(
                                            points      = currentPoints.toList(),
                                            colorHex    = selectedHex,
                                            strokeWidth = if (isEraser) 40f else 8f,
                                            isEraser    = isEraser
                                        ))
                                        currentPoints.clear()
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        renderStrokes(strokes)
                        if (currentPoints.size >= 2) {
                            val path = Path()
                            path.moveTo(currentPoints[0].x, currentPoints[0].y)
                            currentPoints.drop(1).forEach { path.lineTo(it.x, it.y) }
                            drawPath(
                                path  = path,
                                color = if (isEraser) Color.White else hexToColor(selectedHex),
                                style = Stroke(
                                    width = if (isEraser) 40f else 8f,
                                    cap   = StrokeCap.Round,
                                    join  = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Colour palette row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(PALETTE) { sw ->
                    val sel = !isEraser && selectedHex == sw.hex
                    Box(
                        modifier = Modifier
                            .size(if (sel) 36.dp else 30.dp)
                            .clip(CircleShape)
                            .background(sw.color)
                            .then(when {
                                sel            -> Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                sw.hex == "#FFFFFFFF" -> Modifier.border(1.dp, Color.LightGray, CircleShape)
                                else           -> Modifier
                            })
                            .clickable { selectedHex = sw.hex; isEraser = false }
                    )
                }
                // Eraser
                item {
                    Surface(
                        shape  = CircleShape,
                        color  = if (isEraser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(if (isEraser) 36.dp else 30.dp)
                            .clickable { isEraser = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("⌫", fontSize = 13.sp) }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Undo / Clear
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                    shape  = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Undo") }

                OutlinedButton(
                    onClick = { strokes.clear() },
                    shape  = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    val data = DrawingData(strokes.toList(), canvasSize.width, canvasSize.height)
                    onSubmitTurn(ChainEntry(myPlayerId, EntryType.IMAGE, Json.encodeToString(data)))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor   = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Submit Drawing", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}