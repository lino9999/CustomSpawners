package io.github.lino9999.customspawners.service;

import io.github.lino9999.customspawners.model.Boost;
import io.github.lino9999.customspawners.model.PlacedSpawner;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BoostRegistry {
    private final Map<UUID, Boost> players = new HashMap<>();

    public void put(UUID player, Boost boost) {
        players.put(
                Objects.requireNonNull(player, "Player identity is required."),
                Objects.requireNonNull(boost, "Player boost is required."));
    }

    public Map<UUID, Boost> all() {
        return Map.copyOf(players);
    }

    public Boost get(UUID player) {
        return players.getOrDefault(player, Boost.NONE);
    }

    public int extra(PlacedSpawner spawner, long now) {
        return spawner.boost().activeAmount(now) + get(spawner.owner()).activeAmount(now);
    }

    public void prune(long now) {
        players.values().removeIf(boost -> boost.activeAmount(now) == 0);
    }
}
