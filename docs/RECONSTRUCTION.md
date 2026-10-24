# Behavioral reconstruction evidence

This document records the original artifact's observed static contract. It is a specification for an independent implementation, not a copy of the original source, a runtime certification, or proof that every original feature worked correctly.

## Evidence and limits

- Original artifact SHA-256: `f411ef8f4497188152fb554f6685a506caf1ad1bfbe5fc5986da4770925741dc`.
- REA inventory evidence: `ev_88e2d95e6da68a8e447a1a87d4952d6cac3d9f9960b1517a9fe6d82b928c0dd6`.
- REA extraction evidence: `ev_d784b385e9735564b5bf486e49dbb54640eca6152fe7926a5bca38f25b2a37aa`.
- Supplemental inspection: CFR 0.152 static Java reconstruction under the ignored `.tools/original-analysis/com/songoda/epicspawners/` directory. CFR output is inferred from bytecode, not original source. Decompiled control flow, inferred types and exception handling may be imperfect.
- Scope: plugin-specific packages, excluding the bundled `core` and third-party packages. Hook internals, the low-level entity spawn algorithm and the full bundled loot editor were not inspected here.
- No original server runtime was exercised by this investigation. Compatibility, economic transaction behavior, event ordering and integrations require independent tests.

Class references below are relative to the inspected plugin package and share the evidence lineage above. Original command and permission names appear here only as evidence and migration references; the new public interface must use CustomSpawners branding.

## Feature contract and priorities

| Priority | User-visible capability | Static anchors |
| --- | --- | --- |
| P0 | Typed spawner items, placement, ownership, stacked quantities, persistence and safe pickup | `listeners/BlockListeners`, `spawners/spawner/PlacedSpawnerImpl`, `SpawnerStackImpl`, `SpawnerTierImpl` |
| P0 | Configurable entity generation, conditions, delays, redstone pause, natural spawner handling and world blacklist | `tasks/SpawnerSpawnTask`, `spawners/condition/*`, `spawners/spawner/option/SpawnOptionEntity`, `blacklist/BlacklistHandler` |
| P0 | Mixed-type Omni stacks with independent type/tier/quantity entries | `PlacedSpawnerImpl.stack`, `merge`, `getSpawnerStacks`, `gui/SpawnerTiersGui` |
| P1 | Ordered upgrade tiers, money or experience level charging, conversion by money or eggs | `SpawnerStackImpl.upgrade`, `convert`, `listeners/InteractListeners`, `gui/SpawnerOverviewGui`, `SpawnerConvertGui` |
| P1 | Shop browsing, quantity selection and purchases | `gui/SpawnerShopGui`, `SpawnerShopItemGui` |
| P1 | Location boosts purchased with items/money/levels and administrative player boosts | `gui/SpawnerBoostGui`, `commands/CommandBoost`, `boost/*` |
| P1 | Item, block and console command outputs, including combinations with entity outputs | `spawners/spawner/option/*`, `SpawnerTierImpl.reloadSpawnMethods` |
| P1 | Mob kill progress, random or goal rewards, statistics and custom per-tier mob loot | `listeners/EntityListeners`, `player/*`, `lootables/LootablesManager`, `gui/SpawnerStatsGui`, `EditorDropsGui` |
| P1 | Graphical definition and tier editor with creation, deletion, reordering, output and particle editing | `gui/Editor*.java` |
| P2 | Holograms, effect animations, display items, sounds, help and configurable GUI appearance | `EpicSpawners`, `tasks/AppearanceTask`, `SpawnerParticleTask`, `gui/*` |
| P2 | Crafting recipes, per-player/per-chunk limits, automatic combination, liquid repulsion and pickup costs | `EpicSpawners.enabledRecipe`, `listeners/BlockListeners`, `settings/Settings` |
| P2 | Integration events and service API, optional protection/economy/stacker/placeholder/logging hooks | `api/*`, plugin main class and listeners |

## Definitions and original defaults

### Definition fields

