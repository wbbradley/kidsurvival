## Completed

### 4. README.md

Created project README covering features, requirements (MC 1.21.1, Fabric Loader 0.16.0+, Fabric API, Java 21), Prism Launcher installation steps, usage instructions for the `/kid` command, building from source, and troubleshooting common issues.

### 3. Permissions

Restricted `/kid` command to operators (permission level 2+).

### 2. Persistence

Kid-mode state persists across server restarts via `kidsurvival.json` in the world save directory.

### 1. Create "Kid Survival" Fabric Mod

Built the full Fabric mod with `/kid` toggle command. Actual versions used differ from initial plan due to compatibility (MC 1.21.1, Fabric Loader 0.16.14, Fabric API 0.115.0+1.21.1, Loom 1.9-SNAPSHOT, Gradle 8.14). Installed Homebrew openjdk@21 and patched `gradlew` to auto-detect it since Gradle doesn't support Java 25. Build produces `kidsurvival-1.0.0.jar` successfully.
