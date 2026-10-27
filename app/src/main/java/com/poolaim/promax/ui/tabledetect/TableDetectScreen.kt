package com.poolaim.promax.ui.tabledetect

import android.net.Uri
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.poolaim.promax.R
import com.poolaim.promax.domain.model.TableModel

/**
 * Coordinate space of the corner list passed to [ImageWithOverlay].
 * PIXEL: original-image pixels (OpenCV detector output).
 * NORMALIZED: 0..1 fractions of the displayed image (manual calibration).
 */
enum class CornerSpace { PIXEL, NORMALIZED }

/**
 * Table detection screen: shows the imported image, automatic detection
 * result overlay, and manual corner calibration when auto mode fails.
 *
 * The overlay maps corner coordinates into the ContentScale.Fit displayed
 * rect so the drawn quadrilateral lines up with the visible table even when
 * the image is letterboxed.
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
                    cornerSpace = CornerSpace.PIXEL,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                Button(
                    onClick = { onConfirmed(s.table) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.table_detect_confirm))
                }
            }

            is TableDetectViewModel.UiState.ManualCalibration -> {
                ImageWithOverlay(
                    imageUri = s.imageUri,
                    corners = s.corners,
                    cornerSpace = CornerSpace.NORMALIZED,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.table_detect_manual_fallback),
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

/**
 * Image with a quadrilateral overlay drawn over the fitted (ContentScale.Fit)
 * area. Pixel-space corners are normalized using the intrinsic image size
 * reported by the Coil painter state.
 */
@Composable
private fun ImageWithOverlay(
    imageUri: Uri,
    corners: List<Pair<Float, Float>>,
    cornerSpace: CornerSpace,
    modifier: Modifier = Modifier,
) {
    var intrinsicSize by remember { mutableStateOf<IntSize?>(null) }

    Box(modifier = modifier) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUri)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onState = { painterState ->
                val drawable = (painterState as? AsyncImagePainter.State.Success)?.result?.drawable
                intrinsicSize = drawable?.let {
                    IntSize(it.intrinsicWidth.coerceAtLeast(1), it.intrinsicHeight.coerceAtLeast(1))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val image = intrinsicSize ?: return@Canvas
            if (corners.size < 4) return@Canvas

            val normalized: List<Pair<Float, Float>> = when (cornerSpace) {
                CornerSpace.PIXEL -> corners.map { (x, y) ->
                    (x / image.width).coerceIn(0f, 1f) to (y / image.height).coerceIn(0f, 1f)
                }
                CornerSpace.NORMALIZED -> corners
            }

            // ContentScale.Fit: compute the displayed rect inside the canvas.
            val fitScale = minOf(
                size.width.toFloat() / image.width,
                size.height.toFloat() / image.height,
            )
            val drawWidth = image.width * fitScale
            val drawHeight = image.height * fitScale
            val offsetX = (size.width - drawWidth) / 2f
            val offsetY = (size.height - drawHeight) / 2f

            val points = normalized.map { (nx, ny) ->
                Offset(offsetX + nx * drawWidth, offsetY + ny * drawHeight)
            }

            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                lineTo(points[1].x, points[1].y)
                lineTo(points[2].x, points[2].y)
                lineTo(points[3].x, points[3].y)
                close()
            }
            drawPath(path, color = Color(0xFF4CAF50), style = Stroke(width = 6f))
        }
    }
}
