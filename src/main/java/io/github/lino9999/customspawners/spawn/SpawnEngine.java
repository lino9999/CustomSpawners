package io.github.lino9999.customspawners.spawn;

import io.github.lino9999.customspawners.api.SpawnerActionEvent;
import io.github.lino9999.customspawners.config.Messages;
import io.github.lino9999.customspawners.config.Settings;
import io.github.lino9999.customspawners.config.SpawnerCatalog;
import io.github.lino9999.customspawners.model.BlockPosition;
import io.github.lino9999.customspawners.model.PlacedSpawner;
import io.github.lino9999.customspawners.model.SpawnConditions;
import io.github.lino9999.customspawners.model.SpawnOutput;
import io.github.lino9999.customspawners.model.SpawnerTier;
import io.github.lino9999.customspawners.model.StackKey;
import io.github.lino9999.customspawners.service.BoostRegistry;
import io.github.lino9999.customspawners.storage.SpawnerRepository;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnEngine implements Listener {
    private static final int HARD_OUTPUT_LIMIT = 1024;
    private final JavaPlugin plugin;
    private final Settings settings;
    private final SpawnerCatalog catalog;
    private final SpawnerRepository repository;
    private final BoostRegistry boosts;
    private final Map<BlockPosition, PlacedSpawner> active = new HashMap<>();
    private final LinkedHashSet<BlockPosition> queue = new LinkedHashSet<>();
    private final SpawnSafety safety = new SpawnSafety();
    private final SpawnerVisuals visuals;
    private final NamespacedKey spawnedType;
    private final NamespacedKey spawnedTier;
    private BukkitTask task;
    private long tick;
    private long emissionRound;
    private long nextWarningTick;
    private int interval;
    private SpawnExpression amountExpression;

    public SpawnEngine(
            JavaPlugin plugin,
            Settings settings,
            SpawnerCatalog catalog,
            SpawnerRepository repository,
            BoostRegistry boosts) {
        this.plugin = plugin;
        this.settings = settings;
        this.catalog = catalog;
        this.repository = repository;
        this.boosts = boosts;
        this.visuals = new SpawnerVisuals(plugin, settings, catalog);
        spawnedType = new NamespacedKey(plugin, "spawned-type");
        spawnedTier = new NamespacedKey(plugin, "spawned-tier");
    }

    public void start() {
        stop();
        amountExpression = SpawnExpression.compile(settings.text("spawning.amount-expression"));
        interval = Math.clamp(settings.integer("spawning.tick-interval"), 1, 1200);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) discover(chunk);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::advance, interval, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        HandlerList.unregisterAll(this);
        visuals.clear();
        active.clear();
        queue.clear();
    }

    public void activate(PlacedSpawner spawner) {
        if (!loaded(spawner.position())) return;
        if (active.put(spawner.position(), spawner) == null) {
            queue.addLast(spawner.position());
            spawner.nextSpawnTick(tick + delay(spawner));
            repository.dirty();
        }
        refresh(spawner);
    }

    public void deactivate(BlockPosition position) {
        active.remove(position);
        queue.remove(position);
        visuals.remove(position);
    }

    public void discover(Chunk chunk) {
        visuals.clean(chunk);
        for (PlacedSpawner spawner :
                repository.inChunk(
                        new BlockPosition.ChunkPosition(
                                chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())))
            activate(spawner);
    }

    public void refresh(PlacedSpawner spawner) {
        if (!loaded(spawner.position()) || spawner.stacks().isEmpty()) return;
        Block block = spawner.position().location().getBlock();
        if (block.getState() instanceof CreatureSpawner tile) {
            if (tile.getSpawnCount() == 0 && tile.getMaxNearbyEntities() == 0) {
                tile.setSpawnCount(4);
                tile.setMaxNearbyEntities(6);
                tile.setDelay(200);
            }
            var tier = catalog.tier(spawner.first());
            if (tier != null) {
                tier.outputs().stream()
                        .filter(output -> output.kind() == SpawnOutput.Kind.ENTITY)
                        .findFirst()
                        .ifPresent(
                                output -> tile.setSpawnedType(EntityType.valueOf(output.value())));
            }
            tile.update(false, false);
        }
        visuals.update(spawner, pauseReason(spawner));
    }

    public boolean spawnNow(PlacedSpawner spawner) {
        if (!pauseReason(spawner).isEmpty()) return false;
        Budget budget = new Budget(HARD_OUTPUT_LIMIT);
        boolean success = spawn(spawner, budget);
        spawner.nextSpawnTick(tick + delay(spawner));
        repository.dirty();
        visuals.update(spawner, pauseReason(spawner));
        return success;
    }

    public String pauseReason(PlacedSpawner spawner) {
        if (!loaded(spawner.position())) return "Chunk is unloaded";
        Location location = center(spawner.position());
        if (location.getBlock().getType() != Material.SPAWNER) return "Spawner block is missing";
        if (spawner.stacks().isEmpty()) return "No spawner stacks";
        if (spawner.owner() == null && settings.bool("spawning.disable-natural"))
            return "Natural spawners are disabled";
        if (listed("blacklist.worlds", location.getWorld().getName())) return "World is disabled";
        if (settings.bool("spawning.redstone-disable") && location.getBlock().getBlockPower() > 0)
            return "Powered by redstone";
        String reason = "No enabled spawner types";
        for (StackKey key : spawner.stacks().keySet()) {
            var type = catalog.get(key.type());
            var tier = catalog.tier(key);
            if (type == null
                    || !type.enabled()
                    || tier == null
                    || listed("blacklist.types", key.type())) continue;
            if (tier.spawnLimit() > 0 && spawner.cycles() >= tier.spawnLimit()) {
                reason = "Spawn limit reached";
                continue;
            }
            reason = conditions(location, tier.conditions(), false);
            if (reason.isEmpty()) return "";
        }
        return reason;
    }

    private void advance() {
        tick += interval;
        int maxOperations =
                Math.clamp(settings.integer("spawning.max-operations-per-tick"), 1, 4096);
        Budget budget = new Budget(maxOperations);
        int checks = Math.min(queue.size(), maxOperations);
        for (int index = 0; index < checks && budget.remaining > 0; index++) {
            BlockPosition position = queue.isEmpty() ? null : queue.removeFirst();
            if (position == null) break;
            PlacedSpawner spawner = active.get(position);
            if (spawner == null) continue;
            if (!loaded(position) || repository.get(position) != spawner) {
                deactivate(position);
                continue;
            }
            if (position.location().getBlock().getType() != Material.SPAWNER) {
                repository.remove(position);
                deactivate(position);
                continue;
            }
            queue.addLast(position);
            if (spawner.nextSpawnTick() <= tick) {
                if (pauseReason(spawner).isEmpty()) spawn(spawner, budget);
                spawner.nextSpawnTick(tick + delay(spawner));
                repository.dirty();
                visuals.update(spawner, pauseReason(spawner));
            }
        }
    }

    private boolean spawn(PlacedSpawner spawner, Budget budget) {
        var action = new SpawnerActionEvent(spawner, null, SpawnerActionEvent.Action.SPAWN);
        Bukkit.getPluginManager().callEvent(action);
        if (action.isCancelled()
                || repository.get(spawner.position()) != spawner
                || !pauseReason(spawner).isEmpty()) return false;
        Location origin = center(spawner.position());
        boolean successful = false;
        long round = emissionRound++;
        var stacks = new ArrayList<>(spawner.stacks().entrySet());
        int offset = (int) (spawner.cycles() % Math.max(1, stacks.size()));
        for (int stackIndex = 0; stackIndex < stacks.size() && budget.remaining > 0; stackIndex++) {
            var stack = stacks.get((stackIndex + offset) % stacks.size());
            StackKey key = stack.getKey();
            var type = catalog.get(key.type());
            SpawnerTier tier = catalog.tier(key);
            if (type == null
                    || !type.enabled()
                    || tier == null
                    || listed("blacklist.types", key.type())
                    || tier.spawnLimit() > 0 && spawner.cycles() >= tier.spawnLimit()
                    || !conditions(origin, tier.conditions(), false).isEmpty()) continue;
            double evaluated;
            try {
                evaluated =
                        amountExpression.evaluate(
                                random(tier.minSpawn(), tier.maxSpawn()), stack.getValue());
            } catch (IllegalArgumentException ex) {
                warn(key, ex);
                continue;
            }
            long base =
                    (long)
                            Math.min(
                                    HARD_OUTPUT_LIMIT,
                                    Math.max(0, Math.floor(evaluated)) * stack.getValue());
            int amount =
                    (int)
                            Math.min(
                                    HARD_OUTPUT_LIMIT,
                                    base + boosts.extra(spawner, System.currentTimeMillis()));
            int nearby = nearbyEntities(origin, tier.conditions());
            for (int iteration = 0; iteration < amount && budget.remaining > 0; iteration++) {
                int outputOffset = (int) Math.floorMod(round + iteration, tier.outputs().size());
                for (int outputIndex = 0; outputIndex < tier.outputs().size(); outputIndex++) {
                    if (budget.remaining <= 0) break;
                    SpawnOutput output =
                            tier.outputs()
                                    .get((outputOffset + outputIndex) % tier.outputs().size());
                    if (ThreadLocalRandom.current().nextDouble(100) >= output.chance()) {
                        budget.remaining--;
                        continue;
                    }
                    int itemStackSize =
                            output.kind() == SpawnOutput.Kind.ITEM
                                    ? Material.valueOf(output.value()).getMaxStackSize()
                                    : 1;
                    int copies =
                            switch (output.kind()) {
                                case ENTITY, BLOCK -> output.amount();
                                case ITEM -> (output.amount() + itemStackSize - 1) / itemStackSize;
                                case COMMAND -> 1;
                            };
                    copies =
                            Math.min(
                                    copies,
                                    Math.max(
                                            1,
                                            budget.remaining
                                                    / (tier.outputs().size() - outputIndex)));
                    for (int copy = 0; copy < copies && budget.remaining > 0; copy++) {
                        budget.remaining--;
                        if (repository.get(spawner.position()) != spawner
                                || !loaded(spawner.position())
                                || origin.getBlock().getType() != Material.SPAWNER)
                            return successful;
                        if (output.kind() == SpawnOutput.Kind.ENTITY
                                && tier.conditions().maxEntities() > 0
                                && nearby >= tier.conditions().maxEntities()) break;
                        int itemAmount =
                                Math.min(itemStackSize, output.amount() - copy * itemStackSize);
                        if (output(spawner, key, tier, output, origin, itemAmount)) {
                            successful = true;
                            if (output.kind() == SpawnOutput.Kind.ENTITY) nearby++;
                        }
                    }
                }
            }
        }
        if (successful) {
            spawner.cycles(
                    spawner.cycles() == Long.MAX_VALUE ? Long.MAX_VALUE : spawner.cycles() + 1);
            repository.dirty();
            visuals.pulse(spawner);
        }
        return successful;
    }

    private boolean output(
            PlacedSpawner spawner,
            StackKey key,
            SpawnerTier tier,
            SpawnOutput output,
            Location origin,
            int itemAmount) {
        if (output.kind() == SpawnOutput.Kind.COMMAND) {
            try {
                return command(spawner, key, output, origin);
            } catch (RuntimeException exception) {
                warn(key, exception);
                return false;
            }
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            Location target = candidate(origin, tier.spawnRange());
            if (!safety.loaded(target) || !conditions(target, tier.conditions(), true).isEmpty())
                continue;
            if (!tier.conditions().spawnOn().isEmpty()
                    && !tier.conditions()
                            .spawnOn()
                            .contains(target.clone().subtract(0, 1, 0).getBlock().getType().name()))
                continue;
            try {
                if (output.kind() == SpawnOutput.Kind.ENTITY
                        && !safety.fits(target, EntityType.valueOf(output.value()))) continue;
                if (output.kind() == SpawnOutput.Kind.ITEM
                        && (!target.getBlock().isPassable() || target.getBlock().isLiquid()))
                    continue;
                if (output.kind() == SpawnOutput.Kind.BLOCK && !target.getBlock().getType().isAir())
                    continue;
                return switch (output.kind()) {
                    case ENTITY -> entity(key, tier, output, target);
                    case ITEM -> item(output, target, itemAmount);
                    case BLOCK -> block(output, target);
                    case COMMAND -> false;
                };
            } catch (IllegalArgumentException | IllegalStateException exception) {
                warn(key, exception);
                return false;
            }
        }
        return false;
    }

    private boolean entity(StackKey key, SpawnerTier tier, SpawnOutput output, Location location) {
        EntityType type = EntityType.valueOf(output.value());
        Entity entity =
                location.getWorld()
                        .spawnEntity(
                                location,
                                type,
                                SpawnReason.SPAWNER,
                                created -> {
                                    created.getPersistentDataContainer()
                                            .set(
                                                    spawnedType,
                                                    PersistentDataType.STRING,
                                                    key.type());
                                    created.getPersistentDataContainer()
                                            .set(
                                                    spawnedTier,
                                                    PersistentDataType.STRING,
                                                    key.tier());
                                    if (created instanceof LivingEntity living) {
                                        living.setAI(tier.ai() && !settings.bool("spawning.no-ai"));
                                        if (!tier.entityName().isBlank()) {
                                            living.setCustomName(Messages.color(tier.entityName()));
                                            living.setCustomNameVisible(true);
                                        }
                                        if (tier.onFire()) living.setFireTicks(200);
                                    }
                                    if (created instanceof Ageable ageable) {
                                        if (tier.baby()) ageable.setBaby();
                                        else ageable.setAdult();
                                    }
                                });
        if (!entity.isValid() || entity.isDead()) return false;
        if (!safety.clear(location.getWorld(), entity.getBoundingBox(), safety.aquatic(type))) {
            entity.remove();
            return false;
        }
        return true;
    }

    private boolean item(SpawnOutput output, Location target, int amount) {
        if (!target.getBlock().isPassable() || target.getBlock().isLiquid()) return false;
        Material material = Material.valueOf(output.value());
        ItemStack stack = new ItemStack(material, amount);
        var entity = target.getWorld().dropItem(target, stack);
        return entity.isValid() && !entity.isDead();
    }

    private boolean block(SpawnOutput output, Location target) {
        Block block = target.getBlock();
        if (!block.getType().isAir()) return false;
        BlockState result = block.getState();
        result.setType(Material.valueOf(output.value()));
        var event = new BlockFormEvent(block, result);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled()
                && block.getType().isAir()
                && event.getNewState().update(true, true);
    }

    private boolean command(
            PlacedSpawner spawner, StackKey key, SpawnOutput output, Location origin) {
        Player owner = spawner.owner() == null ? null : Bukkit.getPlayer(spawner.owner());
        String value = output.value();
        if (value.contains("{player}") && owner == null) return false;
        value =
                value.replace("{player}", owner == null ? "" : owner.getName())
                        .replace("{world}", origin.getWorld().getName())
                        .replace("{x}", String.valueOf(origin.getBlockX()))
                        .replace("{y}", String.valueOf(origin.getBlockY()))
                        .replace("{z}", String.valueOf(origin.getBlockZ()))
                        .replace("{type}", key.type())
                        .replace("{tier}", key.tier())
                        .replace("{amount}", String.valueOf(output.amount()));
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
    }

    private String conditions(Location location, SpawnConditions conditions, boolean candidate) {
        if (conditions.storm() && !location.getWorld().hasStorm()) return "Waiting for a storm";
        if (!candidate && conditions.nearbyPlayers() > 0) {
            double range = conditions.playerRange() * conditions.playerRange();
            int count = 0;
            for (Player player : location.getWorld().getPlayers()) {
                if (!player.isDead() && player.getLocation().distanceSquared(location) <= range)
                    count++;
                if (count >= conditions.nearbyPlayers()) break;
            }
            if (count < conditions.nearbyPlayers()) return "Waiting for nearby players";
        }
        if (location.getBlockY() < conditions.minY() || location.getBlockY() > conditions.maxY())
            return "Height condition is not met";
        int light = location.getBlock().getLightLevel();
        if (light < conditions.minLight() || light > conditions.maxLight())
            return "Light condition is not met";
        if (!conditions.biomes().isEmpty()) {
            NamespacedKey biome = location.getBlock().getBiome().getKey();
            if (!conditions.biomes().contains(biome.toString())
                    && !conditions.biomes().contains(biome.getKey().toUpperCase(Locale.ROOT)))
                return "Biome condition is not met";
        }
        return "";
    }

    private int nearbyEntities(Location origin, SpawnConditions conditions) {
        if (conditions.maxEntities() <= 0) return 0;
        double range = Math.min(128, Math.max(0.1, conditions.entityRange()));
        double squared = range * range;
        int count = 0;
        for (Entity entity : origin.getWorld().getNearbyEntities(origin, range, range, range)) {
            if (entity instanceof LivingEntity
                    && !(entity instanceof Player)
                    && !entity.isDead()
                    && entity.getLocation().distanceSquared(origin) <= squared) count++;
        }
        return count;
    }

    private Location candidate(Location origin, double configuredRange) {
        double range = Math.min(32, Math.max(0, configuredRange));
        double x = range == 0 ? 0 : ThreadLocalRandom.current().nextDouble(-range, range);
        double z = range == 0 ? 0 : ThreadLocalRandom.current().nextDouble(-range, range);
        return origin.clone().add(x, ThreadLocalRandom.current().nextInt(-1, 2), z);
    }

    private long delay(PlacedSpawner spawner) {
        long result = Long.MAX_VALUE;
        for (StackKey key : spawner.stacks().keySet()) {
            SpawnerTier tier = catalog.tier(key);
            if (tier != null) result = Math.min(result, random(tier.minDelay(), tier.maxDelay()));
        }
        if (result == Long.MAX_VALUE) result = 200;
        return Math.max(1, result + Math.max(0, settings.integer("spawning.extra-delay")));
    }

    private int random(int min, int max) {
        return (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1);
    }

    private boolean listed(String path, String value) {
        return settings.strings(path).stream().anyMatch(entry -> entry.equalsIgnoreCase(value));
    }

    private void warn(StackKey key, RuntimeException exception) {
        if (tick < nextWarningTick) return;
        nextWarningTick = tick + 600;
        plugin.getLogger()
                .warning(
                        "Could not produce "
                                + key.type()
                                + ":"
                                + key.tier()
                                + ": "
                                + exception.getMessage());
    }

    private boolean loaded(BlockPosition position) {
        World world = Bukkit.getWorld(position.world());
        return world != null && world.isChunkLoaded(position.x() >> 4, position.z() >> 4);
    }

    private Location center(BlockPosition position) {
        return position.location().add(0.5, 0, 0.5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        discover(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (PlacedSpawner spawner :
                repository.inChunk(
                        new BlockPosition.ChunkPosition(
                                event.getWorld().getUID(),
                                event.getChunk().getX(),
                                event.getChunk().getZ()))) deactivate(spawner.position());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        for (Chunk chunk : event.getWorld().getLoadedChunks()) discover(chunk);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        UUID world = event.getWorld().getUID();
        for (BlockPosition position :
                active.keySet().stream().filter(value -> value.world().equals(world)).toList())
            deactivate(position);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaSpawn(SpawnerSpawnEvent event) {
        if (repository.get(BlockPosition.of(event.getSpawner().getBlock())) != null
                || settings.bool("spawning.disable-natural")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!settings.bool("spawning.hostile-retaliate-only")
                || !(event.getEntity() instanceof Monster)
                || !event.getEntity()
                        .getPersistentDataContainer()
                        .has(spawnedType, PersistentDataType.STRING)
                || event.getTarget() == null) return;
        String reason = event.getReason().name();
        if (!reason.equals("TARGET_ATTACKED_ENTITY")
                && !reason.equals("TARGET_ATTACKED_NEARBY_ENTITY")
                && !reason.equals("TARGET_ATTACKED_OWNER")) event.setCancelled(true);
    }

    private static final class Budget {
        private int remaining;

        private Budget(int remaining) {
            this.remaining = remaining;
        }
    }
}
