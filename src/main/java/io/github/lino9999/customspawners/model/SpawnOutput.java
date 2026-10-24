package io.github.lino9999.customspawners.model;

public record SpawnOutput(Kind kind, String value, double chance, int amount) {
    public enum Kind {
        ENTITY,
        ITEM,
        BLOCK,
        COMMAND
    }
}
