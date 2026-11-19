package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NewsDeskPreset
import com.example.data.TickerSpeed
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryTextStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live WYSIWYG TV News Broadcast Graphics Overlay rendered directly atop the 16:9 video preview:
 * - Top-Left: Channel Logo image (if uploaded) + Channel Name + Location tag
 * - Top-Right: LIVE badge + real-time digital clock + Presenter Audio indicator
 * - Lower-Third: Breaking News tag + Main Headline bar
 * - Bottom Edge: Smooth continuous scrolling news marquee ticker
 */
@Composable
fun NewsBroadcastOverlay(
    preset: NewsDeskPreset,
    logoBitmap: Bitmap?,
    isLive: Boolean,
    isPresenterSpeaking: Boolean,
    activeSentence: String,
    modifier: Modifier = Modifier,
    isTickerPaused: Boolean = false
) {
    var clockText by remember {
        mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (true) {
            clockText = fmt.format(Date())
            delay(1000L)
        }
    }

    val tickerItems = remember(preset.tickerText) {
        parseTickerItems(preset.tickerText)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // TOP BAR: Channel Logo Bug (left) + LIVE Clock & Voice Indicator (right)
        if (preset.showLogo) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Top-Left Channel Logo & Name Bug
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xD90B1324),
                    border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .clip(CircleShape)
                                .background(LiveCrimson)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (logoBitmap != null && !logoBitmap.isRecycled) {
                            Image(
                                bitmap = logoBitmap.asImageBitmap(),
                                contentDescription = preset.channelName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Column {
                            Text(
                                text = preset.channelName.ifBlank { "StreamNews 24" },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (preset.locationTag.isNotBlank()) {
                                Text(
                                    text = preset.locationTag,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Top-Right LIVE + Clock + Presenter Speaking Pill
                Surface(
                    shape = CircleShape,
                    color = Color(0xD9080E1A),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isLive) LiveCrimson else Color(0xFFB91C1C)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LivePulseDot(color = Color.White, animated = true)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isLive) "LIVE" else "PREVIEW",
                                    style = TelemetryTextStyle.copy(fontSize = 10.sp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = clockText,
                            style = TelemetryTextStyle.copy(fontSize = 10.sp),
                            color = Color.White
                        )
                        if (isPresenterSpeaking) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = SignalEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM STACK: Presenter Audio Caption + Breaking News Lower-Third + Scrolling Ticker
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            if (isPresenterSpeaking && activeSentence.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC063829),
                    border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                        .align(Alignment.Start)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = SignalEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = activeSentence,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Lower-Third Breaking News Headline Banner
            if (preset.showHeadline && preset.headline.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xEE0F172A), Color(0xDE1E293B))
                            )
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFDC2626), Color(0xFF991B1B))
                                )
                            )
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = preset.breakingTag.ifBlank { "ব্রেকিং নিউজ" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = preset.headline,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Continuous Scrolling News Ticker Overlay
            ScrollingNewsTickerOverlay(
                items = tickerItems,
                speed = preset.tickerSpeedEnum,
                badgeText = "সর্বশেষ",
                isLive = isLive,
                isPaused = isTickerPaused,
                visible = preset.showTicker,
                accentColor = SignalAmber
            )
        }
    }
}
