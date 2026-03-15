# Docker URLs

PyCharm plugin that adds a `Docker URLs` tool window.

Features:

- Discovers Docker Compose files under the current project path.
- Lets the user choose which Compose project to inspect and remembers that selection per IDE project.
- Polls `docker compose ps --all --format json` every 5 seconds.
- Shows service name, container status, and localhost URLs sorted by container status.
- Supports manual reload and Compose project reselection.

## Build

This project uses the IntelliJ Gradle plugin and targets PyCharm `2024.3.5`.

Typical local commands:

```bash
./gradlew runIde
./gradlew buildPlugin
```
