package com.mineai;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mineai.model.AiResponse;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.logging.Logger;

/**
 * Watches the responses directory for AI response JSON files using Java NIO WatchService.
 * Runs on a dedicated async thread; dispatches command execution to the main thread.
 */
public final class ResponseWatcher {

    private static final Gson GSON = new Gson();
    private static final Component AI_PREFIX = Component.text("⚡ ")
            .color(NamedTextColor.DARK_RED)
            .decorate(TextDecoration.BOLD)
            .append(Component.text("[MineAI] ")
                    .color(NamedTextColor.DARK_RED)
                    .decoration(TextDecoration.BOLD, true));

    private final MineAI plugin;
    private final Logger logger;
    private final Path responseDir;
    private volatile boolean running;
    private Thread watcherThread;

    public ResponseWatcher(MineAI plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.responseDir = plugin.getDataFolder().toPath().resolve("responses");
        ensureDirectory();
    }

    /**
     * Start the watcher on a dedicated daemon thread.
     */
    public void start() {
        if (running) return;
        running = true;

        // First, process any existing response files from before startup
        processExistingFiles();

        watcherThread = new Thread(this::watchLoop, "MineAI-ResponseWatcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
        logger.info("Response watcher started.");
    }

    /**
     * Stop the watcher gracefully.
     */
    public void stop() {
        running = false;
        if (watcherThread != null) {
            watcherThread.interrupt();
            try {
                watcherThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.info("Response watcher stopped.");
    }

    /**
     * Process any response files that existed before the watcher started.
     */
    private void processExistingFiles() {
        try (var stream = Files.list(responseDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::processResponseFile);
        } catch (IOException e) {
            logger.warning("Failed to list existing response files: " + e.getMessage());
        }
    }

    /**
     * Main watch loop using NIO WatchService.
     */
    private void watchLoop() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            responseDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            while (running) {
                WatchKey key;
                try {
                    key = watchService.poll(java.time.Duration.ofSeconds(2));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException e) {
                    break;
                }

                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path filename = pathEvent.context();

                    if (filename.toString().endsWith(".json")) {
                        // Small delay to ensure file is fully written
                        try { Thread.sleep(100); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        processResponseFile(responseDir.resolve(filename));
                    }
                }

                if (!key.reset()) {
                    logger.warning("Response directory watch key invalidated.");
                    break;
                }
            }
        } catch (IOException e) {
            if (running) {
                logger.severe("Response watcher error: " + e.getMessage());
            }
        }
    }

    /**
     * Parse a response JSON file and execute it on the main thread.
     */
    private void processResponseFile(Path file) {
        try {
            if (!Files.exists(file)) return;

            String json = Files.readString(file, StandardCharsets.UTF_8);
            AiResponse response = GSON.fromJson(json, AiResponse.class);

            if (response == null || response.player() == null) {
                logger.warning("Invalid response file (null data): " + file.getFileName());
                Files.deleteIfExists(file);
                return;
            }

            // Execute on the main server thread
            Bukkit.getScheduler().runTask(plugin, () -> executeResponse(response));

            // Delete the processed file
            Files.deleteIfExists(file);
            logger.info("Processed response for " + response.player());

        } catch (JsonSyntaxException e) {
            logger.warning("Malformed response JSON in " + file.getFileName() + ": " + e.getMessage());
            try { Files.deleteIfExists(file); } catch (IOException ignored) {}
        } catch (IOException e) {
            logger.warning("Failed to read response file " + file.getFileName() + ": " + e.getMessage());
        }
    }

    /**
     * Execute a parsed AI response: broadcast the message and run commands.
     * MUST be called on the main thread.
     */
    private void executeResponse(AiResponse response) {
        // Broadcast the AI's message
        if (response.response() != null && !response.response().isEmpty()) {
            Component message = AI_PREFIX.append(
                    Component.text(response.response())
                            .color(NamedTextColor.WHITE)
                            .decoration(TextDecoration.BOLD, false)
            );

            // Send to all players
            Bukkit.broadcast(message);

            // Also target the specific player with a personal indicator
            Player target = Bukkit.getPlayerExact(response.player());
            if (target != null) {
                target.sendMessage(Component.text("  ↳ (directed at you)")
                        .color(NamedTextColor.DARK_GRAY)
                        .decorate(TextDecoration.ITALIC));
            }
        }

        // Execute commands
        if (response.commands() != null) {
            for (String command : response.commands()) {
                if (command == null || command.isBlank()) continue;

                // Strip leading slash if present
                String cmd = command.strip();
                if (cmd.startsWith("/")) {
                    cmd = cmd.substring(1);
                }

                try {
                    logger.info("Executing AI command: " + cmd);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } catch (Exception e) {
                    logger.warning("Failed to execute command '" + cmd + "': " + e.getMessage());
                }
            }
        }
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(responseDir);
        } catch (IOException e) {
            logger.severe("Failed to create responses directory: " + e.getMessage());
        }
    }
}
