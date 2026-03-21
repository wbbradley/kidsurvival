# Kid Survival

A Fabric server-side mod for kid-friendly Minecraft servers. Toggle invulnerability for young players with `/kid`, or start a multiplayer Hunter Tag game with `/hunter`.

## Features

### Kid Mode

- `/kid` toggle — grants invulnerability, hunger immunity, and continuous health restoration
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
- Game state and scores persist across server restarts

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.0+
- Fabric API (matching MC version)
- Java 21
- Server-side only — no client mod needed

## Installation (Prism Launcher)

1. Create or select a Minecraft 1.21.11 instance in Prism Launcher
2. Click **Edit Instance** > **Version** tab > **Install Fabric** — select loader 0.18.0+
3. Download Fabric API from [Modrinth](https://modrinth.com/mod/fabric-api) (match Minecraft version 1.21.11)
4. Build or obtain `kidsurvival-1.0.0.jar` (see [Building from Source](#building-from-source))
5. Click **Edit Instance** > **Mods** tab > **Add** — add both the Fabric API and Kid Survival JARs
6. For dedicated servers: place both JARs in the server's `mods/` folder

## Usage

- `/kid` — toggle invulnerability (any player can run it)
- `/hunter` — start a Hunter Tag round (needs 2+ online players)

## Building from Source

```
git clone <repo-url>
cd kidsurvival
./gradlew build
```

Output JAR: `build/libs/kidsurvival-1.0.0.jar`

## Troubleshooting

- **/kid command not found:** Ensure the mod JAR and Fabric API are both in the `mods/` folder and the server has restarted.
- **Kid mode didn't persist after restart:** Check that the server shut down cleanly (not force-killed). State is saved during normal shutdown.
- **Version mismatch errors:** Ensure Fabric API version matches your Minecraft version (1.21.11).
- **/hunter says need 2+ players:** Hunter Tag requires at least 2 online players to start a round.
- **Hunter Tag game won't start:** The command must be run by a player, not the server console.

## License

MIT
