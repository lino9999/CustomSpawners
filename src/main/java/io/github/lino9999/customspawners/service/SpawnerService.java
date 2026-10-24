package io.github.lino9999.customspawners.service;

import io.github.lino9999.customspawners.api.SpawnerActionEvent;
import io.github.lino9999.customspawners.api.SpawnerActionEvent.Action;
import io.github.lino9999.customspawners.config.Messages;
import io.github.lino9999.customspawners.config.Settings;
import io.github.lino9999.customspawners.config.SpawnerCatalog;
import io.github.lino9999.customspawners.hooks.EconomyBridge;
import io.github.lino9999.customspawners.hooks.ProtectionBridge;
import io.github.lino9999.customspawners.model.BlockPosition;
import io.github.lino9999.customspawners.model.Boost;
import io.github.lino9999.customspawners.model.PlacedSpawner;
import io.github.lino9999.customspawners.model.SpawnerType;
import io.github.lino9999.customspawners.model.StackKey;
import io.github.lino9999.customspawners.spawn.SpawnEngine;
import io.github.lino9999.customspawners.storage.SpawnerRepository;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

public final class SpawnerService {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final SpawnerCatalog catalog;
    private final SpawnerRepository repository;
    private final SpawnerItems items;
    private final SpawnEngine engine;
    private final EconomyBridge economy;
    private final ProtectionBridge protection;
    private final Messages messages;

    public SpawnerService(
            JavaPlugin plugin,
            Settings settings,
            SpawnerCatalog catalog,
            SpawnerRepository repository,
            SpawnerItems items,
            SpawnEngine engine,
            EconomyBridge economy,
            ProtectionBridge protection,
            Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.catalog = catalog;
        this.repository = repository;
        this.items = items;
        this.engine = engine;
        this.economy = economy;
        this.protection = protection;
        this.messages = messages;
    }

    public boolean permission(Player player, String action) {
        if (player.hasPermission("customspawners." + action)) return true;
        messages.send(player, "no-permission");
        return false;
    }

    public boolean typePermission(Player player, String action, String type) {
        String node = "customspawners." + action + "." + type;
        return player.isPermissionSet(node)
                ? player.hasPermission(node)
                : player.hasPermission("customspawners." + action);
    }

