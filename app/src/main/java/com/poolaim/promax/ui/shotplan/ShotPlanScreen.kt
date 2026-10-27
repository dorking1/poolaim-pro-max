package com.poolaim.promax.ui.shotplan

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.poolaim.promax.domain.model.BallModel
import com.poolaim.promax.domain.model.TableModel
import com.poolaim.promax.domain.physics.Difficulty
import com.poolaim.promax.domain.physics.ShotCandidate

private val AIM_COLOR = Color(0xFFFFEB3B)
private val GHOST_COLOR = Color(0xFF4CAF50)

/**
 * Phase E: interactive AimBoard. The user can tap a shot in the ranked
 * list to switch the aim-line overlay on the image. Best shot is shown
 * by default.
 */
@Composable
fun ShotPlanScreen(
    viewModel: ShotPlanViewModel,
    imageUri: String,
    table: TableModel,
    allBalls: List<BallModel>,
    onFinished: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var selectedShot by remember { mutableStateOf<ShotCandidate?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val s = state) {
            is ShotPlanViewModel.UiState.Idle -> Unit
            is ShotPlanViewModel.UiState.Computing -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            is ShotPlanViewModel.UiState.Error -> {
                Text(
                    text = s.reason,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }

            is ShotPlanViewModel.UiState.Success -> {
                val plan = s.plan
                val best = selectedShot ?: plan.bestShot

                if (best != null) {
                    AimOverlay(
                        imageUri = Uri.parse(imageUri),
                        table = table,
                        shot = best,
                        cueBall = plan.cueBall,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )

                    Text(
                        text = "زاوية القطع ${"%.1f".format(best.cutAngleDeg)}° — " +
                            "الصعوبة: ${difficultyLabel(best.difficulty)}" +
                            if (best.blockersOnCuePath.size + best.blockersOnTargetPath.size > 0) {
                                " — ⚠ مسدودة جزئيًا"
                            } else "",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxWidth(),
                    ) {
                        items(plan.candidates.take(8), key = { it.targetBall.centerX.toLong() * 1000 + it.pocket.x.toLong() }) { shot ->
                            ShotRow(
                                shot = shot,
                                isSelected = shot == best,
                                onClick = { selectedShot = shot },
                            )
                        }
                    }

                    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                        Text("تم — إنهاء التحليل")
                    }
                } else {
                    Text(
                        text = "لا توجد ضربة ممكنة بهذه التشكيلة — جرّب إعادة الكشف",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShotRow(
    shot: ShotCandidate,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "كرة (${shot.targetBall.centerX.toInt()}, ${shot.targetBall.centerY.toInt()}) → جيب (${shot.pocket.x.toInt()}, ${shot.pocket.y.toInt()})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val blocked = shot.blockersOnCuePath.size + shot.blockersOnTargetPath.size
                if (blocked > 0) {
                    Text(
                        text = "مسدودة بـ $blocked كرة",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            AssistChip(
                onClick = onClick,
                label = { Text(difficultyLabel(shot.difficulty)) },
            )
        }
    }
}

private fun difficultyLabel(d: Difficulty): String = when (d) {
    Difficulty.EASY -> "سهلة"
    Difficulty.MEDIUM -> "متوسطة"
    Difficulty.HARD -> "صعبة"
    Difficulty.VERY_HARD -> "صعبة جدًا"
}

/**
 * AimBoard overlay: cue -> ghost line, ghost marker, target -> pocket
 * line, pocket ring. Coordinates re-projected from warped space via the
 * same bilinear mapping used by the ball overlay (must stay in sync with
 * BallDetector's WARP_WIDTH = 800).
 */
@Composable
private fun AimOverlay(
    imageUri: Uri,
    table: TableModel,
    shot: ShotCandidate,
    cueBall: BallModel,
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
                    intrinsic = IntSize(d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val image = intrinsic ?: return@Canvas
            if (table.corners.size < 4) return@Canvas

            val scale = minOf(
                canvas.width.toFloat() / image.width,
                canvas.height.toFloat() / image.height,
            )
            val imgW = image.width * scale
            val imgH = image.height * scale
            val offX = (canvas.width - imgW) / 2f
            val offY = (canvas.height - imgH) / 2f

            fun toScreen(nx: Float, ny: Float) = Offset(offX + nx * imgW, offY + ny * imgH)

            val tl = toScreen(table.corners[0].first / image.width, table.corners[0].second / image.height)
            val tr = toScreen(table.corners[1].first / image.width, table.corners[1].second / image.height)
            val br = toScreen(table.corners[2].first / image.width, table.corners[2].second / image.height)
            val bl = toScreen(table.corners[3].first / image.width, table.corners[3].second / image.height)

            val warpW = 800f
            val topLen = kotlin.math.hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble()).toFloat()
            val leftLen = kotlin.math.hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble()).toFloat()
            val warpH = if (topLen > 0f) warpW * (leftLen / topLen) else 400f

            fun project(wx: Float, wy: Float): Offset {
                val u = wx / warpW
                val v = wy / warpH
                val topX = tl.x + (tr.x - tl.x) * u
                val topY = tl.y + (tr.y - tl.y) * u
                val botX = bl.x + (br.x - bl.x) * u
                val botY = bl.y + (br.y - bl.y) * u
                return Offset(topX + (botX - topX) * v, topY + (botY - topY) * v)
            }

            val cueP = project(cueBall.centerX, cueBall.centerY)
            val ghostP = project(shot.ghostPoint.x, shot.ghostPoint.y)
            val targetP = project(shot.targetBall.centerX, shot.targetBall.centerY)
            val pocketP = project(shot.pocket.x, shot.pocket.y)

            val cuePath = Path().apply {
                moveTo(cueP.x, cueP.y)
                lineTo(ghostP.x, ghostP.y)
            }
            drawPath(cuePath, color = AIM_COLOR, style = Stroke(width = 5f))

            drawCircle(color = GHOST_COLOR, radius = 10f, center = ghostP, style = Stroke(width = 4f))

            val targetPath = Path().apply {
                moveTo(targetP.x, targetP.y)
                lineTo(pocketP.x, pocketP.y)
            }
            drawPath(targetPath, color = AIM_COLOR, style = Stroke(width = 5f))

            drawCircle(color = GHOST_COLOR, radius = 14f, center = pocketP, style = Stroke(width = 4f))
        }
    }
}
