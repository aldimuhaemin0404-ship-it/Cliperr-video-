package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
    @Query("SELECT * FROM clip_projects ORDER BY createdAtMs DESC")
    fun observeAllProjects(): Flow<List<ClipProjectEntity>>

    @Query("SELECT * FROM clip_projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): ClipProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ClipProjectEntity): Long

    @Update
    suspend fun updateProject(project: ClipProjectEntity)

    @Query("DELETE FROM clip_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    @Query("SELECT * FROM clip_segments WHERE projectId = :projectId ORDER BY viralScore DESC")
    fun observeSegmentsForProject(projectId: Long): Flow<List<ClipSegmentEntity>>

    @Query("SELECT * FROM clip_segments WHERE projectId = :projectId ORDER BY id ASC")
    suspend fun getSegmentsForProject(projectId: Long): List<ClipSegmentEntity>

    @Query("SELECT * FROM clip_segments WHERE id = :segmentId")
    suspend fun getSegmentById(segmentId: Long): ClipSegmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<ClipSegmentEntity>)

    @Update
    suspend fun updateSegment(segment: ClipSegmentEntity)

    @Query("SELECT * FROM exported_clips ORDER BY exportedAtMs DESC")
    fun observeExportedClips(): Flow<List<ExportedClipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportedClip(exportedClip: ExportedClipEntity): Long

    @Query("DELETE FROM exported_clips WHERE id = :exportId")
    suspend fun deleteExportedClipById(exportId: Long)
}
