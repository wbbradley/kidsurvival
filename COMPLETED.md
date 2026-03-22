## Completed

### 8. Mod Icon for Modrinth

Generated a 512x512 PNG icon with "KS" initials on a green (#4CAF50) background with darker green border. Added `"icon": "assets/kidsurvival/icon.png"` to `fabric.mod.json`. Icon was generated with a pure-Python script (no dependencies) and not committed.

### 7. Bump version to 1.1.0

Updated `gradle.properties` and README JAR references from 1.0.0 to 1.1.0. Build produces `kidsurvival-1.1.0.jar`.

### 6. Update README and Modrinth Materials

Rewrote README to document both Kid Mode and Hunter Tag features, with updated requirements, usage, and troubleshooting sections. Changed `fabric.mod.json` environment from `"*"` to `"server"` since the mod is entirely server-side. Authors and contact left empty per plan. Icon creation deferred (asset, not code).

### 5. Hunter Tag Game Mode

Added `/hunter` command that starts a tag-style multiplayer game mode. One player is the hunter (frozen for 30s), all others are runners. Hunter tags runners by melee attack (no damage dealt, compatible with kid mode). Tagged runners become hunters. When the last runner is caught, scores are reported and a new round auto-starts.

Implementation details:
- `HunterTagGame.java` — self-contained game state class with round lifecycle, tag detection, freeze enforcement, scoring, and persistence
- `KidSurvivalMod.java` — wired up `/hunter` command, `AttackEntityCallback` for tag detection, `ServerPlayConnectionEvents` for join/disconnect handling, tick integration, and JSON persistence
- Visual indicators: scoreboard teams with colored `[Hunter]`/`[Runner]` prefixes, glowing effect on hunters, action bar showing role and freeze countdown
- Edge cases handled: hunter disconnect (game pauses), runner disconnect, new player joins mid-game (added as runner), server restart (state + scores restored, freeze released)
- All done server-side only — no client entrypoint needed

### 4. README.md

Created project README covering features, requirements (MC 1.21.1, Fabric Loader 0.16.0+, Fabric API, Java 21), Prism Launcher installation steps, usage instructions for the `/kid` command, building from source, and troubleshooting common issues.

### 3. Permissions

Restricted `/kid` command to operators (permission level 2+).

### 2. Persistence

Kid-mode state persists across server restarts via `kidsurvival.json` in the world save directory.

### 1. Create "Kid Survival" Fabric Mod

Built the full Fabric mod with `/kid` toggle command. Actual versions used differ from initial plan due to compatibility (MC 1.21.1, Fabric Loader 0.16.14, Fabric API 0.115.0+1.21.1, Loom 1.9-SNAPSHOT, Gradle 8.14). Installed Homebrew openjdk@21 and patched `gradlew` to auto-detect it since Gradle doesn't support Java 25. Build produces `kidsurvival-1.0.0.jar` successfully.
