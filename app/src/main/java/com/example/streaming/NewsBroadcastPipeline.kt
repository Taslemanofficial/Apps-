package com.example.streaming

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.example.data.NewsDeskPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hardware-accelerated News Desk Broadcast Compositor & Encoder:
 * 1. Decodes uploaded video footage via MediaPlayer -> SurfaceTexture (OES external texture).
 * 2. Composites real-time TV News Graphics (Top Logo, LIVE clock, Breaking Headline, Smooth Scrolling Ticker)
 *    via OpenGL ES 2.0 alpha-blending directly into a 1280x720 H.264 MediaCodec encoder.
 * 3. Synthesizes Bangla/English News Script text via Android TextToSpeech -> WAV -> 44.1kHz Stereo AAC
 *    MediaCodec encoder and streams continuous FLV video + audio packets to all active RTMP destinations.
 */
object NewsBroadcastPipeline {
    private const val TAG = "NewsBroadcastPipeline"
    private const val OUT_WIDTH = 1280
    private const val OUT_HEIGHT = 720
    private const val OUT_FPS = 24
    private const val OUT_VIDEO_BITRATE = 2_400_000
    private const val OUT_AUDIO_SAMPLE_RATE = 44100
    private const val OUT_AUDIO_CHANNELS = 2
    private const val OUT_AUDIO_BITRATE = 128_000

    fun createNewsStreamMetadata(): VideoStreamMetadata = VideoStreamMetadata(
        width = OUT_WIDTH,
        height = OUT_HEIGHT,
        frameRate = OUT_FPS,
        videoBitrateKbps = OUT_VIDEO_BITRATE / 1000,
        audioBitrateKbps = OUT_AUDIO_BITRATE / 1000,
        audioSampleRate = OUT_AUDIO_SAMPLE_RATE,
        audioChannelCount = OUT_AUDIO_CHANNELS,
        avcSequenceHeader = null,
        aacSequenceHeader = buildDefaultAacSequenceHeader()
    )

    private fun buildDefaultAacSequenceHeader(): ByteArray {
        // AAC-LC (2), 44100Hz (index 4), Stereo (2 channels) => 0x12 0x10
        return VideoDemuxer.buildFlvAacSequenceHeader(byteArrayOf(0x12, 0x10))
    }

    /**
     * Runs the full composited News Desk Video + Automated TTS Audio broadcast loop.
     */
    suspend fun runCompositedNewsBroadcast(
        scope: CoroutineScope,
        context: Context,
        videos: List<QueuedVideo>,
        getLivePreset: () -> NewsDeskPreset,
        getLogoBitmap: () -> Bitmap?,
        ttsTriggerVersion: AtomicInteger,
        shouldLoopPlaylist: () -> Boolean,
        isStillActive: () -> Boolean,
        isAudioMuted: () -> Boolean = { false },
        onVideoChanged: (index: Int, name: String) -> Unit,
        onFpsMeasured: (Int) -> Unit,
        onPacketReady: (FlvMediaPacket) -> Unit
    ) {
        val isSpeakingFlag = AtomicBoolean(false)

        // Launch concurrent TTS -> AAC real-time audio broadcaster
        val audioJob = scope.launch(Dispatchers.IO) {
            runTtsAacAudioPipeline(
                context = context,
                getLivePreset = getLivePreset,
                ttsTriggerVersion = ttsTriggerVersion,
                isStillActive = isStillActive,
                isAudioMuted = isAudioMuted,
                isSpeakingFlag = isSpeakingFlag,
                onPacketReady = onPacketReady
            )
        }

        try {
            runEglVideoOverlayPipeline(
                context = context,
                videos = videos,
                getLivePreset = getLivePreset,
                getLogoBitmap = getLogoBitmap,
                shouldLoopPlaylist = shouldLoopPlaylist,
                isStillActive = isStillActive,
                isAudioMuted = isAudioMuted,
                isSpeakingFlag = isSpeakingFlag,
                onVideoChanged = onVideoChanged,
                onFpsMeasured = onFpsMeasured,
                onPacketReady = onPacketReady
            )
        } finally {
            audioJob.cancel()
            NewsTtsEngine.updateLiveAnchorCaption("", 0, 0, false)
        }
    }

