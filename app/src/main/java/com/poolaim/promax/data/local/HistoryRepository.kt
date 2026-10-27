package com.poolaim.promax.data.local

import android.content.Context
import com.poolaim.promax.domain.model.BallModel
import com.poolaim.promax.domain.model.TableModel
import com.poolaim.promax.domain.physics.Difficulty
import com.poolaim.promax.domain.physics.ShotCandidate
import kotlinx.coroutines.flow.Flow

/**
 * Repository over the Room history (Phase F).
 */
class HistoryRepository(context: Context) {

    private val dao = AppDatabase.get(context).analysisSessionDao()

    fun recent(limit: Int = 50): Flow<List<AnalysisSessionEntity>> = dao.recent(limit)

    suspend fun byId(id: Long): AnalysisSessionEntity? = dao.byId(id)

    suspend fun save(
        imageUri: String,
        playerGroup: String,
        balls: List<BallModel>,
        table: TableModel,
        bestShot: ShotCandidate?,
    ): Long = dao.insert(
        AnalysisSessionEntity(
            timestamp = System.currentTimeMillis(),
            imageUri = imageUri,
            playerGroup = playerGroup,
            ballsCsv = balls.toCsv(),
            cornersCsv = table.corners.joinToString(";") { "${it.first},${it.second}" },
            tableConfidence = table.confidence,
            tableManuallyCalibrated = table.manuallyCalibrated,
            bestShotTarget = bestShot?.let { "${it.targetBall.centerX},${it.targetBall.centerY}" },
            bestShotPocket = bestShot?.let { "${it.pocket.x},${it.pocket.y}" },
            bestShotCutAngle = bestShot?.cutAngleDeg,
            bestShotDifficulty = bestShot?.difficulty?.name,
        ),
    )

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clearAll() = dao.clearAll()

    /** Reconstructs the table model (bounds recomputed from corners). */
    fun toTableModel(entity: AnalysisSessionEntity): TableModel {
        val corners = entity.cornersCsv.split(";").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size < 2) return@mapNotNull null
            runCatching { parts[0].toFloat() to parts[1].toFloat() }.getOrNull()
        }
        return TableModel(
            bounds = android.graphics.RectF(
                corners.minOf { it.first },
                corners.minOf { it.second },
                corners.maxOf { it.first },
                corners.maxOf { it.second },
            ),
            corners = corners,
            confidence = entity.tableConfidence,
            manuallyCalibrated = entity.tableManuallyCalibrated,
        )
    }

    fun toBalls(entity: AnalysisSessionEntity): List<BallModel> = parseBallsCsv(entity.ballsCsv)

    fun difficultyLabel(entity: AnalysisSessionEntity): String? =
        entity.bestShotDifficulty?.let {
            runCatching { Difficulty.valueOf(it).name }.getOrNull()
        }
}
