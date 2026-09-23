package com.gammaverify.gvintelligence.util

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Run a suspending operation under a hard wall-clock budget. Returns [fallback]
 * if the operation does not complete within [millis] (fail-soft parity with the
 * web SDK's `safe()` wrapper and the iOS `withTimeout`).
 */
suspend fun <T> withBudget(millis: Long, fallback: T, operation: suspend () -> T): T =
    try {
        if (millis <= 0) fallback else withTimeout(millis) { operation() }
    } catch (_: TimeoutCancellationException) {
        fallback
    } catch (_: Throwable) {
        // Any collector failure is non-fatal — fall back so a token still seals.
        fallback
    }

/** Current epoch milliseconds (matches JS `Date.now()`). */
fun epochMillis(): Long = System.currentTimeMillis()
