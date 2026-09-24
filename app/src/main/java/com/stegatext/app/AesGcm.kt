package com.stegatext.app

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BadKeyException(message: String) : Exception(message)

object AesGcm {

    private const val ITERATIONS = 200_000
    private const val KEY_BITS = 256
    private val AAD = byteArrayOf(0x53, 0x54, 0x58, 0x54, 0x31)

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        val ct = cipher.doFinal(plain)
        return salt + iv + ct
    }

    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        if (blob.size < 29) throw BadKeyException("short blob")
        val salt = blob.copyOfRange(0, 16)
        val iv = blob.copyOfRange(16, 28)
        val ct = blob.copyOfRange(28, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        return try {
            cipher.doFinal(ct)
        } catch (e: Exception) {
            throw BadKeyException("bad key or damaged data")
        }
    }

    fun orderSeed(password: CharArray): Long {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(byteArrayOf(0x53, 0x54, 0x58, 0x4F))
        md.update(String(password).toByteArray(Charsets.UTF_8))
        val d = md.digest()
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (d[i].toLong() and 0xFF)
        return v
    }
}
