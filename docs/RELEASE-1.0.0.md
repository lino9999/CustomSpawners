# CustomSpawners 1.0.0

Initial release of **CustomSpawners by Lino** for **Paper 1.21.11 through 26.3**.

## Features

- Spawner stacking, mixed Omni spawners and configurable upgrade tiers.
- Entity, item, block and console-command generators, including mixed outputs.
- In-game spawner shop, money/XP upgrades, timed boosts and egg/money conversion.
- Silk Touch collection, custom mob loot, kill rewards and player statistics.
- Spawn conditions, entity caps, redstone control, placement limits and blacklists.
- Native holograms, optional particles, custom crafting and in-game editors.
- Optional Vault/PlayerPoints economy and WorldGuard/GriefPrevention protection hooks.
- English messages, persistent metadata and ordered atomic state saves with a backup.

## Requirements and installation

1. Download **CustomSpawners-1.0.0.jar** from the release assets.
2. Stop the server, place the JAR in `plugins`, and start the server.
3. Use Java 21+ for Paper 1.21.11 or Java 25+ for Paper 26.x.
4. Configure `plugins/CustomSpawners`, then run `/csp reload`.

Paper is required. Folia and the Spigot server implementation are outside the supported target. Vault needs an actual economy provider for paid money features.

Main command: `/customspawners`, aliases `/csp` and `/cspawners`. Administration permission: `customspawners.admin`.

## Verification

131 automated checks passed before cleanup. Operational checks and restart replay passed on Paper 1.21.11, 26.1.1, 26.1.2, 26.2 and 26.3. See the [compatibility notes](https://github.com/lino9999/CustomSpawners/blob/main/docs/COMPATIBILITY.md) for exact builds and limits.

## Additional downloads

- `CustomSpawners-1.0.0.jar.sha256`: SHA-256 checksum for the JAR.
- `CustomSpawners-SpigotMC-Description.bbcode.txt`: resource description in English BBCode.
- `CustomSpawners-SpigotMC-Documentation.bbcode.txt`: full English BBCode documentation.
- `CustomSpawners-icon-96.png`, `CustomSpawners-icon-128.png`, `CustomSpawners-icon-256.png`: minimalist pixel-art icon exports.

Automatic legacy item/database migration is not included. Historical test code, fixtures, reports and temporary servers were removed after verification; production runtime code was preserved.

[Documentation](https://github.com/lino9999/CustomSpawners#readme) · [Issue tracker](https://github.com/lino9999/CustomSpawners/issues)
