package uz.ictschool.sketchchain.network

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uz.ictschool.sketchchain.shared.*

class GameClient(private val serverUrl: String) {

    private val client = HttpClient {
        install(WebSockets) {
            pingInterval = 20_000L
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _messages = MutableSharedFlow<GameMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<GameMessage> = _messages.asSharedFlow()

    private var session: DefaultClientWebSocketSession? = null
    private var listenerJob: Job? = null

    /**
     * Connect to the WebSocket server.
     * @param createIfAbsent true = create room if it doesn't exist (Create flow)
     *                       false = only join existing room (Join flow)
     */
    suspend fun connect(
        roomId: String,
        playerId: String,
        playerName: String,
        createIfAbsent: Boolean,
        scope: CoroutineScope
    ) {
        // Cancel any previous listener
        listenerJob?.cancel()
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "Reconnecting"))
        session = null

        val base = if (serverUrl.startsWith("ws")) serverUrl else "ws://$serverUrl"
        val encodedName = playerName.trim().replace(" ", "%20")
        val url = "$base/game/${roomId.uppercase()}?playerId=$playerId&playerName=$encodedName&createIfAbsent=$createIfAbsent"

        println("🔌 Connecting to $url")
        val newSession = client.webSocketSession(urlString = url)
        session = newSession
        println("✅ WebSocket connected!")

        // Start listening in the provided scope (ViewModel scope) so it's properly cancelled
        listenerJob = scope.launch(Dispatchers.IO) {
            try {
                newSession.incoming.consumeAsFlow().collect { frame ->
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        println("📥 Received: $text")
                        try {
                            val msg = json.decodeFromString<GameMessage>(text)
                            _messages.emit(msg)
                        } catch (e: Exception) {
                            println("❌ Parse error: $text — ${e.message}")
                        }
                    }
                }
            } catch (e: CancellationException) {
                println("🔕 Listener cancelled")
            } catch (e: Exception) {
                println("❌ Listener error: ${e.message}")
            }
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
            listenerJob?.cancel()
            listenerJob = null
            session?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnected"))
            session = null
        } catch (e: Exception) {
            println("❌ Disconnect error: ${e.message}")
        }
    }
}