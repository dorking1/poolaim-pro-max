package com.poolaim.promax.ui.tabledetect

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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

private val ACCENT = Color(0xFF4CAF50)

@Composable
fun TableDetectScreen(
    viewModel: TableDetectViewModel,
    onConfirmed: (TableModel) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(text = stringResource(R.string.table_detect_searching))
                    }
                }
            }

            is TableDetectViewModel.UiState.Success -> {
                AutoOverlay(
                    imageUri = s.imageUri,
                    corners = s.table.corners,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Button(
                    onClick = { onConfirmed(s.table) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.table_detect_confirm)) }
            }

            is TableDetectViewModel.UiState.ManualCalibration -> {
                ManualOverlay(
                    imageUri = s.imageUri,
                    corners = s.corners,
                    onCornerDrag = viewModel::updateManualCorner,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.table_detect_manual_fallback),
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = { viewModel.confirmManual()?.let(onConfirmed) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.table_detect_confirm)) }
            }
        }
    }
}

@Composable
private fun AutoOverlay(
    imageUri: Uri,
    corners: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
) {
    var intrinsic by remember { mutableStateOf<IntSize?>(null) }
    var canvas by remember { mutableStateOf(IntSize(1, 1)) }

    Box(modifier = modifier.onSizeChanged { canvas = it }) {
        LoadedImage(imageUri) { intrinsic = it }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val image = intrinsic ?: return@Canvas
            if (corners.size < 4) return@Canvas
            val rect = fitRect(canvas.width.toFloat(), canvas.height.toFloat(), image)
            val norm = corners.map { (x, y) ->
                (x / image.width).coerceIn(0f, 1f) to (y / image.height).coerceIn(0f, 1f)
            }
            drawQuad(norm, rect)
        }
    }
}

@Composable
private fun ManualOverlay(
    imageUri: Uri,
    corners: List<Pair<Float, Float>>,
    onCornerDrag: (Int, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var intrinsic by remember { mutableStateOf<IntSize?>(null) }
    var canvas by remember { mutableStateOf(IntSize(1, 1)) }
    var active by remember { mutableStateOf(-1) }

    Box(modifier = modifier.onSizeChanged { canvas = it }) {
        LoadedImage(imageUri) { intrinsic = it }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(intrinsic) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val image = intrinsic ?: return@detectDragGestures
                            val rect = fitRect(canvas.width.toFloat(), canvas.height.toFloat(), image)
                            active = corners.indices.minByOrNull { i ->
                                val cx = rect.left + corners[i].first * rect.width
                                val cy = rect.top + corners[i].second * rect.height
                                (offset - Offset(cx, cy)).getDistanceSquared()
                            } ?: -1
                        },
                        onDrag = { change, amount ->
                            val image = intrinsic ?: return@detectDragGestures
                            if (active >= 0) {
                                val rect = fitRect(canvas.width.toFloat(), canvas.height.toFloat(), image)
                                val cx = rect.left + corners[active].first * rect.width
                                val cy = rect.top + corners[active].second * rect.height
                                val nx = ((cx + amount.x - rect.left) / rect.width).coerceIn(0f, 1f)
                                val ny = ((cy + amount.y - rect.top) / rect.height).coerceIn(0f, 1f)
                                onCornerDrag(active, nx, ny)
                                change.consume()
                            }
                        },
                        onDragEnd = { active = -1 },
                        onDragCancel = { active = -1 },
                    )
                },
        ) {
            if (corners.size < 4) return@Canvas
            val image = intrinsic ?: return@Canvas
            val rect = fitRect(canvas.width.toFloat(), canvas.height.toFloat(), image)
            drawQuad(corners, rect)
            corners.forEach { (nx, ny) ->
                val c = Offset(rect.left + nx * rect.width, rect.top + ny * rect.height)
                drawCircle(color = ACCENT, radius = 18f, center = c)
                drawCircle(color = Color.White, radius = 8f, center = c)
            }
        }
    }
}

@Composable
private fun LoadedImage(imageUri: Uri, onIntrinsic: (IntSize) -> Unit) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(imageUri).build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        onState = { st ->
            val d = (st as? AsyncImagePainter.State.Success)?.result?.drawable
            if (d != null) {
                onIntrinsic(
                    IntSize(
                        d.intrinsicWidth.coerceAtLeast(1),
                        d.intrinsicHeight.coerceAtLeast(1),
                    ),
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun fitRect(w: Float, h: Float, image: IntSize): Rect {
    val scale = minOf(w / image.width, h / image.height)
    val drawW = image.width * scale
    val drawH = image.height * scale
    return Rect(
        left = (w - drawW) / 2f,
        top = (h - drawH) / 2f,
        right = (w - drawW) / 2f + drawW,
        bottom = (h - drawH) / 2f + drawH,
    )
}

private fun DrawScope.drawQuad(corners: List<Pair<Float, Float>>, rect: Rect) {
    if (corners.size < 4) return
    val path = Path().apply {
        moveTo(rect.left + corners[0].first * rect.width, rect.top + corners[0].second * rect.height)
        lineTo(rect.left + corners[1].first * rect.width, rect.top + corners[1].second * rect.height)
        lineTo(rect.left + corners[2].first * rect.width, rect.top + corners[2].second * rect.height)
        lineTo(rect.left + corners[3].first * rect.width, rect.top + corners[3].second * rect.height)
        close()
    }
    drawPath(path, color = ACCENT, style = Stroke(width = 6f))
}
