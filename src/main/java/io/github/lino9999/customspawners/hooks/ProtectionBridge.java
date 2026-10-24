package io.github.lino9999.customspawners.hooks;

import io.github.lino9999.customspawners.config.Settings;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ProtectionBridge {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final Map<Plugin, AccessCheck> checks = new HashMap<>();
    private final Set<String> warnings = new HashSet<>();

    public ProtectionBridge(JavaPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public boolean canAccess(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        if (!settings.bool("protection.hooks")) {
            return true;
        }
        return permits("WorldGuard", player, block) && permits("GriefPrevention", player, block);
    }

    public void reload() {
        checks.clear();
    }

    private boolean permits(String name, Player player, Block block) {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(name);
        if (dependency == null) {
            return true;
        }
        if (!dependency.isEnabled()) {
            warn(
                    name,
                    "Detected "
                            + name
                            + " is disabled; spawner access is blocked while protection hooks are"
                            + " enabled.");
            return false;
        }
        try {
            AccessCheck check = checks.get(dependency);
            if (check == null) {
                check =
                        name.equals("WorldGuard")
                                ? worldGuard(dependency)
                                : griefPrevention(dependency);
                checks.put(dependency, check);
            }
            return check.test(player, block);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            warn(
                    name,
                    "The "
                            + name
                            + " protection API is incompatible or failed; spawner access is"
                            + " blocked. "
                            + exception.getClass().getSimpleName());
            checks.put(dependency, (checkedPlayer, checkedBlock) -> false);
            return false;
        }
    }

    private AccessCheck griefPrevention(Plugin dependency) throws ReflectiveOperationException {
        Method allow =
                dependency
                        .getClass()
                        .getMethod("allowBuild", Player.class, Location.class, Material.class);
        return (player, block) ->
                allow.invoke(dependency, player, block.getLocation(), block.getType()) == null;
    }

    private AccessCheck worldGuard(Plugin dependency) throws ReflectiveOperationException {
        ClassLoader loader = dependency.getClass().getClassLoader();
        Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
        Class<?> localPlayerClass = Class.forName("com.sk89q.worldguard.LocalPlayer", true, loader);
        Class<?> worldClass = Class.forName("com.sk89q.worldedit.world.World", true, loader);
        Class<?> locationClass = Class.forName("com.sk89q.worldedit.util.Location", true, loader);
        Class<?> stateFlagClass =
                Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, loader);
        Class<?> flagsClass =
                Class.forName("com.sk89q.worldguard.protection.flags.Flags", true, loader);
        Class<?> adapterClass =
                Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader);
        Method platformMethod = worldGuardClass.getMethod("getPlatform");
        Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
        Object platform = platformMethod.invoke(worldGuard);
        Method sessionMethod = platformMethod.getReturnType().getMethod("getSessionManager");
        Object sessions = sessionMethod.invoke(platform);
        Method hasBypass =
                sessionMethod.getReturnType().getMethod("hasBypass", localPlayerClass, worldClass);
        Method containerMethod = platformMethod.getReturnType().getMethod("getRegionContainer");
        Object container = containerMethod.invoke(platform);
        Method queryMethod = containerMethod.getReturnType().getMethod("createQuery");
        Object query = queryMethod.invoke(container);
        Object flags = Array.newInstance(stateFlagClass, 1);
        Array.set(flags, 0, flagsClass.getField("BUILD").get(null));
        Method testState =
                queryMethod
                        .getReturnType()
                        .getMethod("testState", locationClass, localPlayerClass, flags.getClass());
        Method wrap = dependency.getClass().getMethod("wrapPlayer", Player.class);
        Method adaptWorld = adapterClass.getMethod("adapt", World.class);
        Method adaptLocation = adapterClass.getMethod("adapt", Location.class);
        return (player, block) -> {
            Object localPlayer = wrap.invoke(dependency, player);
            Object world = adaptWorld.invoke(null, block.getWorld());
            if (Boolean.TRUE.equals(hasBypass.invoke(sessions, localPlayer, world))) {
                return true;
            }
            Object location = adaptLocation.invoke(null, block.getLocation());
            return Boolean.TRUE.equals(testState.invoke(query, location, localPlayer, flags));
        };
    }

    private void warn(String key, String message) {
        if (warnings.add(key)) {
            plugin.getLogger().warning(message);
        }
    }

    @FunctionalInterface
    private interface AccessCheck {
        boolean test(Player player, Block block) throws ReflectiveOperationException;
    }
}
