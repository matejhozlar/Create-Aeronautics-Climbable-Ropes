## Version 1.10.0

### Added
- Player climb animations driven by [KosmX's Player Animator](https://modrinth.com/mod/player-animator). Empty-hand climbing now plays a body/arm/leg pose layer on the local player: a hand-over-hand overhead-reach `climb_up` loop while holding forward, a lower-amplitude `descend` loop while holding back or coasting from a slide, a static braced `slide` pose while sprint-sliding, and a casual one-handed `idle` hang with a slow lazy sway while resting on the rope. The body bone tilts to the pitch of the rope segment so diagonal ropes visibly lean the player.
- New `[animation]` config section with `enableClimbAnimation` (default `true`) and `animationSpeedMultiplier` (default `1.0`, range `0.1`-`5.0`).
- Player Animator is declared as a required client-side dependency in `neoforge.mods.toml`; install it alongside Climbable Ropes.

## Version 1.9.0

### Added
- Added an in-game configuration screen, accessible via the Config button on the Mods list. All mod settings are labeled in English with descriptive tooltips.

### Changed
- Corrected the website and issue tracker links shown in the Mods screen.
