# Contributing to Axion

Axion is an open-source Minecraft building toolkit built as a Fabric client mod with a companion Paper plugin for server-backed multiplayer editing.

## Project Overview

Axion brings fast in-game building tools directly into Minecraft with hotbar-based editing, live previews, symmetry, long-range building modes, and multiplayer-safe execution through the Paper plugin.

### Architecture

The project consists of three main components:

- **Fabric Mod** (`src/client/`): Client-side tools, previews, input, config UI, and local singleplayer behavior
- **Paper Plugin** (`src/plugin/`): Authoritative multiplayer edit application, validation, history, undo/redo, and large-operation transport
- **Common** (`src/common/`): Shared data models, operations, and protocol definitions

### Technology Stack

- **Kotlin**: Primary language for all components
- **Fabric API**: Minecraft modding framework for the client mod
- **Paper API**: Server plugin API for the Paper plugin
- **Gradle**: Build system with Fabric Loom and Paperweight

## Development Setup

### Prerequisites

- JDK 21 or later (JDK 25+ for 26.1.x)
- IntelliJ IDEA or another Kotlin-compatible IDE
- Git

### Building

```bash
./gradlew build
```

Or use the build script to target a specific version range:

```bash
./build-axion.sh mc1_21_5    # 1.21.5
./build-axion.sh mc1_21_6    # 1.21.6
./build-axion.sh mc1_21_7    # 1.21.7
./build-axion.sh mc1_21_8    # 1.21.8
./build-axion.sh mc1_21_9    # 1.21.9
./build-axion.sh mc1_21_10   # 1.21.10
./build-axion.sh mc1_21_11   # 1.21.11
./build-axion.sh mc26_1_x    # 26.1.x
```

### Running Tests

```bash
./gradlew test
```

### Running Client (Development)

```bash
./run-axion.sh [version]
```

The `run-axion.sh` script launches the Fabric client for the specified version with all required dependencies (Fabric API, Fabric Language Kotlin, ModMenu, IAS):

```bash
./run-axion.sh 1.21.5        # Minecraft 1.21.5
./run-axion.sh 1.21.7        # Minecraft 1.21.7
./run-axion.sh 1.21.11       # Minecraft 1.21.11
./run-axion.sh 26.1          # Minecraft 26.1.2
```

**Note**: Versions marked "infrastructure only" have compatibility folders and build support but may require additional work to be fully functional.

### Testing with Servers

#### Paper Server

Start a Paper server alongside the client:

```bash
WITH_PAPER=true ./run-axion.sh 1.21.11
```

The script will:
1. Download and configure a Paper server for that version
2. Build and install the AxionPaper plugin
3. Accept the EULA
4. Start the Paper server in the background on its default port
5. Launch the Fabric client connected to the offline server

#### Fabric Server (1.21.11 only)

```bash
WITH_FABRIC=true ./run-axion.sh 1.21.11
```

This starts a standalone Fabric server instead of Paper, using the AxionFabricServer mod. The Fabric Installer is downloaded and run automatically on first use. Fabric API and Fabric Language Kotlin are downloaded as mod dependencies.

### Server Ports

| Version  | Paper/Fabric Port |
|----------|-------------------|
| 1.21.5   | 25567             |
| 1.21.7   | 25568             |
| 1.21.11  | 25569             |
| 26.1.x   | 25570             |

### Offline Mode & Operator Access

All servers are configured in offline mode for easier local testing. The **In-Game Account Switcher (IAS)** mod is automatically downloaded and installed by `run-axion.sh`. This lets you connect with any username without Mojang authentication.

When the `.axiondev` marker file is present in the server run directory, the Axion server plugin/mod automatically promotes any player that joins to operator level (op 4). This is enabled by default for local development servers started via `run-axion.sh`.

### Build And Run

```bash
WITH_PAPER=true ./run-axion.sh 1.21.11
```

This builds the matching jars first, then starts both the Paper server and Fabric client.

## API Compatibility

Detailed API differences across supported Minecraft versions are documented in [API_COMPATIBILITY.md](API_COMPATIBILITY.md). When adding features that touch version-specific code, consult this document and add entries for any new APIs introduced.

## Code Organization

### Client Module (`src/client/`)

- `tool/`: Hotbar tool implementations (Move, Clone, Stack, Smear, etc.)
- `selection/`: Selection logic and Magic Select
- `render/`: Rendering pipelines and preview visualization
- `mode/`: Builder modes (replace, infinite reach, no clip, etc.)
- `network/`: Client-side networking and operation dispatch
- `hotbar/`: Hotbar management and saved hotbar system
- `compat/`: Version compatibility layer for different Minecraft versions

### Plugin Module (`src/plugin/`)
- `operation/`: Server-side operation application
- `validation/`: Edit validation and policy checks
- `history/`: Per-player history and undo/redo
- `audit/`: Audit logging and metrics

