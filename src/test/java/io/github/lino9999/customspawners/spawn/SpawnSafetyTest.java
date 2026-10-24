package io.github.lino9999.customspawners.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

final class SpawnSafetyTest {
    @Test
    void rejectsSolidBlocksIntersectingTheBodyButNotAdjacentBlocks() {
        Terrain terrain = new Terrain();
        SpawnSafety safety = new SpawnSafety();
        BoundingBox body = new BoundingBox(0, 64, 0, 1, 66, 1);
        terrain.blocks.put(new Position(1, 64, 0), Material.STONE);
        assertTrue(safety.clear(terrain.world, body, false));
        terrain.blocks.put(new Position(0, 65, 0), Material.STONE);
        assertFalse(safety.clear(terrain.world, body, false));
    }

    @Test
    void requiresWaterForAquaticEntitiesAndRejectsLava() {
        Terrain terrain = new Terrain();
        SpawnSafety safety = new SpawnSafety();
        BoundingBox body = new BoundingBox(0, 64, 0, 1, 65, 1);
        assertFalse(safety.clear(terrain.world, body, true));
        terrain.blocks.put(new Position(0, 64, 0), Material.WATER);
        assertTrue(safety.clear(terrain.world, body, true));
        assertFalse(safety.clear(terrain.world, body, false));
        terrain.blocks.put(new Position(0, 64, 0), Material.LAVA);
        assertFalse(safety.clear(terrain.world, body, true));
    }

    @Test
    void neverReadsBlocksAcrossAnUnloadedChunkBoundary() {
        Terrain terrain = new Terrain();
        terrain.loadedChunkX = 0;
        assertFalse(
                new SpawnSafety()
                        .clear(terrain.world, new BoundingBox(15.5, 64, 0, 16.5, 65, 1), false));
        assertEquals(1, terrain.blockReads);
        assertEquals(0, terrain.unloadedBlockReads);
    }

    @Test
    void boundsHeightAndCollisionWorkBeforeReadingBlocks() {
        Terrain terrain = new Terrain();
        SpawnSafety safety = new SpawnSafety();
        assertFalse(safety.clear(terrain.world, new BoundingBox(0, -65, 0, 1, -63, 1), false));
        assertFalse(safety.clear(terrain.world, new BoundingBox(0, 319, 0, 1, 321, 1), false));
        assertFalse(safety.clear(terrain.world, new BoundingBox(0, 64, 0, 100, 100, 100), false));
        assertEquals(0, terrain.blockReads);
    }

    @Test
    void usesDetachedEntityDimensionsOncePerTypeAndChecksTheWholeBody() {
        Terrain terrain = new Terrain();
        SpawnSafety safety = new SpawnSafety();
        Location location = new Location(terrain.world, 0.5, 64, 0.5);
        assertTrue(safety.fits(location, EntityType.ZOMBIE));
        terrain.blocks.put(new Position(0, 65, 0), Material.STONE);
        assertFalse(safety.fits(location, EntityType.ZOMBIE));
        assertEquals(1, terrain.entityPreviews);
    }

    @Test
    void rejectsUnloadedOrOutOfBorderCandidatesWithoutReadingBlocks() {
        Terrain terrain = new Terrain();
        SpawnSafety safety = new SpawnSafety();
        terrain.loadedChunkX = 0;
        assertFalse(safety.loaded(new Location(terrain.world, 16, 64, 0)));
        terrain.insideBorder = false;
        assertFalse(safety.loaded(new Location(terrain.world, 0, 64, 0)));
        assertFalse(safety.loaded(new Location(terrain.world, 0, 320, 0)));
        assertEquals(0, terrain.blockReads);
    }

    private record Position(int x, int y, int z) {}

    private static final class Terrain {
        private final Map<Position, Material> blocks = new HashMap<>();
        private int loadedChunkX = Integer.MIN_VALUE;
        private int blockReads;
        private int unloadedBlockReads;
        private int entityPreviews;
        private boolean insideBorder = true;
        private final WorldBorder border =
                proxy(
                        WorldBorder.class,
                        (method, arguments) -> {
                            if (method.equals("isInside")) return insideBorder;
                            throw new UnsupportedOperationException(method);
                        });
        private final World world =
                proxy(
                        World.class,
                        (method, arguments) ->
                                switch (method) {
                                    case "getMinHeight" -> -64;
                                    case "getMaxHeight" -> 320;
                                    case "isChunkLoaded" ->
                                            loadedChunkX == Integer.MIN_VALUE
                                                    || (int) arguments[0] == loadedChunkX;
                                    case "getWorldBorder" -> border;
                                    case "getBlockAt" -> {
                                        int x = (int) arguments[0];
                                        int y = (int) arguments[1];
                                        int z = (int) arguments[2];
                                        if (loadedChunkX != Integer.MIN_VALUE
                                                && x >> 4 != loadedChunkX) unloadedBlockReads++;
                                        blockReads++;
                                        Material material =
                                                blocks.getOrDefault(
                                                        new Position(x, y, z), Material.AIR);
                                        yield proxy(
                                                Block.class,
                                                (blockMethod, blockArguments) ->
                                                        switch (blockMethod) {
                                                            case "getType" -> material;
                                                            case "isPassable" ->
                                                                    material == Material.AIR
                                                                            || material
                                                                                    == Material
                                                                                            .CAVE_AIR
                                                                            || material
                                                                                    == Material
                                                                                            .VOID_AIR
                                                                            || material
                                                                                    == Material
                                                                                            .WATER;
                                                            case "isLiquid" ->
                                                                    material == Material.WATER
                                                                            || material
                                                                                    == Material
                                                                                            .LAVA;
                                                            default ->
                                                                    throw new UnsupportedOperationException(
                                                                            blockMethod);
                                                        });
                                    }
                                    case "createEntity" -> {
                                        entityPreviews++;
                                        Location location = (Location) arguments[0];
                                        yield proxy(
                                                Entity.class,
                                                (entityMethod, entityArguments) -> {
                                                    if (entityMethod.equals("getBoundingBox"))
                                                        return new BoundingBox(
                                                                location.getX() - 0.3,
                                                                location.getY(),
                                                                location.getZ() - 0.3,
                                                                location.getX() + 0.3,
                                                                location.getY() + 1.95,
                                                                location.getZ() + 0.3);
                                                    throw new UnsupportedOperationException(
                                                            entityMethod);
                                                });
                                    }
                                    default -> throw new UnsupportedOperationException(method);
                                });
    }

    private interface Invocation {
        Object call(String method, Object[] arguments);
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (instance, method, arguments) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return switch (method.getName()) {
                                    case "hashCode" -> System.identityHashCode(instance);
                                    case "equals" -> instance == arguments[0];
                                    case "toString" -> type.getSimpleName() + " test proxy";
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                };
                            }
                            return invocation.call(method.getName(), arguments);
                        }));
    }
}
