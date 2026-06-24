package com.gammasweep.gsintelligence

import android.content.Context
import com.gammasweep.gsintelligence.crypto.GSKeys
import com.gammasweep.gsintelligence.crypto.sandboxToken
import com.gammasweep.gsintelligence.crypto.sealJwe
import com.gammasweep.gsintelligence.signals.BehaviorCollector
import com.gammasweep.gsintelligence.signals.DeviceCollector
import com.gammasweep.gsintelligence.signals.EnvironmentCollector
import com.gammasweep.gsintelligence.signals.GpsCollector
import com.gammasweep.gsintelligence.signals.NetworkCollector
import com.gammasweep.gsintelligence.signals.TamperCollector
import com.gammasweep.gsintelligence.token.BrowserSignals
import com.gammasweep.gsintelligence.token.EmailSignals
import com.gammasweep.gsintelligence.token.GpsSignals
import com.gammasweep.gsintelligence.token.Signals
import com.gammasweep.gsintelligence.token.StorageSignals
import com.gammasweep.gsintelligence.token.TamperSignals
import com.gammasweep.gsintelligence.token.TokenPayload
import com.gammasweep.gsintelligence.util.GSRandom
import com.gammasweep.gsintelligence.util.Persistence
import com.gammasweep.gsintelligence.util.epochMillis
import com.gammasweep.gsintelligence.util.withBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope


/**
 * GS Intelligence — native Android SDK.
 *
 * Two-step integration:
 * ```kotlin
 * GS.configure(context, clientId = "YOUR_CLIENT_ID")   // once, at app start
 * val session = GS.getSession()                         // per protected action
 * // send `session` to YOUR backend → forwards to /fraud-check
 * ```
 *
 * [getSession] collects native device, network, GPS, behavior, tamper, and
 * device-environment signals in parallel under a wall-clock budget, then seals
 * them into a compact JWE that `fraud-check` decrypts with the existing private
 * key — the token format is identical to the web/iOS SDK's, so no backend change
 * is required.
 */
object GS {
    private val stateLock = Any()
    private var config: GSConfig? = null
    private var appContext: Context? = null
    private var lastGps: GpsSignals? = null

    /** 5-minute token TTL — must mirror `_shared/device-token.ts`. */
    private const val TTL_MILLIS: Long = 5 * 60 * 1000

    // MARK: - Public API

    /** Configure the SDK once (typically in `Application.onCreate`). */
    @JvmStatic
    @JvmOverloads
    fun configure(
        context: Context,
        clientId: String,
        gps: GpsMode = GpsMode.PROMPT,
        behavior: Boolean = true,
        persistence: Boolean = true,
        sandbox: Boolean = false,
        debug: Boolean = false,
        sessionId: String? = null,
        maxWaitTimeMs: Long = 4_000L,
    ) {
        configure(
            context,
            GSConfig(
                clientId = clientId,
                sessionId = sessionId,
                gps = gps,
                behavior = behavior,
                persistence = persistence,
                sandbox = sandbox,
                debug = debug,
                maxWaitTimeMs = maxWaitTimeMs,
            ),
        )
    }

    /** Configure with a fully-built [GSConfig]. */
    @JvmStatic
    fun configure(context: Context, cfg: GSConfig) {
        synchronized(stateLock) {
            config = cfg
            appContext = context.applicationContext
        }
        if (cfg.behavior) BehaviorCollector.start() else BehaviorCollector.stop()
        log("configured: client=${cfg.clientId} gps=${cfg.gps.raw} sandbox=${cfg.sandbox}")
    }

    /**
     * Build a fresh, single-use session token. Generate one per protected
     * user-action (login, checkout, signup). Tokens are one-time and expire in
     * 5 minutes.
     */
    @JvmStatic
    suspend fun getSession(): String {
        val cfg = currentConfig() ?: throw GSException.NotConfigured
        val ctx = currentContext() ?: throw GSException.NotConfigured

        val iat = epochMillis()
        val signals = collectSignals(ctx, cfg)

        if (cfg.gps == GpsMode.REQUIRED &&
            (signals.gps.latitude == null || signals.gps.longitude == null)
        ) {
            throw GSException.GpsRequired(signals.gps.permission_state)
        }

        val payload = TokenPayload(
            v = 1,
            sid = cfg.sessionId ?: GSRandom.uuid(),
            iat = iat,
            exp = iat + TTL_MILLIS,
            nonce = GSRandom.randomHex(16),
            collected_at = iat,
            signals = signals,
        )

        return try {
            if (cfg.sandbox) {
                log("sandbox mode — returning unencrypted payload")
                sandboxToken(payload)
            } else {
                sealJwe(payload, GSKeys.ACTIVE_KID)
            }
        } catch (e: Throwable) {
            throw GSException.SealFailed(e)
        }
    }

