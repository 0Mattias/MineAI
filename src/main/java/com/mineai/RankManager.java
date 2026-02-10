package com.mineai;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the rank/hierarchy system with persistence and display formatting.
 * Uses Adventure Components for all text rendering.
 */
public final class RankManager {

    /**
     * All available ranks ordered from lowest to highest.
     */
    public enum Rank {
        EXILE("Exile", "<dark_gray>[Exile]</dark_gray>", NamedTextColor.DARK_GRAY, ""),
        PEASANT("Peasant", "<gray>[Peasant]</gray>", NamedTextColor.GRAY, ""),
        CITIZEN("Citizen", "<white>[Citizen]</white>", NamedTextColor.WHITE, ""),
        MERCHANT("Merchant", "<yellow>[Merchant]</yellow>", NamedTextColor.YELLOW, ""),
        SOLDIER("Soldier", "<gold>[Soldier]</gold>", NamedTextColor.GOLD, ""),
        KNIGHT("Knight", "<aqua>[Knight]</aqua>", NamedTextColor.AQUA, ""),
        NOBLE("Noble", "<light_purple>[Noble]</light_purple>", NamedTextColor.LIGHT_PURPLE, ""),
        ARCHMAGE("Archmage", "<dark_purple>✦ [Archmage] ✦</dark_purple>", NamedTextColor.DARK_PURPLE, "✦ "),
        WARLORD("Warlord", "<dark_red>⚔ [Warlord] ⚔</dark_red>", NamedTextColor.DARK_RED, "⚔ "),
        PROPHET("Prophet", "<dark_aqua>✧ [Prophet] ✧</dark_aqua>", NamedTextColor.DARK_AQUA, "✧ "),
        CHOSEN("Chosen", "<gold>✯ [The Chosen One] ✯</gold>", NamedTextColor.GOLD, "✯ "),
        OVERLORD("Overlord", "<dark_red>☠ [Overlord] ☠</dark_red>", NamedTextColor.DARK_RED, "☠ "),
        HEAD_OF_STATE("Head of State", "<dark_red>★ [Head of State] ★</dark_red>", NamedTextColor.DARK_RED, "★ "),
        MINEAI("MineAI", "<dark_red>⚡ [MineAI] ⚡</dark_red>", NamedTextColor.DARK_RED, "⚡ ");

        private final String displayName;
        private final String miniMessageTag;
        private final NamedTextColor color;
        private final String symbolPrefix;

        Rank(String displayName, String miniMessageTag, NamedTextColor color, String symbolPrefix) {
            this.displayName = displayName;
            this.miniMessageTag = miniMessageTag;
            this.color = color;
            this.symbolPrefix = symbolPrefix;
        }

        public String displayName() { return displayName; }
        public String miniMessageTag() { return miniMessageTag; }
        public NamedTextColor color() { return color; }
        public String symbolPrefix() { return symbolPrefix; }

        /**
         * Parse a rank from a string (case-insensitive), with aliases.
         */
        public static Rank fromString(String name) {
            if (name == null) return PEASANT;
            String normalized = name.toLowerCase().replace(" ", "_").replace("-", "_");
            // Handle common aliases
            return switch (normalized) {
                case "headofstate", "head_of_state", "hos" -> HEAD_OF_STATE;
                case "chosen", "chosenone", "chosen_one" -> CHOSEN;
                default -> {
                    try {
                        yield Rank.valueOf(normalized.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        yield PEASANT;
                    }
                }
            };
        }
    }

    private final MineAI plugin;
    private final Logger logger;
    private final File dataFile;
    private final Map<UUID, Rank> playerRanks = new ConcurrentHashMap<>();

    public RankManager(MineAI plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataFile = new File(plugin.getDataFolder(), "ranks.yml");
        loadRanks();
    }

    /**
     * Get a player's rank, defaulting to PEASANT.
     */
    public Rank getRank(UUID playerId) {
        return playerRanks.getOrDefault(playerId, Rank.PEASANT);
    }

    /**
     * Get a player's rank by name (online player lookup).
     */
    public Rank getRank(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            return getRank(player.getUniqueId());
        }
        // Search offline — iterate stored ranks
        // For offline players, we'd need a name->UUID mapping; return PEASANT as fallback
        return Rank.PEASANT;
    }

