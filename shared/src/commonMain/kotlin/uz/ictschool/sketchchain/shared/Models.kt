package uz.ictschool.sketchchain.shared

import kotlinx.serialization.SerialName
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
    val content: String
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

/**
 * All messages sent over the WebSocket between server and clients.
 * @SerialName makes the type discriminator short and explicit — required for
 * reliable cross-platform polymorphic deserialization.
 */
@Serializable
sealed class GameMessage {

    @Serializable
    @SerialName("StartGame")
    data class StartGame(val roomId: String) : GameMessage()

    @Serializable
    @SerialName("SubmitTurn")
    data class SubmitTurn(val roomId: String, val chainId: String, val entry: ChainEntry) : GameMessage()

    @Serializable
    @SerialName("RoomStateUpdate")
    data class RoomStateUpdate(val room: Room) : GameMessage()

    @Serializable
    @SerialName("GameStateUpdate")
    data class GameStateUpdate(val game: Game) : GameMessage()

    @Serializable
    @SerialName("NextTurnAssignment")
    data class NextTurnAssignment(
        val chainId: String,
        val expectedType: EntryType,
        val previousEntry: ChainEntry?
    ) : GameMessage()

    @Serializable
    @SerialName("Error")
    data class Error(val message: String) : GameMessage()
}
