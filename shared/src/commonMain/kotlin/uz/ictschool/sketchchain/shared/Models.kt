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
    val content: String // Either text sentence or image Base64/URL
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

// WebSocket Message sealed class for communication between Server and Client
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
    data class NextTurnAssignment(val chainId: String, val expectedType: EntryType, val previousEntry: ChainEntry?) : GameMessage()
    
    @Serializable
    data class Error(val message: String) : GameMessage()
}
