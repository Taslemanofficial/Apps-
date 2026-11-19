package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.streaming.BroadcastTelemetry
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TelemetryTextStyle

/**
 * Real-time Live Broadcast Control Panel with dedicated Start, Stop, and Mute controls
 * plus live telemetry status (state, duration, bitrate, bytes sent, and mute indicator).
 */
@Composable
fun BroadcastControlPanel(
    telemetry: BroadcastTelemetry,
    readyPlatformCount: Int,
    queuedVideoCount: Int,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLive = telemetry.isLive
    val isStarting = telemetry.isStarting
    val isMuted = telemetry.isMuted

    val panelBorderColor by animateColorAsState(
        targetValue = when {
            isLive && isMuted -> SignalAmber.copy(alpha = 0.8f)
            isLive -> LiveCrimson.copy(alpha = 0.7f)
            isStarting -> SignalCyan.copy(alpha = 0.7f)
            else -> StudioOutline
        },
        animationSpec = tween(240),
        label = "control_panel_border"
    )

    val muteContainerColor by animateColorAsState(
        targetValue = if (isMuted) SignalAmber else StudioSurfaceElevated,
        animationSpec = tween(220),
        label = "mute_container_color"
    )
    val muteContentColor by animateColorAsState(
        targetValue = if (isMuted) Color(0xFF0B1120) else Color.White,
        animationSpec = tween(220),
        label = "mute_content_color"
    )

    Surface(
        color = StudioSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.2.dp, panelBorderColor),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("broadcast_control_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Telemetry Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    LivePulseDot(
                        color = when {
                            isLive -> LiveCrimson
                            isStarting -> SignalCyan
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animated = isLive || isStarting
                    )
                    Text(
                        text = when {
                            isLive -> "ON AIR  ${formatDuration(telemetry.elapsedSec)}"
                            isStarting -> "CONNECTING..."
                            else -> "STANDBY • ${readyPlatformCount} CH • ${queuedVideoCount} VID"
                        },
                        style = TelemetryTextStyle,
                        color = when {
                            isLive -> LiveCrimson
                            isStarting -> SignalCyan
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isMuted) {
                        Surface(
                            shape = CircleShape,
                            color = SignalAmber.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, SignalAmber.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "MUTED",
                                style = TelemetryTextStyle,
                                color = SignalAmber,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Text(
                    text = if (isLive) {
                        "${telemetry.totalBitrateKbps} kbps • ${formatBytes(telemetry.totalBytesSent)}"
                    } else {
                        "RTMPS Engine"
                    },
                    style = TelemetryTextStyle,
                    color = if (isLive) SignalEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // 3-Button Real-Time Broadcast Action Deck: Start | Stop | Mute
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // START Button
                Button(
                    onClick = onStartClick,
                    enabled = !isLive && !isStarting,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiveCrimson,
                        contentColor = Color.White,
                        disabledContainerColor = LiveCrimson.copy(alpha = 0.22f),
                        disabledContentColor = Color.White.copy(alpha = 0.48f)
                    ),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(52.dp)
                        .testTag("go_live_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_control_start),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_start_stream),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // STOP Button
                Button(
                    onClick = onStopClick,
                    enabled = isLive || isStarting,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White,
                        disabledContainerColor = StudioSurfaceElevated.copy(alpha = 0.7f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("stop_broadcast_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = stringResource(R.string.cd_control_stop),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_stop_stream),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // MUTE / UNMUTE Button
                FilledTonalButton(
                    onClick = onToggleMute,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isMuted) SignalAmber else StudioOutline
                    ),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = muteContainerColor,
                        contentColor = muteContentColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("mute_broadcast_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.cd_control_mute),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMuted) {
                            stringResource(R.string.action_unmute_stream)
                        } else {
                            stringResource(R.string.action_mute_stream)
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
