package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.auth.AuthFailureDetail
import com.example.ui.auth.LocalGoogleSession
import com.example.ui.auth.LocalGoogleSessionStore
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.components.AiRobotMascot
import com.example.ui.components.AldiLogoClapperboard
import com.example.ui.theme.BrandNeonGradient
import com.example.ui.theme.CosmicBackground
import com.example.ui.theme.CosmicOutline
import com.example.ui.theme.CosmicSurface
import com.example.ui.theme.CosmicSurfaceElevated
import com.example.ui.theme.FuturisticCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioAmber

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onLocalModeSuccess: (LocalGoogleSession) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isCheckingSession by remember { mutableStateOf(true) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authFailureDetail by remember { mutableStateOf<AuthFailureDetail?>(null) }
    var showTechnicalDetails by remember { mutableStateOf(false) }
    var copiedDiagnostic by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSession = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingSession = false
            },
            scope = scope,
            onAuthDiagnostic = { detail ->
                authFailureDetail = detail
            },
            onLocalGoogleSignIn = { localSession ->
                isCheckingSession = false
                onLocalModeSuccess(localSession)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CosmicBackground, Color(0xFF130B2E), CosmicBackground)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AldiLogoClapperboard(modifier = Modifier.size(40.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${stringResource(R.string.splash_brand_prefix)} ",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.splash_brand_accent),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                brush = BrandNeonGradient
                            )
                        )
                    }
                }

                AiRobotMascot(modifier = Modifier.size(110.dp))

                Text(
                    text = stringResource(R.string.auth_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.auth_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AuthFeatureRow(
                        icon = Icons.Filled.AutoAwesome,
                        text = stringResource(R.string.auth_feature_gemini)
                    )
                    AuthFeatureRow(
                        icon = Icons.Filled.CloudDone,
                        text = stringResource(R.string.auth_feature_cloud)
                    )
                    AuthFeatureRow(
                        icon = Icons.Filled.VerifiedUser,
                        text = stringResource(R.string.auth_feature_security)
                    )
                }

                authFailureDetail?.let { detail ->
                    AuthDiagnosticCard(
                        detail = detail,
                        expanded = showTechnicalDetails,
                        copied = copiedDiagnostic,
                        onToggleExpanded = { showTechnicalDetails = !showTechnicalDetails },
                        onCopyLog = {
                            clipboardManager.setText(AnnotatedString(detail.toCopyableDiagnosticReport()))
                            copiedDiagnostic = true
                        },
                        onContinueLocalMode = {
                            val session = LocalGoogleSession(
                                displayName = "Kreator Studio (Lokal)",
                                accountLabel = "",
                                noticeMessage = "Mode Studio Lokal aktif (${detail.authCode}) • Semua fitur edit & ekspor MP4 siap dipakai."
                            )
                            LocalGoogleSessionStore.save(context, session)
                            onLocalModeSuccess(session)
                        }
                    )
                }

                Button(
                    onClick = {
                        authFailureDetail = null
                        showTechnicalDetails = false
                        copiedDiagnostic = false
                        isSigningIn = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isSigningIn = false
                                onAuthSuccess()
                            },
                            onAuthError = { detail ->
                                isSigningIn = false
                                authFailureDetail = detail
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isSigningIn = false
                            },
                            onLocalGoogleSignIn = { localSession ->
                                isSigningIn = false
                                onLocalModeSuccess(localSession)
                            }
                        )
                    },
                    enabled = !isSigningIn && !isCheckingSession,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("google_sign_in_button")
                ) {
                    if (isSigningIn || isCheckingSession) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isCheckingSession) {
                                stringResource(R.string.auth_checking_session)
                            } else {
                                stringResource(R.string.auth_signing_in)
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.auth_google_cta),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthDiagnosticCard(
    detail: AuthFailureDetail,
    expanded: Boolean,
    copied: Boolean,
    onToggleExpanded: () -> Unit,
    onCopyLog: () -> Unit,
    onContinueLocalMode: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CosmicBackground.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.72f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_diagnostic_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = detail.headline,
                        tint = StudioAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = detail.headline,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StudioAmber.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = detail.authCode,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = StudioAmber,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("auth_diagnostic_code")
                    )
                }
            }

            Text(
                text = "${stringResource(R.string.auth_diagnostic_stage_prefix)} ${detail.stage.label} • ${detail.category.label}",
                style = MaterialTheme.typography.labelSmall,
                color = FuturisticCyan
            )

            Text(
                text = detail.reason,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.testTag("auth_diagnostic_reason")
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CosmicSurfaceElevated.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, CosmicOutline.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${stringResource(R.string.auth_diagnostic_recovery_prefix)} ${detail.recoveryHint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.84f),
                    modifier = Modifier.padding(10.dp)
                )
            }

            OutlinedButton(
                onClick = onContinueLocalMode,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, FuturisticCyan.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("auth_local_mode_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.MovieCreation,
                    contentDescription = null,
                    tint = FuturisticCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.auth_continue_local_studio),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = FuturisticCyan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onToggleExpanded)
                        .padding(vertical = 4.dp)
                        .testTag("auth_diagnostic_toggle")
                ) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.auth_diagnostic_hide_details)
                        } else {
                            stringResource(R.string.auth_diagnostic_show_details)
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = FuturisticCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = FuturisticCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                TextButton(
                    onClick = onCopyLog,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("auth_copy_log_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.auth_diagnostic_copy_log),
                        tint = FuturisticCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (copied) {
                            stringResource(R.string.auth_diagnostic_copied)
                        } else {
                            stringResource(R.string.auth_diagnostic_copy_log)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = FuturisticCyan
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF05070E),
                    border = BorderStroke(1.dp, CosmicOutline.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_technical_details")
                ) {
                    Text(
                        text = detail.technicalSummary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthFeatureRow(
    icon: ImageVector,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CosmicBackground.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, CosmicOutline.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FuturisticCyan,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}
