package io.github.lino9999.customspawners.storage;

import io.github.lino9999.customspawners.model.BlockPosition;
import io.github.lino9999.customspawners.model.BlockPosition.ChunkPosition;
import io.github.lino9999.customspawners.model.PlacedSpawner;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SpawnerRepository {
    private final Map<BlockPosition, PlacedSpawner> all = new LinkedHashMap<>();
    private final Map<ChunkPosition, Map<BlockPosition, PlacedSpawner>> chunks = new HashMap<>();
    private final Map<UUID, Integer> ownerCounts = new HashMap<>();
    private long revision;

    public PlacedSpawner get(BlockPosition position) {
        return all.get(position);
    }

    public Collection<PlacedSpawner> all() {
        return Collections.unmodifiableCollection(all.values());
    }

    public Collection<PlacedSpawner> inChunk(ChunkPosition chunk) {
        return Collections.unmodifiableCollection(chunks.getOrDefault(chunk, Map.of()).values());
    }

    public int owned(UUID owner) {
        return ownerCounts.getOrDefault(owner, 0);
    }

    public long revision() {
        return revision;
    }

    public void dirty() {
        revision++;
    }

    public void put(PlacedSpawner spawner) {
        if (all.containsKey(spawner.position()))
            throw new IllegalStateException("Spawner already registered.");
        all.put(spawner.position(), spawner);
        chunks.computeIfAbsent(spawner.position().chunk(), key -> new LinkedHashMap<>())
                .put(spawner.position(), spawner);
        if (spawner.owner() != null) ownerCounts.merge(spawner.owner(), 1, Integer::sum);
        dirty();
    }

    public void remove(BlockPosition position) {
        PlacedSpawner removed = all.remove(position);
        if (removed == null) return;
        Map<BlockPosition, PlacedSpawner> chunk = chunks.get(position.chunk());
        chunk.remove(position);
        if (chunk.isEmpty()) chunks.remove(position.chunk());
        if (removed.owner() != null)
            ownerCounts.computeIfPresent(
                    removed.owner(), (key, value) -> value <= 1 ? null : value - 1);
        dirty();
    }
}
