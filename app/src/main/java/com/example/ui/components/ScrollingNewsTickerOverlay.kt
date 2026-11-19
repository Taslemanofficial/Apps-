package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.TickerSpeed
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import com.example.ui.theme.TelemetryTextStyle
import kotlinx.coroutines.delay

/**
 * Splits a raw multi-line or bullet-delimited ticker string into individual headline items.
 */
fun parseTickerItems(rawTickerText: String): List<String> =
    rawTickerText
        .split("\n", "•", "★", "|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/**
 * Joins structured ticker items back into a single broadcast-ready ticker string.
 */
fun formatTickerItems(items: List<String>): String =
    items.map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" • ")

/**
 * Reusable broadcast-grade Scrolling News Ticker Overlay component.
 *
 * Designed to sit along the lower edge of a live video preview or broadcast stage and
 * dynamically reflect new headline items pushed during a live broadcast with:
 * - Animated show/hide slide transition
 * - Dynamic update pulse highlight when new content arrives on air
 * - Left & right soft gradient fade masks over continuous marquee scrolling
 * - Configurable scroll velocity (SLOW / NORMAL / FAST) and live pause/resume state
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollingNewsTickerOverlay(
    items: List<String>,
    modifier: Modifier = Modifier,
    speed: TickerSpeed = TickerSpeed.NORMAL,
    badgeText: String = "সর্বশেষ",
    isLive: Boolean = false,
    isPaused: Boolean = false,
    visible: Boolean = true,
    accentColor: Color = SignalAmber
) {
    val cleanItems = remember(items) {
        items.map { it.trim() }.filter { it.isNotEmpty() }
    }

    // Briefly pulse the ticker accent hairline whenever new content is pushed dynamically on air
    var justUpdated by remember { mutableStateOf(false) }
    LaunchedEffect(cleanItems) {
        if (cleanItems.isNotEmpty()) {
            justUpdated = true
            delay(650L)
            justUpdated = false
        }
    }

    val animatedAccent by animateColorAsState(
        targetValue = when {
            justUpdated -> LiveCrimson
            isPaused -> SignalCyan
            else -> accentColor
        },
        animationSpec = tween(durationMillis = 280),
        label = "ticker_accent_color"
    )

    val marqueeVelocity = when (speed) {
        TickerSpeed.SLOW -> 42.dp
        TickerSpeed.NORMAL -> 68.dp
        TickerSpeed.FAST -> 108.dp
    }

    val scrollingLine = remember(cleanItems) {
        if (cleanItems.isEmpty()) {
            ""
        } else {
            val joined = cleanItems.joinToString("     ★     ")
            "$joined     ★     $joined"
        }
    }

    AnimatedVisibility(
        visible = visible && cleanItems.isNotEmpty(),
        enter = fadeIn(tween(200)) + slideInVertically(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { it / 2 }
        ),
        exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier
            .fillMaxWidth()
            .testTag("scrolling_news_ticker_overlay")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top luminous broadcast accent hairline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(animatedAccent, animatedAccent.copy(alpha = 0.7f), LiveCrimson)
                        )
                    )
            )

            // Main dark obsidian ticker track + anchored category ribbon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xF5070B14)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Anchored left ticker badge
                Row(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(animatedAccent, animatedAccent.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isLive && !isPaused) {
                        LivePulseDot(color = Color(0xFF0B1120), animated = true)
                    }
                    Text(
                        text = if (isPaused) "বিরতি" else badgeText.ifBlank { "সর্বশেষ" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0B1120),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                // Marquee viewport with edge fade scrims
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 5.dp)
                ) {
                    key(scrollingLine, speed, isPaused) {
                        Text(
                            text = scrollingLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFF8FAFC),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                                .basicMarquee(
                                    iterations = if (isPaused) 0 else Int.MAX_VALUE,
                                    velocity = marqueeVelocity,
                                    initialDelayMillis = 0
                                )
                                .testTag("scrolling_news_ticker_text")
                        )
                    }

                    // Soft edge fade masks for crisp broadcast transitions
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(12.dp)
                            .height(20.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xF5070B14), Color.Transparent)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(16.dp)
                            .height(20.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color(0xF5070B14))
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * Interactive on-air controller allowing broadcasters to dynamically add, remove, pause,
 * toggle, and adjust the speed of scrolling news ticker items without leaving the live stage.
 */
