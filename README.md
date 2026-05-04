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

NeoForge addon for [Create: Aeronautics](https://modrinth.com/mod/create-aeronautics), specifically the bundled Simulated physics module that handles ropes. Adds two empty-hand climb modes alongside Simulated's existing wrench-driven zipline system: near-vertical rope strands, and plunger-fired ropes between paired `LaunchedPlungerEntity` projectiles.

## How it works

Simulated already has a rope-strand riding system (`ZiplineClientManager`) that lets the player slide along a rope held by Create's wrench-tagged item. That system is left completely untouched (no mixins, no overrides), so ziplines behave exactly as in vanilla Simulated.

This mod adds two **separate** climb modes driven by empty-hand interaction.

### Vertical rope strands

- The player right-clicks a near-vertical rope strand with an **empty main hand**.
- A `ClientTickEvent.Post` handler (`ClimbController`) raycasts against rope strands using Simulated's own `ZiplineClientManager.raycastRope` helper.
- If a strand within reach is hit and the segment's closest-point normal is at least 85% vertical (~32° from perfect vertical), we record the rope UUID, send Simulated's `RopeRidingPacket` so the server treats the player as riding (no fall damage, hanging animation, etc.), and run per-tick physics.
- W climbs up, S descends, sprint + S slides faster (smooth acceleration up, smooth coast back down).

### Plunger ropes

- Two paired `LaunchedPlungerEntity` projectiles (fired from Simulated's Plunger Launcher) form a rope between them.
- `PlungerClimbController` iterates `ClientLevel.entitiesForRendering()` for plungers, computes endpoint positions in world render space (accounting for Sable sublevel transforms so ropes attached to assembled physics objects work), and ray-segment-tests against each pair.
- Embark direction is locked when grabbing the rope. For vertical-ish ropes W goes to the upper end; otherwise W goes toward whichever end the player is facing.
- Slide acceleration and terminal speed scale with `sin(angle from horizontal)`, so vertical plunger ropes accelerate fastest and near-horizontal ropes don't slide beyond the descend baseline.
- The same `RopeRidingPacket` signal drives the hanging animation in multiplayer.

## Building

This depends on Simulated's compiled jar. Either:

- Build Simulated locally first (`gradlew :simulated:neoforge:build` in `Simulated-Project/`), or
- Drop a built `simulated-neoforge-*.jar` into `./libs/`.

Then run `gradlew build`.

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

## Configuration

Server-side config lives at `<world>/serverconfig/climbable_ropes-server.toml`, generated on first world load. On a dedicated server the operator owns it; in singleplayer the player owns it (per world). Values auto-sync to connected clients on join, so the server is authoritative. Clients can't tweak the file locally to climb faster.

### `[climbing]`

| Key | Default | Description |
| --- | --- | --- |
| `climbSpeed` | `0.18` | Vertical speed when holding forward (blocks/tick). |
| `descendSpeed` | `0.22` | Vertical speed when holding back (blocks/tick). |
| `jumpOffVelocity` | `0.42` | Upward impulse applied when jumping off. |

### `[sliding]`

| Key | Default | Description |
| --- | --- | --- |
| `slideSpeed` | `1.2` | Maximum sprint-descend slide speed. |
| `slideAcceleration` | `0.05` | How quickly the slide ramps up. |
| `slideDeceleration` | `0.04` | How quickly the slide eases out after releasing back. |

### `[features]`

| Key | Default | Description |
| --- | --- | --- |
| `allowVerticalRopeClimbing` | `true` | Toggle vertical hanging-rope climb. |
| `allowPlungerClimbing` | `true` | Toggle plunger-line climb. |

Toggling a feature off only blocks new embarkations; in-progress climbs finish normally.
