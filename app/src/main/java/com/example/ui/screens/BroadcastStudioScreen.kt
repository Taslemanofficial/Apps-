package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.NewsDeskPreset
import com.example.data.StreamDestination
import com.example.data.TickerSpeed
import com.example.streaming.BroadcastTelemetry
import com.example.streaming.PlatformLiveStats
import com.example.streaming.PlatformStreamStatus
import com.example.streaming.QueuedVideo
import com.example.streaming.TtsPresenterState
import com.example.ui.components.BroadcastControlPanel
import com.example.ui.components.DynamicTickerStudioController
import com.example.ui.components.LivePulseDot
import com.example.ui.components.NewsBroadcastOverlay
import com.example.ui.components.PlatformIconBadge
import com.example.ui.components.StudioWaveformCanvas
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.components.formatDurationMs
import com.example.ui.components.parseTickerItems
import com.example.ui.components.platformAccentColor
import com.example.ui.components.statusColor
import com.example.ui.components.statusLabelBn
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import com.example.ui.theme.TelemetryTextStyle
import com.example.ui.viewmodel.UiState

@Composable
fun BroadcastStudioScreen(
    destinationsState: UiState<List<StreamDestination>>,
    telemetry: BroadcastTelemetry,
    videoQueue: List<QueuedVideo>,
    broadcastTitle: String,
    isAnalyzingVideos: Boolean,
    newsConfig: NewsDeskPreset,
    channelLogoBitmap: Bitmap?,
    ttsPresenterState: TtsPresenterState,
    isTickerPaused: Boolean,
    onTitleChange: (String) -> Unit,
    onAddVideos: (List<Uri>) -> Unit,
    onRemoveVideo: (String) -> Unit,
    onMoveVideoUp: (Int) -> Unit,
    onToggleLoop: (Boolean) -> Unit,
    onToggleDestination: (StreamDestination, Boolean) -> Unit,
    onOpenPlatformsTab: () -> Unit,
    onOpenNewsDeskTab: () -> Unit,
    onTogglePreviewVoice: () -> Unit,
    onAppendTickerItem: (String) -> Unit,
    onRemoveTickerItem: (Int) -> Unit,
    onTickerSpeedChange: (TickerSpeed) -> Unit,
    onToggleTickerVisible: (Boolean) -> Unit,
    onToggleTickerPaused: () -> Unit,
    onStartBroadcast: () -> Unit,
    onStopBroadcast: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Zero-permission Android Photo Picker for multiple videos (Play Policy compliant)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddVideos(uris)
        }
    }

    // Secondary SAF document picker for videos in Downloads/Files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddVideos(uris)
        }
    }

    val destinations = (destinationsState as? UiState.Success)?.data.orEmpty()
    val readyCount = destinations.count { it.isReadyToBroadcast }
    val activeVideo = videoQueue.getOrNull(telemetry.activeVideoIndex) ?: videoQueue.firstOrNull()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 148.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Broadcast Video Stage & News TV Compositor Preview
            item(key = "broadcast_stage") {
                BroadcastStageCard(
                    activeVideo = activeVideo,
                    telemetry = telemetry,
                    readyPlatformCount = readyCount,
                    newsConfig = newsConfig,
                    channelLogoBitmap = channelLogoBitmap,
                    ttsPresenterState = ttsPresenterState,
                    isTickerPaused = isTickerPaused,
                    onPickVideo = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )
            }

            // 1B. Dynamic Live Scrolling Ticker Controller (populate/remove items on air)
            item(key = "dynamic_ticker_controller") {
                DynamicTickerStudioController(
                    items = parseTickerItems(newsConfig.tickerText),
                    speed = newsConfig.tickerSpeedEnum,
                    isTickerVisible = newsConfig.newsModeEnabled && newsConfig.showTicker,
                    isTickerPaused = isTickerPaused,
                    isLive = telemetry.isLive,
                    onAddItem = onAppendTickerItem,
                    onRemoveItem = onRemoveTickerItem,
                    onSpeedChange = onTickerSpeedChange,
                    onToggleVisible = onToggleTickerVisible,
                    onTogglePaused = onToggleTickerPaused
                )
            }

            // 1C. Quick News Desk Status & Instant On-Air Update Strip
            item(key = "news_desk_quick_strip") {
                NewsDeskQuickStatusCard(
                    newsConfig = newsConfig,
                    ttsPresenterState = ttsPresenterState,
                    isLive = telemetry.isLive,
                    onTogglePreviewVoice = onTogglePreviewVoice,
                    onOpenNewsDeskTab = onOpenNewsDeskTab
                )
            }

            // 2. Live System Notice Banner
            if (!telemetry.lastSystemNotice.isNullOrBlank()) {
                item(key = "system_notice") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (telemetry.isLive) SignalEmerald.copy(alpha = 0.14f) else StudioSurfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (telemetry.isLive) SignalEmerald.copy(alpha = 0.4f) else StudioOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LivePulseDot(
                                color = if (telemetry.isLive) SignalEmerald else SignalCyan,
                                animated = telemetry.isLive || telemetry.isStarting
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = telemetry.lastSystemNotice.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 3. Multi-Platform Simultaneous Destinations Strip
            item(key = "platforms_strip") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "মাল্টি-প্ল্যাটফর্ম রাউটিং",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${readyCount}টি প্ল্যাটফর্ম একসাথে লাইভের জন্য প্রস্তুত",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenPlatformsTab,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("manage_platforms_shortcut")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("কী সেটআপ", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    when (destinationsState) {
                        is UiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(92.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = LiveCrimson, modifier = Modifier.size(28.dp))
                            }
                        }
                        is UiState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = destinationsState.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                        is UiState.Success -> {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(destinationsState.data, key = { it.id }) { dest ->
                                    val liveStat = telemetry.platformStats[dest.id]
                                    PlatformRouteTile(
                                        destination = dest,
                                        liveStats = liveStat,
                                        isBroadcastRunning = telemetry.isLive || telemetry.isStarting,
                                        onToggle = { checked -> onToggleDestination(dest, checked) },
                                        onConfigureClick = onOpenPlatformsTab
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Broadcast Title Input
            item(key = "broadcast_title_input") {
                OutlinedTextField(
                    value = broadcastTitle,
                    onValueChange = onTitleChange,
                    enabled = !telemetry.isLive && !telemetry.isStarting,
                    label = { Text("ব্রডকাস্ট শিরোনাম (Broadcast Title)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LiveCrimson,
                        unfocusedBorderColor = StudioOutline,
                        focusedContainerColor = StudioSurface,
                        unfocusedContainerColor = StudioSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("broadcast_title_field")
                )
            }

            // 5. Video Upload & Broadcast Queue Header
            item(key = "queue_header") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, StudioOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "আপলোড ভিডিও কিউ (${videoQueue.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "MP4 (H.264 + AAC) ভিডিও এক বা একাধিক যুক্ত করুন",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Loop,
                                    contentDescription = null,
                                    tint = if (telemetry.loopPlaylist) SignalCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "লুপ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = telemetry.loopPlaylist,
                                    onCheckedChange = onToggleLoop,
                                    colors = SwitchDefaults.colors(checkedTrackColor = SignalCyan),
                                    modifier = Modifier.testTag("loop_playlist_switch")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                enabled = !telemetry.isLive && !telemetry.isStarting,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SignalCyan,
                                    contentColor = Color(0xFF021A2B)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("upload_video_gallery_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VideoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_upload_video),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    filePickerLauncher.launch(arrayOf("video/*"))
                                },
                                enabled = !telemetry.isLive && !telemetry.isStarting,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, StudioOutline),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("upload_video_files_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_browse_files),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        AnimatedVisibility(visible = isAnalyzingVideos) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = SignalCyan
                                )
                                Text(
                                    text = "ভিডিও কোডেক ও রেজোলিউশন বিশ্লেষণ করা হচ্ছে...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SignalCyan
                                )
                            }
                        }
                    }
                }
            }

            // 6. Queued Videos List or Empty State
            if (videoQueue.isEmpty()) {
                item(key = "empty_queue") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = StudioSurface.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, StudioOutline.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                            .testTag("empty_queue_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.UploadFile,
                                contentDescription = null,
                                tint = SignalCyan,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "এখনো কোনো ভিডিও যুক্ত করা হয়নি",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "গ্যালারি বা ফাইল থেকে ভিডিও সিলেক্ট করুন — সিস্টেমটি একসাথে YouTube, Facebook ও Instagram-এ রিয়েল-টাইমে স্ট্রিম করবে।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(videoQueue, key = { _, item -> item.id }) { index, video ->
                    QueuedVideoRow(
                        index = index,
                        video = video,
                        isCurrentlyStreaming = telemetry.isLive && telemetry.activeVideoIndex == index,
                        canEdit = !telemetry.isLive && !telemetry.isStarting,
                        onMoveUp = { onMoveVideoUp(index) },
                        onRemove = { onRemoveVideo(video.id) }
                    )
                }
            }
        }

        // Sticky Bottom Real-Time Broadcast Control Panel (Start, Stop, Mute)
        BroadcastControlPanel(
            telemetry = telemetry,
            readyPlatformCount = readyCount,
            queuedVideoCount = videoQueue.size,
            onStartClick = onStartBroadcast,
            onStopClick = onStopBroadcast,
            onToggleMute = onToggleMute,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun NewsDeskQuickStatusCard(
    newsConfig: NewsDeskPreset,
    ttsPresenterState: TtsPresenterState,
    isLive: Boolean,
    onTogglePreviewVoice: () -> Unit,
    onOpenNewsDeskTab: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StudioSurface,
        border = BorderStroke(
            1.dp,
            if (newsConfig.newsModeEnabled) LiveCrimson.copy(alpha = 0.45f) else StudioOutline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_news_desk_quick_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (ttsPresenterState.isSpeaking) Icons.Rounded.GraphicEq else Icons.Rounded.Newspaper,
                contentDescription = null,
                tint = if (newsConfig.newsModeEnabled) LiveCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (newsConfig.newsModeEnabled) {
                        "${newsConfig.channelName} • নিউজ ডেস্ক সক্রিয়"
                    } else {
                        "নিউজ ডেস্ক ওভারলে বন্ধ আছে"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        ttsPresenterState.isSpeaking -> "প্রেজেন্টার পড়ছেন: ${ttsPresenterState.currentSentence}"
                        newsConfig.newsModeEnabled && newsConfig.ttsEnabled ->
                            "শিরোনাম: ${newsConfig.headline.ifBlank { "সেট করা হয়নি" }}"
                        else -> "লোগো, স্ক্রল ও অটো অডিও সেটআপ করুন"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ttsPresenterState.isSpeaking) SignalEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!isLive && newsConfig.newsModeEnabled && newsConfig.ttsEnabled) {
                IconButton(
                    onClick = onTogglePreviewVoice,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("studio_quick_voice_preview")
                ) {
                    Icon(
                        imageVector = if (ttsPresenterState.isSpeaking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_preview_voice),
                        tint = if (ttsPresenterState.isSpeaking) LiveCrimson else SignalEmerald
                    )
                }
            }
            FilledTonalButton(
                onClick = onOpenNewsDeskTab,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("open_news_desk_shortcut")
            ) {
                Text(
                    text = if (isLive) "লাইভ আপডেট" else "নিউজ ডেস্ক",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun BroadcastStageCard(
    activeVideo: QueuedVideo?,
    telemetry: BroadcastTelemetry,
    readyPlatformCount: Int,
    newsConfig: NewsDeskPreset,
    channelLogoBitmap: Bitmap?,
    ttsPresenterState: TtsPresenterState,
    isTickerPaused: Boolean,
    onPickVideo: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = StudioVoid,
        border = BorderStroke(
            width = if (telemetry.isLive) 1.5.dp else 1.dp,
            color = if (telemetry.isLive) LiveCrimson else StudioOutline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (activeVideo != null) {
                key(activeVideo.id) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(activeVideo.uri)
                                setOnPreparedListener { mp ->
                                    mp.setVolume(0f, 0f)
                                    mp.isLooping = true
                                    if (telemetry.isLive) start() else seekTo(100)
                                }
                            }
                        },
                        update = { view ->
                            if (telemetry.isLive && !view.isPlaying) {
                                view.start()
                            } else if (!telemetry.isLive && view.isPlaying) {
                                view.pause()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                StudioWaveformCanvas(
                    isLive = false,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable(onClick = onPickVideo)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddCircleOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "ভিডিও আপলোড করে ব্রডকাস্ট প্রস্তুত করুন",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Text(
                        text = "YouTube • Facebook • Instagram",
                        style = TelemetryTextStyle,
                        color = SignalCyan
                    )
                }
            }

            if (newsConfig.newsModeEnabled) {
                // Full Broadcast TV News Graphics Overlay (Top Logo + LIVE Clock + Headline + Bottom Scrolling Ticker)
                NewsBroadcastOverlay(
                    preset = newsConfig,
                    logoBitmap = channelLogoBitmap,
                    isLive = telemetry.isLive,
                    isPresenterSpeaking = ttsPresenterState.isSpeaking,
                    activeSentence = ttsPresenterState.currentSentence,
                    isTickerPaused = isTickerPaused
                )
            } else {
                // Standard Telemetry HUD when News Overlay mode is disabled
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (telemetry.isLive) LiveCrimson else Color.Black.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LivePulseDot(
                                color = if (telemetry.isLive) Color.White else SignalCyan,
                                animated = telemetry.isLive || telemetry.isStarting
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    telemetry.isLive -> "ON AIR  ${formatDuration(telemetry.elapsedSec)}"
                                    telemetry.isStarting -> "CONNECTING"
                                    else -> "STANDBY • $readyPlatformCount CH"
                                },
                                style = TelemetryTextStyle,
                                color = Color.White
                            )
                        }
                    }

                    if (telemetry.isLive) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "${telemetry.totalBitrateKbps} kbps • ${telemetry.currentFps} FPS",
                                style = TelemetryTextStyle,
                                color = SignalEmerald,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (activeVideo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeVideo.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${activeVideo.width}×${activeVideo.height}",
                            style = TelemetryTextStyle,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformRouteTile(
    destination: StreamDestination,
    liveStats: PlatformLiveStats?,
    isBroadcastRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    onConfigureClick: () -> Unit
) {
    val accent = platformAccentColor(destination.platformType)
    val hasKey = destination.streamKey.isNotBlank()
    val isLive = liveStats?.status == PlatformStreamStatus.LIVE
    val isError = liveStats?.status == PlatformStreamStatus.ERROR

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StudioSurface,
        border = BorderStroke(
            width = if (isLive || destination.isReadyToBroadcast) 1.5.dp else 1.dp,
            color = when {
                isLive -> SignalEmerald
                isError -> LiveCrimson
                destination.isReadyToBroadcast -> accent.copy(alpha = 0.65f)
                else -> StudioOutline
            }
        ),
        modifier = Modifier
            .width(172.dp)
            .clickable {
                if (!hasKey) onConfigureClick() else if (!isBroadcastRunning) onToggle(!destination.enabled)
            }
            .testTag("route_tile_${destination.platform.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformIconBadge(type = destination.platformType, sizeDp = 34)
                if (isBroadcastRunning && liveStats != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LivePulseDot(
                            color = statusColor(liveStats.status),
                            animated = liveStats.status == PlatformStreamStatus.LIVE ||
                                liveStats.status == PlatformStreamStatus.CONNECTING
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = statusLabelBn(liveStats.status),
                            style = TelemetryTextStyle,
                            color = statusColor(liveStats.status)
                        )
                    }
                } else {
                    Switch(
                        checked = destination.enabled && hasKey,
                        onCheckedChange = { checked ->
                            if (!hasKey) onConfigureClick() else onToggle(checked)
                        },
                        enabled = !isBroadcastRunning,
                        colors = SwitchDefaults.colors(checkedTrackColor = accent)
                    )
                }
            }

            Text(
                text = destination.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = when {
                    liveStats != null && liveStats.status == PlatformStreamStatus.LIVE ->
                        "${liveStats.bitrateKbps} kbps • ${liveStats.latencyMs}ms"
                    liveStats != null && liveStats.status == PlatformStreamStatus.ERROR ->
                        liveStats.errorMessage ?: "সংযোগ ব্যর্থ"
                    !hasKey -> "Stream Key যুক্ত করুন →"
                    destination.enabled -> "ব্রডকাস্টের জন্য সক্রিয়"
                    else -> "টগল করে চালু করুন"
                },
                style = if (isLive) TelemetryTextStyle else MaterialTheme.typography.bodySmall,
                color = when {
                    isLive -> SignalEmerald
                    isError -> LiveCrimson
                    !hasKey -> SignalAmber
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QueuedVideoRow(
    index: Int,
    video: QueuedVideo,
    isCurrentlyStreaming: Boolean,
    canEdit: Boolean,
    onMoveUp: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrentlyStreaming) LiveCrimson.copy(alpha = 0.12f) else StudioSurface,
        border = BorderStroke(
            1.dp,
            if (isCurrentlyStreaming) LiveCrimson else StudioOutline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail with index badge
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (video.thumbnail != null) {
                    Image(
                        bitmap = video.thumbnail.asImageBitmap(),
                        contentDescription = video.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = SignalCyan
                    )
                }
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "#${index + 1}",
                        style = TelemetryTextStyle,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${video.width}×${video.height} • ${formatDurationMs(video.durationMs)}",
                        style = TelemetryTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (video.isH264Compatible) SignalEmerald.copy(alpha = 0.16f) else SignalAmber.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = if (video.isH264Compatible) "H.264" else "Check",
                            style = TelemetryTextStyle,
                            color = if (video.isH264Compatible) SignalEmerald else SignalAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            if (canEdit) {
                if (index > 0) {
                    IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("move_up_video_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(R.string.cd_move_up),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("remove_video_$index")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_remove_video),
                        tint = LiveCrimson
                    )
                }
            }
        }
    }
}
