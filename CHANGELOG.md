## Version 1.6.1

### Fixed
- Fixed perfectly vertical ropes (hanging straight down) not being detectable for climbing. The upstream Simulated mod's `ZiplineClientManager.raycastRope` produces a NaN quaternion for straight-down rope segments, causing the bounding box clip to silently return empty. This mod now uses its own ray-segment closest-approach test instead, bypassing that code path.
