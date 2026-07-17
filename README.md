[![Axion Banner](https://castled.codes/assets/axion-banner.png)](https://castled.codes/axion/dl)

# Axion

Axion is an open-source Minecraft building toolkit. The Fabric mod provides
hotbar-driven editing tools and live in-world previews; the matching Paper plugin
applies and validates those edits on multiplayer servers.

<p align="center">
  <a href="https://github.com/castledking/Axion/actions/workflows/build.yml"><img alt="Build Verification" src="https://github.com/castledking/Axion/actions/workflows/build.yml/badge.svg?branch=main"></a>
  <a href="https://castled.codes/axion/dl"><img alt="Download Axion" src="https://img.shields.io/badge/Download-Axion-818cf8?style=flat-square"></a>
  <a href="https://github.com/castledking/Axion/wiki"><img alt="Wiki" src="https://img.shields.io/badge/GitHub-Wiki-181717?style=flat-square&logo=github"></a>
  <a href="https://discord.com/invite/pCKdCX6nYr"><img alt="Discord" src="https://img.shields.io/badge/Discord-Community-5865F2?style=flat-square&logo=discord&logoColor=white"></a>
</p>

## What Axion Does

- Move, clone, stack, smear, erase, extrude, and mirror terrain directly from
  the hotbar.
- Preview edits in-world before confirming them, including large GPU-rendered
  selections.
- Select organic regions with Magic Select, masks, templates, and a searchable
  block picker.
- Use symmetry, saved hotbars, replace mode, infinite reach, no-clip, and other
  creative building utilities.
- Undo and redo edits locally or through the companion Paper plugin.

Axion is designed for creative building. Singleplayer edits can run locally. On
Paper servers, builders install the Fabric mod and the server runs the matching
Axion Paper jar so edits remain authoritative and policy-aware.

## Downloads

Download the latest Fabric and Paper jars from:

**https://castled.codes/axion/dl**

Choose the jar whose filename matches the Minecraft version range you run. Keep
the Fabric mod and Paper plugin on the same Axion version.

## Supported Versions

| Component | Supported Minecraft versions |
| --- | --- |
| Fabric client mod | `1.21 - 1.21.11`, `26.1 - 26.1.2` |
| Paper plugin | `1.21 - 1.21.11`, `26.1 - 26.1.2` |
| Fabric dedicated server | `1.21.11` |

Release artifacts are grouped into seven compatibility ranges:

- `1.21 - 1.21.1`
- `1.21.2 - 1.21.3`
- `1.21.4`
- `1.21.5`
- `1.21.6 - 1.21.8`
- `1.21.9 - 1.21.11`
- `26.1 - 26.1.2`

## Installation

For singleplayer, install the Axion Fabric jar together with Fabric API and
Fabric Language Kotlin.

For a Paper server:

1. Install the matching Axion Fabric jar on each builder's client.
2. Install the matching Axion Paper jar in the server's `plugins` directory.
3. Restart the server and grant the required Axion permissions.

See [PLUGIN-README.md](PLUGIN-README.md) and the
[wiki](https://github.com/castledking/Axion/wiki) for server configuration and
permissions.

## Contributing

Contributions and focused bug reports are welcome. A full release-range build is:

```bash
./build-axion.sh all
```

Launch a development client for a specific supported version with:

```bash
./run-axion.sh 1.21.3
./run-axion.sh 1.21.11
./run-axion.sh 26.1
```

Before opening a pull request, test the affected Minecraft ranges and describe
the versions you verified. See [CONTRIBUTING.md](CONTRIBUTING.md) for the project
layout, compatibility rules, and server test commands.

## Project Links

- Downloads: https://castled.codes/axion/dl
- Issues: https://github.com/castledking/Axion/issues
- Wiki: https://github.com/castledking/Axion/wiki
- Discord: https://discord.com/invite/pCKdCX6nYr
- Website: https://castled.codes/axion/

Axion is licensed under the [GPL-3.0 license](LICENSE).
