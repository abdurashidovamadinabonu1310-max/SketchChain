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
    println("🚀 Starting SketchChain server on port $port")
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(20)
        timeout = Duration.ofSeconds(60)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val gameManager = GameManager()
    // roomId -> (playerId -> session)
    val connections = ConcurrentHashMap<String, ConcurrentHashMap<String, DefaultWebSocketServerSession>>()
    val json = Json { ignoreUnknownKeys = true }

    gameManager.onMessage = { roomId, playerId, message ->
        connections[roomId]?.get(playerId)?.let { session ->
            try {
                session.send(json.encodeToString<GameMessage>(message))
            } catch (e: Exception) {
                println("⚠️ Failed to send to $playerId: ${e.message}")
            }
        }
    }

    gameManager.broadcastToRoom = { roomId, message ->
        val text = json.encodeToString<GameMessage>(message)
        connections[roomId]?.forEach { (playerId, session) ->
            try {
                session.send(text)
            } catch (e: Exception) {
                println("⚠️ Broadcast failed for $playerId: ${e.message}")
            }
        }
    }

    routing {
        get("/") {
            call.respondText("✅ SketchChain Server is running!")
        }

        webSocket("/game/{roomId}") {
            val roomId = call.parameters["roomId"]?.uppercase() ?: run {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing roomId"))
                return@webSocket
            }
            val playerId = call.request.queryParameters["playerId"] ?: run {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing playerId"))
                return@webSocket
            }
            val playerName = call.request.queryParameters["playerName"] ?: "Guest"
            val createIfAbsent = call.request.queryParameters["createIfAbsent"].toBoolean()

            println("🔌 $playerName ($playerId) connecting to room '$roomId' (create=$createIfAbsent)")

            // Register connection FIRST so broadcasts from joinRoom reach the new player
            val roomConnections = connections.getOrPut(roomId) { ConcurrentHashMap() }
            roomConnections[playerId] = this

            val player = Player(playerId, playerName)

            val room: Room? = when {
                !gameManager.rooms.containsKey(roomId) && createIfAbsent -> {
                    val created = gameManager.createRoom(roomId, playerId, playerName)
                    send(json.encodeToString<GameMessage>(GameMessage.RoomStateUpdate(created)))
                    created
                }
                !gameManager.rooms.containsKey(roomId) && !createIfAbsent -> {
                    send(json.encodeToString<GameMessage>(
                        GameMessage.Error("Room '$roomId' doesn't exist. Check the code and try again.")
                    ))
                    close(CloseReason(CloseReason.Codes.NORMAL, "Room not found"))
                    roomConnections.remove(playerId)
                    return@webSocket
                }
                else -> {
                    gameManager.joinRoom(roomId, player)
                }
            }

            if (room == null) {
                send(json.encodeToString<GameMessage>(
                    GameMessage.Error("Cannot join room '$roomId' — the game has already started.")
                ))
                close(CloseReason(CloseReason.Codes.NORMAL, "Game already started"))
                roomConnections.remove(playerId)
                return@webSocket
            }

            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            when (val message = json.decodeFromString<GameMessage>(text)) {
                                is GameMessage.StartGame -> gameManager.startGame(roomId)
                                is GameMessage.SubmitTurn -> {
                                    gameManager.submitTurn(roomId, playerId, message.chainId, message.entry)
                                }
                                else -> {}
                            }
                        } catch (e: Exception) {
                            println("⚠️ Bad message from $playerName: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("❌ Connection error for $playerName in room '$roomId': ${e.message}")
            } finally {
                roomConnections.remove(playerId)
                println("🚪 $playerName left room '$roomId'")
                gameManager.handlePlayerLeave(roomId, playerId)
            }
        }
    }
}