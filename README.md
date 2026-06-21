# Climbable Ropes for Create Aeronautics

<p align="center">
  <a href="https://modrinth.com/mod/create-aeronautics-climbable-rope"><img src="https://img.shields.io/modrinth/dt/jImqv1M5?logo=modrinth&label=Modrinth&color=00AF5C&style=for-the-badge" alt="Modrinth Downloads"></a>
  <a href="https://www.curseforge.com/projects/1528764"><img src="https://img.shields.io/curseforge/dt/1528764?logo=curseforge&label=CurseForge&color=F16436&style=for-the-badge" alt="CurseForge Downloads"></a>
</p>

<p align="center">
  <a href="https://www.minecraft.net/"><img src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft" alt="Minecraft"></a>
  <a href="https://neoforged.net/"><img src="https://img.shields.io/badge/NeoForge-21.1.227%2B-DC2626" alt="NeoForge"></a>
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"></a>
</p>

NeoForge addon for [Create: Aeronautics](https://modrinth.com/mod/create-aeronautics), specifically the bundled Simulated physics module that handles ropes. Adds two empty-hand climb modes alongside Simulated's existing wrench-driven zipline system: hanging rope strands (vertical by default; the angle gate is configurable up to fully horizontal), and plunger-fired ropes between paired `LaunchedPlungerEntity` projectiles.

<p align="center">
  <img src="docs/images/climbable-ropes-1.jpg" alt="Player climbing a rope hanging from an airship at sunset" width="720">
</p>

## How it works

Simulated already has a rope-strand riding system (`ZiplineClientManager`) that lets the player slide along a rope held by Create's wrench-tagged item. That system is left completely untouched (no mixins, no overrides), so ziplines behave exactly as in vanilla Simulated.

This mod adds two **separate** climb modes driven by empty-hand interaction.

### Hanging rope strands

- The player right-clicks a rope strand with an **empty main hand**.
- A `ClientTickEvent.Post` handler (`ClimbController`) raycasts against rope strands using Simulated's own `ZiplineClientManager.raycastRope` helper.
- If a strand within reach is hit and the segment is within `maxClimbAngleFromVertical` of vertical (default ~32°, configurable up to 90° for fully horizontal ropes), we record the rope UUID, send Simulated's `RopeRidingPacket` so the server treats the player as riding (no fall damage, hanging animation, etc.), and run per-tick physics.
- Climb motion follows the local rope tangent rather than pure Y, with arc-length-based top/bottom limits and a 3D snap-pull, so diagonal and horizontal climbs feel natural.
- Forward direction (W) is locked at embark: vertical-ish ropes use the higher-Y endpoint; for shallower ropes the player's look direction wins, so W follows how the player attached, not how the rope was placed.
- W climbs up, S descends, sprint + S slides faster (smooth acceleration up, smooth coast back down). On near-horizontal ropes, slide acceleration scales with `|tangent.y|`, so a flat rope just travels at descend speed.

### Plunger ropes

- Two paired `LaunchedPlungerEntity` projectiles (fired from Simulated's Plunger Launcher) form a rope between them.
- `PlungerClimbController` iterates `ClientLevel.entitiesForRendering()` for plungers, computes endpoint positions in world render space (accounting for Sable sublevel transforms so ropes attached to assembled physics objects work), and ray-segment-tests against each pair.
- Embark direction is locked when grabbing the rope. For vertical-ish ropes W goes to the upper end; otherwise W goes toward whichever end the player is facing.
- Slide acceleration and terminal speed scale with `sin(angle from horizontal)`, so vertical plunger ropes accelerate fastest and near-horizontal ropes don't slide beyond the descend baseline.
- The same `RopeRidingPacket` signal drives the hanging animation in multiplayer.

### Plunger ziplines

- Right-clicking a plunger rope while holding a `CHAIN_RIDEABLE`-tagged item (typically Create's wrench) embarks the player as a zipline rider rather than a climber.
- `PlungerZiplineController` mirrors Simulated's `ZiplineClientManager.ridingTick` physics: per-tick damping (`v * -0.6`, with the along-rope component subtracted), assistance (`dir * v.dot(dir) * 0.04`), and a spring force pulling the player anchor toward the closest point on the segment. Gravity and normal `WASD` motion come from vanilla player physics, so a horizontal plunger rope can be walked across as a temporary bridge.
- No steepness gate: plunger ropes are taut straight lines, so any angle is rideable (unlike the strand zipline which gates by `maxRopeZiplineAngle`).
- Dismount on Sneak, fly toggle, more than 5 ticks grounded, plunger removed/unplunged, or pushed past either endpoint with `velocity.dot(dir) > 0.6`.

## Building

Simulated has no public maven; it ships only jar-in-jar'd inside Create: Aeronautics. The build extracts Simulated's compiled classes from that bundle automatically (downloading Create: Aeronautics from Modrinth, pinned by `create_aeronautics_version` in `gradle.properties`), so no separate Simulated checkout is required.

```sh
gradlew build
```

The extracted Simulated jar lands at `build/extracted-simulated/simulated.jar`; the output mod jar lands in `build/libs/climbable_ropes-<version>.jar`.

## Controls

To grab on: look at a near-vertical rope strand or a plunger rope within block-interaction range, with an **empty main hand**, and right-click.

While climbing:

| Input | Action |
| --- | --- |
| `Forward` (W) | Climb up (or along the rope toward the locked forward end on plunger ropes) |
| `Back` (S) | Descend |
| `Sprint` + `Back` | Slide: accelerates from descend speed up to a sustained slide, coasts to a stop on release |
| `Sneak` (Left Shift) | Dismount (let go, no impulse) |
| `Jump` (Space) | Jump off with a vanilla-jump-strength upward impulse. When you're at the top of the rope and a block is above, jump instead mantles you onto that block. |
| Stand on the ground without pressing forward | Auto-dismount after a few ticks |
| Mouse / camera | Rotates the player around the rope; you hang on the side you're facing |
| Toggle creative flight | Dismount |
| Switch off empty hand | Dismount |

Sneak-to-dismount matches Simulated's existing on-embark hint ("Press [LEFT SHIFT] to dismount").

Embarking the existing Simulated zipline mode still works the same: look at the rope while holding a `CHAIN_RIDEABLE`-tagged item (typically Create's wrench) and right-click.

### Ziplining a plunger rope

Hold a `CHAIN_RIDEABLE`-tagged item (typically Create's wrench), look at a plunger rope within block-interaction range, and right-click. You'll latch on and slide under gravity + your own input. Sneak to dismount, or run off either endpoint with enough forward momentum.

## Configuration

Server-side config lives at `<world>/serverconfig/climbable_ropes-server.toml`, generated on first world load. On a dedicated server the operator owns it; in singleplayer the player owns it (per world). Values auto-sync to connected clients on join, so the server is authoritative. Clients can't tweak the file locally to climb faster.

The tables below are the quick reference. For a full guide with units, in-game behavior, interactions between settings, and tuning recipes, see [CONFIGURATION.md](CONFIGURATION.md).

### `[climbing]`

| Key | Default | Description |
| --- | --- | --- |
| `climbSpeed` | `0.18` | Speed along the rope when holding forward (blocks/tick). |
| `descendSpeed` | `0.22` | Speed along the rope when holding back (blocks/tick). |
| `jumpOffVelocity` | `0.42` | Upward impulse applied when jumping off. |
| `maxClimbAngleFromVertical` | `90.0` | Maximum angle from vertical (degrees) at which a hanging-rope segment can be grabbed. The default allows any angle, including horizontal. Lower toward `0` to require near-vertical ropes (e.g. `31.79` matches the legacy 0.85 dot-product threshold). |

### `[sliding]`

| Key | Default | Description |
| --- | --- | --- |
| `slideSpeed` | `1.2` | Maximum sprint-descend slide speed. |
| `slideAcceleration` | `0.05` | How quickly the slide ramps up. |
| `slideDeceleration` | `0.04` | How quickly the slide eases out after releasing back. |

### `[features]`

| Key | Default | Description |
| --- | --- | --- |
| `allowVerticalRopeClimbing` | `true` | Toggle hanging-rope climb. |
| `allowPlungerClimbing` | `true` | Toggle plunger-line climb. |
| `allowPlungerZipline` | `true` | Toggle ziplining along plunger ropes with a `CHAIN_RIDEABLE`-tagged item. |
| `allowBlockMantle` | `true` | When jumping off at the top end of a rope, mantle onto the block above instead of getting a normal upward impulse. |

Toggling a feature off only blocks new embarkations; in-progress climbs finish normally.

### `[advanced]`

| Key | Default | Description |
| --- | --- | --- |
| `snapPull` | `0.55` | How aggressively the spring drags you toward the rope each tick. |
| `snapVelocityCap` | `0.35` | Maximum per-tick velocity the snap spring can contribute. |
| `maxLeashDistance` | `3.0` | Distance from the rope (blocks) at which external forces dismount you. |
| `bottomDismountOffset` | `0.6` | How close to the lower endpoint counts as "at the bottom" for the grounded auto-dismount. |
| `bottomGroundedDismountTicks` | `5` | Ticks of ground contact at the bottom before auto-dismount. |
| `ropeHoverRadius` | `0.25` | Raycast hitbox radius (blocks) for rope hover detection. Larger values make ropes easier to aim at. |

Defaults preserve the standard behavior; these affect physics feel and targeting, so change them with care.

### `[animation]`

| Key | Default | Description |
| --- | --- | --- |
| `enableClimbAnimation` | `true` | Play the rope-climb body/arm/leg animations while attached to a rope. |
| `animationSpeedMultiplier` | `1.0` | Playback-speed multiplier for the climb animations. `1.0` is the authored speed. |

Animations are driven by [KosmX's Player Animator](https://modrinth.com/mod/player-animator), an MIT-licensed library. Climbable Ropes bundles it via jar-in-jar, so end users do not install it separately. If a standalone copy is also present, NeoForge deduplicates by version and loads whichever is newer.
