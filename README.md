[![Axion Banner](https://castled.codes/assets/axion-banner.png)](https://github.com/castledking/Axion/wiki/)

# Axion

Axion is an open-source Minecraft building toolkit inspired by Axiom, built as a Fabric client mod with a companion Paper plugin for server-backed multiplayer editing.

<p align="center">
  <a href="https://github.com/castledking/Axion/blob/main/MOD-README.md"><img alt="Fabric Mod README" src="https://img.shields.io/badge/View-Fabric%20Mod%20README-3b82f6?style=for-the-badge"></a>
  <a href="https://github.com/castledking/Axion/blob/main/PLUGIN-README.md"><img alt="Paper Plugin README" src="https://img.shields.io/badge/View-Paper%20Plugin%20README-2563eb?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="https://github.com/castledking/Axion/wiki"><img alt="Wiki" src="https://img.shields.io/badge/GitHub-Wiki-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Axion/issues"><img alt="Issues" src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github"></a>
  <a href="https://discord.com/invite/pCKdCX6nYr"><img alt="Discord" src="https://img.shields.io/badge/Discord-Community-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://castled.codes"><img alt="CASTLED CODEX" src="https://castled.codes/assets/logo-banner.png" width="140" height="35"></a>
</p>

<p align="center">
  <a href="https://modrinth.com/mod/fabric-api"><img alt="Requires Fabric API" src="https://img.shields.io/badge/Requires-Fabric%20API-c9b07a?style=for-the-badge"></a>
  <a href="https://modrinth.com/mod/fabric-language-kotlin"><img alt="Requires Fabric Language Kotlin" src="https://img.shields.io/badge/Requires-Fabric%20Language%20Kotlin-7f52ff?style=for-the-badge&logo=kotlin&logoColor=white"></a>
</p>

<p align="center">
  <img alt="Requires Paper 1.21.11" src="https://img.shields.io/badge/Requires-Paper%201.21.11-white?style=for-the-badge">
</p>

## What Is Axion?

Axion brings fast in-game building tools directly into Minecraft with hotbar-based editing, live previews, symmetry, long-range building modes, and multiplayer-safe execution through the Paper plugin.

The Fabric mod handles the client-side tools, previews, input, config UI, and local singleplayer behavior.

The Paper plugin handles authoritative multiplayer edit application, validation, history, undo/redo, and large-operation transport.

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
  - clearer first/second point markers
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

- **Flying Speed Slider** — Adjust creative flight speed from 100% to 999% via Alt menu
- **Improved Infinite Reach** — Replace mode, bulldozer, and fast place now work seamlessly at any distance
- Magic Select Templates for custom masked selection behavior
- Better move/clone/stack/smear preview flow and scrolling visualization
- Multi-directional stack and smear previews
- Improved symmetry anchors, mirror controls, and feedback
- Alt hotbar toggles like `Keep Existing`, `Copy Entities`, and `Copy Air`

## Project Layout

- [MOD-README](https://github.com/castledking/Axion/blob/main/MOD-README.md)
  - Fabric mod install and usage
- [PLUGIN-README](https://github.com/castledking/Axion/blob/main/PLUGIN-README.md)
  - Paper plugin install and server notes
- [Wiki](https://github.com/castledking/Axion/wiki)
  - documentation, usage notes, and setup pages

## Supported Versions

| Component | Version |
| --- | --- |
| Minecraft | `1.21.11` |
| Fabric Mod | `1.21.11` |
| Paper Plugin | `1.21.11` |

## Requirements

### Fabric Mod

- Fabric Loader
- Fabric API
- Fabric Language Kotlin

### Paper Plugin

- Paper `1.21.11`
- Matching Axion client version for players using the toolset

## Quick Start

### Singleplayer / Client Use

1. Install Fabric Loader for Minecraft `1.21.11`.
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

- Saved hotbars and hotbar presets
- More Axiom-style builder utilities
- Expanded config and workflow customization
- More advanced selection and mask tooling

## Support

- Issues: https://github.com/castledking/Axion/issues
- Wiki: https://github.com/castledking/Axion/wiki
- Discord: https://discord.com/invite/pCKdCX6nYr
- Website: https://castled.codes