Each definition has an identifying name, active/custom flags, shop visibility/order/price, conversion availability and ratio, kill reward goal/chance, crafting switch/layout/ingredients, and an ordered list of tiers.

| Field | Generated/default value |
| --- | --- |
| Custom | `false` |
| Active | `true` |
| In shop | `true` |
| Shop order | `0` |
| Shop price | `1000.0` |
| Convertible | `true` |
| Conversion ratio | `45%` |
| Kill goal / chance | `0` / `0.0`, meaning use global values |
| Craftable | `false` |
| Recipe layout | `AAAABAAAA` |
| Recipe ingredients | `A, IRON_BARS` and `B, SPAWN_EGG` |

Conversion price is the destination definition's first-tier economy cost times the configured percentage, then multiplied by the converted stack quantity. It is not a percentage of shop price.

The manager generates one tier for each eligible default mob. There is no evidence of three automatic upgrade tiers. Additional tiers are administrator-created/configured. `SpawnerDataImpl.addDefaultTier` and `SpawnerManagerImpl.processDefaultSpawner` are different initialization paths: the former creates an empty-output tier with economy cost `1000.0`; the latter creates a mob output tier with economy cost `10.0`.

### Generated mob tier

| Field | Value |
| --- | --- |
| ID / display name | `Tier_1` / friendly mob name |
| Entity outputs | The corresponding entity type |
| Other outputs | Empty |
| Allowed spawn blocks | Resolved through the bundled entity compatibility helper; exact per-mob mappings are outside this inspection |
| Spawn on fire | `false` |
| Pickup cost | `0.0` |
| Pickaxe damage | `1` |
| Upgrade economy cost | `10.0` |
| Upgrade level cost | `2` |
| Tick rate | `800:200`, interpreted as maximum:minimum |
| Lifetime spawn limit | `-1` (disabled) |
| Display item | `AIR`, meaning use a mob/default icon |
| Persistent effect | `HALO` |
| Effect particle | `REDSTONE` |
| Entity spawn particle | `SMOKE` |
| Spawner spawn particle | `FIRE` |
| Density | `NORMAL` |
| Persistent effect only while boosted | `true` |

Particle effect choices are `NONE`, `HALO`, `TARGET`. Density enum values carry `(spawner, entity, effect)` counts: LIGHT `(8,2,1)`, NORMAL `(15,5,3)`, EXCESSIVE `(21,9,7)`, MAD `(30,13,15)`.

### Exact condition semantics

| Condition | Original check | Generated mob default |
| --- | --- | --- |
| Biomes | Spawner block biome is in configured set; ALL bypasses lookup | ALL; SLIME only SWAMP |
| Height | Spawner Y is inclusive between min and max | `-64:320` on modern versions; SLIME `50:70` |
| Light | DARK means combined block light `<=7`; LIGHT means `>7`; BOTH always passes | Monster classes DARK, all others BOTH |
| Storm | Pass unless storm-only is true, then world must have a storm | `false` |
| Nearby entities | Valid non-player, non-armor-stand living entities, counting stacker size; count must be strictly less than max | Max `6`, search box radii `8x4x8` |
| Nearby players | Player entities in an axis-aligned cube with each radius equal to distance; count at least amount | `16:1` |

An optional setting skips the nearby entity cap for a spawner's first spawn. The EpicAnchors integration can count nearby anchors as players using a weight; negative weight bypasses the condition when the chunk has an anchor, zero contributes none, positive contributes `nearby anchors * weight`. Default weight is `1`.

All conditions on all entries of a mixed stack are checked together. One failing entry pauses the entire block. This is an observed coupling, not necessarily the best design for the rewrite.

## Generation and scheduling

`SpawnerSpawnTask` runs synchronously, initially after 50 ticks and subsequently every configured custom tick period (`10` by default). It skips missing worlds, blacklisted worlds and unloaded chunks. Invalid/missing blocks are removed from management. Natural spawners can be disabled. Conditions gate the scheduler. It subtracts the task period from delay and attempts spawning below zero.

