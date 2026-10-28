# Changelog

Each version's section becomes its GitHub release notes. Versions with a suffix are pre-releases.

## 0.1.1-alpha (2026-10-28)

- AirPlay: the floating window opens on the centre screen the same way the veil does, through
  that display's own context. In 0.1.0 the iPhone said it was connected and the car showed
  nothing. If the car still refuses the window, the AirPlay page says why under the status
  line, because toasts from the receiver may not show on the head unit.
- AirPlay: when the iPhone connects for sound only (the AirPlay icon on a music player rather
  than Screen Mirroring), the AirPlay page says so and points to Screen Mirroring. The 0.1.0
  log from the car showed exactly this: a sound session, no mirror stream.

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
