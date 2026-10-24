package io.github.lino9999.customspawners.storage;

import static org.junit.jupiter.api.Assertions.*;

import io.github.lino9999.customspawners.model.BlockPosition;
import io.github.lino9999.customspawners.model.PlacedSpawner;
import io.github.lino9999.customspawners.model.StackKey;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class SpawnerRepositoryTest {
    private PlacedSpawner spawner(UUID world, UUID owner, int x, int z) {
        PlacedSpawner spawner = new PlacedSpawner(new BlockPosition(world, x, 70, z), owner);
        spawner.add(new StackKey("zombie", "tier_1"), 3);
        return spawner;
    }

    @Test
    void indexesRemainConsistentAcrossWorldsNaturalBlocksAndRemoval() {
        SpawnerRepository repository = new SpawnerRepository();
        UUID world = UUID.randomUUID(), otherWorld = UUID.randomUUID(), owner = UUID.randomUUID();
        PlacedSpawner first = spawner(world, owner, -1, -1);
        PlacedSpawner second = spawner(world, owner, -16, -16);
        PlacedSpawner natural = spawner(world, null, -17, -17);
        PlacedSpawner other = spawner(otherWorld, owner, -1, -1);
        repository.put(first);
        repository.put(second);
        repository.put(natural);
        repository.put(other);

        assertEquals(4, repository.all().size());
        assertEquals(3, repository.owned(owner));
        assertEquals(0, repository.owned(null));
        assertEquals(2, repository.inChunk(first.position().chunk()).size());
        assertEquals(1, repository.inChunk(natural.position().chunk()).size());
        assertEquals(1, repository.inChunk(other.position().chunk()).size());

        repository.remove(first.position());
        repository.remove(second.position());
        assertTrue(repository.inChunk(first.position().chunk()).isEmpty());
        assertEquals(1, repository.owned(owner));
        assertEquals(2, repository.all().size());
        assertSame(other, repository.get(other.position()));
        assertSame(natural, repository.get(natural.position()));
    }

    @Test
    void duplicateRegistrationCannotOverwriteOwnerOrChunkIndexes() {
        SpawnerRepository repository = new SpawnerRepository();
        UUID world = UUID.randomUUID(), owner = UUID.randomUUID(), otherOwner = UUID.randomUUID();
        PlacedSpawner first = spawner(world, owner, 16, 16);
        PlacedSpawner duplicate = spawner(world, otherOwner, 16, 16);
        repository.put(first);
        long revision = repository.revision();

        assertThrows(IllegalStateException.class, () -> repository.put(duplicate));

        assertSame(first, repository.get(first.position()));
        assertEquals(1, repository.all().size());
        assertEquals(1, repository.inChunk(first.position().chunk()).size());
        assertEquals(1, repository.owned(owner));
        assertEquals(0, repository.owned(otherOwner));
        assertEquals(revision, repository.revision());
    }

    @Test
    void revisionChangesOnlyForActualMutationsAndExplicitDirtyMarks() {
        SpawnerRepository repository = new SpawnerRepository();
        PlacedSpawner spawner = spawner(UUID.randomUUID(), UUID.randomUUID(), 1, 1);
        assertEquals(0, repository.revision());
        repository.remove(spawner.position());
        assertEquals(0, repository.revision());
        repository.put(spawner);
        repository.dirty();
        repository.remove(spawner.position());
        assertEquals(3, repository.revision());
        repository.remove(spawner.position());
        assertEquals(3, repository.revision());
    }

    @Test
    void callersCannotMutateRepositoryCollections() {
        SpawnerRepository repository = new SpawnerRepository();
        PlacedSpawner spawner = spawner(UUID.randomUUID(), UUID.randomUUID(), 0, 0);
        repository.put(spawner);
        assertThrows(UnsupportedOperationException.class, () -> repository.all().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> repository.inChunk(spawner.position().chunk()).clear());
        assertEquals(1, repository.owned(spawner.owner()));
    }
}
