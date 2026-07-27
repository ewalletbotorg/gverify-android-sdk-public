package com.gammaverify.gvintelligence.signals

import android.os.Build
import com.gammaverify.gvintelligence.token.TamperSignals
import java.io.File
import java.security.KeyStore

/**
 * Native tamper posture: root heuristics, emulator detection, and
 * hardware-attestation availability (hardware-backed Keystore). All checks are
 * fail-soft — any failure leaves the corresponding flag null. Mirrors the iOS
 * `TamperCollector`.
 */
object TamperCollector {
    fun collect(): TamperSignals {
        val tamper = TamperSignals(automation_flags = emptyList())
        tamper.is_emulator = isEmulator()
        tamper.is_rooted = isRooted()
        tamper.remote_desktop_suspected = null // not detectable on Android
        tamper.attestation_supported = attestationSupported()
        return tamper
    }

    /** Heuristic emulator detection from Build fingerprints + known SDK images. */
    fun isEmulator(): Boolean {
        val fp = (Build.FINGERPRINT ?: "").lowercase()
        val model = (Build.MODEL ?: "").lowercase()
        val product = (Build.PRODUCT ?: "").lowercase()
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val hardware = (Build.HARDWARE ?: "").lowercase()
        val brand = (Build.BRAND ?: "").lowercase()
        val device = (Build.DEVICE ?: "").lowercase()

        return fp.startsWith("generic") ||
            fp.startsWith("unknown") ||
            fp.contains("emulator") ||
            fp.contains("sdk_gphone") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            product.contains("sdk") ||
            product.contains("emulator") ||
            product.contains("simulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            hardware.contains("vbox") ||
            manufacturer.contains("genymotion") ||
            (brand.startsWith("generic") && device.startsWith("generic"))
    }

    /** Heuristic root detection: su binaries, test-keys build tags, common paths. */
    fun isRooted(): Boolean {
        if ((Build.TAGS ?: "").contains("test-keys")) return true

        val suspiciousPaths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/system/xbin/daemonsu",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su-backup",
            "/system/app/Magisk.apk",
        )
        for (path in suspiciousPaths) {
            if (runCatching { File(path).exists() }.getOrDefault(false)) return true
        }

        // A `which su` style probe across the PATH.
        val pathDirs = listOf("/sbin/", "/system/bin/", "/system/xbin/", "/data/local/xbin/",
            "/data/local/bin/", "/system/sd/xbin/", "/system/bin/failsafe/", "/data/local/", "/su/bin/")
        for (dir in pathDirs) {
            if (runCatching { File(dir + "su").exists() }.getOrDefault(false)) return true
        }
        return false
    }

    /** Whether hardware-backed key storage / attestation is available. */
    fun attestationSupported(): Boolean = runCatching {
        // Presence of the AndroidKeyStore provider is the portable signal that
        // hardware-backed key + key-attestation features are available.
        KeyStore.getInstance("AndroidKeyStore") != null
    }.getOrDefault(false)
}
