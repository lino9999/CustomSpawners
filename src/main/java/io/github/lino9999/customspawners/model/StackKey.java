package io.github.lino9999.customspawners.model;

public record StackKey(String type, String tier) {
    public StackKey {
        if (type == null || tier == null || type.isBlank() || tier.isBlank()) {
            throw new IllegalArgumentException("Spawner type and tier are required.");
        }
    }
}
