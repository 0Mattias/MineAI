package com.mineai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mineai.model.GameEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Listens for key game events and writes them as JSON files
 * for the AI/watcher to consume. All file I/O is async.
 */
public final class EventLogger implements Listener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MineAI plugin;
    private final Logger logger;
    private final Path eventDir;

    public EventLogger(MineAI plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.eventDir = plugin.getDataFolder().toPath().resolve("events");
        ensureDirectory();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String details = player.hasPlayedBefore()
                ? "Returning player joined the server"
                : "New player joined for the first time!";
        logEvent("join", player.getName(), details);

        // Update their display name on join
        plugin.getRankManager().updatePlayerDisplay(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        logEvent("quit", event.getPlayer().getName(), "Player left the server");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        String deathMessage = event.deathMessage() != null
                ? PlainTextComponentSerializer.plainText().serialize(event.deathMessage())
                : "Unknown cause of death";
        logEvent("death", event.getEntity().getName(), deathMessage);
    }

    /**
     * Log an event asynchronously.
     */
    public void logEvent(String type, String playerName, String details) {
        GameEvent gameEvent = new GameEvent(
                type,
                playerName,
                details,
                System.currentTimeMillis() / 1000L
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                writeEventFile(gameEvent);
            } catch (IOException e) {
                logger.warning("Failed to write event: " + e.getMessage());
            }
        });
    }

    private void writeEventFile(GameEvent event) throws IOException {
        String json = GSON.toJson(event);
        String filename = System.currentTimeMillis() + "_" + event.type();
        Path targetFile = eventDir.resolve(filename + ".json");
        Path tempFile = eventDir.resolve(filename + ".json.tmp");

        Files.writeString(tempFile, json, StandardCharsets.UTF_8);
        Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(eventDir);
        } catch (IOException e) {
            logger.severe("Failed to create events directory: " + e.getMessage());
        }
    }
}
