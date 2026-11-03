package com.azarai.goworkbro

import com.azarai.goworkbro.core.remote.Framing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Framing is the part both transports share — no Android here at all. */
class FramingTest {

    @Test
    fun oneFrameOneChunk() {
        val reader = Framing.Reader()
        assertEquals(listOf("""{"op":"ping"}"""), reader.feed(Framing.encode("""{"op":"ping"}""")))
    }

    @Test
    fun frameSplitAcrossChunks() {
        val bytes = Framing.encode("""{"op":"todos.list"}""")
        val reader = Framing.Reader()
        val out = ArrayList<String>()
        // one byte at a time: the worst split a GATT attribute can hand us
        bytes.forEach { out += reader.feed(byteArrayOf(it)) }
        assertEquals(listOf("""{"op":"todos.list"}"""), out)
    }

    @Test
    fun severalFramesInOneChunk() {
        val a = Framing.encode("""{"a":1}""")
        val b = Framing.encode("""{"b":2}""")
        val reader = Framing.Reader()
        assertEquals(listOf("""{"a":1}""", """{"b":2}"""), reader.feed(a + b))
    }

    @Test
    fun chineseSurvivesTheRoundTrip() {
        val text = """{"title":"写毕设 · 反事实干预"}"""
        val reader = Framing.Reader()
        assertEquals(listOf(text), reader.feed(Framing.encode(text)))
    }

    @Test
    fun splitInsideAMultiByteCharacter() {
        val text = """{"title":"多字节"}"""
        val bytes = Framing.encode(text)
        val reader = Framing.Reader()
        assertEquals(emptyList<String>(), reader.feed(bytes.copyOfRange(0, 6)))
        assertEquals(listOf(text), reader.feed(bytes.copyOfRange(6, bytes.size)))
    }

    @Test
    fun oversizedFrameIsRejected() {
        val thrown = runCatching { Framing.encode("x".repeat(Framing.MAX_FRAME + 1)) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun badLengthHeaderThrows() {
        val reader = Framing.Reader()
        val header = byteArrayOf(0, 2, 0, 0) // claims a 128 KiB frame
        val thrown = runCatching { reader.feed(header) }.exceptionOrNull()
        assertTrue(thrown is Framing.MalformedFrameException)
    }
}