@Composable
fun DynamicTickerStudioController(
    items: List<String>,
    speed: TickerSpeed,
    isTickerVisible: Boolean,
    isTickerPaused: Boolean,
    isLive: Boolean,
    onAddItem: (String) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onSpeedChange: (TickerSpeed) -> Unit,
    onToggleVisible: (Boolean) -> Unit,
    onTogglePaused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draftText by rememberSaveable { mutableStateOf("") }

    fun submitDraft() {
        val trimmed = draftText.trim()
        if (trimmed.isNotEmpty()) {
            onAddItem(trimmed)
            draftText = ""
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = StudioSurface,
        border = BorderStroke(
            1.dp,
            if (isLive && isTickerVisible) SignalAmber.copy(alpha = 0.55f) else StudioOutline
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("dynamic_ticker_controller_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Title + Active count + Pause + Show/Hide switch
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
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = null,
                        tint = if (isTickerVisible) SignalAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "লাইভ স্ক্রলিং টিকার (${items.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isLive && isTickerVisible) {
                                Surface(
                                    shape = CircleShape,
                                    color = SignalEmerald.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "ON AIR",
                                        style = TelemetryTextStyle,
                                        color = SignalEmerald,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isLive) "লাইভ চলাকালীন সরাসরি নতুন খবর যুক্ত বা মুছুন" else "ব্রডকাস্টের নিচে চলমান স্ক্রলিং খবর যুক্ত করুন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onTogglePaused,
                    enabled = isTickerVisible && items.isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("ticker_pause_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isTickerPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = stringResource(R.string.cd_pause_ticker),
                        tint = if (isTickerPaused) SignalCyan else MaterialTheme.colorScheme.onSurface
                    )
                }

                Switch(
                    checked = isTickerVisible,
                    onCheckedChange = onToggleVisible,
                    colors = SwitchDefaults.colors(checkedTrackColor = SignalAmber),
                    modifier = Modifier.testTag("studio_ticker_visibility_switch")
                )
            }

            // Dynamic text input + instant push button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it.take(180) },
                    placeholder = {
                        Text(
                            text = "নতুন স্ক্রলিং খবর লিখুন...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitDraft() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SignalAmber,
                        unfocusedBorderColor = StudioOutline,
                        focusedContainerColor = StudioVoid,
                        unfocusedContainerColor = StudioVoid
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dynamic_ticker_input")
                )

                Button(
                    onClick = { submitDraft() },
                    enabled = draftText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalAmber,
                        contentColor = Color(0xFF0B1120)
                    ),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("dynamic_ticker_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_add_ticker_item),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isLive) "পুশ" else "যুক্ত",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Active scrolling ticker items (removable live chips)
            if (items.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dynamic_ticker_items_row")
                ) {
                    itemsIndexed(items, key = { idx, item -> "${idx}_${item.hashCode()}" }) { index, item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = StudioSurfaceElevated,
                            border = BorderStroke(1.dp, StudioOutline)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = "#${index + 1} $item",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 210.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable { onRemoveItem(index) }
                                        .testTag("remove_ticker_item_$index"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_remove_ticker_item),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Speed controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "স্ক্রল স্পিড:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TickerSpeed.entries.forEach { spd ->
                        FilterChip(
                            selected = speed == spd,
                            onClick = { onSpeedChange(spd) },
                            label = { Text(spd.labelBn, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SignalAmber.copy(alpha = 0.22f),
                                selectedLabelColor = SignalAmber
                            ),
                            modifier = Modifier.testTag("studio_ticker_speed_${spd.name.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}
