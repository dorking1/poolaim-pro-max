package com.poolaim.promax.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** History DAO (Phase F). */
@Dao
interface AnalysisSessionDao {

    @Insert
    suspend fun insert(session: AnalysisSessionEntity): Long

    @Query("SELECT * FROM analysis_sessions ORDER BY timestamp DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<AnalysisSessionEntity>>

    @Query("SELECT * FROM analysis_sessions WHERE id = :id")
    suspend fun byId(id: Long): AnalysisSessionEntity?

    @Query("SELECT COUNT(*) FROM analysis_sessions")
    suspend fun count(): Int

    @Query("DELETE FROM analysis_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM analysis_sessions")
    suspend fun clearAll()
}
