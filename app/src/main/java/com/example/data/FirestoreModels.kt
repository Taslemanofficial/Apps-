package com.example.data

import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class PlatformType(
    val displayName: String,
    val defaultRtmpUrl: String,
    val keyHelpTitle: String,
    val keyHelpSteps: List<String>
) {
    YOUTUBE(
        displayName = "YouTube Live",
        defaultRtmpUrl = "rtmp://a.rtmp.youtube.com/live2",
        keyHelpTitle = "YouTube Stream Key কোথা থেকে পাবেন?",
        keyHelpSteps = listOf(
            "১. YouTube Studio (studio.youtube.com) ওপেন করুন।",
            "২. উপরে ডানে 'Create' → 'Go live' এ ক্লিক করুন।",
            "৩. Stream Settings থেকে 'Stream key' কপি করে এখানে পেস্ট করুন।"
        )
    ),
    FACEBOOK(
        displayName = "Facebook Live",
        defaultRtmpUrl = "rtmps://live-api-s.facebook.com:443/rtmp/",
        keyHelpTitle = "Facebook Stream Key কোথা থেকে পাবেন?",
        keyHelpSteps = listOf(
            "১. Facebook Live Producer (facebook.com/live/producer) ওপেন করুন।",
            "২. Video Source হিসেবে 'Streaming software' সিলেক্ট করুন।",
            "৩. 'Stream key' কপি করুন (RTMPS Server URL আগে থেকেই সেট করা আছে)।"
        )
    ),
    INSTAGRAM(
        displayName = "Instagram Live",
        defaultRtmpUrl = "rtmps://live-upload.instagram.com:443/rtmp/",
        keyHelpTitle = "Instagram Stream Key কোথা থেকে পাবেন?",
        keyHelpSteps = listOf(
            "১. ব্রাউজারে Instagram.com (Professional Account) লগইন করুন।",
            "২. '+' (Create) → 'Live video' সিলেক্ট করে Title দিন।",
            "৩. 'Next' এ ক্লিক করলে Instagram Live Producer-এ Stream Key পাবেন।"
        )
    ),
    CUSTOM(
        displayName = "Custom RTMP",
        defaultRtmpUrl = "rtmp://",
        keyHelpTitle = "Custom RTMP / RTMPS সার্ভার সেটআপ",
        keyHelpSteps = listOf(
            "১. আপনার Twitch, Kick, TikTok বা নিজস্ব Nginx/SRS সার্ভারের RTMP বা RTMPS URL দিন।",
            "২. সেই সার্ভারের Stream Key পেস্ট করে Save করুন।"
        )
    );

    companion object {
        fun fromRaw(raw: String?): PlatformType =
            entries.firstOrNull { it.name == raw } ?: CUSTOM
    }
}

enum class SessionStatus {
    LIVE,
    COMPLETED,
    FAILED;

    companion object {
        fun fromRaw(raw: String?): SessionStatus =
            entries.firstOrNull { it.name == raw } ?: COMPLETED
    }
}

@Keep
@IgnoreExtraProperties
data class StreamDestination(
    @DocumentId val id: String = "",
    val userId: String = "",
    val platform: String = PlatformType.YOUTUBE.name,
    val label: String = "",
    val rtmpUrl: String = "",
    val streamKey: String = "",
    val enabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val platformType: PlatformType
        get() = PlatformType.fromRaw(platform)

    val isConfigured: Boolean
        get() = rtmpUrl.startsWith("rtmp://") || rtmpUrl.startsWith("rtmps://")

    val isReadyToBroadcast: Boolean
        get() = enabled && isConfigured && streamKey.isNotBlank()
}

@Keep
@IgnoreExtraProperties
data class BroadcastSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val videoCount: Long = 1L,
    val platforms: List<String> = emptyList(),
    val status: String = SessionStatus.LIVE.name,
    val durationSec: Long = 0L,
    val avgBitrateKbps: Long = 0L,
    val totalBytesSent: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val sessionStatus: SessionStatus
        get() = SessionStatus.fromRaw(status)
}

enum class TtsLanguage(val displayName: String, val localeCode: String) {
    BN("বাংলা (Bangla)", "bn-BD"),
    EN("English", "en-US");

    companion object {
        fun fromRaw(raw: String?): TtsLanguage =
            entries.firstOrNull { it.name == raw } ?: BN
    }
}

enum class TickerSpeed(val labelBn: String, val pxPerSecond: Float) {
    SLOW("ধীর", 85f),
    NORMAL("স্বাভাবিক", 135f),
    FAST("দ্রুত", 205f);

    companion object {
        fun fromRaw(raw: String?): TickerSpeed =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

@Keep
@IgnoreExtraProperties
data class NewsDeskPreset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelName: String = "StreamNews 24",
    val locationTag: String = "ঢাকা, বাংলাদেশ",
    val breakingTag: String = "ব্রেকিং নিউজ",
    val headline: String = "সরাসরি সংবাদ সম্প্রচার চলছে",
    val tickerText: String = "সর্বশেষ সংবাদ: ইউটিউব, ফেসবুক ও ইনস্টাগ্রামে একসাথে রিয়েল-টাইম লাইভ সংবাদ সম্প্রচার চলছে • যুক্ত থাকুন আমাদের সাথে।",
    val newsScript: String = "সুপ্রিয় দর্শক, আসসালামু আলাইকুম। স্বাগতম আমাদের সরাসরি সংবাদ বুলেটিনে। এখন শুনছেন আজকের সর্বশেষ খবর।",
    val ttsLanguage: String = TtsLanguage.BN.name,
    val ttsSpeed: Double = 1.0,
    val ttsPitch: Double = 1.0,
    val tickerSpeed: String = TickerSpeed.NORMAL.name,
    val newsModeEnabled: Boolean = true,
    val ttsEnabled: Boolean = true,
    val showLogo: Boolean = true,
    val showTicker: Boolean = true,
    val showHeadline: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val languageEnum: TtsLanguage
        get() = TtsLanguage.fromRaw(ttsLanguage)

    val tickerSpeedEnum: TickerSpeed
        get() = TickerSpeed.fromRaw(tickerSpeed)
}

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}
