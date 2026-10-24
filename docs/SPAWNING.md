# Spawning contract

`spawn/SpawnEngine` owns a synchronous scheduler and the set of loaded, registered spawners. Chunk and world events activate or deactivate records through the repository's chunk index. The scheduler never requests chunk loading. Missing spawner blocks are removed from storage when their active record is checked. Unloaded records remain saved.

`start()` registers listeners, discovers already loaded chunks, cleans stale tagged holograms and starts the task. `stop()` cancels the task, unregisters listeners, removes displays and clears runtime tracking. `refresh()` updates the tile's displayed entity and its hologram. Vanilla output is suppressed through the cancellable spawner event, without disabling native tile settings; spawners can resume vanilla operation after plugin removal. A registered tile with the earlier development suppression signature (spawn count 0 and maximum nearby entities 0) is repaired to count 4, nearby maximum 6 and delay 200. The old delay is not used to identify these tiles because vanilla decrements it. Other native settings are preserved. `refresh()` does not remove the record or reset its engine timing. `activate()` initializes a newly active record's timer; saved server tick deadlines are deliberately reset when a chunk is reactivated.

## Timing and work limits

`spawning.tick-interval` is measured in server ticks and accepts 1 through 1200. The active records rotate in insertion order. Each scheduler run checks at most `spawning.max-operations-per-tick` records and shares that many output attempts between them, with a hard runtime range of 1 through 4096. The first default run occurs after 10 ticks. Checks and emissions are separately bounded; the configured limit is not a wall-clock guarantee.

Each cycle chooses a random inclusive delay for each stacked tier, uses the shortest result and adds `spawning.extra-delay`. Paused and failed cycles schedule another check after this delay. A newly activated record also waits for its delay. `spawnNow()` respects all conditions and the spawn limit, bypasses the previous deadline and applies a new one; it has a hard budget of 1024 emissions.

For each stack, `spawning.amount-expression` evaluates with `{RAND}` sampled inclusively from the tier's minimum and maximum spawn amount and `{STACK_SIZE}` set to that component's count. The nonnegative, floored result is multiplied by the component's stack. The default `{RAND}` preserves simple random output. Supported arithmetic is `+ - * / % ^`, unary signs and parentheses; the parser has bounded length, nesting and operation counts and executes no scripts. Each component samples once, keeping evaluation cost independent of stack size. Invalid runtime arithmetic skips that component and produces a throttled warning. Active block and owner boost amounts are added after multiplication. Expired boosts contribute zero. The result is capped at 1024 before its outputs are evaluated. The shared emission budget remains the final bound, including output quantities. Unused work is not deferred as a backlog.

Stack processing rotates by the number of successful cycles. Output processing rotates between attempts. When output quantities exceed the remaining budget, each remaining output receives a share of that budget so that one large quantity cannot continually monopolize it.

## Outputs and limits

A tier can contain ENTITY, ITEM, BLOCK and COMMAND outputs together. Each output rolls its percentage chance once per cycle iteration. Zero always fails and 100 always succeeds. A failed roll consumes one attempt. A successful roll emits the configured entity or block quantity; item quantities split into stacks no larger than the material's maximum stack size. Each entity, block or dropped item stack consumes one attempt. Output quantities may be truncated by the work budget.

COMMAND dispatches once per successful roll from the console. Its `amount` is exposed through `{amount}`. Available replacements are `{player}`, `{world}`, `{x}`, `{y}`, `{z}`, `{type}`, `{tier}` and `{amount}`. `{player}` requires the spawner owner to be online. A command reporting failure does not count as a successful output.

The saved `cycles` counter increases once when any output succeeds in a cycle. The tier's positive `spawn-limit` compares against this counter; zero means unlimited. Failed conditions, cancelled events, failed commands and blocked placements do not advance it. A mixed spawner skips exhausted tiers while allowing other tiers to continue. Timing and successful cycles mark the repository dirty for persistence.

`max-entities` counts living entities other than players within a sphere around the spawner, using `entity-range`. Each successful entity increments the current count before another entity is attempted. Mixed tiers recalculate their own count. Item, block and command outputs continue when the entity cap is reached.

## Conditions and safe locations

Blacklisted worlds and types, disabled types, missing catalog entries, redstone power when configured, and natural managed records when `disable-natural` is enabled cannot produce output. Natural records have no owner. The engine cancels vanilla spawner output for registered blocks; `disable-natural` additionally cancels vanilla output from unregistered natural spawners.

Conditions check storm, minimum nearby player count, spherical player distance, height, light and biome. Height, light and biome must hold at the spawner and at the candidate location. Candidates use the configured horizontal range, limited to 32 blocks, and a vertical offset of -1, 0 or 1. `spawn-on` checks the material immediately below each candidate. The engine tries up to four candidates for each emission.

Candidate chunks must already be loaded and locations must be inside build height and the world border. Entity dimensions are obtained using detached API entity previews and cached by entity type. Every block touched by the proposed bounding box must be passable. Liquids are rejected except water for aquatic entity types; aquatic entities require water in their box. The final spawned entity's actual bounding box is checked again before it counts as success. The collision scan itself has a hard maximum of 4096 blocks. Item drops require a passable, nonliquid block; generated blocks replace air only.

## Events and metadata

One cancellable `SpawnerActionEvent` with action `SPAWN` precedes each cycle. The engine rechecks the registered spawner after the event. Mobs use `SpawnReason.SPAWNER`, allowing normal Bukkit/Paper creature spawn cancellation. Item drops use the normal item spawn path. Block output fires `BlockFormEvent` and rechecks that its original target is still air before applying the accepted state. The engine does not count invalid or cancelled entity and item spawns as successful.

Generated entities receive string PDC keys `customspawners:spawned-type` and `customspawners:spawned-tier` before creature spawn handlers run. The death and progression listeners use these tags. Tier AI, baby, fire and entity name settings are applied before spawn handlers run. Global `no-ai` overrides tier AI. `hostile-retaliate-only` permits attack-response target reasons for generated monsters and cancels proactive targeting.

## Visual lifecycle

Holograms use native `TextDisplay` entities with `customspawners:spawner-display` metadata. They are nonpersistent and are removed on deactivation, world unload and plugin stop. Chunk discovery removes stale tagged displays before creating replacements. No external hologram plugin is required. Displays show the first stack's configured type and tier, the total stack count and an English pause reason when available. Optional data-free tier particles play after successful cycles. Particles and holograms can be disabled independently.

Runtime compatibility and behavior claims require the server checks documented in `TESTING.md`; source compilation alone does not establish them.
