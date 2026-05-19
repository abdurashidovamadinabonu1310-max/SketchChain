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
import java.time.Duration   // ← Use Java Duration here

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
    val json = Json { ignoreUnknownKeys = true }

    gameManager.onMessage = { roomId, playerId, message ->
        connections[roomId]?.get(playerId)?.let { session ->
            try {
                session.send(json.encodeToString(message))
            } catch (e: Exception) {
                println("Failed to send to $playerId")
            }
        }
    }

    gameManager.broadcastToRoom = { roomId, message ->
        val text = json.encodeToString(message)
        connections[roomId]?.values?.forEach { session ->
            try {
                session.send(text)
            } catch (e: Exception) {}
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

            val roomConnections = connections.computeIfAbsent(roomId) { ConcurrentHashMap() }
            roomConnections[playerId] = this

            val player = Player(playerId, playerName)

            val room = if (!gameManager.rooms.containsKey(roomId)) {

                val createdRoom = gameManager.createRoom(
                    roomId,
                    playerId,
                    playerName
                )

                send(json.encodeToString<GameMessage>(GameMessage.RoomStateUpdate(createdRoom)))

                createdRoom

            } else {

                gameManager.joinRoom(roomId, player)
            }

            if (room == null) {
                send(json.encodeToString<GameMessage>(GameMessage.Error("Room not found")))
                close()
                return@webSocket
            }

            println("👤 Player $playerName joined room ${room.id}")

            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val message = json.decodeFromString<GameMessage>(text)

                        when (message) {
                            is GameMessage.StartGame -> gameManager.startGame(roomId)
                            is GameMessage.SubmitTurn -> {
                                gameManager.submitTurn(roomId, playerId, message.chainId, message.entry)
                            }
                            else -> {}
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                roomConnections.remove(playerId)
                println("❌ Player $playerName left room $roomId")
            }
        }
    }
}