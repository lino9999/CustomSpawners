package io.github.lino9999.customspawners.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;
import java.util.UUID;

public record BlockPosition(UUID world, int x, int y, int z) {
    public BlockPosition {
        Objects.requireNonNull(world, "World identity is required.");
    }

    public static BlockPosition of(Block block) {
        return new BlockPosition(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public Location location() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? null : new Location(loaded, x, y, z);
    }

    public ChunkPosition chunk() {
        return new ChunkPosition(world, x >> 4, z >> 4);
    }

    public record ChunkPosition(UUID world, int x, int z) {
        public ChunkPosition {
            Objects.requireNonNull(world, "World identity is required.");
        }
    }
}
