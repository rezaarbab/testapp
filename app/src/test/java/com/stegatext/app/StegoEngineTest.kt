package com.stegatext.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StegoEngineTest {

    private val pw = "key123".toCharArray()
    private val zws = setOf(0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF).map { it.toChar() }.toSet()

    private fun latinCarrier(): String {
        val base = "The quick brown fox jumps over the lazy dog while the young man makes a perfect cake for everyone. "
        return base.repeat(25)
    }

    private fun faCarrier(): String {
        val base = "شبی که ستاره‌ها بالا آمدند و بچه‌ها پشت پنجره نشستند؛ عمو رضا نمی‌دانست که خانه‌های کوچک ما آرزوی بزرگ‌ترین‌هاست. "
        return base.repeat(12)
    }

    @Test
    fun roundTrip_text_latin() {
        val stego = StegoEngine.hide(latinCarrier(), Payload.Text("hello secret world"), pw, false)
        val out = StegoEngine.reveal(stego, pw)
        assertNotNull(out)
        assertFalse(out!!.isFile)
        assertEquals("hello secret world", out.asText())
    }

    @Test
    fun roundTrip_text_persian() {
        val stego = StegoEngine.hide(faCarrier(), Payload.Text("کلمه محرمانه آزمایشی"), pw, false)
        val out = StegoEngine.reveal(stego, pw)
        assertNotNull(out)
        assertEquals("کلمه محرمانه آزمایشی", out!!.asText())
    }

    @Test
    fun roundTrip_mixed_languages() {
        val carrier = ("سلام رضا جان! I love music, а ты? Это просто история про дом. " +
            "Привет friend! بچه‌ها آرزوی بزرگ‌ترین‌ها را در دل داشتند و نمی‌خواستند تنها بمانند. ").repeat(8)
        val stego = StegoEngine.hide(carrier, Payload.Text("پیام مخفی 4311"), pw, false)
        val out = StegoEngine.reveal(stego, pw)
        assertNotNull(out)
        assertEquals("پیام مخفی 4311", out!!.asText())
    }

    @Test
    fun stego_keeps_visible_structure() {
        val carrier = latinCarrier()
        val stego = StegoEngine.hide(carrier, Payload.Text("tiny"), pw, false)
        val a = carrier.filter { it !in zws }
        val b = stego.filter { c -> !zws.contains(c) }
        assertEquals(a.length, b.length)
    }

    @Test
    fun robustMode_survivesZeroWidthStripping() {
        val secret = "robust secret payload!"
        val stego = StegoEngine.hide(latinCarrier(), Payload.Text(secret), pw, true)
        val stripped = stego.filter { c -> !zws.contains(c) }
        val out = StegoEngine.reveal(stripped, pw)
        assertNotNull(out)
        assertEquals(secret, out!!.asText())
    }

    @Test
    fun wrongPassword_returnsNull() {
        val stego = StegoEngine.hide(latinCarrier(), Payload.Text("x"), pw, false)
        assertNull(StegoEngine.reveal(stego, "wrong".toCharArray()))
    }

    @Test
    fun plainText_returnsNull() {
        assertNull(StegoEngine.reveal("nothing hidden here at all", pw))
    }

    @Test
    fun capacityTooSmall_throws() {
        val tiny = "سلام"
        val big = "y".repeat(600)
        var thrown = false
        try {
            StegoEngine.hide(tiny, Payload.Text(big), pw, false)
        } catch (e: CapacityException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun filePayload_roundTrip() {
        val bytes = Random(7).nextBytes(120)
        val stego = StegoEngine.hide(latinCarrier(), Payload.File("doc.pdf", bytes), pw, false)
        val out = StegoEngine.reveal(stego, pw)
        assertNotNull(out)
        assertTrue(out!!.isFile)
        assertEquals("doc.pdf", out.name)
        assertTrue(out.bytes.contentEquals(bytes))
    }
}
