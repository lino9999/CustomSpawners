package io.github.lino9999.customspawners.model;

import java.util.Set;

public record SpawnConditions(
        Set<String> biomes,
        int minY,
        int maxY,
        int minLight,
        int maxLight,
        int nearbyPlayers,
        double playerRange,
        int maxEntities,
        double entityRange,
        boolean storm,
        Set<String> spawnOn) {}
