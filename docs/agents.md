# AI maintenance guide

## Start here

Read this file, pom.xml and the target module. Use rg with a path. Never scan .tools/, Plugin originale/, target/, dist/, runtime/ or .git/ by default. .ignore and .rgignore keep large evidence and generated files out of searches; .gitignore keeps them out of commits. GitHub repository: lino9999/CustomSpawners.

Read docs/COMPATIBILITY.md for the previously completed verification. Test sources, fixtures, raw reports and temporary servers were explicitly removed by the maintainer after verification. Do not recreate them unless requested.

## Rules

Author Lino. English source names, messages, README and documentation. No code comments. Java 21 bytecode, public Bukkit/Paper API only; no NMS or CraftBukkit internals. Target Paper 1.21.11 through 26.3; 26.x servers need Java 25. Never claim a version is runtime-tested without a saved result. Never copy analyzed source or bundled dependencies into this project.

## Modules

- model: positions, immutable catalog records, boosts and mutable main-thread placed state.
- config: validated catalog and settings, English messages.
- storage: indexed repository and atomic persistent snapshots.
- spawn: bounded scheduler, conditions, output execution and visual lifecycle.
- service: item encoding, transactions, economy, access, player progress and crafting.
- listener: event adapters; respect cancellations and validate mutations again on the main thread.
- command and gui: thin entry points into services; never trust names or inventory titles as identity.
- api: cancellable extension events and service access.

## Invariants

All Bukkit world, inventory and domain mutations run on the main server thread. Async persistence receives detached data only, with one ordered writer and atomic replace. Validate configuration before publishing it. Never overwrite unreadable storage. No force-loaded chunks. Stack counts are positive and bounded. Costs must be finite and nonnegative. Check permission, access, catalog validity, limits, inventory capacity and cancellable events before consuming money/items/levels. A rejected operation has no charge or partial mutation. Refund an external economy charge if final delivery fails. Tagged spawner items carry type/tier/count in PDC, never lore. Unknown PDC is rejected. Expired boosts never produce output. Entity caps include boosts. GUIs cancel clicks and drags and recheck access at click time. Never trust cancelled placement, break, spawn or explosion events.

## Build and checks

Use ./mvnw verify or mvnw.cmd verify with JDK 21+. The local development JDK and evidence may be in .tools/ (ignored). CI verifies compilation and packaging against four API versions. The current repository has no automated test sources or fixtures. Widen compatibility claims only after appropriate verification and preserve the maintainer's cleanup preference.

## Evidence and scope

docs/RECONSTRUCTION.md records static behavior findings and deliberate fixes. It is a behavior specification, not implementation source. Raw REA/CFR output remains local and ignored. Update README command/permission/config tables with user-facing changes. Update this module map if adding a subsystem. Keep legacy plugin names out of runtime metadata and commands.

SpigotMC prose lives in docs/spigotmc/*.bbcode.txt. Icon masters and exports live in assets/spigotmc/. Keep release attachments and installation links aligned with the project version. Release artifacts belong in dist/ locally and GitHub Releases, not the Git source tree.
