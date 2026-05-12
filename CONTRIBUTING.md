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

- JDK 21 or later
- IntelliJ IDEA or another Kotlin-compatible IDE
- Git

### Building

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

### Running Client (Development)

```bash
./gradlew runClient
```

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
