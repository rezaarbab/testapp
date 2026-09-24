package com.stegatext.app

import java.security.MessageDigest
import java.security.SecureRandom

class CapacityException(val neededBits: Int, val availableBits: Int) : Exception()

sealed class Payload {
    class Text(val text: String) : Payload()
    class File(val name: String, val bytes: ByteArray) : Payload()
}

class Revealed(val isFile: Boolean, val name: String?, val bytes: ByteArray) {
    fun asText(): String = String(bytes, Charsets.UTF_8)
}

class KeyedRandom(seed: Long) {
    private var state: Long = (seed xor (-0x61C8864680B583EBL)).let { if (it == 0L) 0xB504F32DL else it }

    fun next(): Long {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return x * 0x2545F4914F6CDD1DL
    }

    fun below(bound: Int): Int {
        if (bound <= 1) return 0
        val lim = (0x4000000000000000L / bound) * bound
        var v: Long
        do {
            v = next() and 0x3FFFFFFFFFFFFFFFL
        } while (v >= lim)
        return (v % bound).toInt()
    }
}

fun keyShuffle(count: Int, rnd: KeyedRandom): IntArray {
    val arr = IntArray(count) { it }
    for (i in count - 1 downTo 1) {
        val j = rnd.below(i + 1)
        val t = arr[i]
        arr[i] = arr[j]
        arr[j] = t
    }
    return arr
}

object StegoEngine {

    private const val MAGIC16 = 0x53A9
    private val INNER_MAGIC = byteArrayOf(0x53, 0x54, 0x58, 0x54, 0x31)
    private const val TYPE_TEXT = 1
    private const val TYPE_FILE = 2
    private const val MAX_CIPHER = 4_000_000
    private const val MAX_NAME = 255

    private val CH_ZWSP = '\u200B'
    private val CH_ZWNJ = '\u200C'
    private val CH_ZWJ = '\u200D'
    private val CH_WJ = '\u2060'
    private val CH_BOM = '\uFEFF'
    private val CH_SHY = '\u00AD'
    private val STRIP_ALL = setOf(CH_ZWSP, CH_ZWNJ, CH_ZWJ, CH_WJ, CH_BOM)
    private val GLUE_CHARS = setOf(CH_ZWSP, CH_ZWNJ, CH_ZWJ, CH_WJ, CH_BOM, CH_SHY)

    private val PAIRS = listOf(
        'ی' to 'ي', 'ک' to 'ك',
        'a' to 'а', 'A' to 'А', 'c' to 'с', 'C' to 'С', 'e' to 'е', 'E' to 'Е',
        'o' to 'о', 'O' to 'О', 'p' to 'р', 'P' to 'Р', 'x' to 'х', 'X' to 'Х',
        'y' to 'у', 'Y' to 'У', 'k' to 'к', 'K' to 'К', 'm' to 'м', 'M' to 'М',
        'j' to 'ј', 'J' to 'Ј', 'B' to 'В'
    )
    private val TWINS: Map<Char, Char> = buildMap {
        PAIRS.forEach { (a, b) -> put(a, b); put(b, a) }
    }
    private val PRIMARY: Set<Char> = PAIRS.map { it.first }.toSet()

    private fun primaryOf(c: Char): Char = if (PRIMARY.contains(c)) c else TWINS[c] ?: c

    fun normalizeCarriers(carrier: String): String =
        carrier.filter { !STRIP_ALL.contains(it) }.map { primaryOf(it) }.joinToString("")

    fun capacityBits(carrier: String, robustOnly: Boolean): Int =
        buildSlots(normalizeCarriers(carrier), robustOnly).size

    fun cipherSize(plainSize: Int): Int = plainSize + 44

    fun neededBits(plainSize: Int): Int = 48 + cipherSize(plainSize) * 8

    fun plainTextSize(textBytes: Int): Int = 6 + textBytes

    fun plainFileSize(nameBytes: Int, fileBytes: Int): Int = 6 + 2 + nameBytes + fileBytes

