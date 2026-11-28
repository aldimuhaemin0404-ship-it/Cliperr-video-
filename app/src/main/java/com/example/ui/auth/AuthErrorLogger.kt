package com.example.ui.auth

import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApiNotAvailableException
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class AuthStage(val label: String, val key: String) {
    CHECK_CONFIG("Pemeriksaan Konfigurasi", "check_config"),
    AUTO_SIGN_IN("Login Otomatis", "auto_sign_in"),
    CREDENTIAL_PRIMARY("Dialog Masuk Google", "credential_primary"),
    CREDENTIAL_FALLBACK("Pemilihan Akun Google", "credential_fallback"),
    TOKEN_PARSING("Validasi Token Google", "token_parsing"),
    FIREBASE_CREDENTIAL_SIGN_IN("Autentikasi Firebase", "firebase_credential_sign_in"),
    SIGN_OUT("Keluar Sesi", "sign_out")
}

enum class AuthFailureCategory(val label: String) {
    CONFIG_MISSING("Konfigurasi OAuth / Client ID"),
    NO_GOOGLE_ACCOUNT("Akun Google Tidak Ditemukan"),
    CREDENTIAL_PROVIDER_UNAVAILABLE("Layanan Kredensial Tidak Siap"),
    CREDENTIAL_UNSUPPORTED("Perangkat Tidak Mendukung Credential Manager"),
    CREDENTIAL_INTERRUPTED("Proses Login Terhenti"),
    CREDENTIAL_CUSTOM("Respons Provider Kredensial"),
    TOKEN_PARSING_FAILED("Token Google Tidak Valid"),
    FIREBASE_PROVIDER_DISABLED("Provider Google Belum Aktif"),
    FIREBASE_PROJECT_DELETED("Proyek Firebase Nonaktif / Terhapus"),
    FIREBASE_INVALID_CREDENTIAL("Kredensial / SHA-1 Ditolak"),
    FIREBASE_APP_NOT_AUTHORIZED("Otorisasi Aplikasi / SHA-1"),
    FIREBASE_USER_DISABLED("Akun Dinonaktifkan"),
    FIREBASE_ACCOUNT_COLLISION("Konflik Metode Login"),
    FIREBASE_RATE_LIMITED("Batas Percobaan Tercapai"),
    FIREBASE_TOKEN_EXPIRED("Sesi Token Kedaluwarsa"),
    NETWORK_UNAVAILABLE("Kendala Koneksi Jaringan"),
    PLAY_SERVICES_UNAVAILABLE("Google Play Services Tidak Tersedia"),
    CANCELLED("Dibatalkan Pengguna"),
    UNKNOWN_CAUSE("Kendala Autentikasi Lainnya")
}

data class AuthFailureDetail(
    val stage: AuthStage,
    val category: AuthFailureCategory,
    val authCode: String,
    val exceptionClass: String,
    val headline: String,
    val reason: String,
    val recoveryHint: String,
    val technicalSummary: String,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toCopyableDiagnosticReport(): String = buildString {
        appendLine("Diagnosa Autentikasi Google / Firebase")
        appendLine("Status: $headline")
        appendLine("Tahap: ${stage.label} (${stage.key})")
        appendLine("Kode: $authCode")
        appendLine("Kategori: ${category.label}")
        appendLine("Alasan: $reason")
        appendLine("Saran: $recoveryHint")
        append("Ringkasan Teknis: $technicalSummary")
    }
}

object AuthErrorLogger {

    private const val LOG_TAG = "FirebaseAuthLog"
    private val JWT_PATTERN = Regex("""eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}(?:\.[A-Za-z0-9_-]+)?""")
    private val ACCESS_TOKEN_PATTERN = Regex("""ya29\.[A-Za-z0-9_.-]+""")
    private val PLAY_STATUS_CODE_REGEX = Regex("""\b(7|8|10|13|16|17|12500|12501|12502|28444)\b""")

    fun configMissingFailure(
        stage: AuthStage,
        code: String,
        headline: String,
        reason: String,
        recoveryHint: String
    ): AuthFailureDetail {
        val technical = "[${stage.key}] $code • ConfigurationCheck"
        return AuthFailureDetail(
            stage = stage,
            category = AuthFailureCategory.CONFIG_MISSING,
            authCode = code,
            exceptionClass = "ConfigurationCheck",
            headline = headline,
            reason = reason,
            recoveryHint = recoveryHint,
            technicalSummary = technical
        )
    }

