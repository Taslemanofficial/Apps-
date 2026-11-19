package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlatformType
import com.example.data.StreamDestination
import com.example.streaming.ConnectionTestResult
import com.example.ui.components.PlatformIconBadge
import com.example.ui.components.platformAccentColor
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TelemetryTextStyle
import com.example.ui.viewmodel.UiState

@Composable
fun PlatformsScreen(
    destinationsState: UiState<List<StreamDestination>>,
    connectionTests: Map<String, ConnectionTestResult>,
    testingIds: Set<String>,
    isBroadcastLive: Boolean,
    onSaveDestination: (
        destinationId: String?,
        platform: PlatformType,
        label: String,
        rtmpUrl: String,
        streamKey: String,
        enabled: Boolean
    ) -> Unit,
    onToggleDestination: (StreamDestination, Boolean) -> Unit,
    onDeleteDestination: (StreamDestination) -> Unit,
    onTestConnection: (StreamDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddCustomDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "platforms_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "লাইভ প্ল্যাটফর্ম সেটআপ",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "YouTube, Facebook ও Instagram-এর Stream Key সেভ করুন ও কানেকশন টেস্ট করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { showAddCustomDialog = true },
                    enabled = !isBroadcastLive,
                    modifier = Modifier.testTag("add_custom_rtmp_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_add_platform),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Custom")
                }
            }
        }

        when (destinationsState) {
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
                            text = destinationsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                items(destinationsState.data, key = { it.id }) { destination ->
                    PlatformConfigCard(
                        destination = destination,
                        testResult = connectionTests[destination.id],
                        isTesting = testingIds.contains(destination.id),
                        isBroadcastLive = isBroadcastLive,
                        onSave = { label, url, key, enabled ->
                            onSaveDestination(
                                destination.id,
                                destination.platformType,
                                label,
                                url,
                                key,
                                enabled
                            )
                        },
                        onToggle = { enabled -> onToggleDestination(destination, enabled) },
                        onTest = { onTestConnection(destination) },
                        onDelete = if (destination.platformType == PlatformType.CUSTOM) {
                            { onDeleteDestination(destination) }
                        } else null
                    )
                }
            }
        }
    }

    if (showAddCustomDialog) {
        AddCustomPlatformDialog(
            onDismiss = { showAddCustomDialog = false },
            onConfirm = { label, url, key ->
                onSaveDestination(null, PlatformType.CUSTOM, label, url, key, key.isNotBlank())
                showAddCustomDialog = false
            }
        )
    }
}

@Composable
private fun PlatformConfigCard(
    destination: StreamDestination,
    testResult: ConnectionTestResult?,
    isTesting: Boolean,
    isBroadcastLive: Boolean,
    onSave: (label: String, rtmpUrl: String, streamKey: String, enabled: Boolean) -> Unit,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val accent = platformAccentColor(destination.platformType)
    val clipboard = LocalClipboardManager.current

    var label by rememberSaveable(destination.id, destination.label) { mutableStateOf(destination.label) }
    var rtmpUrl by rememberSaveable(destination.id, destination.rtmpUrl) { mutableStateOf(destination.rtmpUrl) }
    var streamKey by rememberSaveable(destination.id, destination.streamKey) { mutableStateOf(destination.streamKey) }
    var showKey by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    val hasChanges = label.trim() != destination.label ||
        rtmpUrl.trim() != destination.rtmpUrl ||
        streamKey.trim() != destination.streamKey

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = StudioSurface,
        border = BorderStroke(
            width = if (destination.isReadyToBroadcast) 1.5.dp else 1.dp,
            color = if (destination.isReadyToBroadcast) accent.copy(alpha = 0.65f) else StudioOutline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    PlatformIconBadge(type = destination.platformType, sizeDp = 42)
                    Column {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (destination.rtmpUrl.startsWith("rtmps://")) "RTMPS (TLS এনক্রিপ্টেড)" else "RTMP 1.0",
                            style = TelemetryTextStyle,
                            color = SignalCyan
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null && !isBroadcastLive) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "প্ল্যাটফর্ম মুছুন",
                                tint = LiveCrimson
                            )
                        }
                    }
                    Switch(
                        checked = destination.enabled && destination.streamKey.isNotBlank(),
                        onCheckedChange = onToggle,
                        enabled = !isBroadcastLive,
                        colors = SwitchDefaults.colors(checkedTrackColor = accent),
                        modifier = Modifier.testTag("platform_switch_${destination.platform.lowercase()}")
                    )
                }
            }

            // RTMP Server URL
            OutlinedTextField(
                value = rtmpUrl,
                onValueChange = { rtmpUrl = it },
                enabled = !isBroadcastLive,
                label = { Text("RTMP / RTMPS Server URL") },
                singleLine = true,
                textStyle = TelemetryTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = StudioOutline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rtmp_url_${destination.platform.lowercase()}")
            )

            // Stream Key Input (masked with visibility + paste)
            OutlinedTextField(
                value = streamKey,
                onValueChange = { streamKey = it },
                enabled = !isBroadcastLive,
                label = { Text("Stream Key (গোপন কী)") },
                placeholder = { Text("এখানে Stream Key পেস্ট করুন") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                streamKey = it
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "ক্লিপবোর্ড থেকে পেস্ট করুন",
                                tint = SignalCyan
                            )
                        }
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(R.string.cd_toggle_key_visibility),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = StudioOutline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stream_key_${destination.platform.lowercase()}")
            )

            // Action Buttons: Save + Real RTMP Server Connection Test
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onSave(
                            label.ifBlank { destination.platformType.displayName },
                            rtmpUrl,
                            streamKey,
                            streamKey.isNotBlank()
                        )
                    },
                    enabled = !isBroadcastLive && (hasChanges || (streamKey.isNotBlank() && !destination.enabled)),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_platform_${destination.platform.lowercase()}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("সেভ করুন", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onTest,
                    enabled = !isTesting && rtmpUrl.startsWith("rtmp"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StudioOutline),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("test_platform_${destination.platform.lowercase()}")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = SignalCyan
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.NetworkCheck,
                            contentDescription = null,
                            tint = SignalCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTesting) "টেস্ট চলছে..." else "সার্ভার টেস্ট",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Real Connection Test Result Banner
            if (testResult != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (testResult.reachable) SignalEmerald.copy(alpha = 0.14f) else LiveCrimson.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (testResult.reachable) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = if (testResult.reachable) SignalEmerald else LiveCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (testResult.reachable) {
                                "${testResult.serverMessage} • পিং: ${testResult.latencyMs}ms"
                            } else {
                                testResult.serverMessage
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Expandable Step-by-Step Bangla Setup Guide
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioSurfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showGuide = !showGuide }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.HelpOutline,
                                contentDescription = stringResource(R.string.cd_expand_guide),
                                tint = SignalCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = destination.platformType.keyHelpTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (showGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(visible = showGuide) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            destination.platformType.keyHelpSteps.forEach { step ->
                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCustomPlatformDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, url: String, key: String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("rtmp://") }
    var key by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = { Text("নতুন Custom RTMP যোগ করুন") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("প্ল্যাটফর্মের নাম (যেমন: Twitch / TikTok)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("RTMP / RTMPS URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Stream Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isNotBlank() && (url.startsWith("rtmp://") || url.startsWith("rtmps://"))) {
                        onConfirm(label.trim(), url.trim(), key.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
            ) {
                Text("যোগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}
