## Version 2.0.0

### Added
- Added player climb animations for rope climbing, powered by the Player Animator library. Players now visually climb hand-over-hand, idle with a one-handed hang, and brace during fast slides.
- Added climb animation syncing so other players in multiplayer can see your climbing animations.
- Added a new Animation config section with options to enable/disable climb animations and adjust animation speed.

### Changed
- The player model now tilts gently to follow the rope angle while climbing, giving a natural lean on diagonal ropes.
- Create's chain conveyor hanging pose is now suppressed while riding the mod's ropes, so the custom climb animations display correctly.
- Climb animations currently apply only to vertical ropes (within 45° of vertical); ropes beyond that angle fall back to Create's default pose, as animations for steeper angles need more work and will be added in a later version.

### Fixed
- Fixed players drifting off near-horizontal or ground-level ropes due to vanilla walk input bleeding through during a climb.
- Fixed the climb animation flickering at the bottom of a rope when the rope swings the player on and off the ground.
- Fixed players being carried past the end of a rope instead of dismounting at a consistent distance.
- Fixed the player hanging to the side of near-horizontal ropes instead of directly below them.