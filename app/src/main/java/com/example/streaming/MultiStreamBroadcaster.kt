package com.example.streaming

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import com.example.data.NewsDeskPreset
import com.example.data.PlatformType
import com.example.data.StreamDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class PlatformStreamStatus {
    IDLE,
    CONNECTING,
    LIVE,
    RECONNECTING,
    ERROR,
    STOPPED
}

data class PlatformLiveStats(
    val destinationId: String,
    val label: String,
    val platform: PlatformType,
    val status: PlatformStreamStatus = PlatformStreamStatus.IDLE,
    val bitrateKbps: Int = 0,
    val latencyMs: Long = 0L,
    val framesSent: Long = 0L,
    val droppedFrames: Long = 0L,
    val bytesSent: Long = 0L,
    val errorMessage: String? = null
)

data class BroadcastTelemetry(
    val isLive: Boolean = false,
    val isStarting: Boolean = false,
    val isMuted: Boolean = false,
    val isNewsOverlayActive: Boolean = false,
    val activeVideoIndex: Int = 0,
    val activeVideoName: String = "",
    val elapsedSec: Long = 0L,
    val totalBitrateKbps: Int = 0,
    val totalBytesSent: Long = 0L,
    val currentFps: Int = 0,
    val loopPlaylist: Boolean = true,
    val platformStats: Map<String, PlatformLiveStats> = emptyMap(),
    val lastSystemNotice: String? = null
)

/**
 * Singleton real-time multi-platform broadcast fan-out engine.
 * Supports two broadcast pipelines:
 * 1. News Desk Compositor Pipeline: Composites uploaded video footage + Top Channel Logo + LIVE Clock
 *    + Breaking Headline + Smooth Scrolling Bottom Ticker + Auto TTS News Presenter AAC Audio into
 *    real-time H.264/AAC FLV packets pushed simultaneously to YouTube, Facebook, and Instagram.
 * 2. Direct MediaExtractor Passthrough: Pushes raw H.264/AAC tracks at 1.0x pacing when News Mode is off.
 */
object MultiStreamBroadcaster {
    private const val TAG = "MultiStreamBroadcaster"

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var masterJob: Job? = null

    private val liveNewsPresetRef = AtomicReference(NewsDeskPreset())
    private val liveLogoBitmapRef = AtomicReference<Bitmap?>(null)
    private val ttsTriggerVersion = AtomicInteger(0)
    private val isMutedFlag = AtomicBoolean(false)

    private val _telemetry = MutableStateFlow(BroadcastTelemetry())
    val telemetry: StateFlow<BroadcastTelemetry> = _telemetry.asStateFlow()

    fun setLoopPlaylist(loop: Boolean) {
        _telemetry.update { it.copy(loopPlaylist = loop) }
    }

    fun setMuted(muted: Boolean) {
        isMutedFlag.set(muted)
        _telemetry.update { state ->
            state.copy(
                isMuted = muted,
                lastSystemNotice = if (state.isLive) {
                    if (muted) "লাইভ স্ট্রিম অডিও মিউট করা হয়েছে (Stream Muted)"
                    else "লাইভ স্ট্রিম অডিও পুনরায় চালু হয়েছে (Audio Active)"
                } else {
                    state.lastSystemNotice
                }
            )
        }
    }

    fun toggleMute(): Boolean {
        val next = !isMutedFlag.get()
        setMuted(next)
        return next
    }

    /**
     * Updates the live TV News graphics overlay (logo, headline, ticker, script) instantaneously on air.
     */
    fun updateLiveNewsOverlay(preset: NewsDeskPreset, logoBitmap: Bitmap?) {
        liveNewsPresetRef.set(preset)
        liveLogoBitmapRef.set(logoBitmap)
    }

    /**
     * Signals the live TTS News Presenter audio pipeline to immediately read the updated script on air.
     */
    fun triggerLiveScriptNarration() {
        ttsTriggerVersion.incrementAndGet()
    }

