package io.github.lino9999.customspawners;

import io.github.lino9999.customspawners.command.SpawnerCommand;
import io.github.lino9999.customspawners.config.Messages;
import io.github.lino9999.customspawners.config.Settings;
import io.github.lino9999.customspawners.config.SettingsValidator;
import io.github.lino9999.customspawners.config.SpawnerCatalog;
import io.github.lino9999.customspawners.gui.Menu;
import io.github.lino9999.customspawners.gui.Menus;
import io.github.lino9999.customspawners.hooks.EconomyBridge;
import io.github.lino9999.customspawners.hooks.ProtectionBridge;
import io.github.lino9999.customspawners.hooks.WildStackerBridge;
import io.github.lino9999.customspawners.listener.SpawnerListener;
import io.github.lino9999.customspawners.service.BoostRegistry;
import io.github.lino9999.customspawners.service.CraftingService;
import io.github.lino9999.customspawners.service.PlayerProgress;
import io.github.lino9999.customspawners.service.PromptService;
import io.github.lino9999.customspawners.service.SpawnerItems;
import io.github.lino9999.customspawners.service.SpawnerService;
import io.github.lino9999.customspawners.spawn.SpawnEngine;
import io.github.lino9999.customspawners.storage.SpawnerRepository;
import io.github.lino9999.customspawners.storage.StateStore;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class CustomSpawnersPlugin extends JavaPlugin {
    private Settings settings;
    private Messages messages;
    private SpawnerCatalog catalog;
    private final SpawnerRepository repository = new SpawnerRepository();
    private final BoostRegistry boosts = new BoostRegistry();
    private final PlayerProgress progress = new PlayerProgress();
    private StateStore store;
    private SpawnerItems items;
    private SpawnEngine engine;
    private EconomyBridge economy;
    private ProtectionBridge protection;
    private SpawnerService service;
    private CraftingService crafting;
    private PromptService prompts;
    private Menus menus;
    private BukkitTask autosave;
    private boolean ready;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            settings = new Settings(loadSettings());
            messages = new Messages(this);
            catalog = new SpawnerCatalog(this);
            catalog.reload();
            WildStackerBridge.verify(this);
            store = new StateStore(this);
            store.load(repository, progress, boosts);
            items = new SpawnerItems(this, catalog);
            economy = new EconomyBridge(this, settings);
            protection = new ProtectionBridge(this, settings);
            engine = new SpawnEngine(this, settings, catalog, repository, boosts, items);
            service =
                    new SpawnerService(
                            this,
                            settings,
                            catalog,
                            repository,
                            items,
                            engine,
                            economy,
                            protection,
                            messages);
            prompts = new PromptService(this);
            menus = new Menus(this);
            crafting = new CraftingService(this, catalog, items, service);
            var manager = getServer().getPluginManager();
            manager.registerEvents(menus, this);
            manager.registerEvents(prompts, this);
            manager.registerEvents(crafting, this);
            SpawnerListener listener = new SpawnerListener(this);
            manager.registerEvents(listener, this);
            WildStackerBridge.connect(this);
            var executor = new SpawnerCommand(this);
            for (String name : List.of("customspawners", "cspawnershop", "cspawnerstats")) {
                var command = Objects.requireNonNull(getCommand(name));
                command.setExecutor(executor);
                command.setTabCompleter(executor);
            }
            engine.start();
            for (var world : Bukkit.getWorlds())
                for (var chunk : world.getLoadedChunks()) listener.discover(chunk);
            crafting.reload();
            getServer()
                    .getServicesManager()
                    .register(SpawnerService.class, service, this, ServicePriority.Normal);
            ready = true;
            scheduleSave();
            getLogger()
                    .info(
                            "CustomSpawners by Lino enabled with "
                                    + catalog.all().size()
                                    + " spawner definitions.");
        } catch (Exception | LinkageError ex) {
            getLogger()
                    .log(
                            Level.SEVERE,
                            "CustomSpawners could not start; existing configuration and state were"
                                    + " preserved.",
                            ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private YamlConfiguration loadSettings() throws Exception {
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(getDataFolder().toPath().resolve("config.yml").toFile());
        try (var input = getResource("config.yml")) {
            if (input == null) throw new IllegalArgumentException("Bundled settings are missing.");
            loaded.setDefaults(
                    YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(input, StandardCharsets.UTF_8)));
            loaded.options().copyDefaults(true);
        }
        SettingsValidator.validate(loaded);
        return loaded;
    }

    public void reloadPlugin() throws Exception {
        WildStackerBridge.verify(this);
        YamlConfiguration candidate = loadSettings();
        new Messages(this);
        catalog.reload();
        settings.replace(candidate);
        messages.reload();
        economy.reload();
        protection.reload();
        closeMenus();
        crafting.reload();
        engine.start();
        scheduleSave();
    }

    public void editCatalog(String path, Object value) {
        catalog.edit(path, value);
        closeMenus();
        crafting.reload();
        engine.start();
    }

    public void editSetting(String path, Object value) throws Exception {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.loadFromString(settings.configuration().saveToString());
        candidate.set(path, value);
        SettingsValidator.validate(candidate);
        var file = getDataFolder().toPath().resolve("config.yml");
        var temporary = Files.createTempFile(file.getParent(), "config-", ".tmp");
        try {
            Files.writeString(temporary, candidate.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        settings.replace(candidate);
        closeMenus();
        economy.reload();
        protection.reload();
        engine.start();
        scheduleSave();
    }

    private void closeMenus() {
        if (prompts != null) prompts.clear();
        for (var player : Bukkit.getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu)
                player.closeInventory();
    }

    private void scheduleSave() {
        if (autosave != null) autosave.cancel();
        long ticks = settings.integer("storage.autosave-seconds") * 20L;
        autosave =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                this,
                                () -> {
                                    boosts.prune(System.currentTimeMillis());
                                    saveState();
                                },
                                ticks,
                                ticks);
    }

    public void saveState() {
        if (ready && store != null) store.save(repository, progress, boosts);
    }

    @Override
    public void onDisable() {
        closeMenus();
        if (autosave != null) autosave.cancel();
        if (engine != null) engine.stop();
        if (crafting != null) crafting.clear();
        if (prompts != null) prompts.clear();
        saveState();
        if (store != null) store.close();
        ready = false;
        getServer().getServicesManager().unregisterAll(this);
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public SpawnerCatalog catalog() {
        return catalog;
    }

    public SpawnerRepository repository() {
        return repository;
    }

    public BoostRegistry boosts() {
        return boosts;
    }

    public PlayerProgress progress() {
        return progress;
    }

    public SpawnerItems items() {
        return items;
    }

    public SpawnEngine engine() {
        return engine;
    }

    public SpawnerService service() {
        return service;
    }

    public PromptService prompts() {
        return prompts;
    }

    public Menus menus() {
        return menus;
    }
}
