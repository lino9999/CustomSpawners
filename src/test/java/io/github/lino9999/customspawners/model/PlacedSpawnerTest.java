package io.github.lino9999.customspawners.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

class PlacedSpawnerTest {
    private final StackKey zombie = new StackKey("zombie", "tier_1");
    private final StackKey skeleton = new StackKey("skeleton", "tier_1");
    private final StackKey upgraded = new StackKey("zombie", "tier_2");

    private PlacedSpawner spawner() {
        return new PlacedSpawner(
                new BlockPosition(UUID.randomUUID(), -17, 70, 16), UUID.randomUUID());
    }

    @Test
    void mixedStacksRetainQuantitiesWhenUpgradingIntoAnExistingEntry() {
        PlacedSpawner spawner = spawner();
        spawner.add(zombie, 3);
        spawner.add(skeleton, 2);
        spawner.add(upgraded, 4);

        spawner.replace(zombie, upgraded);

        assertEquals(9, spawner.total());
        assertEquals(Map.of(skeleton, 2, upgraded, 7), spawner.stacks());
        assertFalse(spawner.stacks().containsKey(zombie));
        assertEquals(skeleton, spawner.first());
    }

    @Test
    void removingAnEntireEntryKeepsOtherEntriesAndTheirOrder() {
        PlacedSpawner spawner = spawner();
        spawner.add(zombie, 3);
        spawner.add(skeleton, 2);
        spawner.remove(zombie, 1);
        assertEquals(4, spawner.total());
        spawner.remove(zombie, 2);
        assertEquals(Map.of(skeleton, 2), spawner.stacks());
        assertEquals(skeleton, spawner.first());
    }

    @Test
    void failedArithmeticDoesNotPartiallyMutateStacks() {
        PlacedSpawner spawner = spawner();
        spawner.add(zombie, 999_999);
        spawner.add(skeleton, 1);
        Map<StackKey, Integer> before = new LinkedHashMap<>(spawner.stacks());

        assertThrows(IllegalArgumentException.class, () -> spawner.add(upgraded, 1));
        assertThrows(IllegalArgumentException.class, () -> spawner.add(zombie, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> spawner.add(zombie, 0));
        assertThrows(IllegalArgumentException.class, () -> spawner.add(zombie, -1));
        assertThrows(IllegalArgumentException.class, () -> spawner.remove(zombie, 1_000_000));
        assertThrows(IllegalArgumentException.class, () -> spawner.remove(upgraded, 1));
        assertThrows(IllegalArgumentException.class, () -> spawner.remove(zombie, 0));

        assertEquals(before, spawner.stacks());
        assertEquals(1_000_000, spawner.total());
    }

    @Test
    void replacingAnEntryAtCapacityDoesNotTemporarilyExceedTheLimit() {
        PlacedSpawner spawner = spawner();
        spawner.add(zombie, 500_000);
        spawner.add(upgraded, 500_000);
        assertDoesNotThrow(() -> spawner.replace(zombie, upgraded));
        assertEquals(Map.of(upgraded, 1_000_000), spawner.stacks());
    }

    @Test
    void replacingMissingOrIdenticalEntryIsANoop() {
        PlacedSpawner spawner = spawner();
        spawner.add(zombie, 3);
        spawner.replace(skeleton, upgraded);
        spawner.replace(zombie, zombie);
        assertEquals(Map.of(zombie, 3), spawner.stacks());
    }

    @Test
    void stackViewCannotBypassQuantityValidation() {
        PlacedSpawner spawner = spawner();
        spawner.add(zombie, 1);
        assertThrows(UnsupportedOperationException.class, () -> spawner.stacks().put(zombie, -1));
        assertThrows(UnsupportedOperationException.class, () -> spawner.stacks().clear());
        assertEquals(1, spawner.total());
    }

    @Test
    void stackIdentityRequiresBothNonblankParts() {
        assertThrows(IllegalArgumentException.class, () -> new StackKey(null, "tier_1"));
        assertThrows(IllegalArgumentException.class, () -> new StackKey("zombie", null));
        assertThrows(IllegalArgumentException.class, () -> new StackKey(" ", "tier_1"));
        assertThrows(IllegalArgumentException.class, () -> new StackKey("zombie", "\t"));
        assertNotEquals(zombie, skeleton);
        assertNotEquals(zombie, upgraded);
    }

    @Test
    void nullMutationArgumentsCannotRemoveOrCorruptExistingEntries() {
        PlacedSpawner spawner = spawner();
        spawner.add(zombie, 3);
        assertThrows(NullPointerException.class, () -> spawner.add(null, 1));
        assertThrows(NullPointerException.class, () -> spawner.remove(null, 1));
        assertThrows(NullPointerException.class, () -> spawner.replace(zombie, null));
        assertThrows(NullPointerException.class, () -> spawner.replace(null, skeleton));
        assertThrows(NullPointerException.class, () -> spawner.boost(null));
        assertThrows(NullPointerException.class, () -> new PlacedSpawner(null, null));
        assertEquals(Map.of(zombie, 3), spawner.stacks());
        assertEquals(Boost.NONE, spawner.boost());
    }
}
