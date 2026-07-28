package com.gammasweep.gsintelligence.token

import org.json.JSONArray
import org.json.JSONObject

/**
 * Encodable mirror of the web SDK's `Signals` envelope
 * (`packages/gs-web-sdk/src/types.ts`) and the iOS SDK's `TokenPayload.swift`.
 * Field names are snake_case so the sealed JSON matches the shape `fraud-check`
 * / `_shared/device-token.ts` already decrypt and consume — no backend change.
 *
 * All fields are nullable and OMITTED when null (fail-soft), matching Swift's
 * `Encodable` omit-nil behavior. Serialization is done with the Android
 * built-in `org.json` so the SDK ships no JSON dependency.
 */

/** Adds [value] to the object only when it is non-null (parity with omit-nil). */
private fun JSONObject.putOpt(key: String, value: Any?): JSONObject {
    if (value != null) put(key, value)
    return this
}

private fun List<String>?.toJsonArrayOrNull(): JSONArray? =
    if (this == null) null else JSONArray().also { arr -> forEach { arr.put(it) } }

// MARK: - Device

data class ScreenSignals(
    var width: Int? = null,
    var height: Int? = null,
    var color_depth: Int? = null,
    var pixel_ratio: Double? = null,
    var refresh_rate: Double? = null,
    var is_extended: Boolean? = null,
    var available_width: Int? = null,
    var available_height: Int? = null,
    var orientation_type: String? = null,
    var orientation_angle: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("width", width)
        .putOpt("height", height)
        .putOpt("color_depth", color_depth)
        .putOpt("pixel_ratio", pixel_ratio)
        .putOpt("refresh_rate", refresh_rate)
        .putOpt("is_extended", is_extended)
        .putOpt("available_width", available_width)
        .putOpt("available_height", available_height)
        .putOpt("orientation_type", orientation_type)
        .putOpt("orientation_angle", orientation_angle)
}

/** High-entropy "UA-Client-Hints" analog. `platform_version` is surfaced by the
 * backend mapper as `device_info.os_version`. */
data class UserAgentData(
    var platform: String? = null,
    var platform_version: String? = null,
    var model: String? = null,
    var mobile: Boolean? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("platform", platform)
        .putOpt("platform_version", platform_version)
        .putOpt("model", model)
        .putOpt("mobile", mobile)
}

data class DeviceSignals(
    /** Settings.Secure.ANDROID_ID — stable per app-signing-key + device + user. */
    var device_hash: String? = null,
    /** Persisted UUID — primary `deviceFingerprint`. */
    var true_device_id: String? = null,
    var browser_hash: String? = null,
    var platform: String? = null,
    var vendor: String? = null,
    var model: String? = null,
    var timezone: String? = null,
    var timezone_offset: Int? = null,
    var languages: List<String>? = null,
    var locale: String? = null,
    var timezone_country: String? = null,
    var device_memory: Double? = null,
    var hardware_concurrency: Int? = null,
    var touch_support: Boolean? = null,
    var screen: ScreenSignals? = null,
    var user_agent_data: UserAgentData? = null,
    /** Set after tamper collection (matches web's spoofing_hash merge). */
    var spoofing_hash: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("device_hash", device_hash)
        .putOpt("true_device_id", true_device_id)
        .putOpt("browser_hash", browser_hash)
        .putOpt("platform", platform)
        .putOpt("vendor", vendor)
        .putOpt("model", model)
        .putOpt("timezone", timezone)
        .putOpt("timezone_offset", timezone_offset)
        .putOpt("languages", languages.toJsonArrayOrNull())
        .putOpt("locale", locale)
        .putOpt("timezone_country", timezone_country)
        .putOpt("device_memory", device_memory)
        .putOpt("hardware_concurrency", hardware_concurrency)
        .putOpt("touch_support", touch_support)
        .putOpt("screen", screen?.toJson())
        .putOpt("user_agent_data", user_agent_data?.toJson())
        .putOpt("spoofing_hash", spoofing_hash)
}

// MARK: - Browser (no native analog; sent empty for parity)

data class BrowserSignals(
    var user_agent: String? = null,
    var browser_name: String? = null,
    var browser_version: String? = null,
    var spoofing_detected: Boolean? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("user_agent", user_agent)
        .putOpt("browser_name", browser_name)
        .putOpt("browser_version", browser_version)
        .putOpt("spoofing_detected", spoofing_detected)
}

