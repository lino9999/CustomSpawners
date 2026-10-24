package io.github.lino9999.customspawners.config;

import io.github.lino9999.customspawners.spawn.SpawnExpression;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SettingsValidator {
    private static final Map<String, Range> NUMBERS = numericOptions();
    private static final Set<String> BOOLEANS =
            Set.of(
                    "spawning.redstone-disable",
                    "spawning.disable-natural",
                    "spawning.no-ai",
                    "spawning.hostile-retaliate-only",
                    "spawning.particles",
                    "spawning.holograms",
                    "stacking.omni",
                    "stacking.deny-nearby",
                    "pickup.silk-touch",
                    "pickup.only-placed",
                    "pickup.sneak-whole-stack",
                    "pickup.always-whole-stack",
                    "pickup.to-inventory",
                    "pickup.prevent-explosions",
                    "conversion.eggs",
                    "conversion.return-old-egg",
                    "upgrades.money",
                    "upgrades.levels",
                    "rewards.enabled",
                    "rewards.count-unnatural",
                    "protection.hooks",
                    "protection.owner-only",
                    "creative.charge");
    private static final Set<String> LISTS = Set.of("blacklist.worlds", "blacklist.types");
    private static final Set<String> STRINGS =
            Set.of("boosts.currency", "economy.provider", "spawning.amount-expression");
    private static final Set<String> LEAVES = leafOptions();
    private static final Set<String> SECTIONS = sectionOptions();

    private SettingsValidator() {}

    public static void validate(FileConfiguration config) throws IllegalArgumentException {
        if (config == null) throw invalid("config.yml", "Configuration is required");
        for (String path : config.getKeys(true)) {
            if (!LEAVES.contains(path) && !SECTIONS.contains(path))
                throw invalid(path, "Unknown option");
        }
        for (String path : SECTIONS) {
            if (!(config.get(path) instanceof ConfigurationSection))
                throw invalid(path, "Expected a configuration section");
        }
        for (var entry : NUMBERS.entrySet()) {
            String path = entry.getKey();
            Range range = entry.getValue();
            Object value = config.get(path);
            if (!(value instanceof Number number)) throw invalid(path, "Expected a number");
            double parsed = number.doubleValue();
            if (!Double.isFinite(parsed) || parsed < range.min() || parsed > range.max()) {
                throw invalid(
                        path,
                        "Expected a finite number between " + range.min() + " and " + range.max());
            }
            if (range.whole() && parsed != Math.rint(parsed))
                throw invalid(path, "Expected a whole number");
        }
        for (String path : BOOLEANS) {
            if (!(config.get(path) instanceof Boolean))
                throw invalid(path, "Expected true or false");
        }
        for (String path : LISTS) {
            if (!(config.get(path) instanceof List<?> values))
                throw invalid(path, "Expected a list of strings");
            if (values.size() > 10000)
                throw invalid(path, "At most 10000 blacklist entries are allowed");
            for (Object value : values) {
                if (!(value instanceof String text)
                        || text.isBlank()
                        || text.length() > 256
                        || text.chars().anyMatch(Character::isISOControl)) {
                    throw invalid(
                            path,
                            "Expected nonempty strings of at most 256 characters without control"
                                + " characters");
                }
            }
        }
        for (String path : STRINGS) {
            if (!(config.get(path) instanceof String value)
                    || value.isBlank()
                    || value.length() > (path.equals("spawning.amount-expression") ? 256 : 128)
                    || !value.equals(value.trim())
                    || value.chars().anyMatch(Character::isISOControl)) {
                throw invalid(
                        path,
                        "Expected a nonempty string without surrounding whitespace or control"
                            + " characters");
            }
        }
        String provider = config.getString("economy.provider").toLowerCase(Locale.ROOT);
        SpawnExpression.compile(config.getString("spawning.amount-expression"));
        if (!Set.of("vault", "playerpoints", "none").contains(provider)) {
            throw invalid("economy.provider", "Choose Vault, PlayerPoints or none");
        }
        String currency = config.getString("boosts.currency").toUpperCase(Locale.ROOT);
        if (!currency.equals("XP") && !currency.equals("MONEY")) {
            Material material = Material.matchMaterial(currency);
            if (material == null
                    || material == Material.AIR
                    || material == Material.CAVE_AIR
                    || material == Material.VOID_AIR
                    || !material.isItem()) {
                throw invalid(
                        "boosts.currency", "Choose XP, MONEY or a valid non-air item material");
            }
        }
    }

    private static Map<String, Range> numericOptions() {
        LinkedHashMap<String, Range> values = new LinkedHashMap<>();
        values.put("spawning.tick-interval", new Range(1, 1200, true));
        values.put("spawning.max-operations-per-tick", new Range(1, 4096, true));
        values.put("spawning.extra-delay", new Range(0, 72000, true));
        values.put("stacking.max-stack", new Range(1, 1_000_000, true));
        values.put("stacking.force-combine-radius", new Range(0, 32, true));
        values.put("limits.per-chunk", new Range(-1, 1_000_000, true));
        values.put("limits.per-player", new Range(-1, 1_000_000, true));
        values.put("pickup.silk-level", new Range(1, 255, true));
        values.put("pickup.natural-chance", new Range(0, 100, false));
        values.put("pickup.placed-chance", new Range(0, 100, false));
        values.put("pickup.tnt-chance", new Range(0, 100, false));
        values.put("pickup.creeper-chance", new Range(0, 100, false));
        values.put("pickup.other-explosion-chance", new Range(0, 100, false));
        values.put("boosts.max-amount", new Range(1, 100, true));
        values.put("boosts.cost-per-minute", new Range(0, 1_000_000, false));
        values.put("rewards.kill-goal", new Range(1, 1_000_000_000, true));
        values.put("rewards.drop-chance", new Range(0, 100, false));
        values.put("rewards.alert-interval", new Range(1, 1_000_000, true));
        values.put("protection.repel-liquids-radius", new Range(0, 8, true));
        values.put("storage.autosave-seconds", new Range(1, 3600, true));
        return Map.copyOf(values);
    }

    private static Set<String> leafOptions() {
        LinkedHashSet<String> values = new LinkedHashSet<>(NUMBERS.keySet());
        values.addAll(BOOLEANS);
        values.addAll(LISTS);
        values.addAll(STRINGS);
        return Set.copyOf(values);
    }

    private static Set<String> sectionOptions() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String path : LEAVES) {
            int index = path.lastIndexOf('.');
            while (index > 0) {
                values.add(path.substring(0, index));
                index = path.lastIndexOf('.', index - 1);
            }
        }
        return Set.copyOf(values);
    }

    private static IllegalArgumentException invalid(String path, String reason) {
        return new IllegalArgumentException(path + ": " + reason + ".");
    }

    private record Range(double min, double max, boolean whole) {}
}
