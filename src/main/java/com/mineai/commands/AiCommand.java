package com.mineai.commands;

import com.mineai.MineAI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /ai <message> command.
 * Players use this to talk to the AI god.
 */
public final class AiCommand implements CommandExecutor {

    private final MineAI plugin;

    public AiCommand(MineAI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /ai <message>")
                    .color(NamedTextColor.YELLOW));
            return true;
        }

        // Check cooldown
        long remaining = plugin.getCooldownManager().getRemainingSeconds(player.getUniqueId());
        if (remaining > 0) {
            player.sendMessage(Component.text("⏳ MineAI is contemplating... wait " + remaining + "s")
                    .color(NamedTextColor.GRAY)
                    .decorate(TextDecoration.ITALIC));
            return true;
        }

        // Join all args into a single message
        String message = String.join(" ", args);

        // Submit the request
        plugin.getRequestManager().submitRequest(player, message);
        plugin.getCooldownManager().setCooldown(player.getUniqueId());

        // Feedback to player
        player.sendMessage(
                Component.text("⚡ ").color(NamedTextColor.DARK_RED)
                        .append(Component.text("Your message has been sent to MineAI...")
                                .color(NamedTextColor.GOLD)
                                .decorate(TextDecoration.ITALIC))
        );

        return true;
    }
}
