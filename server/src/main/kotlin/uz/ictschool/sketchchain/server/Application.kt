package uz.ictschool.sketchchain.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uz.ictschool.sketchchain.shared.*
import java.util.concurrent.ConcurrentHashMap
import java.time.Duration

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(30)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val gameManager = GameManager()
    val connections = ConcurrentHashMap<String, MutableMap<String, DefaultWebSocketServerSession>>()
    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    // Always use the polymorphic <GameMessage> type so the discriminator is included
    gameManager.onMessage = { roomId, playerId, message ->
        connections[roomId]?.get(playerId)?.let { session ->
            try {
                session.send(json.encodeToString<GameMessage>(message))
            } catch (e: Exception) {
                println("Failed to send to $playerId: ${e.message}")
            }
        }
    }

    gameManager.broadcastToRoom = { roomId, message ->
        val text = json.encodeToString<GameMessage>(message)
        connections[roomId]?.values?.forEach { session ->
            try {
                session.send(text)
            } catch (e: Exception) {
                println("Broadcast error: ${e.message}")
            }
        }
    }

    routing {
        get("/") {
            call.respondText("✅ SketchChain Server is running!")
        }

        webSocket("/game/{roomId}") {
            val roomId = call.parameters["roomId"] ?: return@webSocket
            val playerId = call.request.queryParameters["playerId"] ?: return@webSocket
            val playerName = call.request.queryParameters["playerName"] ?: "Guest"
            // createIfAbsent=true → "Create Room" button. false → "Join Room" button (must already exist).
            val createIfAbsent = call.request.queryParameters["createIfAbsent"]?.toBoolean() ?: false

            val player = Player(playerId, playerName)

            // Register connection BEFORE deciding join/create so broadcasts immediately reach this client
            val roomConnections = connections.computeIfAbsent(roomId) { ConcurrentHashMap() }
            roomConnections[playerId] = this

            val room = when {
                !gameManager.rooms.containsKey(roomId) && createIfAbsent -> {
                    // Create a new room
                    val createdRoom = gameManager.createRoom(roomId, playerId, playerName)
                    send(json.encodeToString<GameMessage>(GameMessage.RoomStateUpdate(createdRoom)))
                    createdRoom
                }
                !gameManager.rooms.containsKey(roomId) && !createIfAbsent -> {
                    // Room doesn't exist, and we were only trying to join
                    send(json.encodeToString<GameMessage>(GameMessage.Error("Room '$roomId' not found. Check the code and try again.")))
                    close()
                    roomConnections.remove(playerId)
                    return@webSocket
                }
                else -> {
                    // Room exists — join it. joinRoom broadcasts to everyone (including the new joiner now that they're registered)
                    gameManager.joinRoom(roomId, player)
                }
            }

            if (room == null) {
                send(json.encodeToString<GameMessage>(GameMessage.Error("Could not join room '$roomId'. It may have already started.")))
                close()
                roomConnections.remove(playerId)
                return@webSocket
            }

            println("👤 Player $playerName joined room ${room.id} (${room.players.size} players total)")

            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            val message = json.decodeFromString<GameMessage>(text)
                            when (message) {
                                is GameMessage.StartGame -> gameManager.startGame(roomId)
                                is GameMessage.SubmitTurn -> {
                                    gameManager.submitTurn(roomId, playerId, message.chainId, message.entry)
                                }
                                else -> {}
                            }
                        } catch (e: Exception) {
                            println("⚠️ Failed to parse message: $text — ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("❌ Connection error for $playerName: ${e.message}")
            } finally {
                // Player disconnected — remove them and handle host reassignment / room deletion
                roomConnections.remove(playerId)
                println("🚪 Player $playerName left room $roomId")
                gameManager.handlePlayerLeave(roomId, playerId)
            }
        }
    }
}