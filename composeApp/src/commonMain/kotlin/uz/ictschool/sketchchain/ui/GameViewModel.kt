package uz.ictschool.sketchchain.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.ictschool.sketchchain.network.GameClient
import uz.ictschool.sketchchain.shared.*

object AppConfig {
    // 🌍 FOR GOOGLE PLAY (PRODUCTION):
    // You must host your server on a cloud provider (e.g., Render, Heroku, AWS, Railway)
    // Once hosted, paste your secure WebSocket URL here (must start with wss://)
    const val SERVER_URL = "wss://sketchchain.onrender.com" 

    // 🏠 FOR LOCAL TESTING:
    // Use your laptop's local IP address and port 8080 (must be on same Wi-Fi)
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

    private var myPlayerId: String = ""

    init {
        viewModelScope.launch {
            client.messages.collect { msg ->
                when (msg) {
                    is GameMessage.RoomStateUpdate -> {
                        _roomState.value = msg.room
                        _isLoading.value = false
                        println("✅ Room updated: ${msg.room.id}")
                    }
                    is GameMessage.GameStateUpdate -> _gameState.value = msg.game
                    is GameMessage.NextTurnAssignment -> _currentAssignment.value = msg
                    is GameMessage.Error -> {
                        _isLoading.value = false
                        println("❌ Server Error: ${msg.message}")
                    }
                    else -> {}
                }
            }
        }
    }

    fun joinRoom(roomId: String, playerName: String) {
        myPlayerId = (1..8).map { ('A'..'Z').random() }.joinToString("")
        _isLoading.value = true
        println("🔌 Trying to connect to room: $roomId as $playerName")

        viewModelScope.launch {
            try {
                client.connect(roomId, myPlayerId, playerName)
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
                println("❌ Connection failed: ${e.message}")
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

    fun getMyPlayerId(): String = myPlayerId
}