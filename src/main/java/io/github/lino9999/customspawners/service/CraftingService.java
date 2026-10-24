package io.github.lino9999.customspawners.service;

import io.github.lino9999.customspawners.config.SpawnerCatalog;
import io.github.lino9999.customspawners.model.StackKey;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class CraftingService implements Listener {
    private final JavaPlugin plugin;
    private final SpawnerCatalog catalog;
    private final SpawnerItems items;
    private final SpawnerService service;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public CraftingService(
            JavaPlugin plugin, SpawnerCatalog catalog, SpawnerItems items, SpawnerService service) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.items = items;
        this.service = service;
    }

    public void reload() {
        List<ShapedRecipe> prepared = new ArrayList<>();
        for (var type : catalog.all()) {
            if (!type.enabled() || !type.craftable()) continue;
            NamespacedKey key = new NamespacedKey(plugin, "recipe_" + type.id());
            ShapedRecipe recipe =
                    new ShapedRecipe(
                            key, items.create(new StackKey(type.id(), type.firstTier().id()), 1));
            recipe.shape(type.recipe().toArray(String[]::new));
            type.ingredients()
                    .forEach(
                            (symbol, material) ->
                                    recipe.setIngredient(symbol, Material.valueOf(material)));
            prepared.add(recipe);
        }
        List<Recipe> previous =
                keys.stream().map(Bukkit::getRecipe).filter(java.util.Objects::nonNull).toList();
        List<NamespacedKey> previousKeys = List.copyOf(keys);
        clear();
        try {
            for (ShapedRecipe recipe : prepared) {
                if (!Bukkit.addRecipe(recipe))
                    throw new IllegalStateException("Could not register recipe " + recipe.getKey());
                keys.add(recipe.getKey());
            }
        } catch (RuntimeException exception) {
            clear();
            boolean restored = true;
            for (Recipe recipe : previous) restored &= Bukkit.addRecipe(recipe);
            keys.addAll(previousKeys);
            if (!restored)
                plugin.getLogger()
                        .severe(
                                "Some previous CustomSpawners recipes could not be restored after a"
                                    + " failed reload.");
            throw exception;
        }
    }

    public void clear() {
        keys.forEach(Bukkit::removeRecipe);
        keys.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (!items.tagged(event.getRecipe().getResult())) return;
        if (!(event.getWhoClicked() instanceof Player player)
                || !service.permission(player, "craft")) {
            event.setCancelled(true);
            return;
        }
        if (!canCraft(player, event.getRecipe().getResult())
                || !canCraft(player, event.getCurrentItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event) {
        var recipe = event.getRecipe();
        if (recipe == null || !items.tagged(recipe.getResult())) return;
        for (var viewer : event.getViewers()) {
            if (!(viewer instanceof Player player) || !canCraft(player, recipe.getResult())) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void automatic(CrafterCraftEvent event) {
        if (items.tagged(event.getResult()) || items.tagged(event.getRecipe().getResult()))
            event.setCancelled(true);
    }

    private boolean canCraft(Player player, org.bukkit.inventory.ItemStack result) {
        if (!player.hasPermission("customspawners.craft") || !items.tagged(result)) return false;
        try {
            var stacks = items.read(result);
            if (stacks.isEmpty()) return false;
            for (var key : stacks.keySet())
                if (service.blocked(player.getWorld().getName(), key.type())
                        || !service.typePermission(player, "craft", key.type())) return false;
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