    fun hide(carrier: String, payload: Payload, password: CharArray, robustOnly: Boolean): String {
        val canon = normalizeCarriers(carrier)
        val plain = buildPlain(payload)
        val cipher = AesGcm.encrypt(plain, password)
        val slots = buildSlots(canon, robustOnly)
        val bitCount = 48 + cipher.size * 8
        if (bitCount > slots.size) throw CapacityException(bitCount, slots.size)
        val rnd = KeyedRandom(AesGcm.orderSeed(password))
        val shuffled = keyShuffle(slots.size, rnd)
        val bits = BooleanArray(bitCount)
        writeIntBits(MAGIC16, 16, bits, 0)
        writeIntBits(cipher.size, 32, bits, 16)
        bitsOfBytes(cipher).copyInto(bits, 48)
        val chars = canon.toMutableList()
        val insertions = ArrayList<Pair<Int, Char>>()
        for (b in 0 until bitCount) {
            when (val s = slots[shuffled[b]]) {
                is Slot.Swap -> if (bits[b]) chars[s.at] = s.twin
                is Slot.Ins -> if (bits[b]) insertions.add(s.at to s.ch)
            }
        }
        insertions.sortedByDescending { it.first }.forEach { (pos, ch) -> chars.add(pos, ch) }
        return chars.joinToString("")
    }

    fun reveal(stego: String, password: CharArray): Revealed? {
        for (robust in arrayOf(true, false)) {
            val slots = buildSlots(stego, robust)
            if (slots.size < 48) continue
            val rnd = KeyedRandom(AesGcm.orderSeed(password))
            val shuffled = keyShuffle(slots.size, rnd)
            val head = BooleanArray(48)
            for (i in 0 until 48) head[i] = evalSlot(stego, slots[shuffled[i]])
            if (readIntBits(head, 0, 16) != MAGIC16) continue
            val cipherLen = readIntBits(head, 16, 32)
            if (cipherLen <= 0 || cipherLen > MAX_CIPHER) continue
            if (48 + cipherLen * 8 > slots.size) continue
            val data = BooleanArray(cipherLen * 8)
            for (i in 0 until cipherLen * 8) data[i] = evalSlot(stego, slots[shuffled[48 + i]])
            val cipher = bytesOfBits(data)
            val plain = try {
                AesGcm.decrypt(cipher, password)
            } catch (e: BadKeyException) {
                continue
            }
            val r = parsePlain(plain) ?: continue
            return r
        }
        return null
    }

    private sealed class Slot {
        class Swap(val at: Int, val twin: Char) : Slot()
        class Ins(val at: Int, val prev: Int, val ch: Char) : Slot()
    }

    private class LetterNode(val ch: Char, val idx: Int)

    private fun isGlue(c: Char): Boolean = !c.isLetter() && GLUE_CHARS.contains(c)

    private fun family(c: Char): Int {
        val code = c.code
        return when {
            code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x08A0..0x08FF ||
                code in 0xFB50..0xFDFF || code in 0xFE70..0xFEFF -> 1
            code in 0x0400..0x052F || code in 0x0041..0x005A || code in 0x0061..0x007A -> 2
            code in 0x0370..0x03FF || code in 0x1F00..0x1FFF -> 3
            code in 0x3040..0x30FF || code in 0x4E00..0x9FFF || code in 0xAC00..0xD7AF -> 4
            else -> 0
        }
    }

    private fun buildSlots(s: String, robustOnly: Boolean): List<Slot> {
        val letters = ArrayList<LetterNode>()
        s.forEachIndexed { i, c -> if (c.isLetter()) letters.add(LetterNode(c, i)) }
        val n = letters.size
        if (n < 2) return emptyList()

        val sameToken = BooleanArray(n - 1)
        for (j in 0 until n - 1) {
            var cont = true
            for (t in letters[j].idx + 1 until letters[j + 1].idx) {
                if (!isGlue(s[t])) { cont = false; break }
            }
            sameToken[j] = cont
        }

        val morphK = HashSet<Int>()
        val morphSlots = ArrayList<Slot>()
        val gapSlots = ArrayList<Slot>()
        val swapSlots = ArrayList<Slot>()

        var k = 0
        while (k < n) {
            var end = k
            while (end + 1 < n && sameToken[end]) end++
            val word = StringBuilder()
            for (t in k..end) word.append(primaryOf(letters[t].ch))
            val w = word.toString()
            var slotK = -1
            if (w.endsWith("هایی") && w.length >= 8) slotK = end - 3
            else if (w.endsWith("ترین") && w.length >= 8) slotK = end - 3
            else if (w.endsWith("های") && w.length >= 7) slotK = end - 2
            else if (w.endsWith("ها") && w.length >= 5) slotK = end - 1
            else if (w.endsWith("تر") && w.length >= 6) slotK = end - 1
            else if (w.startsWith("نمی") && w.length >= 6) slotK = k + 3
            else if (w.startsWith("می") && w.length >= 5) slotK = k + 2
            if (slotK > 0 && slotK > k && slotK <= end) {
                morphK.add(slotK)
                morphSlots.add(Slot.Ins(letters[slotK].idx, letters[slotK - 1].idx, CH_ZWNJ))
            }
            k = end + 1
        }

        for (j in 0 until n - 1) {
            val target = j + 1
            if (morphK.contains(target)) continue
            val f = family(letters[j].ch)
            if (f != 0 && f == family(letters[target].ch)) {
                gapSlots.add(Slot.Ins(letters[target].idx, letters[j].idx, CH_ZWJ))
            }
        }

        for (j in 0 until n) {
            val twin = TWINS[letters[j].ch]
            if (twin != null) swapSlots.add(Slot.Swap(letters[j].idx, twin))
        }

        return if (robustOnly) swapSlots else morphSlots + gapSlots + swapSlots
    }

