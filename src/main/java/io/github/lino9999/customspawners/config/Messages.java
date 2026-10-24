package io.github.lino9999.customspawners.config;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Messages {
    private final JavaPlugin plugin;
    private YamlConfiguration messages;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        try {
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.load(file);
            try (var stream = plugin.getResource("messages.yml")) {
                if (stream == null)
                    throw new IllegalArgumentException("Bundled messages are missing.");
                candidate.setDefaults(
                        YamlConfiguration.loadConfiguration(
                                new InputStreamReader(stream, StandardCharsets.UTF_8)));
            }
            for (String key : candidate.getKeys(true)) {
                if (!(candidate.get(key) instanceof String))
                    throw new IllegalArgumentException("Message must be text: " + key);
            }
            messages = candidate;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Unable to load messages.yml: " + ex.getMessage(), ex);
        }
    }

    public String text(String key, Map<String, ?> values) {
        String message = messages.getString(key);
        if (message == null) message = key;
        for (var value : values.entrySet())
            message = message.replace("{" + value.getKey() + "}", String.valueOf(value.getValue()));
        return color(message);
    }

    public String text(String key) {
        return text(key, Map.of());
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, ?> values) {
        sender.sendMessage(text("prefix") + text(key, values));
    }

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
