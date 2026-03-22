## Completed

### 16. Capture Visual Effect

When a runner is tagged during Hunter Tag, they see a large dark red "CAPTURED!" title that appears instantly, stays for 1 second, and fades out over 0.5 seconds. Only the tagged player sees it. Uses `TitleS2CPacket` and `TitleFadeS2CPacket`.

### 15. Sneak to Hide Name Tag

During Hunter Tag, sneaking (holding shift) applies a short Invisibility effect that hides the player's name tag and model. The Glowing outline remains visible through walls for tracking. Effect auto-expires within a few ticks when the player stops sneaking.

### 14. Bench Mode & Scoreboard Update Frequency

Added `/bench` toggle command that opts players out of Hunter Tag entirely. Benched players are excluded from rounds (not added as hunters/runners), not teleported, not given loadout, not auto-added on mid-game join, and not counted for the "need 2 players" check. Benching mid-game removes the player from the active game. State persists across restarts. Also increased sidebar scoreboard update from every 5 seconds to every 1 second.

### 13. Hunter Tag Loadout

On round start, all participants' inventories are cleared and replaced with a standard loadout: Elytra (equipped in chest slot), 64 firework rockets with flight duration 3 (hotbar slot 0), and an oak boat (hotbar slot 1).

### 12. Return of Hunger

Separated hunger management from `/kid` mode. `/kid` now only affects health (invulnerability, damage cancellation, death prevention). Added new `/hunger` toggle command that independently keeps food and saturation at max. Both modes persist across restarts and the action bar shows combined status ("Kid Mode + No Hunger", "Kid Mode Active", or "No Hunger").

### 11. Runner Time Scoreboard (Sidebar)

Added a sidebar scoreboard during Hunter Tag showing each player's accumulated runner time in seconds. Uses a Minecraft scoreboard objective (`dummy` criteria) in the `SIDEBAR` display slot titled "Hunter Tag". Scores update every 5 seconds (100 ticks). The scoreboard is created on round start, updated on player join, and removed when the game ends.

### 10. Death Prevention for Kid Mode

Registered `ServerLivingEntityEvents.ALLOW_DEATH` as a safety net behind the existing `ALLOW_DAMAGE` handler. When a kid-mode player would die (e.g., from `/kill` or void damage), the death event is canceled and health is restored to max. This catches edge cases where damage bypasses the `ALLOW_DAMAGE` handler.

### 9. Kid Mode Visual Indicator

Added action bar message "Kid Mode Active" (green) shown every second to players with kid mode enabled. Added `isPlayerInGame` method to `HunterTagGame` so the indicator is suppressed during active Hunter Tag games (which show their own role-based action bar messages).

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