    private suspend fun runEglVideoOverlayPipeline(
        context: Context,
        videos: List<QueuedVideo>,
        getLivePreset: () -> NewsDeskPreset,
        getLogoBitmap: () -> Bitmap?,
        shouldLoopPlaylist: () -> Boolean,
        isStillActive: () -> Boolean,
        isAudioMuted: () -> Boolean,
        isSpeakingFlag: AtomicBoolean,
        onVideoChanged: (index: Int, name: String) -> Unit,
        onFpsMeasured: (Int) -> Unit,
        onPacketReady: (FlvMediaPacket) -> Unit
    ) {
        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, OUT_WIDTH, OUT_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, OUT_VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, OUT_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val renderer = NewsOverlayRenderer(OUT_WIDTH, OUT_HEIGHT)
        var encoderSurface: Surface? = null
        var eglHelper: EglOverlayCompositor? = null
        var mediaPlayer: MediaPlayer? = null

        try {
            encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderSurface = encoder.createInputSurface()
            encoder.start()

            eglHelper = EglOverlayCompositor(encoderSurface, OUT_WIDTH, OUT_HEIGHT)
            val playerSurface = Surface(eglHelper.videoSurfaceTexture)

            val clipCompleted = AtomicBoolean(false)
            var currentVideoIdx = 0

            fun startMediaPlayerForIndex(idx: Int) {
                clipCompleted.set(false)
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                } catch (_: Exception) {}
                val video = videos[idx]
                onVideoChanged(idx, video.displayName)
                val mp = MediaPlayer()
                mp.setSurface(playerSurface)
                mp.setDataSource(context, video.uri)
                mp.setVolume(0f, 0f)
                mp.setOnCompletionListener { clipCompleted.set(true) }
                mp.setOnErrorListener { _, _, _ ->
                    clipCompleted.set(true)
                    true
                }
                mp.prepare()
                mp.start()
                mediaPlayer = mp
            }

            startMediaPlayerForIndex(0)

            val bufferInfo = MediaCodec.BufferInfo()
            val startWallMs = SystemClock.elapsedRealtime()
            val frameIntervalMs = 1000L / OUT_FPS
            var frameCount = 0L
            var fpsWindowStart = SystemClock.elapsedRealtime()
            var fpsCounter = 0
            var sentAvcConfig = false

            while (isStillActive()) {
                val frameStartMs = SystemClock.elapsedRealtime()
                val elapsedMs = frameStartMs - startWallMs

                if (clipCompleted.get()) {
                    currentVideoIdx += 1
                    if (currentVideoIdx >= videos.size) {
                        if (shouldLoopPlaylist()) {
                            currentVideoIdx = 0
                            startMediaPlayerForIndex(0)
                        } else {
                            break
                        }
                    } else {
                        startMediaPlayerForIndex(currentVideoIdx)
                    }
                }

                // Render latest News Desk overlay (logo + LIVE clock + headline + scrolling ticker)
                val preset = getLivePreset()
                val overlayBmp = renderer.renderFrame(
                    preset = preset,
                    logoBitmap = getLogoBitmap(),
                    elapsedMs = elapsedMs,
                    isPresenterSpeaking = isSpeakingFlag.get() && !isAudioMuted()
                )

                // Composite Video SurfaceTexture + News Graphics Overlay Texture -> MediaCodec Surface
                val ptsNs = elapsedMs * 1_000_000L
                eglHelper.drawFrame(overlayBmp, ptsNs)

                // Drain encoded H.264 output buffers and emit FLV AVC packets
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val fmt = encoder.outputFormat
                        val spsBuf = fmt.getByteBuffer("csd-0")
                        val ppsBuf = fmt.getByteBuffer("csd-1")
                        if (spsBuf != null && ppsBuf != null) {
                            val sps = ByteArray(spsBuf.remaining()).also { spsBuf.duplicate().get(it) }
                            val pps = ByteArray(ppsBuf.remaining()).also { ppsBuf.duplicate().get(it) }
                            val avcSeq = VideoDemuxer.buildFlvAvcSequenceHeader(
                                stripAnnexBPrefix(sps),
                                stripAnnexBPrefix(pps)
                            )
                            onPacketReady(
                                FlvMediaPacket(
                                    isVideo = true,
                                    isKeyframe = true,
                                    isSequenceHeader = true,
                                    timestampMs = 0,
                                    payload = avcSeq
                                )
                            )
                            sentAvcConfig = true
                        }
                    } else if (outIdx >= 0) {
                        val outBuf = encoder.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (!isConfig && sentAvcConfig) {
                                outBuf.position(bufferInfo.offset)
                                val bytes = ByteArray(bufferInfo.size)
                                outBuf.get(bytes)
                                val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                                val tsMs = (bufferInfo.presentationTimeUs / 1000L).toInt().coerceAtLeast(0)
                                val flvVideo = VideoDemuxer.buildFlvAvcNaluPacket(bytes, bytes.size, isKey)
                                if (flvVideo.size > 5) {
                                    onPacketReady(
                                        FlvMediaPacket(
                                            isVideo = true,
                                            isKeyframe = isKey,
                                            isSequenceHeader = false,
                                            timestampMs = tsMs,
                                            payload = flvVideo
                                        )
                                    )
                                }
                            }
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                    }
                }

