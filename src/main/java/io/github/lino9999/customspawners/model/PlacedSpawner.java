package io.github.lino9999.customspawners.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlacedSpawner {
    private final BlockPosition position;
    private final UUID owner;
    private final LinkedHashMap<StackKey, Integer> stacks = new LinkedHashMap<>();
    private Boost boost = Boost.NONE;
    private long cycles;
    private long nextSpawnTick;

    public PlacedSpawner(BlockPosition position, UUID owner) {
        this.position = Objects.requireNonNull(position, "Spawner position is required.");
        this.owner = owner;
    }

    public BlockPosition position() {
        return position;
    }

    public UUID owner() {
        return owner;
    }

    public Map<StackKey, Integer> stacks() {
        return Collections.unmodifiableMap(stacks);
    }

    public StackKey first() {
        return stacks.keySet().iterator().next();
    }

    public int total() {
        return stacks.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Boost boost() {
        return boost;
    }

    public void boost(Boost value) {
        boost = Objects.requireNonNull(value, "Spawner boost is required.");
    }

    public long cycles() {
        return cycles;
    }

    public void cycles(long value) {
        cycles = Math.max(0, value);
    }

    public long nextSpawnTick() {
        return nextSpawnTick;
    }

    public void nextSpawnTick(long value) {
        nextSpawnTick = value;
    }

    public void add(StackKey key, int amount) {
        Objects.requireNonNull(key, "Spawner stack identity is required.");
        if (amount < 1 || (long) total() + amount > 1_000_000) {
            throw new IllegalArgumentException("Invalid stack amount.");
        }
        stacks.merge(key, amount, Math::addExact);
    }

    public void remove(StackKey key, int amount) {
        Objects.requireNonNull(key, "Spawner stack identity is required.");
        int current = stacks.getOrDefault(key, 0);
        if (amount < 1 || amount > current) {
            throw new IllegalArgumentException("Invalid removal amount.");
        }
        if (current == amount) stacks.remove(key);
        else stacks.put(key, current - amount);
    }

    public void replace(StackKey previous, StackKey replacement) {
        Objects.requireNonNull(previous, "Previous spawner stack identity is required.");
        Objects.requireNonNull(replacement, "Replacement spawner stack identity is required.");
        int amount = stacks.getOrDefault(previous, 0);
        if (amount == 0 || previous.equals(replacement)) return;
        remove(previous, amount);
        add(replacement, amount);
    }
}
