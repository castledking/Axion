[![Axion Banner](https://castled.codes/assets/axion-plugin-banner.png)](https://github.com/castledking/Axion/wiki/)

## What is it?

The Axion Paper plugin is the multiplayer companion for the Axion Fabric mod on Paper servers. It validates edits, applies larger operations server-side, records history, and keeps advanced building tools working correctly on Paper.

For the client-side toolset, install the Fabric mod:

<a href="https://modrinth.com/mod/axion"><img alt="Download on Modrinth" src="https://img.shields.io/badge/Download%20on-Modrinth-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white"></a> <p>[Axion Mod README](https://github.com/castledking/Axion/blob/main/MOD-README.md)

If you are running a Fabric dedicated server instead of Paper:

- Fabric dedicated server support exists only for Minecraft `1.21.11`
- it does not use this plugin
- it uses the same main Axion Fabric mod jar in the server `mods` folder

<p align="center">
  <a href="https://github.com/castledking/Axion"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Axion/issues"><img alt="GitHub Issues" src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Axion/wiki"><img alt="Wiki" src="https://img.shields.io/badge/GitHub-Wiki-181717?style=for-the-badge&logo=github"></a>
</p>

<p align="center">
  <a href="https://discord.com/invite/pCKdCX6nYr"><img alt="Discord" src="https://img.shields.io/badge/Discord-Community-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://github.com/castledking/Axion/blob/main/README.md"><img alt="Main README" src="https://img.shields.io/badge/View-Main%20README-3b82f6?style=for-the-badge"></a>
</p>

<p align="center">
  <img alt="Supports Paper 1.21.8 to 1.21.11" src="https://img.shields.io/badge/Supports-Paper%201.21.8--1.21.11-white?style=for-the-badge">
</p>

## Overview

The Paper plugin is what makes Axion authoritative in multiplayer on Paper.

It handles:

- server-backed edit application
- write and clipboard validation
- per-player history with undo and redo
- large edit transport
- server-side policy checks
- multiplayer-safe tool behavior

If you are only using Axion in singleplayer, you only need the Fabric mod.

If you want Axion edits to work properly on a Paper server, you need this plugin too.

## Version Support

Fabric client support:

- `1.21.8`
- `1.21.9`
- `1.21.10`
- `1.21.11`

Paper plugin support:

- `1.21.8`
- `1.21.9`
- `1.21.10`
- `1.21.11`

Fabric dedicated server support:

- `1.21.11` only

## Features

- Server-backed support for Axion editing tools
- Per-player undo and redo history
- Validation for edit limits and region safety
- Large-operation transport chunking
- Configurable audit logging
- Backend support for newer tool behavior and copy/move workflows

## Requirements

For Paper:

- Paper server for a supported Minecraft version (`1.21.8` through `1.21.11`)
- matching Axion Fabric client for players using the toolset

For Fabric dedicated server instead of Paper:

- use the main Axion Fabric mod jar in the server `mods` folder
- install `fabric-api`
- install `fabric-language-kotlin`
- this Fabric server path currently only supports Minecraft `1.21.11`

## Installation

### Paper

1. Put `axion-plugin-<version>.jar` in your server `plugins` folder.
2. Restart the server fully.
3. Have builders install the matching Axion Fabric client mod.

### Fabric Dedicated Server Alternative (`1.21.11` only)

If you are not running Paper and want Axion on a Fabric dedicated server:

1. Install Fabric Loader for Minecraft `1.21.11`.
2. Put the main Axion Fabric jar in the server `mods` folder.
3. Install `fabric-api` and `fabric-language-kotlin` on the server.
4. Use the same Axion Fabric jar on the client side.

This Fabric server path does not use the Paper plugin jar.

## Client Notes

Players still use the Fabric mod client-side, including:

- Axion tools
- previews
- Magic Select
- saved hotbars / hotbar workspace
- symmetry
- no clip / infinite reach / replace mode UI
- fly speed adjustment via Alt menu

Saved hotbars:

- hold `Alt` to open the saved hotbar overlay
- scroll or click to choose a saved hotbar
- release `Alt` to load it into the active hotbar
- the previously active hotbar is saved back automatically

## Configuration

Axion includes a `config.yml` for plugin-side settings.

One notable option:

```yml
audit:
  enabled: false
  slow-threshold-ms: 200
  summary-every: 50
```

When `audit.enabled` is `false`, routine audit logging and timing summaries stay quiet.

## Notes

- The Fabric mod and Paper plugin should stay on matching compatible builds.
- Paper plugin support covers `1.21.8` through `1.21.11`.
- Fabric dedicated server support is separate from the Paper plugin path and currently only targets `1.21.11`.
- The Fabric dedicated server path uses the main Axion Fabric jar, not the Paper plugin jar.
- Axion is designed for creative-mode building workflows.

## Permissions and Docs

- Permissions guide: https://github.com/castledking/Axion/wiki/Permissions
- Wiki: https://github.com/castledking/Axion/wiki

## Links

- Main README: https://github.com/castledking/Axion/blob/main/README.md
- Mod README: https://github.com/castledking/Axion/blob/main/MOD-README.md
- Modrinth: https://modrinth.com/plugin/axion
- Issues: https://github.com/castledking/Axion/issues
- Wiki: https://github.com/castledking/Axion/wiki
- Website: https://castled.codes
- Discord: https://discord.com/invite/pCKdCX6nYr
