[![Axion Banner](https://castled.codes/assets/axion-banner.png)](https://github.com/castledking/Axion/wiki/)

Axion is an open-source Minecraft building toolkit inspired by Axiom, built as a Fabric client mod with a companion Paper plugin for server-backed multiplayer editing.

<p align="center">
  <a href="https://github.com/castledking/Axion/wiki"><img alt="Wiki" src="https://img.shields.io/badge/GitHub-Wiki-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Axion/issues"><img alt="Issues" src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github"></a>
  <a href="https://discord.com/invite/pCKdCX6nYr"><img alt="Discord" src="https://img.shields.io/badge/Discord-Community-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://castled.codes"><img alt="CASTLED CODEX" src="https://castled.codes/assets/logo-banner.png" width="140" height="35"></a>
</p>

## Quick Links

- **[MOD-README](MOD-README.md)** — Fabric mod install and usage (for Modrinth)
- **[PLUGIN-README](PLUGIN-README.md)** — Paper plugin install and server notes (for Modrinth)
- **[Wiki](https://github.com/castledking/Axion/wiki)** — Documentation, usage notes, and setup pages
- **[Contributing](CONTRIBUTING.md)** — Development guide for contributors

## Changelog

### v0.2.7 (In development)

<a href="https://github.com/castledking/Axion/actions/workflows/cd.yml"><img alt="Build" src="https://github.com/castledking/Axion/actions/workflows/cd.yml/badge.svg?branch=main"></a>

- **Minecraft 26.1.x Support** — Fabric client compatibility work for the latest Minecraft version
- **26.1.x Rendering Fixes** — Restored toolbox icon textures, selection visuals, move-source glass overlays, and GPU block previews
- **Smear Tool Overhaul** — True block smearing that samples your selection and spreads blocks as you scroll, perfect for creating staircases and roofs
- **Stack & Smear Reliability** — Increased repeat limits, improved large GPU previews, fixed no-op smear feedback, and made smear overlap resolution more deterministic
- **Hotbar Save/Load Fixes** — Saved hotbars restore correctly on 26.1.x
- **Paper Admin Reload** — Added console-only `/axion reload` for reloading Paper plugin config and policy options without restarting the server
- **Magic Selection Enhancements**
  - More block tags available for custom masks
  - Red outline for disabled templates for easy visual identification
  - Same Block Select toggle to pick up matching blocks outside your mask
  - Continuous stroke support for faster large selections
- **Flying Speed Slider** — Adjust creative flight speed from 100% to 999% via Alt menu
- **Improved Infinite Reach** — Replace mode, bulldozer, and fast place now work seamlessly at any distance

## Next: v0.2.8

- **Version Support** — Add Paper/mod support for Minecraft 1.21.4 and Fabric dedicated server support for 26.1.x
- **UI Improvements** — Replace the current screen-heavy tool hints/keybind/status overlay with a less intrusive UI for tool state, keybinds, offsets, and preview metadata

## Roadmap & Upcoming

### Future Plans
- More "capabilities" like `No Updates`, `Phantom`, `Angel Placement` and `Tinker`
- Add support for versions `1.20` through `1.21.4` for mod and plugin
- Add support for other Minecraft versions for the Fabric dedicated server
- UI improvements and polish
- 0.3.0 and beyond: Develop the `Editor Mode` for more advanced building workflows (MCEdit-style editor)

## Support

- Issues: https://github.com/castledking/Axion/issues
- Wiki: https://github.com/castledking/Axion/wiki
- Discord: https://discord.com/invite/pCKdCX6nYr
- Website: https://castled.codes
