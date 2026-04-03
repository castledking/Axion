[![Axion Banner](https://castled.codes/assets/axion-banner.png)](https://github.com/castledking/Axion/wiki/)

## What is it?

Axion is an open-source Fabric building tool for Minecraft, inspired by Axiom. It adds fast in-game editing tools, live previews, symmetry, Magic Select, saved hotbars, and long-range builder utilities.

For multiplayer-safe editing on Paper, pair it with the Axion Paper plugin:

- [Axion Plugin README](https://github.com/castledking/Axion/blob/main/PLUGIN-README.md)

For Minecraft `1.21.11`, Axion also has a Fabric dedicated server path using the same main Fabric jar that players install on the client.

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
- saved hotbars / hotbar workspace flow
- builder modes like replace mode, infinite reach, and no clip
- local singleplayer behavior

For multiplayer editing:

- on Paper, use the Axion Paper plugin
- on Fabric dedicated server, use the same Axion Fabric jar in the server `mods` folder, but this server path currently only targets Minecraft `1.21.11`

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
- Saved hotbars / hotbar workspace:
  - hold `Alt` to open the saved hotbar overlay
  - scroll or click to switch saved hotbars
  - release `Alt` to load the selected saved hotbar into your live hotbar
  - the previously active hotbar is saved back automatically
- Builder utilities:
  - replace mode
  - infinite reach
  - no clip
  - far pick block
- Preview and selection feedback:
  - pulsing cuboid feedback
  - ghost block previews
  - guide arrows
  - clearer helper text
- Mod Menu integration and in-game config screen

## Requirements

For Fabric client:

- Fabric Loader
- Fabric API
- Fabric Language Kotlin

Minecraft version support:

- `1.21.8` through `1.21.11` on the client

For Fabric dedicated server:

- Minecraft `1.21.11`
- Fabric Loader
- Fabric API
- Fabric Language Kotlin
- the same Axion Fabric jar that the client uses for `1.21.11`

## Installation

### Fabric Client

1. Install Fabric Loader for your supported Minecraft version (`1.21.8` through `1.21.11`).
2. Put the Axion mod jar in your client `mods` folder.
3. Install Fabric API and Fabric Language Kotlin.
4. Launch Minecraft in creative mode.

### Fabric Dedicated Server (`1.21.11` only)

1. Install Fabric Loader for Minecraft `1.21.11`.
2. Put the same Axion Fabric jar in the server `mods` folder.
3. Install Fabric API and Fabric Language Kotlin on the server.
4. Start the server.

Important:

- the `1.21.11` main Axion Fabric jar is dual-use
- you do not need a separate Fabric server-only Axion jar
- the same file goes in the client `mods` folder and the Fabric server `mods` folder

## Controls and Usage

Axion is designed around a dedicated hotbar slot workflow.

- Equip the Axion slot to access the tool strip
- Use the selected subtool directly in-world
- Hold `Alt` while on Axion to open the hotbar tool menu / saved hotbar overlay
- Press `Right Shift` to open the Axion config screen

Saved hotbars:

- hold `Alt` to preview saved hotbars
- scroll or click to choose a saved hotbar
- release `Alt` to load it
- your previous hotbar is saved back automatically

Magic Select:

- Toggle `Middle Mouse` to `Magic Select` in the Alt menu
- Use `MMB` to select blob-like regions before point two
- Use `Main Mod + MMB` to open Magic Select Templates
- Use `Main Mod + Scroll` to adjust brush size

## Multiplayer

Axion works in three broad modes:

- singleplayer / integrated server
- Paper multiplayer with the Axion Paper plugin
- Fabric dedicated server multiplayer on `1.21.11`

Paper is still the more mature multiplayer backend, especially for protection/policy integrations.

Fabric server support on `1.21.11` now covers real authoritative editing, but it is currently only available for that exact Minecraft version.

## Notes

- Axion is built for creative-mode building workflows.
- Keep the client and server/plugin on matching compatible versions where possible.
- Fabric dedicated server support currently only exists for `1.21.11`.
- Paper plugin support and Fabric client support cover `1.21.8` through `1.21.11`.

## Links

- Main README: https://github.com/castledking/Axion/blob/main/README.md
- Plugin README: https://github.com/castledking/Axion/blob/main/PLUGIN-README.md
- Modrinth: https://modrinth.com/plugin/axion-paper-plugin
- GitHub: https://github.com/castledking/Axion
- Issues: https://github.com/castledking/Axion/issues
- Wiki: https://github.com/castledking/Axion/wiki
- Website: https://castled.codes
- Discord: https://discord.com/invite/pCKdCX6nYr
