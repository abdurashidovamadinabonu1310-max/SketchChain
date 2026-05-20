package uz.ictschool.sketchchain.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.ictschool.sketchchain.network.GameClient
import uz.ictschool.sketchchain.shared.*

object AppConfig {
    // 🌍 PRODUCTION (Render):
    const val SERVER_URL = "wss://sketchchain.onrender.com"

    // 🏠 LOCAL TESTING (same Wi-Fi):
    // const val SERVER_URL = "ws://192.168.1.28:8080"
}

class GameViewModel : ViewModel() {

    private val client = GameClient(AppConfig.SERVER_URL)

    private val _roomState = MutableStateFlow<Room?>(null)
    val roomState: StateFlow<Room?> = _roomState.asStateFlow()

    private val _gameState = MutableStateFlow<Game?>(null)
    val gameState: StateFlow<Game?> = _gameState.asStateFlow()

    private val _currentAssignment = MutableStateFlow<GameMessage.NextTurnAssignment?>(null)
    val currentAssignment: StateFlow<GameMessage.NextTurnAssignment?> = _currentAssignment.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingMessage = MutableStateFlow("Connecting...")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var myPlayerId: String = ""

    init {
        viewModelScope.launch {
            client.messages.collect { msg ->
                when (msg) {
                    is GameMessage.RoomStateUpdate -> {
                        _roomState.value = msg.room
                        _isLoading.value = false
                        println("✅ Room updated: ${msg.room.id}, players=${msg.room.players.size}")
                    }
                    is GameMessage.GameStateUpdate -> {
                        _gameState.value = msg.game
                        println("🎮 Game state updated: round=${msg.game.currentRound}")
                    }
                    is GameMessage.NextTurnAssignment -> {
                        _currentAssignment.value = msg
                        println("🎯 Assignment received: ${msg.expectedType}")
                    }
                    is GameMessage.Error -> {
                        _isLoading.value = false
                        _errorMessage.value = msg.message
                        viewModelScope.launch { client.disconnect() }
                        println("❌ Server error: ${msg.message}")
                    }
                    else -> {}
                }
            }
        }
    }

    /** "Create New Game" — generates a room code, always creates on server */
    fun createRoom(playerName: String) {
        val roomId = generateRoomCode()
        connectWithRetry(roomId, playerName, createIfAbsent = true)
    }

    /** "Join Game" — only joins if the room EXISTS; shows error if not */
    fun joinRoom(roomId: String, playerName: String) {
        connectWithRetry(roomId.uppercase().trim(), playerName, createIfAbsent = false)
    }

    /**
     * Retries the WebSocket connection up to [maxAttempts] times.
     * This handles Render's free-tier cold start (server wakes up in ~15-30s).
     */
    private fun connectWithRetry(roomId: String, playerName: String, createIfAbsent: Boolean, maxAttempts: Int = 5) {
        myPlayerId = (1..8).map { ('A'..'Z').random() }.joinToString("")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            var lastError: String = "Unknown error"
            for (attempt in 1..maxAttempts) {
                _loadingMessage.value = if (attempt == 1) "Connecting..." else "Retrying... ($attempt/$maxAttempts)"
                println("🔌 Connection attempt $attempt/$maxAttempts to room '$roomId'")
                try {
                    client.connect(roomId, myPlayerId, playerName, createIfAbsent, viewModelScope)
                    // Success — the listener will update _roomState when a message arrives
                    return@launch
                } catch (e: Exception) {
                    lastError = e.message ?: "Connection failed"
                    println("⚠️ Attempt $attempt failed: $lastError")
                    if (attempt < maxAttempts) {
                        _loadingMessage.value = "Server waking up... ($attempt/$maxAttempts)"
                        delay(3000L) // wait 3s before retry
                    }
                }
            }
            // All attempts exhausted
            _isLoading.value = false
            _errorMessage.value = "Could not reach server. Please try again.\n($lastError)"
        }
    }

    fun startGame() {
        val room = _roomState.value ?: return
        viewModelScope.launch {
            client.sendMessage(GameMessage.StartGame(room.id))
        }
    }

    fun submitTurn(entry: ChainEntry) {
        val room = _roomState.value ?: return
        val assignment = _currentAssignment.value ?: return
        viewModelScope.launch {
            client.sendMessage(GameMessage.SubmitTurn(room.id, assignment.chainId, entry))
            _currentAssignment.value = null
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun leaveRoom() {
        viewModelScope.launch {
            client.disconnect()
            _roomState.value = null
            _gameState.value = null
            _currentAssignment.value = null
            _isLoading.value = false
            _errorMessage.value = null
        }
    }

    fun resetGame() = leaveRoom()

    fun getMyPlayerId(): String = myPlayerId

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { client.disconnect() }
    }

    private fun generateRoomCode(): String =
        (1..4).map { ('A'..'Z').random() }.joinToString("")
}