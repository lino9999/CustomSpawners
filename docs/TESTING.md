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

## Recorded results: 24 October 2026

All **131 unit tests** passed with zero skipped tests. Clean builds passed against Paper APIs `1.21.11-R0.1-SNAPSHOT`, `26.1.2.build.74-stable`, `26.2.build.134-stable` and `26.3.build.218-stable`. The corresponding four-job [GitHub Actions run passed](https://github.com/lino9999/CustomSpawners/actions/runs/40049581652).

The same production JAR, built against the minimum API with Java 21 bytecode, passed all 28 first-phase integration checks and the actual-restart replay check on each server below. Reports record exact server/Java identities and individual outcomes.

| Paper build | Java | Integration | Restart replay | Reports |
| --- | --- | --- | --- | --- |
| 1.21.11-128 | Temurin 21.0.12.1 | 28 passed | 1 passed | [First phase](verification/paper-1.21.11.yml), [restart](verification/paper-1.21.11-restart.yml) |
| 26.1.1-29 (historical alpha build) | Temurin 25.0.4.1 | 28 passed | 1 passed | [First phase](verification/paper-26.1.1.yml), [restart](verification/paper-26.1.1-restart.yml) |
| 26.1.2-74 | Temurin 25.0.4.1 | 28 passed | 1 passed | [First phase](verification/paper-26.1.2.yml), [restart](verification/paper-26.1.2-restart.yml) |
| 26.2-134 | Temurin 25.0.4.1 | 28 passed | 1 passed | [First phase](verification/paper-26.2.yml), [restart](verification/paper-26.2-restart.yml) |
| 26.3-218 | Temurin 25.0.4.1 | 28 passed | 1 passed | [First phase](verification/paper-26.3.yml), [restart](verification/paper-26.3-restart.yml) |

Production artifact SHA-256: `849499418894a1ac453658ccdf2a7d2d0d404724a06331d2d4d2ec0969c2bf46`.

Integration fixtures and downloaded servers/runtimes stay outside the plugin JAR. All fixture servers were stopped cleanly after the final replay. Existing user server worlds were not modified. The archived alpha build is a compatibility probe, not a recommendation to deploy an outdated server.

## Real-server fixture

Source and instructions: [scripts/integration](../scripts/integration/README.md). This builds a separate test plugin; it is not bundled into CustomSpawners. Use dedicated loopback-only server directories with accepted server terms. The fixture creates its own test world and modifies test configuration.

Its first phase checks real server registries, catalog reload rollback, PDC items, entity/item/block/command outputs, output cancellation, redstone, entity caps including boosts, XP and Vault transactions, inventory rollback, chunk and hologram lifecycle, and state snapshots. A clean server restart runs a second phase against the state restored by CustomSpawners itself.

Test player and economy objects are explicit proxies. World, entity, block, item, event, registry and persistence behavior runs on a real Paper server. This separates API-level transaction checks from connected-client gameplay.

## Scope of claims

An API compilation proves source compatibility for that dependency. Server startup proves loading and enable lifecycle for that build. The fixture proves its recorded assertions only. These checks do not establish every Minecraft patch/fork, a full connected-client GUI session, production performance at every scale, or runtime interoperability with every WorldGuard/GriefPrevention/PlayerPoints version.

Protection adapters have been checked against their official APIs; their runtime integration requires the corresponding server plugins. Vault transaction tests use the local Vault plugin with a controlled in-memory economy provider.

For release validation, also exercise placement, both hands, stacking, Omni selection, money/XP tier progression, every shop quantity, crafting, conversion, pickup, explosions, mob loot and kill rewards from a connected client. Confirm protected claims/regions deny interactions and that no cancelled event consumes currency or items. Restore a backup only with the server stopped.
