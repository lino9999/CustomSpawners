package io.github.lino9999.customspawners.service;

import io.github.lino9999.customspawners.config.Messages;
import io.github.lino9999.customspawners.config.SpawnerCatalog;
import io.github.lino9999.customspawners.model.PlacedSpawner;
import io.github.lino9999.customspawners.model.SpawnOutput;
import io.github.lino9999.customspawners.model.StackKey;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpawnerItems {
    private final NamespacedKey dataKey;
    private final NamespacedKey ownerKey;
    private final SpawnerCatalog catalog;

    public SpawnerItems(JavaPlugin plugin, SpawnerCatalog catalog) {
        dataKey = new NamespacedKey(plugin, "stacks");
        ownerKey = new NamespacedKey(plugin, "placed-by");
        this.catalog = catalog;
    }

    public boolean tagged(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(dataKey);
    }

    public Map<StackKey, Integer> read(ItemStack item) {
        if (!tagged(item)) return Map.of();
        if (item.getType() != Material.SPAWNER
                || !item.getItemMeta()
                        .getPersistentDataContainer()
                        .has(dataKey, PersistentDataType.STRING))
            throw new IllegalArgumentException("Invalid spawner item data.");
        String data =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(dataKey, PersistentDataType.STRING);
        return decode(data);
    }

    public boolean tagged(CreatureSpawner tile) {
        return tile.getPersistentDataContainer().has(dataKey);
    }

    public Map<StackKey, Integer> read(CreatureSpawner tile) {
        if (!tagged(tile)) return Map.of();
        return decode(tile.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING));
    }

    public UUID owner(CreatureSpawner tile) {
        String owner = tile.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return owner == null ? null : UUID.fromString(owner);
    }

    private Map<StackKey, Integer> decode(String data) {
        if (data == null || data.length() > 32768)
            throw new IllegalArgumentException("Invalid spawner item data.");
        Map<StackKey, Integer> stacks = new LinkedHashMap<>();
        long total = 0;
        for (String entry : data.split(";", -1)) {
            String[] parts = entry.split(":", -1);
            if (parts.length != 3) throw new IllegalArgumentException("Invalid spawner item data.");
            StackKey supplied = new StackKey(parts[0], parts[1]);
            int count = Integer.parseInt(parts[2]);
            var type = catalog.get(supplied.type());
            var tier = catalog.tier(supplied);
            if (type == null || !type.enabled() || tier == null || count < 1)
                throw new IllegalArgumentException("Unknown or disabled spawner item.");
            StackKey key = new StackKey(type.id(), tier.id());
            if (stacks.containsKey(key))
                throw new IllegalArgumentException("Duplicate spawner stack.");
            total += count;
            if (total > 1_000_000) throw new IllegalArgumentException("Spawner item is too large.");
            stacks.put(key, count);
        }
        return stacks;
    }

    public ItemStack create(StackKey key, int count) {
        return create(Map.of(key, count));
    }

    public ItemStack create(Map<StackKey, Integer> stacks) {
        if (stacks.isEmpty()) throw new IllegalArgumentException("Spawner stack cannot be empty.");
        Map<StackKey, Integer> normalized = new LinkedHashMap<>();
        for (var entry : stacks.entrySet()) {
            var type = catalog.get(entry.getKey().type());
            var tier = catalog.tier(entry.getKey());
            if (type == null || tier == null || entry.getValue() == null || entry.getValue() < 1)
                throw new IllegalArgumentException("Invalid spawner stack.");
            StackKey canonical = new StackKey(type.id(), tier.id());
            if (normalized.putIfAbsent(canonical, entry.getValue()) != null)
                throw new IllegalArgumentException("Duplicate spawner stack.");
        }
        stacks = normalized;
        StringBuilder data = new StringBuilder();
        List<String> lore = new ArrayList<>();
        int total = 0;
        for (var entry : stacks.entrySet()) {
            var type = catalog.get(entry.getKey().type());
            var tier = catalog.tier(entry.getKey());
            if (type == null || tier == null || entry.getValue() < 1)
                throw new IllegalArgumentException("Invalid spawner stack.");
            if (!data.isEmpty()) data.append(';');
            data.append(entry.getKey().type())
                    .append(':')
                    .append(entry.getKey().tier())
                    .append(':')
                    .append(entry.getValue());
            total = Math.addExact(total, entry.getValue());
            if (total > 1_000_000 || data.length() > 32768)
                throw new IllegalArgumentException("Spawner item is too large.");
            lore.add(
                    Messages.color(
                            "&7"
                                    + type.name()
                                    + " &8/ &7"
                                    + tier.name()
                                    + " &fx"
                                    + entry.getValue()));
        }
        ItemStack item = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        String name =
                stacks.size() == 1
                        ? catalog.get(stacks.keySet().iterator().next().type()).name()
                        : "Omni";
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith("spawner")) name += " Spawner";
        meta.setDisplayName(Messages.color("&6" + name + " &7[&e" + total + "x&7]"));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, data.toString());
        CreatureSpawner tile = (CreatureSpawner) meta.getBlockState();
        apply(tile, stacks, data.toString());
        meta.setBlockState(tile);
        item.setItemMeta(meta);
        return item;
    }

    public void apply(CreatureSpawner tile, PlacedSpawner spawner) {
        String data =
                spawner.stacks().entrySet().stream()
                        .map(
                                entry ->
                                        entry.getKey().type()
                                                + ":"
                                                + entry.getKey().tier()
                                                + ":"
                                                + entry.getValue())
                        .collect(java.util.stream.Collectors.joining(";"));
        apply(tile, spawner.stacks(), data);
        if (spawner.owner() == null) tile.getPersistentDataContainer().remove(ownerKey);
        else
            tile.getPersistentDataContainer()
                    .set(ownerKey, PersistentDataType.STRING, spawner.owner().toString());
    }

    private void apply(CreatureSpawner tile, Map<StackKey, Integer> stacks, String data) {
        var tier = catalog.tier(stacks.keySet().iterator().next());
        EntityType entity =
                tier == null
                        ? null
                        : tier.outputs().stream()
                                .filter(output -> output.kind() == SpawnOutput.Kind.ENTITY)
                                .map(output -> EntityType.valueOf(output.value()))
                                .findFirst()
                                .orElse(null);
        tile.setPotentialSpawns(List.of());
        tile.setSpawnedType(entity);
        tile.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, data);
    }

    public static boolean hasSpace(Player player, ItemStack item, int count) {
        if (item == null || count < 1) return false;
        long room = 0;
        int maximum = Math.min(item.getMaxStackSize(), player.getInventory().getMaxStackSize());
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) room += maximum;
            else if (slot.isSimilar(item)) room += Math.max(0, maximum - slot.getAmount());
        }
        return room >= count;
    }

    public static void giveOrDrop(Player player, ItemStack item) {
        player.getInventory()
                .addItem(item)
                .values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
