package io.github.lino9999.customspawners.integration;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class SmokeActor {
    final ItemStack[] storage = new ItemStack[36];
    final Map<String, Boolean> permissions = new HashMap<>();
    Player player;
    final PlayerInventory inventory;
    int levels;
    boolean rejectDelivery;
    private final UUID id;
    private final World world;

    SmokeActor(World world, UUID id) {
        this.world = world;
        this.id = id;
        inventory =
                (PlayerInventory)
                        Proxy.newProxyInstance(
                                PlayerInventory.class.getClassLoader(),
                                new Class<?>[] {PlayerInventory.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getStorageContents", "getContents" ->
                                                    copy(storage);
                                            case "setStorageContents", "setContents" -> {
                                                Arrays.fill(storage, null);
                                                ItemStack[] values = (ItemStack[]) args[0];
                                                System.arraycopy(
                                                        copy(values),
                                                        0,
                                                        storage,
                                                        0,
                                                        Math.min(values.length, storage.length));
                                                yield null;
                                            }
                                            case "getItemInMainHand", "getItemInHand" ->
                                                    storage[0] == null
                                                            ? new ItemStack(Material.AIR)
                                                            : storage[0];
                                            case "setItemInMainHand", "setItemInHand" -> {
                                                storage[0] = (ItemStack) args[0];
                                                yield null;
                                            }
                                            case "getItem" -> storage[(int) args[0]];
                                            case "setItem" -> {
                                                storage[(int) args[0]] = (ItemStack) args[1];
                                                yield null;
                                            }
                                            case "getMaxStackSize" -> 64;
                                            case "getSize" -> 41;
                                            case "getHeldItemSlot" -> 0;
                                            case "getHolder" -> player;
                                            case "addItem" -> add((ItemStack[]) args[0]);
                                            case "containsAtLeast" ->
                                                    count((ItemStack) args[0]) >= (int) args[1];
                                            case "removeItem" -> remove((ItemStack[]) args[0]);
                                            case "toString" -> "SmokeInventory";
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == args[0];
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            "Unexpected inventory call: " + method);
                                        });
        player =
                (Player)
                        Proxy.newProxyInstance(
                                Player.class.getClassLoader(),
                                new Class<?>[] {Player.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getUniqueId" -> this.id;
                                            case "getName", "getDisplayName" -> "SmokePlayer";
                                            case "getWorld" -> this.world;
                                            case "getLocation" ->
                                                    new Location(this.world, 8, 100, 8);
                                            case "getInventory" -> inventory;
                                            case "getLevel" -> levels;
                                            case "setLevel" -> {
                                                levels = (int) args[0];
                                                yield null;
                                            }
                                            case "getGameMode" -> GameMode.SURVIVAL;
                                            case "hasPermission" -> {
                                                String permission = String.valueOf(args[0]);
                                                yield permissions.getOrDefault(
                                                        permission,
                                                        !permission.equals(
                                                                "customspawners.bypass.cost"));
                                            }
                                            case "isPermissionSet" ->
                                                    permissions.containsKey(
                                                            String.valueOf(args[0]));
                                            case "isOnline", "isValid" -> true;
                                            case "isDead", "isSneaking", "isOp" -> false;
                                            case "sendMessage",
                                                    "sendActionBar",
                                                    "updateInventory" ->
                                                    null;
                                            case "getServer" -> Bukkit.getServer();
                                            case "toString" -> "SmokePlayer";
                                            case "hashCode" -> id.hashCode();
                                            case "equals" -> proxy == args[0];
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            "Unexpected player call: " + method);
                                        });
    }

    private Map<Integer, ItemStack> add(ItemStack[] items) {
        Map<Integer, ItemStack> remaining = new HashMap<>();
        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index].clone();
            if (!rejectDelivery) {
                for (int slot = 0; slot < storage.length && item.getAmount() > 0; slot++) {
                    ItemStack current = storage[slot];
                    if (current != null && !current.isSimilar(item)) continue;
                    int room =
                            Math.min(64, item.getMaxStackSize())
                                    - (current == null ? 0 : current.getAmount());
                    int quantity = Math.min(room, item.getAmount());
                    if (quantity <= 0) continue;
                    if (current == null) {
                        current = item.clone();
                        current.setAmount(0);
                        storage[slot] = current;
                    }
                    current.setAmount(current.getAmount() + quantity);
                    item.setAmount(item.getAmount() - quantity);
                }
            }
            if (item.getAmount() > 0) remaining.put(index, item);
        }
        return remaining;
    }

    private int count(ItemStack item) {
        return Arrays.stream(storage)
                .filter(value -> value != null && value.isSimilar(item))
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    private Map<Integer, ItemStack> remove(ItemStack[] items) {
        Map<Integer, ItemStack> remaining = new HashMap<>();
        for (int index = 0; index < items.length; index++) {
            int count = items[index].getAmount();
            for (int slot = 0; slot < storage.length && count > 0; slot++) {
                ItemStack value = storage[slot];
                if (value == null || !value.isSimilar(items[index])) continue;
                int quantity = Math.min(count, value.getAmount());
                value.setAmount(value.getAmount() - quantity);
                count -= quantity;
                if (value.getAmount() == 0) storage[slot] = null;
            }
            if (count > 0) {
                ItemStack value = items[index].clone();
                value.setAmount(count);
                remaining.put(index, value);
            }
        }
        return remaining;
    }

    private static ItemStack[] copy(ItemStack[] values) {
        return Arrays.stream(values)
                .map(value -> value == null ? null : value.clone())
                .toArray(ItemStack[]::new);
    }
}
