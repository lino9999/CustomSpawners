package io.github.lino9999.customspawners.gui;

import io.github.lino9999.customspawners.config.Messages;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class Menu implements InventoryHolder {
    private final Inventory inventory;
    private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
    private Predicate<Player> access = player -> true;

    public Menu(String title) {
        inventory = Bukkit.createInventory(this, 54, title);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void guard(Predicate<Player> value) {
        access = value;
    }

    public void button(
            int slot,
            Material material,
            String name,
            List<String> lore,
            BiConsumer<Player, ClickType> action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Messages.color(name));
        meta.setLore(lore.stream().map(Messages::color).toList());
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
    }

    public void click(Player player, int slot, ClickType click) {
        if (!access.test(player)) {
            player.closeInventory();
            return;
        }
        var action = actions.get(slot);
        if (action != null) action.accept(player, click);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }
}
