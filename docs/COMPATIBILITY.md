# Compatibility

CustomSpawners 1.0.0 targets Paper 1.21.11 through 26.3. Paper 1.21.11 requires Java 21 or newer; Paper 26.x requires Java 25 or newer. The plugin uses Java 21 bytecode.

Before cleanup, 131 automated checks passed. The same production implementation also passed 28 operational checks and an actual restart replay on each of these builds:

| Paper | Java |
| --- | --- |
| 1.21.11-128 | Temurin 21.0.12.1 |
| 26.1.1-29, historical alpha build | Temurin 25.0.4.1 |
| 26.1.2-74 | Temurin 25.0.4.1 |
| 26.2-134 | Temurin 25.0.4.1 |
| 26.3-218 | Temurin 25.0.4.1 |

Clean compilation also passed against APIs 1.21.11, 26.1.2, 26.2 and 26.3. Historical build verification: [GitHub Actions](https://github.com/lino9999/CustomSpawners/actions/runs/40049724539).

The operational checks covered registry-backed definitions, rejected configuration rollback, tagged items, entity/item/block/command outputs, additive boosts, entity caps, cancellation, redstone, XP/Vault transactions and refunds, chunk/hologram lifecycle, persistence and restart replay. Transaction probes used explicit player/economy proxies on real Paper servers.

These results do not certify every fork or patch, a complete connected-client GUI session, every provider version or every production load. Spigot server software and Folia are outside the supported target. The archived alpha build was a compatibility probe, not a deployment recommendation.

After verification, automated test sources, fixtures, raw reports and temporary servers were removed at the maintainer's request. The current CI workflow checks compilation and packaging. Runtime classes and configuration were not changed by that cleanup.

Release packaging after cleanup was compared with the previously verified JAR: all 62 runtime payload entries are byte-identical. Only embedded Maven project metadata changed when test dependencies were removed. Release JAR SHA-256: `c00421991c3da5c0fe3aa619af4e2d6820ba4330df40d9f1fabc9a6b8a23c04d`.
