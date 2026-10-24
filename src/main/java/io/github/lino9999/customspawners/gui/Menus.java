package io.github.lino9999.customspawners.gui;

import io.github.lino9999.customspawners.CustomSpawnersPlugin;
import io.github.lino9999.customspawners.api.SpawnerActionEvent.Action;
import io.github.lino9999.customspawners.model.PlacedSpawner;
import io.github.lino9999.customspawners.model.SpawnerTier;
import io.github.lino9999.customspawners.model.SpawnerType;
import io.github.lino9999.customspawners.model.StackKey;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class Menus implements Listener {
    private final CustomSpawnersPlugin plugin;

    public Menus(CustomSpawnersPlugin plugin) {
        this.plugin = plugin;
    }

    public void shop(Player player, int page) {
        if (!plugin.service().permission(player, "shop")) return;
        List<SpawnerType> types =
                plugin.catalog().all().stream()
                        .filter(type -> type.enabled() && type.shop())
                        .filter(
                                type ->
                                        !plugin.service()
                                                        .blocked(
                                                                player.getWorld().getName(),
                                                                type.id())
                                                && plugin.service()
                                                        .typePermission(player, "shop", type.id()))
                        .sorted(
                                Comparator.comparingInt(SpawnerType::shopOrder)
                                        .thenComparing(SpawnerType::name))
                        .toList();
        paged(
                player,
                "CustomSpawners Shop",
                types,
                page,
                (menu, entry) -> {
                    SpawnerType type = entry.value();
                    menu.button(
                            entry.slot(),
                            icon(type),
                            "&6" + type.name(),
                            List.of(
                                    "&7Price: &e" + type.price(),
                                    "&7Left: buy 1 | Right: buy 10",
                                    "&7Shift: buy 64"),
                            (p, click) -> {
                                plugin.service()
                                        .buy(
                                                p,
                                                type.id(),
                                                type.firstTier().id(),
                                                click.isShiftClick()
                                                        ? 64
                                                        : click.isRightClick() ? 10 : 1);
                            });
                },
                this::shop,
                p -> plugin.service().permission(p, "shop"));
    }

    public void overview(Player player, PlacedSpawner spawner) {
        overview(player, spawner, 0);
    }

    private void overview(Player player, PlacedSpawner spawner, int page) {
        if (!canUse(player, spawner)) return;
        if (spawner.stacks().size() == 1) {
            detail(player, spawner, spawner.first());
            return;
        }
        var keys = new ArrayList<>(spawner.stacks().keySet());
        paged(
                player,
                "Omni Spawner",
                keys,
                page,
                (menu, entry) -> {
                    StackKey key = entry.value();
                    var type = plugin.catalog().get(key.type());
                    menu.button(
                            entry.slot(),
                            type == null ? Material.BARRIER : icon(type),
                            "&6" + key.type(),
                            List.of(
                                    "&7Tier: " + key.tier(),
                                    "&7Stack: " + spawner.stacks().get(key)),
                            (p, click) -> detail(p, spawner, key));
                },
                (p, next) -> overview(p, spawner, next),
                p -> canUse(p, spawner));
    }

    private void detail(Player player, PlacedSpawner spawner, StackKey key) {
        if (!canUse(player, spawner)) return;
        var type = plugin.catalog().get(key.type());
        var tier = plugin.catalog().tier(key);
        if (type == null || tier == null || !spawner.stacks().containsKey(key)) return;
        Menu menu = new Menu("Spawner: " + type.name());
        menu.guard(p -> canUse(p, spawner));
        menu.button(
                4,
                icon(type),
                "&6" + type.name() + " &ex" + spawner.stacks().get(key),
                List.of(
                        "&7Tier: " + tier.name(),
                        "&7Successful cycles: " + spawner.cycles(),
                        "&7Status: " + status(spawner)),
                null);
        menu.button(
                19,
                Material.SUNFLOWER,
                "&aAdd stack with money",
                List.of("&7Cost: " + tier.upgradeMoney()),
                (p, click) -> {
                    plugin.service().grow(p, spawner, key, false);
                    detail(p, spawner, key);
                });
        menu.button(
                20,
                Material.EXPERIENCE_BOTTLE,
                "&aAdd stack with levels",
                List.of("&7Cost: " + tier.upgradeLevels()),
                (p, click) -> {
                    plugin.service().grow(p, spawner, key, true);
                    detail(p, spawner, key);
                });
        var next = type.nextTier(key.tier());
        menu.button(
                21,
                Material.NETHER_STAR,
                "&aAdvance tier",
                next == null
                        ? List.of("&7Highest tier reached")
                        : List.of(
                                "&7Next: " + next.name(),
                                "&7Left: "
                                        + next.upgradeMoney() * spawner.stacks().get(key)
                                        + " money",
                                "&7Right: "
                                        + (long) next.upgradeLevels() * spawner.stacks().get(key)
                                        + " levels"),
                (p, click) -> {
                    if (plugin.service().upgrade(p, spawner, key, click.isRightClick()))
                        overview(p, spawner);
                });
        menu.button(
                22,
                Material.EGG,
                "&aConvert",
                List.of("&7Choose a new spawner type"),
                (p, click) -> conversions(p, spawner, key, 0));
        menu.button(
                23,
                Material.BLAZE_POWDER,
                "&aBoost",
                List.of(
                        "&7Extra output: +"
                                + plugin.boosts().extra(spawner, System.currentTimeMillis())),
                (p, click) -> boosts(p, spawner, 1));
        menu.button(
                24,
                Material.IRON_PICKAXE,
                "&aCollect",
                List.of("&7Uses the same rules as mining", "&7Sneak to collect all stacks"),
                (p, click) -> {
                    plugin.service().pickup(p, spawner);
                    p.closeInventory();
                });
        menu.button(
                40,
                Material.BOOK,
                "&eHow to use",
                List.of(
                        "&7Right-click with a spawner to stack",
                        "&7Use spawn eggs to convert",
                        "&7Silk Touch preserves spawner items",
                        "&7Redstone pauses spawning",
                        "&7Conditions and tiers are configurable"),
                null);
        close(menu);
        menu.open(player);
    }

    private String status(PlacedSpawner spawner) {
        String reason = plugin.engine().pauseReason(spawner);
        return reason.isEmpty() ? "Ready" : reason;
    }

    public void conversions(Player player, PlacedSpawner spawner, StackKey key, int page) {
        if (!canUse(player, spawner) || !spawner.stacks().containsKey(key)) return;
        var types =
                plugin.catalog().all().stream()
                        .filter(
                                type ->
                                        type.enabled()
                                                && type.convertible()
                                                && !type.id().equals(key.type()))
                        .toList();
        paged(
                player,
                "Convert Spawner",
                types,
                page,
                (menu, entry) -> {
                    var type = entry.value();
                    menu.button(
                            entry.slot(),
                            icon(type),
                            "&6" + type.name(),
                            List.of("&7Cost per stack unit: " + type.conversionCost()),
                            (p, click) -> {
                                if (plugin.service().convert(p, spawner, key, type.id(), false))
                                    overview(p, spawner);
                            });
                },
                (p, next) -> conversions(p, spawner, key, next),
                p -> canUse(p, spawner));
    }

    public void boosts(Player player, PlacedSpawner spawner, int amount) {
        if (!canUse(player, spawner)) return;
        int bonus = Math.clamp(amount, 1, plugin.settings().integer("boosts.max-amount"));
        Menu menu = new Menu("Boost Spawner");
        menu.guard(p -> canUse(p, spawner));
        menu.button(
                4,
                Material.BLAZE_POWDER,
                "&6Extra output: +" + bonus,
                List.of("&7Left: increase | Right: decrease"),
                (p, click) -> boosts(p, spawner, bonus + (click.isRightClick() ? -1 : 1)));
        int slot = 19;
        for (int minutes : List.of(5, 15, 30, 60)) {
            double cost =
                    Math.ceil(
                            plugin.settings().decimal("boosts.cost-per-minute") * minutes * bonus);
            menu.button(
                    slot++,
                    Material.CLOCK,
                    "&a" + minutes + " minutes",
                    List.of("&7Cost: " + cost + " " + plugin.settings().text("boosts.currency")),
                    (p, click) -> {
                        plugin.service().boost(p, spawner, bonus, minutes);
                        overview(p, spawner);
                    });
        }
        close(menu);
        menu.open(player);
    }

    public void stats(Player player) {
        stats(player, 0);
    }

    public void stats(Player player, int page) {
        if (!plugin.service().permission(player, "stats")) return;
        var entries =
                plugin.progress().get(player.getUniqueId()).entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .toList();
        paged(
                player,
                "Spawner Kill Statistics",
                entries,
                page,
                (menu, entry) -> {
                    var type = plugin.catalog().get(entry.value().getKey());
                    menu.button(
                            entry.slot(),
                            type == null ? Material.PAPER : icon(type),
                            "&6" + entry.value().getKey(),
                            List.of("&7Kills: " + entry.value().getValue()),
                            null);
                },
                this::stats,
                p -> plugin.service().permission(p, "stats"));
        if (entries.isEmpty()) plugin.messages().send(player, "stats-empty");
    }

    public void editor(Player player, int page) {
        if (!plugin.service().permission(player, "editor")) return;
        var types = new ArrayList<>(plugin.catalog().all());
        paged(
                player,
                "Spawner Definition Editor",
                types,
                page,
                (menu, entry) -> {
                    var type = entry.value();
                    menu.button(
                            entry.slot(),
                            icon(type),
                            "&6" + type.name(),
                            List.of(
                                    "&7ID: " + type.id(),
                                    "&7Enabled: " + type.enabled(),
                                    "&7Click to edit"),
                            (p, click) -> editorType(p, type.id(), 0));
                },
                this::editor,
                p -> plugin.service().permission(p, "editor"));
    }

    private void editorType(Player player, String id, int page) {
        editorSection(player, id, "", page);
    }

    private void editorSection(Player player, String id, String relative, int page) {
        if (!plugin.service().permission(player, "editor")) return;
        SpawnerType type = plugin.catalog().get(id);
        if (type == null) return;
        var yaml = YamlConfiguration.loadConfiguration(plugin.catalog().file());
        Map<String, Object> section = effectiveType(type);
        if (!relative.isEmpty()) {
            for (String segment : relative.split("\\.")) {
                Object child = section.get(segment);
                if (!(child instanceof Map<?, ?> nested)) return;
                section = stringMap(nested);
            }
        }
        Map<String, Object> current = section;
        var fields = new ArrayList<>(current.keySet());
        Menu menu =
                paged(
                        player,
                        "Edit: " + id + (relative.isEmpty() ? "" : "/" + relative),
                        fields,
                        page,
                        (view, entry) -> {
                            String field = entry.value();
                            Object value = current.get(field);
                            String child = relative.isEmpty() ? field : relative + "." + field;
                            String path = catalogPath(yaml, id, child);
                            List<String> lore = new ArrayList<>();
                            lore.add("&7" + preview(value));
                            lore.add(
                                    yaml.contains(path)
                                            ? "&7Explicit value"
                                            : "&7Inherited or default value");
                            if (value instanceof Map<?, ?>) {
                                lore.add("&7Left: open section");
                                lore.add("&7Right: replace with a YAML map");
                            } else lore.add("&7Click to enter a new YAML value");
                            lore.add("&7Use null to restore inheritance");
                            view.button(
                                    entry.slot(),
                                    value instanceof Map<?, ?>
                                            ? Material.CHEST
                                            : Material.WRITABLE_BOOK,
                                    "&6" + field,
                                    lore,
                                    (p, click) -> {
                                        if (value instanceof Map<?, ?> && !click.isRightClick()) {
                                            editorSection(p, id, child, 0);
                                            return;
                                        }
                                        plugin.prompts()
                                                .request(
                                                        p,
                                                        input -> {
                                                            if (!plugin.service()
                                                                    .permission(p, "editor"))
                                                                return;
                                                            try {
                                                                plugin.editCatalog(
                                                                        path, parseValue(input));
                                                                plugin.messages().send(p, "edited");
                                                                editorSection(
                                                                        p, id, relative, page);
                                                            } catch (Exception ex) {
                                                                plugin.messages()
                                                                        .send(
                                                                                p,
                                                                                "invalid",
                                                                                Map.of(
                                                                                        "reason",
                                                                                        ex
                                                                                                .getMessage()));
                                                            }
                                                        });
                                    });
                        },
                        (p, next) -> editorSection(p, id, relative, next),
                        p -> plugin.service().permission(p, "editor"));
        menu.button(
                48,
                Material.ARROW,
                "&eBack",
                List.of(),
                (p, click) -> {
                    if (relative.isEmpty()) editor(p, 0);
                    else {
                        int separator = relative.lastIndexOf('.');
                        editorSection(
                                p, id, separator < 0 ? "" : relative.substring(0, separator), 0);
                    }
                });
    }

    private Map<String, Object> effectiveType(SpawnerType type) {
        Map<String, Object> tiers = new LinkedHashMap<>();
        for (SpawnerTier tier : type.tiers().values()) tiers.put(tier.id(), effectiveTier(tier));
        Map<String, Object> ingredients = new LinkedHashMap<>();
        type.ingredients().forEach((key, value) -> ingredients.put(String.valueOf(key), value));
        return values(
                "name",
                type.name(),
                "icon",
                type.icon(),
                "enabled",
                type.enabled(),
                "shop",
                type.shop(),
                "shop-order",
                type.shopOrder(),
                "price",
                type.price(),
                "convertible",
                type.convertible(),
                "conversion-cost",
                type.conversionCost(),
                "egg-cost",
                type.eggCost(),
                "kill-goal",
                type.killGoal(),
                "kill-chance",
                type.killChance(),
                "craftable",
                type.craftable(),
                "recipe",
                type.recipe(),
                "ingredients",
                ingredients,
                "tiers",
                tiers);
    }

    private Map<String, Object> effectiveTier(SpawnerTier tier) {
        var conditions = tier.conditions();
        Map<String, Object> conditionValues =
                values(
                        "biomes",
                        List.copyOf(conditions.biomes()),
                        "min-y",
                        conditions.minY(),
                        "max-y",
                        conditions.maxY(),
                        "min-light",
                        conditions.minLight(),
                        "max-light",
                        conditions.maxLight(),
                        "nearby-players",
                        conditions.nearbyPlayers(),
                        "player-range",
                        conditions.playerRange(),
                        "max-entities",
                        conditions.maxEntities(),
                        "entity-range",
                        conditions.entityRange(),
                        "storm",
                        conditions.storm(),
                        "spawn-on",
                        List.copyOf(conditions.spawnOn()));
        var outputs =
                tier.outputs().stream()
                        .map(
                                output ->
                                        values(
                                                "kind",
                                                output.kind().name(),
                                                "value",
                                                output.value(),
                                                "chance",
                                                output.chance(),
                                                "amount",
                                                output.amount()))
                        .toList();
        var drops =
                tier.drops().stream()
                        .map(
                                drop ->
                                        values(
                                                "material",
                                                drop.material(),
                                                "min",
                                                drop.min(),
                                                "max",
                                                drop.max(),
                                                "chance",
                                                drop.chance(),
                                                "name",
                                                drop.name(),
                                                "lore",
                                                drop.lore(),
                                                "command",
                                                drop.command(),
                                                "experience",
                                                drop.experience()))
                        .toList();
        return values(
                "name",
                tier.name(),
                "min-delay",
                tier.minDelay(),
                "max-delay",
                tier.maxDelay(),
                "min-spawn",
                tier.minSpawn(),
                "max-spawn",
                tier.maxSpawn(),
                "spawn-range",
                tier.spawnRange(),
                "upgrade-money",
                tier.upgradeMoney(),
                "upgrade-levels",
                tier.upgradeLevels(),
                "pickup-cost",
                tier.pickupCost(),
                "spawn-limit",
                tier.spawnLimit(),
                "ai",
                tier.ai(),
                "baby",
                tier.baby(),
                "on-fire",
                tier.onFire(),
                "entity-name",
                tier.entityName(),
                "particle",
                tier.particle(),
                "conditions",
                conditionValues,
                "outputs",
                outputs,
                "drops",
                drops);
    }

    private Map<String, Object> values(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2)
            result.put((String) entries[index], entries[index + 1]);
        return result;
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), yamlValue(value)));
        return result;
    }

    private String catalogPath(YamlConfiguration yaml, String id, String relative) {
        String path = "spawners";
        List<String> segments = new ArrayList<>();
        segments.add(id);
        segments.addAll(List.of(relative.split("\\.")));
        for (String segment : segments) {
            var section = yaml.getConfigurationSection(path);
            String actual = segment;
            if (section != null && !section.contains(segment)) {
                actual =
                        section.getKeys(false).stream()
                                .filter(key -> normalized(key).equals(normalized(segment)))
                                .findFirst()
                                .orElse(segment);
            }
            path += "." + actual;
        }
        return path;
    }

    private String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private boolean canUse(Player player, PlacedSpawner spawner) {
        return plugin.service().permission(player, "use")
                && plugin.service().access(player, spawner, Action.ACCESS);
    }

    public void settings(Player player, int page) {
        if (!plugin.service().permission(player, "settings")) return;
        var values = plugin.settings().configuration().getValues(true);
        var fields =
                values.keySet().stream()
                        .filter(
                                key ->
                                        !plugin.settings()
                                                .configuration()
                                                .isConfigurationSection(key))
                        .toList();
        paged(
                player,
                "CustomSpawners Settings",
                fields,
                page,
                (menu, entry) -> {
                    String key = entry.value();
                    menu.button(
                            entry.slot(),
                            Material.COMPARATOR,
                            "&6" + key,
                            List.of(
                                    "&7" + preview(values.get(key)),
                                    "&7Click to enter a new YAML value"),
                            (p, click) ->
                                    plugin.prompts()
                                            .request(
                                                    p,
                                                    input -> {
                                                        if (!plugin.service()
                                                                .permission(p, "settings")) return;
                                                        try {
                                                            plugin.editSetting(
                                                                    key, parseValue(input));
                                                            plugin.messages().send(p, "edited");
                                                            settings(p, page);
                                                        } catch (Exception ex) {
                                                            plugin.messages()
                                                                    .send(
                                                                            p,
                                                                            "invalid",
                                                                            Map.of(
                                                                                    "reason",
                                                                                    ex
                                                                                            .getMessage()));
                                                        }
                                                    }));
                },
                this::settings,
                p -> plugin.service().permission(p, "settings"));
    }

    public static Object parseValue(String input) throws Exception {
        if (input.length() > 32768) throw new IllegalArgumentException("YAML value is too long.");
        if (input.trim().equalsIgnoreCase("null") || input.trim().equals("~")) return null;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("value: " + input);
        if (yaml.getKeys(false).size() != 1 || !yaml.contains("value"))
            throw new IllegalArgumentException("Enter exactly one YAML value.");
        return yamlValue(yaml.get("value"));
    }

    private static Object yamlValue(Object value) {
        return yamlValue(value, 0, new int[] {10000});
    }

    private static Object yamlValue(Object value, int depth, int[] remaining) {
        if (depth > 32 || --remaining[0] < 0)
            throw new IllegalArgumentException(
                    "YAML value is too deeply nested or contains too many values.");
        if (value instanceof org.bukkit.configuration.ConfigurationSection section)
            value = section.getValues(false);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach(
                    (key, nested) ->
                            result.put(
                                    String.valueOf(key), yamlValue(nested, depth + 1, remaining)));
            return result;
        }
        if (value instanceof List<?> list)
            return list.stream().map(nested -> yamlValue(nested, depth + 1, remaining)).toList();
        return value;
    }

    private String preview(Object value) {
        String text = String.valueOf(value);
        return text.substring(0, Math.min(80, text.length()));
    }

    private Material icon(SpawnerType type) {
        Material material = Material.matchMaterial(type.icon());
        return material == null ? Material.SPAWNER : material;
    }

    private void close(Menu menu) {
        menu.button(49, Material.OAK_DOOR, "&cClose", List.of(), (p, click) -> p.closeInventory());
    }

    private record Entry<T>(int slot, T value) {}

    private <T> Menu paged(
            Player player,
            String title,
            List<T> values,
            int page,
            BiConsumer<Menu, Entry<T>> render,
            BiConsumer<Player, Integer> navigate,
            Predicate<Player> access) {
        int current = Math.clamp(page, 0, Math.max(0, (values.size() - 1) / 45));
        Menu menu = new Menu(title);
        menu.guard(access);
        for (int slot = 0, index = current * 45;
                slot < 45 && index < values.size();
                slot++, index++) render.accept(menu, new Entry<>(slot, values.get(index)));
        if (current > 0)
            menu.button(
                    45,
                    Material.ARROW,
                    "&ePrevious",
                    List.of(),
                    (p, click) -> navigate.accept(p, current - 1));
        if ((current + 1) * 45 < values.size())
            menu.button(
                    53,
                    Material.ARROW,
                    "&eNext",
                    List.of(),
                    (p, click) -> navigate.accept(p, current + 1));
        close(menu);
        menu.open(player);
        return menu;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0
                || event.getRawSlot() >= 54) return;
        if (!event.getClick().isLeftClick() && !event.getClick().isRightClick()) return;
        int slot = event.getRawSlot();
        var click = event.getClick();
        org.bukkit.Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (!player.isOnline()
                                    || player.getOpenInventory().getTopInventory().getHolder()
                                            != menu) return;
                            try {
                                menu.click(player, slot, click);
                            } catch (IllegalArgumentException ex) {
                                plugin.messages()
                                        .send(player, "invalid", Map.of("reason", ex.getMessage()));
                            }
                        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }
}
