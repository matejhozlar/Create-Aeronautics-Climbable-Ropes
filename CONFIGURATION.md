# Configuration

This document describes every configuration option exposed by Climbable Ropes
for Create Aeronautics, where the file lives, how values are validated, and the
practical effect each setting has on climbing.

If you just want the keys at a glance, the [README](README.md#configuration)
has a compact table. This file goes deeper: it explains the units, the in-game
behavior tied to each key, and how the settings interact, so you can tune the
mod without guessing.

## File location

The mod registers a single **server-type** NeoForge config. NeoForge writes the
file to:

```
<world>/serverconfig/climbable_ropes-server.toml
```

- On a **dedicated server**, the file lives next to the world's other server
  configs and the server operator owns it.
- In **singleplayer** and on **LAN-hosted** worlds, the integrated server
  writes the file inside the save folder, so each world has its own copy. A
  change you make in one save does not carry over to another save.
- The file is **generated on first world load** with all defaults. To restore
  defaults at any time, stop the server (or close the world), delete the file,
  and start again. NeoForge regenerates it.

Because the config is server-type, **values are authoritative on the server**
and auto-sync to connected clients when they join. A client cannot edit their
local copy to climb faster than the server allows. Editing the file on a
dedicated server requires a restart (or `/reload`-equivalent) to pick up
changes, just like any other server config.

## File format

The file is standard TOML with three sections: `[climbing]`, `[sliding]`, and
`[features]`. Comments describing each key are written into the file
automatically. A fresh default file looks like this:

```toml
# Climbing motion (blocks per tick).
[climbing]
	#Vertical speed when holding forward to climb up.
	#Range: 0.0 ~ 5.0
	climbSpeed = 0.18
	#Vertical speed when holding back to climb down.
	#Range: 0.0 ~ 5.0
	descendSpeed = 0.22
	#Upward impulse applied when jumping off the rope.
	#Range: 0.0 ~ 5.0
	jumpOffVelocity = 0.42
	#Maximum angle from vertical (in degrees) at which a hanging rope segment can be grabbed for climbing.
	#0 = only perfectly vertical ropes; 90 = any angle including horizontal. Increase to climb diagonal lines.
	#Range: 0.0 ~ 90.0
	maxClimbAngleFromVertical = 31.79

# Sprint-while-descending slide mechanics.
[sliding]
	#Maximum slide speed.
	#Range: 0.0 ~ 10.0
	slideSpeed = 1.2
	#How quickly the slide ramps up to slideSpeed.
	#Range: 0.0 ~ 1.0
	slideAcceleration = 0.05
	#How quickly the slide eases out after releasing back.
	#Range: 0.0 ~ 1.0
	slideDeceleration = 0.04

# Toggle individual climbing features.
[features]
	#Allow climbing vertical hanging ropes.
	allowVerticalRopeClimbing = true
	#Allow climbing rope lines between two plungers.
	allowPlungerClimbing = true
	#Allow ziplining along plunger rope lines while holding a CHAIN_RIDEABLE-tagged item (e.g. Create's wrench), mirroring Simulated's existing zipline on hanging rope strands.
	allowPlungerZipline = true
	#Allow mantling onto the block above the rope when jumping off at its top end. When disabled, jumping off at the top performs a normal upward impulse instead.
	allowBlockMantle = true
```

Values out of range are clamped or rejected by NeoForge's `ModConfigSpec`
validation. If you set a value to something nonsensical (a string where a
number is expected, a number above the documented maximum, etc.), NeoForge
falls back to the default for that key and logs a warning.

## Units and tick rate

Minecraft runs at **20 ticks per second**. All speed values below are in
**blocks per tick**, which means:

- `climbSpeed = 0.18` is `0.18 * 20 = 3.6` blocks/second going up.
- `descendSpeed = 0.22` is `4.4` blocks/second going down.
- `slideSpeed = 1.2` is `24` blocks/second (the slide cap, only reached on
  fully vertical ropes; shallower ropes get a fraction of this).

Distances such as `maxClimbAngleFromVertical` are in **degrees from vertical**
(0 means perfectly vertical, 90 means horizontal). All other config values are
plain numbers as documented per key.

## `[climbing]`

### `climbSpeed`

- **Type:** double
- **Default:** `0.18` (3.6 blocks/sec going up a vertical rope)
- **Range:** `0.0` to `5.0`

Speed at which the player travels **along the rope** when holding `Forward`
(W). On a vertical rope this is purely vertical motion; on diagonal and
plunger ropes the speed is applied along the rope's tangent (so a 45-degree
rope at `0.18` still travels `0.18` blocks per tick, but only ~`0.127` of that
is vertical and ~`0.127` is horizontal).

Both climb modes (hanging-rope and plunger-rope) read this same value, so
tuning it affects both.

