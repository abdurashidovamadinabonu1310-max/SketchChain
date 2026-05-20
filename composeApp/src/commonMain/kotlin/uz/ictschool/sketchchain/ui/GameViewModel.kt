package uz.ictschool.sketchchain.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.ictschool.sketchchain.network.GameClient
import uz.ictschool.sketchchain.shared.*

object AppConfig {
    // 🌍 FOR GOOGLE PLAY (PRODUCTION):
    const val SERVER_URL = "wss://sketchchain.onrender.com"

    // 🏠 FOR LOCAL TESTING (must be on same Wi-Fi):
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
                    is GameMessage.GameStateUpdate -> _gameState.value = msg.game
                    is GameMessage.NextTurnAssignment -> _currentAssignment.value = msg
                    is GameMessage.Error -> {
                        _isLoading.value = false
                        _errorMessage.value = msg.message
                        // On error, disconnect so user can try again cleanly
                        viewModelScope.launch { client.disconnect() }
                        println("❌ Server Error: ${msg.message}")
                    }
                    else -> {}
                }
            }
        }
    }

    /** Called when user clicks "Create New Game" — room code is auto-generated */
    fun createRoom(playerName: String) {
        val roomId = generateRoomCode()
        connectInternal(roomId, playerName, createIfAbsent = true)
    }

    /** Called when user types an existing code and clicks "Join Game" */
    fun joinRoom(roomId: String, playerName: String) {
        connectInternal(roomId, playerName, createIfAbsent = false)
    }

    private fun connectInternal(roomId: String, playerName: String, createIfAbsent: Boolean) {
        myPlayerId = (1..8).map { ('A'..'Z').random() }.joinToString("")
        _isLoading.value = true
        println("🔌 Connecting to room: $roomId (create=$createIfAbsent) as $playerName")

        viewModelScope.launch {
            try {
                client.connect(roomId, myPlayerId, playerName, createIfAbsent)
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
                _errorMessage.value = e.message ?: "Failed to connect"
            }
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

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return (1..4).map { chars.random() }.joinToString("")
    }
}