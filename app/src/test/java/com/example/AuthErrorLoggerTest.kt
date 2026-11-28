package com.example

import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.ui.auth.AuthErrorLogger
import com.example.ui.auth.AuthFailureCategory
import com.example.ui.auth.AuthStage
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthErrorLoggerTest {

    @Test
    fun `classifies FirebaseAuthException ERROR_OPERATION_NOT_ALLOWED specifically`() {
        val ex = FirebaseAuthException(
            "ERROR_OPERATION_NOT_ALLOWED",
            "The given sign-in provider is disabled for this Firebase project."
        )

        val detail = AuthErrorLogger.classifyAuthException(ex, AuthStage.FIREBASE_CREDENTIAL_SIGN_IN)

        assertEquals(AuthStage.FIREBASE_CREDENTIAL_SIGN_IN, detail.stage)
        assertEquals(AuthFailureCategory.FIREBASE_PROVIDER_DISABLED, detail.category)
        assertEquals("ERROR_OPERATION_NOT_ALLOWED", detail.authCode)
        assertTrue(detail.headline.contains("Provider Login Google", ignoreCase = true))
        assertTrue(detail.reason.contains("Google Sign-In", ignoreCase = true))
    }

    @Test
    fun `classifies FirebaseAuthInvalidCredentialsException audience mismatch specifically`() {
        val ex = FirebaseAuthInvalidCredentialsException(
            "ERROR_INVALID_CREDENTIAL",
            "The audience of the ID token does not match client_id configured."
        )

        val detail = AuthErrorLogger.classifyAuthException(ex, AuthStage.FIREBASE_CREDENTIAL_SIGN_IN)

        assertEquals(AuthFailureCategory.FIREBASE_INVALID_CREDENTIAL, detail.category)
        assertEquals("ERROR_INVALID_CREDENTIAL", detail.authCode)
        assertTrue(detail.headline.contains("Web Client ID", ignoreCase = true))
        assertTrue(detail.reason.contains("audience", ignoreCase = true))
    }

    @Test
    fun `classifies Play Services status 10 DEVELOPER_ERROR inside GetCredentialUnknownException`() {
        val ex = GetCredentialUnknownException("[10] Developer error / SHA-1 mismatch")

        val detail = AuthErrorLogger.classifyAuthException(ex, AuthStage.CREDENTIAL_PRIMARY)

        assertEquals(AuthFailureCategory.FIREBASE_APP_NOT_AUTHORIZED, detail.category)
        assertEquals("PLAY_10_DEVELOPER_ERROR", detail.authCode)
        assertTrue(detail.headline.contains("SHA-1", ignoreCase = true))
        assertTrue(detail.reason.contains("DEVELOPER_ERROR", ignoreCase = true))
    }

    @Test
    fun `classifies ApiException status 28444 caller not whitelisted`() {
        val ex = ApiException(Status(28444, "Caller not whitelisted to call this API"))

        val detail = AuthErrorLogger.classifyAuthException(ex, AuthStage.CREDENTIAL_FALLBACK)

        assertEquals(AuthFailureCategory.FIREBASE_APP_NOT_AUTHORIZED, detail.category)
        assertEquals("PLAY_28444_CALLER_NOT_WHITELISTED", detail.authCode)
        assertTrue(detail.reason.contains("28444"))
    }

    @Test
    fun `classifies NoCredentialException as NO_GOOGLE_ACCOUNT with actionable Play Store guidance`() {
        val ex = NoCredentialException("No credentials available")

        val detail = AuthErrorLogger.classifyAuthException(ex, AuthStage.CREDENTIAL_FALLBACK)

        assertEquals(AuthFailureCategory.NO_GOOGLE_ACCOUNT, detail.category)
        assertEquals("NO_CREDENTIAL", detail.authCode)
        assertTrue(detail.recoveryHint.contains("Play Store", ignoreCase = true))
        assertFalse(detail.headline.equals("error", ignoreCase = true))
        assertFalse(detail.headline.contains("buf", ignoreCase = true))
    }

    @Test
    fun `classifies wrapped network failure through cause chain`() {
        val wrapped = ExecutionException(UnknownHostException("identitytoolkit.googleapis.com"))

        val detail = AuthErrorLogger.classifyAuthException(wrapped, AuthStage.FIREBASE_CREDENTIAL_SIGN_IN)

        assertEquals(AuthFailureCategory.NETWORK_UNAVAILABLE, detail.category)
        assertTrue(detail.headline.contains("Koneksi", ignoreCase = true))
        assertTrue(detail.technicalSummary.contains("UnknownHostException"))
    }

    @Test
    fun `classifies FirebaseNetworkException and provider configuration exceptions cleanly`() {
        val netDetail = AuthErrorLogger.classifyAuthException(
            FirebaseNetworkException("A network failure has occurred"),
            AuthStage.AUTO_SIGN_IN
        )
        assertEquals(AuthFailureCategory.NETWORK_UNAVAILABLE, netDetail.category)

        val configDetail = AuthErrorLogger.classifyAuthException(
            GetCredentialProviderConfigurationException("Provider missing"),
            AuthStage.CREDENTIAL_PRIMARY
        )
        assertEquals(AuthFailureCategory.CREDENTIAL_PROVIDER_UNAVAILABLE, configDetail.category)

        val unsupportedDetail = AuthErrorLogger.classifyAuthException(
            GetCredentialUnsupportedException("Unsupported"),
            AuthStage.CREDENTIAL_PRIMARY
        )
        assertEquals(AuthFailureCategory.CREDENTIAL_UNSUPPORTED, unsupportedDetail.category)
    }

    @Test
    fun `redacts JWT and OAuth tokens in sanitized diagnostic messages`() {
        val sanitized = AuthErrorLogger.sanitizeMessage(
            "Invalid token eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTYifQ.signature and ya29.a0AfH6SMBxSecretTokenValue"
        )

        assertFalse(sanitized.contains("eyJhbGciOiJSUzI1NiJ9"))
        assertFalse(sanitized.contains("ya29.a0AfH6SMBxSecretTokenValue"))
        assertTrue(sanitized.contains("[REDACTED_JWT]"))
        assertTrue(sanitized.contains("[REDACTED_TOKEN]"))
    }

    @Test
    fun `classifies PROJECT_SOFT_DELETED and triggers verified local mode fallback`() {
        val ex = FirebaseException("An internal error has occurred. [ PROJECT_SOFT_DELETED ]")

        val detail = AuthErrorLogger.classifyAuthException(ex, AuthStage.FIREBASE_CREDENTIAL_SIGN_IN)

        assertEquals(AuthFailureCategory.FIREBASE_PROJECT_DELETED, detail.category)
        assertEquals("PROJECT_SOFT_DELETED", detail.authCode)
        assertTrue(detail.headline.contains("Nonaktif", ignoreCase = true))
        assertTrue(AuthErrorLogger.isFirebaseBackendUnavailableFailure(ex, detail))
    }
}