`0` makes climbing up impossible (you'll just hang there). Values above ~`1.5`
look unnatural and can cause the snap-pull to overshoot on short ropes.

### `descendSpeed`

- **Type:** double
- **Default:** `0.22` (4.4 blocks/sec going down a vertical rope)
- **Range:** `0.0` to `5.0`

Speed at which the player travels along the rope when holding `Back` (S),
without sprinting. This is the **baseline** descend rate; sliding (sprint +
back) lets the player exceed it, scaled by how vertical the rope is.

`descendSpeed` should normally be slightly higher than `climbSpeed`, matching
the intuition that gravity helps you down. Setting `descendSpeed` lower than
`climbSpeed` is allowed but feels strange in play.

### `jumpOffVelocity`

- **Type:** double
- **Default:** `0.42` (matches a vanilla full-strength jump)
- **Range:** `0.0` to `5.0`

Upward velocity (Y component of `Entity#setDeltaMovement`) applied when the
player presses `Jump` (Space) while attached to a rope. The mod replaces the
player's Y velocity with `max(currentY, jumpOffVelocity)`, so it never
*reduces* upward momentum.

Edge case: when `allowBlockMantle` is `true` and the player is at the top of
the rope with a block above, pressing jump performs a mantle onto that block
*instead* of using `jumpOffVelocity`. See `allowBlockMantle` for the exact
trigger.

Setting this to `0` makes "jump off" act like a clean release with no
impulse, useful for low-gravity airships where you don't want a launch.

### `maxClimbAngleFromVertical`

- **Type:** double (degrees)
- **Default:** `31.79`
- **Range:** `0.0` to `90.0`
- **Applies to:** hanging rope strands only. Plunger ropes ignore this gate
  and are climbable at any angle.

This is the **angle-from-vertical** gate that decides whether a hanging rope
segment is grabbable. When you right-click a rope, the mod computes the angle
between the closest rope segment and world up. If that angle is at or below
`maxClimbAngleFromVertical`, the rope is grabbable; otherwise the click is
ignored and you do not embark.

The default of `31.79°` corresponds to the historical "dot product >= 0.85"
threshold (`acos(0.85) ≈ 31.79°`), kept for behavior continuity.

Useful values:

| Value | Effect |
| --- | --- |
| `0` | Only perfectly plumb ropes can be climbed. A rope swinging in the wind becomes briefly ungrabbable. |
| `~30` (default) | Lets you grab ropes that are mostly vertical, including ropes attached to a moving airship that are angled by drag. |
| `45` | Climb anything closer to vertical than to horizontal. |
| `60`-`75` | Useful for shallow rigging, but slide mechanics become noticeably less effective on rope segments near this angle. |
| `90` | Any orientation, including a perfectly horizontal rope. |

Even at `90`, sliding still scales with verticality (see `[sliding]`), so a
horizontal rope is rideable but won't accelerate beyond `descendSpeed`.

## `[sliding]`

Sliding only activates when the player is holding **`Sprint` + `Back`** at the
same time, and even then only when sliding would actually be faster than
walking down at `descendSpeed`. The exact trigger is:

```
slideEffective = sprint && back && slideSpeed * vertical(rope) > descendSpeed
```

For hanging ropes, `vertical(rope)` is the absolute Y component of the locked
forward tangent. For plunger ropes, it is `|sin(angle from horizontal)|`,
i.e. `|dir.y|` of the rope's unit direction. The practical consequence: the
flatter the rope, the harder it is for sliding to outpace descending, and on
a fully horizontal rope sliding has no effect.

### `slideSpeed`

- **Type:** double (blocks per tick)
- **Default:** `1.2` (24 blocks/sec on a fully vertical rope)
- **Range:** `0.0` to `10.0`

The **maximum** slide velocity, reached only after the slide has ramped up
from `descendSpeed` via `slideAcceleration`. On a non-vertical rope the
effective cap is `slideSpeed * verticality`, so a 45-degree rope tops out at
~`0.85` blocks/tick.

Values above ~`2.5` start producing speeds where the snap-pull (which keeps
the player glued to the rope) can struggle, especially on short ropes.

### `slideAcceleration`

- **Type:** double (blocks per tick per tick)
- **Default:** `0.05`
- **Range:** `0.0` to `1.0`

How much velocity is added per tick while the slide is ramping up. Scaled by
`verticality` of the rope, so a vertical rope gains the full `0.05`/tick and
a 45-degree rope gains ~`0.035`/tick.

At the default, going from `descendSpeed = 0.22` to `slideSpeed = 1.2` on a
vertical rope takes about `(1.2 - 0.22) / 0.05 = 19.6` ticks, just under one
second.

Lower values make sliding feel weighty and gradual; higher values let you hit
top speed almost immediately.

### `slideDeceleration`

- **Type:** double (blocks per tick per tick)
- **Default:** `0.04`
- **Range:** `0.0` to `1.0`

How much velocity is bled per tick when the slide trigger is released (the
player stops pressing back, releases sprint, or starts climbing up). This is
**not** scaled by rope verticality, so deceleration time depends only on the
current slide speed.

A clean release from `slideSpeed = 1.2` at `slideDeceleration = 0.04` takes
30 ticks (1.5 seconds) to coast to a stop.

If you set this to `0`, the slide never decays, which can be surprising on
shallow ropes where the player keeps coasting after letting go.

## `[features]`

These are boolean toggles. Setting one to `false` only blocks **new**
embarkations on that mode; a climb already in progress when the value changes
finishes normally and the player dismounts cleanly.

### `allowVerticalRopeClimbing`

- **Type:** boolean
- **Default:** `true`

Master switch for the hanging-rope climb mode. When `false`, right-clicking a
rope strand with an empty hand does nothing (you'll see no embark sound and
the rope behaves as a passive prop).

Plunger ropes and the wrench-driven zipline are unaffected by this toggle.

### `allowPlungerClimbing`

- **Type:** boolean
- **Default:** `true`

Master switch for the plunger-rope **climb** mode (empty hand, right-click a
rope held between two `LaunchedPlungerEntity` projectiles). When `false`,
plunger ropes are not grabbable for empty-hand climbing.

Disabling this does **not** disable plunger-rope ziplining; that is gated by
`allowPlungerZipline` independently.

### `allowPlungerZipline`

- **Type:** boolean
- **Default:** `true`

Toggle for ziplining a plunger rope by holding any item tagged
`CHAIN_RIDEABLE` (Create's wrench by default) and right-clicking the rope.

When `false`, plunger ropes cannot be ridden as ziplines; the wrench-driven
zipline on hanging rope strands (Simulated's own built-in system) is
**unaffected** because that is implemented entirely by Simulated and is not
gated by this mod.

### `allowBlockMantle`

- **Type:** boolean
- **Default:** `true`
- **Applies to:** hanging rope strands only. Plunger ropes always use the
  plain jump-off impulse regardless of this flag.

Controls the behavior of pressing **Jump** while at the top end of a hanging
rope:

- `true` (default): If a block sits directly above the rope's top end (so the
  player would otherwise smack into a ceiling), pressing jump teleports the
  player onto that block. This makes climbing onto an airship deck via a
  rope feel like climbing a ladder.
- `false`: Pressing jump always applies `jumpOffVelocity` upward, regardless
  of geometry. Useful if you find the mantle behavior surprising or if your
  setup involves climbing ropes near low ceilings you don't want to mantle
  onto.

## Tuning recipes

### "Climbing should feel like a ladder"

The mod ships pretty close to vanilla ladder feel. If you want it even more
ladder-like:

```toml
[climbing]
	climbSpeed = 0.15
	descendSpeed = 0.15
	jumpOffVelocity = 0.42
	maxClimbAngleFromVertical = 31.79

[sliding]
	slideSpeed = 0.5
	slideAcceleration = 0.02
	slideDeceleration = 0.05
```

### "I want fast rappelling"

Crank slide speed and acceleration; keep climb up at the default so going
back up still feels like work:

```toml
[sliding]
	slideSpeed = 2.5
	slideAcceleration = 0.15
	slideDeceleration = 0.03
```

### "Horizontal ropes for traversal"

Open the angle gate fully so any rope is grabbable. You can also raise
`climbSpeed` a touch because flat ropes feel slower (no gravity boost on
descend):

```toml
[climbing]
	climbSpeed = 0.24
	descendSpeed = 0.24
	maxClimbAngleFromVertical = 90.0
```

Note that sliding still scales with verticality, so this is a comfort tweak
for traversal rather than a speed tweak.

### "Disable empty-hand climbing entirely (zipline-only server)"

Useful if you want to keep Simulated's wrench-zipline behavior but turn off
this mod's additions:

```toml
[features]
	allowVerticalRopeClimbing = false
	allowPlungerClimbing = false
	allowPlungerZipline = false
```

With all three off, the mod is effectively dormant: only Simulated's
built-in wrench zipline on hanging rope strands remains active.

## Troubleshooting

- **"I edited the file but nothing changed."** Server configs are loaded
  when the world is loaded. After editing, fully exit to the title screen
  (singleplayer) or restart the server (dedicated). For a dedicated server,
  some hot-reload commands exist, but a clean restart is the safest path.
- **"My setting was reset to the default."** NeoForge silently rewrites
  values that are out of the documented range or are the wrong type. Check
  the server log for `Configuration ... is not valid` messages around
  startup.
- **"The config file isn't there yet."** It is generated on first world
  load, not when the mod is installed. Load the world once, exit, and the
  file will be present.
- **"A player on my server can climb faster than my config allows."** The
  server is authoritative; if you see this, confirm the player is actually
  reading the same world's `serverconfig/climbable_ropes-server.toml`. The
  per-client `config/` directory is **not** used by this mod.

## Adding new config options

If you are contributing to the mod and want to add a new option, add it to
[`src/main/java/dev/matejhozlar/climbableropes/ClimbableRopesConfig.java`](src/main/java/dev/matejhozlar/climbableropes/ClimbableRopesConfig.java)
inside the appropriate `push()` / `pop()` group. Keep defaults that preserve
existing behavior, document them here and in the README, and ensure new
toggles short-circuit cleanly inside the relevant controller
(`ClimbController`, `PlungerClimbController`, `PlungerZiplineController`).
See [CONTRIBUTING.md](CONTRIBUTING.md) for the full contribution flow.