    /**
     * Set a player's rank and update displays.
     */
    public void setRank(UUID playerId, Rank rank) {
        playerRanks.put(playerId, rank);
        saveRanksAsync();

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            updatePlayerDisplay(player, rank);
        }
    }

    /**
     * Set rank by player name (finds the online player).
     */
    public boolean setRank(String playerName, Rank rank) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) return false;
        setRank(player.getUniqueId(), rank);
        return true;
    }

    /**
     * Update a player's tab list name and scoreboard team based on rank.
     */
    public void updatePlayerDisplay(Player player, Rank rank) {
        // Tab list name with rank prefix
        Component prefix = Component.text(rank.symbolPrefix() + "[" + rank.displayName() + "] ")
                .color(rank.color());
        Component displayName = prefix.append(Component.text(player.getName()).color(NamedTextColor.WHITE));
        player.playerListName(displayName);
        player.displayName(displayName);

        // Scoreboard team for sorting
        updateScoreboardTeam(player, rank);
    }

    /**
     * Update player display for their current rank (convenience method).
     */
    public void updatePlayerDisplay(Player player) {
        updatePlayerDisplay(player, getRank(player.getUniqueId()));
    }

    /**
     * Build the chat format component for a player message.
     */
    public Component formatChatMessage(Player player, String message) {
        Rank rank = getRank(player.getUniqueId());
        Component prefix = Component.text(rank.symbolPrefix() + "[" + rank.displayName() + "] ")
                .color(rank.color());
        return prefix
                .append(Component.text(player.getName()).color(NamedTextColor.WHITE))
                .append(Component.text(": ").color(NamedTextColor.GRAY))
                .append(Component.text(message).color(NamedTextColor.WHITE));
    }

    /**
     * Get a formatted display component for a rank (used in /ranks listing).
     */
    public Component getRankDisplayComponent(Rank rank) {
        return Component.text(" " + rank.symbolPrefix() + "[" + rank.displayName() + "]")
                .color(rank.color())
                .append(Component.text(" — " + rank.name().toLowerCase())
                        .color(NamedTextColor.GRAY));
    }

    // ── Scoreboard Teams ──

    private void updateScoreboardTeam(Player player, Rank rank) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Remove from all existing teams
        for (Team team : scoreboard.getTeams()) {
            if (team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }

        // Team name: sort order prefix + rank name (ensures proper ordering)
        String teamName = String.format("%02d_%s", rank.ordinal(), rank.name().toLowerCase());
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16); // Team name limit
        }

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.color(rank.color());
            Component teamPrefix = Component.text(rank.symbolPrefix() + "[" + rank.displayName() + "] ")
                    .color(rank.color());
            team.prefix(teamPrefix);
        }

        team.addEntry(player.getName());
    }

    // ── Persistence ──

    private void loadRanks() {
        if (!dataFile.exists()) {
            logger.info("No ranks.yml found, starting fresh.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String rankName = config.getString(key, "PEASANT");
                Rank rank = Rank.fromString(rankName);
                playerRanks.put(uuid, rank);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid UUID in ranks.yml: " + key);
            }
        }
        logger.info("Loaded " + playerRanks.size() + " player ranks.");
    }

    private void saveRanksAsync() {
        // Copy the map snapshot for thread safety
        Map<UUID, Rank> snapshot = Map.copyOf(playerRanks);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration config = new YamlConfiguration();
            for (var entry : snapshot.entrySet()) {
                config.set(entry.getKey().toString(), entry.getValue().name());
            }
            try {
                config.save(dataFile);
            } catch (IOException e) {
                logger.severe("Failed to save ranks: " + e.getMessage());
            }
        });
    }

    /**
     * Force a synchronous save (used during shutdown).
     */
    public void saveSync() {
        YamlConfiguration config = new YamlConfiguration();
        for (var entry : playerRanks.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue().name());
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            logger.severe("Failed to save ranks on shutdown: " + e.getMessage());
        }
    }

    /**
     * Get all ranks for listing.
     */
    public Rank[] getAllRanks() {
        return Rank.values();
    }
}