// MARK: - Tamper / environment posture

data class TamperSignals(
    var is_emulator: Boolean? = null,
    /** Native addition: root heuristics. */
    var is_rooted: Boolean? = null,
    /** HC136 — external/mirrored display present. */
    var screen_mirrored: Boolean? = null,
    /** HC138 — screen recording/capture active (null on Android: no safe API). */
    var screen_captured: Boolean? = null,
    /** HC139 — an active phone call during the transaction. */
    var in_call: Boolean? = null,
    /** HC137 — not detectable on Android; always null here. */
    var remote_desktop_suspected: Boolean? = null,
    /** Hardware-backed Keystore / attestation availability. */
    var attestation_supported: Boolean? = null,
    var automation_flags: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("is_emulator", is_emulator)
        .putOpt("is_rooted", is_rooted)
        .putOpt("screen_mirrored", screen_mirrored)
        .putOpt("screen_captured", screen_captured)
        .putOpt("in_call", in_call)
        .putOpt("remote_desktop_suspected", remote_desktop_suspected)
        .putOpt("attestation_supported", attestation_supported)
        .put("automation_flags", JSONArray().also { arr -> automation_flags.forEach { arr.put(it) } })
}

// MARK: - GPS

data class GpsSignals(
    var latitude: Double? = null,
    var longitude: Double? = null,
    var accuracy_m: Double? = null,
    /** "granted" | "denied" | "prompt" | "unavailable" | "timeout" */
    var permission_state: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("latitude", latitude)
        .putOpt("longitude", longitude)
        .putOpt("accuracy_m", accuracy_m)
        .putOpt("permission_state", permission_state)
}

// MARK: - Network

data class NetworkSignals(
    var effective_type: String? = null,
    /** HC134 — VPN tunnel detected. */
    var vpn_active: Boolean? = null,
    /** HC134 — system HTTP/HTTPS proxy configured. */
    var proxy_active: Boolean? = null,
    var is_expensive: Boolean? = null,
    var is_constrained: Boolean? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("effective_type", effective_type)
        .putOpt("vpn_active", vpn_active)
        .putOpt("proxy_active", proxy_active)
        .putOpt("is_expensive", is_expensive)
        .putOpt("is_constrained", is_constrained)
}

// MARK: - Storage

data class StorageSignals(
    var persistent_id_present: Boolean? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("persistent_id_present", persistent_id_present)
}

// MARK: - Behavior

data class BehaviorSignals(
    var session_duration_ms: Int? = null,
    var risk_score: Double? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("session_duration_ms", session_duration_ms)
        .putOpt("risk_score", risk_score)
}

// MARK: - Email hint

data class EmailSignals(
    var address: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("address", address)
}

// MARK: - Envelope

data class Signals(
    var device: DeviceSignals,
    var browser: BrowserSignals,
    var tamper: TamperSignals,
    var gps: GpsSignals,
    var network: NetworkSignals,
    var storage: StorageSignals,
    var behavior: BehaviorSignals,
    var email: EmailSignals,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("device", device.toJson())
        .put("browser", browser.toJson())
        .put("tamper", tamper.toJson())
        .put("gps", gps.toJson())
        .put("network", network.toJson())
        .put("storage", storage.toJson())
        .put("behavior", behavior.toJson())
        .put("email", email.toJson())
}

/**
 * Top-level sealed payload. Shape matches the web/iOS SDK's `TokenPayload`
 * (`v`, `sid`, `iat`, `exp`, `nonce`, `collected_at`, `signals`). `iat`/`exp`
 * are epoch milliseconds; TTL is 5 minutes (mirrors `_shared/device-token.ts`).
 */
data class TokenPayload(
    val v: Int,
    val sid: String,
    val iat: Long,
    val exp: Long,
    val nonce: String,
    val collected_at: Long,
    val signals: Signals,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("v", v)
        .put("sid", sid)
        .put("iat", iat)
        .put("exp", exp)
        .put("nonce", nonce)
        .put("collected_at", collected_at)
        .put("signals", signals.toJson())

    /** Compact JSON bytes (UTF-8) — the plaintext that gets sealed/sandboxed. */
    fun toBytes(): ByteArray = toJson().toString().toByteArray(Charsets.UTF_8)
}
