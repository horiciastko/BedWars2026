package me.horiciastko.bedwars.commands;

import me.horiciastko.bedwars.BedWars;
import me.horiciastko.bedwars.models.Arena;
import me.horiciastko.bedwars.utils.SerializationUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class LobbyCommand implements SubCommand {

    private final BedWars plugin;

    public LobbyCommand(BedWars plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "lobby";
    }

    @Override
    public String getDescription() {
        return "Return to the main lobby";
    }

    @Override
    public String getSyntax() {
        return "/bw lobby";
    }

    @Override
    public void perform(Player player, String[] args) {
        Arena arena = plugin.getArenaManager().getPlayerArena(player);
        if (arena != null) {
            plugin.getArenaManager().leaveArena(player);
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "lobby-left"));
        }

        String lobbyStr = plugin.getDatabaseManager().getSetting("main_lobby");
        if (lobbyStr == null || lobbyStr.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "lobby-not-set"));
            return;
        }

        Location lobbyLoc = SerializationUtils.stringToLocation(lobbyStr);
        if (lobbyLoc == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage(player.getUniqueId(), "lobby-error"));
            return;
        }

        player.teleport(lobbyLoc);
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        return new ArrayList<>();
    }
}
