package com.example.data.local

import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportedClip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ClipProjectRepository(
    private val clipDao: ClipDao
) {
    val allProjects: Flow<List<ClipProject>> = clipDao.observeAllProjects()
        .map { list -> list.map { it.toDomain() } }

    val allExportedClips: Flow<List<ExportedClip>> = clipDao.observeExportedClips()
        .map { list -> list.map { it.toDomain() } }

    fun observeSegments(projectId: Long): Flow<List<ClipSegment>> =
        clipDao.observeSegmentsForProject(projectId)
            .map { list -> list.map { it.toDomain() } }

    suspend fun saveProjectWithSegments(
        project: ClipProject,
        segmentsProvider: (projectId: Long) -> List<ClipSegment>
    ): Long {
        val projectId = clipDao.insertProject(project.toEntity())
        val segments = segmentsProvider(projectId).map { it.copy(projectId = projectId).toEntity() }
        clipDao.insertSegments(segments)
        return projectId
    }

    suspend fun getProject(projectId: Long): ClipProject? =
        clipDao.getProjectById(projectId)?.toDomain()

    suspend fun getSegment(segmentId: Long): ClipSegment? =
        clipDao.getSegmentById(segmentId)?.toDomain()

    suspend fun getSegmentsForProject(projectId: Long): List<ClipSegment> =
        clipDao.getSegmentsForProject(projectId).map { it.toDomain() }

    suspend fun updateSegment(segment: ClipSegment) {
        clipDao.updateSegment(segment.toEntity())
    }

    suspend fun updateProject(project: ClipProject) {
        clipDao.updateProject(project.toEntity())
    }

    suspend fun deleteProject(projectId: Long) {
        clipDao.deleteProjectById(projectId)
    }

    suspend fun saveExportedClip(exportedClip: ExportedClip): Long =
        clipDao.insertExportedClip(exportedClip.toEntity())

    suspend fun deleteExportedClip(exportId: Long) {
        clipDao.deleteExportedClipById(exportId)
    }
}
