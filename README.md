# CustomSpawners

[![Build](https://github.com/lino9999/CustomSpawners/actions/workflows/build.yml/badge.svg)](https://github.com/lino9999/CustomSpawners/actions/workflows/build.yml)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11%E2%80%9326.3-62B47A)
![Platform](https://img.shields.io/badge/Platform-Paper-49A5D5)
![License](https://img.shields.io/badge/License-MIT-blue)

**CustomSpawners by Lino** is a configurable Minecraft spawner plugin for Paper servers. Combine spawners, build mixed mob farms, unlock tiers, create item and block generators, customize mob loot, and run a spawner economy from in-game menus.

Designed for survival, Skyblock, factions and economy servers, CustomSpawners uses public APIs, persistent item metadata, validated configuration and a bounded scheduler. It includes English messages, native holograms and a definition editor.

## Features

- **Spawner stacking:** combine matching spawners or create Omni spawners containing multiple types and tiers.
- **Tier progression:** configure ordered tiers and buy additional stack units or tier upgrades with money or experience levels.
- **Four generator modes:** spawn entities, drop items, place blocks or execute console commands. A tier can combine all four.
- **Spawner shop:** paginated menus, configurable prices and sorting, and purchases of 1, 10 or 64 items.
- **Timed boosts:** buy extra output for a placed spawner or grant owner boosts, including permanent boosts. Active bonuses add together; expired bonuses produce nothing.
- **Spawner conversion:** exchange types using money or spawn eggs, with an optional return of the previous mob egg.
- **Silk Touch collection:** configurable tool requirements, natural/placed drop chances, whole-stack pickup and direct inventory delivery.
- **Custom loot:** item quantities, probability, names, lore, command rewards, experience and looting bonuses for generated mobs.
- **Mob kill rewards:** configurable guaranteed milestones, random spawner rewards, progress alerts and kill statistics.
- **Spawn conditions:** biome, height, light, nearby players, entity caps, storm requirements and permitted ground materials. Bounded arithmetic expressions customize output amounts without a script engine.
- **Farm controls:** redstone pause, optional no-AI mobs, retaliation-only monsters, chunk/player limits, proximity combining, world/type blacklists and liquid repelling.
- **Explosion handling:** independent TNT, creeper and other explosion probabilities, or complete spawner explosion protection.
- **Custom crafting:** shaped recipes per type, with permission checks.
- **In-game administration:** nested definition/settings menus and validated YAML edits through commands or chat prompts.
- **Native visuals:** `TextDisplay` holograms show type, tier, stack size and pause reasons; optional particles need no hologram dependency.
- **Persistence and extensions:** indexed records, ordered atomic state snapshots with a backup, cancellable API actions and optional economy/protection hooks.

## Compatibility

| Server | Server Java | Plugin bytecode |
| --- | --- | --- |
| Paper 1.21.11 | Java 21+ | Java 21 |
| Paper 26.1–26.3 | Java 25+ | Java 21 |

The compatibility target is **Paper 1.21.11 through 26.3**. Automated builds compile against both endpoint APIs. Runtime checks and their limits are recorded in [Testing](docs/TESTING.md). Paper forks may work but need their own checks. Folia and older Minecraft versions are outside the supported target.

## Installation

1. Build with `./mvnw verify` on Linux/macOS or `mvnw.cmd verify` on Windows using JDK 21 or newer.
2. Copy `target/CustomSpawners-1.0.0.jar` into the server's `plugins` folder and restart.
3. Optionally install Vault with an economy provider, or PlayerPoints. WorldGuard and GriefPrevention are supported protection hooks.
4. Configure `plugins/CustomSpawners/config.yml`, `spawners.yml` and `messages.yml`.
5. Run `/csp reload`. Invalid settings or definitions are rejected and the previously loaded configuration remains active.

The first start generates mob definitions from the server's available entity types. New mobs on newer supported servers receive definitions without a hardcoded entity list. Example item, block and command generators are disabled until you enable them.

## Commands

The main command is `/customspawners`, with aliases **`/csp`** and **`/cspawners`**.

| Command | Purpose | Permission |
| --- | --- | --- |
| `/csp help` | Show command help | Everyone |
| `/csp inspect` | Open the targeted spawner | `customspawners.use` |
| `/csp shop` | Open the shop | `customspawners.shop` |
| `/csp stats` | Open your mob kill statistics | `customspawners.stats` |
| `/csp upgrade [money\|xp] [stack\|tier]` | Buy a stack unit or advance the first component's tier | `customspawners.upgrade` |
| `/csp convert <type>` | Convert the first component using money | `customspawners.convert` |
| `/csp boost [amount minutes]` | Open the boost menu or buy extra output | `customspawners.boost` |
| `/csp give <player\|all> <type\|random> [tier] [items] [stack]` | Give tagged spawner items | `customspawners.give` |
| `/csp openshop <player>` | Open another online player's shop | `customspawners.openshop` |
| `/csp playerboost <player> <amount\|0> [duration]` | Grant or clear an owner boost | `customspawners.adminboost` |
| `/csp change <type> [tier]` | Administratively replace the first targeted component | `customspawners.change` |
| `/csp spawn` | Trigger the targeted spawner, respecting conditions | `customspawners.spawn` |
| `/csp remove` | Remove the targeted spawner without drops | `customspawners.remove` |
| `/csp editor` | Open the spawner definition editor | `customspawners.editor` |
| `/csp editor set <path> <yaml-value>` | Validate and save a definition edit | `customspawners.editor` |
| `/csp settings` | Open the plugin settings editor | `customspawners.settings` |
| `/csp settings set <path> <yaml-value>` | Validate and save a main setting | `customspawners.settings` |
| `/csp reload` | Reload settings, definitions, messages and hooks | `customspawners.reload` |
| `/csp save` | Queue a persistent state snapshot | `customspawners.save` |

Standalone commands: `/cspawnershop` (alias `/cspshop`) and `/cspawnerstats` (alias `/cspstats`). Targeted commands require a spawner within eight blocks. The Omni menu lets you select individual components. Player boosts accept `30s`, `15m`, `2h`, `3d` or `permanent`; omitted duration means permanent, and `0` clears the bonus.

Examples:

```text
/csp give Steve zombie 1 4 1
/csp playerboost Steve 2 30m
/csp editor set spawners.zombie.price 2500
/csp editor set spawners.iron_generator.enabled true
/csp settings set limits.per-chunk 16
```

## Player controls and permissions

Right-click a spawner with a tagged spawner item to combine it. Right-click with a spawn egg to convert it; otherwise the management menu opens. Mine it with a Silk Touch pickaxe to collect it. Sneaking while collecting preserves the whole mixed stack when enabled.

Regular players receive `customspawners.use`, `.place`, `.break`, `.stack`, `.omni`, `.shop`, `.stats`, `.upgrade`, `.convert`, `.boost`, `.rewards`, `.silk` and `.craft` by default. Each name uses the `customspawners` prefix. Set these nodes to false to restrict a feature.

`customspawners.admin` defaults to operators and includes administration commands and bypasses. Bypass permissions are `customspawners.bypass.protection`, `.limits`, `.silk` and `.cost`. A cost bypass also bypasses XP/item charges. Creative players are exempt from charges unless `creative.charge` is enabled.

Per-type overrides use `customspawners.<action>.<type-id>` for applicable placement, shop, crafting, reward, upgrade, conversion, collection and administrative change actions. An explicitly assigned per-type node takes precedence over its general action node. Use lowercase catalog IDs, for example `customspawners.place.zombie` or `customspawners.change.zombie`. Recipes require a player permission context; automatic Crafter production is blocked.

## Configuration

- **`config.yml`** controls stacking, performance budgets, economy, boosts, rewards, limits, pickup and protections.
- **`spawners.yml`** defines types, prices, ordered tiers, conditions, outputs, drops and recipes. `defaults.type` and `defaults.tier` provide inherited values; maps merge recursively and lists replace inherited lists.
- **`messages.yml`** contains configurable English chat messages. GUI text is English.
- **`state.yml`** contains placed spawners, ownership, mixed stacks, successful cycles, boosts and kill counts. It is generated data; stop the server before restoring a backup.

A tier can contain outputs such as:

```yaml
outputs:
  - kind: ENTITY
    value: ZOMBIE
    chance: 100
    amount: 1
  - kind: ITEM
    value: IRON_INGOT
    chance: 25
    amount: 2
```

All probabilities are percentages from 0 to 100. Tier delays are inclusive random server ticks. Output is a random tier quantity multiplied by its stack, plus active bonuses, within work and entity caps. Conditions are evaluated per component so an unsuitable component does not stop every other component of an Omni spawner. A positive `spawn-limit` limits successful cycles; zero means unlimited.

`spawning.amount-expression` defaults to `{RAND}`. It supports `{RAND}`, `{STACK_SIZE}`, `+ - * / % ^` and parentheses. A component evaluates once per cycle, floors the nonnegative result, then multiplies by its stack before adding boosts. No JavaScript or arbitrary code is executed.

Command generators execute administrator-configured console commands and support `{player}`, `{world}`, `{x}`, `{y}`, `{z}`, `{type}`, `{tier}` and `{amount}`. Commands omit the leading slash. `{player}` requires the owner to be online. See [Spawning](docs/SPAWNING.md) for exact output and scheduling semantics.

Money operations require the selected economy provider. PlayerPoints rounds a transaction upward to whole points. Set `economy.provider: none` to disable paid economy operations while retaining free features. Boost currency accepts `MONEY`, `XP` or an item material such as `DIAMOND`.

## Integrations and API

| Integration | Behavior |
| --- | --- |
| Vault + economy plugin | Money purchases, upgrades, conversions and collection costs |
| PlayerPoints | Alternative integer points currency |
| WorldGuard | Region build permission and session bypass checks |
| GriefPrevention | Native trust and build checks |
| Native Paper displays | Holograms without an external plugin |

Installed supported protection hooks fail closed when their APIs cannot be used. Ordinary placement/break/spawn events remain available to other plugins. Additional protection or logging systems can listen to cancellable `SpawnerActionEvent` actions: `ACCESS`, `PLACE`, `BREAK`, `STACK`, `UPGRADE`, `CONVERT`, `BOOST`, `DROP`, `SPAWN`. `SpawnerService` is registered with Bukkit's service manager. Use the API only on the main server thread.

This is an independent implementation. Legacy binary APIs, proprietary item data, old database formats and every historical third-party adapter are not drop-in interfaces. The behavior analysis and intentional fixes are recorded in [Reconstruction](docs/RECONSTRUCTION.md). Original artifacts, decompiled analysis, downloaded runtimes and test worlds are excluded from Git.

## Development

The source separates models, configuration, persistence, spawning, services, hooks, listeners, commands and menus. Read [AI maintenance guide](docs/agents.md) for the module map and invariants. Run `./mvnw verify` before submitting a change. CI checks both endpoint API versions and publishes a build artifact.

Created and maintained by **Lino**. Licensed under [MIT](LICENSE).
