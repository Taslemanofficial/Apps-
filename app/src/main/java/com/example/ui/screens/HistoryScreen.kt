package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BroadcastSession
import com.example.data.SessionStatus
import com.example.ui.components.PlatformMiniPill
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TelemetryTextStyle
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    sessionsState: UiState<List<BroadcastSession>>,
    onDeleteSession: (BroadcastSession) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "history_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "ব্রডকাস্ট হিস্ট্রি ও অ্যানালিটিক্স",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "আপনার প্রতিটি মাল্টি-প্ল্যাটফর্ম লাইভ সেশনের রিয়েল-টাইম রেকর্ড ক্লাউডে সংরক্ষিত থাকে",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (sessionsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = LiveCrimson)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = sessionsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val sessions = sessionsState.data
                val totalDuration = sessions.sumOf { it.durationSec }
                val totalBytes = sessions.sumOf { it.totalBytesSent }

                item(key = "summary_metrics") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            label = "মোট লাইভ",
                            value = "${sessions.size}",
                            accent = LiveCrimson,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "মোট সময়",
                            value = formatDuration(totalDuration),
                            accent = SignalCyan,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "ডেটা ট্রান্সফার",
                            value = formatBytes(totalBytes),
                            accent = SignalEmerald,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (sessions.isEmpty()) {
                    item(key = "empty_history") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = StudioSurface,
                            border = BorderStroke(1.dp, StudioOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = SignalCyan,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "এখনো কোনো লাইভ ব্রডকাস্ট হিস্ট্রি নেই",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "স্টুডিও ট্যাব থেকে আপনার প্রথম মাল্টি-প্ল্যাটফর্ম লাইভ চালু করলে এখানে সম্পূর্ণ রিপোর্ট দেখা যাবে।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(sessions, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            onDelete = { onDeleteSession(session) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioOutline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(
    session: BroadcastSession,
    onDelete: () -> Unit
) {
    val statusColor = when (session.sessionStatus) {
        SessionStatus.LIVE -> LiveCrimson
        SessionStatus.COMPLETED -> SignalEmerald
        SessionStatus.FAILED -> SignalAmber
    }
    val statusText = when (session.sessionStatus) {
        SessionStatus.LIVE -> "LIVE"
        SessionStatus.COMPLETED -> "সম্পন্ন"
        SessionStatus.FAILED -> "ব্যর্থ"
    }
    val dateStr = session.createdAt?.toDate()?.let { date ->
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(date)
    } ?: SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$dateStr • ${session.videoCount} ভিডিও",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = statusText,
                            style = TelemetryTextStyle,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_session_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete_session),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                session.platforms.forEach { platform ->
                    PlatformMiniPill(platformRaw = platform)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StudioSurfaceElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "সময়: ${formatDuration(session.durationSec)}",
                        style = TelemetryTextStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "গড়: ${session.avgBitrateKbps} kbps",
                        style = TelemetryTextStyle,
                        color = SignalCyan
                    )
                    Text(
                        text = formatBytes(session.totalBytesSent),
                        style = TelemetryTextStyle,
                        color = SignalEmerald
                    )
                }
            }
        }
    }
}
