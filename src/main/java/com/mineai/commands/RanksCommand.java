package com.mineai.commands;

import com.mineai.MineAI;
import com.mineai.RankManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Handles /ranks — display the full rank hierarchy.
 */
public final class RanksCommand implements CommandExecutor {

    private final MineAI plugin;

    public RanksCommand(MineAI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(
                Component.text("═══ ").color(NamedTextColor.DARK_GRAY)
                        .append(Component.text("⚡ MineAI Rank Hierarchy ⚡")
                                .color(NamedTextColor.GOLD)
                                .decorate(TextDecoration.BOLD))
                        .append(Component.text(" ═══").color(NamedTextColor.DARK_GRAY))
        );
        sender.sendMessage(Component.empty());

        RankManager rankManager = plugin.getRankManager();
        for (RankManager.Rank rank : rankManager.getAllRanks()) {
            sender.sendMessage(rankManager.getRankDisplayComponent(rank));
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Ranks are bestowed by MineAI.")
                .color(NamedTextColor.GRAY)
                .decorate(TextDecoration.ITALIC));
        sender.sendMessage(Component.empty());

        return true;
    }
}