    public boolean event(Player player, PlacedSpawner spawner, Action action) {
        SpawnerActionEvent event = new SpawnerActionEvent(spawner, player, action);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    public boolean access(Player player, PlacedSpawner spawner, Action action) {
        if (!accessible(player, spawner)) return false;
        return checkedEvent(player, spawner, action);
    }

    private boolean accessible(Player player, PlacedSpawner spawner) {
        if (spawner == null || repository.get(spawner.position()) != spawner) return false;
        var location = spawner.position().location();
        if (location == null
                || !location.getWorld()
                        .isChunkLoaded(spawner.position().x() >> 4, spawner.position().z() >> 4)
                || location.getBlock().getType() != Material.SPAWNER) return false;
        if (blocked(location.getWorld().getName(), null)) return false;
        if (!player.hasPermission("customspawners.bypass.protection")
                && ((settings.bool("protection.owner-only")
                                && spawner.owner() != null
                                && !spawner.owner().equals(player.getUniqueId()))
                        || !protection.canAccess(player, location.getBlock()))) {
            messages.send(player, "protected");
            return false;
        }
        return true;
    }

    public boolean blocked(String world, String type) {
        return settings.strings("blacklist.worlds").stream().anyMatch(world::equalsIgnoreCase)
                || type != null
                        && settings.strings("blacklist.types").stream()
                                .anyMatch(type::equalsIgnoreCase);
    }

    public PlacedSpawner adopt(Block block) {
        BlockPosition position = BlockPosition.of(block);
        PlacedSpawner current = repository.get(position);
        if (current != null) return current;
        if (block.getType() != Material.SPAWNER || blocked(block.getWorld().getName(), null))
            return null;
        CreatureSpawner tile = (CreatureSpawner) block.getState();
        Map<StackKey, Integer> saved;
        java.util.UUID owner;
        try {
            saved = items.read(tile);
            owner = items.owner(tile);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger()
                    .warning("Invalid spawner block data at " + position + ": " + ex.getMessage());
            return null;
        }
        EntityType entity = tile.getSpawnedType();
        SpawnerType type = entity == null ? null : catalog.get(entity.name());
        if (saved.isEmpty() && (type == null || !type.enabled())) return null;
        PlacedSpawner spawner = new PlacedSpawner(position, owner);
        if (saved.isEmpty()) spawner.add(new StackKey(type.id(), type.firstTier().id()), 1);
        else saved.forEach(spawner::add);
        repository.put(spawner);
        engine.activate(spawner);
        engine.refresh(spawner);
        return spawner;
    }

    public Map<StackKey, Integer> placementData(ItemStack item, Block block) {
        Map<StackKey, Integer> stacks = items.read(item);
        if (!stacks.isEmpty()) return stacks;
        EntityType entity =
                block.getState() instanceof CreatureSpawner tile
                        ? tile.getSpawnedType()
                        : EntityType.PIG;
        SpawnerType type = entity == null ? null : catalog.get(entity.name());
        if (type == null || !type.enabled())
            throw new IllegalArgumentException("Unknown spawner item.");
        return Map.of(new StackKey(type.id(), type.firstTier().id()), 1);
    }

    public boolean canPlace(Player player, Block block, Map<StackKey, Integer> stacks) {
        if (!permission(player, "place")) return false;
        if (stacks.isEmpty()
                || stacks.entrySet().stream()
                        .anyMatch(
                                entry ->
                                        entry.getKey() == null
                                                || entry.getValue() == null
                                                || entry.getValue() < 1
                                                || catalog.tier(entry.getKey()) == null
                                                || !catalog.get(entry.getKey().type()).enabled()))
            return fail(player, "unknown-type");
        long total = stacks.values().stream().mapToLong(Integer::longValue).sum();
        if (total > settings.integer("stacking.max-stack")
                || stacks.size() > 1
                        && (!settings.bool("stacking.omni") || !permission(player, "omni")))
            return fail(player, "max-stack");
        for (StackKey key : stacks.keySet())
            if (blocked(block.getWorld().getName(), key.type())
                    || !typePermission(player, "place", key.type()))
                return fail(player, "disabled");
        if (!player.hasPermission("customspawners.bypass.protection")
                && !protection.canAccess(player, block)) return fail(player, "protected");
        if (!player.hasPermission("customspawners.bypass.limits")) {
            int chunk = settings.integer("limits.per-chunk"),
                    owner = settings.integer("limits.per-player");
            if (chunk >= 0 && repository.inChunk(BlockPosition.of(block).chunk()).size() >= chunk)
                return fail(player, "chunk-limit");
            if (owner >= 0 && repository.owned(player.getUniqueId()) >= owner)
                return fail(player, "player-limit");
        }
        PlacedSpawner proposed = new PlacedSpawner(BlockPosition.of(block), player.getUniqueId());
        stacks.forEach(proposed::add);
        Object configuration = settings.configuration();
        ItemStack[] inventory = inventorySnapshot(player);
        Map<StackKey, Object> tiers = new LinkedHashMap<>();
        stacks.keySet().forEach(key -> tiers.put(key, catalog.tier(key)));
        if (!event(player, proposed, Action.PLACE)
                || configuration != settings.configuration()
                || block.getType() != Material.SPAWNER
                || !stacks.equals(proposed.stacks())
                || !Arrays.equals(inventory, player.getInventory().getStorageContents())
                || !player.hasPermission("customspawners.place")) return false;
        for (StackKey key : stacks.keySet())
            if (catalog.tier(key) != tiers.get(key) || !typePermission(player, "place", key.type()))
                return false;
        if (!player.hasPermission("customspawners.bypass.protection")
                && !protection.canAccess(player, block)) return false;
        if (!player.hasPermission("customspawners.bypass.limits")) {
            int chunkLimit = settings.integer("limits.per-chunk"),
                    ownerLimit = settings.integer("limits.per-player");
            if (chunkLimit >= 0
                            && repository.inChunk(BlockPosition.of(block).chunk()).size()
                                    >= chunkLimit
                    || ownerLimit >= 0 && repository.owned(player.getUniqueId()) >= ownerLimit)
                return false;
        }
        return repository.get(BlockPosition.of(block)) == null;
    }

    public void placed(Player player, Block block, Map<StackKey, Integer> stacks) {
        boolean loaded = block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
        if (loaded && block.getType() != Material.SPAWNER
                || stacks.isEmpty()
                || stacks.keySet().stream().anyMatch(key -> catalog.tier(key) == null)) return;
        PlacedSpawner previous = repository.get(BlockPosition.of(block));
        if (previous != null) {
            if (previous.owner() != null) return;
            engine.deactivate(previous.position());
            repository.remove(previous.position());
        }
        PlacedSpawner spawner = new PlacedSpawner(BlockPosition.of(block), player.getUniqueId());
        stacks.forEach(spawner::add);
        repository.put(spawner);
        engine.activate(spawner);
        engine.refresh(spawner);
        messages.send(player, "placed");
    }

    public PlacedSpawner nearest(Block block, int radius) {
        if (radius <= 0) return null;
        PlacedSpawner best = null;
        double closest = (double) radius * radius;
        for (int x = (block.getX() - radius) >> 4; x <= (block.getX() + radius) >> 4; x++) {
            for (int z = (block.getZ() - radius) >> 4; z <= (block.getZ() + radius) >> 4; z++) {
                for (PlacedSpawner spawner :
                        repository.inChunk(
                                new BlockPosition.ChunkPosition(block.getWorld().getUID(), x, z))) {
                    if (spawner.position().equals(BlockPosition.of(block))) continue;
                    double distance =
                            Math.pow(block.getX() - spawner.position().x(), 2)
                                    + Math.pow(block.getY() - spawner.position().y(), 2)
                                    + Math.pow(block.getZ() - spawner.position().z(), 2);
                    if (distance <= closest) {
                        best = spawner;
                        closest = distance;
                    }
                }
            }
        }
        return best;
    }

    public boolean stack(Player player, PlacedSpawner spawner) {
        if (!permission(player, "stack") || !access(player, spawner, Action.ACCESS)) return false;
        ItemStack held = player.getInventory().getItemInMainHand();
        Map<StackKey, Integer> incoming = items.read(held);
        if (incoming.isEmpty()) return false;
        int extra = incoming.values().stream().mapToInt(Integer::intValue).sum();
        if ((long) spawner.total() + extra > settings.integer("stacking.max-stack"))
            return fail(player, "max-stack");
        for (StackKey key : incoming.keySet()) {
            if (blocked(player.getWorld().getName(), key.type())
                    || !typePermission(player, "place", key.type())
                    || !typePermission(player, "stack", key.type()))
                return fail(player, "disabled");
            if (!spawner.stacks().containsKey(key)
                    && (!settings.bool("stacking.omni") || !permission(player, "omni")))
                return fail(player, "max-stack");
        }
        if (!checkedEvent(player, spawner, Action.STACK)) return false;
        incoming.forEach(spawner::add);
        if (charge(player)) {
            ItemStack currentHeld = player.getInventory().getItemInMainHand();
            currentHeld.setAmount(currentHeld.getAmount() - 1);
        }
        changed(spawner);
        messages.send(player, "stacked", Map.of("amount", spawner.total()));
        return true;
    }

    public boolean pickup(Player player, PlacedSpawner spawner) {
        if (!permission(player, "break") || !access(player, spawner, Action.BREAK)) return false;
        StackKey first = spawner.first();
        var tier = catalog.tier(first);
        if (tier == null) return fail(player, "unknown-type");
        boolean whole =
                settings.bool("pickup.always-whole-stack")
                        || player.isSneaking() && settings.bool("pickup.sneak-whole-stack");
        Map<StackKey, Integer> removed =
                whole ? new LinkedHashMap<>(spawner.stacks()) : Map.of(first, 1);
        for (StackKey key : removed.keySet())
            if (!typePermission(player, "break", key.type()) || catalog.tier(key) == null)
                return fail(player, "no-permission");
        int amount = removed.values().stream().mapToInt(Integer::intValue).sum();
        boolean eligibleTool =
                player.hasPermission("customspawners.bypass.silk")
                        || settings.bool("pickup.diamond-pickaxe")
                                && (player.getInventory().getItemInMainHand().getType()
                                                == Material.DIAMOND_PICKAXE
                                        || player.getInventory().getItemInMainHand().getType()
                                                == Material.NETHERITE_PICKAXE)
                        || settings.bool("pickup.silk-touch")
                                && player.hasPermission("customspawners.silk")
                                && player.getInventory()
                                        .getItemInMainHand()
                                        .getType()
                                        .name()
                                        .endsWith("_PICKAXE")
                                && player.getInventory()
                                                .getItemInMainHand()
                                                .getEnchantmentLevel(Enchantment.SILK_TOUCH)
                                        >= settings.integer("pickup.silk-level")
                                && removed.keySet().stream()
                                        .allMatch(
                                                key -> typePermission(player, "silk", key.type()));
        if (!eligibleTool) return fail(player, "pickup-tool-required");
        boolean drop =
                eligibleTool
                        && (!settings.bool("pickup.only-placed") || spawner.owner() != null)
                        && chance(
                                settings.decimal(
                                        spawner.owner() == null
                                                ? "pickup.natural-chance"
                                                : "pickup.placed-chance"));
        if (drop && !checkedEvent(player, spawner, Action.DROP)) return false;
        ItemStack item = drop ? items.create(removed) : null;
        Checkpoint checkpoint = checkpoint(player, spawner, Action.BREAK);
        double cost =
                removed.entrySet().stream()
                        .mapToDouble(
                                entry ->
                                        catalog.tier(entry.getKey()).pickupCost()
                                                * entry.getValue())
                        .sum();
        if (!payChecked(player, cost, checkpoint)) return false;
        removed.forEach(spawner::remove);
        if (spawner.total() == 0) remove(spawner);
        else changed(spawner);
        if (item != null) {
            if (settings.bool("pickup.to-inventory")
                    || spawner.owner() != null && settings.bool("pickup.placed-to-inventory"))
                SpawnerItems.giveOrDrop(player, item);
            else
                player.getWorld()
                        .dropItemNaturally(spawner.position().location().add(.5, .5, .5), item);
        }
        messages.send(player, "broken");
        return true;
    }

    public boolean upgrade(Player player, PlacedSpawner spawner, StackKey key, boolean levels) {
        if (!permission(player, "upgrade") || !access(player, spawner, Action.ACCESS)) return false;
        if (!settings.bool(levels ? "upgrades.levels" : "upgrades.money"))
            return fail(player, "disabled");
        SpawnerType type = catalog.get(key.type());
        if (type == null || !type.enabled() || !spawner.stacks().containsKey(key))
            return fail(player, "unknown-type");
        if (!typePermission(player, "upgrade", type.id())) return fail(player, "no-permission");
        var next = type.nextTier(key.tier());
        if (next == null) return fail(player, "max-tier");
        int count = spawner.stacks().get(key);
        long levelCost = (long) next.upgradeLevels() * count;
        if (charge(player) && levels && player.getLevel() < levelCost)
            return fail(player, "cannot-afford");
        if (!checkedEvent(player, spawner, Action.UPGRADE)) return false;
        if (!levels
                && !payChecked(
                        player,
                        next.upgradeMoney() * count,
                        checkpoint(player, spawner, Action.UPGRADE))) return false;
        if (levels && charge(player)) player.setLevel((int) (player.getLevel() - levelCost));
        spawner.replace(key, new StackKey(type.id(), next.id()));
        changed(spawner);
        messages.send(player, "upgraded", Map.of("tier", next.name()));
        return true;
    }

    public boolean grow(Player player, PlacedSpawner spawner, StackKey key, boolean levels) {
        if (!permission(player, "upgrade") || !access(player, spawner, Action.ACCESS)) return false;
        if (!settings.bool(levels ? "upgrades.levels" : "upgrades.money"))
            return fail(player, "disabled");
        var tier = catalog.tier(key);
        if (tier == null
                || !catalog.get(key.type()).enabled()
                || !spawner.stacks().containsKey(key)) return fail(player, "unknown-type");
        if (!typePermission(player, "upgrade", key.type())) return fail(player, "no-permission");
        if (spawner.total() >= settings.integer("stacking.max-stack"))
            return fail(player, "max-stack");
        if (charge(player) && levels && player.getLevel() < tier.upgradeLevels())
            return fail(player, "cannot-afford");
        if (!checkedEvent(player, spawner, Action.UPGRADE)) return false;
        if (!levels
                && !payChecked(
                        player, tier.upgradeMoney(), checkpoint(player, spawner, Action.UPGRADE)))
            return false;
        if (levels && charge(player)) player.setLevel(player.getLevel() - tier.upgradeLevels());
        spawner.add(key, 1);
        changed(spawner);
        messages.send(player, "stacked", Map.of("amount", spawner.total()));
        return true;
    }

    public boolean convert(
            Player player, PlacedSpawner spawner, StackKey key, String targetId, boolean eggs) {
        if (!permission(player, "convert") || !access(player, spawner, Action.ACCESS)) return false;
        SpawnerType target = catalog.get(targetId), previous = catalog.get(key.type());
        if (target == null
                || previous == null
                || !target.enabled()
                || !target.convertible()
                || !previous.convertible()
                || !spawner.stacks().containsKey(key)
                || blocked(player.getWorld().getName(), target.id()))
            return fail(player, "unknown-type");
        if (target.id().equals(key.type())) return fail(player, "disabled");
        int count = spawner.stacks().get(key);
        ItemStack held = player.getInventory().getItemInMainHand();
        long eggCount = (long) target.eggCost() * count;
        if (eggs
                && (!settings.bool("conversion.eggs")
                        || held.getType() != Material.matchMaterial(target.id() + "_SPAWN_EGG")
                        || charge(player) && held.getAmount() < eggCount))
            return fail(player, "cannot-afford");
        if (!typePermission(player, "convert", target.id())
                || !typePermission(player, "place", target.id()))
            return fail(player, "no-permission");
        if (!checkedEvent(player, spawner, Action.CONVERT) || catalog.get(targetId) != target)
            return false;
        if (!eggs
                && !payChecked(
                        player,
                        target.conversionCost() * count,
                        checkpoint(player, spawner, Action.CONVERT),
                        () ->
                                catalog.get(targetId) == target
                                        && typePermission(player, "convert", target.id())
                                        && typePermission(player, "place", target.id())))
            return false;
        if (eggs && charge(player)) {
            ItemStack currentHeld = player.getInventory().getItemInMainHand();
            currentHeld.setAmount((int) (currentHeld.getAmount() - eggCount));
        }
        spawner.replace(key, new StackKey(target.id(), target.firstTier().id()));
        if (eggs && settings.bool("conversion.return-old-egg")) {
            Material oldEgg = Material.matchMaterial(previous.id() + "_SPAWN_EGG");
            if (oldEgg != null) {
                int remaining = Math.min(count, 4096);
                while (remaining > 0) {
                    int give = Math.min(64, remaining);
                    SpawnerItems.giveOrDrop(player, new ItemStack(oldEgg, give));
                    remaining -= give;
                }
            }
        }
        changed(spawner);
        messages.send(player, "converted", Map.of("type", target.name()));
        return true;
    }

    public boolean buy(Player player, String id, String tierId, int amount) {
        if (!permission(player, "shop")) return false;
        var type = catalog.get(id);
        if (!catalog.availableInShop(type)
                || !type.firstTier().id().equals(tierId)
                || amount < 1
                || amount > 64
                || blocked(player.getWorld().getName(), type.id())
                || !typePermission(player, "shop", type.id())) return fail(player, "unknown-type");
        ItemStack item = items.create(new StackKey(type.id(), tierId), 1);
        if (!SpawnerItems.hasSpace(player, item, amount)) return fail(player, "inventory-full");
        double cost = type.price() * amount;
        ItemStack[] before = inventorySnapshot(player);
        Object configuration = settings.configuration();
        boolean charging = charge(player);
        EconomyBridge.Transaction payment = payment(player, cost);
        if (!payment.successful()) return false;
        if (charging != charge(player)
                || configuration != settings.configuration()
                || catalog.get(id) != type
                || !Arrays.equals(before, player.getInventory().getStorageContents())
                || !SpawnerItems.hasSpace(player, item, amount)
                || !typePermission(player, "shop", type.id())
                || !player.hasPermission("customspawners.shop")
                || blocked(player.getWorld().getName(), type.id())) {
            refund(player, payment);
            return fail(player, "inventory-full");
        }
        item.setAmount(amount);
        var remaining = player.getInventory().addItem(item);
        if (!remaining.isEmpty()) {
            player.getInventory().setStorageContents(before);
            refund(player, payment);
            return fail(player, "inventory-full");
        }
        messages.send(player, "purchased", Map.of("amount", amount, "type", type.name()));
        return true;
    }

    public boolean boost(Player player, PlacedSpawner spawner, int amount, int minutes) {
        if (!permission(player, "boost") || !access(player, spawner, Action.ACCESS)) return false;
        if (amount < 1
                || amount > settings.integer("boosts.max-amount")
                || minutes < 1
                || minutes > 1440) return fail(player, "disabled");
        double cost = Math.ceil(settings.decimal("boosts.cost-per-minute") * amount * minutes);
        if (!Double.isFinite(cost) || cost > Integer.MAX_VALUE)
            return fail(player, "cannot-afford");
        String currency = settings.text("boosts.currency");
        Material material = Material.matchMaterial(currency);
        if (charge(player)) {
            if (currency.equalsIgnoreCase("XP") && player.getLevel() < cost)
                return fail(player, "cannot-afford");
            if (material != null
                    && !player.getInventory().containsAtLeast(new ItemStack(material), (int) cost))
                return fail(player, "cannot-afford");
            if (material == null
                    && !currency.equalsIgnoreCase("XP")
                    && !currency.equalsIgnoreCase("MONEY")) return fail(player, "disabled");
        }
        if (!checkedEvent(player, spawner, Action.BOOST)) return false;
        if (currency.equalsIgnoreCase("MONEY")
                && !payChecked(player, cost, checkpoint(player, spawner, Action.BOOST)))
            return false;
        if (charge(player)) {
            if (currency.equalsIgnoreCase("XP")) player.setLevel(player.getLevel() - (int) cost);
            else if (material != null && cost > 0)
                player.getInventory().removeItem(new ItemStack(material, (int) cost));
        }
        spawner.boost(new Boost(amount, System.currentTimeMillis() + minutes * 60_000L));
        changed(spawner);
        messages.send(player, "boosted", Map.of("amount", amount, "minutes", minutes));
        return true;
    }

    public boolean payMoney(Player player, double cost) {
        return payment(player, cost).successful();
    }

    private EconomyBridge.Transaction payment(Player player, double cost) {
        if (!Double.isFinite(cost) || cost < 0) {
            fail(player, "cannot-afford");
            return EconomyBridge.Transaction.failed();
        }
        if (!charge(player) || cost == 0) return EconomyBridge.Transaction.free();
        EconomyBridge.Transaction payment = economy.withdrawReceipt(player, cost);
        if (!payment.successful()) fail(player, "cannot-afford");
        return payment;
    }

    public boolean charge(Player player) {
        return !player.hasPermission("customspawners.bypass.cost")
                && (player.getGameMode() != GameMode.CREATIVE || settings.bool("creative.charge"));
    }

    public void changed(PlacedSpawner spawner) {
        repository.dirty();
        engine.refresh(spawner);
    }

    private ItemStack[] inventorySnapshot(Player player) {
        return Arrays.stream(player.getInventory().getStorageContents())
                .map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
    }

    private Checkpoint checkpoint(Player player, PlacedSpawner spawner, Action action) {
        Map<StackKey, Integer> stacks = new LinkedHashMap<>(spawner.stacks());
        Map<StackKey, Object> tiers = new LinkedHashMap<>();
        stacks.keySet().forEach(key -> tiers.put(key, catalog.tier(key)));
        return new Checkpoint(
                player,
                spawner,
                stacks,
                tiers,
                inventorySnapshot(player),
                player.getLevel(),
                spawner.boost(),
                charge(player),
                action);
    }

    private boolean checkedEvent(Player player, PlacedSpawner spawner, Action action) {
        Checkpoint checkpoint = checkpoint(player, spawner, action);
        return checkpoint.valid() && event(player, spawner, action) && checkpoint.valid();
    }

    private boolean payChecked(Player player, double cost, Checkpoint checkpoint) {
        return payChecked(player, cost, checkpoint, () -> true);
    }

    private boolean payChecked(
            Player player, double cost, Checkpoint checkpoint, BooleanSupplier additional) {
        if (!checkpoint.valid() || !additional.getAsBoolean()) return false;
        EconomyBridge.Transaction payment = payment(player, cost);
        if (!payment.successful()) return false;
        if (checkpoint.valid() && additional.getAsBoolean()) return true;
        refund(player, payment);
        return false;
    }

    private void refund(Player player, EconomyBridge.Transaction payment) {
        if (!payment.refund())
            plugin.getLogger()
                    .severe(
                            "Economy refund failed for "
                                    + player.getUniqueId()
                                    + ": "
                                    + payment.amount()
                                    + " via "
                                    + payment.provider());
    }

    private final class Checkpoint {
        private final Player player;
        private final PlacedSpawner spawner;
        private final Map<StackKey, Integer> stacks;
        private final Map<StackKey, Object> tiers;
        private final ItemStack[] inventory;
        private final int levels;
        private final Boost boost;
        private final boolean charged;
        private final Action action;
        private final Object configuration;

        private Checkpoint(
                Player player,
                PlacedSpawner spawner,
                Map<StackKey, Integer> stacks,
                Map<StackKey, Object> tiers,
                ItemStack[] inventory,
                int levels,
                Boost boost,
                boolean charged,
                Action action) {
            this.player = player;
            this.spawner = spawner;
            this.stacks = stacks;
            this.tiers = tiers;
            this.inventory = inventory;
            this.levels = levels;
            this.boost = boost;
            this.charged = charged;
            this.action = action;
            this.configuration = settings.configuration();
        }

        private boolean valid() {
            if (configuration != settings.configuration()
                    || !accessible(player, spawner)
                    || !stacks.equals(spawner.stacks())
                    || !boost.equals(spawner.boost())
                    || player.getLevel() != levels
                    || charged != charge(player)
                    || !Arrays.equals(inventory, player.getInventory().getStorageContents()))
                return false;
            for (StackKey key : stacks.keySet()) {
                SpawnerType type = catalog.get(key.type());
                if (type == null
                        || !type.enabled()
                        || blocked(spawner.position().location().getWorld().getName(), key.type())
                        || tiers.get(key) != catalog.tier(key)) return false;
                String permission =
                        switch (action) {
                            case STACK -> "stack";
                            case UPGRADE -> "upgrade";
                            case CONVERT -> "convert";
                            case BOOST -> "boost";
                            case BREAK, DROP -> "break";
                            default -> "";
                        };
                if (!permission.isEmpty()
                        && (!player.hasPermission("customspawners." + permission)
                                || !typePermission(player, permission, key.type()))) return false;
            }
            return true;
        }
    }

    public void remove(PlacedSpawner spawner) {
        engine.deactivate(spawner.position());
        repository.remove(spawner.position());
        var location = spawner.position().location();
        if (location != null
                && location.getWorld()
                        .isChunkLoaded(spawner.position().x() >> 4, spawner.position().z() >> 4)
                && location.getBlock().getType() == Material.SPAWNER)
            location.getBlock().setType(Material.AIR);
    }

    public void explode(PlacedSpawner spawner, double probability) {
        Map<StackKey, Integer> removed = new LinkedHashMap<>(spawner.stacks());
        boolean drop = chance(probability) && event(null, spawner, Action.DROP);
        var location = spawner.position().location();
        remove(spawner);
        if (drop
                && location != null
                && removed.keySet().stream().allMatch(key -> catalog.tier(key) != null))
            location.getWorld().dropItemNaturally(location.add(.5, .5, .5), items.create(removed));
    }

    public static boolean chance(double percent) {
        return percent >= 100
                || percent > 0 && ThreadLocalRandom.current().nextDouble(100) < percent;
    }

    private boolean fail(Player player, String key) {
        messages.send(player, key);
        return false;
    }
}
