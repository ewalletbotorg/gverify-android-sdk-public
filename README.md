# Gamma Verify Intelligence — Android SDK

Gamma Verify Intelligence helps you stop fraudsters from abusing your Android app while
giving you rich insight into every customer who uses it. By collecting device,
network, location, behavioral, and tamper signals, the SDK helps you defend
against account takeovers, multiple-account signups, and fraudulent payments.

Each call returns a single-use, 5-minute sealed token (a JWE) that your backend
forwards to Gamma Verify for scoring. The token format is identical to the Gamma Verify web and iOS
SDKs', so your existing fraud-check endpoint works with **no backend changes**.

- **One lightweight dependency** — Kotlin coroutines only. Everything else is
  Android framework + `java.security` (crypto) + `org.json` (built in).
- **Two-step integration** — `configure` once, then `getSession()` per protected action.
- **Fail-soft by design** — every collector is time-budgeted; any signal that is
  unavailable (e.g. a permission was not granted) is simply omitted, and you
  still receive a valid token.

---

## Requirements

- **Android 7.0 (API 24)** or higher
- **Kotlin** (the SDK exposes a `suspend` API; coroutines are used internally)
- _(optional)_ **Location** permission — required only for GPS signals
  (latitude/longitude and permission state)

> **Note:** If a permission is not granted, the values that depend on it are
> ignored and the SDK still returns a valid token.

---

## Installation (JitPack)

Add JitPack to your repositories (in `settings.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the dependency (in your app module `build.gradle.kts`), using the latest
published version:

```kotlin
dependencies {
    implementation("com.github.ewalletbotorg:gverify-android-sdk-public:2.0.0")
}
```

> Replace `2.0.0` with the latest tag from
> https://github.com/ewalletbotorg/gverify-android-sdk-public/releases.

---

## Host-side setup

GPS signals require a location permission. Declare it in your app's
`AndroidManifest.xml` (the SDK already declares `ACCESS_NETWORK_STATE`, as well
as both location permissions below — re-declaring them in your app is harmless
and makes the intent explicit):

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

> Declare `ACCESS_FINE_LOCATION` for precise GPS. `ACCESS_COARSE_LOCATION` is
> sufficient if you only need approximate location — the SDK treats **either**
> grant as usable.

Because a library cannot show the system permission dialog without an Activity,
**your app must request the runtime permission** before (or while) calling
`getSession()`:

```kotlin
ActivityCompat.requestPermissions(
    activity,
    arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ),
    /* requestCode = */ 1001,
)
```

> You may request just one permission depending on the precision you need; the
> SDK reports a fix as long as fine **or** coarse is granted.


If you never use GPS (`gps = GpsMode.OFF`), no location permission is needed.

---

## Quick start

```kotlin
import com.gammaverify.gvintelligence.GV
import com.gammaverify.gvintelligence.GpsMode

// Step 1 — once, at app start (e.g. in Application.onCreate)
GV.configure(context = applicationContext, clientId = "YOUR_CLIENT_ID")

// Step 2 — per protected user action (login, checkout, signup). It's a suspend
// function, so call it from a coroutine.
lifecycleScope.launch {
    try {
        val session = GV.getSession()
        // Send `session` to YOUR backend (see "Sending the token to your backend").
        // Never call the Gamma Verify fraud-check endpoint directly from the app.
    } catch (e: GSException.GpsRequired) {
        // Only thrown when configured with gps = GpsMode.REQUIRED
    } catch (e: Throwable) {
        // handle
    }
}
```

---

## Configuration options

`GV.configure` accepts the following options. Only `context` and `clientId` are
required.

```kotlin
GV.configure(
    context = applicationContext,
    clientId = "YOUR_CLIENT_ID",
    gps = GpsMode.PROMPT,   // OFF | SILENT | PROMPT | REQUIRED
    behavior = true,        // always-on session-timing capture
    persistence = true,     // stable device id in SharedPreferences (false for GDPR mode)
    sandbox = false,        // true for gk_test_ keys → unencrypted payload
    debug = false,          // verbose Logcat logging (tag: "gv")
    maxWaitTimeMs = 4_000L, // wall-clock collection budget (ms)
)
```

| Option          | Default          | Description |
|-----------------|------------------|-------------|
| `clientId`      | —                | Your client identifier. Required. |
| `gps`           | `PROMPT`         | `OFF` (never collect), `SILENT` (only if already granted), `PROMPT` (use if granted), `REQUIRED` (throw if no fix). |
| `behavior`      | `true`           | Captures always-on session-timing behavior signals. |
| `persistence`   | `true`           | Stores a stable device id in SharedPreferences. Set `false` for a privacy/GDPR mode with no persistent id. |
| `sandbox`       | `false`          | Set `true` with a `gk_test_` key to receive an unencrypted payload for inspection. |
| `debug`         | `false`          | Enables verbose Logcat logging during integration. |
| `maxWaitTimeMs` | `4000`           | Maximum milliseconds the SDK waits while collecting signals. |

> `clientId` mirrors the web/iOS SDK: the token itself stays anonymous and is
> identified server-side by your API key. It is **not** embedded in the token.

---

## Sending the token to your backend

The SDK returns an opaque token. Your app sends it to **your** backend, which
forwards it verbatim to the Gamma Verify fraud-check endpoint with your `x-api-key`. The API
key never lives in the app:

```text
App → (token) → Your backend → POST /functions/v1/fraud-check
                                 headers: x-api-key: <your key>
                                 body:    { "session": "<token>" }
```

---

## Sandbox mode

When you initialize with a `gk_test_` key, set `sandbox = true`. The SDK returns
`gsds_sandbox.<base64(payload)>` (unencrypted) so you can inspect the exact signal
envelope during integration. This mirrors the web/iOS SDK's sandbox behavior.

---

## Platform notes

- **Location prompt:** a library cannot show the runtime-permission dialog without
  an Activity — your app must request `ACCESS_FINE_LOCATION` (or
  `ACCESS_COARSE_LOCATION`; the SDK accepts either). `PROMPT`/`REQUIRED`
  resolve a fix only when permission is already granted; otherwise the GPS block
  reports `permission_state = "prompt"`.
- **Persistent device id:** stored in SharedPreferences, which Android clears on
  uninstall. The id therefore resets on reinstall unless you enable
  [Android Auto Backup](https://developer.android.com/guide/topics/data/autobackup)
  in your app. (The iOS Keychain survives reinstall; this is a platform
  difference.)
- **Screen-capture detection** has no permission-free, broadly-available API on
  Android, so that signal is omitted (the iOS SDK reports it via `UIScreen`).

---

## Support

For integration help, API keys, and documentation, contact your Gamma Verify account team.

---

## Changelog

### 2.0.0
- **Breaking:** Kotlin package renamed `com.gammasweep.gvintelligence` -> `com.gammaverify.gvintelligence`.
  Update your imports; the public API (`GV.configure` / `GV.getSession`) is unchanged.
- **Breaking:** Maven group/artifact renamed to `com.gammaverify:gvintelligence`.
- Persisted `true_device_id` storage key is unchanged, so device identity survives the upgrade.

### 1.0.0
- Initial public release of the Gamma Verify Intelligence Android SDK.
- Two-step `GV.configure` / `GV.getSession` integration.
- Device, network, GPS, behavioral, and tamper signal collection.
- Token format byte-compatible with the Gamma Verify web and iOS SDKs.
- Sandbox mode for `gk_test_` keys.
