package io.github.lino9999.customspawners.spawn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.BoundingBox;

import java.util.EnumMap;
import java.util.Map;

final class SpawnSafety {
    private final Map<EntityType, Dimensions> dimensions = new EnumMap<>(EntityType.class);

    boolean loaded(Location location) {
        World world = location.getWorld();
        return world != null
                && location.getY() >= world.getMinHeight()
                && location.getY() < world.getMaxHeight()
                && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                && world.getWorldBorder().isInside(location);
    }

    boolean fits(Location location, EntityType type) {
        if (!loaded(location) || type.getEntityClass() == null) return false;
        Dimensions size =
                dimensions.computeIfAbsent(
                        type,
                        key -> {
                            Entity preview =
                                    location.getWorld()
                                            .createEntity(location, key.getEntityClass());
                            BoundingBox bounds = preview.getBoundingBox();
                            return new Dimensions(
                                    Math.max(0.1, bounds.getWidthX()),
                                    Math.max(0.1, bounds.getHeight()),
                                    Math.max(0.1, bounds.getWidthZ()));
                        });
        BoundingBox box =
                new BoundingBox(
                        location.getX() - size.x() / 2,
                        location.getY(),
                        location.getZ() - size.z() / 2,
                        location.getX() + size.x() / 2,
                        location.getY() + size.y(),
                        location.getZ() + size.z() / 2);
        return clear(location.getWorld(), box, aquatic(type));
    }

    boolean clear(World world, BoundingBox bounds, boolean waterAllowed) {
        int minX = floor(bounds.getMinX() + 0.001);
        int maxX = floor(bounds.getMaxX() - 0.001);
        int minY = floor(bounds.getMinY() + 0.001);
        int maxY = floor(bounds.getMaxY() - 0.001);
        int minZ = floor(bounds.getMinZ() + 0.001);
        int maxZ = floor(bounds.getMaxZ() - 0.001);
        if (minY < world.getMinHeight()
                || maxY >= world.getMaxHeight()
                || (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 4096)
            return false;
        boolean water = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.WATER) {
                        if (!waterAllowed) return false;
                        water = true;
                    } else if (block.isLiquid() || !block.isPassable()) return false;
                }
            }
        }
        return !waterAllowed || water;
    }

    boolean aquatic(EntityType type) {
        return switch (type.name()) {
            case "COD",
                    "SALMON",
                    "PUFFERFISH",
                    "TROPICAL_FISH",
                    "SQUID",
                    "GLOW_SQUID",
                    "DOLPHIN",
                    "GUARDIAN",
                    "ELDER_GUARDIAN",
                    "TADPOLE",
                    "AXOLOTL" ->
                    true;
            default -> false;
        };
    }

    private int floor(double value) {
        return (int) Math.floor(value);
    }

    private record Dimensions(double x, double y, double z) {}
}
