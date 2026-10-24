package io.github.lino9999.customspawners.spawn;

import io.github.lino9999.customspawners.config.Messages;
import io.github.lino9999.customspawners.config.Settings;
import io.github.lino9999.customspawners.config.SpawnerCatalog;
import io.github.lino9999.customspawners.model.BlockPosition;
import io.github.lino9999.customspawners.model.PlacedSpawner;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

final class SpawnerVisuals {
    private final Settings settings;
    private final SpawnerCatalog catalog;
    private final NamespacedKey marker;
    private final Map<BlockPosition, TextDisplay> displays = new HashMap<>();

    SpawnerVisuals(JavaPlugin plugin, Settings settings, SpawnerCatalog catalog) {
        this.settings = settings;
        this.catalog = catalog;
        marker = new NamespacedKey(plugin, "spawner-display");
    }

    void clean(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof TextDisplay
                    && entity.getPersistentDataContainer().has(marker, PersistentDataType.STRING)) {
                entity.remove();
            }
        }
    }

    void update(PlacedSpawner spawner, String reason) {
        if (!settings.bool("spawning.holograms") || spawner.stacks().isEmpty()) {
            remove(spawner.position());
            return;
        }
        Location location = spawner.position().location();
        if (location == null
                || !location.getWorld()
                        .isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4))
            return;
        TextDisplay display = displays.get(spawner.position());
        if (display == null || !display.isValid()) {
            display =
                    location.getWorld()
                            .spawn(
                                    location.clone().add(0.5, 1.5, 0.5),
                                    TextDisplay.class,
                                    entity -> {
                                        entity.setBillboard(Display.Billboard.CENTER);
                                        entity.setPersistent(false);
                                        entity.setGravity(false);
                                        entity.setInvulnerable(true);
                                        entity.setViewRange(0.6f);
                                        entity.getPersistentDataContainer()
                                                .set(
                                                        marker,
                                                        PersistentDataType.STRING,
                                                        spawner.position().toString());
                                    });
            if (!display.isValid()) return;
            displays.put(spawner.position(), display);
        }
        var type = catalog.get(spawner.first().type());
        var tier = catalog.tier(spawner.first());
        String name = type == null ? spawner.first().type() : type.name();
        String tierName = tier == null ? spawner.first().tier() : tier.name();
        String text = name + " &7x" + spawner.total() + "\n&7" + tierName;
        if (reason != null && !reason.isBlank()) text += "\n&8" + reason;
        display.setText(Messages.color(text));
    }

    void pulse(PlacedSpawner spawner) {
        if (!settings.bool("spawning.particles") || spawner.stacks().isEmpty()) return;
        var tier = catalog.tier(spawner.first());
        if (tier == null || tier.particle().isBlank()) return;
        Location location = spawner.position().location();
        if (location == null
                || !location.getWorld()
                        .isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4))
            return;
        try {
            Particle particle =
                    Particle.valueOf(tier.particle().toUpperCase(java.util.Locale.ROOT));
            if (particle.getDataType() == Void.class) {
                location.getWorld()
                        .spawnParticle(particle, location.add(0.5, 1, 0.5), 5, 0.35, 0.25, 0.35, 0);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    void remove(BlockPosition position) {
        TextDisplay display = displays.remove(position);
        if (display != null && display.isValid()) display.remove();
    }

    void clear() {
        for (TextDisplay display : displays.values()) {
            if (display.isValid()) display.remove();
        }
        displays.clear();
    }
}