### Common Module (`src/common/`)

- `model/`: Shared data models (BlockRegion, ClipboardBuffer, etc.)
- `operation/`: Operation definitions and serialization
- `protocol/`: Network protocol definitions

### Compatibility Layer

Version-specific compatibility code is organized in per-version folders under `src/compat-*/`:

- `src/compat-1_21_5/`: Minecraft 1.21.5 compatibility (legacy Camera signature, shader exclusions)
- `src/compat-1_21_6/`: Minecraft 1.21.6 (infrastructure only)
- `src/compat-1_21_7/`: Minecraft 1.21.7 compatibility (baseline for 1.21.6-1.21.8)
- `src/compat-1_21_8/`: Minecraft 1.21.8 compatibility (infrastructure only)
- `src/compat-1_21_9/`: Minecraft 1.21.9 compatibility (infrastructure only)
- `src/compat-1_21_10/`: Minecraft 1.21.10 compatibility (infrastructure only)
- `src/compat-1_21_11/`: Minecraft 1.21.11+ compatibility (baseline for 1.21.9-1.21.11)
- `src/compat-26_1/`: Minecraft 26.1.x compatibility (official namespace, extensive API bridges)

Each compatibility folder contains:
- `VersionCompatImpl.kt`: Version-specific implementations of the VersionCompat interface
- `kotlin/axion/mixin/client/`: Version-specific mixins

### Fully Tested Versions
- **1.21.5**: Fully working (shader exclusions applied for compatibility)
- **1.21.7**: Fully working
- **1.21.11**: Fully working
- **26.1.x**: Fully working (official namespace, extensive API bridges)

### Infrastructure Only Versions
- **1.21.6**: Compatibility folder exists, requires additional testing and fixes (specific rendering classes, BlockRenderView API changes)
- **1.21.8**: Compatibility folder exists, requires additional testing and fixes
- **1.21.9**: Compatibility folder exists, requires additional testing and fixes
- **1.21.10**: Compatibility folder exists, requires additional testing and fixes

The build system selects the appropriate compatibility folder based on the target version. For versions marked "infrastructure only", the build system supports them but they may need additional work to be fully functional.

## Coding Conventions

- Use Kotlin idioms and standard library functions
- Follow existing code style and naming conventions
- Add KDoc comments for public APIs
- Keep functions focused and reasonably sized
- Use meaningful variable and function names

## Version Compatibility

Axion supports multiple Minecraft versions through a compatibility layer:

- **1.21.5 - 1.21.11**: Primary support
- **26.1.x**: Latest Minecraft version support

When adding new features, ensure they work across supported versions. Use the compatibility layer (`src/compat-26_1/`) to handle version-specific differences.

### Compat Folder Discipline

The compat source sets exist to isolate **bytecode-level differences** between Minecraft versions, not to hold copies of version-agnostic logic. To prevent the duplication problem from coming back, follow these rules:

1. **Files in `src/main/` and `src/client/` must not reference Minecraft classes whose signatures differ between supported versions.** Per-version API divergence goes through the `VersionCompat` interface. If you find yourself wanting to copy the same file into multiple compat folders, that file belongs in `src/client/` with the divergent calls extracted into new `VersionCompat` methods.

2. **Never create a new compat folder preemptively.** A separate compat folder is only justified when the bytecode produced against that MC version genuinely differs from the next — typically because of a yarn rename, a class that doesn't exist, or a mixin target signature change. If the source is byte-identical to an existing compat folder, it shares that folder.

3. **Current layout:**
   - `src/compat-1_21_5/` — 1.21.5 only (CPU preview path, shader exclusions)
   - `src/compat-1_21_6_8/` — 1.21.6, 1.21.7, 1.21.8 (shared, byte-identical)
   - `src/compat-1_21_9_10/` — 1.21.9, 1.21.10 (shared, byte-identical)
   - `src/compat-1_21_11/` — 1.21.11 only (renderer + mixin signatures diverge)
   - `src/compat-26_1/` — 26.1.x (official namespace, extensive API bridges)

4. **When fixing a bug,** prefer adding a `VersionCompat` method over duplicating a fix across compat folders. If a fix to one compat folder requires a parallel shim in another, that's a signal the affected code should move into `src/client/` behind `VersionCompat`.

5. **When a future MC release diverges** inside one of the merged folders, branch the folder back out at that point — not earlier.

## Submitting Changes

1. Fork the repository
2. Create a feature branch
3. Make your changes with clear commit messages
4. Test your changes on supported Minecraft versions
5. Submit a pull request with a description of your changes

## Reporting Issues

When reporting bugs or requesting features:

- Use the GitHub issue tracker
- Provide clear reproduction steps for bugs
- Specify the Minecraft version you're using
- Include relevant logs or screenshots if applicable

## License

By contributing to Axion, you agree that your contributions will be licensed under the GPL-3.0 license.
