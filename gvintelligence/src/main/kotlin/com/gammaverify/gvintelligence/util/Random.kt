package com.gammaverify.gvintelligence.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Random + identity helpers (mirror the web SDK's `util/sha256.ts` and the iOS
 * `GSRandom`).
 */
object GSRandom {
    private val secureRandom = SecureRandom()

    fun uuid(): String = UUID.randomUUID().toString().lowercase()

    fun randomHex(bytes: Int): String {
        val buf = ByteArray(bytes)
        secureRandom.nextBytes(buf)
        return buf.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
