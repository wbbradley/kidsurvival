# Kid Survival

A Fabric mod that lets players toggle invulnerability on kid-friendly Minecraft servers.

## Features

- `/kid` toggle command (available to all players, no operator permissions needed)
- Full damage immunity while active
- Hunger and saturation maintained at max (no food depletion)
- Health auto-restored every tick
- State persists across server restarts (saved to `kidsurvival.json` in world folder)

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.0+
- Fabric API — a companion mod that provides common hooks and utilities used by most Fabric mods; required alongside the Fabric loader
- Java 21

## Installation (Prism Launcher)

1. Create or select a Minecraft 1.21.11 instance in Prism Launcher
2. Click **Edit Instance** > **Version** tab > **Install Fabric** — select loader 0.18.0+
3. Download Fabric API from [Modrinth](https://modrinth.com/mod/fabric-api) (match Minecraft version 1.21.11)
4. Build or obtain `kidsurvival-1.0.0.jar` (see [Building from Source](#building-from-source))
5. Click **Edit Instance** > **Mods** tab > **Add** — add both the Fabric API and Kid Survival JARs
6. For dedicated servers: place both JARs in the server's `mods/` folder

## Usage

- Toggle kid mode: `/kid` (any player can run it to toggle their own kid mode)
- Feedback messages: "Kid mode enabled" / "Kid mode disabled"

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

## License

MIT
