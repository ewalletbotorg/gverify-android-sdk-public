package com.gammaverify.gvintelligence.signals

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.gammaverify.gvintelligence.GpsMode
import com.gammaverify.gvintelligence.token.GpsSignals
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Consent-gated GPS collection via [LocationManager]. Resolves a single fix
 * under a wall-clock budget. Always fail-soft: on denial/timeout, coordinates
 * are null and `permission_state` reports why.
 *
 * Parity note vs iOS: a library cannot show the runtime-permission dialog
 * without an Activity, so the host app must request `ACCESS_FINE_LOCATION`.
 * PROMPT/REQUIRED resolve a fix only when permission is already granted; if not,
 * `permission_state = "prompt"` is reported (and REQUIRED makes getSession throw).
 */
class GpsCollector(private val context: Context) {
    var debug = false

    suspend fun collect(mode: GpsMode, timeoutMs: Long): GpsSignals {
        if (mode == GpsMode.OFF) {
            return GpsSignals(permission_state = "unavailable")
        }

        if (!hasPermission()) {
            // The host app must request the runtime permission. Until granted we
            // cannot read location — report "prompt" so REQUIRED can throw.
            log("permission not granted (mode=${mode.raw})")
            return GpsSignals(permission_state = "prompt")
        }

        val lm = context.applicationContext
            .getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return GpsSignals(permission_state = "unavailable")

        // Fast path: a recent last-known fix from either provider.
        lastKnownFix(lm)?.let {
            log("using last-known fix lat=${it.latitude} lon=${it.longitude}")
            return granted(it)
        }

        return requestSingleFix(lm, timeoutMs)
    }

    private suspend fun requestSingleFix(lm: LocationManager, timeoutMs: Long): GpsSignals =
        suspendCancellableCoroutine { cont ->
            val providers = activeProviders(lm)
            if (providers.isEmpty()) {
                if (cont.isActive) cont.resume(GpsSignals(permission_state = "unavailable"))
                return@suspendCancellableCoroutine
            }

            var resumed = false
            // Holder breaks the mutual reference between finish() and listener:
            // finish() needs the listener to unregister, and the listener calls
            // finish() on a fix.
            var listenerRef: LocationListener? = null

            fun finish(signals: GpsSignals) {
                if (resumed) return
                resumed = true
                listenerRef?.let { runCatching { lm.removeUpdates(it) } }
                if (cont.isActive) cont.resume(signals)
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (isValid(location)) {
                        log("got fix lat=${location.latitude} lon=${location.longitude}")
                        finish(granted(location))
                    }
                }

                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            listenerRef = listener


            // Register each provider independently. GPS_PROVIDER requires
            // ACCESS_FINE_LOCATION and throws SecurityException for coarse-only
            // users; that must NOT abort registration of NETWORK_PROVIDER (which
            // coarse permission is allowed to use).
            var registered = 0
            var sawSecurityDenial = false
            for (provider in providers) {
                try {
                    @Suppress("MissingPermission")
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    registered++
                } catch (e: SecurityException) {
                    sawSecurityDenial = true
                    log("provider $provider rejected (SecurityException): ${e.message}")
                } catch (e: Exception) {
                    log("provider $provider failed: ${e.message}")
                }
            }

            if (registered == 0) {
                finish(GpsSignals(permission_state = if (sawSecurityDenial) "denied" else "unavailable"))
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            // The outer withBudget() enforces the timeout; if it elapses, this
            // coroutine is cancelled and the fallback ("timeout") is used.

        }

    private fun hasPermission(): Boolean {
        val ctx = context.applicationContext
        val fine = ctx.checkPermission(
            Manifest.permission.ACCESS_FINE_LOCATION, android.os.Process.myPid(), android.os.Process.myUid(),
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ctx.checkPermission(
            Manifest.permission.ACCESS_COARSE_LOCATION, android.os.Process.myPid(), android.os.Process.myUid(),
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun activeProviders(lm: LocationManager): List<String> {
        val out = ArrayList<String>()
        runCatching { if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) out.add(LocationManager.GPS_PROVIDER) }
        runCatching { if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) out.add(LocationManager.NETWORK_PROVIDER) }
        return out
    }

    private fun lastKnownFix(lm: LocationManager): Location? {
        var best: Location? = null
        for (provider in activeProviders(lm)) {
            val loc = runCatching {
                @Suppress("MissingPermission")
                lm.getLastKnownLocation(provider)
            }.getOrNull()
            if (loc != null && isValid(loc)) {
                if (best == null || loc.time > best!!.time) best = loc
            }
        }
        return best
    }

    private fun isValid(loc: Location): Boolean =
        !(loc.latitude == 0.0 && loc.longitude == 0.0) &&
            loc.latitude in -90.0..90.0 && loc.longitude in -180.0..180.0

    private fun granted(loc: Location): GpsSignals = GpsSignals(
        latitude = loc.latitude,
        longitude = loc.longitude,
        accuracy_m = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
        permission_state = "granted",
    )

    private fun log(message: String) {
        if (debug) android.util.Log.d("gv", "gps: $message")
    }
}
