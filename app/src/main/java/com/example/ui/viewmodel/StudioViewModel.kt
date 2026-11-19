package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BroadcastSession
import com.example.data.NewsDeskPreset
import com.example.data.PlatformType
import com.example.data.SessionStatus
import com.example.data.StreamDestination
import com.example.data.StreamRepository
import com.example.data.TickerSpeed
import com.example.streaming.BroadcastTelemetry
import com.example.streaming.ConnectionTestResult
import com.example.streaming.MultiStreamBroadcaster
import com.example.streaming.NewsTtsEngine
import com.example.streaming.QueuedVideo
import com.example.streaming.RtmpClient
import com.example.streaming.TtsPresenterState
import com.example.streaming.VideoDemuxer
import com.example.ui.components.formatTickerItems
import com.example.ui.components.parseTickerItems
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class StudioViewModel(
    private val repository: StreamRepository,
    private val currentUserId: String
) : ViewModel() {

    val destinationsState: StateFlow<UiState<List<StreamDestination>>> =
        repository.observeDestinations(currentUserId)
            .map<List<StreamDestination>, UiState<List<StreamDestination>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing destinations", rootError)
                emit(UiState.Error(rootError.message ?: "প্ল্যাটফর্ম তালিকা লোড করা যায়নি।"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val sessionsState: StateFlow<UiState<List<BroadcastSession>>> =
        repository.observeSessions(currentUserId)
            .map<List<BroadcastSession>, UiState<List<BroadcastSession>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing sessions", rootError)
                emit(UiState.Error(rootError.message ?: "ব্রডকাস্ট হিস্ট্রি লোড করা যায়নি।"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val newsPresetsState: StateFlow<UiState<List<NewsDeskPreset>>> =
        repository.observeNewsPresets(currentUserId)
            .map<List<NewsDeskPreset>, UiState<List<NewsDeskPreset>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing news presets", rootError)
                emit(UiState.Error(rootError.message ?: "নিউজ ডেস্ক প্রিসেট লোড করা যায়নি।"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val broadcastTelemetry: StateFlow<BroadcastTelemetry> = MultiStreamBroadcaster.telemetry
    val ttsPresenterState: StateFlow<TtsPresenterState> = NewsTtsEngine.state

    private val _newsConfig = MutableStateFlow(NewsDeskPreset(id = ACTIVE_DESK_DOC_ID, userId = currentUserId))
    val newsConfig: StateFlow<NewsDeskPreset> = _newsConfig.asStateFlow()

    private val _channelLogoBitmap = MutableStateFlow<Bitmap?>(null)
    val channelLogoBitmap: StateFlow<Bitmap?> = _channelLogoBitmap.asStateFlow()

    private val _isTickerPaused = MutableStateFlow(false)
    val isTickerPaused: StateFlow<Boolean> = _isTickerPaused.asStateFlow()

    private val _videoQueue = MutableStateFlow<List<QueuedVideo>>(emptyList())
    val videoQueue: StateFlow<List<QueuedVideo>> = _videoQueue.asStateFlow()

    private val _broadcastTitle = MutableStateFlow("লাইভ নিউজ ডেস্ক ব্রডকাস্ট")
    val broadcastTitle: StateFlow<String> = _broadcastTitle.asStateFlow()

    private val _connectionTests = MutableStateFlow<Map<String, ConnectionTestResult>>(emptyMap())
    val connectionTests: StateFlow<Map<String, ConnectionTestResult>> = _connectionTests.asStateFlow()

    private val _testingIds = MutableStateFlow<Set<String>>(emptySet())
    val testingIds: StateFlow<Set<String>> = _testingIds.asStateFlow()

    private val _isAnalyzingVideos = MutableStateFlow(false)
    val isAnalyzingVideos: StateFlow<Boolean> = _isAnalyzingVideos.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private var activeSessionDocId: String? = null
    private var hydratedActiveNewsDesk = false

    init {
        viewModelScope.launch {
            repository.ensureDefaultDestinations(currentUserId)
        }
        // Hydrate the user's saved News Desk config from Firestore on first emission
        viewModelScope.launch {
            newsPresetsState.collect { state ->
                if (!hydratedActiveNewsDesk && state is UiState.Success) {
                    val savedActive = state.data.firstOrNull { it.id == ACTIVE_DESK_DOC_ID }
                    if (savedActive != null) {
                        _newsConfig.value = savedActive
                        MultiStreamBroadcaster.updateLiveNewsOverlay(savedActive, _channelLogoBitmap.value)
                    }
                    hydratedActiveNewsDesk = true
                }
            }
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun updateBroadcastTitle(newTitle: String) {
        _broadcastTitle.value = newTitle.take(120)
    }

    fun setLoopPlaylist(enabled: Boolean) {
        MultiStreamBroadcaster.setLoopPlaylist(enabled)
    }

    // --- NEWS DESK CONTROLS (LIVE ON-AIR UPDATES + FIRESTORE PERSISTENCE) ---

    fun updateNewsConfig(transform: (NewsDeskPreset) -> NewsDeskPreset) {
        val updated = transform(_newsConfig.value)
        _newsConfig.value = updated
        // Push immediately to live overlay compositor so changes show instantaneously on air!
        MultiStreamBroadcaster.updateLiveNewsOverlay(updated, _channelLogoBitmap.value)
    }

    /**
     * Dynamically appends a new scrolling news item during a live broadcast,
     * pushing immediately to the active RTMP overlay compositor and persisting to Firestore.
     */
    fun appendLiveTickerItem(itemText: String) {
        val clean = itemText.trim()
        if (clean.isEmpty()) return
        val currentItems = parseTickerItems(_newsConfig.value.tickerText)
        val combined = formatTickerItems(currentItems + clean).take(1000)
        val updated = _newsConfig.value.copy(
            tickerText = combined,
            showTicker = true,
            newsModeEnabled = true
        )
        _newsConfig.value = updated
        _isTickerPaused.value = false
        MultiStreamBroadcaster.updateLiveNewsOverlay(updated, _channelLogoBitmap.value)
        persistActiveNewsConfigSilently(updated)
        if (broadcastTelemetry.value.isLive) {
            _bannerMessage.value = "নতুন স্ক্রলিং খবর সরাসরি লাইভে যুক্ত হয়েছে!"
        }
    }

    /**
     * Dynamically removes an item from the scrolling news ticker during a broadcast.
     */
    fun removeLiveTickerItem(index: Int) {
        val currentItems = parseTickerItems(_newsConfig.value.tickerText).toMutableList()
        if (index !in currentItems.indices) return
        currentItems.removeAt(index)
        val updated = _newsConfig.value.copy(tickerText = formatTickerItems(currentItems))
        _newsConfig.value = updated
        MultiStreamBroadcaster.updateLiveNewsOverlay(updated, _channelLogoBitmap.value)
        persistActiveNewsConfigSilently(updated)
    }

    fun toggleTickerPaused() {
        _isTickerPaused.update { !it }
    }

    fun setTickerSpeed(speed: TickerSpeed) {
        val updated = _newsConfig.value.copy(tickerSpeed = speed.name)
        _newsConfig.value = updated
        MultiStreamBroadcaster.updateLiveNewsOverlay(updated, _channelLogoBitmap.value)
        persistActiveNewsConfigSilently(updated)
    }

    fun setTickerVisible(visible: Boolean) {
        val updated = _newsConfig.value.copy(
            showTicker = visible,
            newsModeEnabled = if (visible) true else _newsConfig.value.newsModeEnabled
        )
        _newsConfig.value = updated
        MultiStreamBroadcaster.updateLiveNewsOverlay(updated, _channelLogoBitmap.value)
        persistActiveNewsConfigSilently(updated)
    }

    private fun persistActiveNewsConfigSilently(config: NewsDeskPreset) {
        viewModelScope.launch {
            repository.saveNewsPreset(currentUserId, ACTIVE_DESK_DOC_ID, config)
        }
    }

    fun uploadChannelLogo(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val decoded = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        val raw = BitmapFactory.decodeStream(stream) ?: return@use null
                        val maxDim = 220
                        val scale = minOf(maxDim.toFloat() / raw.width, maxDim.toFloat() / raw.height, 1f)
                        val w = (raw.width * scale).toInt().coerceAtLeast(32)
                        val h = (raw.height * scale).toInt().coerceAtLeast(32)
                        val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                        if (scaled !== raw) raw.recycle()
                        scaled
                    }
                }
                if (decoded != null) {
                    _channelLogoBitmap.value = decoded
                    MultiStreamBroadcaster.updateLiveNewsOverlay(_newsConfig.value, decoded)
                    _bannerMessage.value = "চ্যানেল লোগো সফলভাবে যুক্ত হয়েছে।"
                } else {
                    _bannerMessage.value = "লোগো ইমেজ পড়া যায়নি।"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = "লোগো আপলোড ব্যর্থ হয়েছে: ${e.localizedMessage ?: "ত্রুটি"}"
            }
        }
    }

    fun clearChannelLogo() {
        _channelLogoBitmap.value = null
        MultiStreamBroadcaster.updateLiveNewsOverlay(_newsConfig.value, null)
    }

    fun saveAndPushNewsUpdate(readScriptNow: Boolean) {
        val current = _newsConfig.value
        MultiStreamBroadcaster.updateLiveNewsOverlay(current, _channelLogoBitmap.value)
        if (readScriptNow && broadcastTelemetry.value.isLive) {
            MultiStreamBroadcaster.triggerLiveScriptNarration()
        }
        viewModelScope.launch {
            val res = repository.saveNewsPreset(currentUserId, ACTIVE_DESK_DOC_ID, current)
            if (res.isSuccess) {
                _bannerMessage.value = when {
                    readScriptNow && broadcastTelemetry.value.isLive ->
                        "নিউজ আপডেট লাইভে পুশ হয়েছে এবং প্রেজেন্টার নতুন স্ক্রিপ্ট পড়া শুরু করেছেন!"
                    broadcastTelemetry.value.isLive ->
                        "শিরোনাম ও নিচের স্ক্রলিং লেখা লাইভে আপডেট হয়েছে!"
                    else ->
                        "নিউজ ডেস্ক সেটিংস ক্লাউডে সেভ হয়েছে।"
                }
            } else {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "নিউজ সেভ করতে সমস্যা হয়েছে।"
            }
        }
    }

    fun saveAsBulletinPreset() {
        val current = _newsConfig.value
        viewModelScope.launch {
            val res = repository.saveNewsPreset(currentUserId, null, current)
            if (res.isSuccess) {
                _bannerMessage.value = "নতুন নিউজ বুলেটিন প্রিসেট সংরক্ষিত হয়েছে।"
            } else {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "বুলেটিন সেভ ব্যর্থ হয়েছে।"
            }
        }
    }

    fun applyBulletinPreset(preset: NewsDeskPreset, pushAndReadIfLive: Boolean = true) {
        val applied = preset.copy(id = ACTIVE_DESK_DOC_ID)
        _newsConfig.value = applied
        MultiStreamBroadcaster.updateLiveNewsOverlay(applied, _channelLogoBitmap.value)
        if (pushAndReadIfLive && broadcastTelemetry.value.isLive && applied.ttsEnabled) {
            MultiStreamBroadcaster.triggerLiveScriptNarration()
        }
        _bannerMessage.value = "বুলেটিন লোড হয়েছে: ${applied.headline.ifBlank { applied.breakingTag }}"
    }

    fun deleteBulletinPreset(preset: NewsDeskPreset) {
        if (preset.id == ACTIVE_DESK_DOC_ID) return
        viewModelScope.launch {
            val res = repository.deleteNewsPreset(currentUserId, preset.id)
            if (res.isFailure) {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "বুলেটিন মুছতে সমস্যা হয়েছে।"
            }
        }
    }

    fun togglePreviewVoice(context: Context) {
        if (NewsTtsEngine.state.value.isSpeaking) {
            NewsTtsEngine.stopPreview()
            return
        }
        val cfg = _newsConfig.value
        if (cfg.newsScript.isBlank()) {
            _bannerMessage.value = "প্রেজেন্টার ভয়েস শুনতে আগে নিউজ স্ক্রিপ্ট লিখুন।"
            return
        }
        viewModelScope.launch {
            NewsTtsEngine.previewSpeak(
                context = context,
                script = cfg.newsScript,
                language = cfg.languageEnum,
                speed = cfg.ttsSpeed.toFloat(),
                pitch = cfg.ttsPitch.toFloat()
            )
        }
    }

    fun stopPreviewVoice() {
        NewsTtsEngine.stopPreview()
    }

    // --- VIDEO QUEUE CONTROLS ---

    fun addVideos(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isAnalyzingVideos.value = true
            try {
                val analyzed = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        VideoDemuxer.inspectVideo(
                            context = appContext,
                            uri = uri,
                            itemId = UUID.randomUUID().toString()
                        )
                    }
                }
                _videoQueue.update { current -> (current + analyzed).take(50) }
                _bannerMessage.value = "${analyzed.size}টি ভিডিও ফুটেজ ব্রডকাস্ট কিউ-তে যুক্ত হয়েছে।"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = "ভিডিও পড়তে সমস্যা হয়েছে: ${e.localizedMessage ?: "অজানা ত্রুটি"}"
            } finally {
                _isAnalyzingVideos.value = false
            }
        }
    }

    fun removeVideo(videoId: String) {
        if (broadcastTelemetry.value.isLive) {
            _bannerMessage.value = "লাইভ চলাকালীন ভিডিও সরানো যাবে না।"
            return
        }
        _videoQueue.update { list -> list.filterNot { it.id == videoId } }
    }

    fun moveVideoUp(index: Int) {
        if (broadcastTelemetry.value.isLive || index <= 0) return
        _videoQueue.update { list ->
            if (index >= list.size) return@update list
            val mutable = list.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index - 1, item)
            mutable
        }
    }

    // --- PLATFORMS & BROADCAST CONTROLS ---

    fun toggleDestination(destination: StreamDestination, enabled: Boolean) {
        if (enabled && destination.streamKey.isBlank()) {
            _bannerMessage.value = "${destination.label} চালু করতে আগে Stream Key সেভ করুন।"
            return
        }
        viewModelScope.launch {
            val result = repository.toggleDestination(currentUserId, destination.id, enabled)
            if (result.isFailure) {
                _bannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "আপডেট ব্যর্থ হয়েছে।"
            }
        }
    }

    fun saveDestination(
        destinationId: String?,
        platform: PlatformType,
        label: String,
        rtmpUrl: String,
        streamKey: String,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            val result = if (destinationId.isNullOrBlank()) {
                repository.createDestination(
                    userId = currentUserId,
                    platform = platform,
                    label = label,
                    rtmpUrl = rtmpUrl,
                    streamKey = streamKey,
                    enabled = enabled && streamKey.isNotBlank()
                ).map { Unit }
            } else {
                repository.updateDestination(
                    userId = currentUserId,
                    destinationId = destinationId,
                    label = label,
                    rtmpUrl = rtmpUrl,
                    streamKey = streamKey,
                    enabled = enabled && streamKey.isNotBlank()
                )
            }
            if (result.isSuccess) {
                _bannerMessage.value = "$label সফলভাবে সেভ হয়েছে।"
            } else {
                _bannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "সেভ করতে সমস্যা হয়েছে।"
            }
        }
    }

    fun deleteDestination(destination: StreamDestination) {
        viewModelScope.launch {
            val res = repository.deleteDestination(currentUserId, destination.id)
            if (res.isSuccess) {
                _bannerMessage.value = "${destination.label} মুছে ফেলা হয়েছে।"
            } else {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "মুছতে ব্যর্থ হয়েছে।"
            }
        }
    }

    fun testConnection(destination: StreamDestination) {
        if (_testingIds.value.contains(destination.id)) return
        viewModelScope.launch {
            _testingIds.update { it + destination.id }
            try {
                val res = withContext(Dispatchers.IO) {
                    RtmpClient(destination.rtmpUrl, destination.streamKey).testConnection()
                }
                _connectionTests.update { it + (destination.id to res) }
            } finally {
                _testingIds.update { it - destination.id }
            }
        }
    }

    fun startBroadcast(context: Context) {
        val videos = _videoQueue.value
        if (videos.isEmpty()) {
            _bannerMessage.value = "লাইভ শুরু করতে প্রথমে কমপক্ষে একটি ভিডিও ফুটেজ আপলোড করুন।"
            return
        }
        val destinations = (destinationsState.value as? UiState.Success)?.data.orEmpty()
        val ready = destinations.filter { it.isReadyToBroadcast }
        if (ready.isEmpty()) {
            _bannerMessage.value = "প্ল্যাটফর্ম ট্যাব থেকে YouTube, Facebook বা Instagram-এর Stream Key দিয়ে চালু করুন।"
            return
        }

        NewsTtsEngine.stopPreview()
        val currentNews = _newsConfig.value
        val title = _broadcastTitle.value.ifBlank { currentNews.headline.ifBlank { videos.first().displayName } }
        val platformNames = ready.map { it.platform }

        viewModelScope.launch {
            val sessionRes = repository.startBroadcastSession(
                userId = currentUserId,
                title = title,
                videoCount = videos.size,
                platforms = platformNames
            )
            activeSessionDocId = sessionRes.getOrNull()

            MultiStreamBroadcaster.startBroadcast(
                context = context.applicationContext,
                videos = videos,
                destinations = ready,
                newsPreset = currentNews,
                logoBitmap = _channelLogoBitmap.value
            ) { elapsedSec, avgKbps, totalBytes, anySuccess ->
                val sessionId = activeSessionDocId
                activeSessionDocId = null
                if (!sessionId.isNullOrBlank()) {
                    viewModelScope.launch {
                        repository.finishBroadcastSession(
                            userId = currentUserId,
                            sessionId = sessionId,
                            finalStatus = if (anySuccess) SessionStatus.COMPLETED else SessionStatus.FAILED,
                            durationSec = elapsedSec,
                            avgBitrateKbps = avgKbps,
                            totalBytesSent = totalBytes
                        )
                    }
                }
            }
        }
    }

    fun stopBroadcast(context: Context) {
        MultiStreamBroadcaster.stopBroadcast(context.applicationContext)
    }

    fun toggleMute() {
        val nowMuted = MultiStreamBroadcaster.toggleMute()
        if (nowMuted) {
            NewsTtsEngine.stopPreview()
        }
    }

    fun deleteSession(session: BroadcastSession) {
        viewModelScope.launch {
            val res = repository.deleteSession(currentUserId, session.id)
            if (res.isFailure) {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "হিস্ট্রি মুছতে সমস্যা হয়েছে।"
            }
        }
    }

    companion object {
        private const val TAG = "StudioViewModel"
        private const val STOP_TIMEOUT_MS = 5000L
        const val ACTIVE_DESK_DOC_ID = "active_desk"
    }
}
