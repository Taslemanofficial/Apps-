package com.example.streaming

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Random
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class RtmpEndpoint(
    val isTls: Boolean,
    val host: String,
    val port: Int,
    val app: String,
    val tcUrl: String
)

data class ConnectionTestResult(
    val reachable: Boolean,
    val latencyMs: Long,
    val serverMessage: String
)

/**
 * Pure-Kotlin RTMP 1.0 / RTMPS (TLS over port 443) publisher client.
 * Supports YouTube Live, Facebook Live (RTMPS), Instagram Live Producer (RTMPS), and custom servers.
 */
class RtmpClient(
    private val rtmpUrl: String,
    private val streamKey: String
) {
    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null

    private var outChunkSize = DEFAULT_CHUNK_SIZE
    private var inChunkSize = DEFAULT_CHUNK_SIZE
    private var publishStreamId = 1
    private var transactionId = 1.0
    private var bytesReceivedAck = 0L
    private var windowAckSize = 2_500_000L

    // Per-chunk-stream state for incoming RTMP chunks
    private data class ChunkHeaderState(
        var timestamp: Int = 0,
        var messageLength: Int = 0,
        var messageTypeId: Int = 0,
        var messageStreamId: Int = 0,
        val buffer: ByteArrayOutputStream = ByteArrayOutputStream()
    )
    private val incomingChunks = HashMap<Int, ChunkHeaderState>()

    @Throws(IOException::class)
    fun connectAndPublish(metadata: VideoStreamMetadata): Long {
        val startMs = System.currentTimeMillis()
        val endpoint = parseEndpoint(rtmpUrl)
        openSocket(endpoint)
        performHandshake()

        // Raise outbound chunk size to 4096 bytes for smooth video delivery
        sendSetChunkSize(OUTBOUND_CHUNK_SIZE)
        outChunkSize = OUTBOUND_CHUNK_SIZE

        // 1. RTMP connect
        val connectTx = nextTx()
        val connectObj = linkedMapOf<String, Any?>(
            "app" to endpoint.app,
            "flashVer" to "FMLE/3.0 (compatible; StreamCast/1.0)",
            "swfUrl" to endpoint.tcUrl,
            "tcUrl" to endpoint.tcUrl,
            "fpad" to false,
            "capabilities" to 239.0,
            "audioCodecs" to 3575.0,
            "videoCodecs" to 252.0,
            "videoFunction" to 1.0
        )
        sendCommandMessage(CSID_COMMAND, 0, Amf0Codec.encodeCommand("connect", connectTx, connectObj))
        waitForCommandResult(connectTx, "connect")
        val rttMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        // 2. releaseStream + FCPublish (standard FMLE/OBS sequence expected by YouTube/Facebook/Instagram)
        val cleanKey = streamKey.trim()
        sendCommandMessage(CSID_COMMAND, 0, Amf0Codec.encodeCommand("releaseStream", nextTx(), null, cleanKey))
        sendCommandMessage(CSID_COMMAND, 0, Amf0Codec.encodeCommand("FCPublish", nextTx(), null, cleanKey))

        // 3. createStream
        val createTx = nextTx()
        sendCommandMessage(CSID_COMMAND, 0, Amf0Codec.encodeCommand("createStream", createTx, null))
        val createResult = waitForCommandResult(createTx, "createStream")
        val assignedId = (createResult.getOrNull(3) as? Double)?.toInt()
        if (assignedId != null && assignedId > 0) {
            publishStreamId = assignedId
        }

        // 4. publish
        sendCommandMessage(
            CSID_PUBLISH,
            publishStreamId,
            Amf0Codec.encodeCommand("publish", 0.0, null, cleanKey, "live")
        )
        waitForPublishStart()

        // 5. onMetaData
        val metaMap = linkedMapOf<String, Any?>(
            "duration" to 0.0,
            "width" to metadata.width.toDouble(),
            "height" to metadata.height.toDouble(),
            "videodatarate" to metadata.videoBitrateKbps.toDouble(),
            "framerate" to metadata.frameRate.toDouble(),
            "videocodecid" to 7.0, // AVC / H.264
            "audiodatarate" to metadata.audioBitrateKbps.toDouble(),
            "audiosamplerate" to metadata.audioSampleRate.toDouble(),
            "audiosamplesize" to 16.0,
            "stereo" to (metadata.audioChannelCount >= 2),
            "audiocodecid" to 10.0, // AAC
            "encoder" to "StreamCast Android RTMPS 1.0"
        )
        sendRtmpMessage(CSID_DATA, 0, MSG_DATA_AMF0, publishStreamId, Amf0Codec.encodeMetadata(metaMap))
        return rttMs
    }

    /**
     * Tests real TCP/TLS reachability + RTMP handshake + connect against the target ingest server.
     */
    fun testConnection(): ConnectionTestResult {
        val startMs = System.currentTimeMillis()
        return try {
            val endpoint = parseEndpoint(rtmpUrl)
            openSocket(endpoint)
            performHandshake()
            sendSetChunkSize(OUTBOUND_CHUNK_SIZE)
            outChunkSize = OUTBOUND_CHUNK_SIZE

            val tx = nextTx()
            val connectObj = linkedMapOf<String, Any?>(
                "app" to endpoint.app,
                "flashVer" to "FMLE/3.0 (compatible; StreamCast/1.0)",
                "tcUrl" to endpoint.tcUrl,
                "fpad" to false,
                "audioCodecs" to 3575.0,
                "videoCodecs" to 252.0,
                "videoFunction" to 1.0
            )
            sendCommandMessage(CSID_COMMAND, 0, Amf0Codec.encodeCommand("connect", tx, connectObj))
            waitForCommandResult(tx, "connect")
            val rtt = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            ConnectionTestResult(
                reachable = true,
                latencyMs = rtt,
                serverMessage = "${endpoint.host}:${endpoint.port} (${if (endpoint.isTls) "RTMPS/TLS" else "RTMP"}) সংযুক্ত"
            )
        } catch (e: Exception) {
            val rtt = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            ConnectionTestResult(
                reachable = false,
                latencyMs = rtt,
                serverMessage = e.localizedMessage ?: "সার্ভারে সংযোগ করা যায়নি"
            )
        } finally {
            closeQuietly()
        }
    }

    @Throws(IOException::class)
    fun sendVideoPacket(timestampMs: Int, flvVideoTagPayload: ByteArray): Int {
        sendRtmpMessage(CSID_VIDEO, timestampMs, MSG_VIDEO, publishStreamId, flvVideoTagPayload)
        return flvVideoTagPayload.size
    }

    @Throws(IOException::class)
    fun sendAudioPacket(timestampMs: Int, flvAudioTagPayload: ByteArray): Int {
        sendRtmpMessage(CSID_AUDIO, timestampMs, MSG_AUDIO, publishStreamId, flvAudioTagPayload)
        return flvAudioTagPayload.size
    }

    fun disconnectGracefully() {
        try {
            if (socket?.isConnected == true && socket?.isClosed == false) {
                sendCommandMessage(CSID_COMMAND, 0, Amf0Codec.encodeCommand("FCUnpublish", nextTx(), null, streamKey.trim()))
                sendCommandMessage(CSID_COMMAND, 0, Amf0Codec.encodeCommand("deleteStream", nextTx(), null, publishStreamId.toDouble()))
                output?.flush()
            }
        } catch (_: Exception) {
        } finally {
            closeQuietly()
        }
    }

    fun closeQuietly() {
        try { output?.close() } catch (_: Exception) {}
        try { input?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        output = null
        input = null
        socket = null
    }

    private fun openSocket(endpoint: RtmpEndpoint) {
        val rawSocket = Socket()
        rawSocket.tcpNoDelay = true
        rawSocket.soTimeout = SOCKET_TIMEOUT_MS
        rawSocket.connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)

        val activeSocket: Socket = if (endpoint.isTls) {
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = sslFactory.createSocket(rawSocket, endpoint.host, endpoint.port, true) as SSLSocket
            val sslParams = sslSocket.sslParameters
            sslParams.serverNames = listOf(SNIHostName(endpoint.host))
            sslSocket.sslParameters = sslParams
            sslSocket.soTimeout = SOCKET_TIMEOUT_MS
            sslSocket.startHandshake()
            sslSocket
        } else {
            rawSocket
        }

        socket = activeSocket
        input = BufferedInputStream(activeSocket.getInputStream(), 65536)
        output = BufferedOutputStream(activeSocket.getOutputStream(), 65536)
    }

    private fun performHandshake() {
        val out = output ?: throw IOException("Output stream unavailable")
        val inp = input ?: throw IOException("Input stream unavailable")

        // C0 (version 3) + C1 (1536 bytes: 4-byte time, 4-byte zero, 1528 random)
        val c1 = ByteArray(HANDSHAKE_SIZE)
        Random().nextBytes(c1)
        for (i in 0 until 8) c1[i] = 0
        out.write(0x03)
        out.write(c1)
        out.flush()

        // S0
        val s0 = inp.read()
        if (s0 != 0x03) {
            throw IOException("Unsupported RTMP server version: $s0")
        }
        // S1 + S2
        val s1 = readExact(inp, HANDSHAKE_SIZE)
        readExact(inp, HANDSHAKE_SIZE)

        // C2 echoes S1
        out.write(s1)
        out.flush()
    }

    private fun sendSetChunkSize(chunkSize: Int) {
        val payload = byteArrayOf(
            ((chunkSize ushr 24) and 0x7F).toByte(),
            ((chunkSize ushr 16) and 0xFF).toByte(),
            ((chunkSize ushr 8) and 0xFF).toByte(),
            (chunkSize and 0xFF).toByte()
        )
        sendRtmpMessage(CSID_PROTOCOL, 0, MSG_SET_CHUNK_SIZE, 0, payload)
    }

    private fun sendAcknowledgement(sequenceNumber: Int) {
        val payload = byteArrayOf(
            ((sequenceNumber ushr 24) and 0xFF).toByte(),
            ((sequenceNumber ushr 16) and 0xFF).toByte(),
            ((sequenceNumber ushr 8) and 0xFF).toByte(),
            (sequenceNumber and 0xFF).toByte()
        )
        sendRtmpMessage(CSID_PROTOCOL, 0, MSG_ACK, 0, payload)
    }

    private fun sendCommandMessage(csid: Int, streamId: Int, payload: ByteArray) {
        sendRtmpMessage(csid, 0, MSG_COMMAND_AMF0, streamId, payload)
    }

    @Synchronized
    private fun sendRtmpMessage(
        csid: Int,
        timestampMs: Int,
        messageTypeId: Int,
        messageStreamId: Int,
        payload: ByteArray
    ) {
        val out = output ?: throw IOException("RTMP socket not connected")
        val ts = timestampMs.coerceAtLeast(0)
        val hasExtendedTs = ts >= 0xFFFFFF
        val headerTs = if (hasExtendedTs) 0xFFFFFF else ts

        // Format 0 Chunk Basic Header (1 byte for csid 2..63)
        out.write(csid and 0x3F)

        // Format 0 Message Header (11 bytes)
        out.write((headerTs ushr 16) and 0xFF)
        out.write((headerTs ushr 8) and 0xFF)
        out.write(headerTs and 0xFF)

        val len = payload.size
        out.write((len ushr 16) and 0xFF)
        out.write((len ushr 8) and 0xFF)
        out.write(len and 0xFF)

        out.write(messageTypeId and 0xFF)

        // Message Stream ID is little-endian in RTMP fmt 0
        out.write(messageStreamId and 0xFF)
        out.write((messageStreamId ushr 8) and 0xFF)
        out.write((messageStreamId ushr 16) and 0xFF)
        out.write((messageStreamId ushr 24) and 0xFF)

        if (hasExtendedTs) {
            writeIntBE(out, ts)
        }

        var offset = 0
        while (offset < len) {
            if (offset > 0) {
                // Format 3 continuation chunk header
                out.write(0xC0 or (csid and 0x3F))
                if (hasExtendedTs) {
                    writeIntBE(out, ts)
                }
            }
            val chunkLen = minOf(outChunkSize, len - offset)
            out.write(payload, offset, chunkLen)
            offset += chunkLen
        }
        out.flush()
    }

    private fun waitForCommandResult(expectedTx: Double, label: String): List<Any?> {
        repeat(40) {
            val (typeId, payload) = readNextMessage()
            if (typeId == MSG_COMMAND_AMF0) {
                val values = Amf0Codec.decodeAll(payload)
                val cmd = values.firstOrNull() as? String
                val tx = values.getOrNull(1) as? Double
                if (cmd == "_result" && tx != null && tx == expectedTx) {
                    return values
                }
                if (cmd == "_error" && tx != null && tx == expectedTx) {
                    throw IOException("RTMP $label প্রত্যাখ্যাত হয়েছে: ${extractErrorDescription(values)}")
                }
            }
        }
        throw IOException("RTMP $label রেসপন্স টাইমআউট হয়েছে।")
    }

    private fun waitForPublishStart() {
        repeat(40) {
            val (typeId, payload) = readNextMessage()
            if (typeId == MSG_COMMAND_AMF0) {
                val values = Amf0Codec.decodeAll(payload)
                val cmd = values.firstOrNull() as? String
                if (cmd == "onStatus") {
                    val info = values.getOrNull(3) as? Map<*, *>
                    val code = info?.get("code")?.toString().orEmpty()
                    val desc = info?.get("description")?.toString().orEmpty()
                    if (code.contains("Publish.Start", ignoreCase = true)) {
                        return
                    }
                    if (code.contains("Failed", ignoreCase = true) ||
                        code.contains("BadName", ignoreCase = true) ||
                        code.contains("Rejected", ignoreCase = true)
                    ) {
                        throw IOException("Stream Key সঠিক নয় বা প্রত্যাখ্যাত ($code): $desc")
                    }
                } else if (cmd == "_error") {
                    throw IOException("Publish ব্যর্থ হয়েছে: ${extractErrorDescription(values)}")
                }
            }
        }
        throw IOException("সার্ভার থেকে NetStream.Publish.Start পাওয়া যায়নি।")
    }

    private fun readNextMessage(): Pair<Int, ByteArray> {
        val inp = input ?: throw IOException("Input stream closed")
        while (true) {
            val basicByte = inp.read()
            if (basicByte < 0) throw EOFException("RTMP সার্ভার সংযোগ বিচ্ছিন্ন করেছে।")
            bytesReceivedAck += 1

            val fmt = (basicByte ushr 6) and 0x03
            var csid = basicByte and 0x3F
            if (csid == 0) {
                csid = 64 + readByteU(inp)
            } else if (csid == 1) {
                csid = 64 + readByteU(inp) + 256 * readByteU(inp)
            }

            val state = incomingChunks.getOrPut(csid) { ChunkHeaderState() }
            when (fmt) {
                0 -> {
                    val ts = readInt24BE(inp)
                    state.messageLength = readInt24BE(inp)
                    state.messageTypeId = readByteU(inp)
                    state.messageStreamId = readInt32LE(inp)
                    state.timestamp = if (ts == 0xFFFFFF) readInt32BE(inp) else ts
                    state.buffer.reset()
                }
                1 -> {
                    val tsDelta = readInt24BE(inp)
                    state.messageLength = readInt24BE(inp)
                    state.messageTypeId = readByteU(inp)
                    state.timestamp += if (tsDelta == 0xFFFFFF) readInt32BE(inp) else tsDelta
                    state.buffer.reset()
                }
                2 -> {
                    val tsDelta = readInt24BE(inp)
                    state.timestamp += if (tsDelta == 0xFFFFFF) readInt32BE(inp) else tsDelta
                }
                3 -> {
                    if (state.timestamp >= 0xFFFFFF) {
                        readInt32BE(inp)
                    }
                }
            }

            val remaining = state.messageLength - state.buffer.size()
            if (remaining < 0) {
                state.buffer.reset()
                continue
            }
            val toRead = minOf(inChunkSize, remaining)
            if (toRead > 0) {
                val chunkData = readExact(inp, toRead)
                state.buffer.write(chunkData)
            }

            if (bytesReceivedAck >= windowAckSize) {
                sendAcknowledgement(bytesReceivedAck.toInt())
                bytesReceivedAck = 0L
            }

            if (state.buffer.size() >= state.messageLength) {
                val fullPayload = state.buffer.toByteArray()
                state.buffer.reset()

                when (state.messageTypeId) {
                    MSG_SET_CHUNK_SIZE -> {
                        if (fullPayload.size >= 4) {
                            inChunkSize = ((fullPayload[0].toInt() and 0x7F) shl 24) or
                                ((fullPayload[1].toInt() and 0xFF) shl 16) or
                                ((fullPayload[2].toInt() and 0xFF) shl 8) or
                                (fullPayload[3].toInt() and 0xFF)
                        }
                    }
                    MSG_WINDOW_ACK_SIZE -> {
                        if (fullPayload.size >= 4) {
                            windowAckSize = (((fullPayload[0].toInt() and 0xFF).toLong() shl 24) or
                                ((fullPayload[1].toInt() and 0xFF).toLong() shl 16) or
                                ((fullPayload[2].toInt() and 0xFF).toLong() shl 8) or
                                (fullPayload[3].toInt() and 0xFF).toLong()).coerceAtLeast(100_000L)
                        }
                    }
                    MSG_SET_PEER_BANDWIDTH, MSG_ACK, MSG_USER_CONTROL -> {
                        // Handled silently during handshake/control
                    }
                    else -> return state.messageTypeId to fullPayload
                }
            }
        }
    }

    private fun extractErrorDescription(values: List<Any?>): String {
        val map = values.getOrNull(3) as? Map<*, *>
        val desc = map?.get("description")?.toString()
        val code = map?.get("code")?.toString()
        return listOfNotNull(code, desc).joinToString(" - ").ifBlank { "অজানা সার্ভার ত্রুটি" }
    }

    private fun nextTx(): Double {
        val current = transactionId
        transactionId += 1.0
        return current
    }

    private fun readByteU(inp: BufferedInputStream): Int {
        val b = inp.read()
        if (b < 0) throw EOFException("Unexpected EOF in RTMP stream")
        bytesReceivedAck += 1
        return b and 0xFF
    }

    private fun readExact(inp: BufferedInputStream, count: Int): ByteArray {
        val data = ByteArray(count)
        var readTotal = 0
        while (readTotal < count) {
            val n = inp.read(data, readTotal, count - readTotal)
            if (n < 0) throw EOFException("Unexpected EOF reading $count bytes")
            readTotal += n
        }
        bytesReceivedAck += count
        return data
    }

    private fun readInt24BE(inp: BufferedInputStream): Int {
        val b1 = readByteU(inp)
        val b2 = readByteU(inp)
        val b3 = readByteU(inp)
        return (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readInt32BE(inp: BufferedInputStream): Int {
        val b1 = readByteU(inp)
        val b2 = readByteU(inp)
        val b3 = readByteU(inp)
        val b4 = readByteU(inp)
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    private fun readInt32LE(inp: BufferedInputStream): Int {
        val b1 = readByteU(inp)
        val b2 = readByteU(inp)
        val b3 = readByteU(inp)
        val b4 = readByteU(inp)
        return b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
    }

    private fun writeIntBE(out: BufferedOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    companion object {
        private const val TAG = "RtmpClient"
        private const val HANDSHAKE_SIZE = 1536
        private const val DEFAULT_CHUNK_SIZE = 128
        private const val OUTBOUND_CHUNK_SIZE = 4096
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val SOCKET_TIMEOUT_MS = 15_000

        private const val CSID_PROTOCOL = 2
        private const val CSID_COMMAND = 3
        private const val CSID_DATA = 4
        private const val CSID_AUDIO = 4
        private const val CSID_VIDEO = 6
        private const val CSID_PUBLISH = 8

        private const val MSG_SET_CHUNK_SIZE = 1
        private const val MSG_ACK = 3
        private const val MSG_USER_CONTROL = 4
        private const val MSG_WINDOW_ACK_SIZE = 5
        private const val MSG_SET_PEER_BANDWIDTH = 6
        private const val MSG_AUDIO = 8
        private const val MSG_VIDEO = 9
        private const val MSG_DATA_AMF0 = 18
        private const val MSG_COMMAND_AMF0 = 20

        fun parseEndpoint(rawUrl: String): RtmpEndpoint {
            val trimmed = rawUrl.trim()
            val uri = try {
                URI(trimmed)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid RTMP URI: $trimmed", e)
                throw IOException("অবৈধ RTMP URL: $trimmed")
            }
            val scheme = uri.scheme?.lowercase()
            val isTls = when (scheme) {
                "rtmps" -> true
                "rtmp" -> false
                else -> throw IOException("শুধুমাত্র rtmp:// অথবা rtmps:// সাপোর্টেড।")
            }
            val host = uri.host?.takeIf { it.isNotBlank() }
                ?: throw IOException("RTMP সার্ভারে হোস্টনেম পাওয়া যায়নি।")
            val port = if (uri.port > 0) uri.port else if (isTls) 443 else 1935
            val cleanPath = uri.path.orEmpty().trim('/')
            val app = cleanPath.ifEmpty { "live" }
            val querySuffix = uri.rawQuery?.let { "?$it" }.orEmpty()
            val tcUrl = "${scheme}://${host}:${port}/${app}${querySuffix}"
            return RtmpEndpoint(
                isTls = isTls,
                host = host,
                port = port,
                app = "$app$querySuffix",
                tcUrl = tcUrl
            )
        }
    }
}
