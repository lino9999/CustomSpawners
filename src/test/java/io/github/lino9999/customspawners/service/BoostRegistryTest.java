package io.github.lino9999.customspawners.service;

import static org.junit.jupiter.api.Assertions.*;

import io.github.lino9999.customspawners.model.BlockPosition;
import io.github.lino9999.customspawners.model.Boost;
import io.github.lino9999.customspawners.model.PlacedSpawner;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

class BoostRegistryTest {
    @Test
    void expiryIsExclusiveAndPermanentBoostsSurviveTheBoundary() {
        Boost timed = new Boost(5, 1_000);
        assertEquals(5, timed.activeAmount(999));
        assertEquals(0, timed.activeAmount(1_000));
        assertEquals(0, timed.activeAmount(1_001));
        assertEquals(100, new Boost(100, Long.MAX_VALUE).activeAmount(Long.MAX_VALUE));
        assertEquals(0, Boost.NONE.activeAmount(0));
    }

    @Test
    void ownerAndLocationBonusesCombineWithoutLeakingToOtherPlayersOrNaturalBlocks() {
        UUID owner = UUID.randomUUID();
        BoostRegistry registry = new BoostRegistry();
        registry.put(owner, new Boost(7, 2_000));
        PlacedSpawner owned =
                new PlacedSpawner(new BlockPosition(UUID.randomUUID(), 0, 70, 0), owner);
        owned.boost(new Boost(3, 1_000));
        PlacedSpawner unrelated =
                new PlacedSpawner(
                        new BlockPosition(UUID.randomUUID(), 0, 70, 0), UUID.randomUUID());
        PlacedSpawner natural =
                new PlacedSpawner(new BlockPosition(UUID.randomUUID(), 0, 70, 0), null);

        assertEquals(10, registry.extra(owned, 999));
        assertEquals(7, registry.extra(owned, 1_000));
        assertEquals(0, registry.extra(owned, 2_000));
        assertEquals(0, registry.extra(unrelated, 999));
        assertEquals(0, registry.extra(natural, 999));
    }

    @Test
    void snapshotsDetachAndPruningKeepsOnlyActiveBonuses() {
        UUID expired = UUID.randomUUID(), active = UUID.randomUUID(), permanent = UUID.randomUUID();
        BoostRegistry registry = new BoostRegistry();
        registry.put(expired, new Boost(2, 1_000));
        registry.put(active, new Boost(3, 1_001));
        registry.put(permanent, new Boost(4, Long.MAX_VALUE));
        Map<UUID, Boost> snapshot = registry.all();
        registry.prune(1_000);

        assertEquals(3, snapshot.size());
        assertEquals(2, registry.all().size());
        assertEquals(Boost.NONE, registry.get(expired));
        assertEquals(3, registry.get(active).activeAmount(1_000));
        assertEquals(4, registry.get(permanent).activeAmount(1_000));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void invalidBoostsAreRejectedBeforeTheyCanEnterState() {
        assertThrows(IllegalArgumentException.class, () -> new Boost(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Boost(101, 1));
        assertThrows(IllegalArgumentException.class, () -> new Boost(1, -1));
    }

    @Test
    void nullInputsDoNotPoisonFutureSnapshots() {
        BoostRegistry registry = new BoostRegistry();
        UUID player = UUID.randomUUID();
        registry.put(player, new Boost(2, Long.MAX_VALUE));
        assertThrows(NullPointerException.class, () -> registry.put(null, Boost.NONE));
        assertThrows(NullPointerException.class, () -> registry.put(player, null));
        assertEquals(Map.of(player, new Boost(2, Long.MAX_VALUE)), registry.all());
    }
}
