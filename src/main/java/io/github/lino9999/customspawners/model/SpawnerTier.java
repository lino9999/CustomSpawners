package io.github.lino9999.customspawners.model;

import java.util.List;

public record SpawnerTier(
        String id,
        String name,
        int minDelay,
        int maxDelay,
        int minSpawn,
        int maxSpawn,
        double spawnRange,
        double upgradeMoney,
        int upgradeLevels,
        double pickupCost,
        long spawnLimit,
        boolean ai,
        boolean baby,
        boolean onFire,
        String entityName,
        String particle,
        SpawnConditions conditions,
        List<SpawnOutput> outputs,
        List<LootDrop> drops) {
    public record LootDrop(
            String material,
            int min,
            int max,
            double chance,
            String name,
            List<String> lore,
            String command,
            int experience) {}
}
