package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StreamRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUid: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Google সাইন-ইন ছাড়া ক্লাউড ডেটা অ্যাক্সেস করা যাবে না।")
        if (expectedUid != null) {
            check(uid == expectedUid) { "ইউজার সেশন পরিবর্তিত হয়েছে।" }
        }
        return uid
    }

    fun observeDestinations(userId: String): Flow<List<StreamDestination>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/destinations"
        emitAll(
            db.collection("users").document(uid).collection("destinations")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StreamDestination::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { platformSortOrder(it.platformType) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSessions(userId: String): Flow<List<BroadcastSession>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/sessions"
        emitAll(
            db.collection("users").document(uid).collection("sessions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BroadcastSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureDefaultDestinations(userId: String): Result<Unit> {
        val basePath = "users/$userId/destinations"
        return try {
            val uid = requireUserId(userId)
            val colRef = db.collection("users").document(uid).collection("destinations")
            val existing = colRef.get().await()
            if (!existing.isEmpty) {
                return Result.success(Unit)
            }
            val defaults = listOf(
                Triple("default_youtube", PlatformType.YOUTUBE, "YouTube Live"),
                Triple("default_facebook", PlatformType.FACEBOOK, "Facebook Live"),
                Triple("default_instagram", PlatformType.INSTAGRAM, "Instagram Live")
            )
            val batch = db.batch()
            defaults.forEach { (docId, platform, label) ->
                val payload = mapOf(
                    "userId" to uid,
                    "platform" to platform.name,
                    "label" to label,
                    "rtmpUrl" to platform.defaultRtmpUrl,
                    "streamKey" to "",
                    "enabled" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(colRef.document(docId), payload)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, basePath)
            Result.failure(e)
        }
    }

    suspend fun createDestination(
        userId: String,
        platform: PlatformType,
        label: String,
        rtmpUrl: String,
        streamKey: String,
        enabled: Boolean
    ): Result<String> {
        var path = "users/$userId/destinations"
        return try {
            val uid = requireUserId(userId)
            val cleanLabel = label.trim().take(60)
            val cleanUrl = rtmpUrl.trim().take(300)
            val cleanKey = streamKey.trim().take(256)
            require(cleanLabel.isNotEmpty()) { "প্ল্যাটফর্মের নাম দিন।" }
            require(cleanUrl.startsWith("rtmp://") || cleanUrl.startsWith("rtmps://")) {
                "সঠিক RTMP বা RTMPS সার্ভার URL দিন (rtmp:// অথবা rtmps://)।"
            }
            val docRef = db.collection("users").document(uid).collection("destinations").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "platform" to platform.name,
                "label" to cleanLabel,
                "rtmpUrl" to cleanUrl,
                "streamKey" to cleanKey,
                "enabled" to enabled,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateDestination(
        userId: String,
        destinationId: String,
        label: String,
        rtmpUrl: String,
        streamKey: String,
        enabled: Boolean
    ): Result<Unit> {
        val path = "users/$userId/destinations/$destinationId"
        return try {
            val uid = requireUserId(userId)
            require(destinationId.isNotBlank()) { "Destination ID খালি হতে পারবে না।" }
            val cleanLabel = label.trim().take(60)
            val cleanUrl = rtmpUrl.trim().take(300)
            val cleanKey = streamKey.trim().take(256)
            require(cleanLabel.isNotEmpty()) { "প্ল্যাটফর্মের নাম দিন।" }
            require(cleanUrl.startsWith("rtmp://") || cleanUrl.startsWith("rtmps://")) {
                "URL অবশ্যই rtmp:// বা rtmps:// দিয়ে শুরু হতে হবে।"
            }
            db.collection("users").document(uid)
                .collection("destinations").document(destinationId)
                .update(
                    mapOf(
                        "label" to cleanLabel,
                        "rtmpUrl" to cleanUrl,
                        "streamKey" to cleanKey,
                        "enabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleDestination(
        userId: String,
        destinationId: String,
        enabled: Boolean
    ): Result<Unit> {
        val path = "users/$userId/destinations/$destinationId"
        return try {
            val uid = requireUserId(userId)
            require(destinationId.isNotBlank()) { "Destination ID খালি হতে পারবে না।" }
            db.collection("users").document(uid)
                .collection("destinations").document(destinationId)
                .update(
                    mapOf(
                        "enabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDestination(userId: String, destinationId: String): Result<Unit> {
        val path = "users/$userId/destinations/$destinationId"
        return try {
            val uid = requireUserId(userId)
            require(destinationId.isNotBlank()) { "Destination ID খালি হতে পারবে না।" }
            db.collection("users").document(uid)
                .collection("destinations").document(destinationId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun startBroadcastSession(
        userId: String,
        title: String,
        videoCount: Int,
        platforms: List<String>
    ): Result<String> {
        var path = "users/$userId/sessions"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().ifEmpty { "Multi-Platform Live" }.take(120)
            val validCount = videoCount.coerceIn(1, 100).toLong()
            val validPlatforms = platforms.map { it.trim().take(60) }.filter { it.isNotEmpty() }.take(10)
            require(validPlatforms.isNotEmpty()) { "কমপক্ষে একটি প্ল্যাটফর্ম প্রয়োজন।" }
            val docRef = db.collection("users").document(uid).collection("sessions").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "videoCount" to validCount,
                "platforms" to validPlatforms,
                "status" to SessionStatus.LIVE.name,
                "durationSec" to 0L,
                "avgBitrateKbps" to 0L,
                "totalBytesSent" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun finishBroadcastSession(
        userId: String,
        sessionId: String,
        finalStatus: SessionStatus,
        durationSec: Long,
        avgBitrateKbps: Long,
        totalBytesSent: Long
    ): Result<Unit> {
        val path = "users/$userId/sessions/$sessionId"
        return try {
            val uid = requireUserId(userId)
            require(sessionId.isNotBlank()) { "Session ID খালি হতে পারবে না।" }
            val resolvedStatus = if (finalStatus == SessionStatus.LIVE) SessionStatus.COMPLETED else finalStatus
            db.collection("users").document(uid)
                .collection("sessions").document(sessionId)
                .update(
                    mapOf(
                        "status" to resolvedStatus.name,
                        "durationSec" to durationSec.coerceIn(0L, 604800L),
                        "avgBitrateKbps" to avgBitrateKbps.coerceIn(0L, 100000L),
                        "totalBytesSent" to totalBytesSent.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> {
        val path = "users/$userId/sessions/$sessionId"
        return try {
            val uid = requireUserId(userId)
            require(sessionId.isNotBlank()) { "Session ID খালি হতে পারবে না।" }
            db.collection("users").document(uid)
                .collection("sessions").document(sessionId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeNewsPresets(userId: String): Flow<List<NewsDeskPreset>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/newsPresets"
        emitAll(
            db.collection("users").document(uid).collection("newsPresets")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        NewsDeskPreset::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveNewsPreset(
        userId: String,
        presetId: String?,
        preset: NewsDeskPreset
    ): Result<String> {
        var path = "users/$userId/newsPresets"
        return try {
            val uid = requireUserId(userId)
            val colRef = db.collection("users").document(uid).collection("newsPresets")
            val docRef = if (presetId.isNullOrBlank()) colRef.document() else colRef.document(presetId)
            path = docRef.path

            val cleanChannel = preset.channelName.trim().ifEmpty { "StreamNews 24" }.take(40)
            val cleanLocation = preset.locationTag.trim().take(40)
            val cleanTag = preset.breakingTag.trim().ifEmpty { "ব্রেকিং নিউজ" }.take(30)
            val cleanHeadline = preset.headline.trim().take(140)
            val cleanTicker = preset.tickerText.trim().take(1000)
            val cleanScript = preset.newsScript.trim().take(4000)
            val cleanLang = TtsLanguage.fromRaw(preset.ttsLanguage).name
            val cleanSpeed = preset.ttsSpeed.coerceIn(0.5, 2.0)
            val cleanPitch = preset.ttsPitch.coerceIn(0.5, 2.0)
            val cleanTickerSpeed = TickerSpeed.fromRaw(preset.tickerSpeed).name

            val existingDoc = if (!presetId.isNullOrBlank()) docRef.get().await() else null
            if (existingDoc != null && existingDoc.exists()) {
                docRef.update(
                    mapOf(
                        "channelName" to cleanChannel,
                        "locationTag" to cleanLocation,
                        "breakingTag" to cleanTag,
                        "headline" to cleanHeadline,
                        "tickerText" to cleanTicker,
                        "newsScript" to cleanScript,
                        "ttsLanguage" to cleanLang,
                        "ttsSpeed" to cleanSpeed,
                        "ttsPitch" to cleanPitch,
                        "tickerSpeed" to cleanTickerSpeed,
                        "newsModeEnabled" to preset.newsModeEnabled,
                        "ttsEnabled" to preset.ttsEnabled,
                        "showLogo" to preset.showLogo,
                        "showTicker" to preset.showTicker,
                        "showHeadline" to preset.showHeadline,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "channelName" to cleanChannel,
                        "locationTag" to cleanLocation,
                        "breakingTag" to cleanTag,
                        "headline" to cleanHeadline,
                        "tickerText" to cleanTicker,
                        "newsScript" to cleanScript,
                        "ttsLanguage" to cleanLang,
                        "ttsSpeed" to cleanSpeed,
                        "ttsPitch" to cleanPitch,
                        "tickerSpeed" to cleanTickerSpeed,
                        "newsModeEnabled" to preset.newsModeEnabled,
                        "ttsEnabled" to preset.ttsEnabled,
                        "showLogo" to preset.showLogo,
                        "showTicker" to preset.showTicker,
                        "showHeadline" to preset.showHeadline,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteNewsPreset(userId: String, presetId: String): Result<Unit> {
        val path = "users/$userId/newsPresets/$presetId"
        return try {
            val uid = requireUserId(userId)
            require(presetId.isNotBlank()) { "Preset ID খালি হতে পারবে না।" }
            db.collection("users").document(uid)
                .collection("newsPresets").document(presetId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun platformSortOrder(type: PlatformType): Int = when (type) {
        PlatformType.YOUTUBE -> 0
        PlatformType.FACEBOOK -> 1
        PlatformType.INSTAGRAM -> 2
        PlatformType.CUSTOM -> 3
    }
}
