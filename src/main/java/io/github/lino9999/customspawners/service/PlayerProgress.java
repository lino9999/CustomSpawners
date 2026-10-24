package io.github.lino9999.customspawners.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlayerProgress {
    private final Map<UUID, Map<String, Long>> kills = new HashMap<>();

    public long increment(UUID player, String type) {
        validate(player, type);
        return kills.computeIfAbsent(player, key -> new LinkedHashMap<>())
                .merge(type, 1L, (a, b) -> a == Long.MAX_VALUE ? a : a + b);
    }

    public void set(UUID player, String type, long amount) {
        validate(player, type);
        if (amount < 0) throw new IllegalArgumentException("Kill count cannot be negative.");
        kills.computeIfAbsent(player, key -> new LinkedHashMap<>()).put(type, amount);
    }

    public Map<String, Long> get(UUID player) {
        return Map.copyOf(kills.getOrDefault(player, Map.of()));
    }

    public Map<UUID, Map<String, Long>> snapshot() {
        Map<UUID, Map<String, Long>> result = new HashMap<>();
        kills.forEach((player, values) -> result.put(player, Map.copyOf(values)));
        return Map.copyOf(result);
    }

    private void validate(UUID player, String type) {
        Objects.requireNonNull(player, "Player identity is required.");
        if (type == null || type.isBlank())
            throw new IllegalArgumentException("Spawner type is required.");
    }
}
