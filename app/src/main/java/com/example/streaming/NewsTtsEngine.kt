package com.example.streaming

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.TtsLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TtsPresenterState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val currentSentence: String = "",
    val activeChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val statusNote: String? = null
)

/**
 * Automated News Presenter Voice Engine powered by Android TextToSpeech.
 * Supports both live studio preview playback AND PCM WAV synthesis for real-time AAC broadcast mixing.
 */
object NewsTtsEngine {
    private const val TAG = "NewsTtsEngine"

    private var tts: TextToSpeech? = null
    private var initDeferred: CompletableDeferred<Boolean>? = null
    private val synthWaiters = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val speakChunks = ConcurrentHashMap<String, Pair<Int, String>>()

    private val _state = MutableStateFlow(TtsPresenterState())
    val state: StateFlow<TtsPresenterState> = _state.asStateFlow()

    @Synchronized
    fun ensureInitialized(context: Context): CompletableDeferred<Boolean> {
        val existing = initDeferred
        if (tts != null && existing != null) {
            return existing
        }
        val deferred = CompletableDeferred<Boolean>()
        initDeferred = deferred
        val appContext = context.applicationContext

        tts = TextToSpeech(appContext) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == null) return
                        speakChunks[utteranceId]?.let { (idx, sentence) ->
                            _state.update {
                                it.copy(
                                    isSpeaking = true,
                                    activeChunkIndex = idx,
                                    currentSentence = sentence
                                )
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == null) return
                        synthWaiters.remove(utteranceId)?.complete(true)
                        val removed = speakChunks.remove(utteranceId)
                        if (removed != null && speakChunks.isEmpty()) {
                            _state.update {
                                it.copy(
                                    isSpeaking = false,
                                    currentSentence = ""
                                )
                            }
                        }
                    }

                    @Deprecated("Deprecated in Android API")
                    override fun onError(utteranceId: String?) {
                        handleUtteranceFailure(utteranceId)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        handleUtteranceFailure(utteranceId)
                    }
                })
                _state.update { it.copy(isReady = true, statusNote = null) }
            } else {
                Log.w(TAG, "TextToSpeech init failed with status=$status")
                _state.update {
                    it.copy(
                        isReady = false,
                        statusNote = "ডিভাইসে Text-to-Speech ইঞ্জিন প্রস্তুত নয়।"
                    )
                }
            }
            if (!deferred.isCompleted) deferred.complete(ok)
        }
        return deferred
    }

    private fun handleUtteranceFailure(utteranceId: String?) {
        if (utteranceId == null) return
        synthWaiters.remove(utteranceId)?.complete(false)
        speakChunks.remove(utteranceId)
        if (speakChunks.isEmpty()) {
            _state.update { it.copy(isSpeaking = false) }
        }
    }

    fun splitNewsScript(script: String): List<String> {
        val cleaned = script.trim()
        if (cleaned.isEmpty()) return emptyList()
        return cleaned
            .split(Regex("[।.!?\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { sentence ->
                if (sentence.length <= 240) listOf(sentence)
                else sentence.chunked(240)
            }
    }

    private fun configureVoice(language: TtsLanguage, speed: Float, pitch: Float) {
        val engine = tts ?: return
        val primaryLocale = when (language) {
            TtsLanguage.BN -> Locale("bn", "BD")
            TtsLanguage.EN -> Locale.US
        }
        val res = engine.setLanguage(primaryLocale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallbackLocale = when (language) {
                TtsLanguage.BN -> Locale("bn", "IN")
                TtsLanguage.EN -> Locale.UK
            }
            val fallbackRes = engine.setLanguage(fallbackLocale)
            if (fallbackRes == TextToSpeech.LANG_MISSING_DATA || fallbackRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.getDefault())
            }
        }
        engine.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
        engine.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    /**
     * Speaks the news script aloud immediately through device audio for live studio preview/monitoring.
     */
    suspend fun previewSpeak(
        context: Context,
        script: String,
        language: TtsLanguage,
        speed: Float,
        pitch: Float
    ) {
        val ready = withTimeoutOrNull(4000L) { ensureInitialized(context).await() } ?: false
        if (!ready) return
        val chunks = splitNewsScript(script)
        if (chunks.isEmpty()) return

        val engine = tts ?: return
        stopPreview()
        configureVoice(language, speed, pitch)

        _state.update {
            it.copy(
                isSpeaking = true,
                activeChunkIndex = 0,
                totalChunks = chunks.size,
                currentSentence = chunks.first()
            )
        }

        chunks.forEachIndexed { idx, chunk ->
            val id = "preview_${UUID.randomUUID()}"
            speakChunks[id] = idx to chunk
            val queueMode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            engine.speak(chunk, queueMode, params, id)
        }
    }

    fun stopPreview() {
        speakChunks.clear()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _state.update { it.copy(isSpeaking = false, currentSentence = "") }
    }

    /**
     * Synthesizes one news sentence to a 16-bit PCM WAV file for real-time AAC encoding into RTMP.
     */
    suspend fun synthesizeChunkToWav(
        context: Context,
        sentence: String,
        language: TtsLanguage,
        speed: Float,
        pitch: Float,
        outputFile: File
    ): Boolean {
        val ready = withTimeoutOrNull(4000L) { ensureInitialized(context).await() } ?: false
        if (!ready) return false
        val engine = tts ?: return false

        configureVoice(language, speed, pitch)
        val utteranceId = "synth_${UUID.randomUUID()}"
        val waiter = CompletableDeferred<Boolean>()
        synthWaiters[utteranceId] = waiter

        val params = Bundle()
        val result = engine.synthesizeToFile(sentence, params, outputFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            synthWaiters.remove(utteranceId)
            return false
        }
        return withTimeoutOrNull(25_000L) { waiter.await() } ?: false
    }

    fun updateLiveAnchorCaption(sentence: String, index: Int, total: Int, speaking: Boolean) {
        _state.update {
            it.copy(
                isSpeaking = speaking,
                currentSentence = sentence,
                activeChunkIndex = index,
                totalChunks = total
            )
        }
    }
}
