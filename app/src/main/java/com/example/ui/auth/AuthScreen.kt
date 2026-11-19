package com.example.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.ui.components.StudioWaveformCanvas
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.InstagramMagenta
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import com.example.ui.theme.YouTubeRed

/**
 * Simple Firebase Authentication login screen powered by Jetpack Credential Manager (Google Sign-In).
 * Gates access to the multi-platform broadcast dashboard until the user signs in.
 */
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isCheckingSession by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val cancelledNotice = stringResource(R.string.auth_cancelled_notice)

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
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioVoid)
    ) {
        StudioWaveformCanvas(
            isLive = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .align(Alignment.TopCenter)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            StudioVoid.copy(alpha = 0.88f),
                            StudioVoid
                        ),
                        startY = 100f,
                        endY = 860f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(max = 480.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Brand emblem
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(LiveCrimson, Color(0xFFFF6B4A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CastConnected,
                    contentDescription = stringResource(R.string.cd_app_logo),
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "StreamCast",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Supported live platforms row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuthPlatformChip("YouTube", YouTubeRed, Icons.Rounded.PlayArrow)
                AuthPlatformChip("Facebook", FacebookBlue, Icons.Rounded.Public)
                AuthPlatformChip("Instagram", InstagramMagenta, Icons.Rounded.Videocam)
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Login Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = StudioSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_card")
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.auth_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "ব্রডকাস্ট ড্যাশবোর্ডে প্রবেশ করতে আপনার Google অ্যাকাউন্ট দিয়ে লগইন করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Concise capability checklist
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LoginBenefitRow("Simultaneous RTMP / RTMPS multi-streaming")
                        LoginBenefitRow("Cloud-synced stream keys & broadcast history")
                        LoginBenefitRow("Protected per-user Firebase Authentication")
                    }

                    AnimatedVisibility(visible = statusMessage != null) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isError) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    StudioSurfaceElevated
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_status_banner")
                            ) {
                                Text(
                                    text = statusMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isError) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = {
                            statusMessage = null
                            isError = false
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    isError = true
                                    statusMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isLoading = false
                                    isError = false
                                    statusMessage = cancelledNotice
                                }
                            )
                        },
                        enabled = !isLoading && !isCheckingSession,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LiveCrimson,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isLoading || isCheckingSession) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isCheckingSession) {
                                    stringResource(R.string.auth_checking_session)
                                } else {
                                    stringResource(R.string.auth_sign_in_google)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.auth_sign_in_google),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = SignalCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.auth_secure_sync_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LoginBenefitRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircleOutline,
            contentDescription = null,
            tint = SignalEmerald,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AuthPlatformChip(
    label: String,
    accent: Color,
    icon: ImageVector
) {
    Surface(
        shape = CircleShape,
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
