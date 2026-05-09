package me.horiciastko.bedwars.commands;

import me.horiciastko.bedwars.BedWars;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BedWarsCommand implements CommandExecutor, TabCompleter {

    private final List<SubCommand> subCommands = new ArrayList<>();

    public BedWarsCommand(BedWars plugin) {
        subCommands.add(new JoinCommand(plugin));
        subCommands.add(new LobbyCommand(plugin));
        subCommands.add(new AdminCommand(plugin));
        subCommands.add(new LeaveCommand(plugin));
        subCommands.add(new RejoinCommand(plugin));
        subCommands.add(new StatsCommand(plugin));
        subCommands.add(new PartyCommand(plugin));
    }

    public SubCommand getSubCommand(String name) {
        for (SubCommand sub : subCommands) {
            if (sub.getName().equalsIgnoreCase(name)) return sub;
        }
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(null, "must-be-player"));
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        for (SubCommand sub : subCommands) {
            if (sub.getName().equalsIgnoreCase(args[0])) {
                sub.perform(player, args);
                return true;
            }
        }

        sendHelp(player);
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(" ");
        player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "help-title"));
        for (SubCommand sub : subCommands) {
            if (sub.getName().equalsIgnoreCase("admin"))
                continue;
            player.sendMessage(BedWars.getInstance().getLanguageManager().getMessage(player.getUniqueId(), "help-command").replace("%syntax%", sub.getSyntax()).replace("%description%", sub.getDescription()));
        }
        player.sendMessage(" ");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (!(sender instanceof Player))
            return null;
        Player player = (Player) sender;

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            for (SubCommand sub : subCommands) {
                if (sub.getName().equalsIgnoreCase("admin") && !player.hasPermission("bedwars.admin")) {
                    continue;
                }
                if (sub.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(sub.getName());
                }
            }
            return suggestions;
        }

        for (SubCommand sub : subCommands) {
            if (sub.getName().equalsIgnoreCase(args[0])) {
                return sub.tabComplete(player, args);
            }
        }

        return new ArrayList<>();
    }
}
