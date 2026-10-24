package io.github.lino9999.customspawners.hooks;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class WildStackerBridge {
    private WildStackerBridge() {}

    public static void connect(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("WildStacker"))
            plugin.getServer()
                    .getPluginManager()
                    .registerEvents(new WildStackerEntities(plugin), plugin);
    }

    public static void stack(JavaPlugin plugin, org.bukkit.entity.Entity entity) {
        if (entity instanceof org.bukkit.entity.LivingEntity living
                && plugin.getServer().getPluginManager().isPluginEnabled("WildStacker"))
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (living.isValid()
                                        && !living.isDead()
                                        && plugin.getServer()
                                                .getPluginManager()
                                                .isPluginEnabled("WildStacker"))
                                    WildStackerEntities.stack(living);
                            });
    }

    public static void verify(JavaPlugin plugin) {
        var installed = plugin.getServer().getPluginManager().getPlugin("WildStacker");
        if (installed == null) return;
        if (!installed.isEnabled() || !(installed instanceof JavaPlugin wildStacker))
            throw new IllegalStateException(
                    "WildStacker is installed but not enabled. Install a WildStacker build"
                        + " supporting this server, or remove it before starting CustomSpawners.");
        List<String> conflicts = new ArrayList<>();
        for (String path :
                List.of(
                        "spawners.enabled",
                        "spawners.spawners-override.enabled",
                        "spawners.listen-paper-pre-spawn-event")) {
            if (wildStacker.getConfig().getBoolean(path, true)) conflicts.add(path + ": false");
        }
        if (!conflicts.isEmpty())
            throw new IllegalStateException(
                    "WildStacker conflict: set "
                            + String.join(", ", conflicts)
                            + " in plugins/WildStacker/config.yml and restart. CustomSpawners must"
                            + " own spawner blocks; WildStacker can continue stacking mobs and"
                            + " dropped items. Existing data has been preserved.");
        plugin.getLogger()
                .info(
                        "WildStacker "
                                + installed.getPluginMeta().getVersion()
                                + " detected: CustomSpawners manages spawners; WildStacker manages"
                                + " entity and item stacks.");
    }
}
