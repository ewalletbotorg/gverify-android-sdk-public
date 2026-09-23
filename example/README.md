# GS Demo — example app

A minimal Jetpack Compose app that exercises the Gamma Verify Intelligence Android SDK
end-to-end:

- **Get Session** — seals a token. In sandbox mode (`sandbox = true` + a
  `gk_test_` key) the token is unencrypted so the app can pretty-print the
  decoded signal envelope. With a live key it returns an encrypted JWE that your
  backend forwards verbatim to `fraud-check`.
- **Get Device ID** — reads the stable persisted identifier.
- **Decoded signals** — renders the signal JSON (sandbox only) so you can
  confirm device/network/GPS/tamper fields look correct.

> The example module is only included by the root `settings.gradle.kts` when
> present and is ignored by JitPack's library resolution, so shipping it in the
> public repo does not affect consumers.

## Run it

1. Open `packages/gv-android-sdk` in Android Studio (it's a self-contained Gradle
   project once the public tree is assembled).
2. Set `clientId` in `MainActivity.kt`.
3. Pick the `example` run configuration and run on a device or emulator.

For real device-rule testing (VPN, calls, root), run on a physical device — the
emulator cannot produce those signals. The emulator is itself flagged as an
emulator (`is_emulator = true`).

## Getting GPS on the emulator

The emulator has no GPS hardware, so location only resolves if you set a
simulated location:

1. Run the app and tap **Get Session**; allow the location permission.
2. Emulator → **⋯ (Extended controls) → Location** → set coordinates → **Send**.
3. Tap **Get Session** again. With `debug = true` you'll see `gs gps: …` lines in
   Logcat (filter on tag `gs`).