    fun startBroadcast(
        context: Context,
        videos: List<QueuedVideo>,
        destinations: List<StreamDestination>,
        newsPreset: NewsDeskPreset,
        logoBitmap: Bitmap?,
        onFinished: (elapsedSec: Long, avgBitrateKbps: Long, totalBytes: Long, anySuccess: Boolean) -> Unit
    ) {
        if (masterJob?.isActive == true) return
        val appContext = context.applicationContext
        val readyDestinations = destinations.filter { it.isReadyToBroadcast }
        if (videos.isEmpty() || readyDestinations.isEmpty()) return

        liveNewsPresetRef.set(newsPreset)
        liveLogoBitmapRef.set(logoBitmap)
        ttsTriggerVersion.incrementAndGet()

        val initialStats = readyDestinations.associate { dest ->
            dest.id to PlatformLiveStats(
                destinationId = dest.id,
                label = dest.label,
                platform = dest.platformType,
                status = PlatformStreamStatus.CONNECTING
            )
        }

        _telemetry.value = BroadcastTelemetry(
            isLive = false,
            isStarting = true,
            isMuted = isMutedFlag.get(),
            isNewsOverlayActive = newsPreset.newsModeEnabled,
            activeVideoIndex = 0,
            activeVideoName = videos.first().displayName,
            loopPlaylist = _telemetry.value.loopPlaylist,
            platformStats = initialStats,
            lastSystemNotice = if (newsPreset.newsModeEnabled) {
                "${readyDestinations.size}টি প্ল্যাটফর্মে নিউজ ডেস্ক লাইভ (লোগো, টিকার ও ভয়েস) সংযুক্ত হচ্ছে..."
            } else {
                "${readyDestinations.size}টি প্ল্যাটফর্মে রিয়েল-টাইম সংযোগ স্থাপন করা হচ্ছে..."
            }
        )

        BroadcastForegroundService.start(appContext, readyDestinations.size)

        masterJob = engineScope.launch {
            val startWallMs = SystemClock.elapsedRealtime()
            val totalBytesAtomic = AtomicLong(0L)
            var anyPlatformWentLive = false
            val channels = HashMap<String, Channel<FlvMediaPacket>>()
            val workerJobs = mutableListOf<Job>()

            try {
                val useNewsCompositor = newsPreset.newsModeEnabled
                val initialMeta = if (useNewsCompositor) {
                    NewsBroadcastPipeline.createNewsStreamMetadata()
                } else {
                    VideoDemuxer.extractStreamMetadata(appContext, videos.first().uri)
                }

                // Launch one concurrent RTMP/RTMPS publisher coroutine per enabled platform
                for (dest in readyDestinations) {
                    val ch = Channel<FlvMediaPacket>(capacity = 200)
                    channels[dest.id] = ch

                    val worker = launch(Dispatchers.IO) {
                        runPlatformWorker(
                            destination = dest,
                            initialMeta = initialMeta,
                            packetChannel = ch,
                            onLiveConnected = { anyPlatformWentLive = true },
                            onBytesAdded = { added -> totalBytesAtomic.addAndGet(added.toLong()) }
                        )
                    }
                    workerJobs.add(worker)
                }

                // Wait briefly for initial RTMP handshakes to settle (up to 6s)
                var waitedMs = 0
                while (isActive && waitedMs < 6000) {
                    val stats = _telemetry.value.platformStats.values
                    if (stats.any { it.status == PlatformStreamStatus.LIVE } ||
                        stats.all { it.status == PlatformStreamStatus.ERROR }
                    ) {
                        break
                    }
                    delay(200)
                    waitedMs += 200
                }

                val allFailed = _telemetry.value.platformStats.values.all {
                    it.status == PlatformStreamStatus.ERROR
                }
                if (allFailed) {
                    _telemetry.update {
                        it.copy(
                            isLive = false,
                            isStarting = false,
                            lastSystemNotice = "কোনো প্ল্যাটফর্মেই সংযোগ স্থাপন করা যায়নি। Stream Key ও ইন্টারনেট যাচাই করুন।"
                        )
                    }
                    return@launch
                }

                _telemetry.update {
                    it.copy(
                        isLive = true,
                        isStarting = false,
                        lastSystemNotice = if (useNewsCompositor) {
                            "প্রোফেশনাল নিউজ ডেস্ক লাইভ চালু আছে (লোগো + স্ক্রলিং টিকার + অটো প্রেজেন্টার)"
                        } else {
                            "সকল সক্রিয় প্ল্যাটফর্মে রিয়েল-টাইম ব্রডকাস্ট চলছে"
                        }
                    )
                }

                // Launch live clock + aggregate telemetry updater
                val tickerJob = launch {
                    while (isActive) {
                        delay(1000)
                        val elapsed = ((SystemClock.elapsedRealtime() - startWallMs) / 1000L).coerceAtLeast(1L)
                        val currentStats = _telemetry.value.platformStats.values
                        val totalKbps = currentStats.filter { it.status == PlatformStreamStatus.LIVE }
                            .sumOf { it.bitrateKbps }
                        val totalBytes = totalBytesAtomic.get()
                        _telemetry.update {
                            it.copy(
                                elapsedSec = elapsed,
                                totalBitrateKbps = totalKbps,
                                totalBytesSent = totalBytes
                            )
                        }
                        val liveCount = currentStats.count { it.status == PlatformStreamStatus.LIVE }
                        BroadcastForegroundService.updateStatus(appContext, liveCount, elapsed, totalKbps)
                    }
                }

                val hasActiveWorkers: () -> Boolean = {
                    masterJob?.isActive == true && _telemetry.value.platformStats.values.any {
                        it.status == PlatformStreamStatus.LIVE ||
                            it.status == PlatformStreamStatus.CONNECTING ||
                            it.status == PlatformStreamStatus.RECONNECTING
                    }
                }

                if (useNewsCompositor) {
                    try {
                        NewsBroadcastPipeline.runCompositedNewsBroadcast(
                            scope = this,
                            context = appContext,
                            videos = videos,
                            getLivePreset = { liveNewsPresetRef.get() },
                            getLogoBitmap = { liveLogoBitmapRef.get() },
                            ttsTriggerVersion = ttsTriggerVersion,
                            shouldLoopPlaylist = { _telemetry.value.loopPlaylist },
                            isStillActive = hasActiveWorkers,
                            isAudioMuted = { isMutedFlag.get() },
                            onVideoChanged = { idx, name ->
                                _telemetry.update { it.copy(activeVideoIndex = idx, activeVideoName = name) }
                            },
                            onFpsMeasured = { fps ->
                                _telemetry.update { it.copy(currentFps = fps) }
                            },
                            onPacketReady = { packet ->
                                broadcastPacket(channels, packet)
                            }
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "News compositor unavailable on this device, falling back to passthrough", e)
                        _telemetry.update {
                            it.copy(
                                isNewsOverlayActive = false,
                                lastSystemNotice = "হার্ডওয়্যার এনকোডার ব্যর্থ হওয়ায় ডাইরেক্ট স্ট্রিম চালু হয়েছে।"
                            )
                        }
                        runPassthroughLoop(appContext, videos, channels, hasActiveWorkers)
                    }
                } else {
                    runPassthroughLoop(appContext, videos, channels, hasActiveWorkers)
                }

                tickerJob.cancel()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Broadcast engine error", e)
                _telemetry.update {
                    it.copy(lastSystemNotice = e.localizedMessage ?: "ব্রডকাস্ট ইঞ্জিনে ত্রুটি হয়েছে।")
                }
            } finally {
                channels.values.forEach { it.close() }
                workerJobs.forEach { it.cancel() }
                NewsTtsEngine.stopPreview()
                val elapsedSec = ((SystemClock.elapsedRealtime() - startWallMs) / 1000L).coerceAtLeast(1L)
                val totalBytes = totalBytesAtomic.get()
                val avgKbps = if (elapsedSec > 0) ((totalBytes * 8L) / 1000L / elapsedSec) else 0L
                _telemetry.update { state ->
                    state.copy(
                        isLive = false,
                        isStarting = false,
                        isNewsOverlayActive = false,
                        elapsedSec = elapsedSec,
                        totalBytesSent = totalBytes,
                        platformStats = state.platformStats.mapValues { (_, s) ->
                            if (s.status == PlatformStreamStatus.ERROR) s
                            else s.copy(status = PlatformStreamStatus.STOPPED, bitrateKbps = 0)
                        }
                    )
                }
                BroadcastForegroundService.stop(appContext)
                onFinished(elapsedSec, avgKbps, totalBytes, anyPlatformWentLive)
            }
        }
    }

