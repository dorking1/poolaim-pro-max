package com.poolaim.promax.ui.balldetect

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.poolaim.promax.domain.model.BallModel
import com.poolaim.promax.domain.model.TableModel

private val PLAYER_COLOR = Color(0xFF4CAF50)   // green outline: player's group
private val OPPONENT_COLOR = Color(0xFFEF5350) // red outline: opponent
private val CUE_COLOR = Color(0xFFFFFFFF)     // white outline: cue ball
private val EIGHT_COLOR = Color(0xFF9E9E9E)    // grey outline: 8-ball

/**
 * Phase C screen: shows detected balls overlaid on the original photo.
 * Ball coordinates are in the WARPED canonical space (WARP_WIDTH x h),
 * so the overlay re-projects them onto the table quad drawn over the
 * fitted image for display purposes.
 */
@Composable
fun BallDetectScreen(
    viewModel: BallDetectViewModel,
    onConfirmed: () -> Unit,
    onRetry: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val s = state) {
            is BallDetectViewModel.UiState.Idle -> Unit

            is BallDetectViewModel.UiState.Detecting -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(text = "جارٍ كشف الكرات…")
                    }
                }
            }

            is BallDetectViewModel.UiState.Error -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "فشل كشف الكرات — أعد المحاولة أو استخدم صورة أوضح",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = onRetry) { Text("إعادة المحاولة") }
                    }
                }
            }

            is BallDetectViewModel.UiState.Success -> {
                DetectedBallsOverlay(
                    imageUri = s.imageUri,
                    table = s.table,
                    playerBalls = s.playerBalls,
                    opponentBalls = s.opponentBalls,
                    cueBall = s.cueBall,
                    eightBall = s.eightBall,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "كراتك: ${s.playerBalls.size}",
                        color = PLAYER_COLOR,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "كرات الخصم: ${s.opponentBalls.size}",
                        color = OPPONENT_COLOR,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Button(
                    onClick = onConfirmed,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("تأكيد الكرات") }
            }
        }
    }
}

/**
 * Projects warped-space ball coordinates back onto the table quad in the
 * displayed (fitted) image and draws color-coded circles.
 */
@Composable
private fun DetectedBallsOverlay(
    imageUri: android.net.Uri,
    table: TableModel,
    playerBalls: List<BallModel>,
    opponentBalls: List<BallModel>,
    cueBall: BallModel?,
    eightBall: BallModel?,
    modifier: Modifier = Modifier,
) {
    var intrinsic by remember { mutableStateOf<IntSize?>(null) }
    var canvas by remember { mutableStateOf(IntSize(1, 1)) }

    Box(modifier = modifier.onSizeChanged { canvas = it }) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(imageUri).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onState = { st ->
                val d = (st as? AsyncImagePainter.State.Success)?.result?.drawable
                if (d != null) {
                    intrinsic = IntSize(
                        d.intrinsicWidth.coerceAtLeast(1),
                        d.intrinsicHeight.coerceAtLeast(1),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val image = intrinsic ?: return@Canvas
            if (table.corners.size < 4) return@Canvas

            // Fitted rect of the image inside the canvas (ContentScale.Fit).
            val scale = minOf(
                canvas.width.toFloat() / image.width,
                canvas.height.toFloat() / image.height,
            )
            val imgW = image.width * scale
            val imgH = image.height * scale
            val offX = (canvas.width - imgW) / 2f
            val offY = (canvas.height - imgH) / 2f

            fun toScreen(nx: Float, ny: Float) = Offset(offX + nx * imgW, offY + ny * imgH)

            // Table corners normalized in image space.
            val tl = toScreen(
                table.corners[0].first / image.width,
                table.corners[0].second / image.height,
            )
            val tr = toScreen(
                table.corners[1].first / image.width,
                table.corners[1].second / image.height,
            )
            val br = toScreen(
                table.corners[2].first / image.width,
                table.corners[2].second / image.height,
            )
            val bl = toScreen(
                table.corners[3].first / image.width,
                table.corners[3].second / image.height,
            )

            // Warp canonical space dimensions (must match BallDetector).
            val warpW = 800f
            val warpH = (warpW * hypotValue(bl - tl, tr - tl)).coerceAtLeast(1f)

            // Bilinear projection of a warped-space point onto the screen quad.
            fun project(wx: Float, wy: Float): Offset {
                val u = wx / warpW
                val v = wy / warpH
                // Interpolate along the quad edges (perspective approximated).
                val topX = tl.x + (tr.x - tl.x) * u
                val topY = tl.y + (tr.y - tl.y) * u
                val botX = bl.x + (br.x - bl.x) * u
                val botY = bl.y + (br.y - bl.y) * u
                return Offset(
                    topX + (botX - topX) * v,
                    topY + (botY - topY) * v,
                )
            }

            fun ballRadius(b: BallModel): Float {
                val p = project(b.centerX, b.centerY)
                val pEdge = project(b.centerX + b.radius, b.centerY)
                return (pEdge - p).getDistance()
            }

            fun drawBall(b: BallModel, color: Color) {
                val c = project(b.centerX, b.centerY)
                drawCircle(color = color, radius = ballRadius(b) + 2f, center = c, style = Stroke(width = 4f))
            }

            playerBalls.forEach { drawBall(it, PLAYER_COLOR) }
            opponentBalls.forEach { drawBall(it, OPPONENT_COLOR) }
            cueBall?.let { drawBall(it, CUE_COLOR) }
            eightBall?.let { drawBall(it, EIGHT_COLOR) }
        }
    }
}

private fun hypotValue(a: androidx.compose.ui.geometry.Offset, b: androidx.compose.ui.geometry.Offset): Float {
    // Edge length difference; we approximate aspect from two adjacent edges.
    val topLen = (b - a).getDistance()
    return topLen
}

private operator fun Offset.minus(other: Offset): Offset = Offset(this.x - other.x, this.y - other.y)
