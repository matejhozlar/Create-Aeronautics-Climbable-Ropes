# Contributing

Thanks for your interest in contributing to Climbable Ropes for Create Aeronautics. This document covers what you need to build the mod locally, the conventions used in the repository, and how to get a change reviewed and merged.

## Reporting issues

Before opening a bug report:

1. Confirm the bug reproduces with **only** Climbable Ropes plus the required dependencies (Create: Aeronautics, which jars-in-jars Simulated, Aeronautics, and Offroad). Crashes that only happen with a large modpack are usually not actionable until they're isolated.
2. Make sure you're on the latest released version. Compare your symptoms against [`CHANGELOG.md`](CHANGELOG.md) in case the issue is already fixed.
3. Search existing issues first.

Open new issues:

- **Bug report**: include Minecraft version, NeoForge version, mod version, and a `latest.log` or `crash-report` file. Without those, a bug report is hard to act on.
- **Feature request**: describe the player-facing behavior you want, not the implementation. If it touches the climb feel (speeds, angles, slide curves), say what feels wrong about the current behavior.

Issues drafted from user reports sometimes contain wrong diagnoses or stale references. If you're proposing a fix, read the referenced code first and verify the premise holds.

## Development setup

### Prerequisites

- JDK 21 (Temurin recommended; matches CI)
- Git
- An IDE that understands Gradle (IntelliJ IDEA is what the project is configured for; `.idea/` settings include `downloadSources = true`)

### The Simulated dependency

Climbable Ropes is an addon for Simulated (the physics module bundled inside Create: Aeronautics) and is built against Simulated's compiled output. Simulated has no public maven; it ships only jar-in-jar'd inside Create: Aeronautics, so the build extracts Simulated's compiled classes straight from that bundle. No separate Simulated checkout is required.

Create: Aeronautics is downloaded from Modrinth, pinned by `create_aeronautics_version` in `gradle.properties`. The regular build extracts Simulated automatically; to do it explicitly:

```sh
gradlew extractSimulated
```

This drops the bundled Simulated jar at `build/extracted-simulated/simulated.jar`.

### Building

```sh
./gradlew build
```

The output jar lands in `build/libs/climbable_ropes-<version>.jar`.

### Running the dev client/server

```sh
./gradlew runClient
./gradlew runServer
```

Both runs use the configuration in `run/`. The server config for climbing lives at `run/saves/<world>/serverconfig/climbable_ropes-server.toml` once a world has been loaded; see the [README](README.md#configuration) for the keys.

## Code style

- **Java 21**, with `JavaLanguageVersion.of(21)` enforced by the build.
- **Comments**: default to writing none. Only add a comment when the *why* is non-obvious (a hidden constraint, a workaround for a specific upstream bug, a subtle invariant). Don't restate what well-named identifiers already say, and don't leave historical notes ("previously did X, now does Y") or PR back-references in source.
- **Match the existing structure.** The mod has a small surface area: each climb mode lives in its own controller (`ClimbController`, `PlungerClimbController`, `PlungerZiplineController`) and reads from `ClimbableRopesConfig`. New climb behaviors should slot in alongside, not modify Simulated via mixin. The README's "How it works" section explains the boundary deliberately: Simulated's existing zipline path is left untouched.
- When you reference Simulated APIs (e.g. `ZiplineClientManager.raycastRope`, `RopeRidingPacket`, `LaunchedPlungerEntity`), check the version the extracted Simulated jar exposes; the surface has changed across releases.

## Branching

- Branch off `1.21.1` (the active development branch for Minecraft 1.21.1).
- Branch naming: `<type>/<short-slug>`, where `<type>` matches the commit type (`feat/...`, `fix/...`, `chore/...`, `refactor/...`, `docs/...`).

## Commits

Format: `type: description`

- **Types**: `feat`, `fix`, `chore`, `refactor`, `docs`, `style`, `test`, `perf`.
- **Description**: lowercase, imperative mood, no trailing period.

Examples:

```
fix: detect perfectly vertical ropes for climbing
feat: add allowBlockMantle config flag
chore: bump create_aeronautics_version to 1.3.0
refactor: extract closest-approach test from ClimbController
```

## Pull requests

A good PR description covers:

- **What changed** at the player-visible level (one or two sentences).
- **Why** the change is needed: the bug it fixes, the gap in behavior, or the issue it closes.
- **How to test it in-game.** For climb-feel changes this is essential; reviewers can't tell from a diff alone whether a slide curve "feels right." Include the rope orientations and inputs you exercised (vertical, diagonal, near-horizontal; W, S, sprint+S, jump-off, mantle).
- **Config or save-format impact**, if any. New config keys should have a default that preserves existing behavior.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE) that covers this project.
