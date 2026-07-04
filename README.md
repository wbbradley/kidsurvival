# Kid Survival

A Fabric server-side mod for kid-friendly Minecraft servers. Toggle invulnerability for young players with `/kid`, or start a multiplayer Hunter Tag game with `/hunter`.

## Features

### Kid Mode

- `/kid` toggle — grants invulnerability and continuous health restoration
- Available to all players, no operator permissions needed
- State persists across server restarts

### Hunger Mode

- `/hunger` toggle — keeps food and saturation maxed so the player never gets hungry
- Independent of Kid Mode; enable either on its own or both together
- Combined status is shown in the action bar ("Kid Mode + No Hunger", "Kid Mode Active", or "No Hunger")
- Available to all players, no operator permissions needed
- State persists across server restarts

### Hunter Tag

- `/hunter` starts a round — the invoking player becomes the hunter, everyone else becomes a runner
- Hunter is frozen for 30 seconds, then chases runners
- Tag runners by melee attack (no damage dealt, compatible with kid mode)
- Tagged runners become hunters
- When the last runner is tagged, scores are reported and a new round auto-starts
- Colored team names (`[Hunter]`/`[Runner]`) and glowing effect on hunters
- Role shown in action bar with freeze countdown
- Cumulative runner-time scoring across rounds
- Handles disconnects (pause/resume) and mid-game joins
- `/bench` opts a player out entirely — benched players are skipped when rounds start and never auto-join mid-game
- Game state and scores persist across server restarts

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API (matching MC version)
- Java 25
- Server-side only — no client mod needed

## Installation (Prism Launcher)

1. Create or select a Minecraft 26.2 instance in Prism Launcher
2. Click **Edit Instance** > **Version** tab > **Install Fabric** — select loader 0.19.3+
3. Download Fabric API from [Modrinth](https://modrinth.com/mod/fabric-api) (match Minecraft version 26.2)
4. Build or obtain `kidsurvival-2.2.2.jar` (see [Building from Source](#building-from-source))
5. Click **Edit Instance** > **Mods** tab > **Add** — add both the Fabric API and Kid Survival JARs
6. For dedicated servers: place both JARs in the server's `mods/` folder

## Usage

- `/kid` — toggle invulnerability (any player can run it)
- `/hunger` — toggle no-hunger mode, keeping food and saturation full (any player can run it)
- `/hunter` — start a Hunter Tag round (needs 2+ non-benched online players)
- `/bench` — toggle opting out of Hunter Tag (any player can run it)

## Building from Source

```
git clone <repo-url>
cd kidsurvival
./gradlew build
```

Output JAR: `build/libs/kidsurvival-2.2.2.jar`

## Troubleshooting

- **/kid command not found:** Ensure the mod JAR and Fabric API are both in the `mods/` folder and the server has restarted.
- **Kid mode didn't persist after restart:** Check that the server shut down cleanly (not force-killed). State is saved during normal shutdown.
- **Version mismatch errors:** Ensure Fabric API version matches your Minecraft version (26.2).
- **/hunter says need 2+ players:** Hunter Tag requires at least 2 online players to start a round.
- **Hunter Tag game won't start:** The command must be run by a player, not the server console.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the release history.

## License

MIT
