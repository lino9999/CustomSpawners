# Verification

Run the unit suite with JDK 21 or newer:

```text
./mvnw verify
```

On Windows use `mvnw.cmd`. The Maven wrapper downloads Maven 3.9.11. The default dependency is Paper API 1.21.11 and the plugin targets Java 21 bytecode. To check the current 26.3 endpoint, use JDK 25:

```text
./mvnw verify -Dpaper.version=26.3.build.218-stable
```

The suite covers strict configuration validation, expression parsing, collision and chunk-boundary checks, YAML editor values, command duration and arity, stack/null/count invariants, chunk and owner indexes, boost expiry, kill counts, exact state decoding and idempotent economy refunds bound to their original provider.

## Real-server fixture

Source and instructions: [scripts/integration](../scripts/integration/README.md). This builds a separate test plugin; it is not bundled into CustomSpawners. Use dedicated loopback-only server directories with accepted server terms. The fixture creates its own test world and modifies test configuration.

Its first phase checks real server registries, catalog reload rollback, PDC items, entity/item/block/command outputs, output cancellation, redstone, entity caps including boosts, XP and Vault transactions, inventory rollback, chunk and hologram lifecycle, and state snapshots. A clean server restart runs a second phase against the state restored by CustomSpawners itself.

Test player and economy objects are explicit proxies. World, entity, block, item, event, registry and persistence behavior runs on a real Paper server. This separates API-level transaction checks from connected-client gameplay.

## Scope of claims

An API compilation proves source compatibility for that dependency. Server startup proves loading and enable lifecycle for that build. The fixture proves its recorded assertions only. These checks do not establish every Minecraft patch/fork, a full connected-client GUI session, production performance at every scale, or runtime interoperability with every WorldGuard/GriefPrevention/PlayerPoints version.

Protection adapters have been checked against their official APIs; their runtime integration requires the corresponding server plugins. Vault transaction tests use the local Vault plugin with a controlled in-memory economy provider.

For release validation, also exercise placement, both hands, stacking, Omni selection, money/XP tier progression, every shop quantity, crafting, conversion, pickup, explosions, mob loot and kill rewards from a connected client. Confirm protected claims/regions deny interactions and that no cancelled event consumes currency or items. Restore a backup only with the server stopped.
