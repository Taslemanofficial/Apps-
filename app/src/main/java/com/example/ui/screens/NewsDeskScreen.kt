package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.NewsDeskPreset
import com.example.data.TickerSpeed
import com.example.data.TtsLanguage
import com.example.streaming.QueuedVideo
import com.example.streaming.TtsPresenterState
import com.example.ui.components.LivePulseDot
import com.example.ui.components.NewsBroadcastOverlay
import com.example.ui.components.StudioWaveformCanvas
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import com.example.ui.theme.TelemetryTextStyle
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.UiState
import java.util.Locale

private val QUICK_BREAKING_TAGS = listOf(
    "ব্রেকিং নিউজ",
    "সর্বশেষ",
    "সরাসরি",
    "বিশেষ সংবাদ",
    "BREAKING"
)

@Composable
fun NewsDeskScreen(
    newsConfig: NewsDeskPreset,
    channelLogoBitmap: Bitmap?,
    activeVideo: QueuedVideo?,
    ttsPresenterState: TtsPresenterState,
    newsPresetsState: UiState<List<NewsDeskPreset>>,
    isLive: Boolean,
    onUpdateConfig: ((NewsDeskPreset) -> NewsDeskPreset) -> Unit,
    onUploadLogo: (Uri) -> Unit,
    onClearLogo: () -> Unit,
    onTogglePreviewVoice: () -> Unit,
    onSaveAndPush: (Boolean) -> Unit,
    onSaveAsBulletin: () -> Unit,
    onApplyBulletin: (NewsDeskPreset) -> Unit,
    onDeleteBulletin: (NewsDeskPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    // Zero-permission Android Photo Picker for Channel Logo (Play Policy compliant)
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onUploadLogo(uri)
    }

    val savedBulletins = (newsPresetsState as? UiState.Success)?.data
        .orEmpty()
        .filter { it.id != StudioViewModel.ACTIVE_DESK_DOC_ID }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live WYSIWYG TV News Preview Monitor
        item(key = "news_preview_monitor") {
            NewsPreviewMonitorCard(
                newsConfig = newsConfig,
                channelLogoBitmap = channelLogoBitmap,
                activeVideo = activeVideo,
                ttsPresenterState = ttsPresenterState,
                isLive = isLive
            )
        }

        // 2. Master News Mode & Auto Presenter Toggles
        item(key = "news_master_switches") {
            DeskSectionCard(
                title = "নিউজ ব্রডকাস্ট কন্ট্রোল",
                subtitle = if (isLive) "লাইভ চালু আছে — যেকোনো পরিবর্তন সরাসরি সম্প্রচারে আপডেট হবে" else "আপলোড ভিডিও ফুটেজের ওপর নিউজ ওভারলে ও অডিও যুক্ত করুন"
            ) {
                DeskToggleRow(
                    label = "নিউজ ওভারলে ও অডিও লাইভে যুক্ত করুন",
                    description = "উপরে লোগো, শিরোনাম ও নিচের স্ক্রলিং টিকার সরাসরি ভিডিওর ওপর কম্পোজিট হবে",
                    checked = newsConfig.newsModeEnabled,
                    accentColor = LiveCrimson,
                    tag = "news_mode_master_switch",
                    onCheckedChange = { enabled -> onUpdateConfig { it.copy(newsModeEnabled = enabled) } }
                )
                DeskToggleRow(
                    label = "অটো নিউজ প্রেজেন্টার ভয়েস (TTS)",
                    description = "নিউজ স্ক্রিপ্ট লিখলে অটোমেটিক নিউজ প্রেজেন্টার পড়ে শোনাবে ও লাইভে যুক্ত করবে",
                    checked = newsConfig.ttsEnabled,
                    accentColor = SignalEmerald,
                    tag = "news_tts_master_switch",
                    onCheckedChange = { enabled -> onUpdateConfig { it.copy(ttsEnabled = enabled) } }
                )
            }
        }

        // 3. Automated News Presenter Audio (TTS Script + Voice Controls)
        item(key = "news_presenter_audio_card") {
            NewsPresenterAudioCard(
                newsConfig = newsConfig,
                ttsPresenterState = ttsPresenterState,
                isLive = isLive,
                onUpdateConfig = onUpdateConfig,
                onTogglePreviewVoice = onTogglePreviewVoice,
                onPushAndRead = { onSaveAndPush(true) }
            )
        }

        // 4. Breaking News Headline & Bottom Scrolling Ticker
        item(key = "news_headline_ticker_card") {
            NewsHeadlineTickerCard(
                newsConfig = newsConfig,
                isLive = isLive,
                onUpdateConfig = onUpdateConfig,
                onPushGraphicsUpdate = { onSaveAndPush(false) }
            )
        }

        // 5. Top Channel Logo, Brand Name & Overlay Layer Toggles
        item(key = "news_logo_branding_card") {
            ChannelLogoBrandingCard(
                newsConfig = newsConfig,
                channelLogoBitmap = channelLogoBitmap,
                onUpdateConfig = onUpdateConfig,
                onPickLogo = {
                    logoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClearLogo = onClearLogo
            )
        }

        // 6. Cloud-Saved News Bulletins Section
        item(key = "saved_bulletins_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "সংরক্ষিত নিউজ বুলেটিন (${savedBulletins.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "এক ট্যাপে আগের স্ক্রিপ্ট ও শিরোনাম লাইভে লোড করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onSaveAsBulletin,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("save_new_bulletin_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = null,
                        tint = SignalCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("বুলেটিন সেভ", color = SignalCyan, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (savedBulletins.isEmpty()) {
            item(key = "saved_bulletins_empty") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StudioSurface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, StudioOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "এখনো কোনো বুলেটিন প্রিসেট সেভ করা হয়নি। উপরের 'বুলেটিন সেভ' বাটনে ট্যাপ করে বর্তমান শিরোনাম, টিকার ও স্ক্রিপ্ট ক্লাউডে সংরক্ষণ করুন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(savedBulletins, key = { it.id }) { preset ->
                SavedBulletinRow(
                    preset = preset,
                    isLive = isLive,
                    onApply = { onApplyBulletin(preset) },
                    onDelete = { onDeleteBulletin(preset) }
                )
            }
        }
    }
}

@Composable
private fun NewsPreviewMonitorCard(
    newsConfig: NewsDeskPreset,
    channelLogoBitmap: Bitmap?,
    activeVideo: QueuedVideo?,
    ttsPresenterState: TtsPresenterState,
    isLive: Boolean
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = StudioVoid,
        border = BorderStroke(if (isLive) 1.5.dp else 1.dp, if (isLive) LiveCrimson else StudioOutline),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .testTag("news_desk_preview_monitor")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (activeVideo?.thumbnail != null && !activeVideo.thumbnail.isRecycled) {
                Image(
                    bitmap = activeVideo.thumbnail.asImageBitmap(),
                    contentDescription = activeVideo.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                StudioWaveformCanvas(isLive = isLive, modifier = Modifier.fillMaxSize())
            }

            if (newsConfig.newsModeEnabled) {
                NewsBroadcastOverlay(
                    preset = newsConfig,
                    logoBitmap = channelLogoBitmap,
                    isLive = isLive,
                    isPresenterSpeaking = ttsPresenterState.isSpeaking,
                    activeSentence = ttsPresenterState.currentSentence
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "নিউজ ওভারলে বন্ধ আছে — নিচের সুইচ চালু করুন",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsPresenterAudioCard(
    newsConfig: NewsDeskPreset,
    ttsPresenterState: TtsPresenterState,
    isLive: Boolean,
    onUpdateConfig: ((NewsDeskPreset) -> NewsDeskPreset) -> Unit,
    onTogglePreviewVoice: () -> Unit,
    onPushAndRead: () -> Unit
) {
    DeskSectionCard(
        title = "অটো নিউজ প্রেজেন্টার অডিও (TTS)",
        subtitle = "সংবাদের লেখা দিন — সিস্টেম অটোমেটিক পড়ে ভিডিওর সাথে লাইভে যুক্ত করবে"
    ) {
        OutlinedTextField(
            value = newsConfig.newsScript,
            onValueChange = { txt -> onUpdateConfig { it.copy(newsScript = txt.take(2500)) } },
            label = { Text("নিউজ স্ক্রিপ্ট (Presenter Script)") },
            placeholder = { Text("এখানে সংবাদের বিস্তারিত লিখুন...") },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(14.dp),
            colors = deskFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("news_script_input")
        )

        // Language chips (Bangla / English)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.RecordVoiceOver,
                contentDescription = null,
                tint = SignalEmerald,
                modifier = Modifier.size(18.dp)
            )
            TtsLanguage.entries.forEach { lang ->
                val selected = newsConfig.languageEnum == lang
                FilterChip(
                    selected = selected,
                    onClick = { onUpdateConfig { it.copy(ttsLanguage = lang.name) } },
                    label = { Text(lang.displayName, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SignalEmerald.copy(alpha = 0.2f),
                        selectedLabelColor = SignalEmerald
                    ),
                    modifier = Modifier.testTag("tts_lang_${lang.name.lowercase()}")
                )
            }
        }

        // Speed & Pitch sliders
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "গতি: ${String.format(Locale.US, "%.2fx", newsConfig.ttsSpeed)}",
                    style = TelemetryTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = newsConfig.ttsSpeed.toFloat().coerceIn(0.75f, 1.4f),
                    onValueChange = { v -> onUpdateConfig { it.copy(ttsSpeed = v.toDouble()) } },
                    valueRange = 0.75f..1.4f,
                    colors = SliderDefaults.colors(thumbColor = SignalCyan, activeTrackColor = SignalCyan),
                    modifier = Modifier.testTag("tts_speed_slider")
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ভয়েস টোন: ${String.format(Locale.US, "%.2f", newsConfig.ttsPitch)}",
                    style = TelemetryTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = newsConfig.ttsPitch.toFloat().coerceIn(0.8f, 1.2f),
                    onValueChange = { v -> onUpdateConfig { it.copy(ttsPitch = v.toDouble()) } },
                    valueRange = 0.8f..1.2f,
                    colors = SliderDefaults.colors(thumbColor = SignalEmerald, activeTrackColor = SignalEmerald),
                    modifier = Modifier.testTag("tts_pitch_slider")
                )
            }
        }

        // Active Presenter Speaking Status Pill
        if (ttsPresenterState.isSpeaking || !ttsPresenterState.statusNote.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (ttsPresenterState.isSpeaking) SignalEmerald.copy(alpha = 0.14f) else SignalAmber.copy(alpha = 0.14f),
                border = BorderStroke(
                    1.dp,
                    if (ttsPresenterState.isSpeaking) SignalEmerald.copy(alpha = 0.5f) else SignalAmber.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = if (ttsPresenterState.isSpeaking) SignalEmerald else SignalAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (ttsPresenterState.isSpeaking) {
                            "প্রেজেন্টার পড়ছেন (${ttsPresenterState.activeChunkIndex}/${ttsPresenterState.totalChunks.coerceAtLeast(1)}): ${ttsPresenterState.currentSentence}"
                        } else {
                            ttsPresenterState.statusNote.orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Action Buttons: Voice Preview + Push & Read Live
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onTogglePreviewVoice,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (ttsPresenterState.isSpeaking) LiveCrimson else SignalEmerald),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("preview_voice_button")
            ) {
                Icon(
                    imageVector = if (ttsPresenterState.isSpeaking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_preview_voice),
                    tint = if (ttsPresenterState.isSpeaking) LiveCrimson else SignalEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (ttsPresenterState.isSpeaking) "থামান" else "ভয়েস প্রিভিউ",
                    color = if (ttsPresenterState.isSpeaking) LiveCrimson else SignalEmerald,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onPushAndRead,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiveCrimson,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1.25f)
                    .height(48.dp)
                    .testTag("push_and_read_live_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Campaign,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isLive) "লাইভে আপডেট ও পড়ুন" else "সেভ ও প্রস্তুত করুন",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NewsHeadlineTickerCard(
    newsConfig: NewsDeskPreset,
    isLive: Boolean,
    onUpdateConfig: ((NewsDeskPreset) -> NewsDeskPreset) -> Unit,
    onPushGraphicsUpdate: () -> Unit
) {
    DeskSectionCard(
        title = "শিরোনাম ও নিচের স্ক্রলিং টিকার",
        subtitle = "ব্রেকিং নিউজ ট্যাগ, মূল শিরোনাম এবং নিচে চলমান স্ক্রলিং লেখা নিয়ন্ত্রণ করুন"
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(QUICK_BREAKING_TAGS) { tagText ->
                val selected = newsConfig.breakingTag == tagText
                FilterChip(
                    selected = selected,
                    onClick = { onUpdateConfig { it.copy(breakingTag = tagText) } },
                    label = { Text(tagText, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LiveCrimson.copy(alpha = 0.22f),
                        selectedLabelColor = LiveCrimson
                    )
                )
            }
        }

        OutlinedTextField(
            value = newsConfig.headline,
            onValueChange = { txt -> onUpdateConfig { it.copy(headline = txt.take(160)) } },
            label = { Text("মূল শিরোনাম (Lower-Third Headline)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = deskFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("news_headline_input")
        )

        OutlinedTextField(
            value = newsConfig.tickerText,
            onValueChange = { txt -> onUpdateConfig { it.copy(tickerText = txt.take(1000)) } },
            label = { Text("নিচের স্ক্রলিং লেখা (Bottom Scrolling Ticker)") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(14.dp),
            colors = deskFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("news_ticker_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "স্ক্রল স্পিড:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TickerSpeed.entries.forEach { spd ->
                    val selected = newsConfig.tickerSpeedEnum == spd
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateConfig { it.copy(tickerSpeed = spd.name) } },
                        label = { Text(spd.labelBn, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SignalAmber.copy(alpha = 0.22f),
                            selectedLabelColor = SignalAmber
                        ),
                        modifier = Modifier.testTag("ticker_speed_${spd.name.lowercase()}")
                    )
                }
            }
        }

        Button(
            onClick = onPushGraphicsUpdate,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SignalCyan,
                contentColor = Color(0xFF021A2B)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("push_news_graphics_button")
        ) {
            Icon(imageVector = Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isLive) "শিরোনাম ও স্ক্রল লাইভে আপডেট করুন" else "নিউজ ওভারলে সেভ করুন",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChannelLogoBrandingCard(
    newsConfig: NewsDeskPreset,
    channelLogoBitmap: Bitmap?,
    onUpdateConfig: ((NewsDeskPreset) -> NewsDeskPreset) -> Unit,
    onPickLogo: () -> Unit,
    onClearLogo: () -> Unit
) {
    DeskSectionCard(
        title = "উপরের চ্যানেল লোগো ও ডিসপ্লে লেয়ার",
        subtitle = "PNG/JPG লোগো আপলোড করুন এবং প্রতিটি ওভারলে লেয়ার আলাদাভাবে নিয়ন্ত্রণ করুন"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, StudioOutline),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (channelLogoBitmap != null && !channelLogoBitmap.isRecycled) {
                        Image(
                            bitmap = channelLogoBitmap.asImageBitmap(),
                            contentDescription = newsConfig.channelName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Newspaper,
                            contentDescription = null,
                            tint = SignalCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Button(
                onClick = onPickLogo,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioSurfaceElevated,
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.6f)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("upload_channel_logo_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = stringResource(R.string.cd_upload_logo),
                    tint = SignalCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (channelLogoBitmap != null) "লোগো পরিবর্তন" else "চ্যানেল লোগো আপলোড",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (channelLogoBitmap != null) {
                IconButton(
                    onClick = onClearLogo,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("clear_channel_logo_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_clear_logo),
                        tint = LiveCrimson
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = newsConfig.channelName,
                onValueChange = { txt -> onUpdateConfig { it.copy(channelName = txt.take(40)) } },
                label = { Text("চ্যানেলের নাম") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = deskFieldColors(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("channel_name_input")
            )
            OutlinedTextField(
                value = newsConfig.locationTag,
                onValueChange = { txt -> onUpdateConfig { it.copy(locationTag = txt.take(40)) } },
                label = { Text("লোকেশন ট্যাগ") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = deskFieldColors(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("channel_location_input")
            )
        }

        DeskToggleRow(
            label = "উপরের লোগো ও লাইভ ঘড়ি",
            description = null,
            checked = newsConfig.showLogo,
            accentColor = SignalCyan,
            tag = "toggle_show_logo",
            onCheckedChange = { v -> onUpdateConfig { it.copy(showLogo = v) } }
        )
        DeskToggleRow(
            label = "ব্রেকিং নিউজ শিরোনাম বার",
            description = null,
            checked = newsConfig.showHeadline,
            accentColor = LiveCrimson,
            tag = "toggle_show_headline",
            onCheckedChange = { v -> onUpdateConfig { it.copy(showHeadline = v) } }
        )
        DeskToggleRow(
            label = "নিচের স্ক্রলিং টিকার",
            description = null,
            checked = newsConfig.showTicker,
            accentColor = SignalAmber,
            tag = "toggle_show_ticker",
            onCheckedChange = { v -> onUpdateConfig { it.copy(showTicker = v) } }
        )
    }
}

@Composable
private fun SavedBulletinRow(
    preset: NewsDeskPreset,
    isLive: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = LiveCrimson.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = preset.breakingTag.ifBlank { "ব্রেকিং" },
                            style = MaterialTheme.typography.labelSmall,
                            color = LiveCrimson,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = preset.channelName,
                        style = TelemetryTextStyle,
                        color = SignalCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = preset.headline.ifBlank { "শিরোনামহীন বুলেটিন" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (preset.newsScript.isNotBlank()) {
                    Text(
                        text = preset.newsScript,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onApply,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLive) LiveCrimson else SignalCyan,
                    contentColor = if (isLive) Color.White else Color(0xFF021A2B)
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("apply_bulletin_${preset.id}")
            ) {
                Text(
                    text = if (isLive) "লাইভে লোড" else "লোড",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_bulletin_${preset.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_bulletin),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeskSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LivePulseDot(color = LiveCrimson, animated = false)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun DeskToggleRow(
    label: String,
    description: String?,
    checked: Boolean,
    accentColor: Color,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accentColor),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun deskFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SignalCyan,
    unfocusedBorderColor = StudioOutline,
    focusedContainerColor = StudioVoid,
    unfocusedContainerColor = StudioVoid
)
