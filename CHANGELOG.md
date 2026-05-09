## Version 1.6.2

### Fixed
- Fixed getting stuck against a block when right-clicking a plunger rope to start climbing. The player is now teleported onto the rope at embark instead of relying on the spring to drag them in, with a clean abort if no valid position exists.
- Fixed the spring being able to pin the player against a ceiling with uncapped vertical velocity. Snap-pull velocity is now clamped on the full 3D magnitude rather than horizontal only.
- Fixed any ground contact (including touching a ceiling mid-climb) triggering dismount. Auto-dismount now only triggers when the player is both on the ground and near the lower plunger end, matching the behavior of vertical ropes.
- Fixed plunger ropes behind walls being selectable through geometry. Rope hover detection now uses the block raycast depth as its threshold, so ropes occluded by blocks can no longer be highlighted through them.
- Added a leash check: if external forces push the player more than 3 blocks off the rope line, they cleanly dismount instead of rubber-banding back.