                frameCount += 1
                fpsCounter += 1
                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - fpsWindowStart >= 1000L) {
                    onFpsMeasured(fpsCounter)
                    fpsCounter = 0
                    fpsWindowStart = nowMs
                }

                val targetNextMs = startWallMs + frameCount * frameIntervalMs
                val sleepMs = targetNextMs - SystemClock.elapsedRealtime()
                if (sleepMs > 2L) {
                    delay(sleepMs)
                }
            }
            playerSurface.release()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "News compositor pipeline error", e)
            throw IOException("নিউজ ওভারলে এনকোডার ত্রুটি: ${e.localizedMessage}", e)
        } finally {
            try { mediaPlayer?.stop() } catch (_: Exception) {}
            try { mediaPlayer?.release() } catch (_: Exception) {}
            try { eglHelper?.release() } catch (_: Exception) {}
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            try { encoderSurface?.release() } catch (_: Exception) {}
            renderer.release()
        }
    }

    /**
     * Synthesizes News Script chunks with Android TTS -> resamples PCM to 44.1kHz Stereo ->
     * encodes live AAC via MediaCodec -> emits continuous RTMP Audio FLV packets (and monitors locally).
     */
    private suspend fun runTtsAacAudioPipeline(
        context: Context,
        getLivePreset: () -> NewsDeskPreset,
        ttsTriggerVersion: AtomicInteger,
        isStillActive: () -> Boolean,
        isAudioMuted: () -> Boolean,
        isSpeakingFlag: AtomicBoolean,
        onPacketReady: (FlvMediaPacket) -> Unit
    ) {
        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            OUT_AUDIO_SAMPLE_RATE,
            OUT_AUDIO_CHANNELS
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, OUT_AUDIO_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val aacEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var monitorTrack: AudioTrack? = null
        val pcmChunkQueue = Channel<ShortArray>(capacity = 32)
        val cacheDir = File(context.cacheDir, "news_tts_pcm").apply { mkdirs() }

        // Producer coroutine: watches news script & live updates, synthesizes WAV -> 44.1kHz stereo ShortArray
        val producerJob = CoroutineScope(Dispatchers.IO).launch {
            var lastProcessedTrigger = -1
            var lastSpokenScript = ""
            try {
                while (isActive && isStillActive()) {
                    val preset = getLivePreset()
                    val currentTrigger = ttsTriggerVersion.get()
                    val shouldSpeakNow = preset.ttsEnabled && preset.newsScript.isNotBlank() &&
                        (currentTrigger != lastProcessedTrigger || preset.newsScript != lastSpokenScript)

                    if (!shouldSpeakNow) {
                        isSpeakingFlag.set(false)
                        NewsTtsEngine.updateLiveAnchorCaption("", 0, 0, false)
                        delay(450)
                        continue
                    }

                    lastProcessedTrigger = currentTrigger
                    lastSpokenScript = preset.newsScript
                    val sentences = NewsTtsEngine.splitNewsScript(preset.newsScript)

                    for ((idx, sentence) in sentences.withIndex()) {
                        if (!isActive || !isStillActive() || !getLivePreset().ttsEnabled) break
                        if (ttsTriggerVersion.get() != lastProcessedTrigger) break

                        val wavFile = File(cacheDir, "tts_${System.nanoTime()}.wav")
                        val ok = NewsTtsEngine.synthesizeChunkToWav(
                            context = context,
                            sentence = sentence,
                            language = preset.languageEnum,
                            speed = preset.ttsSpeed.toFloat(),
                            pitch = preset.ttsPitch.toFloat(),
                            outputFile = wavFile
                        )
                        if (ok && wavFile.exists() && wavFile.length() > 44) {
                            val stereo44k = readWavAs44100StereoPcm(wavFile)
                            wavFile.delete()
                            if (stereo44k.isNotEmpty()) {
                                isSpeakingFlag.set(true)
                                NewsTtsEngine.updateLiveAnchorCaption(sentence, idx, sentences.size, true)
                                // Slice into 1024-sample stereo frames (2048 shorts each, ~23.2ms)
                                val frameShorts = 1024 * OUT_AUDIO_CHANNELS
                                var offset = 0
                                while (offset < stereo44k.size && isActive && isStillActive()) {
                                    val slice = ShortArray(frameShorts)
                                    val copy = minOf(frameShorts, stereo44k.size - offset)
                                    System.arraycopy(stereo44k, offset, slice, 0, copy)
                                    pcmChunkQueue.send(slice)
                                    offset += frameShorts
                                }
                                // Brief natural broadcaster pause (~320ms) between sentences
                                repeat(14) {
                                    if (isActive && isStillActive()) {
                                        pcmChunkQueue.send(ShortArray(frameShorts))
                                    }
                                }
                            }
                        } else {
                            wavFile.delete()
                        }
                    }

                    isSpeakingFlag.set(false)
                    NewsTtsEngine.updateLiveAnchorCaption("", 0, 0, false)
                    delay(1200)
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Log.w(TAG, "TTS producer warning", e)
            } finally {
                pcmChunkQueue.close()
                cacheDir.listFiles()?.forEach { it.delete() }
            }
        }

        try {
            aacEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aacEncoder.start()

            // Optional local studio monitor AudioTrack so the creator hears the News Presenter live
            val minBuf = AudioTrack.getMinBufferSize(
                OUT_AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(8192)
            monitorTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OUT_AUDIO_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            monitorTrack.play()

            // Send initial AAC sequence header
            onPacketReady(
                FlvMediaPacket(
                    isVideo = false,
                    isKeyframe = false,
                    isSequenceHeader = true,
                    timestampMs = 0,
                    payload = buildDefaultAacSequenceHeader()
                )
            )

            val samplesPerFrame = 1024
            val frameBytesSize = samplesPerFrame * OUT_AUDIO_CHANNELS * 2
            val frameDurationMs = (samplesPerFrame * 1000.0) / OUT_AUDIO_SAMPLE_RATE // ~23.22ms
            val silentShorts = ShortArray(samplesPerFrame * OUT_AUDIO_CHANNELS)
            val pcmByteScratch = ByteArray(frameBytesSize)
            val bufferInfo = MediaCodec.BufferInfo()
            val startWallMs = SystemClock.elapsedRealtime()
            var frameIndex = 0L

            while (isStillActive()) {
                val dequeued = pcmChunkQueue.tryReceive().getOrNull()
                val muted = isAudioMuted()
                val frameShorts = if (muted || dequeued == null) silentShorts else dequeued

                // Convert little-endian shorts to PCM byte array
                val bb = ByteBuffer.wrap(pcmByteScratch).order(ByteOrder.LITTLE_ENDIAN)
                bb.asShortBuffer().put(frameShorts)

                // Play non-silent TTS audio on local monitor when unmuted
                if (!muted && frameShorts !== silentShorts) {
                    try {
                        monitorTrack.write(frameShorts, 0, frameShorts.size, AudioTrack.WRITE_NON_BLOCKING)
                    } catch (_: Exception) {}
                }

                // Feed PCM frame into MediaCodec AAC encoder
                val inIdx = aacEncoder.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val inBuf = aacEncoder.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        inBuf.clear()
                        inBuf.put(pcmByteScratch, 0, frameBytesSize)
                        val ptsUs = (frameIndex * samplesPerFrame * 1_000_000L) / OUT_AUDIO_SAMPLE_RATE
                        aacEncoder.queueInputBuffer(inIdx, 0, frameBytesSize, ptsUs, 0)
                    }
                }

                // Drain AAC output frames -> FLV Audio tags
                while (true) {
                    val outIdx = aacEncoder.dequeueOutputBuffer(bufferInfo, 0)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val csd0 = aacEncoder.outputFormat.getByteBuffer("csd-0")
                        if (csd0 != null) {
                            val asc = ByteArray(csd0.remaining()).also { csd0.duplicate().get(it) }
                            onPacketReady(
                                FlvMediaPacket(
                                    isVideo = false,
                                    isKeyframe = false,
                                    isSequenceHeader = true,
                                    timestampMs = 0,
                                    payload = VideoDemuxer.buildFlvAacSequenceHeader(asc)
                                )
                            )
                        }
                    } else if (outIdx >= 0) {
                        val outBuf = aacEncoder.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (!isConfig) {
                                outBuf.position(bufferInfo.offset)
                                val aacBytes = ByteArray(bufferInfo.size)
                                outBuf.get(aacBytes)
                                val tsMs = (bufferInfo.presentationTimeUs / 1000L).toInt().coerceAtLeast(0)
                                val flvAudio = VideoDemuxer.buildFlvAacRawPacket(aacBytes, aacBytes.size)
                                onPacketReady(
                                    FlvMediaPacket(
                                        isVideo = false,
                                        isKeyframe = false,
                                        isSequenceHeader = false,
                                        timestampMs = tsMs,
                                        payload = flvAudio
                                    )
                                )
                            }
                        }
                        aacEncoder.releaseOutputBuffer(outIdx, false)
                    }
                }

                frameIndex += 1
                val targetMs = startWallMs + (frameIndex * frameDurationMs).toLong()
                val sleepMs = targetMs - SystemClock.elapsedRealtime()
                if (sleepMs > 2L) {
                    delay(sleepMs)
                }
            }
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            Log.e(TAG, "AAC audio encoder error", e)
        } finally {
            producerJob.cancel()
            try { monitorTrack?.stop() } catch (_: Exception) {}
            try { monitorTrack?.release() } catch (_: Exception) {}
            try { aacEncoder.stop() } catch (_: Exception) {}
            try { aacEncoder.release() } catch (_: Exception) {}
        }
    }

    /**
     * Parses a standard RIFF WAV file generated by Android TTS and resamples it via linear interpolation
     * into 44,100 Hz 16-bit Stereo PCM ready for the AAC broadcast encoder.
     */
    private fun readWavAs44100StereoPcm(file: File): ShortArray {
        val bytes = FileInputStream(file).use { it.readBytes() }
        if (bytes.size < 44) return ShortArray(0)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        var channels = 1
        var sampleRate = 22050
        var dataOffset = 44
        var dataSize = (bytes.size - 44).coerceAtLeast(0)

        // Scan RIFF chunks safely so non-44-byte WAV headers also parse accurately
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkLen = bb.getInt(pos + 4).coerceAtLeast(0)
            val chunkBody = pos + 8
            if (chunkId == "fmt " && chunkBody + 16 <= bytes.size) {
                channels = bb.getShort(chunkBody + 2).toInt().coerceIn(1, 2)
                sampleRate = bb.getInt(chunkBody + 4).coerceIn(8000, 96000)
            } else if (chunkId == "data") {
                dataOffset = chunkBody
                dataSize = chunkLen.coerceAtMost(bytes.size - chunkBody)
                break
            }
            pos = chunkBody + chunkLen + (chunkLen and 1)
        }

        val srcSampleCount = dataSize / (2 * channels)
        if (srcSampleCount <= 0) return ShortArray(0)

        val srcMonoOrLeft = ShortArray(srcSampleCount)
        val srcRight = ShortArray(srcSampleCount)
        var bytePos = dataOffset
        for (i in 0 until srcSampleCount) {
            if (bytePos + 2 > bytes.size) break
            val left = bb.getShort(bytePos)
            srcMonoOrLeft[i] = left
            if (channels == 2 && bytePos + 4 <= bytes.size) {
                srcRight[i] = bb.getShort(bytePos + 2)
                bytePos += 4
            } else {
                srcRight[i] = left
                bytePos += 2
            }
        }

        val ratio = sampleRate.toDouble() / OUT_AUDIO_SAMPLE_RATE.toDouble()
        val outFrames = ((srcSampleCount / ratio).toInt()).coerceAtLeast(1)
        val outStereo = ShortArray(outFrames * 2)
        for (i in 0 until outFrames) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, srcSampleCount - 1)
            val idx1 = (idx0 + 1).coerceAtMost(srcSampleCount - 1)
            val frac = (srcPos - idx0).toFloat()
            val l = (srcMonoOrLeft[idx0] * (1f - frac) + srcMonoOrLeft[idx1] * frac).toInt().toShort()
            val r = (srcRight[idx0] * (1f - frac) + srcRight[idx1] * frac).toInt().toShort()
            outStereo[i * 2] = l
            outStereo[i * 2 + 1] = r
        }
        return outStereo
    }

    private fun stripAnnexBPrefix(raw: ByteArray): ByteArray {
        if (raw.size >= 4 && raw[0].toInt() == 0 && raw[1].toInt() == 0 && raw[2].toInt() == 0 && raw[3].toInt() == 1) {
            return raw.copyOfRange(4, raw.size)
        }
        if (raw.size >= 3 && raw[0].toInt() == 0 && raw[1].toInt() == 0 && raw[2].toInt() == 1) {
            return raw.copyOfRange(3, raw.size)
        }
        return raw
    }
}

