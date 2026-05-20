package uz.ictschool.sketchchain.network

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uz.ictschool.sketchchain.shared.*

class GameClient(private val serverUrl: String) {

    private val client = HttpClient {
        install(WebSockets) {
            pingInterval = 15000L
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    private val _messages = MutableSharedFlow<GameMessage>()
    val messages: SharedFlow<GameMessage> = _messages.asSharedFlow()

    private var session: DefaultClientWebSocketSession? = null

    /**
     * @param createIfAbsent  true = "Create Room" (server creates room if it doesn't exist)
     *                        false = "Join Room"  (server returns error if room doesn't exist)
     */
    suspend fun connect(roomId: String, playerId: String, playerName: String, createIfAbsent: Boolean) {
        val base = if (serverUrl.startsWith("ws")) serverUrl else "ws://$serverUrl"
        val encodedName = playerName.replace(" ", "%20")
        val url = "$base/game/$roomId?playerId=$playerId&playerName=$encodedName&createIfAbsent=$createIfAbsent"

        println("🔌 Connecting to $url")

        try {
            session = client.webSocketSession(urlString = url)
            println("✅ WebSocket Connected!")

            val currentSession = session ?: return

            CoroutineScope(Dispatchers.Default).launch {
                try {
                    currentSession.incoming.consumeAsFlow().collect { frame ->
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            println("📥 Received: $text")
                            try {
                                val msg = json.decodeFromString<GameMessage>(text)
                                _messages.emit(msg)
                            } catch (e: Exception) {
                                println("❌ Parse error for: $text — ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("❌ Incoming collector failed: ${e.message}")
                }
            }

        } catch (e: Exception) {
            println("❌ WebSocket Error: ${e.message}")
            throw e
        }
    }

    suspend fun sendMessage(message: GameMessage) {
        try {
            val text = json.encodeToString<GameMessage>(message)
            session?.send(Frame.Text(text))
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