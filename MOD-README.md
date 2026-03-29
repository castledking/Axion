[![Axion Banner](https://castled.codes/assets/axion-banner.png)](https://github.com/castledking/Axion/wiki/)

## What is it?

Axion is an open-source Fabric building tool for Minecraft, inspired by Axiom. It adds fast in-game editing tools, live previews, symmetry, Magic Select, and long-range builder utilities.

For multiplayer-safe editing, pair it with the Axion Paper plugin:

- [Axion Plugin README](https://github.com/castledking/Axion/blob/main/PLUGIN-README.md)

<p align="center">
  <a href="https://modrinth.com/mod/axion-paper-plugin"><img alt="Download on Modrinth" src="https://img.shields.io/badge/Download%20on-Modrinth-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white"></a>
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
  <a href="https://modrinth.com/mod/fabric-api"><img alt="Requires Fabric API" src="https://img.shields.io/badge/Requires-Fabric%20API-c9b07a?style=for-the-badge"></a>
  <a href="https://modrinth.com/mod/fabric-language-kotlin"><img alt="Requires Fabric Language Kotlin" src="https://img.shields.io/badge/Requires-Fabric%20Language%20Kotlin-7f52ff?style=for-the-badge&logo=kotlin&logoColor=white"></a>
</p>

## Overview

The Fabric mod is the client-side half of Axion. It handles:

- hotbar tool selection
- live region previews
- symmetry anchors and gizmos
- Magic Select templates and masks
- builder modes like replace mode, infinite reach, and no clip
- local singleplayer behavior

For multiplayer editing, the Paper plugin is what makes server-backed edits, history, validation, and large operations work correctly.

## Features

- Hotbar-based tools for:
  - Move
  - Clone
  - Stack
  - Smear
  - Erase
  - Extrude
  - Symmetry
- Magic Select with:
  - editable templates
  - shared custom masks
  - searchable block picker
  - brush size control
- Stronger preview feedback:
  - ghost block previews
  - guide arrows
  - pulsing selection feedback
  - clearer helper text
- Builder utilities:
  - replace mode
  - infinite reach
  - no clip
  - far pick block
- Mod Menu integration and in-game config screen

## Requirements

- Minecraft `1.21.11`
- Fabric Loader
- Fabric API
- Fabric Language Kotlin

## Installation

1. Install Fabric Loader for Minecraft `1.21.11`.
2. Put the Axion mod jar in your `mods` folder.
3. Install Fabric API and Fabric Language Kotlin.
4. Launch Minecraft in creative mode.

## Controls and Usage

Axion is designed around a dedicated hotbar slot workflow.

- Equip the Axion slot to access the tool strip
- Use the selected subtool directly in-world
- Hold `Alt` while on Axion to open the hotbar tool menu
- Press `Right Shift` to open the Axion config screen

Magic Select:

- Toggle `Middle Mouse` to `Magic Select` in the Alt menu
- Use `MMB` to select blob-like regions before point two
- Use `Main Mod + MMB` to open Magic Select Templates
- Use `Main Mod + Scroll` to adjust brush size

## Multiplayer

The mod works best with the companion Paper plugin.

Without the plugin, Axion still works in singleplayer and for client-side/local behavior, but multiplayer server-backed edit application, validation, and history are not available.

## Notes

- Axion is built for creative-mode building workflows.
- Keep the client mod and Paper plugin on matching compatible versions if you use both.
- Some newer features, especially server-backed tool behavior, depend on the plugin being updated too.

## Links

- Main README: https://github.com/castledking/Axion/blob/main/README.md
- Plugin README: https://github.com/castledking/Axion/blob/main/PLUGIN-README.md
- Modrinth: https://modrinth.com/plugin/axion-paper-plugin
- GitHub: https://github.com/castledking/Axion
- Issues: https://github.com/castledking/Axion/issues
- Wiki: https://github.com/castledking/Axion/wiki
- Website: https://castled.codes
- Discord: https://discord.com/invite/pCKdCX6nYr
