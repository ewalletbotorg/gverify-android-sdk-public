package com.gammaverify.gvintelligence.crypto

/**
 * Pure-Kotlin Base64 (standard + URL-safe), padding-free, with no Android or
 * Java-8+ platform dependency. This keeps the JWE sealer:
 *  - testable on the plain JVM (android.util.Base64 is stubbed in unit tests),
 *  - working on minSdk 24 (java.util.Base64 requires API 26).
 *
 * Output is byte-for-byte identical to the web/iOS SDK encodings.
 */
internal object Base64 {
    private const val STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encodeStandard(data: ByteArray): String = encode(data, STD)

    fun encodeUrl(data: ByteArray): String = encode(data, URL)

    fun decodeUrl(input: String): ByteArray = decode(input, URL)

    private fun encode(data: ByteArray, alphabet: String): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xff
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xff else -1
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xff else -1

            sb.append(alphabet[b0 shr 2])
            if (b1 == -1) {
                sb.append(alphabet[(b0 and 0x03) shl 4])
            } else if (b2 == -1) {
                sb.append(alphabet[((b0 and 0x03) shl 4) or (b1 shr 4)])
                sb.append(alphabet[(b1 and 0x0f) shl 2])
            } else {
                sb.append(alphabet[((b0 and 0x03) shl 4) or (b1 shr 4)])
                sb.append(alphabet[((b1 and 0x0f) shl 2) or (b2 shr 6)])
                sb.append(alphabet[b2 and 0x3f])
            }
            i += 3
        }
        return sb.toString() // padding-free
    }

    private fun decode(input: String, alphabet: String): ByteArray {
        val lookup = IntArray(128) { -1 }
        for (idx in alphabet.indices) lookup[alphabet[idx].code] = idx

        val clean = input.filter { it != '=' && it.code < 128 && lookup[it.code] != -1 }
        val out = ArrayList<Byte>(clean.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            buffer = (buffer shl 6) or lookup[c.code]
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xff).toByte())
            }
        }
        return out.toByteArray()
    }
}