    private fun evalSlot(s: String, slot: Slot): Boolean = when (slot) {
        is Slot.Swap -> slot.at in s.indices && !PRIMARY.contains(s[slot.at])
        is Slot.Ins -> betweenHas(s, slot.prev, slot.at, slot.ch)
    }

    private fun betweenHas(s: String, from: Int, to: Int, ch: Char): Boolean {
        if (from < 0 || to >= s.length || to <= from) return false
        for (i in from + 1 until to) if (s[i] == ch) return true
        return false
    }

    private fun writeIntBits(v: Int, width: Int, bits: BooleanArray, offset: Int) {
        for (i in 0 until width) bits[offset + i] = ((v shr (width - 1 - i)) and 1) == 1
    }

    private fun readIntBits(bits: BooleanArray, offset: Int, width: Int): Int {
        var v = 0
        for (i in 0 until width) v = (v shl 1) or (if (bits[offset + i]) 1 else 0)
        return v
    }

    private fun bitsOfBytes(b: ByteArray): BooleanArray {
        val bits = BooleanArray(b.size * 8)
        for (i in b.indices) {
            for (j in 0 until 8) bits[i * 8 + j] = ((b[i].toInt() shr (7 - j)) and 1) == 1
        }
        return bits
    }

    private fun bytesOfBits(bits: BooleanArray): ByteArray {
        val out = ByteArray(bits.size / 8)
        for (i in out.indices) {
            var v = 0
            for (j in 0 until 8) v = (v shl 1) or (if (bits[i * 8 + j]) 1 else 0)
            out[i] = v.toByte()
        }
        return out
    }

    internal fun buildPlain(payload: Payload): ByteArray {
        val out = ArrayList<Byte>()
        INNER_MAGIC.forEach { out.add(it) }
        when (payload) {
            is Payload.Text -> {
                out.add(TYPE_TEXT.toByte())
                payload.text.toByteArray(Charsets.UTF_8).forEach { out.add(it) }
            }
            is Payload.File -> {
                out.add(TYPE_FILE.toByte())
                var name = payload.name.toByteArray(Charsets.UTF_8)
                if (name.size > MAX_NAME) name = name.copyOf(MAX_NAME)
                out.add(((name.size shr 8) and 0xFF).toByte())
                out.add((name.size and 0xFF).toByte())
                name.forEach { out.add(it) }
                payload.bytes.forEach { out.add(it) }
            }
        }
        return out.toByteArray()
    }

    internal fun parsePlain(plain: ByteArray): Revealed? {
        if (plain.size < 7) return null
        for (i in INNER_MAGIC.indices) if (plain[i] != INNER_MAGIC[i]) return null
        val type = plain[5].toInt()
        return if (type == TYPE_TEXT) {
            Revealed(false, null, plain.copyOfRange(6, plain.size))
        } else if (type == TYPE_FILE && plain.size >= 9) {
            val nameLen = ((plain[6].toInt() and 0xFF) shl 8) or (plain[7].toInt() and 0xFF)
            if (8 + nameLen > plain.size) return null
            val name = String(plain, 8, nameLen, Charsets.UTF_8)
            Revealed(true, name, plain.copyOfRange(8 + nameLen, plain.size))
        } else null
    }
}