The configured delay is chosen from minimum inclusive to maximum exclusive plus extra ticks, with fixed values supported. For mixed entries the original chooses the pair with the highest `max + min`, so the slower tier controls the block. Default is 200..799 ticks. Powered blocks pause when redstone deactivation is enabled.

Each populated output category runs once per eligible tier. Categories coexist rather than choosing one category. The underlying entity spawn selection/collision behavior is delegated to bundled core and is not established here.

- Entities: evaluate the configured equation once per unit in the stack, sum integer results, clamp to nearby capacity, then add all boost amounts. Defaults: equation `{RAND}`, random configuration `1:4`. The original Java RNG uses upper exclusive bounds, producing 1..3 rather than the documented 1..4. `{STACK_SIZE}` is also available. Created entities receive type/tier metadata; plugin spawn events can cancel each creation; fire lasts 160 ticks; optional no-AI mode disables awareness. mcMMO metadata is applied when present. Spawned UUIDs are tracked as unnatural for rewards.
- Items: for each repetition equal to stack quantity plus additive boost amount, drop every configured item stack slightly above the spawner with small random velocity. Preserve full item metadata. Empty/AIR items are skipped.
- Blocks: for each such repetition, attempt each configured material at random offsets from -3 to +3 in each dimension, replacing AIR only. The original permits up to 251 candidate attempts per material.
- Commands: for each such repetition, run every configured command as console. Case-insensitive placeholders: `@x`, `@y`, `@z` random local coordinates; `@w` world; `@p` nearby player; `@n` placing player. Original `@p` picks the first player found in search-box enumeration, despite naming its helper nearest-player. Missing player skips commands that require `@p`.

Spawn counters and an optional lifetime spawn limit are persisted. The original limit uses first-tier limit and `spawnCount * numberOfEntries > limit`, then destroys the block with effects. Counter bugs in block/command paths are listed below and should not become a new contract.

## Placement, stacking and removal

- Typed items carry definition, tier and internal quantity metadata. Original NBT keys are `data`, `tier`, `size`; display-name parsing is a legacy fallback.
- Placement requires a type permission, permitted world, chunk/player limits and non-cancelled API event. Store owner UUID, location, entries and generated ID; configure visual entity/display and initial delay.
- Total stack cap is `5` by default. Right-clicking with a spawner while not sneaking combines it with the block. Matching tiers merge quantities; a different tier/type requires Omni enabled and permission. Excess quantity is returned as an item. Original direct stacking also checks placing owner.
- Mixed entries retain type, tier and quantity. Overview shows an entry selector for multi-entry blocks and a direct overview for single-entry blocks.
- Breaking normally removes one unit from the first entry. Sneaking with the configured option or forcing whole-stack drops removes that entry's entire quantity. Other entries remain. Final removal destroys the physical block and state.
- Pickup requires the configured silk-touch level and type permission, or the no-silk-drop bypass permission. Natural and placed chance settings are separate; both default to 100%. There are settings for placed-only drops and direct-to-inventory delivery; safe overflow must drop leftovers.
- Pickaxe damage comes from tier configuration; an exemption permission bypasses extra damage.
- Pickup cost can require a second break within 300 ticks (15 seconds). A natural-only-charge switch exempts placed blocks.
- Explosion immunity is optional. TNT/Creeper drop chances default to 100%. Intended preservation includes all mixed entries and quantities. The original loses upgraded tier information by creating first-tier drops; do not reproduce this defect.
- Automatic nearby combination is disabled with radius `0` by default. It can combine into a nearby block or deny placement inside a configured radius. Chunk/player count limits default to `-1` (unlimited); numeric permission can override player maximum.
- Liquid repulsion defaults to radius `1` and affects flow/bucket placement near spawners. The original also removes nearby water/lava during placement; this destructive side effect should be reconsidered.
- Vanilla managed-spawner entity events remove vanilla output to avoid double spawning. Anvil interaction with spawner items is denied by the original.

## Progression, shop, boosts and rewards

