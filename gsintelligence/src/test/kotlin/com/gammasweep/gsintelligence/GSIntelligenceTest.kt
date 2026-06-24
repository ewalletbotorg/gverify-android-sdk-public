package com.gammasweep.gsintelligence

import com.gammasweep.gsintelligence.crypto.GSKeys
import com.gammasweep.gsintelligence.crypto.base64UrlDecode
import com.gammasweep.gsintelligence.crypto.base64UrlEncode
import com.gammasweep.gsintelligence.crypto.sandboxToken
import com.gammasweep.gsintelligence.crypto.sealJwe
import com.gammasweep.gsintelligence.token.BehaviorSignals
import com.gammasweep.gsintelligence.token.BrowserSignals
import com.gammasweep.gsintelligence.token.DeviceSignals
import com.gammasweep.gsintelligence.token.EmailSignals
import com.gammasweep.gsintelligence.token.GpsSignals
import com.gammasweep.gsintelligence.token.NetworkSignals
import com.gammasweep.gsintelligence.token.Signals
import com.gammasweep.gsintelligence.token.StorageSignals
import com.gammasweep.gsintelligence.token.TamperSignals
import com.gammasweep.gsintelligence.token.TokenPayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests mirroring the iOS `GSIntelligenceTests`. These assert the
 * token format stays byte-compatible with the web/iOS SDK so `fraud-check`
 * decrypts/parses Android tokens unchanged.
 */
class GSIntelligenceTest {

    private fun samplePayload(): TokenPayload {
        val device = DeviceSignals().apply {
            true_device_id = "11111111-2222-3333-4444-555555555555"
            platform = "Android"
            model = "Pixel 7"
        }
        val tamper = TamperSignals(automation_flags = emptyList()).apply {
            is_emulator = false
            is_rooted = false
            screen_mirrored = true  // HC136
            screen_captured = false // HC138
            in_call = true          // HC139
        }
        val network = NetworkSignals().apply {
            effective_type = "wifi"
            vpn_active = true       // HC134
            proxy_active = false
        }
        val gps = GpsSignals(latitude = 37.33, longitude = -122.03, accuracy_m = 65.0, permission_state = "granted")

        val signals = Signals(
            device = device,
            browser = BrowserSignals(),
            tamper = tamper,
            gps = gps,
            network = network,
            storage = StorageSignals(persistent_id_present = true),
            behavior = BehaviorSignals(session_duration_ms = 1200, risk_score = null),
            email = EmailSignals(),
        )

        val iat = 1_700_000_000_000L
        return TokenPayload(
            v = 1,
            sid = "abc",
            iat = iat,
            exp = iat + 5 * 60 * 1000,
            nonce = "deadbeef",
            collected_at = iat,
            signals = signals,
        )
    }

    // base64url

    @Test
    fun base64UrlRoundTrip() {
        val raw = byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0x00, 0x10, 0x3e, 0x3f)
        val encoded = base64UrlEncode(raw)
        assertFalse(encoded.contains("+"))
        assertFalse(encoded.contains("/"))
        assertFalse(encoded.contains("="))
        assertArrayEqualsBytes(raw, base64UrlDecode(encoded))
    }

    // JWE structure

    @Test
    fun sealJweProducesFiveCompactParts() {
        val token = sealJwe(samplePayload())
        val parts = token.split(".")
        assertEquals(5, parts.size)
        assertTrue(parts.all { it.isNotEmpty() })
    }

    @Test
    fun jweHeaderMatchesWebFormat() {
        val token = sealJwe(samplePayload())
        val headerJson = String(base64UrlDecode(token.split(".")[0]), Charsets.UTF_8)
        val header = JSONObject(headerJson)
        assertEquals("RSA-OAEP-256", header.getString("alg"))
        assertEquals("A256GCM", header.getString("enc"))
        assertEquals("v1", header.getString("kid"))
        assertEquals("GSDS", header.getString("typ"))
    }

    @Test
    fun jweIvIsTwelveBytes() {
        val token = sealJwe(samplePayload())
        assertEquals(12, base64UrlDecode(token.split(".")[2]).size)
    }

    @Test
    fun jweTagIsSixteenBytes() {
        val token = sealJwe(samplePayload())
        assertEquals(16, base64UrlDecode(token.split(".")[4]).size)
    }

    @Test
    fun wrappedCekMatchesModulusSize() {
        val token = sealJwe(samplePayload())
        val wrapped = base64UrlDecode(token.split(".")[1])
        val modulus = base64UrlDecode(GSKeys.PUBLIC_KEYS["v1"]!!.n)
        // RSA-OAEP output size == modulus size. Active key is RSA-4096.
        assertEquals(modulus.size, wrapped.size)
    }

    // Sandbox

    @Test
    fun sandboxTokenRoundTrips() {
        val token = sandboxToken(samplePayload())
        assertTrue(token.startsWith("gsds_sandbox."))
        val b64 = token.removePrefix("gsds_sandbox.")
        // standard base64 (no padding) — re-pad for the decoder.
        val padded = b64 + "=".repeat((4 - b64.length % 4) % 4)
        val json = String(java.util.Base64.getDecoder().decode(padded), Charsets.UTF_8)
        val obj = JSONObject(json)
        assertEquals(1, obj.getInt("v"))
        assertEquals("abc", obj.getString("sid"))
        val signals = obj.getJSONObject("signals")
        val tamper = signals.getJSONObject("tamper")
        assertEquals(true, tamper.getBoolean("screen_mirrored")) // HC136
        assertEquals(true, tamper.getBoolean("in_call"))         // HC139
        assertEquals(true, signals.getJSONObject("network").getBoolean("vpn_active")) // HC134
    }

    // Payload shape parity

    @Test
    fun payloadHasExpectedTopLevelKeys() {
        val obj = samplePayload().toJson()
        for (key in listOf("v", "sid", "iat", "exp", "nonce", "collected_at", "signals")) {
            assertTrue("missing top-level key: $key", obj.has(key))
        }
        val signals = obj.getJSONObject("signals")
        for (key in listOf("device", "browser", "tamper", "gps", "network", "storage", "behavior", "email")) {
            assertTrue("missing signals key: $key", signals.has(key))
        }
    }

    @Test
    fun nilFieldsAreOmitted() {
        // BrowserSignals has all-null fields → encodes to an empty object.
        val obj = BrowserSignals().toJson()
        assertEquals(0, obj.length())
    }

    private fun assertArrayEqualsBytes(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.toList(), actual.toList())
    }
}