    fun classifyAuthException(
        throwable: Throwable,
        stage: AuthStage
    ): AuthFailureDetail {
        val causeChain = generateSequence(throwable) { it.cause }
            .take(12)
            .toList()

        val rootFirebaseAuth = causeChain.filterIsInstance<FirebaseAuthException>().firstOrNull()
        val rootCredential = causeChain.filterIsInstance<GetCredentialException>().firstOrNull()
        val rootApi = causeChain.filterIsInstance<ApiException>().firstOrNull()
        val rootNetwork = causeChain.firstOrNull {
            it is FirebaseNetworkException ||
                it is UnknownHostException ||
                it is SocketTimeoutException ||
                it is ConnectException ||
                it is SSLException
        }
        val rootPlayUnavailable = causeChain.filterIsInstance<FirebaseApiNotAvailableException>().firstOrNull()
        val rootTokenParse = causeChain.filterIsInstance<GoogleIdTokenParsingException>().firstOrNull()
        val rootRateLimit = causeChain.filterIsInstance<FirebaseTooManyRequestsException>().firstOrNull()

        val primaryEx = rootFirebaseAuth
            ?: rootTokenParse
            ?: rootNetwork
            ?: rootPlayUnavailable
            ?: rootRateLimit
            ?: rootApi
            ?: rootCredential
            ?: causeChain.lastOrNull()
            ?: throwable

        val sanitizedRawMsg = sanitizeMessage(
            listOfNotNull(
                primaryEx.localizedMessage,
                primaryEx.message,
                throwable.message
            ).firstOrNull { it.isNotBlank() }
        )

        val exceptionChainSummary = causeChain.joinToString(" -> ") { ex ->
            ex::class.java.simpleName.ifBlank { "Exception" }
        }

        val detail = when {
            primaryEx is GetCredentialCancellationException -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.CANCELLED,
                authCode = "USER_CANCELLED",
                exceptionClass = primaryEx::class.java.simpleName,
                headline = "Pemilihan Akun Google Dibatalkan",
                reason = "Dialog pemilihan akun Google ditutup sebelum proses masuk selesai.",
                recoveryHint = "Ketuk Masuk dengan Google dan pilih akun Google aktif untuk melanjutkan.",
                technicalSummary = buildTechnicalSummary(stage, "USER_CANCELLED", exceptionChainSummary, sanitizedRawMsg)
            )

            rootFirebaseAuth != null -> classifyFirebaseAuthException(
                authEx = rootFirebaseAuth,
                stage = stage,
                exceptionChain = exceptionChainSummary,
                sanitizedMessage = sanitizedRawMsg
            )

            rootRateLimit != null -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.FIREBASE_RATE_LIMITED,
                authCode = "TOO_MANY_REQUESTS",
                exceptionClass = rootRateLimit::class.java.simpleName,
                headline = "Terlalu Banyak Percobaan Masuk",
                reason = "Firebase membatasi sementara permintaan autentikasi dari perangkat ini karena percobaan berulang dalam waktu singkat.",
                recoveryHint = "Tunggu beberapa menit sebelum mencoba Masuk dengan Google kembali.",
                technicalSummary = buildTechnicalSummary(stage, "TOO_MANY_REQUESTS", exceptionChainSummary, sanitizedRawMsg)
            )

