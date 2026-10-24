package io.github.lino9999.customspawners.hooks;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.bgsoftware.wildstacker.api.events.EntityStackEvent;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class WildStackerEntities implements Listener {
    private final NamespacedKey typeKey;
    private final NamespacedKey tierKey;

    public WildStackerEntities(JavaPlugin plugin) {
        typeKey = new NamespacedKey(plugin, "spawned-type");
        tierKey = new NamespacedKey(plugin, "spawned-tier");
    }

    public static void stack(LivingEntity entity) {
        WildStackerAPI.getStackedEntity(entity).runStackAsync(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void merge(EntityStackEvent event) {
        var source = event.getEntity().getLivingEntity().getPersistentDataContainer();
        var target = event.getTarget().getLivingEntity().getPersistentDataContainer();
        if (!Objects.equals(
                        source.get(typeKey, PersistentDataType.STRING),
                        target.get(typeKey, PersistentDataType.STRING))
                || !Objects.equals(
                        source.get(tierKey, PersistentDataType.STRING),
                        target.get(tierKey, PersistentDataType.STRING))) event.setCancelled(true);
    }
}
