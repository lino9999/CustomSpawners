package io.github.lino9999.customspawners.listener;

import io.github.lino9999.customspawners.CustomSpawnersPlugin;
import io.github.lino9999.customspawners.api.SpawnerActionEvent.Action;
import io.github.lino9999.customspawners.model.BlockPosition;
import io.github.lino9999.customspawners.model.StackKey;
import io.github.lino9999.customspawners.service.SpawnerItems;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnerListener implements Listener {
    private final CustomSpawnersPlugin plugin;

    private record Placement(BlockPosition position, Map<StackKey, Integer> stacks) {}

    private final Map<BlockPlaceEvent, Placement> pending = new IdentityHashMap<>();
    private final Set<BlockPosition> placing = new HashSet<>();
    private final NamespacedKey unnatural;
    private final NamespacedKey typeKey;
    private final NamespacedKey tierKey;

    public SpawnerListener(CustomSpawnersPlugin plugin) {
        this.plugin = plugin;
        unnatural = new NamespacedKey(plugin, "unnatural");
        typeKey = new NamespacedKey(plugin, "spawned-type");
        tierKey = new NamespacedKey(plugin, "spawned-tier");
    }

    public void discover(org.bukkit.Chunk chunk) {
        if (plugin.service().blocked(chunk.getWorld().getName(), null)) return;
        for (var tile : chunk.getTileEntities())
            if (tile instanceof CreatureSpawner
                    && !placing.contains(BlockPosition.of(tile.getBlock())))
                plugin.service().adopt(tile.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunk(ChunkLoadEvent event) {
        discover(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void world(WorldLoadEvent event) {
        for (var chunk : event.getWorld().getLoadedChunks()) discover(chunk);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void validatePlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;
        try {
            var block = event.getBlockPlaced();
            var incoming = plugin.service().placementData(event.getItemInHand(), block);
            var nearest =
                    plugin.service()
                            .nearest(
                                    block,
                                    plugin.settings().integer("stacking.force-combine-radius"));
            if (nearest != null) {
                event.setCancelled(true);
                if (plugin.settings().bool("stacking.deny-nearby")) {
                    plugin.messages().send(event.getPlayer(), "too-close");
                    return;
                }
                if (event.getHand() != EquipmentSlot.HAND
                        || !plugin.items().tagged(event.getItemInHand())) return;
                ItemStack expected = event.getItemInHand().clone();
                Bukkit.getScheduler()
                        .runTask(
                                plugin,
                                () -> {
                                    Player player = event.getPlayer();
                                    if (player.isOnline()
                                            && player.getInventory()
                                                    .getItemInMainHand()
                                                    .isSimilar(expected)) {
                                        try {
                                            plugin.service().stack(player, nearest);
                                        } catch (IllegalArgumentException ex) {
                                            plugin.messages()
                                                    .send(
                                                            player,
                                                            "invalid",
                                                            Map.of("reason", ex.getMessage()));
                                        }
                                    }
                                });
                return;
            }
            if (!plugin.service().canPlace(event.getPlayer(), block, incoming)) {
                event.setCancelled(true);
                return;
            }
            BlockPosition position = BlockPosition.of(block);
            if (!placing.add(position)) {
                event.setCancelled(true);
                return;
            }
            pending.put(event, new Placement(position, Map.copyOf(incoming)));
        } catch (IllegalArgumentException ex) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "invalid", Map.of("reason", ex.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void commitPlace(BlockPlaceEvent event) {
        Placement placement = pending.remove(event);
        if (placement == null) return;
        if (event.isCancelled()) {
            placing.remove(placement.position());
            return;
        }
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            try {
                                if (!event.isCancelled()
                                        && (!event.getBlockPlaced()
                                                        .getWorld()
                                                        .isChunkLoaded(
                                                                placement.position().x() >> 4,
                                                                placement.position().z() >> 4)
                                                || event.getBlockPlaced().getType()
                                                        == Material.SPAWNER)) {
                                    plugin.service()
                                            .placed(
                                                    event.getPlayer(),
                                                    event.getBlockPlaced(),
                                                    placement.stacks());
                                }
                            } finally {
                                placing.remove(placement.position());
                            }
                        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SPAWNER) return;
        if (placing.contains(BlockPosition.of(event.getBlock()))) {
            event.setCancelled(true);
            return;
        }
        var spawner = plugin.service().adopt(event.getBlock());
        if (spawner == null) return;
        event.setCancelled(true);
        plugin.service().pickup(event.getPlayer(), spawner);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.SPAWNER) return;
        if (placing.contains(BlockPosition.of(event.getClickedBlock()))) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        var spawner = plugin.service().adopt(event.getClickedBlock());
        if (spawner == null) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) return;
        try {
            ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
            if (plugin.items().tagged(held)) plugin.service().stack(event.getPlayer(), spawner);
            else if (held.getType().name().endsWith("_SPAWN_EGG")) {
                String type = held.getType().name().replace("_SPAWN_EGG", "");
                plugin.service().convert(event.getPlayer(), spawner, spawner.first(), type, true);
            } else plugin.menus().overview(event.getPlayer(), spawner);
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(event.getPlayer(), "invalid", Map.of("reason", ex.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosionProtect(EntityExplodeEvent event) {
        if (plugin.settings().bool("pickup.prevent-explosions"))
            event.blockList().removeIf(this::managed);
        else event.blockList().removeIf(block -> placing.contains(BlockPosition.of(block)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosionProtect(BlockExplodeEvent event) {
        if (plugin.settings().bool("pickup.prevent-explosions"))
            event.blockList().removeIf(this::managed);
        else event.blockList().removeIf(block -> placing.contains(BlockPosition.of(block)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent event) {
        String chance =
                event.getEntity() instanceof TNTPrimed
                        ? "pickup.tnt-chance"
                        : event.getEntity() instanceof Creeper
                                ? "pickup.creeper-chance"
                                : "pickup.other-explosion-chance";
        for (Block block : event.blockList()) {
            var spawner = plugin.service().adopt(block);
            if (spawner != null)
                plugin.service().explode(spawner, plugin.settings().decimal(chance));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explosion(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            var spawner = plugin.service().adopt(block);
            if (spawner != null)
                plugin.service()
                        .explode(
                                spawner,
                                plugin.settings().decimal("pickup.other-explosion-chance"));
        }
    }

    private boolean managed(Block block) {
        return block.getType() == Material.SPAWNER
                && (placing.contains(BlockPosition.of(block))
                        || plugin.service().adopt(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::managed)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::managed)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fluid(BlockFromToEvent event) {
        int radius = plugin.settings().integer("protection.repel-liquids-radius");
        if (radius < 1) return;
        Block to = event.getToBlock();
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    int bx = to.getX() + x, bz = to.getZ() + z;
                    if (!to.getWorld().isChunkLoaded(bx >> 4, bz >> 4)) continue;
                    if (plugin.repository()
                                    .get(
                                            new BlockPosition(
                                                    to.getWorld().getUID(), bx, to.getY() + y, bz))
                            != null) {
                        event.setCancelled(true);
                        return;
                    }
                }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void dispense(BlockDispenseEvent event) {
        if (!event.getItem().getType().name().endsWith("_SPAWN_EGG")) return;
        for (var face :
                java.util.List.of(
                        org.bukkit.block.BlockFace.NORTH,
                        org.bukkit.block.BlockFace.SOUTH,
                        org.bukkit.block.BlockFace.EAST,
                        org.bukkit.block.BlockFace.WEST,
                        org.bukkit.block.BlockFace.UP,
                        org.bukkit.block.BlockFace.DOWN)) {
            Block block = event.getBlock();
            int x = block.getX() + face.getModX(), z = block.getZ() + face.getModZ();
            if (block.getWorld().isChunkLoaded(x >> 4, z >> 4)
                    && managed(block.getRelative(face))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void creature(CreatureSpawnEvent event) {
        switch (event.getSpawnReason()) {
            case NATURAL, CHUNK_GEN, PATROL, RAID, REINFORCEMENTS -> {}
            default ->
                    event.getEntity()
                            .getPersistentDataContainer()
                            .set(unnatural, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) {
        var data = event.getEntity().getPersistentDataContainer();
        Player killer = event.getEntity().getKiller();
        String typeId = data.get(typeKey, PersistentDataType.STRING),
                tierId = data.get(tierKey, PersistentDataType.STRING);
        if (typeId != null && tierId != null) {
            var tier = plugin.catalog().tier(new StackKey(typeId, tierId));
            if (tier != null
                    && !tier.drops().isEmpty()
                    && !Boolean.FALSE.equals(
                            event.getEntity().getWorld().getGameRuleValue(GameRule.DO_MOB_LOOT))) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                int looting =
                        killer == null
                                ? 0
                                : Math.clamp(
                                        killer.getInventory()
                                                .getItemInMainHand()
                                                .getEnchantmentLevel(Enchantment.LOOTING),
                                        0,
                                        10);
                for (var drop : tier.drops()) {
                    if (!io.github.lino9999.customspawners.service.SpawnerService.chance(
                            drop.chance())) continue;
                    int amount =
                            ThreadLocalRandom.current().nextInt(drop.min(), drop.max() + 1)
                                    + looting;
                    Material material = Material.matchMaterial(drop.material());
                    if (material != null && !material.isAir()) {
                        while (amount > 0) {
                            int quantity = Math.min(amount, material.getMaxStackSize());
                            ItemStack item = new ItemStack(material, quantity);
                            var meta = item.getItemMeta();
                            if (!drop.name().isBlank())
                                meta.setDisplayName(
                                        io.github.lino9999.customspawners.config.Messages.color(
                                                drop.name()));
                            if (!drop.lore().isEmpty())
                                meta.setLore(
                                        drop.lore().stream()
                                                .map(
                                                        io.github.lino9999.customspawners.config
                                                                        .Messages
                                                                ::color)
                                                .toList());
                            item.setItemMeta(meta);
                            event.getDrops().add(item);
                            amount -= quantity;
                        }
                    }
                    event.setDroppedExp(
                            (int)
                                    Math.min(
                                            100000,
                                            (long) event.getDroppedExp() + drop.experience()));
                    if (!drop.command().isBlank() && killer != null)
                        Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                drop.command()
                                        .replace("{player}", killer.getName())
                                        .replace("{world}", event.getEntity().getWorld().getName())
                                        .replace(
                                                "{x}",
                                                String.valueOf(
                                                        event.getEntity()
                                                                .getLocation()
                                                                .getBlockX()))
                                        .replace(
                                                "{y}",
                                                String.valueOf(
                                                        event.getEntity()
                                                                .getLocation()
                                                                .getBlockY()))
                                        .replace(
                                                "{z}",
                                                String.valueOf(
                                                        event.getEntity()
                                                                .getLocation()
                                                                .getBlockZ())));
                }
            }
        }
        if (killer == null
                || !plugin.settings().bool("rewards.enabled")
                || !killer.hasPermission("customspawners.rewards")) return;
        if (!plugin.settings().bool("rewards.count-unnatural")
                && (typeId != null || data.has(unnatural, PersistentDataType.BYTE))) return;
        var type = plugin.catalog().get(event.getEntityType().name());
        if (type == null
                || !type.enabled()
                || plugin.service().blocked(killer.getWorld().getName(), type.id())
                || !plugin.service().typePermission(killer, "rewards", type.id())) return;
        long count = plugin.progress().increment(killer.getUniqueId(), type.id());
        int goal =
                type.killGoal() > 0
                        ? type.killGoal()
                        : plugin.settings().integer("rewards.kill-goal");
        double chance =
                type.killChance() > 0
                        ? type.killChance()
                        : plugin.settings().decimal("rewards.drop-chance");
        boolean reward =
                goal > 0 && count % goal == 0
                        || io.github.lino9999.customspawners.service.SpawnerService.chance(chance);
        if (reward) {
            var location = event.getEntity().getLocation();
            var proposed =
                    new io.github.lino9999.customspawners.model.PlacedSpawner(
                            BlockPosition.of(location.getBlock()), killer.getUniqueId());
            proposed.add(new StackKey(type.id(), type.firstTier().id()), 1);
            StackKey rewardKey = proposed.first();
            if (plugin.service().event(killer, proposed, Action.DROP)
                    && plugin.catalog().get(type.id()) == type
                    && proposed.stacks().equals(Map.of(rewardKey, 1))
                    && killer.hasPermission("customspawners.rewards")
                    && plugin.service().typePermission(killer, "rewards", type.id())
                    && !plugin.service().blocked(killer.getWorld().getName(), type.id())) {
                SpawnerItems.giveOrDrop(killer, plugin.items().create(rewardKey, 1));
                plugin.messages().send(killer, "reward", Map.of("type", type.name()));
            }
        } else if (goal > 0 && count % plugin.settings().integer("rewards.alert-interval") == 0)
            plugin.messages()
                    .send(
                            killer,
                            "progress",
                            Map.of("type", type.name(), "remaining", goal - count % goal));
    }
}
