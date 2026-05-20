package uz.ictschool.sketchchain

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import uz.ictschool.sketchchain.shared.RoomStatus
import uz.ictschool.sketchchain.ui.*
import uz.ictschool.sketchchain.ui.theme.SketchChainTheme

@Composable
fun App() {
    val viewModel = remember { GameViewModel() }

    val roomState    by viewModel.roomState.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val assignment   by viewModel.currentAssignment.collectAsState()
    val gameState    by viewModel.gameState.collectAsState()

    SketchChainTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                roomState == null -> {
                    HomeScreen(
                        onCreateRoom = { playerName -> viewModel.createRoom(playerName) },
                        onJoinRoom   = { roomId, playerName -> viewModel.joinRoom(roomId, playerName) },
                        isLoading    = isLoading,
                        errorMessage = errorMessage,
                        onClearError = { viewModel.clearError() }
                    )
                }
                roomState?.status == RoomStatus.LOBBY -> {
                    LobbyScreen(
                        room        = roomState,
                        myPlayerId  = viewModel.getMyPlayerId(),
                        onStartGame = { viewModel.startGame() },
                        onLeaveRoom = { viewModel.leaveRoom() }
                    )
                }
                roomState?.status == RoomStatus.PLAYING -> {
                    GameScreen(
                        myPlayerId   = viewModel.getMyPlayerId(),
                        assignment   = assignment,
                        onSubmitTurn = { viewModel.submitTurn(it) }
                    )
                }
                roomState?.status == RoomStatus.FINISHED -> {
                    ResultsScreen(
                        game        = gameState,
                        room        = roomState,
                        onBackToHome = { viewModel.resetGame() }
                    )
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}