Upgrades move one selected entry to the next ordered tier. The next tier defines price. The original multiplies price by total block quantity, including other Omni entries. Money or experience levels can be enabled separately. Creative charging is configurable. A cancellable change event precedes the mutation; matching entries merge after upgrade/conversion. The rewrite should explicitly document any improved per-entry cost rule.

The shop filters active, shop-visible and permitted definitions; displays icons/lore; supports pagination and 1..64 purchase quantity with +/-1, +/-10 and set1/set64. It charges `shop price * amount`, delivers first-tier items and drops inventory overflow. It requires an economy provider.

Conversion GUI filters active, convertible and permitted destinations. Conversion changes an entry to the destination's first tier and charges its configured conversion price times entry quantity. Administrative targeted conversion refuses multi-entry Omni blocks. Egg conversion consumes eggs equal to the block quantity; destination-specific permission and protection checks apply; the previous mob may be returned as a spawn egg. The original affects only its first entry despite counting the whole block, an ambiguity to correct explicitly.

Boosts are additive output bonuses in the inspected implementation, despite settings referring to multipliers. Location and player-owner boosts can apply together and sum. GUI duration options are 5, 15, 30 and 60 minutes; selectable amount starts at 1 and defaults to max 5. Cost is `ceil(rate * minutes * amount)`, default `DIAMOND:2`; ECO and XP (levels) are alternate payment types. Administrative player boosts support duration or effectively permanent expiry. No global boost command/type was found in this plugin scope.

Kill rewards default to enabled, goal `100`, random chance `2.5%`, action-bar alert every `10`, excluding plugin-spawned entities unless configured otherwise. Per-type goal/chance override global values when nonzero. A player kill increments persistent type progress. Either the goal or an independent random roll grants the first-tier spawner. Reaching the goal clears that type's counter; a random reward before goal does not. UltimateStacker whole-stack kills can count the whole entity quantity. The statistics GUI lists current counts and goals.

Custom mob loot is keyed by definition+tier and applies only to plugin-tagged mobs. A configured lootable replaces/processes death drops. Babies return no custom loot; mob-loot gamerule is honored; looting, burning/cooked output, charged-creeper state, weapon/killer and projectile shooter are passed to the bundled loot engine. Sheep wool is recolored or omitted if sheared. The full loot rule schema and bundled editor were not inspected, so exact parity cannot be claimed from this document alone.

## Administration and interfaces

| Original command shape | Function | Original permission |
| --- | --- | --- |
| `es give <player/all> <type/random> [tier] [amount] [stack-size]` | Give typed items to online players | `epicspawners.admin.give` |
| `es openshop <player/all>` | Open another player's shop | `epicspawners.admin.openshop` |
| `es boost <player> <amount> [duration]` | Set/add player-owner bonus | `epicspawners.admin.boost` |
| `es editor` | Definition/tier editor | `epicspawners.admin.editor` |
| `es settings` | General plugin configuration GUI | `epicspawners.admin` |
| `es reload` | Reload configuration and language | `epicspawners.admin.reload` |
| `es change <type>` | Convert targeted spawner | admin or `epicspawners.change.<type>` / wildcard |
| `es spawn` | Force targeted spawn | `epicspawners.admin.spawn` |
| `spawnershop` | Open own shop | `epicspawners.openshop` |
| `spawnerstats` | Show kill progress | `epicspawners.stats` |

Targeted commands search up to 200 blocks. Give supports random enabled type and optional tier matching, and online `all`. Tab completion includes players, type IDs, tiers and numeric values. The new naming/aliases/permissions must be different and fully documented in the public README.

Other original permission families: overview, per-type place/shop/convert/egg/silkdrop, per-tier stack, Omni, kill counter, no-silk-drop, no-pick-damage and numeric placement limit. Some original checks are inconsistently capitalized or point to tier rather than type; the rewrite should establish one consistent lowercase scheme.

The player overview shows block eligibility/failed conditions, allowed spawn surfaces, stack quantity, lifetime spawn count, upgrade prices/actions, conversion, boost status and paged help. It refreshes while open. Mixed-entry selection also shows total spawn statistics.

The graphical editor supports:

