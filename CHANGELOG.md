# Changelog

Each version's section becomes its GitHub release notes. Versions with a suffix are pre-releases.

## Unreleased

- Network page log: while a video is cast the iPhone polls the receiver several times a second
  (`/playback-info`, `/getProperty`), about a dozen log lines each, so in 0.2.0 the page showed
  only that and the lines saying why a cast failed were gone within seconds. Polling requests
  and the receiver's per-request plumbing are now left off the page (the log file keeps
  everything); other requests show their address, headers and body, and Veil's own `cast:` lines
  stay. The `/feedback` filter in 0.1.1 removed only one line of each keep-alive.

## 0.2.0-alpha (2026-10-28)

- Video casting: tap the AirPlay button in a video's player on the iPhone and choose Veil. Only
  the video plays, in the floating window (ExoPlayer, HLS); play, pause, seeking and the audio
  track picked on the iPhone follow its controls, and the iPhone's own player shows the position.
  The car downloads the video over its Wi-Fi when it has one with internet, else over its default
  network; the Network page log says which. A "Video casting" switch on the AirPlay page (on by
  default) turns it off, which keeps every app on screen mirroring and uses no car internet.
  Copy-protected apps still will not play. From 7xDash, where the real-car fixes were made:
  the receiver trims YouTube's track list before fetching, starts after one second of video,
  and asks for media audio focus so the sound plays without a radio channel first.

## 0.1.1-alpha (2026-10-28)

- AirPlay picture lag: decoded frames now go on screen from their own thread the moment the
  decoder has them. 0.1.0 collected them only when the next frame arrived, so the picture ran
  as many frames behind as the car's decoder holds, and since an iPhone sends frames only while
  its screen changes, the end of each movement waited for the next touch.
- AirPlay sound only (a music app's AirPlay button rather than Screen Mirroring): the receiver
  now offers ALAC, the format the iPhone sends for that, and plays it through Android's own
  ALAC decoder (Veil has no FFmpeg). 0.1.0 had ALAC off and stayed silent. If the head unit
  has no ALAC decoder, the log says `audio/alac decoder init failed`.
- The AirPlay page says, under the status line, why there is no picture: the iPhone connected
  for sound only (with a pointer to Screen Mirroring), the overlay permission is missing, or
  the car refused the floating window. Toasts from the receiver may not show on the head unit.
- Network page: the iPhone's keep-alive (`/feedback`, every two seconds) is left out of the
  last log lines so the lines that matter fit; the log file keeps everything. "Mirroring
  stopped" is logged once per session, not twice.

## 0.1.0-alpha (2026-10-28)

First build to run on the car. Untested there: everything below needs an in-car check.
Video casting from an app's AirPlay button is not in yet.

- AirPlay receiver: the head unit shows up in the iPhone's Screen Mirroring list under a name you
  can change; the iPhone's screen plays in a floating window (drag, pinch, dock to a side with a
  strip showing, double-tap for small or large, × to stop) and its sound through the car's
  speakers with media audio focus, so the radio pauses. Veil reads nothing from the car and never
  hides the picture for driving (the owner's decision). Veil's own mDNS responder answers each
  network with its own address. The Network page shows the receiver's address, why the iPhone
  might not see it, the car's networks and the AirPlay log.
  AirPlay and the veil come back after a reboot if they were left on. UxPlay through
  jqssun/android-airplay-server, as in 7xDash (all GPL-3.0).

- Dim overlay: an adjustable veil over the centre screen (opacity, brightness, warmth, touches
  pass through, reset), drawn on the default display only, never on the HUD or the cluster.
  Its own floating moon button: tap for on/off, drag right or down for darker and left or up
  for lighter, hold then drag to move it; position remembered. Settings survive a restart and
  the veil comes back the way it was left when Veil starts.
- Automatic dim, untested on the car: by the clock (19:00 to 07:00 by default, hours adjustable,
  from 7xDash's experimental version) or following the head unit's own night mode. The rule acts
  only when its answer changes, so a tap on the moon holds until the next sunset or sunrise.

- Scaffold: Gradle project (AGP 9, JDK 17, minSdk 28, arm64-v8a), GPL-3.0 licence and NOTICE,
  the design shell (64 dp title bar, 280 dp section list, day/night palette, six text sizes,
  app-drawn dialogs) and a plain-Java settings store with unit tests.
