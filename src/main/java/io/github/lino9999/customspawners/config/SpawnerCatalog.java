package io.github.lino9999.customspawners.config;

import io.github.lino9999.customspawners.model.SpawnConditions;
import io.github.lino9999.customspawners.model.SpawnOutput;
import io.github.lino9999.customspawners.model.SpawnerTier;
import io.github.lino9999.customspawners.model.SpawnerType;
import io.github.lino9999.customspawners.model.StackKey;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SpawnerCatalog {
    private static final Set<String> TYPE_KEYS =
            Set.of(
                    "name",
                    "icon",
                    "enabled",
                    "shop",
                    "shop-order",
                    "price",
                    "convertible",
                    "conversion-cost",
                    "egg-cost",
                    "kill-goal",
                    "kill-chance",
                    "craftable",
                    "recipe",
                    "ingredients",
                    "tiers");
    private static final Set<String> TIER_KEYS =
            Set.of(
                    "name",
                    "min-delay",
                    "max-delay",
                    "min-spawn",
                    "max-spawn",
                    "spawn-range",
                    "upgrade-money",
                    "upgrade-levels",
                    "pickup-cost",
                    "spawn-limit",
                    "ai",
                    "baby",
                    "on-fire",
                    "entity-name",
                    "particle",
                    "conditions",
                    "outputs",
                    "drops");
    private static final Set<String> CONDITION_KEYS =
            Set.of(
                    "biomes",
                    "min-y",
                    "max-y",
                    "min-light",
                    "max-light",
                    "nearby-players",
                    "player-range",
                    "max-entities",
                    "entity-range",
                    "storm",
                    "spawn-on");
    private final JavaPlugin plugin;
    private final File file;
    private volatile Map<String, SpawnerType> types = Map.of();
    private YamlConfiguration configuration;

    public SpawnerCatalog(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawners.yml");
    }

    public synchronized void reload() throws IllegalArgumentException {
        try {
            YamlConfiguration candidate = new YamlConfiguration();
            if (file.isFile()) {
                candidate.load(file);
            } else {
                try (InputStream input = plugin.getResource("spawners.yml")) {
                    if (input == null) throw invalid("spawners.yml", "Bundled catalog is missing");
                    candidate.load(new InputStreamReader(input, StandardCharsets.UTF_8));
                }
            }
            boolean changed = addEntityDefaults(candidate);
            Map<String, SpawnerType> parsed = parse(candidate);
            if (changed || !file.isFile()) write(candidate);
            configuration = candidate;
            types = parsed;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalArgumentException(
                    "Unable to load spawners.yml: " + exception.getMessage(), exception);
        }
    }

    public Collection<SpawnerType> all() {
        return types.values();
    }

    public boolean availableInShop(SpawnerType type) {
        return type != null
                && type.enabled()
                && type.shop()
                && type.tiers().values().stream().allMatch(SpawnerCatalog::mobTier);
    }

    private static boolean mobTier(SpawnerTier tier) {
        return !tier.outputs().isEmpty()
                && tier.outputs().stream().allMatch(SpawnerCatalog::mobOutput);
    }

    private static boolean mobOutput(SpawnOutput output) {
        return output.kind() == SpawnOutput.Kind.ENTITY
                && isMob(EntityType.valueOf(output.value()));
    }

    private static boolean isMob(EntityType entity) {
        return entity.isSpawnable()
                && entity.getEntityClass() != null
                && org.bukkit.entity.Mob.class.isAssignableFrom(entity.getEntityClass());
    }

    public SpawnerType get(String id) {
        return id == null ? null : types.get(normalize(id));
    }

    public SpawnerTier tier(StackKey key) {
        SpawnerType type = get(key.type());
        return type == null ? null : type.tiers().get(normalize(key.tier()));
    }

    public File file() {
        return file;
    }

    public synchronized void edit(String path, Object value) throws IllegalArgumentException {
        if (configuration == null) throw invalid("catalog", "Catalog has not been loaded");
        if (path == null
                || path.isBlank()
                || path.startsWith(".")
                || path.endsWith(".")
                || path.contains("..")) {
            throw invalid("path", "A valid configuration path is required");
        }
        try {
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.loadFromString(configuration.saveToString());
            candidate.set(path, value);
            Map<String, SpawnerType> parsed = parse(candidate);
            write(candidate);
            configuration = candidate;
            types = parsed;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalArgumentException(
                    "Unable to save spawners.yml: " + exception.getMessage(), exception);
        }
    }

    private void write(YamlConfiguration candidate) throws IOException {
        Path parent = file.toPath().toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "spawners-", ".tmp");
        try {
            Files.writeString(temporary, candidate.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Map<String, SpawnerType> parse(YamlConfiguration yaml) {
        Map<String, Object> root = section(yaml, "catalog");
        known(
                root,
                Set.of("schema-version", "generate-mob-spawners", "defaults", "spawners"),
                "catalog");
        integer(root, "schema-version", 1, 1, 1, "catalog");
        bool(root, "generate-mob-spawners", true, "catalog");
        Map<String, Object> defaults = object(root, "defaults", "catalog");
        known(defaults, Set.of("type", "tier"), "defaults");
        Map<String, Object> typeDefaults = object(defaults, "type", "defaults");
        Map<String, Object> tierDefaults = object(defaults, "tier", "defaults");
        known(typeDefaults, TYPE_KEYS, "defaults.type");
        if (typeDefaults.containsKey("tiers"))
            throw invalid("defaults.type.tiers", "Define tiers on individual spawners");
        known(tierDefaults, TIER_KEYS, "defaults.tier");
        Map<String, Object> entries = object(root, "spawners", "catalog");
        if (entries.isEmpty()) throw invalid("spawners", "At least one spawner type is required");
        LinkedHashMap<String, SpawnerType> result = new LinkedHashMap<>();
        for (var entry : entries.entrySet()) {
            String id = identifier(entry.getKey(), "spawners");
            String path = "spawners." + entry.getKey();
            Map<String, Object> values = merge(typeDefaults, asObject(entry.getValue(), path));
            known(values, TYPE_KEYS, path);
            if (result.containsKey(id))
                throw invalid(path, "Duplicate normalized spawner identifier");
            Map<String, Object> tierEntries = object(values, "tiers", path);
            if (tierEntries.isEmpty())
                throw invalid(path + ".tiers", "At least one tier is required");
            LinkedHashMap<String, SpawnerTier> tiers = new LinkedHashMap<>();
            for (var tierEntry : tierEntries.entrySet()) {
                String tierId = identifier(tierEntry.getKey(), path + ".tiers");
                if (tiers.containsKey(tierId))
                    throw invalid(path + ".tiers", "Duplicate normalized tier identifier");
                tiers.put(
                        tierId,
                        parseTier(
                                tierId,
                                merge(
                                        tierDefaults,
                                        asObject(tierEntry.getValue(), path + ".tiers." + tierId)),
                                path + ".tiers." + tierId));
            }
            String icon =
                    material(text(values, "icon", "SPAWNER", path), false, path + ".icon").name();
            List<String> recipe = strings(values, "recipe", path);
            Map<Character, String> ingredients = ingredients(values, recipe, path);
            boolean craftable = bool(values, "craftable", false, path);
            if (craftable && (recipe.isEmpty() || ingredients.isEmpty()))
                throw invalid(
                        path + ".recipe", "Craftable spawners require a recipe and ingredients");
            result.put(
                    id,
                    new SpawnerType(
                            id,
                            text(values, "name", humanize(id), path),
                            icon,
                            bool(values, "enabled", true, path),
                            bool(values, "shop", true, path),
                            integer(values, "shop-order", 0, 0, 1_000_000, path),
                            number(values, "price", 1000, 0, 1_000_000_000_000D, path),
                            bool(values, "convertible", true, path),
                            number(values, "conversion-cost", 4.5, 0, 1_000_000_000D, path),
                            integer(values, "egg-cost", 1, 0, 1_000_000, path),
                            integer(values, "kill-goal", 0, 0, 1_000_000_000, path),
                            number(values, "kill-chance", 0, 0, 100, path),
                            craftable,
                            recipe,
                            ingredients,
                            Collections.unmodifiableMap(tiers)));
        }
        return Collections.unmodifiableMap(result);
    }

    private static SpawnerTier parseTier(String id, Map<String, Object> values, String path) {
        known(values, TIER_KEYS, path);
        int minDelay = integer(values, "min-delay", 200, 1, 72_000, path);
        int maxDelay = integer(values, "max-delay", 800, minDelay, 72_000, path);
        int minSpawn = integer(values, "min-spawn", 1, 1, 256, path);
        int maxSpawn = integer(values, "max-spawn", 4, minSpawn, 256, path);
        String particle = text(values, "particle", "", path).toUpperCase(Locale.ROOT);
        if (!particle.isEmpty()) {
            try {
                Particle parsed = Particle.valueOf(particle);
                if (parsed.getDataType() != Void.class)
                    throw invalid(path + ".particle", "Particle requires unsupported extra data");
            } catch (IllegalArgumentException exception) {
                throw invalid(path + ".particle", "Unknown or unsupported particle: " + particle);
            }
        }
        List<SpawnOutput> outputs = outputs(values, path);
        if (outputs.isEmpty()) throw invalid(path + ".outputs", "At least one output is required");
        return new SpawnerTier(
                id,
                text(values, "name", "Tier " + id, path),
                minDelay,
                maxDelay,
                minSpawn,
                maxSpawn,
                number(values, "spawn-range", 4, 0, 32, path),
                number(values, "upgrade-money", 10, 0, 1_000_000_000D, path),
                integer(values, "upgrade-levels", 2, 0, 10000, path),
                number(values, "pickup-cost", 0, 0, 1_000_000_000D, path),
                whole(values, "spawn-limit", 0, 0, 1_000_000_000_000L, path),
                bool(values, "ai", true, path),
                bool(values, "baby", false, path),
                bool(values, "on-fire", false, path),
                text(values, "entity-name", "", path),
                particle,
                conditions(object(values, "conditions", path), path + ".conditions"),
                outputs,
                drops(values, path));
    }

    private static SpawnConditions conditions(Map<String, Object> values, String path) {
        known(values, CONDITION_KEYS, path);
        LinkedHashSet<String> biomes = new LinkedHashSet<>();
        for (String value : strings(values, "biomes", path)) {
            NamespacedKey key = NamespacedKey.fromString(value.toLowerCase(Locale.ROOT));
            if (key == null || Registry.BIOME.get(key) == null)
                throw invalid(path + ".biomes", "Unknown biome: " + value);
            biomes.add(
                    key.getNamespace().equals(NamespacedKey.MINECRAFT)
                            ? key.getKey().toUpperCase(Locale.ROOT)
                            : key.toString());
        }
        LinkedHashSet<String> spawnOn = new LinkedHashSet<>();
        for (String value : strings(values, "spawn-on", path))
            spawnOn.add(material(value, true, path + ".spawn-on").name());
        int minY = integer(values, "min-y", -64, -2048, 2048, path);
        int maxY = integer(values, "max-y", 320, minY, 2048, path);
        int minLight = integer(values, "min-light", 0, 0, 15, path);
        int maxLight = integer(values, "max-light", 15, minLight, 15, path);
        return new SpawnConditions(
                Collections.unmodifiableSet(biomes),
                minY,
                maxY,
                minLight,
                maxLight,
                integer(values, "nearby-players", 1, 0, 10000, path),
                number(values, "player-range", 16, 1, 256, path),
                integer(values, "max-entities", 6, 1, 10000, path),
                number(values, "entity-range", 8, 1, 128, path),
                bool(values, "storm", false, path),
                Collections.unmodifiableSet(spawnOn));
    }

    private static List<SpawnOutput> outputs(Map<String, Object> values, String path) {
        List<SpawnOutput> result = new ArrayList<>();
        int index = 0;
        for (Object value : list(values, "outputs", path)) {
            String at = path + ".outputs[" + index++ + "]";
            Map<String, Object> output = asObject(value, at);
            known(output, Set.of("kind", "value", "chance", "amount"), at);
            SpawnOutput.Kind kind;
            try {
                kind =
                        SpawnOutput.Kind.valueOf(
                                text(output, "kind", "", at).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw invalid(at + ".kind", "Expected ENTITY, ITEM, BLOCK or COMMAND");
            }
            String target = text(output, "value", "", at);
            switch (kind) {
                case ENTITY -> {
                    EntityType entity;
                    try {
                        entity = EntityType.valueOf(target.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        throw invalid(at + ".value", "Unknown entity: " + target);
                    }
                    if (!entity.isAlive() || !entity.isSpawnable())
                        throw invalid(at + ".value", "Entity must be alive and spawnable");
                    target = entity.name();
                }
                case ITEM -> target = material(target, false, at + ".value").name();
                case BLOCK -> target = material(target, true, at + ".value").name();
                case COMMAND -> target = command(target, at + ".value");
            }
            int amount =
                    integer(output, "amount", 1, 1, kind == SpawnOutput.Kind.ITEM ? 4096 : 256, at);
            result.add(
                    new SpawnOutput(
                            kind, target, number(output, "chance", 100, 0, 100, at), amount));
        }
        if (result.size() > 64) throw invalid(path + ".outputs", "At most 64 outputs are allowed");
        return List.copyOf(result);
    }

    private static List<SpawnerTier.LootDrop> drops(Map<String, Object> values, String path) {
        List<SpawnerTier.LootDrop> result = new ArrayList<>();
        int index = 0;
        for (Object value : list(values, "drops", path)) {
            String at = path + ".drops[" + index++ + "]";
            Map<String, Object> drop = asObject(value, at);
            known(
                    drop,
                    Set.of(
                            "material",
                            "min",
                            "max",
                            "chance",
                            "name",
                            "lore",
                            "command",
                            "experience"),
                    at);
            String material = text(drop, "material", "", at);
            if (!material.isBlank()) material = material(material, false, at + ".material").name();
            String command = text(drop, "command", "", at);
            if (!command.isBlank()) command = command(command, at + ".command");
            int experience = integer(drop, "experience", 0, 0, 10000, at);
            if (material.isBlank() && command.isBlank() && experience == 0)
                throw invalid(at, "A drop needs material, a command or experience");
            int min = integer(drop, "min", 1, 1, 4096, at);
            int max = integer(drop, "max", min, min, 4096, at);
            result.add(
                    new SpawnerTier.LootDrop(
                            material,
                            min,
                            max,
                            number(drop, "chance", 100, 0, 100, at),
                            text(drop, "name", "", at),
                            strings(drop, "lore", at),
                            command,
                            experience));
        }
        if (result.size() > 64) throw invalid(path + ".drops", "At most 64 drops are allowed");
        return List.copyOf(result);
    }

    private static Map<Character, String> ingredients(
            Map<String, Object> values, List<String> recipe, String path) {
        if (!recipe.isEmpty()
                && (recipe.size() > 3
                        || recipe.getFirst().isEmpty()
                        || recipe.getFirst().length() > 3)) {
            throw invalid(
                    path + ".recipe",
                    "Recipe must contain one to three rows of one to three characters");
        }
        for (String row : recipe)
            if (row.length() != recipe.getFirst().length())
                throw invalid(path + ".recipe", "Recipe rows must have equal lengths");
        LinkedHashMap<Character, String> result = new LinkedHashMap<>();
        for (var entry : object(values, "ingredients", path).entrySet()) {
            String key = entry.getKey();
            if (key.length() != 1 || key.charAt(0) == ' ')
                throw invalid(
                        path + ".ingredients",
                        "Ingredient keys must be single non-space characters");
            if (recipe.stream().noneMatch(row -> row.contains(key)))
                throw invalid(path + ".ingredients." + key, "Ingredient is not used in the recipe");
            result.put(
                    key.charAt(0),
                    material(String.valueOf(entry.getValue()), false, path + ".ingredients." + key)
                            .name());
        }
        for (String row : recipe)
            for (char key : row.toCharArray()) {
                if (key != ' ' && !result.containsKey(key))
                    throw invalid(path + ".recipe", "Missing ingredient: " + key);
            }
        return Collections.unmodifiableMap(result);
    }

    private static boolean addEntityDefaults(YamlConfiguration yaml) {
        if (!yaml.getBoolean("generate-mob-spawners", true)) return false;
        ConfigurationSection entries = yaml.getConfigurationSection("spawners");
        if (entries == null && yaml.contains("spawners"))
            throw invalid("spawners", "Expected a mapping");
        Set<String> existing = new LinkedHashSet<>();
        if (entries != null) for (String id : entries.getKeys(false)) existing.add(normalize(id));
        boolean changed = false;
        for (EntityType entity : EntityType.values()) {
            if (!isMob(entity)) continue;
            String id = entity.name().toLowerCase(Locale.ROOT);
            if (existing.contains(id)) continue;
            String at = "spawners." + id;
            yaml.set(at + ".name", humanize(id) + " Spawner");
            Material egg = Material.matchMaterial(entity.name() + "_SPAWN_EGG");
            yaml.set(at + ".icon", egg == null ? "SPAWNER" : egg.name());
            String tier = at + ".tiers.1";
            yaml.set(tier + ".name", "Tier 1");
            yaml.set(
                    tier + ".outputs",
                    List.of(
                            Map.of(
                                    "kind",
                                    "ENTITY",
                                    "value",
                                    entity.name(),
                                    "chance",
                                    100,
                                    "amount",
                                    1)));
            Class<?> entityClass = entity.getEntityClass();
            yaml.set(
                    tier + ".conditions.max-light",
                    entityClass != null && Monster.class.isAssignableFrom(entityClass) ? 7 : 15);
            if (entity == EntityType.SLIME) {
                yaml.set(tier + ".conditions.biomes", List.of("SWAMP"));
                yaml.set(tier + ".conditions.min-y", 50);
                yaml.set(tier + ".conditions.max-y", 70);
            }
            changed = true;
        }
        return changed;
    }

    private static Material material(String value, boolean block, String path) {
        Material material = Material.matchMaterial(value);
        if (material == null
                || material.isAir()
                || (block ? !material.isBlock() : !material.isItem())) {
            throw invalid(
                    path,
                    "Unknown or unsupported " + (block ? "block" : "item") + " material: " + value);
        }
        return material;
    }

    private static String command(String value, String path) {
        String command = value.trim();
        if (command.isBlank()
                || command.startsWith("/")
                || command.length() > 2048
                || value.chars().anyMatch(Character::isISOControl)
                || !command.split("\\s+", 2)[0].matches("[A-Za-z0-9_:.\\-]+")) {
            throw invalid(
                    path,
                    "Commands must contain a command label without a leading slash or control"
                            + " characters");
        }
        return command;
    }

    private static String identifier(String value, String path) {
        String id = normalize(value);
        if (!id.matches("[a-z0-9][a-z0-9_-]{0,63}"))
            throw invalid(
                    path,
                    "Identifiers must contain 1 to 64 letters, digits, underscores or hyphens");
        return id;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String humanize(String value) {
        List<String> words = new ArrayList<>();
        for (String word : value.toLowerCase(Locale.ROOT).split("[_-]")) {
            if (!word.isEmpty())
                words.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", words);
    }

    private static Map<String, Object> merge(
            Map<String, Object> defaults, Map<String, Object> values) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(defaults);
        for (var entry : values.entrySet()) {
            Object previous = merged.get(entry.getKey());
            if (previous instanceof Map<?, ?> && entry.getValue() instanceof Map<?, ?>) {
                merged.put(
                        entry.getKey(),
                        merge(
                                asObject(previous, entry.getKey()),
                                asObject(entry.getValue(), entry.getKey())));
            } else merged.put(entry.getKey(), entry.getValue());
        }
        return merged;
    }

    private static Map<String, Object> section(ConfigurationSection section, String path) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (var entry : section.getValues(false).entrySet()) {
            result.put(
                    entry.getKey(),
                    entry.getValue() instanceof ConfigurationSection child
                            ? section(child, path + "." + entry.getKey())
                            : entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> asObject(Object value, String path) {
        if (value instanceof ConfigurationSection section) return section(section, path);
        if (!(value instanceof Map<?, ?> map)) throw invalid(path, "Expected a mapping");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String))
                throw invalid(path, "Mapping keys must be strings; quote numeric tier identifiers");
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> object(Map<String, Object> values, String key, String path) {
        return values.containsKey(key) ? asObject(values.get(key), path + "." + key) : Map.of();
    }

    private static void known(Map<String, Object> values, Set<String> allowed, String path) {
        for (String key : values.keySet())
            if (!allowed.contains(key)) throw invalid(path + "." + key, "Unknown option");
    }

    private static String text(
            Map<String, Object> values, String key, String fallback, String path) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String string)
                || string.length() > 8192
                || string.indexOf('\0') >= 0)
            throw invalid(path + "." + key, "Expected a string of at most 8192 characters");
        return string;
    }

    private static boolean bool(
            Map<String, Object> values, String key, boolean fallback, String path) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Boolean parsed))
            throw invalid(path + "." + key, "Expected true or false");
        return parsed;
    }

    private static double number(
            Map<String, Object> values,
            String key,
            double fallback,
            double min,
            double max,
            String path) {
        Object value = values.get(key);
        if (value == null) value = fallback;
        if (!(value instanceof Number numeric))
            throw invalid(path + "." + key, "Expected a number");
        double parsed = numeric.doubleValue();
        if (!Double.isFinite(parsed) || parsed < min || parsed > max)
            throw invalid(
                    path + "." + key, "Expected a finite number between " + min + " and " + max);
        return parsed;
    }

    private static long whole(
            Map<String, Object> values,
            String key,
            long fallback,
            long min,
            long max,
            String path) {
        double parsed = number(values, key, fallback, min, max, path);
        if (parsed != Math.rint(parsed)) throw invalid(path + "." + key, "Expected a whole number");
        return (long) parsed;
    }

    private static int integer(
            Map<String, Object> values, String key, int fallback, int min, int max, String path) {
        return (int) whole(values, key, fallback, min, max, path);
    }

    private static List<?> list(Map<String, Object> values, String key, String path) {
        Object value = values.get(key);
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw invalid(path + "." + key, "Expected a list");
        return list;
    }

    private static List<String> strings(Map<String, Object> values, String key, String path) {
        List<String> result = new ArrayList<>();
        for (Object value : list(values, key, path)) {
            if (!(value instanceof String string) || string.length() > 8192)
                throw invalid(path + "." + key, "Expected a list of strings");
            result.add(string);
        }
        return List.copyOf(result);
    }

    private static IllegalArgumentException invalid(String path, String reason) {
        return new IllegalArgumentException(path + ": " + reason + ".");
    }
}
