package com.mineai.commands;

import com.mineai.MineAI;
import com.mineai.RankManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handles /rank [player] — check your own or another player's rank.
 */
public final class RankCommand implements CommandExecutor, TabCompleter {

    private final MineAI plugin;

    public RankCommand(MineAI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + args[0] + "' not found.")
                        .color(NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Usage: /rank <player>")
                    .color(NamedTextColor.RED));
            return true;
        }

        RankManager.Rank rank = plugin.getRankManager().getRank(target.getUniqueId());
        Component display = plugin.getRankManager().getRankDisplayComponent(rank);

        sender.sendMessage(
                Component.text(target.getName() + "'s rank: ").color(NamedTextColor.GOLD)
                        .append(display)
        );

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .toList();
        }
        return List.of();
    }
}
