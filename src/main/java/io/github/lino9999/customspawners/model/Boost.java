package io.github.lino9999.customspawners.model;

public record Boost(int amount, long expiresAt) {
    public static final Boost NONE = new Boost(0, 0);

    public Boost {
        if (amount < 0 || amount > 100 || expiresAt < 0) {
            throw new IllegalArgumentException("Invalid boost.");
        }
    }

    public int activeAmount(long now) {
        return expiresAt == Long.MAX_VALUE || expiresAt > now ? amount : 0;
    }
}
