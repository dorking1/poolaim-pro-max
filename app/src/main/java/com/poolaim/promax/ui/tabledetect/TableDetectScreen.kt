package com.poolaim.promax.ui.tabledetect

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.input.pointer.pointerInput
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

/** Coordinate space of the corner list. */
enum class CornerSpace { PIXEL, NORMALIZED }

/**
 * Table detection screen with REAL manual calibration: draggable corner
 * handles when auto-detection fails, and fitted overlay for auto results.
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
                AutoDetectOverlay(
                    imageUri = s.imageUri,
                    corners = s.table.corners,
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
                ManualCalibrationOverlay(
                    imageUri = s.imageUri,
                    corners = s.corners,
                    onCornerDrag = viewModel::updateManualCorner,
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
 * Auto-detection result overlay: pixel-space corners mapped onto the
 * ContentScale.Fit displayed rect using the Coil painter intrinsic size.
 */
@Composable
private fun AutoDetectOverlay(
    imageUri: android.net.Uri,
    corners: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
) {
    var intrinsicSize by remember { mutableStateOf<IntSize?>(null) }

    Box(modifier = modifier) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(imageUri).build(),
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

            val normalized = corners.map { (x, y) ->
                (x / image.width).coerceIn(0f, 1f) to (y / image.height).coerceIn(0f, 1f)
            }
            val rect = fitRect(size.width, size.height, image)
            drawQuad(normalized, rect, Color(0xFF4CAF50))
        }
    }
}

/**
 * Manual calibration overlay: normalized corners (0..1 of the DISPLAYED
 * image rect) with draggable handles. Dragging maps screen pixels back to
 * normalized coordinates and reports via [onCornerDrag].
 */
@Composable
private fun ManualCalibrationOverlay(
    imageUri: android.net.Uri,
    corners: List<Pair<Float, Float>>,
    onCornerDrag: (index: Int, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var intrinsicSize by remember { mutableStateOf<IntSize?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var activeCorner by remember { mutableStateOf(-1) }

    Box(modifier = modifier) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(imageUri).build(),
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

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChangedCompat { canvasSize = it }
                .pointerInput(corners) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val rect = fitRect(
                                canvasSize.width.toFloat(),
                                canvasSize.height.toFloat(),
                                intrinsicSize ?: return@detectDragGestures IntSize(1, 1),
                            )
                            // Nearest corner within a generous touch radius.
                            activeCorner = corners.indices.minByOrNull { i ->
                                val cx = rect.left + corners[i].first * rect.width
                                val cy = rect.top + corners[i].second * rect.height
                                (offset - Offset(cx, cy)).getDistanceSquared()
                            } ?: -1
                        },
                        onDrag = { change, amount ->
                            if (activeCorner >= 0) {
                                val rect = fitRect(
                                    canvasSize.width.toFloat(),
                                    canvasSize.height.toFloat(),
                                    intrinsicSize ?: return@detectDragGestures,
                                )
                                val cx = rect.left + corners[activeCorner].first * rect.width
                                val cy = rect.top + corners[activeCorner].second * rect.height
                                val nx = ((cx + amount.x - rect.left) / rect.width).coerceIn(0f, 1f)
                                val ny = ((cy + amount.y - rect.top) / rect.height).coerceIn(0f, 1f)
                                onCornerDrag(activeCorner, nx, ny)
                                change.consume()
                            }
                        },
                        onDragEnd = { activeCorner = -1 },
                    )
                },
        ) {
            if (corners.size < 4) return@Canvas
            val rect = fitRect(size.width, size.height, intrinsicSize ?: return@Canvas)
            drawQuad(corners, rect, Color(0xFF4CAF50))

            // Draw handle circles.
            corners.forEach { (nx, ny) ->
                drawCircle(
                    color = Color(0xFF4CAF50),
                    radius = 18f,
                    center = Offset(rect.left + nx * rect.width, rect.top + ny * rect.height),
                )
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = Offset(rect.left + nx * rect.width, rect.top + ny * rect.height),
                )
            }
        }
    }
}

/** ContentScale.Fit rect of the image inside a (w x h) canvas. */
private fun fitRect(w: Float, h: Float, image: IntSize): androidx.compose.ui.geometry.Rect {
    val scale = minOf(w / image.width, h / image.height)
    val drawW = image.width * scale
    rectHeight@ return androidx.compose.ui.geometry.Rect(
        left = (w - drawW) / 2f,
        top = (h - image.height * scale) / 2f,
        right = (w - drawW) / 2f + drawW,
        bottom = (h - image.height * scale) / 2f + image.height * scale,
    )
}

private fun androidx.compose.foundation.CanvasModifier... placeholder
