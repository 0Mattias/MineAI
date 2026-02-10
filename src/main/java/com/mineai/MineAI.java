package com.mineai;

import com.mineai.commands.AiCommand;
import com.mineai.commands.MineAICommand;
import com.mineai.commands.RankCommand;
import com.mineai.commands.RanksCommand;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * MineAI — Main plugin class.
 * An AI-controlled Minecraft server plugin where an LLM acts as a god-like entity.
 *
 * Architecture:
 *   /ai command → RequestManager → JSON file → External watcher → OpenClaw AI → Response JSON
 *   ResponseWatcher → reads response → broadcasts message + executes commands
 */
public final class MineAI extends JavaPlugin implements Listener {

    private static final Duration DEFAULT_COOLDOWN = Duration.ofSeconds(5);

    private RankManager rankManager;
    private CooldownManager cooldownManager;
    private RequestManager requestManager;
    private ResponseWatcher responseWatcher;
    private EventLogger eventLogger;

    @Override
    public void onEnable() {
        Logger log = getLogger();
        long start = System.currentTimeMillis();

        // Ensure data folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize managers
        rankManager = new RankManager(this);
        cooldownManager = new CooldownManager(DEFAULT_COOLDOWN);
        requestManager = new RequestManager(this);
        responseWatcher = new ResponseWatcher(this);
        eventLogger = new EventLogger(this);

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(eventLogger, this);

        // Register commands
        registerCommand("ai", new AiCommand(this));
        MineAICommand mineAICommand = new MineAICommand(this);
        registerCommand("mineai", mineAICommand, mineAICommand);
        registerCommand("rank", new RankCommand(this));
        registerCommand("ranks", new RanksCommand(this));

        // Start response watcher
        responseWatcher.start();

        // Schedule cooldown cleanup every 60 seconds
        Bukkit.getScheduler().runTaskTimer(this, cooldownManager::cleanup, 1200L, 1200L);

        // Update display for all currently online players (in case of reload)
        Bukkit.getOnlinePlayers().forEach(rankManager::updatePlayerDisplay);

        long elapsed = System.currentTimeMillis() - start;
        log.info("MineAI v" + getDescription().getVersion() + " enabled in " + elapsed + "ms");
        log.info("Watching for AI responses in: " + getDataFolder().toPath().resolve("responses"));
    }

    @Override
    public void onDisable() {
        // Stop response watcher
        if (responseWatcher != null) {
            responseWatcher.stop();
        }

        // Save ranks synchronously
        if (rankManager != null) {
            rankManager.saveSync();
        }

        // Clear cooldowns
        if (cooldownManager != null) {
            cooldownManager.clear();
        }

        getLogger().info("MineAI disabled. Farewell, mortals.");
    }

    // ── Chat formatting with rank prefixes ──

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // Replace the default chat renderer with our rank-aware formatter
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            String plainMessage = PlainTextComponentSerializer.plainText().serialize(message);
            return rankManager.formatChatMessage(source, plainMessage);
        });
    }

    // ── Accessors for managers ──

    public RankManager getRankManager() {
        return rankManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public RequestManager getRequestManager() {
        return requestManager;
    }

    public ResponseWatcher getResponseWatcher() {
        return responseWatcher;
    }

    public EventLogger getEventLogger() {
        return eventLogger;
    }

    // ── Helpers ──

    private void registerCommand(String name, Object executor) {
        registerCommand(name, executor, null);
    }

    private void registerCommand(String name, Object executor, Object tabCompleter) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Command '/" + name + "' not found in plugin.yml!");
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) {
            cmd.setExecutor(ce);
        }
        if (tabCompleter instanceof org.bukkit.command.TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }
}
