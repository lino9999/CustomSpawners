package io.github.lino9999.customspawners.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

class PlayerProgressTest {
    @Test
    void progressIsIsolatedByPlayerAndMobAndResetDoesNotAffectOthers() {
        PlayerProgress progress = new PlayerProgress();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertEquals(1, progress.increment(first, "zombie"));
        assertEquals(2, progress.increment(first, "zombie"));
        assertEquals(1, progress.increment(first, "skeleton"));
        assertEquals(1, progress.increment(second, "zombie"));
        progress.set(first, "zombie", 0);

        assertEquals(Map.of("zombie", 0L, "skeleton", 1L), progress.get(first));
        assertEquals(Map.of("zombie", 1L), progress.get(second));
        assertTrue(progress.get(UUID.randomUUID()).isEmpty());
    }

    @Test
    void persistedMaximumCountSaturatesInsteadOfWrappingNegative() {
        PlayerProgress progress = new PlayerProgress();
        UUID player = UUID.randomUUID();
        progress.set(player, "zombie", Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE, progress.increment(player, "zombie"));
        assertEquals(Long.MAX_VALUE, progress.increment(player, "zombie"));
    }

    @Test
    void invalidLoadedCountLeavesPreviousValueIntact() {
        PlayerProgress progress = new PlayerProgress();
        UUID player = UUID.randomUUID();
        progress.set(player, "zombie", 42);
        assertThrows(IllegalArgumentException.class, () -> progress.set(player, "zombie", -1));
        assertEquals(42L, progress.get(player).get("zombie"));
    }

    @Test
    void snapshotsAreDeeplyDetachedAndCannotMutateLiveProgress() {
        PlayerProgress progress = new PlayerProgress();
        UUID player = UUID.randomUUID();
        progress.set(player, "zombie", 5);
        Map<UUID, Map<String, Long>> snapshot = progress.snapshot();
        Map<String, Long> playerSnapshot = progress.get(player);
        progress.increment(player, "zombie");
        progress.increment(player, "skeleton");

        assertEquals(Map.of("zombie", 5L), snapshot.get(player));
        assertEquals(Map.of("zombie", 5L), playerSnapshot);
        assertThrows(
                UnsupportedOperationException.class, () -> snapshot.get(player).put("zombie", 99L));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, playerSnapshot::clear);
        assertEquals(Map.of("zombie", 6L, "skeleton", 1L), progress.get(player));
    }

    @Test
    void invalidIdentityCannotPoisonPersistenceSnapshots() {
        PlayerProgress progress = new PlayerProgress();
        UUID player = UUID.randomUUID();
        progress.set(player, "zombie", 5);
        assertThrows(NullPointerException.class, () -> progress.increment(null, "zombie"));
        assertThrows(IllegalArgumentException.class, () -> progress.increment(player, null));
        assertThrows(IllegalArgumentException.class, () -> progress.set(player, " ", 3));
        assertEquals(Map.of(player, Map.of("zombie", 5L)), progress.snapshot());
    }
}
