# Compatibility

## Version 1.0.1 verification

On October 24, 2026, the patch passed compilation and packaging against Paper APIs 1.21.11, 26.1.2, 26.2 and 26.3, using Java 21 for the production JAR. These checks were repeated after adding the optional WildStacker API dependency, which is provided by WildStacker and is not bundled.

A real Mineflayer 4.39.0 client connected to isolated Paper 1.21.11-128 with WildStacker 2025.2-b178. As a non-operator, it checked all 89 shop entries over both pages, purchased a Cow spawner, inspected its native item mob data, placed it in survival, verified the block's native Cow type, observed actual Cow output without Pig or Armor Stand, and recovered the typed item directly into inventory with an unenchanted diamond pickaxe. An ordinary iron pickaxe preserved the block and produced the rejection message. The same flow passed with an old configuration missing the new pickup flags and explicitly setting `pickup.to-inventory: false`. Legacy Armor Stand and enabled item/block/command generator entries were present but absent from the shop.

Focused probes ran on real servers with these combinations:

| Paper | Java | WildStacker |
| --- | --- | --- |
| 1.21.11-128 | Temurin 21.0.12.1 | 2025.2-b178 |
| 26.3-218 | Temurin 25.0.4.1 | 2026.3, official stable Jenkins build 11 |

The final patch also started on Paper 26.3 without WildStacker, confirming that the optional API is not required on the server.

Both passed native item type, recovery of tile type/count/owner, early native-spawn cancellation, actual WildStacker merging of three generated mob units into one entity with logical amount three, and rejection of a different-tier merge. An enabled item generator was additionally checked on 1.21.11: its item had no native Pig fallback and remained excluded from the shop. The startup guard rejected WildStacker's conflicting default spawner settings while preserving CustomSpawners state.

Coexistence requires WildStacker's `spawners.enabled`, `spawners.spawners-override.enabled` and `spawners.listen-paper-pre-spawn-event` to be false, followed by a server restart. WildStacker owns entity/item stacks; CustomSpawners owns spawner blocks. WildStacker 2025.2-b178 itself fails to initialize on Paper 26.3 with `No enum constant ...ServerVersion.v26_3`; the official 2026.3 build initializes and passed the probes above. Download reference: [official WildStacker stable build 11](https://hub.bg-software.com/job/WildStacker%20-%20Stable%20Builds/11/). The 2026.3 JAR used for verification had SHA-256 `b0e981febc6d3e0da8a91943a703f71d9a3012b7742cacf87f037ba0dd5cc6f2`.

These results do not certify every WildStacker configuration, custom loot/economy combination, provider version or server fork. Existing WildStacker spawner stacks are not imported. Runtime probes are distinct from the connected-client flow, which ran only on 1.21.11. Temporary probe sources, client packages, reports and isolated servers are removed after verification as requested. Release JAR SHA-256: `eabaf570125e6c02c27fac5dcc47440bac004bdbf90f04765459e2c7554ca954`.

## Version 1.0.0 historical verification

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
