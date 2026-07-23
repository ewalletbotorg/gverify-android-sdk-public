package com.gammasweep.gvintelligence.signals

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.gammasweep.gvintelligence.token.NetworkSignals

/**
 * Collects connection type plus VPN/proxy posture (HC134). Mirrors the iOS
 * `NetworkCollector`. Requires the normal `ACCESS_NETWORK_STATE` permission
 * (declared in the SDK manifest).
 */
object NetworkCollector {
    fun collect(context: Context): NetworkSignals {
        val signals = NetworkSignals()
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return signals

        runCatching {
            val network = cm.activeNetwork
            val caps: NetworkCapabilities? = network?.let { cm.getNetworkCapabilities(it) }

            if (caps != null) {
                signals.effective_type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                    else -> "other"
                }
                signals.vpn_active = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                // Metered networks are the closest analog to iOS `isExpensive`.
                signals.is_expensive = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    signals.is_constrained = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
                }
            } else {
                signals.effective_type = "none"
            }
        }

        signals.proxy_active = proxyActive()
        return signals
    }

    /** System HTTP/HTTPS proxy configured (JVM system properties). */
    private fun proxyActive(): Boolean {
        val host = System.getProperty("http.proxyHost").orEmpty()
        val httpsHost = System.getProperty("https.proxyHost").orEmpty()
        return host.isNotBlank() || httpsHost.isNotBlank()
    }
}
