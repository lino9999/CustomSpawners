# CustomSpawners 1.0.1

- Restrict the shop and purchase validation to mob-only definitions across every tier. Exclude Armor Stand and item, block, command or mixed generators, even in existing catalogs.
- Generate defaults only for actual Bukkit mobs. Keep legacy definitions and saved spawners recoverable.
- Encode native spawner block state on items and synchronize block type, stack metadata and ownership. Typed spawners now retain their mob display instead of defaulting to Pig.
- Allow ordinary diamond and netherite pickaxes by default. Invalid tools preserve the spawner and display an English explanation. Return placed spawners to inventory by default, including old configurations; overflow drops safely.
- Detect WildStacker and prevent overlapping spawner management. Configure the three WildStacker options below before restarting. Entity and dropped-item stacking may remain enabled.
- Hand generated mobs to WildStacker's public stacking API and reject merges between different CustomSpawners types or tiers. Cancel native Paper spawner attempts before WildStacker can modify existing entity stacks.

```yaml
spawners:
  enabled: false
  spawners-override:
    enabled: false
  listen-paper-pre-spawn-event: false
```

Replace the previous CustomSpawners JAR while the server is stopped. Do not delete your configuration or state. To change an existing Armor Stand spawner, aim at it and run `/csp change pig` with administrator permission, or choose another mob ID.

See `docs/COMPATIBILITY.md` for verification scope.
