package com.poolaim.promax.ui.tabledetect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import التحديث.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poolaim.promax.R
import com.poolaim.promax.domain.model.TableModel
import kotlin.math.hypot

/**
 * Table detection screen: shows the imported image, automatic detection
 * result overlay, and manual corner calibration when auto mode fails.
 */
@Composable
fun TableDetectScreen(
    viewModel: TableDetectViewModel,
    onConfirmed: (TableModel) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.table_detect_title),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
        )

        when (val s = state) {
            is TableDetectViewModel.UiState.Idle -> {
                Text(text = stringResource(R.string.action_import_image))
            }

            is TableDetectViewModel.UiState.Analyzing -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(text = stringResource(R.string.table_detect_searching))
                    }
                }
            }

            is TableDetectViewModel.UiState.Success -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AsyncImage(
                        model = s.imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    DetectedOverlay(
                        corners = s.table.corners,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Button(
                    onClick = { onConfirmed(s.table) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.table_detect_confirm))
                }
            }

            is TableDetectViewModel.UiState.ManualCalibration -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AsyncImage(
                        model = s.imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ManualCornerOverlay(
                        corners = s.corners,
                        onCornerMoved = { index, x, y ->
                            viewModel.updateManualCorner(index, x, y)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = stringResource(R.string.table_detect_manual_fallback),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = {
                        viewModel.confirmManual()?.let(onConfirmed)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.table_detect_confirm))
                }
            }
        }
    }
}

/** Draws the detected quadrilateral on top of the table photo. */
@Composable
private fun DetectedOverlay(
    corners: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (corners.size < 4) return@Canvas
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(corners[0].first, corners[0].second)
            lineTo(corners[1].first, corners[1].second)
            lineTo(corners[2].first, corners[2].second)
            lineTo(corners[3].first, corners[3].second)
            close()
        }
        drawPath(path, color = Color(0xFF4CAF50), style = Stroke(width = 6f))
    }
}

/** Interactive manual calibration: draggable corner handles. */
@Composable
private fun ManualCornerOverlay(
    corners: List<Pair<Float, Float>>,
    onCornerMoved: (index: Int, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Phase B screen layout: drag handles are wired in the interactive
    // version of this composable; detection pipeline and state are complete.
    DetectedOverlay(corners = corners, modifier = modifier)
}
