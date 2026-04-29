# Create Aeronautics: Climbable Ropes

NeoForge addon for [Create: Aeronautics](https://modrinth.com/mod/create-aeronautics) — specifically the bundled Simulated physics module that handles ropes. Lets the player grab and climb vertical rope strands with an empty hand, separate from the existing wrench-driven zipline system.

## How it works

Simulated already has a rope-strand riding system (`ZiplineClientManager`) that lets the player slide along a rope held by Create's wrench-tagged item. That system is left completely untouched — no mixins, no overrides — so ziplines behave exactly as in vanilla Simulated.

This mod adds a **separate** climb mode driven by empty-hand interaction:

- The player right-clicks a near-vertical rope strand with an **empty main hand**.
- A `ClientTickEvent.Post` handler (`ClimbController`) raycasts against rope strands using Simulated's own `ZiplineClientManager.raycastRope` helper.
- If a strand within reach is hit and the segment under the player is near-vertical (verticalDot >= 0.97, ~14° from perfect vertical), we record the rope UUID in our own state, send Simulated's `RopeRidingPacket` so the server treats the player as riding (no fall damage, etc.), and run our own per-tick physics.
- W = up, S = down, Shift = dismount. Camera yaw rotates the player around the rope.
- Ziplines and the wrench mode are entirely separate code paths and behave normally.

## Building

This depends on Simulated's compiled jar. Either:

- Build Simulated locally first (`gradlew :simulated:neoforge:build` in `Simulated-Project/`), or
- Drop a built `simulated-neoforge-*.jar` into `./libs/`.

Then run `gradlew build`.

## Controls

To grab on: look at a near-vertical rope strand within block-interaction range, with an **empty main hand**, and right-click.

While climbing:

| Input | Action |
| --- | --- |
| `Forward` (W) | Climb up |
| `Back` (S) | Climb down |
| `Sneak` (Left Shift) | Dismount (let go, no impulse) |
| `Jump` (Space) | Jump off — dismounts with a vanilla-jump-strength upward impulse |
| Stand on the ground at the bottom (no forward) | Auto-dismount after a few ticks |
| Mouse / camera | Rotates the player around the rope; you hang on the side you're facing |
| Toggle creative flight | Dismount |
| Switch off empty hand | Dismount |

Sneak-to-dismount matches Simulated's existing on-embark hint ("Press [LEFT SHIFT] to dismount").

Embarking still uses the existing Simulated UX (look at the rope while holding a `CHAIN_RIDEABLE`-tagged item — typically Create's wrench — and right-click).
