package com.poolaim.promax.ui.shotplan

import androidx.lifecycle.ViewModel
import com.poolaim.promax.domain.model.BallModel
import com.poolaim.promax.domain.model.TableModel
import com.poolaim.promax.domain.physics.ShotCalculator
import com.poolaim.promax.domain.physics.ShotPlan
import com.poolaim.promax.domain.physics.TableGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.withContext

/**
 * Phase D ViewModel: takes the confirmed balls and produces the ranked
 * shot plan (best shot first). Pure geometry; no statistical claims.
 */
class ShotPlanViewModel(
    private val calculator: ShotCalculator = ShotCalculator(),
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Computing : UiState
        data class Success(val plan: ShotPlan) : UiState
        data class Error(val reason: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    fun compute(
        cueBall: BallModel?,
        playerBalls: List<BallModel>,
        allBalls: List<BallModel>,
        table: TableModel,
    ) {
        if (cueBall == null) {
            _state.value = UiState.Error("كرة اللي غير مكتشفة")
            return
        }
        if (playerBalls.isEmpty()) {
            _state.value = UiState.Error("لا توجد كرات للمجموعة المختارة")
            return
        }

        _state.value = UiState.Computing

        viewModelScope.launch {
            val plan = withContext(Dispatchers.Default) {
                val geometry = TableGeometry(
                    width = 800f,
                    height = warpHeight(table),
                )
                calculator.calculateShots(
                    cueBall = cueBall,
                    playerBalls = playerBalls,
                    allBalls = allBalls,
                    geometry = geometry,
                )
            }
            _state.value = UiState.Success(plan)
        }
    }

    /** Warped-space table height estimated from the confirmed quad aspect. */
    private fun warpHeight(table: TableModel): Float {
        val tl = table.corners[0]
        val tr = table.corners[1]
        val bl = table.corners[3]
        val widthTop = kotlin.math.hypot(
            (tr.first - tl.first).toDouble(),
            (tr.second - tl.second).toDouble(),
        ).toFloat()
        val heightLeft = kotlin.math.hypot(
            (bl.first - tl.first).toDouble(),
            (bl.second - tl.second).toDouble(),
        ).toFloat()
        return if (widthTop > 0f) 800f * (heightLeft / widthTop) else 400f
    }
}
