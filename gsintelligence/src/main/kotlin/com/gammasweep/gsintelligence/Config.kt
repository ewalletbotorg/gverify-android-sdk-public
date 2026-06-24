package com.gammasweep.gsintelligence

/**
 * GPS collection mode (mirrors the web/iOS SDK's GPS mode).
 */
enum class GpsMode(val raw: String) {
    /** Never touch location services. */
    OFF("off"),

    /** Use location only if already authorized; never prompt. */
    SILENT("silent"),

    /** Use location if authorized; the host app is responsible for requesting
     * the runtime permission (a library cannot show the system dialog). */
    PROMPT("prompt"),

    /** Require a fix; [GS.getSession] throws [GSException.GpsRequired] when no
     * coordinates are captured. */
    REQUIRED("required"),
}

/**
 * SDK configuration captured by [GS.configure].
 */
data class GSConfig(
    /** Client identifier (used by your backend's API key model; not embedded in
     * the token — identification happens server-side, exactly like the web SDK). */
    val clientId: String,
    /** Optional fresh per-action session id; auto-generated if null. */
    val sessionId: String? = null,
    val gps: GpsMode = GpsMode.PROMPT,
    val behavior: Boolean = true,
    val persistence: Boolean = true,
    /** Emit an unencrypted sandbox payload (for `gk_test_` keys). */
    val sandbox: Boolean = false,
    val debug: Boolean = false,
    /** Max wall-clock time (milliseconds) collectors may run before sealing. */
    val maxWaitTimeMs: Long = 4_000L,
)

/**
 * Errors surfaced by the public API.
 */
sealed class GSException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** [GS.getSession] called before [GS.configure]. */
    object NotConfigured : GSException("GS.configure(...) must be called before getSession()")

    /** `gps = REQUIRED` but no coordinates were captured. */
    class GpsRequired(val permissionState: String?) :
        GSException("GPS required but unavailable (permission_state=$permissionState)")

    /** Token sealing failed. */
    class SealFailed(cause: Throwable) : GSException("failed to seal session token", cause)
}
