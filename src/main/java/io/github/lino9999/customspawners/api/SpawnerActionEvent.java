package io.github.lino9999.customspawners.api;

import io.github.lino9999.customspawners.model.PlacedSpawner;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class SpawnerActionEvent extends Event implements Cancellable {
    public enum Action {
        ACCESS,
        PLACE,
        BREAK,
        STACK,
        UPGRADE,
        CONVERT,
        BOOST,
        DROP,
        SPAWN
    }

    private static final HandlerList HANDLERS = new HandlerList();
    private final PlacedSpawner spawner;
    private final Player player;
    private final Action action;
    private boolean cancelled;

    public SpawnerActionEvent(PlacedSpawner spawner, Player player, Action action) {
        this.spawner = spawner;
        this.player = player;
        this.action = action;
    }

    public PlacedSpawner getSpawner() {
        return spawner;
    }

    public Player getPlayer() {
        return player;
    }

    public Action getAction() {
        return action;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean value) {
        cancelled = value;
    }

    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
