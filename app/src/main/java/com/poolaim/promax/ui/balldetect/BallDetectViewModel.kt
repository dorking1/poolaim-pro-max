package com.poolaim.promax.ui.balldetect

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poolaim.promax.data.vision.BallDetector
import com.poolaim.promax.domain.model.BallModel
import com.poolaim.promax.domain.model.PlayerGroup
import com.poolaim.promax.domain.model.TableModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase C ViewModel: runs ball detection inside the confirmed table and
 * separates the player's group balls from the opponent's balls.
 */
class BallDetectViewModel(
    private val detector: BallDetector,
    private val playerGroup: PlayerGroup,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Detecting(val imageUri: Uri) : UiState
        data class Success(
            val imageUri: Uri,
            val table: TableModel,
            val allBalls: List<BallModel>,
            val playerBalls: List<BallModel>,
            val opponentBalls: List<BallModel>,
            val cueBall: BallModel?,
            val eightBall: BallModel?,
        ) : UiState
        data class Error(val imageUri: Uri?) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    fun detect(uri: Uri, table: TableModel) {
        _state.value = UiState.Detecting(uri)

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { detector.detect(uri, table) }
                    .getOrElse { BallDetector.BallDetectionResult.Failure }
            }

            _state.value = when (result) {
                is BallDetector.BallDetectionResult.Success -> {
                    val balls = result.balls
                    val cue = balls.firstOrNull { it.type == BallModel.Type.CUE }
                    val eight = balls.firstOrNull { it.type == BallModel.Type.EIGHT }

                    val (player, opponent) = splitByGroup(balls)

                    UiState.Success(
                        imageUri = uri,
                        table = table,
                        allBalls = balls,
                        playerBalls = player,
                        opponentBalls = opponent,
                        cueBall = cue,
                        eightBall = eight,
                    )
                }
                BallDetector.BallDetectionResult.Failure -> UiState.Error(uri)
            }
        }
    }

    /**
     * Splits solid/stripe balls according to the saved player group.
     * EIGHT_BALL and CUSTOM groups keep everything in the player list
     * (used as raw input for shot selection later).
     */
    private fun splitByGroup(balls: List<BallModel>): Pair<List<BallModel>, List<BallModel>> {
        val solids = balls.filter { it.type == BallModel.Type.SOLID }
        val stripes = balls.filter { it.type == BallModel.Type.STRIPE }
        val unknown = balls.filter { it.type == BallModel.Type.UNKNOWN }

        return when (playerGroup) {
            PlayerGroup.SOLIDS -> solids + unknown to stripes
            PlayerGroup.STRIPES -> stripes + unknown to solids
            PlayerGroup.EIGHT_BALL, PlayerGroup.CUSTOM -> balls to emptyList()
        }
    }
}
