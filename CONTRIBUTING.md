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

The `run-axion.sh` s6        # Minecraft 1.21.6 (infrastructure only)
./run-axion.sh 1.21.cript launches the Fabric client for the specified version with all required dependencies (Fabric API, Fabric Language Kotlin, ModMenu, IAS):
 1.21.8        # Minecraft.8 (infrastructure only)
/run-axion.sh .2.9 ft 1.21.9 (inrastrucureonly)
./run-axion.sh 1.10       # Minecraft 2.0 (infrastructure only)
```bash1.1.11       # Minecraft 1.21.11
./run-axion.sh 2
./r

**Note**: Versions marked "infrastructure only" have compatibility folders and build support but may require additional work to be fully functional.un-axion.sh 1.21.5        # Minecraft 1.21.5
./run-axion.sh 1.21.7        # Minecraft 1.21.7
./run-axion.sh 1.21.11       # Minecraft 1.21.11
./run-axion.sh 26.1          # Minecraft 26.1.2
```

### Testing with Servers

#### Paper Server

Start a Paper server alongside the client:

```bash
WITH_PAPER=true ./run-axion.sh 1.21.11
```

The script will:
1. Download and configure a Paper server for that version
2. Build and install the AxionPaper plugin
3. Accept the EULA and pre-authorize your user as operator
4. Start the Paper server in the background on its default port
5. Launch the Fabric client connected to the offline server

#### Fabric Server (1.21.11 only)

```bash
WITH_FA6   | 25568             |
| 1.21.BRIC=true 9             |
| 1.21..   | 25570   /run-axion|
| 1.21.9.sh  25571             |1.21.11
```0  | 25572             |
| 1.21.173
4
This starts a standalone Fabric server instead of Paper, using the AxionFabricServer mod. The Fabric Installer is downloaded and run automatically on first use. Fabric API and Fabric Language Kotlin are downloaded as mod dependencies.

### Server Ports

| Version  | Paper/Fabric Port |
|----------|-------------------|
| 1.21.5   | 25567             |
| 1.21.7   | 25568             |
| 1.21.11  | 25569             |
| 26.1.x   | 25570             |

### Offline Mode & Operator Access

All servers are configured in offline mode for easier local testing. The **In-Game Account Switcher (IAS)** mod is automatically downloaded and installed by `run-axion.sh`. This lets you connect with any username without Mojang authentication — useful when joining an offline-mode server with the `ggpots` account.

To add your own UUID as operator, edit the `ops.json` block in `run-axion.sh`. Look for `start_paper_server` and `start_fabric_server` — both write an `ops.json` with the default user `ggpots` (UUID `710f96df-8b04-4c91-8828-b2b5afc45cd3`). Replace with your own UUID and username.

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


Version-specificcompatibility code is organized in per-version folders under *:
### Plugin Module (`src/plugin/`)
- `src/compat-1_21_5/`: Mincaft 1.21.5 compatibility (legacy Camera gnature, shader exclusis)
 `rc/comat-1_21_6/`: Minrat1.21.6 (infrastruturnly)
- `sc/compat-1_21_7/`:1.1.7 compatibility (baseline for 1.21./128)
- `src/compat-1_21_8/`: Minecraft 121.8compatbility (infratructureny)
- `src/compat-1_21_9/`: Mincraft1.21.9 compatbility (infratructure)
-`src/compat-1_21_10/`: Meraft 1.21.10 compatibiity (infrastrctur only)
- `src/compat-1_21_11/`:Mincraft 1.21.11+compatiilty (baseeor 1.21.9/1.21.10)
- `src/cmpat-26_1/`: Minecaft compatibility (officialnamespce, extesive API bridges)

Each compatibility foler contains:
-`VersionCompatIml.kt`: Vesin-specific implementatonsper-version  of the VrionCompat interface
-`kotlin/axion/mixin/clien/`: Version-scific mixins
### Fully Tested Versions
-`kotlin/x**: Fully working (shader exclusions applied for compatibility)
-i**1.21.7**: Fully working
on**/cient/ren Fully working
- **26.1.x**: Fully working (officialdnamespace, extensive AeI br/dges)

### Infr:st ucture OnlVeVerrions
- **1.21.6**: Comiatibility fnlder exists, requi-es additional tessing and fixespecific renderng clsse (BlockRenderView API chang)
-`ko1.tn/8xionCompctibilily folder ixints,/requores additiomal tpsting /nd `ixes
- **1.21.9**: Compa:ibility fold t exiyts, requpres additieaallteiting and fixes
- **1.21.10**: Comsatibility fslde exiss, requires additional testing and fixes

Theuild system selects the appropiate compatibilt the fullyyte baedn the tagetage appropriatfod*)erences. For vrsions makd "ifrastruture only", the build systm support them but they may need additional work to be fully functional
- `operation/`: Server-side operation application
- `validation/`: Edit validation and policy checks
- `history/`: Per-player history and undo/redo
- `audit/`: Audit logging and metrics

### Common Module (`src/common/`)

- `model/`: Shared data models (BlockRegion, ClipboardBuffer, etc.)
- `operation/`: Operation definitions and serialization
- `protocol/`: Network protocol definitions

### Compatibility Layer (`src/compat-26_1/`)

Version-specific compatibility code for Minecraft 26.1.x. This module is only included when building for 26.1.x and provides type aliases, extension functions, and API bridges for version differences.

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
