## Version 1.3.0

### Added
- Added a server-side configuration file (`climbable_ropes-server.toml`) that lets server operators customize climbing and sliding behavior without restarting. Settings include climb speed, descend speed, jump-off velocity, slide speed/acceleration/deceleration, and toggles to enable or disable vertical rope climbing and plunger climbing. The config lives in the world's `serverconfig` folder and is automatically synced to connected clients.

### Fixed
- Fixed players floating above the top of a rope when climb speed was configured very high. Upward movement is now clamped so players stop precisely at the rope's endpoint.