    fun stopBroadcast(context: Context) {
        masterJob?.cancel()
        masterJob = null
        NewsTtsEngine.stopPreview()
        BroadcastForegroundService.stop(context.applicationContext)
    }

    private suspend fun runPassthroughLoop(
        appContext: Context,
        videos: List<QueuedVideo>,
        channels: Map<String, Channel<FlvMediaPacket>>,
        hasActiveWorkers: () -> Boolean
    ) {
        var cumulativeTimestampOffsetMs = 0L
        var videoIndex = 0
        val sampleBuffer = ByteBuffer.allocate(1024 * 1024)
        val scratchBytes = ByteArray(1024 * 1024)

        while (hasActiveWorkers()) {
            val currentVideo = videos[videoIndex]
            _telemetry.update {
                it.copy(
                    activeVideoIndex = videoIndex,
                    activeVideoName = currentVideo.displayName
                )
            }

            val clipEndOffsetMs = streamSingleVideoRealTime(
                context = appContext,
                video = currentVideo,
                baseTimestampMs = cumulativeTimestampOffsetMs,
                sampleBuffer = sampleBuffer,
                scratchBytes = scratchBytes,
                channels = channels
            )
            cumulativeTimestampOffsetMs = clipEndOffsetMs + 33L

            videoIndex += 1
            if (videoIndex >= videos.size) {
                if (_telemetry.value.loopPlaylist) {
                    videoIndex = 0
                } else {
                    break
                }
            }
        }
    }

