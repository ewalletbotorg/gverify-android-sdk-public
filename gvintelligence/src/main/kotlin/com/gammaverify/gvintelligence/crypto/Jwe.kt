package com.gammaverify.gvintelligence.crypto

import com.gammaverify.gvintelligence.token.TokenPayload
import java.math.BigInteger
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

class JweException(message: String, cause: Throwable? = null) : Exception(message, cause)

private val secureRandom = SecureRandom()

/** base64url-encode bytes (no padding) — matches the web/iOS SDK's `b64url`. */
fun base64UrlEncode(data: ByteArray): String = Base64.encodeUrl(data)

/** base64url-decode (tolerant of missing padding). */
fun base64UrlDecode(input: String): ByteArray = Base64.decodeUrl(input)

/**
 * Seal a payload into a compact JWE, byte-compatible with the web/iOS SDK:
 *   BASE64URL(header).BASE64URL(encKey).BASE64URL(iv).BASE64URL(ciphertext).BASE64URL(tag)
 * - CEK: random AES-256-GCM key, wrapped with RSA-OAEP-256.
 * - AAD: the ASCII bytes of the encoded protected header (per RFC 7516).
 */
fun sealJwe(payload: TokenPayload, kid: String = GSKeys.ACTIVE_KID): String {
    val jwk = GSKeys.PUBLIC_KEYS[kid] ?: throw JweException("unknown kid: $kid")

    // Protected header — values must match what `fraud-check` expects. Key order
    // is irrelevant (header is decoded as JSON), but we mirror the iOS string.
    val header = "{\"alg\":\"RSA-OAEP-256\",\"enc\":\"A256GCM\",\"kid\":\"$kid\",\"typ\":\"GSDS\"}"
    val encodedHeader = base64UrlEncode(header.toByteArray(Charsets.UTF_8))
    val aad = encodedHeader.toByteArray(Charsets.US_ASCII)

    // Content-encryption key (AES-256-GCM): 32 random bytes.
    val rawCek = ByteArray(32).also { secureRandom.nextBytes(it) }

    // Wrap the CEK with RSA-OAEP-256 (OAEP + SHA-256 for BOTH digest and MGF1).
    val wrapped = rsaOaepEncrypt(jwk, rawCek)

    // Encrypt the plaintext payload (12-byte random IV, 128-bit tag).
    val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
    val cek = SecretKeySpec(rawCek, "AES")
    val gcm = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, cek, GCMParameterSpec(128, iv))
        updateAAD(aad)
    }
    val sealed = gcm.doFinal(payload.toBytes())
    // GCM output = ciphertext || 16-byte tag; JWE keeps them as separate segments.
    val ciphertext = sealed.copyOfRange(0, sealed.size - 16)
    val tag = sealed.copyOfRange(sealed.size - 16, sealed.size)

    return listOf(
        encodedHeader,
        base64UrlEncode(wrapped),
        base64UrlEncode(iv),
        base64UrlEncode(ciphertext),
        base64UrlEncode(tag),
    ).joinToString(".")
}

/**
 * Unencrypted sandbox payload for `gk_test_` integration — mirrors the web/iOS
 * SDK's `gsds_sandbox.<base64(payload)>` format (standard base64, no padding).
 */
fun sandboxToken(payload: TokenPayload): String {
    val b64 = Base64.encodeStandard(payload.toBytes())
    return "gsds_sandbox.$b64"
}

// MARK: - RSA helpers

private fun rsaOaepEncrypt(jwk: PublicJwk, data: ByteArray): ByteArray {
    try {
        val modulus = BigInteger(1, base64UrlDecode(jwk.n))
        val exponent = BigInteger(1, base64UrlDecode(jwk.e))
        val pub = KeyFactory.getInstance("RSA")
            .generatePublic(RSAPublicKeySpec(modulus, exponent))

        // RSA-OAEP-256 == OAEP with SHA-256 as the hash AND SHA-256 for MGF1.
        // The JCE default for "...OAEPWithSHA-256AndMGF1Padding" uses SHA-1 for
        // MGF1, which would NOT match the web/iOS tokens — set it explicitly.
        val oaep = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT,
        )
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.ENCRYPT_MODE, pub, oaep)
        return cipher.doFinal(data)
    } catch (e: Exception) {
        throw JweException("RSA-OAEP key wrap failed", e)
    }
}
