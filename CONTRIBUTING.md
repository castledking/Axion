# Contributing to Axion

Axion is a multi-version Minecraft project with a Fabric client mod, a Paper
plugin, and a shared operation protocol. Changes should remain compatible with
every release range they affect.

## Prerequisites

- JDK 25 for the complete build matrix
- Git
- A Kotlin-capable IDE (IntelliJ IDEA is recommended)

The Gradle wrapper and build scripts handle the remaining build tooling.

## Build and Test

Build every supported Fabric and Paper range with:

```bash
./build-axion.sh all
```

Build one range while iterating:

```bash
./build-axion.sh 1.21-1.21.1
./build-axion.sh 1.21.2-1.21.3
./build-axion.sh 1.21.4
./build-axion.sh 1.21.5
./build-axion.sh legacy       # 1.21.6 - 1.21.8
./build-axion.sh modern       # 1.21.9 - 1.21.11
./build-axion.sh 26.1         # 26.1 - 26.1.2
```

Run the root and Paper unit tests with:

```bash
./gradlew :test :paper-plugin:test
```

The GitHub build workflow runs `./build-axion.sh all` for pushes to the primary
branches and for pull requests.

## Run a Development Client

Use `run-axion.sh` to launch an isolated development instance:

```bash
./run-axion.sh 1.21
./run-axion.sh 1.21.3
./run-axion.sh 1.21.11
./run-axion.sh 26.1
```

Start a matching Paper server and connect automatically:

```bash
WITH_PAPER=true QUICKPLAY=true ./run-axion.sh 1.21.11
```

Minecraft `1.21.11` also supports the dedicated Fabric server path:

```bash
WITH_FABRIC=true QUICKPLAY=true ./run-axion.sh 1.21.11
```

Development servers use isolated run directories and offline mode. Do not reuse
those settings for a public server.

## Project Layout

- `src/client/kotlin/`: client tools, input, previews, UI, local operations, and
  singleplayer behavior
- `src/main/kotlin/`: shared models, operations, history types, and compatibility
  interfaces
- `protocol/`: client/server transport messages and codecs
- `paper-plugin/`: authoritative Paper implementation
- `fabric-server/`: dedicated Fabric server implementation
- `src/compat-*/`: bytecode-level Minecraft version adapters

The current compatibility source sets are:

- `compat-1_21_0_1`: Minecraft `1.21 - 1.21.1`
- `compat-1_21_4`: Minecraft `1.21.2 - 1.21.4`
- `compat-1_21_5`: Minecraft `1.21.5`
- `compat-1_21_6_8`: Minecraft `1.21.6 - 1.21.8`
- `compat-1_21_9_10`: Minecraft `1.21.9 - 1.21.10`
- `compat-1_21_11`: Minecraft `1.21.11`
- `compat-26_1`: Minecraft `26.1 - 26.1.2`

## Compatibility Rules

1. Keep version-independent behavior in `src/client` or `src/main`.
2. Put only genuine Minecraft API or bytecode differences in `src/compat-*`.
3. Prefer extending the compatibility interface over copying an entire feature
   into multiple version folders.
4. When a change touches rendering, input, networking, world writes, or mixins,
   test the oldest and newest affected ranges at minimum.
5. Keep Fabric and Paper operation behavior equivalent where both paths support
   the operation.

See [API_COMPATIBILITY.md](API_COMPATIBILITY.md) for API-specific notes.

## Pull Requests

Before opening a pull request:

1. Keep the change focused and document the user-visible result.
2. Add a regression test when the behavior can be isolated.
3. Run the affected range builds and tests.
4. Include the Minecraft versions and server type you tested.
5. Attach logs or screenshots for rendering and multiplayer issues when useful.

Report bugs and feature requests through
[GitHub Issues](https://github.com/castledking/Axion/issues).

By contributing, you agree that your work is licensed under the
[GPL-3.0 license](LICENSE).