    private suspend fun streamSingleVideoRealTime(
        context: Context,
        video: QueuedVideo,
        baseTimestampMs: Long,
        sampleBuffer: ByteBuffer,
        scratchBytes: ByteArray,
        channels: Map<String, Channel<FlvMediaPacket>>
    ): Long {
        val meta = VideoDemuxer.extractStreamMetadata(context, video.uri)

        meta.avcSequenceHeader?.let { avcHdr ->
            broadcastPacket(
                channels,
                FlvMediaPacket(
                    isVideo = true,
                    isKeyframe = true,
                    isSequenceHeader = true,
                    timestampMs = baseTimestampMs.toInt(),
                    payload = avcHdr
                )
            )
        }
        meta.aacSequenceHeader?.let { aacHdr ->
            broadcastPacket(
                channels,
                FlvMediaPacket(
                    isVideo = false,
                    isKeyframe = false,
                    isSequenceHeader = true,
                    timestampMs = baseTimestampMs.toInt(),
                    payload = aacHdr
                )
            )
        }

        val extractor = MediaExtractor()
        var lastTimestampMs = baseTimestampMs
        try {
            extractor.setDataSource(context, video.uri, null)
            var videoTrackIndex = -1
            var audioTrackIndex = -1

            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    extractor.selectTrack(i)
                } else if (mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) && audioTrackIndex == -1) {
                    audioTrackIndex = i
                    extractor.selectTrack(i)
                }
            }
            if (videoTrackIndex == -1) return baseTimestampMs

            val clipStartClockNs = SystemClock.elapsedRealtimeNanos()
            var firstSampleTimeUs = -1L
            var fpsFrames = 0
            var fpsWindowStartMs = SystemClock.elapsedRealtime()

            while (masterJob?.isActive == true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break

                sampleBuffer.clear()
                val sampleSize = extractor.readSampleData(sampleBuffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                if (firstSampleTimeUs < 0L) firstSampleTimeUs = sampleTimeUs
                val relativeUs = (sampleTimeUs - firstSampleTimeUs).coerceAtLeast(0L)

                val elapsedSinceStartUs = (SystemClock.elapsedRealtimeNanos() - clipStartClockNs) / 1000L
                val sleepMs = (relativeUs - elapsedSinceStartUs) / 1000L
                if (sleepMs > 2L) {
                    delay(sleepMs.coerceAtMost(250L))
                }

                val packetTimestampMs = (baseTimestampMs + (relativeUs / 1000L)).toInt()
                lastTimestampMs = packetTimestampMs.toLong()

                val copyLen = sampleSize.coerceAtMost(scratchBytes.size)
                sampleBuffer.position(0)
                sampleBuffer.get(scratchBytes, 0, copyLen)

                if (trackIndex == videoTrackIndex) {
                    val isSync = (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    val flvPayload = VideoDemuxer.buildFlvAvcNaluPacket(scratchBytes, copyLen, isSync)
                    if (flvPayload.size > 5) {
                        broadcastPacket(
                            channels,
                            FlvMediaPacket(
                                isVideo = true,
                                isKeyframe = isSync,
                                isSequenceHeader = false,
                                timestampMs = packetTimestampMs,
                                payload = flvPayload
                            )
                        )
                    }
                    fpsFrames += 1
                    val nowMs = SystemClock.elapsedRealtime()
                    if (nowMs - fpsWindowStartMs >= 1000L) {
                        val measuredFps = fpsFrames
                        fpsFrames = 0
                        fpsWindowStartMs = nowMs
                        _telemetry.update { it.copy(currentFps = measuredFps) }
                    }
                } else if (trackIndex == audioTrackIndex) {
                    if (!isMutedFlag.get()) {
                        val flvPayload = VideoDemuxer.buildFlvAacRawPacket(scratchBytes, copyLen)
                        if (flvPayload.size > 2) {
                            broadcastPacket(
                                channels,
                                FlvMediaPacket(
                                    isVideo = false,
                                    isKeyframe = false,
                                    isSequenceHeader = false,
                                    timestampMs = packetTimestampMs,
                                    payload = flvPayload
                                )
                            )
                        }
                    }
                }

                extractor.advance()
            }
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
        return lastTimestampMs
    }

    private fun broadcastPacket(
        channels: Map<String, Channel<FlvMediaPacket>>,
        packet: FlvMediaPacket
    ) {
        channels.forEach { (destId, channel) ->
            val sent = channel.trySend(packet)
            if (sent.isFailure && !packet.isSequenceHeader) {
                updatePlatformStat(destId) { it.copy(droppedFrames = it.droppedFrames + 1L) }
            }
        }
    }

    private suspend fun runPlatformWorker(
        destination: StreamDestination,
        initialMeta: VideoStreamMetadata,
        packetChannel: Channel<FlvMediaPacket>,
        onLiveConnected: () -> Unit,
        onBytesAdded: (Int) -> Unit
    ) {
        var client: RtmpClient? = null
        var attempt = 0
        val maxAttempts = 2

        try {
            while (attempt < maxAttempts) {
                attempt += 1
                updatePlatformStat(destination.id) {
                    it.copy(
                        status = if (attempt == 1) PlatformStreamStatus.CONNECTING else PlatformStreamStatus.RECONNECTING,
                        errorMessage = null
                    )
                }
                try {
                    val rtmp = RtmpClient(destination.rtmpUrl, destination.streamKey)
                    client = rtmp
                    val rttMs = rtmp.connectAndPublish(initialMeta)

                    initialMeta.avcSequenceHeader?.let { rtmp.sendVideoPacket(0, it) }
                    initialMeta.aacSequenceHeader?.let { rtmp.sendAudioPacket(0, it) }

                    onLiveConnected()
                    updatePlatformStat(destination.id) {
                        it.copy(
                            status = PlatformStreamStatus.LIVE,
                            latencyMs = rttMs,
                            errorMessage = null
                        )
                    }

                    var windowBytes = 0L
                    var windowStartMs = SystemClock.elapsedRealtime()
                    var framesSent = 0L
                    var totalPlatformBytes = 0L

                    for (packet in packetChannel) {
                        val written = if (packet.isVideo) {
                            rtmp.sendVideoPacket(packet.timestampMs, packet.payload)
                        } else {
                            rtmp.sendAudioPacket(packet.timestampMs, packet.payload)
                        }
                        onBytesAdded(written)
                        windowBytes += written
                        totalPlatformBytes += written
                        if (packet.isVideo && !packet.isSequenceHeader) framesSent += 1L

                        val nowMs = SystemClock.elapsedRealtime()
                        val deltaMs = nowMs - windowStartMs
                        if (deltaMs >= 1000L) {
                            val kbps = ((windowBytes * 8L) / deltaMs).toInt().coerceAtLeast(0)
                            windowBytes = 0L
                            windowStartMs = nowMs
                            updatePlatformStat(destination.id) {
                                it.copy(
                                    bitrateKbps = kbps,
                                    framesSent = framesSent,
                                    bytesSent = totalPlatformBytes
                                )
                            }
                        }
                    }
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Platform ${destination.label} stream error (attempt $attempt)", e)
                    client?.closeQuietly()
                    client = null
                    if (attempt >= maxAttempts) {
                        updatePlatformStat(destination.id) {
                            it.copy(
                                status = PlatformStreamStatus.ERROR,
                                bitrateKbps = 0,
                                errorMessage = e.localizedMessage ?: "সংযোগ বিচ্ছিন্ন হয়েছে"
                            )
                        }
                        return
                    }
                    delay(1500)
                }
            }
        } finally {
            client?.disconnectGracefully()
        }
    }

    private fun updatePlatformStat(
        destinationId: String,
        transform: (PlatformLiveStats) -> PlatformLiveStats
    ) {
        _telemetry.update { state ->
            val current = state.platformStats[destinationId] ?: return@update state
            state.copy(platformStats = state.platformStats + (destinationId to transform(current)))
        }
    }
}
