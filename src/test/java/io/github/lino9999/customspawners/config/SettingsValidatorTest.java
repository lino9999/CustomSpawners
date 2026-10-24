package io.github.lino9999.customspawners.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

class SettingsValidatorTest {
    private YamlConfiguration defaults() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/config.yml")) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            config.set("boosts.currency", "XP");
            return config;
        }
    }

    @Test
    void acceptsBundledSettingsWithRegistryIndependentCurrency() throws Exception {
        assertDoesNotThrow(() -> SettingsValidator.validate(defaults()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Vault", "PlayerPoints", "none", "vAuLt"})
    void acceptsSupportedEconomyProviders(String provider) throws Exception {
        YamlConfiguration config = defaults();
        config.set("economy.provider", provider);
        assertDoesNotThrow(() -> SettingsValidator.validate(config));
    }

    @Test
    void resolvesRequiredOptionsFromBukkitDefaults() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.setDefaults(defaults());
        assertDoesNotThrow(() -> SettingsValidator.validate(config));
    }

    @ParameterizedTest
    @MethodSource("unsafeNumbers")
    void rejectsValuesThatCouldBreakSchedulingOrOverflow(String path, Object value)
            throws Exception {
        YamlConfiguration config = defaults();
        config.set(path, value);
        assertInvalid(config, path);
    }

    static Stream<Arguments> unsafeNumbers() {
        return Stream.of(
                Arguments.of("spawning.tick-interval", 0),
                Arguments.of("spawning.tick-interval", 1201),
                Arguments.of("spawning.max-operations-per-tick", 4097),
                Arguments.of("spawning.extra-delay", Integer.MAX_VALUE),
                Arguments.of("stacking.max-stack", 1_000_001),
                Arguments.of("stacking.max-stack", 1.5),
                Arguments.of("stacking.force-combine-radius", 33),
                Arguments.of("limits.per-player", -2),
                Arguments.of("limits.per-chunk", Long.MAX_VALUE),
                Arguments.of("pickup.silk-level", 256),
                Arguments.of("pickup.natural-chance", Double.NaN),
                Arguments.of("pickup.placed-chance", Double.POSITIVE_INFINITY),
                Arguments.of("pickup.tnt-chance", -0.1),
                Arguments.of("pickup.creeper-chance", 100.1),
                Arguments.of("pickup.other-explosion-chance", "50"),
                Arguments.of("boosts.max-amount", 101),
                Arguments.of("boosts.cost-per-minute", -1),
                Arguments.of("boosts.cost-per-minute", Double.NEGATIVE_INFINITY),
                Arguments.of("boosts.cost-per-minute", 1_000_001),
                Arguments.of("rewards.kill-goal", 0),
                Arguments.of("rewards.drop-chance", Double.NaN),
                Arguments.of("rewards.alert-interval", 0),
                Arguments.of("protection.repel-liquids-radius", 9),
                Arguments.of("storage.autosave-seconds", 0),
                Arguments.of("storage.autosave-seconds", 3601));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "spawning.no-ai",
                "stacking.omni",
                "pickup.silk-touch",
                "rewards.enabled",
                "creative.charge"
            })
    void rejectsQuotedBooleans(String path) throws Exception {
        YamlConfiguration config = defaults();
        config.set(path, "true");
        assertInvalid(config, path);
    }

    @Test
    void rejectsUnknownKeysToCatchConfigurationTypos() throws Exception {
        YamlConfiguration config = defaults();
        config.set("stacking.maximum", 10);
        assertInvalid(config, "stacking.maximum");
    }

    @Test
    void rejectsScalarSections() throws Exception {
        YamlConfiguration config = defaults();
        config.set("stacking", false);
        assertInvalid(config, "stacking");
    }

    @ParameterizedTest
    @MethodSource("malformedBlacklists")
    void rejectsMalformedBlacklists(Object value) throws Exception {
        YamlConfiguration config = defaults();
        config.set("blacklist.worlds", value);
        assertInvalid(config, "blacklist.worlds");
    }

    static Stream<Arguments> malformedBlacklists() {
        return Stream.of(
                Arguments.of("world,world_nether"),
                Arguments.of(List.of(12)),
                Arguments.of(List.of("")),
                Arguments.of(List.of("world\nstop")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AIR", "CAVE_AIR", "VOID_AIR", "INVALID_CURRENCY", " XP", "XP\n"})
    void rejectsInvalidBoostCurrencies(String currency) throws Exception {
        YamlConfiguration config = defaults();
        config.set("boosts.currency", currency);
        assertInvalid(config, "boosts.currency");
    }

    @Test
    void rejectsUnknownEconomyProvider() throws Exception {
        YamlConfiguration config = defaults();
        config.set("economy.provider", "UnknownBank");
        assertInvalid(config, "economy.provider");
    }

    private void assertInvalid(YamlConfiguration config, String path) {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> SettingsValidator.validate(config));
        assertTrue(exception.getMessage().contains(path), exception.getMessage());
    }
}
