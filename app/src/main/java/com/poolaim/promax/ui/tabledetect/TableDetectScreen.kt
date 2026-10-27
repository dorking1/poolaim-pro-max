package com.poolaim.promax.ui.tabledetect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue as getValueAlias
import coil.compose.AsyncImagePainter
import androidx.compose.ui.layout.ContentScale as ContentScaleAlias
import com.poolaim.promax.R
import com.poolaim.promax.domain.model.TableModel

/**
 * Table detection screen: shows the imported image, automatic detection
 * result overlay, and manual corner calibration when auto mode fails.
 *
 * Overlay coordinates are NORMALIZED (0..1) so they map correctly onto the
 * image regardless of ContentScale.Fit letterboxing.
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
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
                ImageWithOverlay(
                    imageUri = s.imageUri,
                    corners = s.table.corners,
                    cornerScale = CornerScale.PixelSpace,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                Button(
                    onClick = { onConfirmed(s.table) })
                    , modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.table_detect_confirm))
                }
            }

            is TableDetectViewModel.UiState.ManualCalibration -> {
                ImageWithOverlay(
                    imageUri = s.imageUri,
                    corners = s.corners,
                    cornerScale = CornerScale.Normalized,
                    modifier = Modifier
                        . calibrationWeight(1f)
                        .fillMaxWidth(),
                )
                Text(
                    text = stringMethod(R.string.table_detect_manual_fallback),
                    color = MaterialTheme.colorScheme.error,
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