    /** The persistent device identity, without building a full token. */
    @JvmStatic
    fun getDeviceId(): String {
        val ctx = currentContext() ?: throw GSException.NotConfigured
        val persist = currentConfig()?.persistence ?: true
        return Persistence.trueDeviceId(ctx, persist).id
    }

    /** The last GPS block captured by [getSession] — useful for diagnostics. */
    @JvmStatic
    fun getLastGps(): GpsSignals? = synchronized(stateLock) { lastGps }

    // MARK: - Orchestration

    private suspend fun collectSignals(context: Context, cfg: GSConfig): Signals = coroutineScope {
        val budget = cfg.maxWaitTimeMs
        val persisted = Persistence.trueDeviceId(context, cfg.persistence)

        val gpsBudget = if (cfg.gps == GpsMode.PROMPT || cfg.gps == GpsMode.REQUIRED) {
            maxOf(budget, 15_000L)
        } else {
            minOf(budget, 3_000L)
        }

        // Run independent collectors concurrently under their own budgets.
        val deviceTask = async(Dispatchers.Default) {
            withBudget(budget, com.gammasweep.gsintelligence.token.DeviceSignals()) {
                DeviceCollector.collect(context, persisted.id)
            }
        }
        val networkTask = async(Dispatchers.Default) {
            withBudget(minOf(budget, 2_500L), com.gammasweep.gsintelligence.token.NetworkSignals()) {
                NetworkCollector.collect(context)
            }
        }
        val tamperTask = async(Dispatchers.Default) {
            withBudget(budget, TamperSignals(automation_flags = emptyList())) {
                TamperCollector.collect()
            }
        }
        val environmentTask = async(Dispatchers.Main) {
            withBudget(budget, EnvironmentCollector.Result()) {
                EnvironmentCollector.collect(context)
            }
        }
        val gpsTask = async(Dispatchers.Main) {
            withBudget(gpsBudget + 250L, GpsSignals(permission_state = "timeout")) {
                GpsCollector(context).apply { debug = cfg.debug }.collect(cfg.gps, gpsBudget)
            }
        }

        val device = deviceTask.await()
        val network = networkTask.await()
        val tamper = tamperTask.await()
        val environment = environmentTask.await()
        val gps = gpsTask.await()

        // Merge environment posture into the tamper block where backend slots
        // exist so HC136/HC138/HC139 resolve from a single security envelope.
        tamper.screen_mirrored = environment.externalDisplay // HC136
        tamper.screen_captured = environment.screenCaptured  // HC138
        tamper.in_call = environment.inCall                  // HC139

        // Spoofing posture summary hash (mirrors the web/iOS SDK's spoofing_hash).
        device.spoofing_hash = spoofingHash(tamper)

        val storage = StorageSignals(persistent_id_present = persisted.present)
        val behavior = if (cfg.behavior) {
            BehaviorCollector.snapshot()
        } else {
            com.gammasweep.gsintelligence.token.BehaviorSignals()
        }

        val signals = Signals(
            device = device,
            browser = BrowserSignals(),
            tamper = tamper,
            gps = gps,
            network = network,
            storage = storage,
            behavior = behavior,
            email = EmailSignals(),
        )

        synchronized(stateLock) { lastGps = gps }
        signals
    }

    /** Compact, stable hash of the tamper posture. */
    private fun spoofingHash(tamper: TamperSignals): String {
        val parts = listOf(
            tamper.is_emulator?.toString() ?: "-",
            tamper.is_rooted?.toString() ?: "-",
            tamper.screen_mirrored?.toString() ?: "-",
            tamper.screen_captured?.toString() ?: "-",
            tamper.in_call?.toString() ?: "-",
            tamper.attestation_supported?.toString() ?: "-",
        ).joinToString("|")
        return GSRandom.sha256Hex(parts).take(32)
    }

    // MARK: - Helpers

    private fun currentConfig(): GSConfig? = synchronized(stateLock) { config }

    private fun currentContext(): Context? = synchronized(stateLock) { appContext }

    private fun log(message: String) {
        if (currentConfig()?.debug == true) android.util.Log.d("gs", message)
    }
}
