# Veil

An Android app for the Zeekr 7X head unit with two features:

- **AirPlay receiver**: an iPhone can mirror its screen or cast a video to the car. The picture
  hides whenever the car is in gear or moving; sound keeps playing.
- **Dim overlay**: an adjustable veil over the centre screen for night driving, with a small
  floating moon button.

Veil is separate from the dashcam app on purpose. It never opens a camera or a microphone, so
it cannot interfere with recording.

Licence: GPL-3.0 (`LICENSE`). Component provenance: `NOTICE.md`.

Full install, connection and release notes arrive with step 5.

## Build

JDK 17, Android SDK (compileSdk 36), and for the AirPlay native library NDK 27.1.12297006 and
CMake 3.22.1. Point `local.properties` at the SDK, then:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Without the NDK, the Java side and the unit tests still build with
`-Pveil.skipNative=true`; that APK cannot receive AirPlay. The GitHub Actions workflow in
`.github/workflows/build.yml` does the full native build.

Release builds sign with your own key, passed as `VEIL_KEYSTORE`, `VEIL_KEYSTORE_PASSWORD`,
`VEIL_KEY_ALIAS` and `VEIL_KEY_PASSWORD` in the environment or `local.properties`. Without them
the release APK is unsigned.
