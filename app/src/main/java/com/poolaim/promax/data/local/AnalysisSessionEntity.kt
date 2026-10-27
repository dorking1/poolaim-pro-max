package com.poolaim.promax.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A persisted analysis session (Phase F history).
 * Ball coordinates are stored compactly as a serialized string of
 * "x,y,r,type" entries (JSON kept out of scope; compact CSV of floats).
 */
@Entity(tableName = "analysis_sessions")
data class AnalysisSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO-8601 timestamp of the analysis. */
    val timestamp: Long,
    /** Persisted (takePersistableUriPermission) content URI of the image. */
    val imageUri: String,
    val playerGroup: String,
    /** Serialized balls: "x,y,r,type;x,y,r,type;..." */
    val ballsCsv: String,
    /** Serialized table corners: "x,y;x,y;x,y;x,y". */
    val cornersCsv: String,
    val tableConfidence: Float,
    val tableManuallyCalibrated: Boolean,
    /** Best-shot summary fields. */
    val bestShotTarget: String?,
    val bestShotPocket: String?,
    val bestShotCutAngle: Float?,
    val bestShotDifficulty: String?,
)

/** Serializes a ball list into the compact CSV format. */
fun List<com.poolaim.promax.domain.model.BallModel>.toCsv(): String =
    joinToString(";") { "${it.centerX},${it.centerY},${it.radius},${it.type.name}" }

/** Parses the compact CSV back into models (best-effort; skips bad rows). */
fun parseBallsCsv(csv: String): List<com.poolaim.promax.domain.model.BallModel> =
    csv.split(";").mapNotNull { entry ->
        val parts = entry.split(",")
        if (parts.size < 4) return@mapNotNull null
        runCatching {
            com.poolaim.promax.domain.model.BallModel(
                number = null,
                centerX = parts[0].toFloat(),
                centerY = parts[1].toFloat(),
                radius = parts[2].toFloat(),
                type = com.poolaim.promax.domain.model.BallModel.Type.valueOf(parts[3]),
            )
        }.getOrNull()
    }
