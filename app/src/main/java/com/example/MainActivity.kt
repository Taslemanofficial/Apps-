package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.StreamRepository
import com.example.streaming.MultiStreamBroadcaster
import com.example.ui.auth.AuthScreen
import com.example.ui.components.LivePulseDot
import com.example.ui.screens.BroadcastStudioScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.NewsDeskScreen
import com.example.ui.screens.PlatformsScreen
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioVoid
import com.example.ui.theme.StreamCastTheme
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamCastTheme {
                AppNavigation()
            }
        }
    }
}

private enum class StudioTab(
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String
) {
    STUDIO(R.string.nav_studio, Icons.Rounded.Sensors, "nav_tab_studio"),
    NEWS_DESK(R.string.nav_news_desk, Icons.Rounded.Newspaper, "nav_tab_news_desk"),
    PLATFORMS(R.string.nav_platforms, Icons.Rounded.Hub, "nav_tab_platforms"),
    HISTORY(R.string.nav_history, Icons.Rounded.History, "nav_tab_history")
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            StreamCastConsole(
                currentUserId = user.uid,
                userDisplayName = user.displayName.orEmpty(),
                userEmail = user.email.orEmpty()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamCastConsole(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StudioViewModel(StreamRepository(db), currentUserId)
            }
        }
    )

    val destinationsState by viewModel.destinationsState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val newsPresetsState by viewModel.newsPresetsState.collectAsStateWithLifecycle()
    val newsConfig by viewModel.newsConfig.collectAsStateWithLifecycle()
    val channelLogoBitmap by viewModel.channelLogoBitmap.collectAsStateWithLifecycle()
    val ttsPresenterState by viewModel.ttsPresenterState.collectAsStateWithLifecycle()
    val isTickerPaused by viewModel.isTickerPaused.collectAsStateWithLifecycle()
    val telemetry by viewModel.broadcastTelemetry.collectAsStateWithLifecycle()
    val videoQueue by viewModel.videoQueue.collectAsStateWithLifecycle()
    val broadcastTitle by viewModel.broadcastTitle.collectAsStateWithLifecycle()
    val connectionTests by viewModel.connectionTests.collectAsStateWithLifecycle()
    val testingIds by viewModel.testingIds.collectAsStateWithLifecycle()
    val isAnalyzingVideos by viewModel.isAnalyzingVideos.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(StudioTab.STUDIO) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Return to primary Studio tab on system Back when on secondary tabs
    BackHandler(enabled = currentTab != StudioTab.STUDIO) {
        currentTab = StudioTab.STUDIO
    }

    // Request POST_NOTIFICATIONS at runtime on Android 13+ before starting foreground broadcast
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.startBroadcast(context)
    }

    fun triggerGoLive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.startBroadcast(context)
    }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    val accountLabel = userDisplayName.ifBlank { userEmail.substringBefore("@") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 640.dp

        Scaffold(
            containerColor = StudioVoid,
            contentWindowInsets = WindowInsets.statusBars,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = StudioSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CastConnected,
                                contentDescription = stringResource(R.string.cd_app_logo),
                                tint = LiveCrimson,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "StreamCast",
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (telemetry.isLive) {
                                Spacer(modifier = Modifier.width(10.dp))
                                LivePulseDot(color = LiveCrimson, animated = true)
                            }
                        }
                    },
                    actions = {
                        if (accountLabel.isNotBlank()) {
                            Text(
                                text = accountLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = { showSignOutDialog = true },
                            modifier = Modifier.testTag("sign_out_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = stringResource(R.string.cd_sign_out),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = StudioSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        StudioTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = LiveCrimson,
                                    selectedTextColor = LiveCrimson,
                                    indicatorColor = LiveCrimson.copy(alpha = 0.16f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = StudioSurface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        StudioTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = LiveCrimson,
                                    selectedTextColor = LiveCrimson,
                                    indicatorColor = LiveCrimson.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentTab) {
                        StudioTab.STUDIO -> BroadcastStudioScreen(
                            destinationsState = destinationsState,
                            telemetry = telemetry,
                            videoQueue = videoQueue,
                            broadcastTitle = broadcastTitle,
                            isAnalyzingVideos = isAnalyzingVideos,
                            newsConfig = newsConfig,
                            channelLogoBitmap = channelLogoBitmap,
                            ttsPresenterState = ttsPresenterState,
                            isTickerPaused = isTickerPaused,
                            onTitleChange = viewModel::updateBroadcastTitle,
                            onAddVideos = { uris -> viewModel.addVideos(context, uris) },
                            onRemoveVideo = viewModel::removeVideo,
                            onMoveVideoUp = viewModel::moveVideoUp,
                            onToggleLoop = viewModel::setLoopPlaylist,
                            onToggleDestination = viewModel::toggleDestination,
                            onOpenPlatformsTab = { currentTab = StudioTab.PLATFORMS },
                            onOpenNewsDeskTab = { currentTab = StudioTab.NEWS_DESK },
                            onTogglePreviewVoice = { viewModel.togglePreviewVoice(context) },
                            onAppendTickerItem = viewModel::appendLiveTickerItem,
                            onRemoveTickerItem = viewModel::removeLiveTickerItem,
                            onTickerSpeedChange = viewModel::setTickerSpeed,
                            onToggleTickerVisible = viewModel::setTickerVisible,
                            onToggleTickerPaused = viewModel::toggleTickerPaused,
                            onStartBroadcast = { triggerGoLive() },
                            onStopBroadcast = { viewModel.stopBroadcast(context) },
                            onToggleMute = viewModel::toggleMute
                        )
                        StudioTab.NEWS_DESK -> NewsDeskScreen(
                            newsConfig = newsConfig,
                            channelLogoBitmap = channelLogoBitmap,
                            activeVideo = videoQueue.getOrNull(telemetry.activeVideoIndex) ?: videoQueue.firstOrNull(),
                            ttsPresenterState = ttsPresenterState,
                            newsPresetsState = newsPresetsState,
                            isLive = telemetry.isLive,
                            onUpdateConfig = viewModel::updateNewsConfig,
                            onUploadLogo = { uri -> viewModel.uploadChannelLogo(context, uri) },
                            onClearLogo = viewModel::clearChannelLogo,
                            onTogglePreviewVoice = { viewModel.togglePreviewVoice(context) },
                            onSaveAndPush = viewModel::saveAndPushNewsUpdate,
                            onSaveAsBulletin = viewModel::saveAsBulletinPreset,
                            onApplyBulletin = { preset -> viewModel.applyBulletinPreset(preset, pushAndReadIfLive = true) },
                            onDeleteBulletin = viewModel::deleteBulletinPreset
                        )
                        StudioTab.PLATFORMS -> PlatformsScreen(
                            destinationsState = destinationsState,
                            connectionTests = connectionTests,
                            testingIds = testingIds,
                            isBroadcastLive = telemetry.isLive || telemetry.isStarting,
                            onSaveDestination = viewModel::saveDestination,
                            onToggleDestination = viewModel::toggleDestination,
                            onDeleteDestination = viewModel::deleteDestination,
                            onTestConnection = viewModel::testConnection
                        )
                        StudioTab.HISTORY -> HistoryScreen(
                            sessionsState = sessionsState,
                            onDeleteSession = viewModel::deleteSession
                        )
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            containerColor = StudioSurfaceElevated,
            title = { Text("অ্যাকাউন্ট থেকে সাইন আউট") },
            text = {
                Text(
                    text = if (userEmail.isNotBlank()) {
                        "$userEmail থেকে সাইন আউট করতে চান? চলমান কোনো ব্রডকাস্ট থাকলে তা বন্ধ হয়ে যাবে।"
                    } else {
                        "আপনি কি সাইন আউট করতে চান?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        MultiStreamBroadcaster.stopBroadcast(context)
                        signOut(credentialManager = credentialManager, scope = scope)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiveCrimson,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text(stringResource(R.string.action_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("বাতিল", color = SignalCyan)
                }
            }
        )
    }
}
