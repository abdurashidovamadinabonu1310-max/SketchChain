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
        
        if (room.players.none { it.id == player.id }) {
            val updatedRoom = room.copy(players = room.players + player)
            rooms[roomId] = updatedRoom
            broadcastToRoom(roomId, GameMessage.RoomStateUpdate(updatedRoom))
            return updatedRoom
        }
        return room
    }

    suspend fun startGame(roomId: String) {
        val room = rooms[roomId] ?: return
        if (room.status != RoomStatus.LOBBY || room.players.size < 2) return // Require at least 2 players
        
        // Update room status
        val updatedRoom = room.copy(status = RoomStatus.PLAYING)
        rooms[roomId] = updatedRoom
        broadcastToRoom(roomId, GameMessage.RoomStateUpdate(updatedRoom))
        
        // Initialize game
        val players = room.players
        val n = players.size
        
        val chains = players.mapIndexed { index, player ->
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
            totalRounds = n // Round 0 (Start) to N-1
        )
        games[roomId] = game
        
        broadcastToRoom(roomId, GameMessage.GameStateUpdate(game))
        
        // Send initial assignments (Round 0)
        assignTurns(game, room)
    }

    suspend fun submitTurn(roomId: String, playerId: String, chainId: String, entry: ChainEntry) {
        val game = games[roomId] ?: return
        val room = rooms[roomId] ?: return
        
        val chain = game.chains.find { it.id == chainId } ?: return
        
        // Verify this player hasn't already submitted for this round on this chain
        // In a strict implementation we'd check if `chain.entries.size == game.currentRound`
        // But simply appending is fine for MVP if we trust the client to only send once
        if (chain.entries.size == game.currentRound) {
            chain.entries.add(entry)
        }
        
        // Check if all chains have the current round completed
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
            // Game Over
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

    private fun generateId(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}