- Definition selector with filters and creation of a custom named definition.
- Adding/deleting tiers, deleting a definition when its last tier disappears, changing tier order and remapping existing entries.
- Tier display name and held-item display icon.
- Entity list, full item stacks, block list and console command list, with add/remove/save actions.
- Spawn-on-fire, economy/level costs, pickup cost, tick range and definition-level reward overrides.
- Definition shop visibility and shop price.
- Effect, particle channels, boosted-only effects and density.
- Enabling, editing, resetting and disabling custom tier loot through the bundled loot editor.
- Saving definitions on editor close; changes must survive restart and update loaded entries.

Not every definition field has a confirmed GUI control. Conditions, recipe layout, conversion flags and other fields remain file-configurable in observed paths. Do not infer controls merely because the data model has a setter.

## Persistence and optional integrations

The original schema stores placed blocks `(id, spawn_count, nullable placed_by, world, x,y,z)`, entries `(spawner_id, data_type, amount, tier)`, player boosts `(player, amount, end_time)`, location boosts `(world,x,y,z,amount,end_time)`, and kills `(player,entity_type,count)`. Migration 2 adds tier with default `Tier_1`. Migration 3 attempts to keep maximum duplicate kill count and add uniqueness for player/type. Definitions use `Spawners.yml`; blacklist uses `blacklist.yml`; custom loot uses `lootables/`; messages use locale files. The rewrite may choose a cleaner versioned storage format, with explicit import support if promised.

The plugin exposes manager/data/tier/player/boost APIs and cancellable access, place, break, drop, change and individual entity-spawn events. Optional observed integrations include economy provider hooks (Vault/Reserve/PlayerPoints), hologram providers, protection hooks, entity stackers, EpicAnchors, UltimateStacker, PlaceholderAPI (GUI lore), mcMMO and CoreProtect logging. Their presence is not proof of compatibility with current provider versions.

## Defects and risky quirks to avoid

These are static findings requiring targeted regression checks, not bugs to preserve for parity.

1. Natural spawner serialization dereferences nullable owner UUID; natural right-click stacking also dereferences owner.
2. Explosion handling assumes a managed spawner, parses an empty chance for other explosion types, resets tier in drops and may leave stored state after a failed drop roll.
3. Blacklist reload reads the file but does not replace its cached name list.
4. Player boosts are not expiry-filtered in the inspected application path; only location boost expiry is handled there.
5. Boosts can exceed the nearby cap because they are added after clamping. Missing nearby-cap condition initializes maximum to zero.
6. RAND excludes the configured high endpoint. Invalid equations/ranges/materials/particles/conditions can throw while loading or ticking.
7. Coordinate command substitution loops 151 times, increments spawn counters on attempts and can dereference absent owner. Its player selection is not actually nearest.
8. Block output increments spawn count for failed location attempts, inflating lifetime totals and limits.
9. Failed XP upgrades can still execute persistence logic using the next tier. Money operations do not consistently check withdrawal success or provide rollback.
10. Natural-only/drop chances are applied inconsistently when direct-to-inventory is enabled; some give/natural pickup paths discard overflow.
11. Original GUI controls for kill chance mutate kill goal, and one definition-level kill goal control mutates shop price.
12. Loot reroll chance uses integer division `looting / (looting + 1)` and always yields zero for nonnegative looting.
13. Autosave setting says seconds but is multiplied by `60 * 20`, effectively minutes.
14. Force-combine scans all blocks, can combine without equivalent ownership/protection checks, and may consume/refund items inconsistently on cancelled place.
15. Generic hostile-target suppression affects all entities rather than just managed spawned mobs. Tracking and caches can retain entries indefinitely.
16. Mixed-tier global condition/delay coupling and total-block upgrade charging are surprising. Preserve them only if documented as intentional design.
17. Core world access must remain on the server thread; persist immutable snapshots asynchronously with atomic replacement and ordered writes. The original mixes mutable objects, asynchronous DB writes and reloads.
18. Preserve stable IDs during tier reorder/deletion, reject duplicate/invalid definitions and negative counts/prices, and validate a replacement configuration before swapping active state.
19. Cancelled events and failed payment must produce no item consumption, state mutation, reward, block generation or duplicate vanilla spawn. Validate all inventory click/drag/hotbar/shift paths.

