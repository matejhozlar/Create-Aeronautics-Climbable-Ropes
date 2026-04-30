# Climbable Ropes for Create Aeronautics

NeoForge addon for [Create: Aeronautics](https://modrinth.com/mod/create-aeronautics) — specifically the bundled Simulated physics module that handles ropes. Adds two empty-hand climb modes alongside Simulated's existing wrench-driven zipline system: near-vertical rope strands, and plunger-fired ropes between paired `LaunchedPlungerEntity` projectiles.

## How it works

Simulated already has a rope-strand riding system (`ZiplineClientManager`) that lets the player slide along a rope held by Create's wrench-tagged item. That system is left completely untouched — no mixins, no overrides — so ziplines behave exactly as in vanilla Simulated.

This mod adds two **separate** climb modes driven by empty-hand interaction.

### Vertical rope strands

- The player right-clicks a near-vertical rope strand with an **empty main hand**.
- A `ClientTickEvent.Post` handler (`ClimbController`) raycasts against rope strands using Simulated's own `ZiplineClientManager.raycastRope` helper.
- If a strand within reach is hit and the segment's closest-point normal is at least 85% vertical (~32° from perfect vertical), we record the rope UUID, send Simulated's `RopeRidingPacket` so the server treats the player as riding (no fall damage, hanging animation, etc.), and run per-tick physics.
- W climbs up, S descends, sprint + S slides faster (smooth acceleration up, smooth coast back down).

### Plunger ropes

- Two paired `LaunchedPlungerEntity` projectiles (fired from Simulated's Plunger Launcher) form a rope between them.
- `PlungerClimbController` iterates `ClientLevel.entitiesForRendering()` for plungers, computes endpoint positions in world render space (accounting for Sable sublevel transforms so ropes attached to assembled physics objects work), and ray-segment-tests against each pair.
- Embark direction is locked when grabbing the rope — for vertical-ish ropes W goes to the upper end; otherwise W goes toward whichever end the player is facing.
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
| `Sprint` + `Back` | Slide — accelerates from descend speed up to a sustained slide; coasts to a stop on release |
| `Sneak` (Left Shift) | Dismount (let go, no impulse) |
| `Jump` (Space) | Jump off — dismounts with a vanilla-jump-strength upward impulse |
| Stand on the ground without pressing forward | Auto-dismount after a few ticks |
| Mouse / camera | Rotates the player around the rope; you hang on the side you're facing |
| Toggle creative flight | Dismount |
| Switch off empty hand | Dismount |

Sneak-to-dismount matches Simulated's existing on-embark hint ("Press [LEFT SHIFT] to dismount").

Embarking the existing Simulated zipline mode still works the same — look at the rope while holding a `CHAIN_RIDEABLE`-tagged item (typically Create's wrench) and right-click.
