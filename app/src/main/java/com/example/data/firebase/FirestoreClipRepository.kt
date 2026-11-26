package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSegment
import com.example.domain.model.ExportedClip
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreClipRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
    }

    fun projectDocId(localProjectId: Long): String = "proj_$localProjectId"
    fun segmentDocId(localSegmentId: Long): String = "seg_$localSegmentId"
    fun exportDocId(localExportId: Long): String = "exp_$localExportId"

    private suspend fun commitChunkedBatches(
        operations: List<(com.google.firebase.firestore.WriteBatch) -> Unit>
    ) {
        if (operations.isEmpty()) return
        operations.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { op -> op(batch) }
            batch.commit().await()
        }
    }

    suspend fun syncUserProfile(user: FirebaseUser): Result<Unit> {
        val docPath = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "User ID mismatch during profile sync." }
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val safeName = (user.displayName ?: user.email?.substringBefore('@') ?: "Kreator Aldi")
                .trim()
                .ifEmpty { "Kreator Aldi" }
                .take(120)

            if (!snapshot.exists()) {
                val createMap = mapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to user.email?.take(160),
                    "photoUrl" to user.photoUrl?.toString()?.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }.mapValues { it.value!! }
                docRef.set(createMap).await()
            } else {
                val updateMap = mapOf<String, Any?>(
                    "displayName" to safeName,
                    "email" to user.email?.take(160),
                    "photoUrl" to user.photoUrl?.toString()?.take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }.mapValues { it.value!! }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeCloudProjects(userId: String): Flow<List<CloudClipProject>> {
        val path = "users/$userId/projects"
        return db.collection("users")
            .document(userId)
            .collection("projects")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudClipProject::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeCloudSegments(userId: String): Flow<List<CloudClipSegment>> {
        val path = "users/$userId/segments"
        return db.collection("users")
            .document(userId)
            .collection("segments")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudClipSegment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeCloudExports(userId: String): Flow<List<CloudExportedClip>> {
        val path = "users/$userId/exports"
        return db.collection("users")
            .document(userId)
            .collection("exports")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudExportedClip::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun syncProjectWithSegments(
        project: ClipProject,
        segments: List<ClipSegment>
    ): Result<Unit> {
        var currentPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects"
        return try {
            val uid = requireUserId()
            val projId = projectDocId(project.id)
            val userDoc = db.collection("users").document(uid)
            val projRef = userDoc.collection("projects").document(projId)
            currentPath = projRef.path

            val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()
            val projExists = projRef.get().await().exists()
            if (projExists) {
                ops += { b -> b.update(projRef, project.toFirestoreUpdateMap()) }
            } else {
                ops += { b -> b.set(projRef, project.toFirestoreCreateMap(uid)) }
            }

            segments.forEach { seg ->
                val segRef = userDoc.collection("segments").document(segmentDocId(seg.id))
                ops += { b -> b.set(segRef, seg.toFirestoreCreateMap(userId = uid, projectCloudId = projId)) }
            }
            commitChunkedBatches(ops)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun upsertProject(project: ClipProject): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects"
        return try {
            val uid = requireUserId()
            val projRef = db.collection("users").document(uid)
                .collection("projects").document(projectDocId(project.id))
            docPath = projRef.path
            if (projRef.get().await().exists()) {
                projRef.update(project.toFirestoreUpdateMap()).await()
            } else {
                projRef.set(project.toFirestoreCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertSegment(segment: ClipSegment, fallbackProject: ClipProject? = null): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/segments"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)
            val projId = projectDocId(segment.projectId)
            val projRef = userDoc.collection("projects").document(projId)
            val segRef = userDoc.collection("segments").document(segmentDocId(segment.id))
            docPath = segRef.path

            if (segRef.get().await().exists()) {
                segRef.update(segment.toFirestoreUpdateMap()).await()
            } else {
                val batch = db.batch()
                if (!projRef.get().await().exists() && fallbackProject != null) {
                    batch.set(projRef, fallbackProject.toFirestoreCreateMap(uid))
                }
                batch.set(segRef, segment.toFirestoreCreateMap(uid, projId))
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertExportedClip(
        exportedClip: ExportedClip,
        fallbackProject: ClipProject? = null
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/exports"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)
            val projId = projectDocId(exportedClip.projectId)
            val projRef = userDoc.collection("projects").document(projId)
            val expRef = userDoc.collection("exports").document(exportDocId(exportedClip.id))
            docPath = expRef.path

            if (expRef.get().await().exists()) {
                expRef.update(exportedClip.toFirestoreUpdateMap()).await()
            } else {
                val batch = db.batch()
                if (!projRef.get().await().exists() && fallbackProject != null) {
                    batch.set(projRef, fallbackProject.toFirestoreCreateMap(uid))
                }
                batch.set(expRef, exportedClip.toFirestoreCreateMap(uid, projId))
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProjectAndChildren(localProjectId: Long): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)
            val projId = projectDocId(localProjectId)
            val projRef = userDoc.collection("projects").document(projId)
            docPath = projRef.path

            val segSnapshots = userDoc.collection("segments")
                .whereEqualTo("projectCloudId", projId)
                .get()
                .await()
            val expSnapshots = userDoc.collection("exports")
                .whereEqualTo("projectCloudId", projId)
                .get()
                .await()

            val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()
            segSnapshots.documents.forEach { doc -> ops += { b -> b.delete(doc.reference) } }
            expSnapshots.documents.forEach { doc -> ops += { b -> b.delete(doc.reference) } }
            ops += { b -> b.delete(projRef) }
            commitChunkedBatches(ops)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteExportedClip(localExportId: Long): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/exports"
        return try {
            val uid = requireUserId()
            val expRef = db.collection("users").document(uid)
                .collection("exports").document(exportDocId(localExportId))
            docPath = expRef.path
            expRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
