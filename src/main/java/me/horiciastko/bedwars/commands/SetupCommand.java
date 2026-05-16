package me.horiciastko.bedwars.commands;

import lombok.RequiredArgsConstructor;
import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class SetupCommand implements SubCommand {

    private final BedWars plugin;

    @Override
    public String getName() {
        return "setupArena";
    }

    @Override
    public String getDescription() {
        return "Create or edit a BedWars arena";
    }

    @Override
    public String getSyntax() {
        return "/bw setupArena <name>";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (!player.hasPermission("bedwars.setup")) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "setup-no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "setup-usage"));
            return;
        }

        String name = args[1];
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            arena = new Arena(name);
            plugin.getArenaManager().addArena(arena);
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "setup-created")
                .replace("%name%", name));
        }

        if (arena.isResetting()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "arena-resetting"));
            return;
        }

        plugin.getArenaManager().setEditArena(player, arena);
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "setup-editing")
            .replace("%name%", name));
        player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "setup-instructions"));
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (!player.hasPermission("bedwars.setup"))
            return new ArrayList<>();
        if (args.length == 2) {
            return plugin.getArenaManager().getArenas().stream().map(Arena::getName).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
