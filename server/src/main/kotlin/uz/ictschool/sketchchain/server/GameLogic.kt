package uz.ictschool.sketchchain.server

import uz.ictschool.sketchchain.shared.*
import java.util.UUID

class GameManager {
    val rooms = mutableMapOf<String, Room>()
    val games = mutableMapOf<String, Game>()

    // Callbacks to send messages to players (handled by routing)
    var onMessage: suspend (roomId: String, playerId: String, message: GameMessage) -> Unit = { _, _, _ -> }
    var broadcastToRoom: suspend (roomId: String, message: GameMessage) -> Unit = { _, _ -> }

    fun createRoom(roomId: String, hostId: String, hostName: String): Room {
        val host = Player(hostId, hostName)
        val room = Room(
            id = roomId,
            hostId = hostId,
            players = listOf(host),
            status = RoomStatus.LOBBY
        )
        rooms[roomId] = room
        return room
    }

    suspend fun joinRoom(roomId: String, player: Player): Room? {
        val room = rooms[roomId] ?: return null
        if (room.status != RoomStatus.LOBBY) return null

        return if (room.players.none { it.id == player.id }) {
            val updatedRoom = room.copy(players = room.players + player)
            rooms[roomId] = updatedRoom
            // Broadcast to everyone (including the new joiner, who is already registered in connections)
            broadcastToRoom(roomId, GameMessage.RoomStateUpdate(updatedRoom))
            updatedRoom
        } else {
            // Player already in room (e.g. reconnect) — just send current state to them
            onMessage(roomId, player.id, GameMessage.RoomStateUpdate(room))
            room
        }
    }

    /**
     * Called when a player's WebSocket disconnects.
     * - Removes the player from the room.
     * - If the room is now empty, deletes it.
     * - If the host left, assigns the first remaining player as the new host.
     * - Broadcasts updated room state to remaining players.
     */
    suspend fun handlePlayerLeave(roomId: String, playerId: String) {
        val room = rooms[roomId] ?: return
        val remainingPlayers = room.players.filter { it.id != playerId }

        if (remainingPlayers.isEmpty()) {
            // Room is empty — clean up
            rooms.remove(roomId)
            games.remove(roomId)
            println("🗑️ Room $roomId deleted (no players left)")
            return
        }

        // Reassign host if the host left
        val newHostId = if (room.hostId == playerId) {
            remainingPlayers.first().id
        } else {
            room.hostId
        }

        val updatedRoom = room.copy(players = remainingPlayers, hostId = newHostId)
        rooms[roomId] = updatedRoom

        println("📢 Broadcasting updated room (${remainingPlayers.size} players) after $playerId left. Host: $newHostId")
        broadcastToRoom(roomId, GameMessage.RoomStateUpdate(updatedRoom))
    }

    suspend fun startGame(roomId: String) {
        val room = rooms[roomId] ?: return
        if (room.status != RoomStatus.LOBBY || room.players.size < 2) return

        val updatedRoom = room.copy(status = RoomStatus.PLAYING)
        rooms[roomId] = updatedRoom
        broadcastToRoom(roomId, GameMessage.RoomStateUpdate(updatedRoom))

        val players = room.players
        val n = players.size

        val chains = players.map { player ->
            Chain(
                id = UUID.randomUUID().toString(),
                startingPlayerId = player.id
            )
        }.toMutableList()

        val game = Game(
            id = UUID.randomUUID().toString(),
            roomId = roomId,
            chains = chains,
            currentRound = 0,
            totalRounds = n
        )
        games[roomId] = game

        broadcastToRoom(roomId, GameMessage.GameStateUpdate(game))
        assignTurns(game, room)
    }

    suspend fun submitTurn(roomId: String, playerId: String, chainId: String, entry: ChainEntry) {
        val game = games[roomId] ?: return
        val room = rooms[roomId] ?: return

        val chain = game.chains.find { it.id == chainId } ?: return

        if (chain.entries.size == game.currentRound) {
            chain.entries.add(entry)
        }

        val allSubmitted = game.chains.all { it.entries.size > game.currentRound }
        if (allSubmitted) {
            advanceRound(game, room)
        }
    }

    private suspend fun advanceRound(game: Game, room: Room) {
        val nextRound = game.currentRound + 1
        val updatedGame = game.copy(currentRound = nextRound)
        games[game.roomId] = updatedGame

        broadcastToRoom(game.roomId, GameMessage.GameStateUpdate(updatedGame))

        if (nextRound < updatedGame.totalRounds) {
            assignTurns(updatedGame, room)
        } else {
            val finishedRoom = room.copy(status = RoomStatus.FINISHED)
            rooms[game.roomId] = finishedRoom
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

            val previousEntry = if (r > 0) chain.entries.last() else null

            val message = GameMessage.NextTurnAssignment(
                chainId = chain.id,
                expectedType = expectedType,
                previousEntry = previousEntry
            )
            onMessage(game.roomId, player.id, message)
        }
    }
}
