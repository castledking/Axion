[![Axion Banner](https://castled.codes/assets/axion-banner.png)](https://github.com/castledking/Axion/wiki/)

Axion is an open-source Minecraft building toolkit built as a Fabric client mod with a companion Paper plugin for server-backed multiplayer editing.

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

### v0.2.8 (Released)

- **UI/UX Polish** — Reworked the tool hint HUD into compact contextual prompts, including under-crosshair mouse hints and bottom-right keybind hints.
- **New GUI Assets** — Added texture-backed mouse/input hints and a new icon-based Axion tool palette.
- **Alt Tool Menu Improvements** — Holding Alt now expands the tool stack with cleaner icon slots and improved hover/selection behavior.
- **Iris Shader Support** — Added Iris shader-pack detection and shader-aware preview fallback so large previews behave correctly when shaders are enabled or toggled.
- **Input Fixes** — Improved mouse-scroll handling so Axion does not hijack scrolling while chat, menus, or option screens are open.
- **Release Automation Update** — Release tags now publish the jars produced by `./build-axion.sh all` to the GitHub release, with release body text loaded from `.release/latest/<tag>.md`.
- **Supported Versions** — Axion v0.2.8 supports Minecraft `1.21.4` through `1.21.11` and `26.1.x`.

### v0.2.7 (Released)

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
- **1.21.5 - 1.21.8 GPU Preview Compatibility**
  - Added chunked GPU preview support for Minecraft 1.21.5
  - Added chunked GPU preview support for the legacy 1.21.6 - 1.21.8 range, compiled against 1.21.7
  - Large move, clone, stack, smear, and glass/origin overlay previews now use persistent per-section GPU buffers on these older ranges
  - Modern 1.21.9 - 1.21.11 previews adapt at runtime across the render-pass texture binding and `DynamicUniforms` API changes in that range

## Next: v0.2.9

- **26.1.x Fabric Dedicated Server Support** — Fabric dedicated server support for the 26.1.x range is planned next in v0.2.9.
- **1.21.1 - 1.21.3 Support** — Support for Minecraft `1.21.1` through `1.21.3` is coming soon as part of the ongoing 1.21.x backport work.

## Roadmap & Upcoming

### Future Plans
- More "capabilities" like `No Updates`, `Phantom`, `Angel Placement` and `Tinker`
- Add support for older Minecraft versions after the `1.21.x` backport work
- Add support for more Minecraft versions on Fabric dedicated servers
- UI improvements and polish
- 0.3.0 and beyond: Develop the `Editor Mode` for more advanced building workflows (MCEdit-style editor)

## Support

- Issues: https://github.com/castledking/Axion/issues
- Wiki: https://github.com/castledking/Axion/wiki
- Discord: https://discord.com/invite/pCKdCX6nYr
- Website: https://castled.codes
