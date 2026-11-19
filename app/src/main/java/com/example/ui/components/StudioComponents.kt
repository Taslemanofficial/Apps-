package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.data.PlatformType
import com.example.streaming.PlatformStreamStatus
import com.example.ui.theme.CustomRtmpCyan
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.InstagramMagenta
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.StudioVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.YouTubeRed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun platformAccentColor(type: PlatformType): Color = when (type) {
    PlatformType.YOUTUBE -> YouTubeRed
    PlatformType.FACEBOOK -> FacebookBlue
    PlatformType.INSTAGRAM -> InstagramMagenta
    PlatformType.CUSTOM -> CustomRtmpCyan
}

fun platformIcon(type: PlatformType): ImageVector = when (type) {
    PlatformType.YOUTUBE -> Icons.Rounded.PlayArrow
    PlatformType.FACEBOOK -> Icons.Rounded.Public
    PlatformType.INSTAGRAM -> Icons.Rounded.Videocam
    PlatformType.CUSTOM -> Icons.Rounded.Sensors
}

fun statusColor(status: PlatformStreamStatus): Color = when (status) {
    PlatformStreamStatus.LIVE -> SignalEmerald
    PlatformStreamStatus.CONNECTING, PlatformStreamStatus.RECONNECTING -> SignalAmber
    PlatformStreamStatus.ERROR -> LiveCrimson
    PlatformStreamStatus.IDLE, PlatformStreamStatus.STOPPED -> TextMuted
}

fun statusLabelBn(status: PlatformStreamStatus): String = when (status) {
    PlatformStreamStatus.LIVE -> "LIVE"
    PlatformStreamStatus.CONNECTING -> "সংযুক্ত হচ্ছে..."
    PlatformStreamStatus.RECONNECTING -> "পুনঃসংযোগ..."
    PlatformStreamStatus.ERROR -> "ত্রুটি"
    PlatformStreamStatus.STOPPED -> "বন্ধ"
    PlatformStreamStatus.IDLE -> "প্রস্তুত"
}

@Composable
fun PlatformIconBadge(
    type: PlatformType,
    sizeDp: Int = 38
) {
    val accent = platformAccentColor(type)
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = platformIcon(type),
            contentDescription = type.displayName,
            tint = accent,
            modifier = Modifier.size((sizeDp * 0.54f).dp)
        )
    }
}

@Composable
fun LivePulseDot(
    color: Color,
    animated: Boolean,
    modifier: Modifier = Modifier
) {
    if (animated) {
        val infinite = rememberInfiniteTransition(label = "live_pulse")
        val alpha by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(750, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        Box(
            modifier = modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
    } else {
        Box(
            modifier = modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun PlatformMiniPill(platformRaw: String) {
    val type = PlatformType.fromRaw(platformRaw)
    val accent = platformAccentColor(type)
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = platformIcon(type),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = when (type) {
                    PlatformType.YOUTUBE -> "YouTube"
                    PlatformType.FACEBOOK -> "Facebook"
                    PlatformType.INSTAGRAM -> "Instagram"
                    PlatformType.CUSTOM -> "RTMP"
                },
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
    }
}

/**
 * Procedural dark studio broadcast optical rays & animated signal waveform Canvas.
 * Uses DrawScope size properties directly for responsive rendering across orientations.
 */
@Composable
fun StudioWaveformCanvas(
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "studio_wave")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isLive) 2600 else 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.5f, h * 0.48f)
        val baseRadius = minOf(w, h) * 0.24f

        // Deep studio backdrop
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF171E36),
                    StudioVoid
                ),
                center = center,
                radius = maxOf(w, h) * 0.72f
            )
        )

        // Converging platform light beams (YouTube Crimson, Facebook Blue, Instagram Magenta)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(YouTubeRed.copy(alpha = 0.0f), YouTubeRed.copy(alpha = 0.35f)),
                start = Offset(0f, 0f),
                end = center
            ),
            start = Offset(w * 0.05f, 0f),
            end = center,
            strokeWidth = 3f
        )
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(FacebookBlue.copy(alpha = 0.0f), FacebookBlue.copy(alpha = 0.35f)),
                start = Offset(w, 0f),
                end = center
            ),
            start = Offset(w * 0.95f, 0f),
            end = center,
            strokeWidth = 3f
        )
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(InstagramMagenta.copy(alpha = 0.0f), InstagramMagenta.copy(alpha = 0.35f)),
                start = Offset(w, h),
                end = center
            ),
            start = Offset(w * 0.92f, h),
            end = center,
            strokeWidth = 3f
        )
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(SignalCyan.copy(alpha = 0.0f), SignalCyan.copy(alpha = 0.28f)),
                start = Offset(0f, h),
                end = center
            ),
            start = Offset(w * 0.08f, h),
            end = center,
            strokeWidth = 3f
        )

        // Concentric broadcast rings
        for (ring in 1..3) {
            val r = baseRadius * (0.78f + ring * 0.32f)
            drawCircle(
                color = if (isLive) LiveCrimson.copy(alpha = 0.22f / ring) else SignalCyan.copy(alpha = 0.14f / ring),
                radius = r,
                center = center,
                style = Stroke(width = 2f)
            )
        }

        // Animated circular audio/signal waveform ring
        val wavePath = Path()
        val points = 72
        for (i in 0..points) {
            val angle = (i.toFloat() / points) * (2.0 * PI).toFloat()
            val amp = if (isLive) 12f else 5f
            val mod = sin(angle * 6f + phase) * amp + cos(angle * 3f - phase * 1.3f) * (amp * 0.5f)
            val r = baseRadius + mod
            val x = center.x + cos(angle) * r
            val y = center.y + sin(angle) * r
            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
        }
        wavePath.close()

        drawPath(
            path = wavePath,
            brush = Brush.sweepGradient(
                colors = listOf(LiveCrimson, SignalCyan, InstagramMagenta, FacebookBlue, LiveCrimson),
                center = center
            ),
            style = Stroke(width = if (isLive) 4f else 2.5f)
        )
    }
}

fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hrs = safe / 3600
    val mins = (safe % 3600) / 60
    val secs = safe % 60
    return if (hrs > 0) "%02d:%02d:%02d".format(hrs, mins, secs) else "%02d:%02d".format(mins, secs)
}

fun formatDurationMs(durationMs: Long): String = formatDuration(durationMs / 1000L)

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}