/**
 * Minimal OpenGL ES 2.0 / EGL14 offscreen compositor that blends a hardware MediaPlayer
 * SurfaceTexture (OES) underneath a transparent News Desk Bitmap texture onto a MediaCodec input Surface.
 */
private class EglOverlayCompositor(
    outputSurface: Surface,
    private val width: Int,
    private val height: Int
) {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private val videoTexId: Int
    private val overlayTexId: Int
    private val videoProgram: Int
    private val overlayProgram: Int

    val videoSurfaceTexture: SurfaceTexture
    private val stMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val quadBuffer: FloatBuffer

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1, // EGL_RECORDABLE_ANDROID
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        val config = configs[0] ?: throw IOException("No compatible EGLConfig found")

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        val surfAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, outputSurface, surfAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        val quadCoords = floatArrayOf(
            // X, Y, U, V
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )
        quadBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadCoords)
        quadBuffer.position(0)

        val texIds = IntArray(2)
        GLES20.glGenTextures(2, texIds, 0)
        videoTexId = texIds[0]
        overlayTexId = texIds[1]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        videoProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_OES)
        overlayProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D)

        videoSurfaceTexture = SurfaceTexture(videoTexId)
        videoSurfaceTexture.setDefaultBufferSize(width, height)
    }

    fun drawFrame(overlayBitmap: Bitmap, presentationTimeNs: Long) {
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        try {
            videoSurfaceTexture.updateTexImage()
            videoSurfaceTexture.getTransformMatrix(stMatrix)
        } catch (_: Exception) {
            Matrix.setIdentityM(stMatrix, 0)
        }

        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0.04f, 0.06f, 0.10f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Pass 1: Draw underlying video footage
        GLES20.glDisable(GLES20.GL_BLEND)
        drawQuad(videoProgram, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexId, stMatrix, flipY = false)

        // Pass 2: Alpha-blend crisp TV News Overlay (Logo + LIVE + Headline + Scrolling Ticker)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlayBitmap, 0)
        drawQuad(overlayProgram, GLES20.GL_TEXTURE_2D, overlayTexId, identityMatrix, flipY = true)

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun drawQuad(
        program: Int,
        texTarget: Int,
        texId: Int,
        texMatrix: FloatArray,
        flipY: Boolean
    ) {
        GLES20.glUseProgram(program)
        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        val matLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        val flipLoc = GLES20.glGetUniformLocation(program, "uFlipY")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(texTarget, texId)
        GLES20.glUniformMatrix4fv(matLoc, 1, false, texMatrix, 0)
        GLES20.glUniform1f(flipLoc, if (flipY) 1f else 0f)

        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        quadBuffer.position(2)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
    }

    fun release() {
        try { videoSurfaceTexture.release() } catch (_: Exception) {}
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun createProgram(vSrc: String, fSrc: String): Int {
        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vSrc)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vShader)
        GLES20.glAttachShader(program, fShader)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            uniform float uFlipY;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vec2 uv = aTexCoord.xy;
                if (uFlipY > 0.5) {
                    uv.y = 1.0 - uv.y;
                }
                vTexCoord = (uTexMatrix * vec4(uv, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_SHADER_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        private const val FRAGMENT_SHADER_2D = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
