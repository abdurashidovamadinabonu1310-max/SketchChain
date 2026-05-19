package uz.ictschool.sketchchain.network

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uz.ictschool.sketchchain.shared.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
class GameClient(private val serverUrl: String) {

    private val client = HttpClient {


        install(WebSockets) {
            pingInterval = 15000L
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _messages = MutableSharedFlow<GameMessage>()
    val messages: SharedFlow<GameMessage> = _messages.asSharedFlow()

    private var session: DefaultClientWebSocketSession? = null

    suspend fun connect(roomId: String, playerId: String, playerName: String) {

        // Use string builder to handle if serverUrl already has ws:// or wss://
        val url = if (serverUrl.startsWith("ws")) {
            "$serverUrl/game/$roomId?playerId=$playerId&playerName=$playerName"
        } else {
            "ws://$serverUrl/game/$roomId?playerId=$playerId&playerName=$playerName"
        }

        println("🔌 Connecting to $url")

        try {

            session = client.webSocketSession(
                urlString = url
            )

            println("✅ WebSocket Connected Successfully!")

            session?.let { currentSession ->

                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {

                    try {

                        currentSession.incoming.consumeAsFlow().collect { frame ->

                            if (frame is Frame.Text) {

                                val text = frame.readText()

                                println("📥 Received: $text")

                                try {

                                    val msg = json.decodeFromString<GameMessage>(text)

                                    _messages.emit(msg)

                                } catch (e: Exception) {

                                    println("❌ Parse error: $text")
                                }
                            }
                        }

                    } catch (e: Exception) {

                        println("❌ Incoming collector failed: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {

            println("❌ WebSocket Error: ${e.message}")
            e.printStackTrace()
            throw e // Rethrow to let ViewModel handle the failure
        }
    }
    suspend fun sendMessage(message: GameMessage) {
        try {
            session?.send(Frame.Text(json.encodeToString(message)))
            println("📤 Sent: ${message::class.simpleName}")
        } catch (e: Exception) {
            println("❌ Send failed: ${e.message}")
        }
    }

    suspend fun disconnect() {
        try {
            session?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnected"))
            session = null
        } catch (e: Exception) {
            println("❌ Error disconnecting: ${e.message}")
        }
    }
}