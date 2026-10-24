package io.github.lino9999.customspawners.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class Settings {
    private FileConfiguration config;

    public Settings(FileConfiguration config) {
        this.config = config;
    }

    public void replace(FileConfiguration value) {
        config = value;
    }

    public boolean bool(String path) {
        return config.getBoolean(path);
    }

    public int integer(String path) {
        return config.getInt(path);
    }

    public double decimal(String path) {
        return config.getDouble(path);
    }

    public String text(String path) {
        String value = config.getString(path);
        return value == null ? "" : value;
    }

    public List<String> strings(String path) {
        return config.getStringList(path);
    }

    public FileConfiguration configuration() {
        return config;
    }
}
