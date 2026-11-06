# Changelog

Each version's section becomes its GitHub release notes. Versions with a suffix are pre-releases.

## 0.3.1-alpha (2026-11-06)

- Driver display test, second round, after 0.3.0 put its RIGHT label in the cluster's
  navigation window on the first try. The pattern is now a measuring grid: a line every 160 px
  across and 134 px down, each cell labelled with its pixel position, so a photo of the cluster
  gives the exact rectangle it shows. It stays for 20 seconds. A new switch, Ask the head unit to
  project (on by default), runs the test with or without the projection request, to tell whether
  the request is needed at all. A second switch, Ask for the full-width layout (on by default),
  also asks the cluster for its full-width navigation layout, the call the car's own navigation
  uses, and puts the small window back when the test ends; the 7X's menu offers only the small
  window, so this finds out whether the cluster still has the full one and takes the request from
  another app. An interrupted test puts the small window back on the next start as well.

## 0.3.0-alpha (2026-11-06)

- New Driver display page with a test for showing something on the instrument cluster. The
  test covers the cluster's 2560x536 screen with a pattern labelled LEFT, CENTRE and RIGHT for
  8 seconds and asks the head unit to project it through Zeekr's own cluster interface
  (request, start; stop when the test ends), the way the car's navigation puts its map there.
  It runs only in P: the gear is read from the car, and if it cannot be read the page asks you to
  confirm. Every step goes to the Driver display log. If the app is stopped mid-test, the next
  start sends the stop. Moved here from CAMCORDER, where the first round (a card in the middle
  of the screen, no projection request) was accepted by Android but never shown by the cluster,
  which on the 7X exposes Android content only in its navigation window.

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
