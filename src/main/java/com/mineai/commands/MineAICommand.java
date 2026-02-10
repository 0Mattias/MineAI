package com.mineai.commands;

import com.mineai.MineAI;
import com.mineai.MineAIPowers;
import com.mineai.RankManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Handles the /mineai admin command with subcommands for powers, ranks, and announcements.
 * Includes tab completion for all subcommands and arguments.
 */
public final class MineAICommand implements CommandExecutor, TabCompleter {

    private static final List<String> WRATH_COMMANDS = List.of(
            "smite", "fireball", "firestorm", "tntbomb", "arrowrain", "nuke",
            "meteor", "bombardment", "witherstorm", "creeperswarm", "lavaflood",
            "lightningstorm", "encase", "cage", "prison", "launch", "freeze",
            "burn", "tornado", "anvil", "void", "explode", "earthquake", "airstrike"
    );

    private static final List<String> BLESS_COMMANDS = List.of(
            "bless", "curse", "godset", "kit", "feast", "treasure",
            "heal", "fullheal", "shield", "superspeed"
    );

    private static final List<String> MOB_COMMANDS = List.of(
            "spawn", "army", "boss", "rain"
    );

    private static final List<String> SOCIAL_COMMANDS = List.of(
            "say", "announce", "setrank", "ranks"
    );

    private static final List<String> ALL_COMMANDS;
    static {
        ALL_COMMANDS = new ArrayList<>();
        ALL_COMMANDS.addAll(WRATH_COMMANDS);
        ALL_COMMANDS.addAll(BLESS_COMMANDS);
        ALL_COMMANDS.addAll(MOB_COMMANDS);
        ALL_COMMANDS.addAll(SOCIAL_COMMANDS);
    }

    private static final List<String> KIT_TYPES = List.of(
            "starter", "warrior", "mage", "archer", "tank", "god"
    );

    private static final List<String> ENCASE_MATERIALS = List.of(
            "lava", "obsidian", "tnt", "ice", "bedrock"
    );

    private static final List<String> ARMY_TYPES = List.of(
            "zombie", "skeleton", "creeper", "wither_skeleton", "piglin"
    );

    private final MineAI plugin;
    private final MineAIPowers powers;

    public MineAICommand(MineAI plugin) {
        this.plugin = plugin;
        this.powers = new MineAIPowers(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /mineai <command> [args...]")
                    .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Use tab completion to see available commands.")
                    .color(NamedTextColor.GRAY));
            return true;
        }

        String subcommand = args[0].toLowerCase();
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        // Social commands that don't require a target player
        switch (subcommand) {
            case "say" -> {
                if (subArgs.length == 0) {
                    sender.sendMessage(Component.text("Usage: /mineai say <message>").color(NamedTextColor.RED));
                    return true;
                }
                powers.executeSay(String.join(" ", subArgs));
                return true;
            }
            case "announce" -> {
                if (subArgs.length == 0) {
                    sender.sendMessage(Component.text("Usage: /mineai announce <message>").color(NamedTextColor.RED));
                    return true;
                }
                powers.executeAnnounce(String.join(" ", subArgs));
                return true;
            }
            case "ranks" -> {
                powers.executeShowRanks(sender);
                return true;
            }
            case "setrank" -> {
                if (subArgs.length < 2) {
                    sender.sendMessage(Component.text("Usage: /mineai setrank <player> <rank>").color(NamedTextColor.RED));
                    return true;
                }
                powers.executeSetRank(sender, subArgs[0], subArgs[1]);
                return true;
            }
        }

        // All other commands require a target player
        if (subArgs.length == 0) {
            sender.sendMessage(Component.text("Usage: /mineai " + subcommand + " <player> [args...]")
                    .color(NamedTextColor.RED));
            return true;
        }

        String targetName = subArgs[0];
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            sender.sendMessage(Component.text("Player '" + targetName + "' not found or offline.")
                    .color(NamedTextColor.RED));
            return true;
        }

        String[] powerArgs = subArgs.length > 1 ? Arrays.copyOfRange(subArgs, 1, subArgs.length) : new String[0];

        // Dispatch to the powers system
        boolean handled = powers.executePower(subcommand, target, powerArgs);

        if (!handled) {
            sender.sendMessage(Component.text("Unknown command: " + subcommand)
                    .color(NamedTextColor.RED));
            sender.sendMessage(Component.text("Use tab completion to see available commands.")
                    .color(NamedTextColor.GRAY));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filterCompletions(ALL_COMMANDS, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            return switch (sub) {
                case "say", "announce", "ranks" -> List.of();
                default -> getOnlinePlayerNames(args[1]);
            };
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            return switch (sub) {
                case "kit" -> filterCompletions(KIT_TYPES, args[2]);
                case "encase" -> filterCompletions(ENCASE_MATERIALS, args[2]);
                case "army" -> filterCompletions(ARMY_TYPES, args[2]);
                case "setrank" -> filterCompletions(
                        Arrays.stream(RankManager.Rank.values())
                                .map(r -> r.name().toLowerCase())
                                .toList(),
                        args[2]);
                default -> List.of();
            };
        }

        return List.of();
    }

    private List<String> filterCompletions(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream()
                .filter(s -> s.startsWith(lower))
                .toList();
    }

    private List<String> getOnlinePlayerNames(String input) {
        String lower = input.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(lower))
                .toList();
    }
}
