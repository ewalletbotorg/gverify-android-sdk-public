package com.gammaverify.gvintelligence.util

import android.content.Context

/**
 * SharedPreferences-backed storage for the long-lived `true_device_id`, giving a
 * stable device identity for "same device, multiple users" detection. When
 * `persistence` is disabled (GDPR mode) the SDK returns an ephemeral per-session
 * UUID and never writes to disk.
 *
 * Parity note vs iOS: the iOS Keychain survives app reinstall; Android's app
 * storage is cleared on uninstall, so this id resets on reinstall unless the
 * host app enables Android Auto Backup. Documented in the README.
 */
object Persistence {
    // DO NOT RENAME. This is an on-disk identity key (and the matching iOS
    // Keychain service string). Changing it resets every installed user's
    // `true_device_id`, breaking device-history continuity and "same device,
    // multiple users" detection. It intentionally keeps the legacy prefix even
    // though the Kotlin package is now com.gammaverify.gvintelligence.
    private const val PREFS = "com.gammasweep.gsintelligence"
    private const val KEY = "true_device_id"


    data class Result(val id: String, val present: Boolean)

    /**
     * Returns the stored device id, generating + persisting one on first use.
     * The `present` flag reports whether the id already existed before this call
     * (drives `storage.persistent_id_present`).
     */
    fun trueDeviceId(context: Context, persist: Boolean): Result {
        if (!persist) {
            return Result(GSRandom.uuid(), false)
        }
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return Result(it, true) }

        val fresh = GSRandom.uuid()
        prefs.edit().putString(KEY, fresh).apply()
        return Result(fresh, false)
    }
}
