# Axion

An open-source building tool for Minecraft, inspired by [Axiom](https://axiom.moulberry.com/). Axion provides powerful in-game editing tools through a hotbar-based interface, with full server-side support via a companion Paper plugin.

**Website:** [castled.codes](https://castled.codes)

## Features

- **Hotbar Slot Tools** -- access all tools directly from your hotbar
  - **Symmetry** -- mirror your builds across configurable axes
  - **Clone** -- duplicate selections with high performance, even on servers
  - **Smear** -- drag and repeat blocks along a direction
  - **Stack** -- repeat selections in a direction
  - **Move** -- relocate selections
  - **Extrude** -- push or pull faces of a selection
- **Replace Mode** -- replace blocks
- **Infinite Reach** -- edit blocks at any distance
- **No Clip** -- fly through blocks for easier building
- **Server Support** -- dedicated Paper plugin so everything works in multiplayer
- **Open Source** -- contributions welcome

## Supported Versions

| Platform | Version |
|----------|---------|
| Fabric   | 1.21.11 |
| Paper    | 1.21.11 |

## Known Issues

- Selection boxes may not be accurate
- Middle mouse button may not extend face
- Visualizations are very early alpha (Move tool has none; other tools have minimal visuals)
- Very large edits may kick the user on servers (Clone is very performant however)

## To Do

- Improve visualizations to be more like Axiom
- Symmetry is still not server-backed

## Links

- [GitHub](https://github.com/castledking/axion)
- [Issues](https://github.com/castledking/axion/issues)
- [Wiki](https://github.com/castledking/axion/wiki)
- [Discord (Bug Reports)](https://discord.com/invite/pCKdCX6nYr)
