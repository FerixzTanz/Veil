# Changelog

Each version's section becomes its GitHub release notes.

## Unreleased

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
