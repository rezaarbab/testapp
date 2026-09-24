package com.stegatext.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FileStegoEngineTest {

    private val pw = "key123".toCharArray()

    private fun carrier(size: Int): ByteArray {
        val b = ByteArray(size)
        Random(11).nextBytes(b)
        return b
    }

    @Test
    fun roundTrip_file() {
        val payload = Random(3).nextBytes(500)
        val stego = FileStegoEngine.hide(carrier(64 * 1024), Payload.File("song.mp3", payload), pw)
        val out = FileStegoEngine.reveal(stego, pw)
        assertNotNull(out)
        assertTrue(out!!.isFile)
        assertEquals("song.mp3", out.name)
        assertTrue(out.bytes.contentEquals(payload))
    }

    @Test
    fun roundTrip_text() {
        val stego = FileStegoEngine.hide(carrier(32 * 1024), Payload.Text("سلام مخفی"), pw)
        val out = FileStegoEngine.reveal(stego, pw)
        assertNotNull(out)
        assertEquals("سلام مخفی", out!!.asText())
        assertFalse(out.isFile)
    }

    @Test
    fun tampered_returnsNull() {
        val stego = FileStegoEngine.hide(carrier(32 * 1024), Payload.Text("x"), pw)
        val tampered = stego.copyOf()
        val bound = tampered.size - 1024
        val rnd = Random(42)
        for (k in 0 until bound / 2) {
            val idx = 1024 + rnd.nextInt(bound)
            tampered[idx] = (tampered[idx].toInt() xor 0x01).toByte()
        }
        assertNull(FileStegoEngine.reveal(tampered, pw))
    }

    @Test
    fun wrongKey_returnsNull() {
        val stego = FileStegoEngine.hide(carrier(32 * 1024), Payload.Text("x"), pw)
        assertNull(FileStegoEngine.reveal(stego, "nope".toCharArray()))
    }

    @Test
    fun capacityTooSmall_throws() {
        var thrown = false
        try {
            FileStegoEngine.hide(carrier(2 * 1024), Payload.Text("y".repeat(5000)), pw)
        } catch (e: CapacityException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
