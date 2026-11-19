package com.example.streaming

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

data class QueuedVideo(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val videoMime: String,
    val audioMime: String?,
    val isH264Compatible: Boolean,
    val thumbnail: Bitmap?
)

data class VideoStreamMetadata(
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val videoBitrateKbps: Int,
    val audioBitrateKbps: Int,
    val audioSampleRate: Int,
    val audioChannelCount: Int,
    val avcSequenceHeader: ByteArray?,
    val aacSequenceHeader: ByteArray?
)

data class FlvMediaPacket(
    val isVideo: Boolean,
    val isKeyframe: Boolean,
    val isSequenceHeader: Boolean,
    val timestampMs: Int,
    val payload: ByteArray
)

/**
 * Extracts real metadata, thumbnails, and H.264/AAC FLV packets from user-uploaded Android video Uris
 * using Android's native MediaExtractor and MediaMetadataRetriever.
 */
object VideoDemuxer {

    fun inspectVideo(context: Context, uri: Uri, itemId: String): QueuedVideo {
        var displayName = "video_${System.currentTimeMillis() % 10000}.mp4"
        var sizeBytes = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIdx >= 0) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {
        }

        var durationMs = 0L
        var width = 1280
        var height = 720
        var thumbnail: Bitmap? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1280
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 720
            val rawFrame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (rawFrame != null) {
                thumbnail = Bitmap.createScaledBitmap(rawFrame, 240, 135, true)
                if (thumbnail !== rawFrame) rawFrame.recycle()
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        var videoMime = "video/avc"
        var audioMime: String? = null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    videoMime = mime
                    if (fmt.containsKey(MediaFormat.KEY_WIDTH)) width = fmt.getInteger(MediaFormat.KEY_WIDTH)
                    if (fmt.containsKey(MediaFormat.KEY_HEIGHT)) height = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                } else if (mime.startsWith("audio/") && audioMime == null) {
                    audioMime = mime
                }
            }
        } catch (_: Exception) {
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }

        val isH264 = videoMime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)

        return QueuedVideo(
            id = itemId,
            uri = uri,
            displayName = displayName,
            durationMs = durationMs,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            videoMime = videoMime,
            audioMime = audioMime,
            isH264Compatible = isH264,
            thumbnail = thumbnail
        )
    }

    /**
     * Extracts stream metadata + FLV sequence headers (AVC SPS/PPS & AAC AudioSpecificConfig) from the video.
     */
    @Throws(IOException::class)
    fun extractStreamMetadata(context: Context, uri: Uri): VideoStreamMetadata {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var width = 1280
            var height = 720
            var frameRate = 30
            var videoBitrate = 2500
            var audioBitrate = 128
            var sampleRate = 44100
            var channels = 2
            var avcSeqHeader: ByteArray? = null
            var aacSeqHeader: ByteArray? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) && avcSeqHeader == null) {
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) width = format.getInteger(MediaFormat.KEY_WIDTH)
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(15, 60)
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        videoBitrate = (format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000).coerceIn(500, 12000)
                    }
                    val sps = format.getByteBuffer("csd-0")?.let { copyByteBuffer(it) }
                    val pps = format.getByteBuffer("csd-1")?.let { copyByteBuffer(it) }
                    if (sps != null && pps != null) {
                        avcSeqHeader = buildFlvAvcSequenceHeader(stripStartCode(sps), stripStartCode(pps))
                    }
                } else if (mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) && aacSeqHeader == null) {
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        audioBitrate = (format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000).coerceIn(64, 320)
                    }
                    val asc = format.getByteBuffer("csd-0")?.let { copyByteBuffer(it) }
                    if (asc != null && asc.isNotEmpty()) {
                        aacSeqHeader = buildFlvAacSequenceHeader(asc)
                    }
                }
            }

            if (avcSeqHeader == null) {
                throw IOException("ভিডিওটি H.264 (MP4/AVC) ফরম্যাটে হতে হবে যাতে YouTube, Facebook ও Instagram-এ লাইভ করা যায়।")
            }

            return VideoStreamMetadata(
                width = width,
                height = height,
                frameRate = frameRate,
                videoBitrateKbps = videoBitrate,
                audioBitrateKbps = audioBitrate,
                audioSampleRate = sampleRate,
                audioChannelCount = channels,
                avcSequenceHeader = avcSeqHeader,
                aacSequenceHeader = aacSeqHeader
            )
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * Builds an FLV AVC sequence header tag payload (0x17 0x00 0x00 0x00 0x00 + AVCDecoderConfigurationRecord).
     */
    fun buildFlvAvcSequenceHeader(sps: ByteArray, pps: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream(24 + sps.size + pps.size)
        // FLV Video Tag header: keyframe (1) + AVC (7) = 0x17; AVCPacketType = 0 (seq header); CTS = 0
        baos.write(0x17)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)

        // AVCDecoderConfigurationRecord (ISO/IEC 14496-15)
        baos.write(0x01) // configurationVersion
        baos.write(if (sps.size > 1) sps[1].toInt() and 0xFF else 0x64) // AVCProfileIndication
        baos.write(if (sps.size > 2) sps[2].toInt() and 0xFF else 0x00) // profile_compatibility
        baos.write(if (sps.size > 3) sps[3].toInt() and 0xFF else 0x1F) // AVCLevelIndication
        baos.write(0xFF) // 6 bits reserved (111111) + 2 bits lengthSizeMinusOne (3 => 4-byte NALU lengths)

        // numOfSequenceParameterSets (3 bits reserved 111 + 5 bits count = 1 => 0xE1)
        baos.write(0xE1)
        baos.write((sps.size ushr 8) and 0xFF)
        baos.write(sps.size and 0xFF)
        baos.write(sps)

        // numOfPictureParameterSets = 1
        baos.write(0x01)
        baos.write((pps.size ushr 8) and 0xFF)
        baos.write(pps.size and 0xFF)
        baos.write(pps)

        return baos.toByteArray()
    }

    /**
     * Builds an FLV AVC NALU packet payload (converting Annex-B start codes into 4-byte AVCC lengths if needed).
     */
    fun buildFlvAvcNaluPacket(sampleBytes: ByteArray, sampleLength: Int, isKeyframe: Boolean): ByteArray {
        val avccData = normalizeToAvcc(sampleBytes, sampleLength)
        val out = ByteArray(5 + avccData.size)
        out[0] = if (isKeyframe) 0x17 else 0x27
        out[1] = 0x01 // AVC NALU
        out[2] = 0x00 // CompositionTime = 0
        out[3] = 0x00
        out[4] = 0x00
        System.arraycopy(avccData, 0, out, 5, avccData.size)
        return out
    }

    fun buildFlvAacSequenceHeader(audioSpecificConfig: ByteArray): ByteArray {
        val out = ByteArray(2 + audioSpecificConfig.size)
        out[0] = 0xAF.toByte() // AAC (10), 44kHz (3), 16-bit (1), stereo (1)
        out[1] = 0x00 // AAC sequence header
        System.arraycopy(audioSpecificConfig, 0, out, 2, audioSpecificConfig.size)
        return out
    }

    fun buildFlvAacRawPacket(sampleBytes: ByteArray, sampleLength: Int): ByteArray {
        // Strip 7-byte ADTS header if present
        val offset = if (sampleLength > 7 &&
            (sampleBytes[0].toInt() and 0xFF) == 0xFF &&
            (sampleBytes[1].toInt() and 0xF0) == 0xF0
        ) 7 else 0
        val payloadLen = (sampleLength - offset).coerceAtLeast(0)
        val out = ByteArray(2 + payloadLen)
        out[0] = 0xAF.toByte()
        out[1] = 0x01 // AAC raw frame
        if (payloadLen > 0) {
            System.arraycopy(sampleBytes, offset, out, 2, payloadLen)
        }
        return out
    }

    private fun normalizeToAvcc(data: ByteArray, length: Int): ByteArray {
        if (length < 4) return data.copyOf(length)
        val isAnnexB = (data[0].toInt() == 0 && data[1].toInt() == 0 &&
            ((data[2].toInt() == 0 && data[3].toInt() == 1) || data[2].toInt() == 1))
        if (!isAnnexB) {
            return data.copyOf(length)
        }
        val baos = ByteArrayOutputStream(length + 16)
        var i = 0
        while (i < length) {
            val startCodeLen = findStartCodeLength(data, i, length)
            if (startCodeLen > 0) {
                i += startCodeLen
                val naluStart = i
                while (i < length && findStartCodeLength(data, i, length) == 0) {
                    i++
                }
                val naluLen = i - naluStart
                if (naluLen > 0) {
                    val naluType = data[naluStart].toInt() and 0x1F
                    // Skip inline AUD/SPS/PPS duplicates when streaming NALU packets
                    if (naluType != 7 && naluType != 8 && naluType != 9) {
                        baos.write((naluLen ushr 24) and 0xFF)
                        baos.write((naluLen ushr 16) and 0xFF)
                        baos.write((naluLen ushr 8) and 0xFF)
                        baos.write(naluLen and 0xFF)
                        baos.write(data, naluStart, naluLen)
                    }
                }
            } else {
                i++
            }
        }
        return baos.toByteArray()
    }

    private fun findStartCodeLength(data: ByteArray, offset: Int, limit: Int): Int {
        if (offset + 3 < limit &&
            data[offset].toInt() == 0 && data[offset + 1].toInt() == 0 &&
            data[offset + 2].toInt() == 0 && data[offset + 3].toInt() == 1
        ) return 4
        if (offset + 2 < limit &&
            data[offset].toInt() == 0 && data[offset + 1].toInt() == 0 &&
            data[offset + 2].toInt() == 1
        ) return 3
        return 0
    }

    private fun stripStartCode(raw: ByteArray): ByteArray {
        val scLen = findStartCodeLength(raw, 0, raw.size)
        return if (scLen > 0) raw.copyOfRange(scLen, raw.size) else raw
    }

    private fun copyByteBuffer(buf: ByteBuffer): ByteArray {
        val dup = buf.duplicate()
        val arr = ByteArray(dup.remaining())
        dup.get(arr)
        return arr
    }
}
