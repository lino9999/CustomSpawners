package io.github.lino9999.customspawners.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsTest {
    @Test
    void textReadsInheritedDefaultsForExistingConfigurations() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("spawning.amount-expression", "{RAND}");
        YamlConfiguration existing = new YamlConfiguration();
        existing.setDefaults(defaults);
        Settings settings = new Settings(existing);
        assertEquals("{RAND}", settings.text("spawning.amount-expression"));
        assertEquals("", settings.text("missing"));
        existing.set("spawning.amount-expression", "2 * {RAND}");
        assertEquals("2 * {RAND}", settings.text("spawning.amount-expression"));
    }
}
