package com.stegatext.app

import java.security.MessageDigest

object FileStegoEngine {

    private const val SKIP = 1024

    private class Rnd(seed: Long) {
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
            do { v = next() and 0x3FFFFFFFFFFFFFFFL } while (v >= lim)
            return (v % bound).toInt()
        }
    }

    private fun seq(password: CharArray): Long {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(byteArrayOf(0x46, 0x53, 0x54, 0x4F))
        md.update(String(password).toByteArray(Charsets.UTF_8))
        val d = md.digest()
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (d[i].toLong() and 0xFF)
        return v
    }

    fun capacity(carrier: ByteArray): Int {
        val usable = carrier.size - SKIP
        return if (usable <= 8) 0 else (usable - 8) / 8
    }

    private fun draw(used: HashSet<Int>, bound: Int, rnd: Rnd): Int {
        while (true) {
            val p = SKIP + rnd.below(bound)
            if (!used.contains(p)) { used.add(p); return p }
        }
    }

    fun hide(carrier: ByteArray, payload: Payload, password: CharArray): ByteArray {
        val plain = StegoEngine.buildPlain(payload)
        val cipher = AesGcm.encrypt(plain, password)
        val total = ByteArray(8 + cipher.size)
        total[0] = 0x46; total[1] = 0x5A; total[2] = 0x53; total[3] = 0x54
        val len = cipher.size
        total[4] = ((len shr 24) and 0xFF).toByte()
        total[5] = ((len shr 16) and 0xFF).toByte()
        total[6] = ((len shr 8) and 0xFF).toByte()
        total[7] = (len and 0xFF).toByte()
        cipher.copyInto(total, 8)
        val cap = capacity(carrier)
        if (len > cap) throw CapacityException(len, cap)
        val rnd = Rnd(seq(password))
        val bound = carrier.size - SKIP
        val used = HashSet<Int>()
        val out = carrier.copyOf()
        var i = 0
        while (i < total.size * 8) {
            val p = draw(used, bound, rnd)
            val bit = (total[i / 8].toInt() shr (7 - (i % 8))) and 1
            out[p] = ((out[p].toInt() and 0xF0) or bit).toByte()
            i++
        }
        return out
    }

    fun reveal(stego: ByteArray, password: CharArray): Revealed? {
        if (stego.size < SKIP + 9) return null
        val bound = stego.size - SKIP
        val rnd = Rnd(seq(password))
        val used = HashSet<Int>()
        val head = ByteArray(8)
        var i = 0
        while (i < 64) {
            val p = draw(used, bound, rnd)
            val nib = stego[p].toInt() and 1
            head[i / 8] = (head[i / 8].toInt() or (nib shl (7 - (i % 8)))).toByte()
            i++
        }
        if (head[0].toInt() != 0x46 || head[1].toInt() != 0x5A || head[2].toInt() != 0x53 || head[3].toInt() != 0x54) return null
        val a = ((head[4].toInt() and 0xFF) shl 24) or ((head[5].toInt() and 0xFF) shl 16) or
            ((head[6].toInt() and 0xFF) shl 8) or (head[7].toInt() and 0xFF)
        if (a <= 0 || a * 8 + 64 > bound) return null
        val cipher = ByteArray(a)
        var j = 0
        while (j < a * 8) {
            val p = draw(used, bound, rnd)
            val nib = stego[p].toInt() and 1
            cipher[j / 8] = (cipher[j / 8].toInt() or (nib shl (7 - (j % 8)))).toByte()
            j++
        }
        val plain = try { AesGcm.decrypt(cipher, password) } catch (e: Exception) { return null }
        return StegoEngine.parsePlain(plain)
    }
}