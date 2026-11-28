package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class LocalGoogleSession(
    val displayName: String,
    val accountLabel: String,
    val noticeMessage: String? = null
)

object LocalGoogleSessionStore {
    private const val PREFS_NAME = "aldi_local_google_session"
    private const val KEY_ACTIVE = "session_active"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_ACCOUNT_LABEL = "account_label"
    private const val KEY_NOTICE = "notice_message"

    fun save(context: Context, session: LocalGoogleSession) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_DISPLAY_NAME, session.displayName)
                .putString(KEY_ACCOUNT_LABEL, session.accountLabel)
                .putString(KEY_NOTICE, session.noticeMessage)
                .apply()
        }
    }

    fun load(context: Context): LocalGoogleSession? {
        return runCatching {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_ACTIVE, false)) return@runCatching null
            val name = prefs.getString(KEY_DISPLAY_NAME, null)
                ?.takeIf { it.isNotBlank() }
                ?: "Kreator Studio (Lokal)"
            val label = prefs.getString(KEY_ACCOUNT_LABEL, null).orEmpty()
            val notice = prefs.getString(KEY_NOTICE, null)
            LocalGoogleSession(
                displayName = name,
                accountLabel = label,
                noticeMessage = notice
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private fun resolveWebClientId(context: Context): String? {
    val value = runCatching { context.getString(R.string.default_web_client_id).trim() }.getOrNull()
    return value?.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") }
}

private fun resolveFirebaseProjectId(): String? = runCatching {
    FirebaseApp.getInstance().options.projectId
}.getOrNull()

private fun buildLocalSessionFromGoogleCredential(
    googleCred: GoogleIdTokenCredential,
    detail: AuthFailureDetail
): LocalGoogleSession {
    val rawId = googleCred.id.trim()
    val cleanName = googleCred.displayName
        ?.takeIf { it.isNotBlank() }
        ?: rawId.substringBefore('@').takeIf { it.isNotBlank() }
        ?: "Kreator Google"
    return LocalGoogleSession(
        displayName = cleanName,
        accountLabel = rawId,
        noticeMessage = "Akun Google terhubung (${detail.authCode}) • Mode Studio Lokal aktif."
    )
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
    onAuthDiagnostic: (AuthFailureDetail) -> Unit = {},
    onLocalGoogleSignIn: (LocalGoogleSession) -> Unit = {}
) {
    if (Firebase.auth.currentUser != null) {
        LocalGoogleSessionStore.clear(context)
        AuthErrorLogger.logEvent(
            stage = AuthStage.AUTO_SIGN_IN,
            event = "existing_firebase_session",
            details = mapOf("firebaseProjectId" to resolveFirebaseProjectId())
        )
        onAuthSuccess()
        return
    }

    val existingLocalSession = LocalGoogleSessionStore.load(context)
    if (existingLocalSession != null) {
        AuthErrorLogger.logEvent(
            stage = AuthStage.AUTO_SIGN_IN,
            event = "restored_local_google_session",
            details = mapOf("hasDisplayName" to existingLocalSession.displayName.isNotBlank())
        )
        onLocalGoogleSignIn(existingLocalSession)
        return
    }

    val projectId = resolveFirebaseProjectId()
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
        val detail = AuthErrorLogger.configMissingFailure(
            stage = AuthStage.CHECK_CONFIG,
            code = "MISSING_WEB_CLIENT_ID",
            headline = "Web Client ID Firebase Belum Ditemukan",
            reason = "Resource default_web_client_id tidak tersedia atau kosong di konfigurasi google-services.json aplikasi.",
            recoveryHint = "Pastikan google-services.json sudah dimuat atau lanjutkan lewat Mode Studio Lokal."
        )
        AuthErrorLogger.logFailure(
            detail = detail,
            level = Log.ERROR,
            webClientIdConfigured = false,
            firebaseProjectId = projectId
        )
        onAuthDiagnostic(detail)
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred !is CustomCredential || cred.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val unexpected = AuthErrorLogger.configMissingFailure(
                    stage = AuthStage.TOKEN_PARSING,
                    code = "UNEXPECTED_CREDENTIAL_TYPE",
                    headline = "Tipe Kredensial Bukan Google ID Token",
                    reason = "Sistem mengembalikan kredensial bertipe ${cred.type} yang bukan Google ID Token.",
                    recoveryHint = "Ketuk Masuk dengan Google untuk memilih akun Google secara manual."
                )
                AuthErrorLogger.logFailure(
                    detail = unexpected,
                    level = Log.WARN,
                    webClientIdConfigured = true,
                    firebaseProjectId = projectId
                )
                onUnauthenticated()
                return@launch
            }

            val googleCred = try {
                GoogleIdTokenCredential.createFrom(cred.data)
            } catch (parseEx: Exception) {
                if (parseEx is CancellationException) throw parseEx
                val detail = AuthErrorLogger.classifyAuthException(parseEx, AuthStage.TOKEN_PARSING)
                AuthErrorLogger.logFailure(
                    detail = detail,
                    throwable = parseEx,
                    level = Log.ERROR,
                    webClientIdConfigured = true,
                    firebaseProjectId = projectId
                )
                onAuthDiagnostic(detail)
                onUnauthenticated()
                return@launch
            }

            try {
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(googleCred.idToken, null)).await()
                LocalGoogleSessionStore.clear(context)
                AuthErrorLogger.logEvent(
                    stage = AuthStage.AUTO_SIGN_IN,
                    event = "auto_sign_in_success",
                    details = mapOf("firebaseProjectId" to projectId)
                )
                onAuthSuccess()
            } catch (firebaseEx: Exception) {
                if (firebaseEx is CancellationException) throw firebaseEx
                val detail = AuthErrorLogger.classifyAuthException(firebaseEx, AuthStage.FIREBASE_CREDENTIAL_SIGN_IN)
                AuthErrorLogger.logFailure(
                    detail = detail,
                    throwable = firebaseEx,
                    level = Log.WARN,
                    webClientIdConfigured = true,
                    firebaseProjectId = projectId
                )
                if (AuthErrorLogger.isFirebaseBackendUnavailableFailure(firebaseEx, detail)) {
                    val localSession = buildLocalSessionFromGoogleCredential(googleCred, detail)
                    LocalGoogleSessionStore.save(context, localSession)
                    AuthErrorLogger.logEvent(
                        stage = AuthStage.AUTO_SIGN_IN,
                        event = "verified_google_local_mode_fallback",
                        details = mapOf("authCode" to detail.authCode)
                    )
                    onLocalGoogleSignIn(localSession)
                } else {
                    onAuthDiagnostic(detail)
                    onUnauthenticated()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = AuthErrorLogger.classifyAuthException(e, AuthStage.AUTO_SIGN_IN)
            val isExpectedQuiet = detail.category == AuthFailureCategory.NO_GOOGLE_ACCOUNT ||
                detail.category == AuthFailureCategory.CANCELLED
            AuthErrorLogger.logFailure(
                detail = detail,
                throwable = if (isExpectedQuiet) null else e,
                level = if (isExpectedQuiet) Log.INFO else Log.WARN,
                webClientIdConfigured = true,
                firebaseProjectId = projectId
            )
            if (!isExpectedQuiet && detail.category != AuthFailureCategory.CREDENTIAL_INTERRUPTED) {
                onAuthDiagnostic(detail)
            }
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (AuthFailureDetail) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
    onLocalGoogleSignIn: (LocalGoogleSession) -> Unit = {}
) {
    val projectId = resolveFirebaseProjectId()
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
        val detail = AuthErrorLogger.configMissingFailure(
            stage = AuthStage.CHECK_CONFIG,
            code = "MISSING_WEB_CLIENT_ID",
            headline = "Web Client ID Firebase Belum Tersedia",
            reason = "Aplikasi belum memiliki default_web_client_id yang valid dari google-services.json.",
            recoveryHint = "Ketuk Masuk ke Studio (Mode Lokal) untuk langsung memakai seluruh fitur editor video."
        )
        AuthErrorLogger.logFailure(
            detail = detail,
            level = Log.ERROR,
            webClientIdConfigured = false,
            firebaseProjectId = projectId
        )
        onAuthError(detail)
        return
    }

    val activity = context as? Activity
    if (activity == null) {
        val detail = AuthErrorLogger.configMissingFailure(
            stage = AuthStage.CHECK_CONFIG,
            code = "MISSING_ACTIVITY_CONTEXT",
            headline = "Konteks Layar Belum Siap untuk Dialog Google",
            reason = "Permintaan Credential Manager memerlukan Activity aktif, bukan Application Context.",
            recoveryHint = "Ketuk Masuk dengan Google sekali lagi setelah layar login selesai dimuat."
        )
        AuthErrorLogger.logFailure(
            detail = detail,
            level = Log.WARN,
            webClientIdConfigured = true,
            firebaseProjectId = projectId
        )
        onAuthError(detail)
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val primaryRequest = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        val attemptedStages = mutableListOf(AuthStage.CREDENTIAL_PRIMARY.key)
        var primaryFailureDetail: AuthFailureDetail? = null

        try {
            val cred = try {
                credentialManager.getCredential(activity, primaryRequest).credential
            } catch (primaryEx: Exception) {
                if (primaryEx is GetCredentialCancellationException || primaryEx is CancellationException) {
                    throw primaryEx
                }
                val primaryDetail = AuthErrorLogger.classifyAuthException(primaryEx, AuthStage.CREDENTIAL_PRIMARY)
                primaryFailureDetail = primaryDetail
                AuthErrorLogger.logFailure(
                    detail = primaryDetail,
                    throwable = primaryEx,
                    level = Log.WARN,
                    webClientIdConfigured = true,
                    firebaseProjectId = projectId,
                    extraStages = attemptedStages.toList()
                )

                val shouldTryGoogleIdFallback = primaryEx is NoCredentialException ||
                    primaryEx is GetCredentialProviderConfigurationException ||
                    primaryEx is GetCredentialUnsupportedException ||
                    primaryEx is GetCredentialUnknownException
                if (!shouldTryGoogleIdFallback) {
                    throw primaryEx
                }

                attemptedStages.add(AuthStage.CREDENTIAL_FALLBACK.key)
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackOption)
                    .build()
                credentialManager.getCredential(activity, fallbackRequest).credential
            }

            if (cred !is CustomCredential || cred.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val detail = AuthErrorLogger.configMissingFailure(
                    stage = AuthStage.TOKEN_PARSING,
                    code = "UNEXPECTED_CREDENTIAL_TYPE",
                    headline = "Kredensial Bukan Token ID Google",
                    reason = "Credential Manager mengembalikan tipe '${cred.type}', bukan Google ID Token.",
                    recoveryHint = "Pilih akun Google Anda melalui dialog Masuk dengan Google atau lanjutkan lewat Mode Studio Lokal."
                )
                AuthErrorLogger.logFailure(
                    detail = detail,
                    level = Log.ERROR,
                    webClientIdConfigured = true,
                    firebaseProjectId = projectId,
                    extraStages = attemptedStages
                )
                onAuthError(detail)
                return@launch
            }

            attemptedStages.add(AuthStage.TOKEN_PARSING.key)
            val googleCred = try {
                GoogleIdTokenCredential.createFrom(cred.data)
            } catch (parseEx: Exception) {
                if (parseEx is CancellationException) throw parseEx
                val detail = AuthErrorLogger.classifyAuthException(parseEx, AuthStage.TOKEN_PARSING)
                AuthErrorLogger.logFailure(
                    detail = detail,
                    throwable = parseEx,
                    level = Log.ERROR,
                    webClientIdConfigured = true,
                    firebaseProjectId = projectId,
                    extraStages = attemptedStages
                )
                onAuthError(detail)
                return@launch
            }

            val idToken = googleCred.idToken
            if (idToken.isBlank()) {
                val detail = AuthErrorLogger.configMissingFailure(
                    stage = AuthStage.TOKEN_PARSING,
                    code = "EMPTY_GOOGLE_ID_TOKEN",
                    headline = "Token ID Google Kosong",
                    reason = "Google Identity mengembalikan token ID kosong sehingga Firebase Authentication tidak dapat memverifikasi sesi.",
                    recoveryHint = "Periksa kecocokan OAuth Web Client ID pada google-services.json atau masuk lewat Mode Studio Lokal."
                )
                AuthErrorLogger.logFailure(
                    detail = detail,
                    level = Log.ERROR,
                    webClientIdConfigured = true,
                    firebaseProjectId = projectId,
                    extraStages = attemptedStages
                )
                onAuthError(detail)
                return@launch
            }

            attemptedStages.add(AuthStage.FIREBASE_CREDENTIAL_SIGN_IN.key)
            try {
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                LocalGoogleSessionStore.clear(context)
                AuthErrorLogger.logEvent(
                    stage = AuthStage.FIREBASE_CREDENTIAL_SIGN_IN,
                    event = "google_sign_in_success",
                    details = mapOf(
                        "firebaseProjectId" to projectId,
                        "attemptedStages" to attemptedStages.joinToString(",")
                    )
                )
                onAuthSuccess()
            } catch (firebaseEx: Exception) {
                if (firebaseEx is CancellationException) throw firebaseEx
                val detail = AuthErrorLogger.classifyAuthException(firebaseEx, AuthStage.FIREBASE_CREDENTIAL_SIGN_IN)
                AuthErrorLogger.logFailure(
                    detail = detail,
                    throwable = firebaseEx,
                    level = Log.ERROR,
                    webClientIdConfigured = true,
                    firebaseProjectId = projectId,
                    extraStages = attemptedStages
                )
                if (AuthErrorLogger.isFirebaseBackendUnavailableFailure(firebaseEx, detail)) {
                    val localSession = buildLocalSessionFromGoogleCredential(googleCred, detail)
                    LocalGoogleSessionStore.save(context, localSession)
                    AuthErrorLogger.logEvent(
                        stage = AuthStage.FIREBASE_CREDENTIAL_SIGN_IN,
                        event = "verified_google_local_mode_fallback",
                        details = mapOf("authCode" to detail.authCode)
                    )
                    onLocalGoogleSignIn(localSession)
                } else {
                    onAuthError(detail)
                }
            }
        } catch (e: GetCredentialCancellationException) {
            val cancelDetail = AuthErrorLogger.classifyAuthException(e, AuthStage.CREDENTIAL_PRIMARY)
            AuthErrorLogger.logFailure(
                detail = cancelDetail,
                throwable = e,
                level = Log.WARN,
                webClientIdConfigured = true,
                firebaseProjectId = projectId,
                extraStages = attemptedStages
            )
            onAuthCancelled()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val fallbackStage = if (primaryFailureDetail != null) {
                AuthStage.CREDENTIAL_FALLBACK
            } else {
                AuthStage.CREDENTIAL_PRIMARY
            }
            val currentDetail = AuthErrorLogger.classifyAuthException(e, fallbackStage)
            val finalDetail = if (
                primaryFailureDetail != null &&
                primaryFailureDetail.category != AuthFailureCategory.NO_GOOGLE_ACCOUNT &&
                primaryFailureDetail.category != AuthFailureCategory.UNKNOWN_CAUSE &&
                currentDetail.category == AuthFailureCategory.NO_GOOGLE_ACCOUNT
            ) {
                primaryFailureDetail.copy(
                    technicalSummary = "${primaryFailureDetail.technicalSummary} | Fallback(${currentDetail.authCode})"
                )
            } else {
                currentDetail
            }
            AuthErrorLogger.logFailure(
                detail = finalDetail,
                throwable = e,
                level = Log.ERROR,
                webClientIdConfigured = true,
                firebaseProjectId = projectId,
                extraStages = attemptedStages
            )
            onAuthError(finalDetail)
        }
    }
}

fun signOut(
    context: Context? = null,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onBeforeSignOut: () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                onBeforeSignOut()
                context?.let { LocalGoogleSessionStore.clear(it) }
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                AuthErrorLogger.logEvent(
                    stage = AuthStage.SIGN_OUT,
                    event = "sign_out_complete"
                )
            } catch (e: Exception) {
                val detail = AuthErrorLogger.classifyAuthException(e, AuthStage.SIGN_OUT)
                AuthErrorLogger.logFailure(
                    detail = detail,
                    throwable = e,
                    level = Log.WARN
                )
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
