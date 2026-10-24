package io.github.lino9999.customspawners.model;

import java.util.List;
import java.util.Map;

public record SpawnerType(
        String id,
        String name,
        String icon,
        boolean enabled,
        boolean shop,
        int shopOrder,
        double price,
        boolean convertible,
        double conversionCost,
        int eggCost,
        int killGoal,
        double killChance,
        boolean craftable,
        List<String> recipe,
        Map<Character, String> ingredients,
        Map<String, SpawnerTier> tiers) {
    public SpawnerTier firstTier() {
        return tiers.values().iterator().next();
    }

    public SpawnerTier nextTier(String tier) {
        boolean found = false;
        for (SpawnerTier value : tiers.values()) {
            if (found) return value;
            found = value.id().equals(tier);
        }
        return null;
    }
}
