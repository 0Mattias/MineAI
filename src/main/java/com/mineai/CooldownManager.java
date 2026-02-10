package com.mineai;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player cooldowns for the /ai command.
 * Thread-safe and auto-cleans expired entries.
 */
public final class CooldownManager {

    private final Duration cooldownDuration;
    private final Map<UUID, Instant> cooldowns = new ConcurrentHashMap<>();

    public CooldownManager(Duration cooldownDuration) {
        this.cooldownDuration = cooldownDuration;
    }

    /**
     * Check if a player is on cooldown.
     *
     * @return remaining seconds, or 0 if not on cooldown
     */
    public long getRemainingSeconds(UUID playerId) {
        Instant expiry = cooldowns.get(playerId);
        if (expiry == null) return 0;

        long remaining = Duration.between(Instant.now(), expiry).getSeconds();
        if (remaining <= 0) {
            cooldowns.remove(playerId);
            return 0;
        }
        return remaining;
    }

    /**
     * Check if a player is currently on cooldown.
     */
    public boolean isOnCooldown(UUID playerId) {
        return getRemainingSeconds(playerId) > 0;
    }

    /**
     * Put a player on cooldown starting now.
     */
    public void setCooldown(UUID playerId) {
        cooldowns.put(playerId, Instant.now().plus(cooldownDuration));
    }

    /**
     * Remove all expired entries to prevent memory leaks.
     * Should be called periodically (e.g., every minute).
     */
    public void cleanup() {
        Instant now = Instant.now();
        cooldowns.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    /**
     * Clear all cooldowns (e.g., on plugin disable).
     */
    public void clear() {
        cooldowns.clear();
    }
}
