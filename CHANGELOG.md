## Version 2.0.1

### Changed
- Animation settings (enable/disable climb animations, animation speed) moved to a client-side config file so individual players can adjust them without server-admin access.

### Fixed
- Fixed W/S keys being inverted on angled ropes: W now always moves you toward the higher end, S toward the lower end, regardless of which direction you're looking.
- Fixed grabbing a rope when the raycast aimed at a vertical segment but a different, near-horizontal segment of the same rope was closer to your body, causing the grab to be incorrectly rejected.
- Fixed plunger zipline mode ignoring the `maxLeashDistance` and `groundedDismountTicks` config settings; both values are now applied consistently.
