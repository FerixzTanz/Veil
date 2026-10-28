# Notice

Veil is free software under the GNU General Public License, version 3 (see `LICENSE`).
It is an independent, experimental app for the Zeekr 7X head unit. It has no affiliation
with Zeekr or with Apple Inc. AirPlay is a trademark of Apple Inc.

This file lists every component Veil is built from, where it comes from and its licence.
It is shipped inside the APK and shown on the About page. Update it in the same change
that adds or removes a component.

## Design and code adapted from the author's other apps

| Component | Source | Licence |
|---|---|---|
| Design shell (64 dp title bar, 280 dp section list, semantic day/night palette, six-step type scale, app-drawn dialogs: `VeilDialogs`, `themes.xml`, `type.xml`, `colors.xml`) | CAMCORDER (the author's Zeekr dashcam app) and [7xDash](https://github.com/zeekrownerapp-hub/7xdash-companion) | GPL-3.0 |
| Dim overlay: `DimShade` (veil colour, Android 12 pass-through rules), the shade window in `DimService`, the moon drag that changes opacity, the clock window of `AutoDim` | [7xDash](https://github.com/zeekrownerapp-hub/7xdash-companion) `DimShade`, `DimOverlayService`, `RecordingFloatingService` | GPL-3.0 |

## AirPlay receiver (`app/src/main/cpp` and `app.veil.airplay`)

The following source is in the repository (not fetched by Gradle), each under its upstream
licence, as 7xDash's own `NOTICE.md` records:

| Component | Source | Licence |
|---|---|---|
| Android native bridge (`app/src/main/cpp/*.c`, `*.cpp`, `*.h`, `cmake/`) | [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) `75465c7`, via 7xDash | GPL-3.0 |
| UxPlay protocol library (`third_party/UxPlay/lib`, patched by the repository above) | [FDH2/UxPlay](https://github.com/FDH2/UxPlay) | GPL-3.0 (contains playfair, llhttp and others; see file headers) |
| libplist (`third_party/libplist`) | [libimobiledevice/libplist](https://github.com/libimobiledevice/libplist) | LGPL-2.1 |
| openssl-cmake (`third_party/openssl-cmake`) | [viaduck/openssl-cmake](https://github.com/viaduck/openssl-cmake) | MIT |
| OpenSSL 3.4.4 (downloaded at build time from the official release, SHA-256 pinned, linked statically) | [openssl/openssl](https://github.com/openssl/openssl) | Apache License 2.0 |
| Java side: `AirPlayService`, `AirPlayWindow`, `MirrorDecoder`, `MdnsResponder`, `NetworkReport`, `DrivingGate`, `AirPlayLog`, `FloatingWindowGestures`, `WindowTouchModel`, and the `io.github.jqssun.airplay.bridge` package (rewritten from the Kotlin in the repository above) | [7xDash](https://github.com/zeekrownerapp-hub/7xdash-companion) | GPL-3.0 |
| Car link (`app.veil.car`): gear and speed over the head unit's ECARX interface, by reflection | distilled from 7xDash's `EcarxSource` | GPL-3.0 |

Changes made in 7xDash and kept here: `audio_decoder.h` drops FFmpeg (mirroring audio is AAC-ELD,
decoded with MediaCodec); OpenSSL is downloaded from the official release and hash-checked.

## Libraries fetched by Gradle

| Component | Licence |
|---|---|
| AndroidX (AppCompat, Activity, Fragment, RecyclerView, Core) | Apache License 2.0 |
| Material Components for Android | Apache License 2.0 |
| Oboe 1.9.3 (AirPlay audio output) | Apache License 2.0 |
| JUnit 4 (tests only, not shipped) | Eclipse Public License 1.0 |

## Icons

| Component | Source | Licence |
|---|---|---|
| Vector icons (back, AirPlay, moon, Wi-Fi, info) | [Material Symbols / Material Icons](https://fonts.google.com/icons) | Apache License 2.0 |

## Coming in step 4

| Component | Source | Licence |
|---|---|---|
| AndroidX Media3 ExoPlayer + HLS, OkHttp (video cast from an app's AirPlay button) | Google, Square | Apache License 2.0 |
