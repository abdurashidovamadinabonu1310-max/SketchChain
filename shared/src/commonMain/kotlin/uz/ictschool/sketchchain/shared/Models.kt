package uz.ictschool.sketchchain.shared

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String,
    val name: String,
    val avatarUrl: String? = null
)

@Serializable
enum class RoomStatus {
    LOBBY, PLAYING, FINISHED
}

@Serializable
data class Room(
    val id: String,
    val hostId: String,
    val players: List<Player>,
    val status: RoomStatus
)

@Serializable
enum class EntryType {
    TEXT, IMAGE
}

@Serializable
data class ChainEntry(
    val playerId: String,
    val type: EntryType,
    val content: String  // TEXT: sentence string | IMAGE: JSON-encoded DrawingData
)

@Serializable
data class Chain(
    val id: String,
    val startingPlayerId: String,
    val entries: MutableList<ChainEntry> = mutableListOf()
)

@Serializable
data class Game(
    val id: String,
    val roomId: String,
    val chains: MutableList<Chain> = mutableListOf(),
    val currentRound: Int = 0,
    val totalRounds: Int
)

// ── Drawing data model ────────────────────────────────────────────────────────
// Stored as JSON inside ChainEntry.content when type == IMAGE
@Serializable
data class DrawingPoint(val x: Float, val y: Float)

@Serializable
data class DrawingStroke(
    val points: List<DrawingPoint>,
    val colorHex: String = "#FF000000",  // ARGB hex string
    val strokeWidth: Float = 8f,
    val isEraser: Boolean = false
)

@Serializable
data class DrawingData(
    val strokes: List<DrawingStroke>,
    val canvasWidth: Float,
    val canvasHeight: Float
)

// ── WebSocket message protocol ────────────────────────────────────────────────
@Serializable
sealed class GameMessage {
    @Serializable
    data class JoinRoom(val player: Player, val roomId: String) : GameMessage()

    @Serializable
    data class StartGame(val roomId: String) : GameMessage()

    @Serializable
    data class SubmitTurn(val roomId: String, val chainId: String, val entry: ChainEntry) : GameMessage()

    @Serializable
    data class RoomStateUpdate(val room: Room) : GameMessage()

    @Serializable
    data class GameStateUpdate(val game: Game) : GameMessage()

    @Serializable
    data class NextTurnAssignment(
        val chainId: String,
        val expectedType: EntryType,
        val previousEntry: ChainEntry?
    ) : GameMessage()

    @Serializable
    data class Error(val message: String) : GameMessage()
}