## Verification boundary

A complete rewrite should independently verify loaded and unloaded chunks; natural and owned blocks; maximum/overflow stacks; mixed entries; cancelled events; inventory overflow; payment failures; restart persistence; invalid reload rollback; timed expiry; all output categories; caps and lifetime limits; crafting; protection paths; custom loot; and GUI edits. Advertise only versions actually tested. Static API compilation alone does not verify every Minecraft release, especially future releases.

## Rewrite integration boundary

The new `hooks` package supports Vault and PlayerPoints reflectively, without bundling either API. Vault signatures were verified against the locally supplied `../Dipendenze/Vault.jar` using `javap`; debit and credit inspect `EconomyResponse.transactionSuccess()`. Selecting Vault requires both Vault and a registered enabled economy service. PlayerPoints `getAPI`, `look(UUID)`, `take(UUID,int)` and `give(UUID,int)` were checked against the [official plugin class](https://github.com/Rosewood-Development/PlayerPoints/blob/master/src/main/java/org/black_ixx/playerpoints/PlayerPoints.java) and [official API](https://github.com/Rosewood-Development/PlayerPoints/blob/master/src/main/java/org/black_ixx/playerpoints/PlayerPointsAPI.java). PlayerPoints is integral, so fractional prices and matching refunds round upward. Nonfinite/negative amounts and point overflow are rejected. Failed external transactions block the paid action; the caller must also refund successful withdrawals if later delivery fails.

Protection currently combines WorldGuard BUILD eligibility with its actual session bypass and GriefPrevention build trust at the affected location. WorldGuard's [official protection-query guide](https://worldguard.enginehub.org/en/latest/developer/regions/protection-query/) requires separate bypass handling; [RegionQuery](https://github.com/EngineHub/WorldGuard/blob/master/worldguard-core/src/main/java/com/sk89q/worldguard/protection/regions/RegionQuery.java) provides the reflected test signature. GriefPrevention's [official allowBuild API](https://github.com/TechFortress/GriefPrevention/blob/master/src/main/java/me/ryanhamshire/GriefPrevention/GriefPrevention.java) supplies its world/trust policy. These API checks were static, not an integration runtime test. With hooks enabled, an installed supported plugin that is disabled or fails API inspection blocks access and logs once; an absent plugin adds no check. Ownership is enforced separately by the spawner service. Other providers, including Lands, BentoBox, Towny, PlotSquared, Reserve, legacy hologram plugins and external mob stackers, are not implemented by these two bridges and must not be advertised as verified integrations.

Scoped Java compilation of `Settings`, `EconomyBridge` and `ProtectionBridge` with `--release 21` succeeded using the locally cached Paper dependencies. Live provider behavior, reload/replacement and server protection checks still require isolated server verification.

The rewrite now binds each successful debit receipt to its original provider object, account, exact charged amount and refund method. A configuration/provider reload during an external callback cannot redirect its refund to another currency. Receipts attempt refund at most once, including after uncertain provider failure. Shop delivery rolls back the complete inventory addition and refunds the full receipt if delivery is incomplete; it never calculates a separately rounded PlayerPoints partial refund. Mutation guards recheck repository identity, catalog references, configuration identity, stack contents, inventory, levels, permissions, access and charge policy around extension/economy callbacks.

State decoding validates the raw spawner list and each row rather than using permissive list conversion. It rejects duplicate positions/entry keys, malformed UUIDs/sections, invalid identifiers, fractional/overflowing integers and negative counters before publishing loaded state. Thirty-seven focused tests passed for model mutation atomicity, null rejection, chunk boundaries, world/owner indexes, expiry, detached progress snapshots, strict state decoding and refund idempotency. Those tests were executed through an isolated JUnit platform runner without a server; they do not establish live economy-provider compatibility.
