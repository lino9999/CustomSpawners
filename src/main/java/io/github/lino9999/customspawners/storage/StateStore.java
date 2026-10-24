package io.github.lino9999.customspawners.storage;

import io.github.lino9999.customspawners.model.BlockPosition;
import io.github.lino9999.customspawners.model.Boost;
import io.github.lino9999.customspawners.model.PlacedSpawner;
import io.github.lino9999.customspawners.model.StackKey;
import io.github.lino9999.customspawners.service.BoostRegistry;
import io.github.lino9999.customspawners.service.PlayerProgress;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class StateStore implements AutoCloseable {
    private final JavaPlugin plugin;
    private final Path file;
    private final ExecutorService writer =
            Executors.newSingleThreadExecutor(
                    task -> {
                        Thread thread = new Thread(task, "CustomSpawners-storage");
                        thread.setDaemon(true);
                        return thread;
                    });
    private boolean loaded;

    public StateStore(JavaPlugin plugin) {
        this.plugin = plugin;
        file = plugin.getDataFolder().toPath().resolve("state.yml");
    }

    public void load(SpawnerRepository repository, PlayerProgress progress, BoostRegistry boosts)
            throws Exception {
        if (!Files.exists(file)) {
            loaded = true;
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        LoadedState state = decode(yaml, repository);
        state.spawners().forEach(repository::put);
        state.boosts().forEach(boosts::put);
        state.progress()
                .forEach(
                        (player, counts) ->
                                counts.forEach((type, count) -> progress.set(player, type, count)));
        loaded = true;
    }

    static LoadedState decode(YamlConfiguration yaml, SpawnerRepository repository)
            throws IOException {
        if (exact(yaml.get("schema"), 1, 1, "schema") != 1)
            throw new IOException("Unsupported state schema; state.yml was preserved.");
        List<PlacedSpawner> pending = new ArrayList<>();
        Set<BlockPosition> positions = new HashSet<>();
        Object rawSpawners = yaml.get("spawners");
        if (!(rawSpawners instanceof List<?> rows))
            throw new IOException("Spawner state must be a list.");
        for (Object rawRow : rows) {
            if (!(rawRow instanceof Map<?, ?> row))
                throw new IOException("Invalid spawner state row.");
            BlockPosition position =
                    new BlockPosition(
                            uuid(required(row, "world")),
                            (int) exact(row.get("x"), Integer.MIN_VALUE, Integer.MAX_VALUE, "x"),
                            (int) exact(row.get("y"), Integer.MIN_VALUE, Integer.MAX_VALUE, "y"),
                            (int) exact(row.get("z"), Integer.MIN_VALUE, Integer.MAX_VALUE, "z"));
            if (!positions.add(position) || repository.get(position) != null)
                throw new IOException("Duplicate spawner position.");
            String owner = required(row, "owner");
            PlacedSpawner spawner =
                    new PlacedSpawner(position, owner.isEmpty() ? null : uuid(owner));
            Object rawStacks = row.get("stacks");
            if (!(rawStacks instanceof List<?> stacks) || stacks.isEmpty())
                throw new IOException("Missing spawner stacks.");
            Set<StackKey> keys = new HashSet<>();
            for (Object raw : stacks) {
                if (!(raw instanceof Map<?, ?> stack))
                    throw new IOException("Invalid spawner stack.");
                StackKey key =
                        new StackKey(
                                identifier(required(stack, "type")),
                                identifier(required(stack, "tier")));
                if (!keys.add(key)) throw new IOException("Duplicate spawner stack.");
                spawner.add(key, (int) exact(stack.get("amount"), 1, 1_000_000, "amount"));
            }
            spawner.cycles(exact(row.get("cycles"), 0, Long.MAX_VALUE, "cycles"));
            spawner.boost(
                    new Boost(
                            (int) exact(row.get("boost"), 0, 100, "boost"),
                            exact(row.get("expires"), 0, Long.MAX_VALUE, "expires")));
            pending.add(spawner);
        }
        Map<UUID, Boost> pendingBoosts = new LinkedHashMap<>();
        Map<UUID, Map<String, Long>> pendingProgress = new LinkedHashMap<>();
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (yaml.contains("players") && players == null)
            throw new IOException("Invalid player state.");
        if (players != null)
            for (String id : players.getKeys(false)) {
                UUID player = uuid(id);
                if (pendingBoosts.containsKey(player))
                    throw new IOException("Duplicate player state.");
                ConfigurationSection data = players.getConfigurationSection(id);
                if (data == null) throw new IOException("Invalid player state.");
                pendingBoosts.put(
                        player,
                        new Boost(
                                (int) exact(data.get("boost", 0), 0, 100, "player boost"),
                                exact(
                                        data.get("expires", 0L),
                                        0,
                                        Long.MAX_VALUE,
                                        "player expires")));
                ConfigurationSection kills = data.getConfigurationSection("kills");
                if (data.contains("kills") && kills == null)
                    throw new IOException("Invalid kill state.");
                Map<String, Long> counts = new LinkedHashMap<>();
                if (kills != null)
                    for (String type : kills.getKeys(false))
                        counts.put(
                                identifier(type),
                                exact(kills.get(type), 0, Long.MAX_VALUE, "kill count"));
                pendingProgress.put(player, counts);
            }
        return new LoadedState(
                List.copyOf(pending), Map.copyOf(pendingBoosts), Map.copyOf(pendingProgress));
    }

    record LoadedState(
            List<PlacedSpawner> spawners,
            Map<UUID, Boost> boosts,
            Map<UUID, Map<String, Long>> progress) {}

    private static String required(Map<?, ?> row, String key) throws IOException {
        Object value = row.get(key);
        if (!(value instanceof String text)) throw new IOException("Invalid state field: " + key);
        return text;
    }

    static long exact(Object value, long min, long max, String key) throws IOException {
        if (!(value instanceof Number number))
            throw new IOException("Invalid state number: " + key);
        try {
            long result = new BigDecimal(number.toString()).longValueExact();
            if (result < min || result > max)
                throw new IOException("State number is out of range: " + key);
            return result;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IOException("State number must be an exact integer: " + key, exception);
        }
    }

    private static String identifier(String value) throws IOException {
        if (!value.matches("[a-z0-9][a-z0-9_-]{0,63}"))
            throw new IOException("Invalid state identifier.");
        return value;
    }

    private static UUID uuid(String value) throws IOException {
        try {
            UUID result = UUID.fromString(value);
            if (!result.toString().equalsIgnoreCase(value))
                throw new IOException("Invalid state UUID.");
            return result;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid state UUID.", exception);
        }
    }

    public void save(SpawnerRepository repository, PlayerProgress progress, BoostRegistry boosts) {
        if (!loaded) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlacedSpawner spawner : repository.all()) {
            Map<String, Object> row = new LinkedHashMap<>();
            BlockPosition position = spawner.position();
            row.put("world", position.world().toString());
            row.put("x", position.x());
            row.put("y", position.y());
            row.put("z", position.z());
            row.put("owner", spawner.owner() == null ? "" : spawner.owner().toString());
            row.put("cycles", spawner.cycles());
            row.put("boost", spawner.boost().amount());
            row.put("expires", spawner.boost().expiresAt());
            List<Map<String, Object>> stacks = new ArrayList<>();
            spawner.stacks()
                    .forEach(
                            (key, amount) ->
                                    stacks.add(
                                            Map.of(
                                                    "type",
                                                    key.type(),
                                                    "tier",
                                                    key.tier(),
                                                    "amount",
                                                    amount)));
            row.put("stacks", stacks);
            rows.add(row);
        }
        yaml.set("spawners", rows);
        progress.snapshot()
                .forEach((player, kills) -> yaml.set("players." + player + ".kills", kills));
        boosts.all()
                .forEach(
                        (player, boost) -> {
                            yaml.set("players." + player + ".boost", boost.amount());
                            yaml.set("players." + player + ".expires", boost.expiresAt());
                        });
        String snapshot = yaml.saveToString();
        writer.execute(
                () -> {
                    try {
                        write(snapshot);
                    } catch (IOException ex) {
                        plugin.getLogger()
                                .log(
                                        Level.SEVERE,
                                        "Could not save state.yml; previous state was preserved.",
                                        ex);
                    }
                });
    }

    private void write(String snapshot) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling("state.yml.tmp");
        Files.writeString(temporary, snapshot, StandardCharsets.UTF_8);
        if (Files.exists(file))
            Files.copy(
                    file,
                    file.resolveSibling("state.yml.bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS))
                plugin.getLogger().severe("Storage writer did not finish within 30 seconds.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
