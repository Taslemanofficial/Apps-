package com.example.streaming

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/**
 * Pure-Kotlin AMF0 binary encoder & lightweight decoder for RTMP 1.0 commands and metadata.
 */
object Amf0Codec {
    private const val TYPE_NUMBER = 0x00
    private const val TYPE_BOOLEAN = 0x01
    private const val TYPE_STRING = 0x02
    private const val TYPE_OBJECT = 0x03
    private const val TYPE_NULL = 0x05
    private const val TYPE_ECMA_ARRAY = 0x08
    private const val TYPE_OBJECT_END = 0x09

    fun writeNumber(out: DataOutputStream, value: Double) {
        out.writeByte(TYPE_NUMBER)
        out.writeLong(java.lang.Double.doubleToRawLongBits(value))
    }

    fun writeBoolean(out: DataOutputStream, value: Boolean) {
        out.writeByte(TYPE_BOOLEAN)
        out.writeByte(if (value) 1 else 0)
    }

    fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeByte(TYPE_STRING)
        out.writeShort(bytes.size)
        out.write(bytes)
    }

    fun writeNull(out: DataOutputStream) {
        out.writeByte(TYPE_NULL)
    }

    private fun writePropertyKey(out: DataOutputStream, key: String) {
        val bytes = key.toByteArray(Charsets.UTF_8)
        out.writeShort(bytes.size)
        out.write(bytes)
    }

    fun writeObject(out: DataOutputStream, properties: Map<String, Any?>) {
        out.writeByte(TYPE_OBJECT)
        for ((k, v) in properties) {
            writePropertyKey(out, k)
            writeAny(out, v)
        }
        out.writeShort(0)
        out.writeByte(TYPE_OBJECT_END)
    }

    fun writeEcmaArray(out: DataOutputStream, properties: Map<String, Any?>) {
        out.writeByte(TYPE_ECMA_ARRAY)
        out.writeInt(properties.size)
        for ((k, v) in properties) {
            writePropertyKey(out, k)
            writeAny(out, v)
        }
        out.writeShort(0)
        out.writeByte(TYPE_OBJECT_END)
    }

    fun writeAny(out: DataOutputStream, value: Any?) {
        when (value) {
            null -> writeNull(out)
            is Double -> writeNumber(out, value)
            is Number -> writeNumber(out, value.toDouble())
            is Boolean -> writeBoolean(out, value)
            is String -> writeString(out, value)
            is Map<*, *> -> {
                val map = LinkedHashMap<String, Any?>()
                value.forEach { (k, v) -> if (k is String) map[k] = v }
                writeObject(out, map)
            }
            else -> writeString(out, value.toString())
        }
    }

    fun encodeCommand(vararg values: Any?): ByteArray {
        val baos = ByteArrayOutputStream(256)
        val dos = DataOutputStream(baos)
        values.forEach { writeAny(dos, it) }
        dos.flush()
        return baos.toByteArray()
    }

    fun encodeMetadata(properties: Map<String, Any?>): ByteArray {
        val baos = ByteArrayOutputStream(256)
        val dos = DataOutputStream(baos)
        writeString(dos, "@setDataFrame")
        writeString(dos, "onMetaData")
        writeEcmaArray(dos, properties)
        dos.flush()
        return baos.toByteArray()
    }

    /**
     * Decodes a sequence of AMF0 values from an RTMP command message payload.
     */
    fun decodeAll(bytes: ByteArray): List<Any?> {
        val buf = ByteBuffer.wrap(bytes)
        val result = mutableListOf<Any?>()
        while (buf.hasRemaining()) {
            val value = readValue(buf) ?: break
            result.add(value)
        }
        return result
    }

    private fun readValue(buf: ByteBuffer): Any? {
        if (!buf.hasRemaining()) return null
        return when (buf.get().toInt() and 0xFF) {
            TYPE_NUMBER -> {
                if (buf.remaining() < 8) null else java.lang.Double.longBitsToDouble(buf.long)
            }
            TYPE_BOOLEAN -> {
                if (!buf.hasRemaining()) null else (buf.get().toInt() != 0)
            }
            TYPE_STRING -> readStringBody(buf)
            TYPE_NULL -> "null"
            TYPE_OBJECT -> readObjectBody(buf)
            TYPE_ECMA_ARRAY -> {
                if (buf.remaining() < 4) return null
                buf.int // count hint
                readObjectBody(buf)
            }
            TYPE_OBJECT_END -> null
            else -> null
        }
    }

    private fun readStringBody(buf: ByteBuffer): String? {
        if (buf.remaining() < 2) return null
        val len = buf.short.toInt() and 0xFFFF
        if (buf.remaining() < len) return null
        val strBytes = ByteArray(len)
        buf.get(strBytes)
        return String(strBytes, Charsets.UTF_8)
    }

    private fun readObjectBody(buf: ByteBuffer): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        while (buf.remaining() >= 3) {
            val keyLen = buf.short.toInt() and 0xFFFF
            if (keyLen == 0) {
                val endMarker = buf.get().toInt() and 0xFF
                if (endMarker == TYPE_OBJECT_END) break
                break
            }
            if (buf.remaining() < keyLen) break
            val keyBytes = ByteArray(keyLen)
            buf.get(keyBytes)
            val key = String(keyBytes, Charsets.UTF_8)
            val value = readValue(buf)
            map[key] = value
        }
        return map
    }
}
