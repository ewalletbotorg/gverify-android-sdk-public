package com.gammaverify.gvintelligence.signals

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioManager

/**
 * Collects device-environment signals that map to dashboard Device Rules:
 * - HC136 screen mirrored  (more than one active display)
 * - HC139 active phone call (AudioManager mode in-call / in-communication)
 *
 * Note: HC138 (screen captured) has no permission-free, broadly-available API
 * on Android, so it is left null (unlike iOS `UIScreen.isCaptured`).
 */
object EnvironmentCollector {
    data class Result(
        var screenCaptured: Boolean? = null,
        var externalDisplay: Boolean? = null,
        var inCall: Boolean? = null,
    )

    fun collect(context: Context): Result {
        val ctx = context.applicationContext
        val result = Result()

        runCatching {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val displays = dm?.displays
            if (displays != null) result.externalDisplay = displays.size > 1 // HC136
        }

        runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mode = am?.mode
            result.inCall = mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION // HC139
        }

        result.screenCaptured = null // HC138 — not reliably detectable
        return result
    }
}
