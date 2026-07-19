[![Axion Paper Banner](https://castled.codes/assets/axion-plugin-banner.png)](https://castled.codes/axion/downloads)

# Axion Paper Plugin

The Axion Paper plugin is the authoritative multiplayer companion to the Axion
Fabric mod. It validates client requests, applies edits on the server, records
history, and enforces server policy for advanced building tools.

Download matching Fabric and Paper jars from
**https://castled.codes/axion/dl**.

## Supported Versions

The Paper plugin supports Minecraft `1.21` through `1.21.11` and `26.1` through
`26.1.2`. Each release provides one Paper jar for every supported compatibility
range:

- `1.21 - 1.21.1`
- `1.21.2 - 1.21.3`
- `1.21.4`
- `1.21.5`
- `1.21.6 - 1.21.8`
- `1.21.9 - 1.21.11`
- `26.1 - 26.1.2`

Fabric dedicated-server support is separate from Paper and currently targets
Minecraft `1.21.11` only. That path uses the main Axion Fabric jar instead of the
Paper plugin.

## Features

- Server-backed move, clone, stack, smear, erase, extrude, and placement edits
- Per-player undo and redo history
- Request, clipboard, region, and operation-size validation
- Large-operation transport and transaction handling
- Configurable world policies, permissions, and audit logging
- Optional WorldGuard and GriefPrevention protection integration
- No-physics edit writes so confirm, undo, and redo do not immediately trigger
  gravity blocks

## Installation

1. Download the Paper jar matching the server's Minecraft range.
2. Put `AxionPaper-v<version>-mc<range>.jar` in the server's `plugins` directory.
3. Restart the server fully.
4. Install the matching Axion Fabric jar on each builder's client.
5. Configure permissions and any protection integrations for your server.

The Fabric and Paper jars should use the same Axion version. Singleplayer users
do not need the Paper plugin.

## Configuration

The plugin creates `plugins/Axion/config.yml`. Audit output can be kept quiet by
default and enabled when diagnosing server behavior:

```yml
audit:
  enabled: false
  slow-threshold-ms: 200
  summary-every: 50
```

Optional protection hooks support WorldGuard, GriefPrevention, and
GriefPrevention3D when compatible versions of those plugins are installed.

## Fabric Dedicated Server Alternative

For Minecraft `1.21.11`, a Fabric dedicated server can use the main Axion Fabric
jar in its `mods` directory together with Fabric API and Fabric Language Kotlin.
Do not install the Paper jar on a Fabric server.

## Support

- Downloads: https://castled.codes/axion/dl
- Permissions: https://github.com/castledking/Axion/wiki/Permissions
- Wiki: https://github.com/castledking/Axion/wiki
- Issues: https://github.com/castledking/Axion/issues
- Discord: https://discord.com/invite/pCKdCX6nYr