            rootTokenParse != null -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.TOKEN_PARSING_FAILED,
                authCode = "GOOGLE_ID_TOKEN_PARSE_FAILED",
                exceptionClass = rootTokenParse::class.java.simpleName,
                headline = "Token Identitas Google Tidak Valid",
                reason = "Respons kredensial dari Google Identity tidak memuat token ID yang dapat dibaca oleh Firebase Auth.",
                recoveryHint = "Periksa kesesuaian default_web_client_id dengan OAuth Web Client ID proyek Firebase, lalu coba masuk ulang.",
                technicalSummary = buildTechnicalSummary(stage, "GOOGLE_ID_TOKEN_PARSE_FAILED", exceptionChainSummary, sanitizedRawMsg)
            )

            rootNetwork != null -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.NETWORK_UNAVAILABLE,
                authCode = rootNetwork::class.java.simpleName.uppercase(),
                exceptionClass = rootNetwork::class.java.simpleName,
                headline = "Koneksi ke Server Autentikasi Terputus",
                reason = "Perangkat tidak dapat menjangkau server Google Identity atau Firebase Auth (${rootNetwork::class.java.simpleName}).",
                recoveryHint = "Pastikan koneksi Wi-Fi/seluler aktif, tanggal & jam perangkat akurat, lalu ketuk Masuk dengan Google lagi.",
                technicalSummary = buildTechnicalSummary(stage, rootNetwork::class.java.simpleName.uppercase(), exceptionChainSummary, sanitizedRawMsg)
            )

            rootPlayUnavailable != null -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.PLAY_SERVICES_UNAVAILABLE,
                authCode = "FIREBASE_PLAY_SERVICES_UNAVAILABLE",
                exceptionClass = rootPlayUnavailable::class.java.simpleName,
                headline = "Google Play Services Tidak Tersedia",
                reason = "Autentikasi Firebase membutuhkan Google Play Services yang aktif dan terpasang di perangkat atau emulator ini.",
                recoveryHint = "Pastikan menggunakan image Android dengan Google Play / Google APIs dan perbarui Google Play Services.",
                technicalSummary = buildTechnicalSummary(stage, "FIREBASE_PLAY_SERVICES_UNAVAILABLE", exceptionChainSummary, sanitizedRawMsg)
            )

            rootApi != null -> classifyPlayServicesStatusCode(
                statusCode = rootApi.statusCode,
                stage = stage,
                exceptionClass = rootApi::class.java.simpleName,
                exceptionChain = exceptionChainSummary,
                sanitizedMessage = sanitizedRawMsg
            )

            rootCredential != null -> classifyCredentialManagerException(
                credEx = rootCredential,
                stage = stage,
                exceptionChain = exceptionChainSummary,
                sanitizedMessage = sanitizedRawMsg
            )

            else -> {
                val upperRaw = sanitizedRawMsg.uppercase()
                val detectedPlayCode = extractPlayStatusCode(sanitizedRawMsg)
                when {
                    upperRaw.contains("PROJECT_SOFT_DELETED") ||
                        upperRaw.contains("PROJECT_DELETED") ||
                        upperRaw.contains("PROJECT_NOT_FOUND") -> AuthFailureDetail(
                        stage = stage,
                        category = AuthFailureCategory.FIREBASE_PROJECT_DELETED,
                        authCode = if (upperRaw.contains("PROJECT_SOFT_DELETED")) "PROJECT_SOFT_DELETED" else "PROJECT_NOT_FOUND",
                        exceptionClass = primaryEx::class.java.simpleName,
                        headline = "Proyek Firebase Sedang Nonaktif (Terhapus)",
                        reason = "Akun Google Anda berhasil dipilih, namun proyek Firebase backend tertaut sedang dalam status dihapus (PROJECT_SOFT_DELETED).",
                        recoveryHint = "Masuk otomatis ke Mode Studio Lokal dengan akun Google Anda, atau hubungkan proyek Firebase baru yang aktif.",
                        technicalSummary = buildTechnicalSummary(stage, "PROJECT_SOFT_DELETED", exceptionChainSummary, sanitizedRawMsg)
                    )

                    upperRaw.contains("CONFIGURATION_NOT_FOUND") -> AuthFailureDetail(
                        stage = stage,
                        category = AuthFailureCategory.FIREBASE_PROVIDER_DISABLED,
                        authCode = "CONFIGURATION_NOT_FOUND",
                        exceptionClass = primaryEx::class.java.simpleName,
                        headline = "Konfigurasi Firebase Auth Belum Aktif",
                        reason = "Layanan Firebase Authentication belum diinisialisasi di proyek backend (CONFIGURATION_NOT_FOUND).",
                        recoveryHint = "Aktifkan Authentication di Firebase Console atau lanjutkan memakai Mode Studio Lokal.",
                        technicalSummary = buildTechnicalSummary(stage, "CONFIGURATION_NOT_FOUND", exceptionChainSummary, sanitizedRawMsg)
                    )

                    upperRaw.contains("API_KEY_ANDROID_APP_BLOCKED") ||
                        upperRaw.contains("API_KEY_INVALID") -> AuthFailureDetail(
                        stage = stage,
                        category = AuthFailureCategory.FIREBASE_APP_NOT_AUTHORIZED,
                        authCode = "API_KEY_RESTRICTED",
                        exceptionClass = primaryEx::class.java.simpleName,
                        headline = "Kunci API Firebase Tidak Diizinkan",
                        reason = "Google Cloud API Key pada proyek Firebase menolak permintaan dari aplikasi ($sanitizedRawMsg).",
                        recoveryHint = "Periksa pembatasan Android API Key & SHA-1 proyek Firebase atau masuk lewat Mode Studio Lokal.",
                        technicalSummary = buildTechnicalSummary(stage, "API_KEY_RESTRICTED", exceptionChainSummary, sanitizedRawMsg)
                    )

                    detectedPlayCode != null -> classifyPlayServicesStatusCode(
                        statusCode = detectedPlayCode,
                        stage = stage,
                        exceptionClass = primaryEx::class.java.simpleName,
                        exceptionChain = exceptionChainSummary,
                        sanitizedMessage = sanitizedRawMsg
                    )

                    else -> {
                        val code = when (primaryEx) {
                            is IOException -> "IO_FAILURE"
                            is FirebaseException -> "FIREBASE_EXCEPTION"
                            else -> primaryEx::class.java.simpleName.uppercase()
                        }
                        AuthFailureDetail(
                            stage = stage,
                            category = AuthFailureCategory.UNKNOWN_CAUSE,
                            authCode = code,
                            exceptionClass = primaryEx::class.java.simpleName,
                            headline = "Sesi Autentikasi Google Belum Terhubung",
                            reason = if (sanitizedRawMsg.isNotBlank()) {
                                "Proses ${stage.label.lowercase()} berhenti dengan keterangan: $sanitizedRawMsg"
                            } else {
                                "Terjadi kendala saat menghubungkan sesi Google pada tahap ${stage.label.lowercase()}."
                            },
                            recoveryHint = "Periksa akun Google aktif di perangkat, koneksi internet, dan konfigurasi SHA-1 proyek Firebase.",
                            technicalSummary = buildTechnicalSummary(stage, code, exceptionChainSummary, sanitizedRawMsg)
                        )
                    }
                }
            }
        }
        return detail
    }

    private fun classifyFirebaseAuthException(
        authEx: FirebaseAuthException,
        stage: AuthStage,
        exceptionChain: String,
        sanitizedMessage: String
    ): AuthFailureDetail {
        val code = authEx.errorCode.ifBlank { "FIREBASE_AUTH_UNKNOWN" }
        return when (code) {
            "ERROR_OPERATION_NOT_ALLOWED" -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.FIREBASE_PROVIDER_DISABLED,
                authCode = code,
                exceptionClass = authEx::class.java.simpleName,
                headline = "Provider Login Google Belum Aktif di Firebase",
                reason = "Firebase Authentication menolak login karena metode Google Sign-In belum diaktifkan pada proyek Firebase backend.",
                recoveryHint = "Aktifkan Google Sign-In pada Firebase Console > Authentication > Sign-in method, dan pastikan SHA-1 terdaftar.",
                technicalSummary = buildTechnicalSummary(stage, code, exceptionChain, sanitizedMessage)
            )

            "ERROR_INVALID_CREDENTIAL",
            "ERROR_INVALID_IDP_RESPONSE" -> {
                val lowerMsg = sanitizedMessage.lowercase()
                val audienceHint = lowerMsg.contains("audience") ||
                    lowerMsg.contains("client_id") ||
                    lowerMsg.contains("azp")
                AuthFailureDetail(
                    stage = stage,
                    category = AuthFailureCategory.FIREBASE_INVALID_CREDENTIAL,
                    authCode = code,
                    exceptionClass = authEx::class.java.simpleName,
                    headline = if (audienceHint) {
                        "Web Client ID / Konfigurasi Firebase Tidak Cocok"
                    } else {
                        "Token Kredensial Ditolak Firebase Auth"
                    },
                    reason = if (audienceHint) {
                        "Firebase menolak token Google karena Client ID (audience) di aplikasi tidak cocok dengan OAuth Web Client ID pada proyek Firebase ($code)."
                    } else {
                        "Firebase Auth menolak token kredensial Google yang dikirim pada tahap ${stage.label} (kode: $code)."
                    },
                    recoveryHint = "Cocokkan google-services.json, Web Client ID (default_web_client_id), dan SHA-1 keystore debug/release di Firebase Console.",
                    technicalSummary = buildTechnicalSummary(stage, code, exceptionChain, sanitizedMessage)
                )
            }

            "ERROR_APP_NOT_AUTHORIZED",
            "ERROR_INVALID_API_KEY",
            "ERROR_APP_NOT_VERIFIED",
            "ERROR_INVALID_CERT_HASH" -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.FIREBASE_APP_NOT_AUTHORIZED,
                authCode = code,
                exceptionClass = authEx::class.java.simpleName,
                headline = "Aplikasi atau SHA-1 Belum Terotorisasi di Firebase",
                reason = "Package ID atau sertifikat SHA-1/API Key APK ini belum diizinkan mengakses proyek Firebase Authentication ($code).",
                recoveryHint = "Pastikan SHA-1 debug.keystore & package name terdaftar di Firebase Console dan API Key Google Cloud tidak memblokir aplikasi.",
                technicalSummary = buildTechnicalSummary(stage, code, exceptionChain, sanitizedMessage)
            )

            "ERROR_USER_DISABLED" -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.FIREBASE_USER_DISABLED,
                authCode = code,
                exceptionClass = authEx::class.java.simpleName,
                headline = "Akun Google Ini Dinonaktifkan",
                reason = "Status akun pengguna pada proyek Firebase Authentication saat ini dinonaktifkan ($code).",
                recoveryHint = "Gunakan akun Google lain yang aktif atau aktifkan kembali pengguna di Firebase Console.",
                technicalSummary = buildTechnicalSummary(stage, code, exceptionChain, sanitizedMessage)
            )

            "ERROR_USER_NOT_FOUND",
            "ERROR_INVALID_USER_TOKEN",
            "ERROR_USER_TOKEN_EXPIRED" -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.FIREBASE_TOKEN_EXPIRED,
                authCode = code,
                exceptionClass = authEx::class.java.simpleName,
                headline = "Sesi Token Autentikasi Kedaluwarsa",
                reason = "Token pengguna sebelumnya sudah tidak berlaku atau dicabut oleh server Firebase ($code).",
                recoveryHint = "Ketuk Masuk dengan Google lagi untuk memperbarui token autentikasi.",
                technicalSummary = buildTechnicalSummary(stage, code, exceptionChain, sanitizedMessage)
            )

            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
            "ERROR_CREDENTIAL_ALREADY_IN_USE" -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.FIREBASE_ACCOUNT_COLLISION,
                authCode = code,
                exceptionClass = authEx::class.java.simpleName,
                headline = "Konflik Akun dengan Metode Login Lain",
                reason = "Email atau kredensial Google ini sudah terhubung dengan akun Firebase lain ($code).",
                recoveryHint = "Masuk menggunakan akun Google utama yang terhubung sebelumnya.",
                technicalSummary = buildTechnicalSummary(stage, code, exceptionChain, sanitizedMessage)
            )

            "ERROR_TOO_MANY_REQUESTS" -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.FIREBASE_RATE_LIMITED,
                authCode = code,
                exceptionClass = authEx::class.java.simpleName,
                headline = "Terlalu Banyak Permintaan Autentikasi",
                reason = "Server Firebase membatasi permintaan login sementara waktu ($code).",
                recoveryHint = "Tunggu beberapa menit sebelum mencoba masuk lagi.",
                technicalSummary = buildTechnicalSummary(stage, code, exceptionChain, sanitizedMessage)
            )

            "ERROR_NETWORK_REQUEST_FAILED" -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.NETWORK_UNAVAILABLE,
                authCode = code,
                exceptionClass = authEx::class.java.simpleName,
                headline = "Permintaan Jaringan Firebase Auth Terputus",
                reason = "SDK Firebase Auth tidak memperoleh respons jaringan saat memverifikasi token Google ($code).",
                recoveryHint = "Periksa koneksi internet, DNS/VPN, lalu ketuk Masuk dengan Google ulang.",
                technicalSummary = buildTechnicalSummary(stage, code, exceptionChain, sanitizedMessage)
            )

            else -> {
                val subclassHint = when (authEx) {
                    is FirebaseAuthInvalidCredentialsException -> "Kredensial tidak valid"
                    is FirebaseAuthInvalidUserException -> "Status pengguna tidak valid"
                    is FirebaseAuthUserCollisionException -> "Konflik identitas pengguna"
                    else -> "Penolakan Firebase Authentication"
                }
                AuthFailureDetail(
                    stage = stage,
                    category = AuthFailureCategory.FIREBASE_INVALID_CREDENTIAL,
                    authCode = code,
                    exceptionClass = authEx::class.java.simpleName,
                    headline = "$subclassHint ($code)",
                    reason = if (sanitizedMessage.isNotBlank()) {
                        "Firebase Authentication mengembalikan kode $code: $sanitizedMessage"
                    } else {
                        "Firebase Authentication mengembalikan kode $code pada tahap ${stage.label}."
                    },
                    recoveryHint = "Periksa status Firebase Auth provider Google & kecocokan konfigurasi SHA-1 proyek.",
                    technicalSummary = buildTechnicalSummary(stage, code, exceptionChain, sanitizedMessage)
                )
            }
        }
    }

    private fun classifyCredentialManagerException(
        credEx: GetCredentialException,
        stage: AuthStage,
        exceptionChain: String,
        sanitizedMessage: String
    ): AuthFailureDetail {
        val detectedPlayCode = extractPlayStatusCode(sanitizedMessage)
        if (detectedPlayCode != null && credEx !is NoCredentialException) {
            return classifyPlayServicesStatusCode(
                statusCode = detectedPlayCode,
                stage = stage,
                exceptionClass = credEx::class.java.simpleName,
                exceptionChain = exceptionChain,
                sanitizedMessage = sanitizedMessage
            )
        }

        return when (credEx) {
            is NoCredentialException -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.NO_GOOGLE_ACCOUNT,
                authCode = "NO_CREDENTIAL",
                exceptionClass = credEx::class.java.simpleName,
                headline = "Belum Ada Akun Google Aktif di Perangkat",
                reason = "Android Credential Manager tidak menemukan akun Google yang tersimpan atau siap dipilih di perangkat/emulator ini.",
                recoveryHint = "Buka Pengaturan Android > Akun atau Google Play Store, masuk ke akun Google Anda, lalu ketuk Masuk dengan Google.",
                technicalSummary = buildTechnicalSummary(stage, "NO_CREDENTIAL", exceptionChain, sanitizedMessage)
            )

            is GetCredentialProviderConfigurationException -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.CREDENTIAL_PROVIDER_UNAVAILABLE,
                authCode = "PROVIDER_CONFIGURATION_UNAVAILABLE",
                exceptionClass = credEx::class.java.simpleName,
                headline = "Layanan Kredensial Google Play Belum Siap",
                reason = "Provider autentikasi Google Play Services tidak ditemukan atau belum terkonfigurasi di sistem Android ini.",
                recoveryHint = "Pastikan Google Play Services aktif dan diperbarui di perangkat atau gunakan emulator Android berlabel Google Play.",
                technicalSummary = buildTechnicalSummary(stage, "PROVIDER_CONFIGURATION_UNAVAILABLE", exceptionChain, sanitizedMessage)
            )

            is GetCredentialUnsupportedException -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.CREDENTIAL_UNSUPPORTED,
                authCode = "CREDENTIAL_MANAGER_UNSUPPORTED",
                exceptionClass = credEx::class.java.simpleName,
                headline = "Credential Manager Tidak Didukung Perangkat",
                reason = "Sistem Android pada perangkat/emulator ini tidak menyediakan modul Jetpack Credential Manager atau Google Play Auth.",
                recoveryHint = "Gunakan perangkat/emulator Android dengan Google Play Services terpasang.",
                technicalSummary = buildTechnicalSummary(stage, "CREDENTIAL_MANAGER_UNSUPPORTED", exceptionChain, sanitizedMessage)
            )

            is GetCredentialInterruptedException -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.CREDENTIAL_INTERRUPTED,
                authCode = "CREDENTIAL_INTERRUPTED",
                exceptionClass = credEx::class.java.simpleName,
                headline = "Dialog Masuk Google Terhenti Sementara",
                reason = "Proses pemilihan akun Google terinterupsi oleh sistem atau perpindahan layar.",
                recoveryHint = "Ketuk Masuk dengan Google kembali dan tunggu dialog akun selesai memuat.",
                technicalSummary = buildTechnicalSummary(stage, "CREDENTIAL_INTERRUPTED", exceptionChain, sanitizedMessage)
            )

            is GetCredentialCustomException -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.CREDENTIAL_CUSTOM,
                authCode = credEx.type.ifBlank { "CUSTOM_CREDENTIAL_STATUS" },
                exceptionClass = credEx::class.java.simpleName,
                headline = "Respons Khusus Provider Kredensial",
                reason = if (sanitizedMessage.isNotBlank()) {
                    "Provider kredensial mengembalikan status (${credEx.type}): $sanitizedMessage"
                } else {
                    "Provider kredensial mengembalikan tipe status ${credEx.type}."
                },
                recoveryHint = "Periksa akun Google aktif di sistem dan coba ulangi proses masuk.",
                technicalSummary = buildTechnicalSummary(stage, credEx.type.ifBlank { "CUSTOM" }, exceptionChain, sanitizedMessage)
            )

            is GetCredentialUnknownException -> {
                val lower = sanitizedMessage.lowercase()
                val looksLikeNoAccount = lower.contains("no credential") ||
                    lower.contains("cannot find a matching credential") ||
                    lower.contains("account") && lower.contains("not found")
                if (looksLikeNoAccount) {
                    AuthFailureDetail(
                        stage = stage,
                        category = AuthFailureCategory.NO_GOOGLE_ACCOUNT,
                        authCode = "NO_MATCHING_GOOGLE_ACCOUNT",
                        exceptionClass = credEx::class.java.simpleName,
                        headline = "Tidak Ada Akun Google yang Siap Dipilih",
                        reason = "Google Identity tidak menemukan akun Google aktif pada perangkat/emulator ($sanitizedMessage).",
                        recoveryHint = "Masuk ke akun Google di Setelan Android atau Play Store, lalu ulangi Masuk dengan Google.",
                        technicalSummary = buildTechnicalSummary(stage, "NO_MATCHING_GOOGLE_ACCOUNT", exceptionChain, sanitizedMessage)
                    )
                } else {
                    AuthFailureDetail(
                        stage = stage,
                        category = AuthFailureCategory.UNKNOWN_CAUSE,
                        authCode = "CREDENTIAL_UNKNOWN_STATUS",
                        exceptionClass = credEx::class.java.simpleName,
                        headline = "Layanan Identitas Google Belum Merespons",
                        reason = if (sanitizedMessage.isNotBlank()) {
                            "Google Credential Manager mengembalikan status: $sanitizedMessage"
                        } else {
                            "Google Credential Manager tidak mengembalikan akun yang dapat dipilih pada tahap ${stage.label}."
                        },
                        recoveryHint = "Pastikan Google Play Services aktif, koneksi internet stabil, dan SHA-1 aplikasi cocok dengan proyek Firebase.",
                        technicalSummary = buildTechnicalSummary(stage, "CREDENTIAL_UNKNOWN_STATUS", exceptionChain, sanitizedMessage)
                    )
                }
            }

            else -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.UNKNOWN_CAUSE,
                authCode = credEx.type.ifBlank { credEx::class.java.simpleName.uppercase() },
                exceptionClass = credEx::class.java.simpleName,
                headline = "Kendala Pemilihan Kredensial Google",
                reason = if (sanitizedMessage.isNotBlank()) {
                    "Detail dari Credential Manager (${credEx.type}): $sanitizedMessage"
                } else {
                    "Credential Manager berhenti dengan tipe ${credEx.type}."
                },
                recoveryHint = "Periksa akun Google di perangkat & jaringan internet, lalu coba ulang.",
                technicalSummary = buildTechnicalSummary(stage, credEx.type.ifBlank { "CREDENTIAL" }, exceptionChain, sanitizedMessage)
            )
        }
    }

    private fun classifyPlayServicesStatusCode(
        statusCode: Int,
        stage: AuthStage,
        exceptionClass: String,
        exceptionChain: String,
        sanitizedMessage: String
    ): AuthFailureDetail {
        val codeLabel = "PLAY_SERVICES_$statusCode"
        return when (statusCode) {
            10 -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.FIREBASE_APP_NOT_AUTHORIZED,
                authCode = "PLAY_10_DEVELOPER_ERROR",
                exceptionClass = exceptionClass,
                headline = "Konfigurasi OAuth / SHA-1 Tidak Cocok (Kode 10)",
                reason = "Google Play Auth menolak permintaan (status 10: DEVELOPER_ERROR). Biasanya terjadi karena SHA-1 keystore aplikasi atau Web Client ID di google-services.json belum sinkron dengan konsol Firebase/Google Cloud.",
                recoveryHint = "Pastikan SHA-1 debug.keystore & package name terdaftar pada OAuth Android Client serta Web Client ID cocok.",
                technicalSummary = buildTechnicalSummary(stage, "PLAY_10_DEVELOPER_ERROR", exceptionChain, sanitizedMessage)
            )

            7 -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.NETWORK_UNAVAILABLE,
                authCode = "PLAY_7_NETWORK_ERROR",
                exceptionClass = exceptionClass,
                headline = "Jaringan ke Google Play Auth Terputus (Kode 7)",
                reason = "Google Play Services melaporkan gangguan koneksi jaringan saat meminta token Google (status 7).",
                recoveryHint = "Periksa koneksi internet perangkat/emulator lalu coba masuk lagi.",
                technicalSummary = buildTechnicalSummary(stage, "PLAY_7_NETWORK_ERROR", exceptionChain, sanitizedMessage)
            )

            8 -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.CREDENTIAL_PROVIDER_UNAVAILABLE,
                authCode = "PLAY_8_INTERNAL_ERROR",
                exceptionClass = exceptionClass,
                headline = "Layanan Google Play Auth Mengalami Gangguan Internal (Kode 8)",
                reason = "Google Play Services mengembalikan status internal 8 saat memproses akun Google.",
                recoveryHint = "Coba ulangi ketuk Masuk dengan Google atau perbarui Google Play Services di perangkat.",
                technicalSummary = buildTechnicalSummary(stage, "PLAY_8_INTERNAL_ERROR", exceptionChain, sanitizedMessage)
            )

            16, 12501 -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.CANCELLED,
                authCode = "PLAY_${statusCode}_CANCELLED",
                exceptionClass = exceptionClass,
                headline = "Proses Login Google Dihentikan (Kode $statusCode)",
                reason = "Alur persetujuan akun Google dihentikan sebelum token diterbitkan (status $statusCode).",
                recoveryHint = "Ketuk Masuk dengan Google dan selesaikan pilihan akun pada dialog.",
                technicalSummary = buildTechnicalSummary(stage, "PLAY_${statusCode}_CANCELLED", exceptionChain, sanitizedMessage)
            )

            17, 12500, 12502 -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.PLAY_SERVICES_UNAVAILABLE,
                authCode = codeLabel,
                exceptionClass = exceptionClass,
                headline = "Google Sign-In API Belum Tersedia / Perlu Update (Kode $statusCode)",
                reason = "Google Play Services belum terhubung atau butuh pembaruan untuk menerbitkan token OAuth (status $statusCode).",
                recoveryHint = "Perbarui Google Play Services, pastikan ada akun Google aktif di perangkat, dan coba lagi.",
                technicalSummary = buildTechnicalSummary(stage, codeLabel, exceptionChain, sanitizedMessage)
            )

            28444 -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.FIREBASE_APP_NOT_AUTHORIZED,
                authCode = "PLAY_28444_CALLER_NOT_WHITELISTED",
                exceptionClass = exceptionClass,
                headline = "Package / Fingerprint Aplikasi Belum Diizinkan (Kode 28444)",
                reason = "Google Identity belum mengenali sertifikat penandatangan aplikasi ini untuk OAuth Client proyek (kode 28444).",
                recoveryHint = "Tambahkan SHA-1 & SHA-256 sertifikat aplikasi ke Firebase Console dan unduh ulang konfigurasi.",
                technicalSummary = buildTechnicalSummary(stage, "PLAY_28444_CALLER_NOT_WHITELISTED", exceptionChain, sanitizedMessage)
            )

            else -> AuthFailureDetail(
                stage = stage,
                category = AuthFailureCategory.UNKNOWN_CAUSE,
                authCode = codeLabel,
                exceptionClass = exceptionClass,
                headline = "Status Google Play Auth (Kode $statusCode)",
                reason = if (sanitizedMessage.isNotBlank()) {
                    "Google Play Services melaporkan status $statusCode: $sanitizedMessage"
                } else {
                    "Google Play Services mengembalikan status kode $statusCode pada tahap ${stage.label}."
                },
                recoveryHint = "Periksa akun Google aktif di perangkat, koneksi internet, dan SHA-1 aplikasi di Firebase Console.",
                technicalSummary = buildTechnicalSummary(stage, codeLabel, exceptionChain, sanitizedMessage)
            )
        }
    }

    internal fun extractPlayStatusCode(message: String): Int? {
        if (message.isBlank()) return null
        if (message.contains("DEVELOPER_ERROR", ignoreCase = true)) return 10
        if (message.contains("Caller not whitelisted", ignoreCase = true)) return 28444
        val match = PLAY_STATUS_CODE_REGEX.find(message)?.value?.toIntOrNull()
        return if (match != null && (
                message.contains("status", ignoreCase = true) ||
                    message.contains("code", ignoreCase = true) ||
                    message.contains(": $match") ||
                    message.contains("[$match]") ||
                    message.trimStart().startsWith("$match:")
                )
        ) {
            match
        } else {
            null
        }
    }

    internal fun sanitizeMessage(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(JWT_PATTERN, "[REDACTED_JWT]")
            .replace(ACCESS_TOKEN_PATTERN, "[REDACTED_TOKEN]")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .take(220)
    }

    private fun buildTechnicalSummary(
        stage: AuthStage,
        authCode: String,
        exceptionChain: String,
        sanitizedMessage: String
    ): String {
        val base = "[${stage.key}] $authCode • $exceptionChain"
        return if (sanitizedMessage.isNotBlank()) {
            "$base — $sanitizedMessage"
        } else {
            base
        }
    }

    fun logFailure(
        detail: AuthFailureDetail,
        throwable: Throwable? = null,
        level: Int = Log.ERROR,
        webClientIdConfigured: Boolean? = null,
        firebaseProjectId: String? = null,
        extraStages: List<String> = emptyList()
    ) {
        val payload = JSONObject().apply {
            put("stage", detail.stage.key)
            put("category", detail.category.name)
            put("authCode", detail.authCode)
            put("exceptionClass", detail.exceptionClass)
            put("headline", detail.headline)
            put("reason", detail.reason)
            put("technicalSummary", detail.technicalSummary)
            webClientIdConfigured?.let { put("webClientIdConfigured", it) }
            firebaseProjectId?.takeIf { it.isNotBlank() }?.let { put("firebaseProjectId", it) }
            if (extraStages.isNotEmpty()) {
                put("attemptedStages", JSONArray(extraStages))
            }
        }.toString()

        runCatching {
            when (level) {
                Log.INFO -> Log.i(LOG_TAG, payload)
                Log.WARN -> if (throwable != null) Log.w(LOG_TAG, payload, throwable) else Log.w(LOG_TAG, payload)
                else -> if (throwable != null) Log.e(LOG_TAG, payload, throwable) else Log.e(LOG_TAG, payload)
            }
        }
    }

    fun isFirebaseBackendUnavailableFailure(
        throwable: Throwable,
        detail: AuthFailureDetail
    ): Boolean {
        if (detail.category == AuthFailureCategory.FIREBASE_PROJECT_DELETED ||
            detail.category == AuthFailureCategory.FIREBASE_PROVIDER_DISABLED ||
            detail.category == AuthFailureCategory.FIREBASE_APP_NOT_AUTHORIZED ||
            detail.category == AuthFailureCategory.CONFIG_MISSING
        ) {
            return true
        }
        val fullMsg = generateSequence(throwable) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .uppercase()
        return fullMsg.contains("PROJECT_SOFT_DELETED") ||
            fullMsg.contains("PROJECT_DELETED") ||
            fullMsg.contains("PROJECT_NOT_FOUND") ||
            fullMsg.contains("CONFIGURATION_NOT_FOUND") ||
            fullMsg.contains("OPERATION_NOT_ALLOWED") ||
            fullMsg.contains("API_KEY_ANDROID_APP_BLOCKED")
    }

    fun logEvent(
        stage: AuthStage,
        event: String,
        details: Map<String, Any?> = emptyMap()
    ) {
        val payload = JSONObject().apply {
            put("stage", stage.key)
            put("event", event)
            details.forEach { (k, v) -> if (v != null) put(k, v) }
        }.toString()
        runCatching { Log.i(LOG_TAG, payload) }
    }
}
