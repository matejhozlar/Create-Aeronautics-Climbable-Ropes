## Version 1.6.0

### Added
- Added a configurable maximum climb angle (`maxClimbAngleFromVertical`, default 31.79 degrees) so players can grab and ride ropes at shallower angles.
- Climbing motion now follows the rope's local direction, so the W/S keys move you along the strand based on how you grabbed it rather than the rope's placement direction.

### Fixed
- Fixed players getting stranded off a rope when a piston, explosion, or other external force pushed them too far from it. The player now dismounts automatically when drifting more than 3 blocks from the nearest rope point.
- Fixed a jarring vertical pop that could occur when drifting along a near-horizontal rope due to the snap-pull not being capped in the vertical direction.
