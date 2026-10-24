package io.github.lino9999.customspawners.integration;

import io.github.lino9999.customspawners.CustomSpawnersPlugin;
import io.github.lino9999.customspawners.api.SpawnerActionEvent;
import io.github.lino9999.customspawners.model.BlockPosition;
import io.github.lino9999.customspawners.model.Boost;
import io.github.lino9999.customspawners.model.PlacedSpawner;
import io.github.lino9999.customspawners.model.StackKey;
import io.github.lino9999.customspawners.service.BoostRegistry;
import io.github.lino9999.customspawners.service.PlayerProgress;
import io.github.lino9999.customspawners.storage.SpawnerRepository;
import io.github.lino9999.customspawners.storage.StateStore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

public final class SmokePlugin extends JavaPlugin implements Listener {
    private static final UUID OWNER = UUID.fromString("7c8e99ab-f8b2-4bc3-87ec-a4f388214ee1");
    private static final String WORLD = "customspawners_smoke";
    private final YamlConfiguration results = new YamlConfiguration();
    private CustomSpawnersPlugin plugin;
    private World world;
    private boolean cancelAction;
    private boolean cancelCreature;
    private boolean cancelBlock;
    private SpawnerActionEvent.Action cancelledAction = SpawnerActionEvent.Action.SPAWN;
    private int passes;
    private int failures;
    private int commandOutputs;
    private String commandArguments;
    private PlacedSpawner persisted;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            try {
                                plugin =
                                        (CustomSpawnersPlugin)
                                                Bukkit.getPluginManager()
                                                        .getPlugin("CustomSpawners");
                                require(
                                        plugin != null && plugin.isEnabled(),
                                        "CustomSpawners must be enabled");
                                world =
                                        new WorldCreator(WORLD)
                                                .generator(
                                                        new ChunkGenerator() {
                                                            @Override
                                                            public boolean shouldGenerateNoise() {
                                                                return false;
                                                            }

                                                            @Override
                                                            public boolean shouldGenerateSurface() {
                                                                return false;
                                                            }

                                                            @Override
                                                            public boolean shouldGenerateCaves() {
                                                                return false;
                                                            }

                                                            @Override
                                                            public boolean
                                                                    shouldGenerateDecorations() {
                                                                return false;
                                                            }

                                                            @Override
                                                            public boolean shouldGenerateMobs() {
                                                                return false;
                                                            }

                                                            @Override
                                                            public boolean
                                                                    shouldGenerateStructures() {
                                                                return false;
                                                            }

                                                            @Override
                                                            public Location getFixedSpawnLocation(
                                                                    World created, Random random) {
                                                                return new Location(
                                                                        created, 8, 100, 8);
                                                            }
                                                        })
                                                .createWorld();
                                require(world != null, "Fixture world must load");
                                world.setSpawnFlags(false, false);
                                world.setTime(6000);
                                results.set("server", Bukkit.getVersion());
                                results.set("java", System.getProperty("java.version"));
                                results.set("plugin", plugin.getPluginMeta().getVersion());
                                if (Files.exists(getDataFolder().toPath().resolve("replay.yml")))
                                    replay();
                                else phaseOne();
                            } catch (Throwable error) {
                                failure("startup", error);
                                finish("aborted");
                            }
                        },
                        40);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cssmokeoutput")) {
            commandOutputs++;
            commandArguments = String.join(" ", args);
            return true;
        }
        sender.sendMessage(
                "Smoke results: "
                        + passes
                        + " passed, "
                        + failures
                        + " failed. See plugins/CustomSpawnersSmoke/results.yml.");
        return true;
    }

    private void phaseOne() throws Exception {
        results.set("phase", "first-run");
        plugin.editSetting("spawning.tick-interval", 1200);
        plugin.editSetting("spawning.amount-expression", "{RAND}");
        plugin.editSetting("spawning.holograms", true);
        plugin.editSetting("protection.hooks", false);
        for (int x = -1; x <= 17; x++) {
            for (int z = -1; z <= 1; z++) world.getChunkAt(x, z);
        }
        for (PlacedSpawner spawner : List.copyOf(plugin.repository().all())) {
            if (spawner.position().world().equals(world.getUID())) plugin.service().remove(spawner);
        }
        for (Entity entity : world.getEntities()) entity.remove();
        definitions();
        check(
                "catalog-runtime-mob-defaults",
                () -> {
                    for (EntityType type : EntityType.values()) {
                        if (type.isAlive() && type.isSpawnable())
                            require(
                                    plugin.catalog().get(type.name()) != null,
                                    "Missing mob: " + type);
                    }
                    require(
                            plugin.catalog().get("SMOKE ENTITY").id().equals("smoke_entity"),
                            "Normalized lookup failed");
                    require(
                            new java.util.ArrayList<>(
                                            plugin.catalog().get("smoke_entity").tiers().keySet())
                                    .equals(List.of("1", "2")),
                            "Tier ordering changed");
                    require(
                            plugin.catalog().get("smoke_entity").firstTier().upgradeMoney() == 10,
                            "Tier defaults were not inherited");
                    expectFailure(() -> plugin.catalog().get("smoke_entity").tiers().clear());
                });
        check(
                "catalog-invalid-edit-rollback",
                () -> {
                    byte[] before = Files.readAllBytes(plugin.catalog().file().toPath());
                    var previous = plugin.catalog().get("smoke_entity");
                    expectFailure(
                            () -> plugin.catalog().edit("spawners.smoke_entity.price", Double.NaN));
                    require(
                            plugin.catalog().get("smoke_entity") == previous,
                            "Failed edit published a replacement");
                    require(
                            Arrays.equals(
                                    before, Files.readAllBytes(plugin.catalog().file().toPath())),
                            "Failed edit modified the file");
                });
        check(
                "catalog-invalid-reload-rollback",
                () -> {
                    Path file = plugin.catalog().file().toPath();
                    byte[] before = Files.readAllBytes(file);
                    var previous = plugin.catalog().get("smoke_entity");
                    YamlConfiguration broken = new YamlConfiguration();
                    broken.load(file.toFile());
                    broken.set("spawners.smoke_entity.tiers.1.min-spawn", -1);
                    try {
                        Files.writeString(file, broken.saveToString(), StandardCharsets.UTF_8);
                        expectFailure(() -> plugin.catalog().reload());
                        require(
                                plugin.catalog().get("smoke_entity") == previous,
                                "Failed reload replaced the active catalog");
                    } finally {
                        Files.write(file, before);
                        plugin.catalog().reload();
                    }
                });
        check(
                "settings-invalid-edit-rollback",
                () -> {
                    Path file = plugin.getDataFolder().toPath().resolve("config.yml");
                    byte[] before = Files.readAllBytes(file);
                    Object previous = plugin.settings().configuration();
                    expectFailure(() -> plugin.editSetting("spawning.tick-interval", 0));
                    require(
                            previous == plugin.settings().configuration(),
                            "Invalid settings were published");
                    require(
                            Arrays.equals(before, Files.readAllBytes(file)),
                            "Invalid settings edit changed the file");
                });
        check(
                "mixed-pdc-roundtrip-and-validation",
                () -> {
                    LinkedHashMap<StackKey, Integer> stacks = new LinkedHashMap<>();
                    stacks.put(key("smoke_entity"), 2);
                    stacks.put(key("smoke_item"), 3);
                    ItemStack item = plugin.items().create(stacks);
                    require(
                            plugin.items().read(item.clone()).equals(stacks),
                            "Mixed item data changed");
                    var meta = item.getItemMeta();
                    meta.setDisplayName("A renamed item");
                    meta.setLore(List.of("Untrusted cosmetic lore"));
                    item.setItemMeta(meta);
                    require(
                            plugin.items().read(item).equals(stacks),
                            "Cosmetic changes altered encoded stacks");
                    NamespacedKey data = new NamespacedKey(plugin, "stacks");
                    meta.getPersistentDataContainer()
                            .set(data, PersistentDataType.STRING, "smoke_entity:1:0");
                    item.setItemMeta(meta);
                    expectFailure(() -> plugin.items().read(item));
                    meta.getPersistentDataContainer()
                            .set(
                                    data,
                                    PersistentDataType.STRING,
                                    "smoke_entity:1:2;smoke_entity:1:2");
                    item.setItemMeta(meta);
                    expectFailure(() -> plugin.items().read(item));
                    meta.getPersistentDataContainer().set(data, PersistentDataType.INTEGER, 1);
                    item.setItemMeta(meta);
                    expectFailure(() -> plugin.items().read(item));
                });
        PlacedSpawner entities = place(8, "smoke_entity", OWNER);
        check(
                "entity-output-flags-and-quantity",
                () -> {
                    require(
                            plugin.engine().spawnNow(entities),
                            "Entity output failed: " + plugin.engine().pauseReason(entities));
                    List<LivingEntity> spawned = living(entities);
                    require(spawned.size() == 2, "Expected two entities, found " + spawned.size());
                    for (LivingEntity entity : spawned) {
                        require(!entity.hasAI(), "AI flag was ignored");
                        require(
                                entity instanceof Ageable ageable && !ageable.isAdult(),
                                "Baby flag was ignored");
                        require(
                                "smoke_entity"
                                        .equals(
                                                entity.getPersistentDataContainer()
                                                        .get(
                                                                new NamespacedKey(
                                                                        plugin, "spawned-type"),
                                                                PersistentDataType.STRING)),
                                "Spawn identity missing");
                    }
                });
        check(
                "additive-boosts-clamped-by-nearby-cap",
                () -> {
                    living(entities).forEach(Entity::remove);
                    entities.boost(new Boost(3, Long.MAX_VALUE));
                    plugin.boosts().put(OWNER, new Boost(2, Long.MAX_VALUE));
                    require(
                            plugin.boosts().extra(entities, System.currentTimeMillis()) == 5,
                            "Boosts were not additive");
                    require(plugin.engine().spawnNow(entities), "Boosted output failed");
                    require(living(entities).size() == 2, "Boosted output exceeded the nearby cap");
                    living(entities).forEach(Entity::remove);
                    entities.boost(new Boost(3, 1));
                    plugin.boosts().put(OWNER, new Boost(2, 1));
                    require(
                            plugin.boosts().extra(entities, System.currentTimeMillis()) == 0,
                            "Expired boosts still apply");
                });
        check(
                "cancelled-spawn-action-produces-nothing",
                () -> {
                    long before = entities.cycles();
                    cancelAction = true;
                    try {
                        require(
                                !plugin.engine().spawnNow(entities),
                                "Cancelled action reported success");
                    } finally {
                        cancelAction = false;
                    }
                    require(
                            living(entities).isEmpty() && entities.cycles() == before,
                            "Cancelled action changed output or statistics");
                });
        check(
                "cancelled-creature-event-produces-nothing",
                () -> {
                    long before = entities.cycles();
                    cancelCreature = true;
                    try {
                        require(
                                !plugin.engine().spawnNow(entities),
                                "Cancelled creature event reported success");
                    } finally {
                        cancelCreature = false;
                    }
                    require(
                            living(entities).isEmpty() && entities.cycles() == before,
                            "Cancelled entity output changed statistics");
                });
        check(
                "redstone-pauses-output",
                () -> {
                    var block = entities.position().location().getBlock();
                    block.getRelative(1, 0, 0).setType(Material.REDSTONE_BLOCK);
                    try {
                        require(block.getBlockPower() > 0, "Fixture did not power the spawner");
                        require(
                                plugin.engine().pauseReason(entities).contains("redstone"),
                                "Redstone pause reason missing");
                        require(
                                !plugin.engine().spawnNow(entities),
                                "Powered spawner produced output");
                    } finally {
                        block.getRelative(1, 0, 0).setType(Material.AIR);
                    }
                });
        check(
                "vanilla-spawn-event-cancelled-with-healthy-native-tile",
                () -> {
                    CreatureSpawner tile =
                            (CreatureSpawner) entities.position().location().getBlock().getState();
                    require(
                            tile.getSpawnCount() > 0 && tile.getMaxNearbyEntities() > 0,
                            "Native tile was left frozen after plugin removal");
                    Entity preview =
                            world.createEntity(
                                    entities.position().location().add(0, 3, 0),
                                    org.bukkit.entity.Pig.class);
                    SpawnerSpawnEvent event = new SpawnerSpawnEvent(preview, tile);
                    Bukkit.getPluginManager().callEvent(event);
                    require(event.isCancelled(), "Vanilla spawner event was not cancelled");
                });
        PlacedSpawner items = place(40, "smoke_item", null);
        check(
                "item-output-quantity-and-legal-stack-sizes",
                () -> {
                    require(plugin.engine().spawnNow(items), "Item output failed");
                    int total = 0;
                    for (Entity entity :
                            world.getNearbyEntities(items.position().location(), 8, 8, 8)) {
                        if (entity instanceof Item item) {
                            require(
                                    item.getItemStack().getAmount()
                                            <= item.getItemStack().getMaxStackSize(),
                                    "Oversized dropped stack");
                            if (item.getItemStack().getType() == Material.IRON_INGOT)
                                total += item.getItemStack().getAmount();
                        }
                    }
                    require(total == 130, "Expected 130 iron ingots, found " + total);
                });
        PlacedSpawner blocks = place(72, "smoke_block", null);
        check(
                "block-output-quantity",
                () -> {
                    require(plugin.engine().spawnNow(blocks), "Block output failed");
                    require(generatedBlocks(blocks) == 3, "Block output amount was not honored");
                });
        clearGeneratedBlocks(blocks);
        check(
                "cancelled-block-form-preserves-air-and-statistics",
                () -> {
                    long before = blocks.cycles();
                    cancelBlock = true;
                    try {
                        require(
                                !plugin.engine().spawnNow(blocks),
                                "Cancelled block output reported success");
                    } finally {
                        cancelBlock = false;
                    }
                    require(
                            generatedBlocks(blocks) == 0 && blocks.cycles() == before,
                            "Cancelled block formation mutated state");
                });
        PlacedSpawner commands = place(104, "smoke_command", null);
        check(
                "command-output-dispatch-and-placeholders",
                () -> {
                    int before = commandOutputs;
                    require(plugin.engine().spawnNow(commands), "Command output failed");
                    require(
                            commandOutputs == before + 1,
                            "Command was not dispatched exactly once");
                    require(
                            "smoke_command 1 7".equals(commandArguments),
                            "Command placeholders were not expanded: " + commandArguments);
                });
        formulaChecks(commands);
        serviceTransactions();
        PlacedSpawner lifecycle = place(200, "smoke_command", null);
        check(
                "hologram-refresh-does-not-duplicate",
                () -> {
                    plugin.engine().refresh(lifecycle);
                    plugin.engine().refresh(lifecycle);
                    require(
                            displays(lifecycle) == 1,
                            "Expected one hologram after repeated refresh");
                    plugin.engine().discover(lifecycle.position().location().getChunk());
                    require(
                            displays(lifecycle) == 1,
                            "Rediscovery duplicated or lost the hologram");
                });
        List<Entity> previousDisplays = world.getNearbyEntities(lifecycle.position().location(), 3, 5, 3)
                .stream().filter(entity -> entity instanceof TextDisplay).toList();
        Bukkit.getScheduler().runTaskLater(this, () -> unloadFixture(lifecycle, previousDisplays, 0), 30);
    }

    private void unloadFixture(PlacedSpawner lifecycle, List<Entity> previousDisplays, int attempt) {
        int cx = lifecycle.position().x() >> 4, cz = lifecycle.position().z() >> 4;
        if (world.isChunkLoaded(cx, cz)) world.unloadChunk(cx, cz, true);
        if (world.isChunkLoaded(cx, cz) && attempt < 20) {
            Bukkit.getScheduler().runTaskLater(this, () -> unloadFixture(lifecycle, previousDisplays, attempt + 1), 10);
            return;
        }
        check("chunk-unload-removes-visuals-without-force-loading", () -> {
            require(!world.isChunkLoaded(cx, cz), "Fixture chunk remained loaded after 230 ticks");
            require(previousDisplays.stream().noneMatch(Entity::isValid), "Old holograms remain valid after unload");
            require(!plugin.engine().spawnNow(lifecycle), "Unloaded spawner produced output");
            require(!world.isChunkLoaded(cx, cz), "Spawn attempt force-loaded the chunk");
        });
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            check(
                                    "chunk-reload-restores-exactly-one-visual",
                                    () -> {
                                        world.getChunkAt(
                                                lifecycle.position().x() >> 4,
                                                lifecycle.position().z() >> 4);
                                        require(
                                                displays(lifecycle) == 1,
                                                "Chunk reload did not restore exactly one"
                                                    + " hologram");
                                    });
                            check("persistence-prepare", this::preparePersistence);
                            awaitPersistence(0);
                        },
                        10);
    }

    private void formulaChecks(PlacedSpawner commands) {
        check("spawn-formula-variables-and-stack-multiplication", () -> {
            commands.add(key("smoke_command"), 2);
            try {
                plugin.editSetting("spawning.amount-expression", "{RAND} + {STACK_SIZE}");
                int before = commandOutputs;
                require(plugin.engine().spawnNow(commands), "Formula output failed");
                require(commandOutputs - before == 12, "Expected (1 + 3) * 3 = 12 command dispatches, found " + (commandOutputs - before));
            } finally {
                commands.remove(key("smoke_command"), 2);
                plugin.editSetting("spawning.amount-expression", "{RAND}");
            }
        });
        check("spawn-formula-runtime-error-produces-no-output", () -> {
            try {
                plugin.editSetting("spawning.amount-expression", "1 / ({STACK_SIZE} - {STACK_SIZE})");
                int before = commandOutputs;
                long cycles = commands.cycles();
                require(!plugin.engine().spawnNow(commands), "Division by zero produced successful output");
                require(commandOutputs == before && commands.cycles() == cycles, "Invalid runtime arithmetic changed output or statistics");
            } finally {
                plugin.editSetting("spawning.amount-expression", "{RAND}");
            }
        });
        check("spawn-formula-output-bound-is-enforced", () -> {
            try {
                plugin.editSetting("spawning.amount-expression", "9999999");
                int before = commandOutputs;
                require(plugin.engine().spawnNow(commands), "Bounded formula output failed");
                require(commandOutputs - before == 1024, "Formula output did not honor the hard limit: " + (commandOutputs - before));
            } finally {
                plugin.editSetting("spawning.amount-expression", "{RAND}");
            }
        });
    }

    private void definitions() {
        plugin.catalog().edit("spawners.smoke_entity", definition("ENTITY", "PIG", 2, 2));
        plugin.catalog().edit("spawners.smoke_item", definition("ITEM", "IRON_INGOT", 130, 6));
        plugin.catalog().edit("spawners.smoke_block", definition("BLOCK", "GOLD_BLOCK", 3, 6));
        plugin.catalog()
                .edit(
                        "spawners.smoke_command",
                        definition("COMMAND", "cssmokeoutput {type} {tier} {amount}", 7, 6));
    }

    private Map<String, Object> definition(String kind, String value, int amount, int cap) {
        Map<String, Object> tier = new LinkedHashMap<>();
        tier.put("min-delay", 72000);
        tier.put("max-delay", 72000);
        tier.put("min-spawn", 1);
        tier.put("max-spawn", 1);
        tier.put("spawn-range", 3);
        tier.put("ai", false);
        tier.put("baby", true);
        tier.put(
                "conditions",
                Map.of(
                        "nearby-players",
                        0,
                        "min-light",
                        0,
                        "max-light",
                        15,
                        "min-y",
                        -64,
                        "max-y",
                        320,
                        "max-entities",
                        cap,
                        "spawn-on",
                        List.of(),
                        "biomes",
                        List.of()));
        tier.put(
                "outputs",
                List.of(Map.of("kind", kind, "value", value, "chance", 100, "amount", amount)));
        Map<String, Object> tiers = new LinkedHashMap<>();
        tiers.put("1", tier);
        tiers.put(
                "2",
                Map.of(
                        "outputs",
                        tier.get("outputs"),
                        "conditions",
                        tier.get("conditions"),
                        "upgrade-levels",
                        2));
        return Map.of(
                "name",
                "Smoke " + kind,
                "icon",
                "SPAWNER",
                "enabled",
                true,
                "price",
                10,
                "tiers",
                tiers);
    }

    private PlacedSpawner place(int x, String type, UUID owner) {
        world.getChunkAt(x >> 4, 0);
        var old = plugin.repository().get(new BlockPosition(world.getUID(), x, 100, 8));
        if (old != null) plugin.service().remove(old);
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++) {
                world.getBlockAt(x + dx, 98, 8 + dz).setType(Material.STONE, false);
                for (int y = 99; y <= 107; y++)
                    world.getBlockAt(x + dx, y, 8 + dz).setType(Material.AIR, false);
            }
        var block = world.getBlockAt(x, 100, 8);
        block.setType(Material.SPAWNER);
        PlacedSpawner spawner = new PlacedSpawner(BlockPosition.of(block), owner);
        spawner.add(key(type), 1);
        plugin.repository().put(spawner);
        plugin.engine().activate(spawner);
        return spawner;
    }

    private void serviceTransactions() {
        PlacedSpawner spawner = place(136, "smoke_entity", OWNER);
        SmokeActor actor = new SmokeActor(world, OWNER);
        check(
                "xp-insufficient-upgrade-does-not-mutate",
                () -> {
                    require(
                            !plugin.service()
                                    .upgrade(actor.player, spawner, key("smoke_entity"), true),
                            "Insufficient XP upgrade succeeded");
                    require(
                            actor.levels == 0
                                    && spawner.stacks().equals(Map.of(key("smoke_entity"), 1)),
                            "Failed XP upgrade changed state");
                });
        check(
                "cancelled-upgrade-does-not-charge-xp",
                () -> {
                    actor.levels = 10;
                    cancelAction = true;
                    cancelledAction = SpawnerActionEvent.Action.UPGRADE;
                    try {
                        require(
                                !plugin.service()
                                        .upgrade(actor.player, spawner, key("smoke_entity"), true),
                                "Cancelled upgrade succeeded");
                    } finally {
                        cancelAction = false;
                        cancelledAction = SpawnerActionEvent.Action.SPAWN;
                    }
                    require(
                            actor.levels == 10
                                    && spawner.stacks().equals(Map.of(key("smoke_entity"), 1)),
                            "Cancelled upgrade consumed XP or changed tiers");
                });
        check(
                "xp-upgrade-success-charges-exactly-once",
                () -> {
                    actor.levels = 10;
                    require(
                            plugin.service()
                                    .upgrade(actor.player, spawner, key("smoke_entity"), true),
                            "XP upgrade failed");
                    require(
                            actor.levels == 8
                                    && spawner.stacks()
                                            .equals(Map.of(new StackKey("smoke_entity", "2"), 1)),
                            "XP upgrade charged wrong amount or wrong tier");
                    spawner.replace(new StackKey("smoke_entity", "2"), key("smoke_entity"));
                });
        check(
                "cancelled-stacking-preserves-held-item",
                () -> {
                    actor.storage[0] = plugin.items().create(key("smoke_entity"), 1);
                    cancelAction = true;
                    cancelledAction = SpawnerActionEvent.Action.STACK;
                    try {
                        require(
                                !plugin.service().stack(actor.player, spawner),
                                "Cancelled stacking succeeded");
                    } finally {
                        cancelAction = false;
                        cancelledAction = SpawnerActionEvent.Action.SPAWN;
                    }
                    require(
                            spawner.total() == 1 && actor.storage[0].getAmount() == 1,
                            "Cancelled stacking consumed an item");
                });
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            results.set("tests.money-provider-transactions.status", "SKIP");
            results.set(
                    "tests.money-provider-transactions.detail",
                    "Install Vault in this isolated fixture server to exercise the reflected"
                        + " service provider");
            return;
        }
        check(
                "money-provider-failure-preserves-state",
                () -> {
                    SmokeEconomy economy = new SmokeEconomy(this);
                    plugin.editSetting("economy.provider", "Vault");
                    economy.allowWithdrawal = false;
                    require(
                            !plugin.service()
                                    .grow(actor.player, spawner, key("smoke_entity"), false),
                            "Rejected economy withdrawal succeeded");
                    require(
                            spawner.total() == 1 && economy.balance == 10000,
                            "Rejected withdrawal mutated state");
                    economy.allowWithdrawal = true;
                    require(
                            plugin.service()
                                    .grow(actor.player, spawner, key("smoke_entity"), false),
                            "Valid economy growth failed");
                    require(
                            spawner.total() == 2 && economy.balance == 9990,
                            "Successful growth charged the wrong amount");
                    double before = economy.balance;
                    economy.onWithdrawal =
                            () -> actor.storage[35] = new ItemStack(Material.DIAMOND);
                    require(
                            !plugin.service()
                                    .grow(actor.player, spawner, key("smoke_entity"), false),
                            "Reentrant inventory mutation was accepted");
                    require(
                            spawner.total() == 2
                                    && economy.balance == before
                                    && economy.refunds == 1,
                            "Reentrant transaction did not refund exactly once");
                    actor.storage[35] = null;
                    Arrays.fill(actor.storage, null);
                    actor.rejectDelivery = true;
                    require(
                            !plugin.service().buy(actor.player, "smoke_entity", "1", 1),
                            "Failed inventory delivery succeeded");
                    require(
                            economy.balance == before
                                    && economy.refunds == 2
                                    && Arrays.stream(actor.storage)
                                            .allMatch(value -> value == null),
                            "Failed delivery did not refund and restore inventory");
                    actor.rejectDelivery = false;
                });
    }

    private void preparePersistence() throws Exception {
        persisted = place(264, "smoke_entity", OWNER);
        persisted.add(key("smoke_item"), 3);
        persisted.add(key("smoke_entity"), 1);
        persisted.cycles(1234);
        persisted.boost(new Boost(3, Long.MAX_VALUE));
        plugin.boosts().put(OWNER, new Boost(2, Long.MAX_VALUE));
        plugin.progress().set(OWNER, "smoke_entity", 7);
        world.save();
        plugin.saveState();
    }

    private void awaitPersistence(int attempt) {
        try {
            SpawnerRepository replayed = new SpawnerRepository();
            PlayerProgress progress = new PlayerProgress();
            BoostRegistry boosts = new BoostRegistry();
            try (StateStore store = new StateStore(plugin)) {
                store.load(replayed, progress, boosts);
            }
            PlacedSpawner loaded = persisted == null ? null : replayed.get(persisted.position());
            require(
                    loaded != null
                            && loaded.cycles() == 1234
                            && loaded.stacks().equals(persisted.stacks()),
                    "Snapshot has not reached disk");
            require(
                    progress.get(OWNER).get("smoke_entity") == 7,
                    "Kill progress missing from saved state");
            require(boosts.get(OWNER).amount() == 2, "Player boost missing from saved state");
            pass("persistence-disk-roundtrip");
            YamlConfiguration marker = new YamlConfiguration();
            marker.set("world", world.getUID().toString());
            marker.set("x", persisted.position().x());
            marker.set("y", 100);
            marker.set("z", 8);
            Files.createDirectories(getDataFolder().toPath());
            marker.save(getDataFolder().toPath().resolve("replay.yml").toFile());
            finish("phase1-complete-restart-required");
        } catch (Throwable error) {
            if (attempt < 10)
                Bukkit.getScheduler().runTaskLater(this, () -> awaitPersistence(attempt + 1), 10);
            else {
                failure("persistence-disk-roundtrip", error);
                finish("phase1-failed");
            }
        }
    }

    private void replay() {
        results.set("phase", "restart-replay");
        check(
                "restart-replays-world-stack-owner-statistics-and-boosts",
                () -> {
                    YamlConfiguration marker = new YamlConfiguration();
                    marker.load(getDataFolder().toPath().resolve("replay.yml").toFile());
                    require(
                            world.getUID().toString().equals(marker.getString("world")),
                            "Saved world identity changed");
                    BlockPosition position =
                            new BlockPosition(
                                    world.getUID(),
                                    marker.getInt("x"),
                                    marker.getInt("y"),
                                    marker.getInt("z"));
                    PlacedSpawner loaded = plugin.repository().get(position);
                    require(loaded != null, "Persisted spawner was not replayed at plugin startup");
                    require(
                            OWNER.equals(loaded.owner()) && loaded.cycles() == 1234,
                            "Owner or spawn statistics changed");
                    require(
                            loaded.stacks()
                                    .equals(Map.of(key("smoke_entity"), 2, key("smoke_item"), 3)),
                            "Mixed stack quantities changed");
                    require(
                            loaded.boost().equals(new Boost(3, Long.MAX_VALUE)),
                            "Location boost changed");
                    require(
                            plugin.boosts().get(OWNER).equals(new Boost(2, Long.MAX_VALUE)),
                            "Player boost changed");
                    require(
                            plugin.progress().get(OWNER).get("smoke_entity") == 7,
                            "Player kill progress changed");
                    require(
                            position.location().getBlock().getType() == Material.SPAWNER,
                            "Saved world block is missing");
                    plugin.engine().discover(position.location().getChunk());
                    require(displays(loaded) == 1, "Replay created duplicate or missing holograms");
                });
        finish("phase2-complete");
    }

    private List<LivingEntity> living(PlacedSpawner spawner) {
        return world.getNearbyEntities(spawner.position().location(), 8, 8, 8).stream()
                .filter(entity -> entity instanceof LivingEntity && entity.isValid())
                .map(entity -> (LivingEntity) entity)
                .toList();
    }

    private long displays(PlacedSpawner spawner) {
        return world.getNearbyEntities(spawner.position().location(), 3, 5, 3).stream()
                .filter(
                        entity ->
                                entity instanceof TextDisplay
                                        && entity.getPersistentDataContainer()
                                                .has(
                                                        new NamespacedKey(
                                                                plugin, "spawner-display"),
                                                        PersistentDataType.STRING))
                .count();
    }

    private int generatedBlocks(PlacedSpawner spawner) {
        int count = 0;
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++)
                for (int y = 99; y <= 102; y++) {
                    if (world.getBlockAt(spawner.position().x() + x, y, spawner.position().z() + z)
                                    .getType()
                            == Material.GOLD_BLOCK) count++;
                }
        return count;
    }

    private void clearGeneratedBlocks(PlacedSpawner spawner) {
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++)
                for (int y = 99; y <= 102; y++) {
                    var block =
                            world.getBlockAt(
                                    spawner.position().x() + x, y, spawner.position().z() + z);
                    if (block.getType() == Material.GOLD_BLOCK) block.setType(Material.AIR);
                }
    }

    @EventHandler
    public void action(SpawnerActionEvent event) {
        if (cancelAction && event.getAction() == cancelledAction) event.setCancelled(true);
    }

    @EventHandler
    public void creature(CreatureSpawnEvent event) {
        if (cancelCreature && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER)
            event.setCancelled(true);
    }

    @EventHandler
    public void block(BlockFormEvent event) {
        if (cancelBlock) event.setCancelled(true);
    }

    private static StackKey key(String type) {
        return new StackKey(type, "1");
    }

    private void check(String name, Checked checked) {
        try {
            checked.run();
            pass(name);
        } catch (Throwable error) {
            failure(name, error);
        }
    }

    private void pass(String name) {
        passes++;
        results.set("tests." + name + ".status", "PASS");
        getLogger().info("PASS " + name);
    }

    private void failure(String name, Throwable error) {
        failures++;
        results.set("tests." + name + ".status", "FAIL");
        results.set("tests." + name + ".detail", error.toString());
        getLogger().log(Level.SEVERE, "FAIL " + name, error);
    }

    private void finish(String phase) {
        try {
            results.set("state", phase);
            results.set("passed", passes);
            results.set("failed", failures);
            Files.createDirectories(getDataFolder().toPath());
            results.save(
                    getDataFolder()
                            .toPath()
                            .resolve(
                                    results.getString("phase", "startup").equals("restart-replay")
                                            ? "results-restart.yml"
                                            : "results.yml")
                            .toFile());
            getLogger()
                    .info("SMOKE " + phase + ": " + passes + " passed, " + failures + " failed.");
        } catch (Exception error) {
            getLogger().log(Level.SEVERE, "Unable to save smoke results", error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Checked checked) throws Exception {
        try {
            checked.run();
        } catch (IllegalArgumentException | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Invalid operation did not fail");
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Exception;
    }
}
