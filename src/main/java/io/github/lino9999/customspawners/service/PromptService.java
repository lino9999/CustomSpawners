package io.github.lino9999.customspawners.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class PromptService implements Listener {
    private record Prompt(long expiry, long ticket, Consumer<String> callback) {}

    private final Map<UUID, Prompt> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessions = new ConcurrentHashMap<>();
    private final AtomicLong tickets = new AtomicLong();
    private final JavaPlugin plugin;

    public PromptService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, Consumer<String> callback) {
        long ticket = tickets.incrementAndGet();
        sessions.put(player.getUniqueId(), ticket);
        pending.put(
                player.getUniqueId(),
                new Prompt(
                        System.currentTimeMillis() + 120_000,
                        ticket,
                        Objects.requireNonNull(callback)));
        player.closeInventory();
        player.sendMessage("Enter a YAML value in chat within two minutes, or type cancel.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void chat(AsyncPlayerChatEvent event) {
        Prompt prompt = pending.remove(event.getPlayer().getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        if (prompt.expiry() <= System.currentTimeMillis()) {
            sessions.remove(event.getPlayer().getUniqueId(), prompt.ticket());
            return;
        }
        String text = event.getMessage();
        if (text.equalsIgnoreCase("cancel")) {
            sessions.remove(event.getPlayer().getUniqueId(), prompt.ticket());
            return;
        }
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (plugin.isEnabled()
                                    && event.getPlayer().isOnline()
                                    && sessions.remove(
                                            event.getPlayer().getUniqueId(), prompt.ticket()))
                                prompt.callback().accept(text);
                        });
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        sessions.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        pending.clear();
        sessions.clear();
    }
}
