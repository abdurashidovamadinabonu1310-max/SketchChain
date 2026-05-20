package uz.ictschool.sketchchain.server

import uz.ictschool.sketchchain.shared.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GameManager {
    // ConcurrentHashMap for thread-safety under Netty's multi-threaded execution
    private val _rooms = ConcurrentHashMap<String, Room>()
    private val _games = ConcurrentHashMap<String, Game>()

    val rooms: Map<String, Room> get() = _rooms
    val games: Map<String, Game> get() = _games

    var onMessage: suspend (roomId: String, playerId: String, message: GameMessage) -> Unit = { _, _, _ -> }
    var broadcastToRoom: suspend (roomId: String, message: GameMessage) -> Unit = { _, _ -> }

    fun createRoom(roomId: String, hostId: String, hostName: String): Room {
        val host = Player(hostId, hostName)
        val room = Room(id = roomId, hostId = hostId, players = listOf(host), status = RoomStatus.LOBBY)
        _rooms[roomId] = room
        println("🏠 Room '$roomId' created by $hostName")
        return room
    }

    suspend fun joinRoom(roomId: String, player: Player): Room? {
        val room = _rooms[roomId] ?: return null
        if (room.status != RoomStatus.LOBBY) {
            println("⚠️ ${player.name} tried to join room '$roomId' but game already started")
            return null
        }

        return if (room.players.none { it.id == player.id }) {
            val updatedRoom = room.copy(players = room.players + player)
            _rooms[roomId] = updatedRoom
            println("👥 ${player.name} joined room '$roomId' (${updatedRoom.players.size} players)")
            broadcastToRoom(roomId, GameMessage.RoomStateUpdate(updatedRoom))
            updatedRoom
        } else {
            // Reconnect — send current state to this player only
            println("🔄 ${player.name} reconnected to room '$roomId'")
            onMessage(roomId, player.id, GameMessage.RoomStateUpdate(room))
            room
        }
    }

    suspend fun handlePlayerLeave(roomId: String, playerId: String) {
        val room = _rooms[roomId] ?: return
        val remainingPlayers = room.players.filter { it.id != playerId }

        if (remainingPlayers.isEmpty()) {
            _rooms.remove(roomId)
            _games.remove(roomId)
            println("🗑️ Room '$roomId' deleted — no players left")
            return
        }

        val newHostId = if (room.hostId == playerId) {
            remainingPlayers.first().id.also { println("👑 New host for '$roomId': $it") }
        } else {
            room.hostId
        }

        val updatedRoom = room.copy(players = remainingPlayers, hostId = newHostId)
        _rooms[roomId] = updatedRoom
        broadcastToRoom(roomId, GameMessage.RoomStateUpdate(updatedRoom))
    }

    suspend fun startGame(roomId: String) {
        val room = _rooms[roomId] ?: return
        if (room.status != RoomStatus.LOBBY || room.players.size < 2) {
            println("⚠️ Cannot start '$roomId': status=${room.status}, players=${room.players.size}")
            return
        }

        val updatedRoom = room.copy(status = RoomStatus.PLAYING)
        _rooms[roomId] = updatedRoom
        broadcastToRoom(roomId, GameMessage.RoomStateUpdate(updatedRoom))

        val n = room.players.size
        val chains = room.players.map { player ->
            Chain(id = UUID.randomUUID().toString(), startingPlayerId = player.id)
        }.toMutableList()

        val game = Game(
            id = UUID.randomUUID().toString(),
            roomId = roomId,
            chains = chains,
            currentRound = 0,
            totalRounds = n
        )
        _games[roomId] = game

        println("🎮 Game started in room '$roomId' with $n players, $n rounds")
        broadcastToRoom(roomId, GameMessage.GameStateUpdate(game))
        assignTurns(game, room)
    }

    suspend fun submitTurn(roomId: String, playerId: String, chainId: String, entry: ChainEntry) {
        val game = _games[roomId] ?: return
        val room = _rooms[roomId] ?: return

        val chain = game.chains.find { it.id == chainId } ?: return

        synchronized(chain.entries) {
            if (chain.entries.size == game.currentRound) {
                chain.entries.add(entry)
            }
        }

        val allSubmitted = game.chains.all { it.entries.size > game.currentRound }
        if (allSubmitted) {
            advanceRound(game, room)
        }
    }

    private suspend fun advanceRound(game: Game, room: Room) {
        val nextRound = game.currentRound + 1
        val updatedGame = game.copy(currentRound = nextRound)
        _games[game.roomId] = updatedGame

        broadcastToRoom(game.roomId, GameMessage.GameStateUpdate(updatedGame))

        if (nextRound < updatedGame.totalRounds) {
            assignTurns(updatedGame, room)
        } else {
            val finishedRoom = room.copy(status = RoomStatus.FINISHED)
            _rooms[game.roomId] = finishedRoom
            println("🏁 Game finished in room '${game.roomId}'")
            broadcastToRoom(game.roomId, GameMessage.RoomStateUpdate(finishedRoom))
        }
    }

    private suspend fun assignTurns(game: Game, room: Room) {
        val n = room.players.size
        val r = game.currentRound
        val expectedType = if (r % 2 == 0) EntryType.TEXT else EntryType.IMAGE

        for (i in 0 until n) {
            val player = room.players[i]
            val chainIndex = (i + r) % n
            val chain = game.chains[chainIndex]
            val previousEntry = if (r > 0) chain.entries.lastOrNull() else null

            onMessage(
                game.roomId, player.id,
                GameMessage.NextTurnAssignment(chain.id, expectedType, previousEntry)
            )
        }
    }
}
