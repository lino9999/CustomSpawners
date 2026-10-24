package io.github.lino9999.customspawners.storage;

import static org.junit.jupiter.api.Assertions.*;

import io.github.lino9999.customspawners.model.StackKey;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class StateDecodeTest {
    private Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("world", UUID.randomUUID().toString());
        row.put("x", -17);
        row.put("y", -64);
        row.put("z", 16);
        row.put("owner", "");
        row.put("cycles", 42L);
        row.put("boost", 3);
        row.put("expires", Long.MAX_VALUE);
        row.put("stacks", List.of(Map.of("type", "zombie", "tier", "tier_1", "amount", 5)));
        return row;
    }

    private YamlConfiguration state(Object rows) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        yaml.set("spawners", rows);
        return yaml;
    }

    @Test
    void naturalOwnersAndPermanentBoostsDecodeWithoutTouchingTheRepository() throws Exception {
        SpawnerRepository repository = new SpawnerRepository();
        var state = StateStore.decode(state(List.of(row())), repository);
        var spawner = state.spawners().getFirst();
        assertNull(spawner.owner());
        assertEquals(-17, spawner.position().x());
        assertEquals(-64, spawner.position().y());
        assertEquals(42, spawner.cycles());
        assertEquals(3, spawner.boost().activeAmount(Long.MAX_VALUE));
        assertEquals(Map.of(new StackKey("zombie", "tier_1"), 5), spawner.stacks());
        assertTrue(repository.all().isEmpty());
    }

    @Test
    void malformedRowsCannotBeSilentlyFilteredOut() {
        for (Object invalid :
                List.of("broken", Map.of("wrong", "shape"), List.of(row(), "broken"))) {
            assertThrows(
                    Exception.class,
                    () -> StateStore.decode(state(invalid), new SpawnerRepository()));
        }
        assertThrows(
                Exception.class, () -> StateStore.decode(state(null), new SpawnerRepository()));
    }

    @Test
    void duplicatePositionsAndDuplicateStackKeysAreRejected() {
        Map<String, Object> row = row();
        assertThrows(
                Exception.class,
                () ->
                        StateStore.decode(
                                state(List.of(row, new LinkedHashMap<>(row))),
                                new SpawnerRepository()));
        row.put(
                "stacks",
                List.of(
                        Map.of("type", "zombie", "tier", "tier_1", "amount", 1),
                        Map.of("type", "zombie", "tier", "tier_1", "amount", 2)));
        assertThrows(
                Exception.class,
                () -> StateStore.decode(state(List.of(row)), new SpawnerRepository()));
    }

    @Test
    void invalidLateRowsDoNotPublishEarlierValidRows() {
        SpawnerRepository repository = new SpawnerRepository();
        Map<String, Object> invalid = row();
        invalid.put("x", 1.5);
        assertThrows(
                Exception.class,
                () -> StateStore.decode(state(List.of(row(), invalid)), repository));
        assertTrue(repository.all().isEmpty());
        assertEquals(0, repository.revision());
    }

    @Test
    void fractionalOverflowAndNegativeCountsCannotBeTruncatedIntoValidState() {
        for (Object amount : List.of(-1, 0, 1.5, 4_294_967_297L)) {
            Map<String, Object> row = row();
            row.put(
                    "stacks",
                    List.of(Map.of("type", "zombie", "tier", "tier_1", "amount", amount)));
            assertThrows(
                    Exception.class,
                    () -> StateStore.decode(state(List.of(row)), new SpawnerRepository()));
        }
    }

    @Test
    void malformedPlayerSectionsAndKillCountsAreRejected() {
        UUID player = UUID.randomUUID();
        YamlConfiguration yaml = state(new ArrayList<>());
        yaml.set("players", "broken");
        assertThrows(Exception.class, () -> StateStore.decode(yaml, new SpawnerRepository()));
        yaml.set("players", null);
        yaml.set("players." + player + ".kills.zombie", 1.5);
        assertThrows(Exception.class, () -> StateStore.decode(yaml, new SpawnerRepository()));
        yaml.set("players." + player + ".kills.zombie", Long.MAX_VALUE);
        assertDoesNotThrow(() -> StateStore.decode(yaml, new SpawnerRepository()));
    }
}
