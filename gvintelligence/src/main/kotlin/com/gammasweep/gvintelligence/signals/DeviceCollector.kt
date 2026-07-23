package com.gammasweep.gvintelligence.signals

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import com.gammasweep.gvintelligence.token.DeviceSignals
import com.gammasweep.gvintelligence.token.ScreenSignals
import com.gammasweep.gvintelligence.token.UserAgentData
import java.util.Locale
import java.util.TimeZone

/**
 * Collects stable device, locale, and screen signals from native APIs. Mirrors
 * the iOS `DeviceCollector`.
 */
object DeviceCollector {
    fun collect(context: Context, trueDeviceId: String): DeviceSignals {
        val ctx = context.applicationContext
        val tz = TimeZone.getDefault()
        val locale = Locale.getDefault()

        val device = DeviceSignals()
        device.true_device_id = trueDeviceId
        device.platform = "Android"
        device.vendor = Build.MANUFACTURER
        device.model = deviceModelIdentifier()
        device.timezone = tz.id
        // JS getTimezoneOffset() semantics: minutes, positive when behind UTC.
        device.timezone_offset = -tz.getOffset(System.currentTimeMillis()) / 60000
        device.languages = preferredLanguages()
        device.locale = locale.toLanguageTag()
        device.timezone_country = if (locale.country.isNotEmpty()) locale.country else null
        device.touch_support = true

        // ANDROID_ID — stable per (device, user, app signing key). Closest analog
        // to the iOS IDFV used for `device_hash`.
        runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { device.device_hash = it.lowercase() }

        // Memory in GB (rounded to 2dp) + processor count.
        runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            device.device_memory = (mem.totalMem.toDouble() / 1_073_741_824.0 * 100).toLong() / 100.0
        }
        device.hardware_concurrency = Runtime.getRuntime().availableProcessors()

        device.screen = collectScreen(ctx)
        device.user_agent_data = UserAgentData(
            platform = "Android",
            platform_version = Build.VERSION.RELEASE,
            model = deviceModelIdentifier(),
            mobile = true,
        )

        return device
    }

    private fun preferredLanguages(): List<String>? {
        val tags = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val list = android.os.LocaleList.getDefault()
            for (i in 0 until list.size()) tags.add(list[i].toLanguageTag())
        } else {
            @Suppress("DEPRECATION")
            tags.add(Locale.getDefault().toLanguageTag())
        }
        return tags.ifEmpty { null }
    }

    private fun collectScreen(context: Context): ScreenSignals {
        val screen = ScreenSignals()
        runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screen.width = metrics.widthPixels
            screen.height = metrics.heightPixels
            screen.available_width = metrics.widthPixels
            screen.available_height = metrics.heightPixels
            screen.pixel_ratio = metrics.density.toDouble()
            @Suppress("DEPRECATION")
            screen.refresh_rate = wm.defaultDisplay.refreshRate.toDouble()
        }
        return screen
    }

    /** Hardware model identifier, e.g. "Pixel 7" / "SM-G991B". */
    fun deviceModelIdentifier(): String {
        val manufacturer = Build.MANUFACTURER ?: ""
        val model = Build.MODEL ?: ""
        return if (model.startsWith(manufacturer, ignoreCase = true) || manufacturer.isEmpty()) {
            model
        } else {
            "$manufacturer $model".trim()
        }.ifEmpty { "android" }
    }
}
