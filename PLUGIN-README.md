[![Axion Banner](https://castled.codes/assets/axion-plugin-banner.png)](https://github.com/castledking/Axion/wiki/)


## What is it?

The Axion Paper plugin is the multiplayer companion for the Axion Fabric mod. It validates edits, applies large operations server-side, records history, and keeps advanced tools working on Paper servers.

<p align="center">
  <a href="https://modrinth.com/mod/axion"><img alt="Download on Modrinth" src="https://img.shields.io/badge/Download%20on-Modrinth-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white"></a>
  <a href="https://github.com/castledking/Axion"><img alt="GitHub" src="https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Axion/issues"><img alt="GitHub Issues" src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/Axion/wiki"><img alt="Wiki" src="https://img.shields.io/badge/GitHub-Wiki-181717?style=for-the-badge&logo=github"></a>
</p>

<p align="center">
  <a href="https://discord.com/invite/pCKdCX6nYr"><img alt="Discord" src="https://img.shields.io/badge/Discord-Community-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://github.com/castledking/Axion/blob/main/README.md"><img alt="Fabric Mod README" src="https://img.shields.io/badge/View-Fabric%20Mod%20README-3b82f6?style=for-the-badge"></a>
  <a href="https://castled.codes"><img alt="CASTLED CODEX" src="https://castled.codes/assets/logo-banner.png" width="140" height="35"></a>
</p>

<p align="center">
  <img alt="Requires Paper 1.21.11" src="https://img.shields.io/badge/Requires-Paper%201.21.11-white?style=for-the-badge">
  <img alt="Optional PacketEvents" src="https://img.shields.io/badge/Optional-PacketEvents-ef4444?style=for-the-badge">
</p>

## Overview

Axion pushes edit application and validation to the server so multiplayer building can stay fast and authoritative. The plugin handles write limits, history, undo/redo, policy checks, transport framing for very large edits, and optional PacketEvents-backed noclip support.

For the client-side toolset, install the Fabric mod:

- [Axion Mod README](https://github.com/castledking/Axion/blob/main/MOD-README.md)

## Features

- Server-backed clone, move, erase, stack, smear, extrude, and placement operations
- Per-player history with undo and redo support
- Policy validation for write budgets, clipboard budgets, and edit regions
- Large-operation transport chunking so big edits do not overflow plugin-channel packet size
- Optional PacketEvents integration for stronger noclip support on Paper
- Audit logging and timing visibility for accepted and rejected edits

## Requirements

- Minecraft / Paper `1.21.11`
- Matching Axion Fabric client on players who use the toolset

## Installation

1. Put `axion-plugin-<version>.jar` in your server `plugins` folder.
2. Restart the server.
3. Optionally install PacketEvents for stronger noclip support.
4. Have players install the matching Axion Fabric mod.

## Notes

- Axion is intended for creative-mode building workflows.
- Very large edits are server-backed, but still constrained by configured policy limits.
- The Paper plugin and Fabric mod should stay on matching protocol-compatible builds.

## Support the Project

<p align="center">
  <a href="https://www.paypal.com/ncp/payment/GU2G67R85XEBA"><img alt="Support on PayPal" src="https://img.shields.io/badge/Support-PayPal-00457C?style=for-the-badge&logo=paypal&logoColor=white"></a>
</p>

## Links

- Modrinth: https://modrinth.com/mod/axion
- GitHub: https://github.com/castledking/Axion
- Issues: https://github.com/castledking/Axion/issues
- Wiki: https://github.com/castledking/Axion/wiki
- Website: https://castled.codes
- Discord: https://discord.com/invite/pCKdCX6nYr
