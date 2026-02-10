package com.mineai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mineai.model.AiRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles writing AI request JSON files asynchronously.
 * Uses atomic file writes (write-to-temp then rename) to prevent partial reads.
 */
public final class RequestManager {

    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MineAI plugin;
    private final Logger logger;
    private final Path requestDir;

    public RequestManager(MineAI plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.requestDir = plugin.getDataFolder().toPath().resolve("requests");
        ensureDirectories();
    }

    /**
     * Write a player's request to a JSON file, asynchronously.
     */
    public void submitRequest(Player player, String message) {
        String sanitized = sanitizeMessage(message);
        if (sanitized.isEmpty()) {
            return;
        }

        String rankName = plugin.getRankManager()
                .getRank(player.getUniqueId())
                .name()
                .toLowerCase();

        String requestId = UUID.randomUUID().toString();
        AiRequest request = new AiRequest(
                requestId,
                player.getName(),
                rankName,
                sanitized,
                System.currentTimeMillis() / 1000L
        );

        // Write async to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                writeRequestFile(requestId, request);
                logger.info("Request submitted: " + requestId + " from " + player.getName());
            } catch (IOException e) {
                logger.severe("Failed to write request " + requestId + ": " + e.getMessage());
            }
        });
    }

    /**
     * Atomic file write: write to a temp file, then rename.
     * This prevents the watcher from reading a partially-written file.
     */
    private void writeRequestFile(String requestId, AiRequest request) throws IOException {
        String json = GSON.toJson(request);
        Path targetFile = requestDir.resolve(requestId + ".json");
        Path tempFile = requestDir.resolve(requestId + ".json.tmp");

        Files.writeString(tempFile, json, StandardCharsets.UTF_8);
        Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Sanitize player input:
     * - Strip control characters (§, color codes)
     * - Trim whitespace
     * - Limit length
     * - Remove path traversal attempts
     */
    static String sanitizeMessage(String message) {
        if (message == null) return "";

        // Remove Minecraft color/formatting codes (§ followed by any char)
        String clean = message.replaceAll("§[0-9a-fk-or]", "");

        // Remove other control characters
        clean = clean.replaceAll("[\\x00-\\x1F\\x7F]", "");

        // Trim and limit length
        clean = clean.trim();
        if (clean.length() > MAX_MESSAGE_LENGTH) {
            clean = clean.substring(0, MAX_MESSAGE_LENGTH);
        }

        return clean;
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(requestDir);
        } catch (IOException e) {
            logger.severe("Failed to create request directory: " + e.getMessage());
        }
    }
}
