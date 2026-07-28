package com.gammasweep.gsintelligence.signals

import com.gammasweep.gsintelligence.token.BehaviorSignals

/**
 * Always-on behavioral capture. Native apps don't expose web-style mouse/
 * keystroke dynamics, so this captures privacy-safe session timing only
 * (no input values), matching the web/iOS SDK's "timing metadata only" stance.
 */
object BehaviorCollector {
    private val lock = Any()
    private var sessionStart: Long? = null

    fun start() {
        synchronized(lock) {
            if (sessionStart == null) sessionStart = System.currentTimeMillis()
        }
    }

    fun stop() {
        synchronized(lock) { sessionStart = null }
    }

    fun snapshot(): BehaviorSignals {
        synchronized(lock) {
            val signals = BehaviorSignals()
            sessionStart?.let {
                signals.session_duration_ms = (System.currentTimeMillis() - it).toInt()
            }
            return signals
        }
    }
}
