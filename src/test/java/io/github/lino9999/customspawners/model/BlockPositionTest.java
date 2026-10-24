package io.github.lino9999.customspawners.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class BlockPositionTest {
    @Test
    void negativeCoordinatesUseFloorDivisionAtChunkBoundaries() {
        UUID world = UUID.randomUUID();
        int[][] cases = {
            {-33, -3}, {-32, -2}, {-17, -2}, {-16, -1}, {-1, -1}, {0, 0}, {15, 0}, {16, 1}, {31, 1},
            {32, 2}
        };
        for (int[] coordinates : cases) {
            BlockPosition position = new BlockPosition(world, coordinates[0], -64, coordinates[0]);
            assertEquals(
                    new BlockPosition.ChunkPosition(world, coordinates[1], coordinates[1]),
                    position.chunk());
        }
    }

    @Test
    void worldIdentitySeparatesCoincidentBlocksAndChunks() {
        BlockPosition first = new BlockPosition(UUID.randomUUID(), -1, 10, 20);
        BlockPosition second = new BlockPosition(UUID.randomUUID(), -1, 10, 20);
        assertNotEquals(first, second);
        assertNotEquals(first.chunk(), second.chunk());
    }

    @Test
    void chunkIndexIgnoresHeightButBlockIdentityKeepsIt() {
        UUID world = UUID.randomUUID();
        BlockPosition lower = new BlockPosition(world, 1, -64, -1);
        BlockPosition upper = new BlockPosition(world, 1, 319, -1);
        assertNotEquals(lower, upper);
        assertEquals(lower.chunk(), upper.chunk());
    }

    @Test
    void missingWorldCannotEnterBlockOrChunkIndexes() {
        assertThrows(NullPointerException.class, () -> new BlockPosition(null, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> new BlockPosition.ChunkPosition(null, 0, 0));
    }
}
