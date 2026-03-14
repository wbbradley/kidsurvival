# Kid Survival

A server-side Fabric mod that lets operators toggle invulnerability for players on kid-friendly Minecraft servers.

## Features

- `/kid` toggle command (operator-only, permission level 2+)
- Full damage immunity while active
- Hunger and saturation maintained at max (no food depletion)
- Health auto-restored every tick
- State persists across server restarts (saved to `kidsurvival.json` in world folder)

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16.0+
- Fabric API — a companion mod that provides common hooks and utilities used by most Fabric mods; required alongside the Fabric loader
- Java 21

## Installation (Prism Launcher)

1. Create or select a Minecraft 1.21.1 instance in Prism Launcher
2. Click **Edit Instance** > **Version** tab > **Install Fabric** — select loader 0.16.0+
3. Download Fabric API from [Modrinth](https://modrinth.com/mod/fabric-api) (match Minecraft version 1.21.1)
4. Build or obtain `kidsurvival-1.0.0.jar` (see [Building from Source](#building-from-source))
5. Click **Edit Instance** > **Mods** tab > **Add** — add both the Fabric API and Kid Survival JARs
6. For dedicated servers: place both JARs in the server's `mods/` folder

## Usage

- Grant operator status: `/op <player>` from console or as an existing op
- Toggle kid mode: `/kid` (the operator runs the command themselves — it toggles their own kid mode)
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
- **/kid says "Unknown command":** You need operator permissions (level 2+). Run `/op <yourname>` from the server console.
- **Kid mode didn't persist after restart:** Check that the server shut down cleanly (not force-killed). State is saved during normal shutdown.
- **Version mismatch errors:** Ensure Fabric API version matches your Minecraft version (1.21.1).

## License

MIT
