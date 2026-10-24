package io.github.lino9999.customspawners.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpawnerCatalogTest {
    private YamlConfiguration commandCatalog() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(
                """
                schema-version: 1
                generate-mob-spawners: false
                spawners:
                  rewards:
                    tiers:
                      '1':
                        outputs:
                          - kind: COMMAND
                            value: 'give {player} diamond 1'
                """);
        return yaml;
    }

    @Test
    void rejectsUnknownRootOptionsInsteadOfSilentlyIgnoringTypos() throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set("default", "wrong key");
        assertInvalid(yaml, "catalog.default");
    }

    @Test
    void rejectsScalarWhereTierMappingIsRequired() throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set("spawners.rewards.tiers", "tier one");
        assertInvalid(yaml, "spawners.rewards.tiers");
    }

    @Test
    void rejectsUnknownNestedConditionOptions() throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set("spawners.rewards.tiers.1.conditions.player-ragne", 16);
        assertInvalid(yaml, "conditions.player-ragne");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 33})
    void rejectsUnsafeSpawnRanges(double range) throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set("spawners.rewards.tiers.1.spawn-range", range);
        assertInvalid(yaml, "spawn-range");
    }

    @Test
    void rejectsFractionalSpawnCounts() throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set("spawners.rewards.tiers.1.min-spawn", 1.5);
        assertInvalid(yaml, "min-spawn");
    }

    @Test
    void validatesFallbackMaximumAgainstExplicitMinimum() throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set("spawners.rewards.tiers.1.min-delay", 900);
        assertInvalid(yaml, "max-delay");
    }

    @Test
    void rejectsInvertedLightRangeAfterMergingDefaults() throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set("defaults.tier.conditions.max-light", 7);
        yaml.set("spawners.rewards.tiers.1.conditions.min-light", 8);
        assertInvalid(yaml, "max-light");
    }

    @Test
    void rejectsCountOverflowBeforeCreatingAnyItems() throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set("spawners.rewards.tiers.1.max-spawn", Long.MAX_VALUE);
        assertInvalid(yaml, "max-spawn");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/give player diamond",
                "give player diamond\nstop",
                "give player diamond\rstop",
                "say Hello\n",
                "",
                "{player} diamond"
            })
    void rejectsMalformedCommands(String command) throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set(
                "spawners.rewards.tiers.1.outputs",
                java.util.List.of(java.util.Map.of("kind", "COMMAND", "value", command)));
        assertInvalid(yaml, "outputs[0].value");
    }

    @Test
    void rejectsUnknownOutputFields() throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set(
                "spawners.rewards.tiers.1.outputs",
                java.util.List.of(
                        java.util.Map.of("kind", "COMMAND", "value", "say Hello", "weight", 1)));
        assertInvalid(yaml, "outputs[0].weight");
    }

    @Test
    void rejectsProbabilityOutsidePercentRange() throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set(
                "spawners.rewards.tiers.1.outputs",
                java.util.List.of(
                        java.util.Map.of("kind", "COMMAND", "value", "say Hello", "chance", 101)));
        assertInvalid(yaml, "outputs[0].chance");
    }

    @Test
    void rejectsMissingOutputs() throws Exception {
        YamlConfiguration yaml = commandCatalog();
        yaml.set("spawners.rewards.tiers.1.outputs", java.util.List.of());
        assertInvalid(yaml, "outputs");
    }

    private void assertInvalid(YamlConfiguration yaml, String path) {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> SpawnerCatalog.parse(yaml));
        assertTrue(exception.getMessage().contains(path), exception.getMessage());
    }
}
