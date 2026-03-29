[![Axion Banner](https://castled.codes/assets/axion-plugin-banner.png)](https://github.com/castledking/Axion/wiki/)

## What is it?

The Axion Paper plugin is the multiplayer companion for the Axion Fabric mod. It validates edits, applies larger operations server-side, records history, and keeps advanced building tools working correctly on Paper servers.

For the client-side toolset, install the Fabric mod:

- [Axion Mod README](https://github.com/castledking/Axion/blob/main/MOD-README.md)

<p align="center">
  <a href="https://modrinth.com/mod/axion"><img alt="Download on Modrinth" src="https://img.shields.io/badge/Download%20on-Modrinth-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white"></a>
  <a href="https://github.com/castledking/Axion"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Axion/issues"><img alt="GitHub Issues" src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Axion/wiki"><img alt="Wiki" src="https://img.shields.io/badge/GitHub-Wiki-181717?style=for-the-badge&logo=github"></a>
</p>

<p align="center">
  <a href="https://discord.com/invite/pCKdCX6nYr"><img alt="Discord" src="https://img.shields.io/badge/Discord-Community-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://github.com/castledking/Axion/blob/main/README.md"><img alt="Main README" src="https://img.shields.io/badge/View-Main%20README-3b82f6?style=for-the-badge"></a>
  <a href="https://castled.codes"><img alt="CASTLED CODEX" src="https://castled.codes/assets/logo-banner.png" width="140" height="35"></a>
</p>

<p align="center">
  <img alt="Requires Paper 1.21.11" src="https://img.shields.io/badge/Requires-Paper%201.21.11-white?style=for-the-badge">
</p>

## Overview

The Paper plugin is what makes Axion authoritative in multiplayer.

It handles:

- server-backed edit application
- write and clipboard validation
- per-player history with undo and redo
- large edit transport
- server-side policy checks
- multiplayer-safe tool behavior

If you are only using Axion in singleplayer, you only need the Fabric mod. If you want Axion edits to work properly on a Paper server, you need this plugin too.

## Features

- Server-backed support for Axion editing tools
- Per-player undo and redo history
- Validation for edit limits and region safety
- Large-operation transport chunking
- Configurable audit logging
- Backend support for newer tool behavior and copy/move workflows

## Requirements

- Paper `1.21.11`
- Matching Axion Fabric client for players using the toolset

## Installation

1. Put `axion-plugin-<version>.jar` in your server `plugins` folder.
2. Restart the server fully.
3. Have builders install the matching Axion Fabric mod.

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
- Newer features, especially around multiplayer edit behavior, are safest when both sides are updated together.
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
