# Isolated Paper integration fixture

Author: Lino.

This fixture runs against a real Paper server with the built CustomSpawners plugin. It creates `customspawners_smoke`, adds `smoke_*` catalog entries, changes a few test-server settings, and persists test state. Use a dedicated server directory.

Build the main plugin, then compile the fixture with Java 21 or newer:

```powershell
./scripts/integration/build.ps1 -JavaHome $env:JAVA_HOME
```

The output is `.tools/smoke/CustomSpawnersSmoke.jar`. Copy that file and `target/CustomSpawners-1.0.0.jar` into the isolated server's `plugins` directory. To exercise money transactions, also install Vault. The fixture registers an in-memory Vault economy service with the highest service priority; an additional economy plugin is unnecessary.

Start Paper with the JDK required by that Minecraft release. The fixture starts 40 ticks after its own enable callback. Inspect `plugins/CustomSpawnersSmoke/results.yml` and the console for `SMOKE phase1-complete-restart-required`. Every assertion records `PASS` or `FAIL`; missing Vault records the money-provider group as `SKIP`.

Stop the server normally and start the same directory again. The second run verifies state replay by CustomSpawners itself, rather than loading only a detached storage snapshot. Inspect `plugins/CustomSpawnersSmoke/results-restart.yml` and `SMOKE phase2-complete`. Keep both result files with the server version and Java version printed inside them.

To repeat the full first phase, stop the isolated server and remove only its `plugins/CustomSpawnersSmoke/replay.yml` marker. Existing fixture spawners in the fixture world are reset on the next first-phase run. The `/cssmoke` console command reports the current counts.

## Coverage

- Real registry-backed catalog parsing, generated mob coverage, deep tier defaults and immutable tier ordering.
- Invalid catalog edit, invalid disk reload and invalid settings edit preserve the active snapshot and the appropriate file.
- Mixed spawner PDC survives cloning and cosmetic changes; zero counts, duplicate entries and incorrect PDC types are rejected.
- Entity quantities, AI and baby flags, spawn identity, item quantity splitting, block quantities and command placeholder dispatch.
- Runtime spawn expression variables, stack multiplication, arithmetic-error rejection and the hard output limit.
- Additive location/player boosts, expiration and final nearby-entity caps.
- Cancelled extension spawn, creature spawn and block formation events produce no output or successful-cycle increments.
- Redstone pause, healthy native spawner counters and cancellation of a dispatched vanilla spawner event.
- XP upgrades, cancellation and held-item preservation using real server item stacks with explicit player/inventory proxies.
- Optional Vault service withdrawal rejection, successful charging, reentrant inventory changes and delivery failures with refunds.
- Real chunk unload/load behavior, unloaded output refusal, no forced reload and hologram recreation without duplicates. The fixture waits for temporary Paper chunk tickets to expire and retries unloading for a bounded interval before asserting failure.
- Atomic storage snapshot replay, followed by an actual server restart replay of world identity, owner, mixed quantities, successful-cycle count, location/player boosts and kill progress.

The fixture invokes manual `spawnNow` calls and uses a long automatic tick interval. Its vanilla suppression check dispatches the relevant server event and checks the tile state. It does not claim a connected-client gameplay session, complete GUI interaction, every protection-provider integration, or a timed vanilla spawning experiment with a nearby real player. Player transaction probes use strict proxies that reject unexpected method calls, so an unsupported path fails rather than silently returning a default.
