package com.azarai.goworkbro.core.remote

/**
 * The wire framing every remote transport shares (BLE attribute chunks, adb
 * broadcasts, a future socket): one UTF-8 JSON document per frame, prefixed
 * with its byte length as a 4-byte big-endian header.
 *
 * GATT hands us at most (MTU - 3) bytes per attribute write, so a transport
 * must be able to split and rejoin frames; keeping that here means the
 * Bluetooth layer never has to understand JSON.
 */
object Framing {

    /** No single message may exceed this — a runaway length would otherwise
     *  let a peer make us allocate unbounded memory. */
    const val MAX_FRAME = 64 * 1024

    private const val HEADER = 4

    /** Thrown when a peer's byte stream is not a valid sequence of frames. */
    class MalformedFrameException(message: String) : Exception(message)

    fun encode(text: String): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        require(body.size in 1..MAX_FRAME) { "frame size ${body.size} out of range" }
        return ByteArray(HEADER + body.size).also { out ->
            out[0] = (body.size ushr 24).toByte()
            out[1] = (body.size ushr 16).toByte()
            out[2] = (body.size ushr 8).toByte()
            out[3] = body.size.toByte()
            body.copyInto(out, HEADER)
        }
    }

    /** Reassembles frames from arbitrary chunk boundaries. */
    class Reader(private val max: Int = MAX_FRAME) {

        private var buffer = ByteArray(HEADER)
        private var size = 0

        /** Feeds one chunk; returns every frame that is now complete. */
        fun feed(chunk: ByteArray): List<String> {
            append(chunk)
            val frames = ArrayList<String>(1)
            var start = 0
            while (size - start >= HEADER) {
                val length = (buffer[start].toInt() and 0xFF shl 24) or
                    (buffer[start + 1].toInt() and 0xFF shl 16) or
                    (buffer[start + 2].toInt() and 0xFF shl 8) or
                    (buffer[start + 3].toInt() and 0xFF)
                if (length < 1 || length > max) {
                    reset()
                    throw MalformedFrameException("bad frame length $length")
                }
                if (size - start - HEADER < length) break
                frames += String(buffer, start + HEADER, length, Charsets.UTF_8)
                start += HEADER + length
            }
            if (start > 0) {
                buffer.copyInto(buffer, 0, start, size)
                size -= start
            }
            return frames
        }

        fun reset() {
            size = 0
        }

        private fun append(chunk: ByteArray) {
            if (size + chunk.size > buffer.size) {
                buffer = buffer.copyOf(maxOf(buffer.size * 2, size + chunk.size))
            }
            chunk.copyInto(buffer, size)
            size += chunk.size
        }
    }
}
