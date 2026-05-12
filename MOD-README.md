[![Axion Banner](https://castled.codes/assets/axion-banner.png)](https://github.com/castledking/Axion/wiki/)

## What is it?

Axion is an open-source Fabric building tool for Minecraft, inspired by Axiom. It adds fast in-game editing tools, live previews, symmetry, Magic Select, saved hotbars, and long-range builder utilities.

For multiplayer-safe editing on Paper, pair it with the Axion Paper plugin:

<a href="https://modrinth.com/mod/axion-paper-plugin"><img alt="Download on Modrinth" src="https://img.shields.io/badge/Download%20on-Modrinth-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white"></a> <p>[Axion Plugin README](https://github.com/castledking/Axion/blob/main/PLUGIN-README.md)

For Minecraft `1.21.11`, Axion also has a Fabric dedicated server path using the same main Fabric jar that players install on the client.

<p align="center">
  <a href="https://github.com/castledking/Axion"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Axion/issues"><img alt="GitHub Issues" src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Axion/wiki"><img alt="Wiki" src="https://img.shields.io/badge/GitHub-Wiki-181717?style=for-the-badge&logo=github"></a>
  <a href="https://discord.com/invite/pCKdCX6nYr"><img alt="Discord" src="https://img.shields.io/badge/Discord-Community-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://github.com/castledking/Axion/blob/main/README.md"><img alt="Main README" src="https://img.shields.io/badge/View-Main%20README-3b82f6?style=for-the-badge"></a>
  <a href="https://modrinth.com/mod/fabric-api"><img alt="Requires Fabric API" src="https://img.shields.io/badge/Requires-Fabric%20API-c9b07a?style=for-the-badge"></a>
  <a href="https://modrinth.com/mod/fabric-language-kotlin"><img alt="Requires Fabric Language Kotlin" src="https://img.shields.io/badge/Requires-Fabric%20Language%20Kotlin-7f52ff?style=for-the-badge&logo=kotlin&logoColor=white"></a>
</p>

## Overview

The Fabric mod is the client-side half of Axion. It handles:

- hotbar tool selection
- live region previews
- symmetry anchors and gizmos
- Magic Select templates and masks
- saved hotbars / hotbar workspace flow
- builder modes like replace mode, infinite reach, and no clip
- local singleplayer behavior

**Version Support:** Axion currently supports `1.21.5` through `1.21.11` & `26.1.x` for both the Fabric client and the Paper plugin. Fabric dedicated server support is currently only available for `1.21.11`.

For multiplayer editing:

- on Paper, use the Axion Paper plugin
- on Fabric dedicated server, use the same Axion Fabric jar in the server `mods` folder, but this server path currently only targets Minecraft `1.21.11`

## Features

- **Hotbar Tools**
  - `Move`
  - `Clone`
  - `Stack`
  - `Smear`
  - `Erase`
  - `Extrude`
  - `Symmetry`
- **Magic Select**
  - blob-style selection before point two
  - configurable templates and custom masks
  - searchable block picker and adjustable brush size
- **Live Previews**
  - scrolling ghost previews
  - direction arrows
  - pulsing source-region visualization
  - first/second point markers
- **Builder Modes**
  - replace mode
  - infinite reach
  - no clip
  - bulldozer
  - fast place
- **Config and UI**
  - Mod Menu support
  - in-game config screen
  - Alt hotbar menu for adjusting fly speed andhotbar saving/loading
- **Multiplayer Support**
  - server-backed edits through the companion Paper plugin
  - undo/redo and validation on the server side
- **Open Source**
  - GPL-3.0 licensed

## Current Highlights

- **Minecraft 26.1.x Support** — Full compatibility with the latest Minecraft version
- **Smear Tool Overhaul** — True block smearing that samples your selection and spreads blocks as you scroll, perfect for creating staircases and roofs
- **Magic Selection Enhancements**
  - More block tags available for custom masks
  - Red outline for disabled templates for easy visual identification
  - Same Block Select toggle to pick up matching blocks outside your mask
  - Continuous stroke support for faster large selections
- **Flying Speed Slider** — Adjust creative flight speed from 100% to 999% via Alt menu
- **Improved Infinite Reach** — Replace mode, bulldozer, and fast place now work seamlessly at any distance
- Better move/clone/stack preview flow and scrolling visualization
- Multi-directional stack previews
- Improved symmetry anchors, mirror controls, and feedback
- Alt hotbar toggles like `Keep Existing`, `Copy Entities`, and `Copy Air`

## Quick Links

- [MOD-README](https://github.com/castledking/Axion/blob/main/MOD-README.md)
  - Fabric mod install and usage
- [PLUGIN-README](https://github.com/castledking/Axion/blob/main/PLUGIN-README.md)
  - Paper plugin install and server notes
- [Wiki](https://github.com/castledking/Axion/wiki)
  - documentation, usage notes, and setup pages

## Supported Versions

| Component | Version |
| --- | --- |
| Fabric Mod (client) | `1.21.5 - 1.21.11 & 26.1.x`  |
| Paper Plugin | `1.21.5 - 1.21.11 & 26.1.x` |
| Fabric Dedicated Server | `1.21.11` only |

## Requirements

### Fabric Mod

- Fabric Loader
- Fabric API
- Fabric Language Kotlin

### Paper Plugin

- Matching Axion client version for players using the toolset
- Rest of the requirements are optional but recommended for protection hooks:
  - WorldGuard 7.0.14 or later
  - GriefPrevention 16.1.3 or later
  - GriefPrevention3D 17.0.0 or later

## Quick Start

### Singleplayer / Client Use

1. Install Fabric Loader for your Minecraft version.
2. Put the Axion mod jar in your `mods` folder.
3. Install Fabric API and Fabric Language Kotlin.
4. Launch Minecraft in creative mode.

### Multiplayer / Server Use

1. Install the Axion Fabric mod on clients.
2. Install the Axion Paper plugin on the server.
3. Restart the server.
4. Keep the mod and plugin on matching compatible builds.

## Notes

- Axion is designed around creative-mode building workflows.
- The Fabric mod by itself does not replace the Paper plugin for multiplayer server edits.
- Some newer features, especially server-backed edit behavior, are best tested with matching client and plugin versions.

## Roadmap

- More "capabilities" like `No Updates`, `Phantom`, `Angel Placement` and `Tinker`
- Add support for versions `1.20` through `1.21.4` for mod and plugin
- Add support for other Minecraft versions for the Fabric dedicated server
- UI improvements and polish
- 0.3.0 and beyond: Develop the `Editor Mode` for more advanced building workflows (MCEdit-style editor)

## Links

- Main README: https://github.com/castledking/Axion/blob/main/README.md
- Plugin README: https://github.com/castledking/Axion/blob/main/PLUGIN-README.md
- Modrinth: https://modrinth.com/plugin/axion-paper-plugin
- GitHub: https://github.com/castledking/Axion
- Issues: https://github.com/castledking/Axion/issues
- Wiki: https://github.com/castledking/Axion/wiki
- Website: https://castled.codes
- Discord: https://discord.com/invite/pCKdCX6nYr
