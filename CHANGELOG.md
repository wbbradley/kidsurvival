# Changelog

All notable changes to Kid Survival are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.2.2] - 2026-07-04

### Changed

- Documented the `/hunger` (no-hunger toggle) and `/bench` (opt out of Hunter Tag) commands in the
  README.

### Fixed

- Corrected the Kid Mode description, which previously implied `/kid` also suppressed hunger; hunger
  is managed separately by `/hunger`.

## [2.2.1] - 2026-07-04

### Added

- This CHANGELOG, documenting the full release history, shipped as part of the release artifact.

## [2.2.0] - 2026-07-04

### Changed

- Bumped the release workflow's GitHub Actions off the Node 20 runtime ahead of GitHub's
  deprecation deadlines: `actions/checkout@v4 → @v7`, `actions/setup-java@v4 → @v5`, and
  `gradle/actions/setup-gradle@v4 → @v6` (all run on Node 24).
- Added a `workflow_dispatch:` trigger and guarded the release-publishing step to `refs/tags/*`
  so the workflow can be exercised manually from `main` without cutting a release.
- Updated README compatibility info to Minecraft 26.2 / Fabric Loader 0.19.3+ / Java 25.

## [2.1.0] - 2026-07-04

### Changed

- Upgraded to Minecraft 26.2 (Fabric Loader 0.19.3, Fabric API 0.154.0+26.2, Loom 1.17.13,
  Gradle 9.5.1). Adapted `PlayerTeam.setColor` to the new `Optional<TeamColor>` signature.

## [2.0.0] - 2026-04-27

### Changed

- Upgraded to Minecraft 26.1.2 with Mojang official mappings (Yarn dropped), Java 25,
  Gradle 9.4.1, and Loom 1.15.5. Rewrote the codebase against Mojang names.
- Bumped the release workflow to Java 25.

### Removed

- Deleted unused mixin scaffolding (`kidsurvival.mixins.json` and the empty `mixin/` package).

## [1.5.0] - 2026-03-22

### Added

- Red "CAPTURED!" title flash shown to a runner when they are tagged during Hunter Tag.
- Sneaking (holding shift) hides a player's name tag during Hunter Tag via a brief invisibility
  effect; the glowing outline remains for tracking.

## [1.4.0] - 2026-03-22

### Added

- `/bench` command to opt players out of Hunter Tag entirely (excluded from rounds, loadout, and
  mid-game auto-join).

### Changed

- Increased the sidebar scoreboard update frequency from every 5 seconds to every 1 second.
- Removed the elytra and fireworks from the Hunter Tag loadout.

## [1.3.0] - 2026-03-22

### Added

- Hunter Tag loadout: on round start, participants are equipped with an elytra, firework rockets,
  and an oak boat.

## [1.2.0] - 2026-03-22

### Added

- `/hunger` command that independently keeps food and saturation maxed, separated from `/kid`
  (which now only affects health).
- Sidebar scoreboard showing each player's accumulated runner time during Hunter Tag.

## [1.1.0] - 2026-03-22

### Added

- Hunter Tag multiplayer game mode (`/hunter`): frozen-hunter start, melee tagging, role-based
  teams/glow, action-bar status, cumulative scoring, and persistence.
- Kid mode action bar indicator ("Kid Mode Active").
- Death prevention for kid mode (cancels death from `/kill`, void, etc. and restores health).
- Mod icon for Modrinth publishing.
- GitHub Actions release workflow that builds the JAR and attaches it to a GitHub Release on tag
  push.

### Fixed

- Load the mod on both client and dedicated server environments.

## [1.0.0] - 2026-03-21

### Added

- Initial Kid Survival Fabric mod with the `/kid` toggle (invulnerability, hunger immunity,
  continuous health restoration).
- Kid-mode state persistence across server restarts via `kidsurvival.json`.
- README with installation, usage, and troubleshooting.

[2.2.2]: https://github.com/wbbradley/kidsurvival/releases/tag/v2.2.2
[2.2.1]: https://github.com/wbbradley/kidsurvival/releases/tag/v2.2.1
[2.2.0]: https://github.com/wbbradley/kidsurvival/releases/tag/v2.2.0
[2.1.0]: https://github.com/wbbradley/kidsurvival/releases/tag/v2.1.0
[2.0.0]: https://github.com/wbbradley/kidsurvival/releases/tag/v2.0.0
[1.5.0]: https://github.com/wbbradley/kidsurvival/releases/tag/v1.5.0
[1.4.0]: https://github.com/wbbradley/kidsurvival/releases/tag/v1.4.0
[1.3.0]: https://github.com/wbbradley/kidsurvival/releases/tag/v1.3.0
[1.2.0]: https://github.com/wbbradley/kidsurvival/releases/tag/v1.2.0
[1.1.0]: https://github.com/wbbradley/kidsurvival/releases/tag/v1.1.0
