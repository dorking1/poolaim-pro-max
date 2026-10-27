package com.poolaim.promax.ui.tabledetect

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poolaim.promax.data.vision.TableBoundaryDetector
import com.poolaim.promax.domain.model.TableModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for table boundary detection: runs automatic OpenCV detection
 * and exposes results; the user can also provide manual calibration.
 */
class TableDetectViewModel(
    private val detector: TableBoundaryDetector,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Analyzing(val imageUri: Uri) : UiState
        data class Success(val imageUri: Uri, val table: TableModel) : UiState
        data class ManualCalibration(val imageUri: Uri, val corners: List<Pair<Float, Float>>) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    /** Last table confirmed by the user (auto or manual) — for downstream phases. */
    private val _confirmedTable = MutableStateFlow<TableModel?>(null)

    /**
     * Returns the currently confirmed table geometry, or the table from the
     * latest Success state if not yet explicitly confirmed. Null when no
     * table is available yet.
     */
    fun currentTable(): TableModel? {
        _confirmedTable.value?.let { return it }
        return (state.value as? UiState.Success)?.table
    }

    fun analyze(uri: Uri) {
        _state.value = UiState.Analyzing(uri)

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { detector.detect(uri) }
                    .getOrElse { TableBoundaryDetector.DetectionResult.Failure }
            }

            _state.value = when (result) {
                is TableBoundaryDetector.DetectionResult.Success ->
                    UiState.Success(uri, result.table)
                TableBoundaryDetector.DetectionResult.Failure -> {
                    // Fallback: user drags the four corners manually.
                    UiState.ManualCalibration(
                        uri = uri,
                        corners = defaultCorners(),
                    )
                }
            }
        }
    }

    /** Marks the given table as confirmed (called on user confirmation). */
    fun confirmTable(table: TableModel) {
        _confirmedTable.value = table
    }

    fun updateManualCorner(index: Int, x: Float, y: Float) {
        val current = _state.value as? UiState.ManualCalibration ?: return
        val newCorners = current.corners.toMutableList().apply {
            set(index, Pair(x, y))
        }
        _state.value = current.copy(corners = newCorners)
    }

    fun confirmManual(): TableModel? {
        val current = _state.value as? UiState.ManualCalibration ?: return null
        val bounds = android.graphics.RectF(
            current.corners.minOf { it.first },
            current.corners.minOf { it.second },
            current.corners.maxOf { it.first },
            current.corners.maxOf { it.second },
        )
        val table = TableModel(
            bounds = bounds,
            corners = current.corners,
            confidence = 1f,           // user-confirmed geometry is authoritative
            manuallyCalibrated = true,
        )
        _confirmedTable.value = table
        return table
    }

    /** Slightly inset corners as a sensible manual starting point. */
    private fun defaultCorners(): List<Pair<Float, Float>> = listOf(
        Pair(0.15f, 0.15f),
        Pair(0.85f, 0.15f),
        Pair(0.85f, 0.85f),
        Pair(0.15f, 0.85f),
    )
}
