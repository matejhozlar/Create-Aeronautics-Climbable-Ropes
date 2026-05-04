## Version 1.4.0

### Added
- Added ziplining along plunger-rope lines: right-click a plunger rope while holding a CHAIN_RIDEABLE-tagged item (such as Create's wrench) to ride it as a zipline. Physics mirror Simulated's existing strand zipline, with damping, assistance, and spring forces. A horizontal plunger rope can also be walked across as a temporary bridge. Can be disabled via the `features.allowPlungerZipline` config option.

### Changed
- Grabbing a rope now teleports the player directly to their final hanging position instead of snapping to the centerline and easing over several ticks, making embarkation feel instant and smooth.
- Automatic ceiling-snap behavior at the top of a rope has been replaced with a manual mantle: press Jump while at the top of a rope (or pressed against a ceiling) to pull up onto the surface above. If no clear landing spot is found, the normal jump-off fires as a fallback.
