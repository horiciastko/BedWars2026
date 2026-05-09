package me.horiciastko.bedwars.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridges a standalone command (e.g. /party, /join, /leave) to the
 * corresponding {@link SubCommand} implementation, so all logic stays
 * in one place. The SubCommand interface expects args[0] to be the
 * sub-command name (as it would appear after /bw), so this executor
 * prepends the sub-command name to the args array before delegating.
 */
public class StandaloneCommandExecutor implements CommandExecutor, TabCompleter {

    private final SubCommand subCommand;

    public StandaloneCommandExecutor(SubCommand subCommand) {
        this.subCommand = subCommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cYou must be a player to use this command.");
            return true;
        }
        subCommand.perform((Player) sender, prepend(subCommand.getName(), args));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return new ArrayList<>();
        return subCommand.tabComplete((Player) sender, prepend(subCommand.getName(), args));
    }

    /**
     * Prepends {@code name} as args[0] so the SubCommand receives the same
     * array structure as when called from BedWarsCommand.
     */
    private static String[] prepend(String name, String[] args) {
        String[] shifted = new String[args.length + 1];
        shifted[0] = name;
        System.arraycopy(args, 0, shifted, 1, args.length);
        return shifted;
    }
}
