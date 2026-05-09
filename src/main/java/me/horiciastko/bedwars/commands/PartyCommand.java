package me.horiciastko.bedwars.commands;

import lombok.RequiredArgsConstructor;
import me.horiciastko.bedwars.BedWars;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class PartyCommand implements SubCommand {

    private final BedWars plugin;

    @Override
    public String getName() {
        return "party";
    }

    @Override
    public String getDescription() {
        return "Create and manage a BedWars party";
    }

    @Override
    public String getSyntax() {
        return "/bw party <invite|accept|leave|kick|list|disband>";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (args.length < 2) {
            sendHelp(player);
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "invite":
                invite(player, args);
                return;
            case "accept":
                accept(player);
                return;
            case "leave":
                leave(player);
                return;
            case "kick":
                kick(player, args);
                return;
            case "list":
                list(player);
                return;
            case "disband":
                disband(player);
                return;
            default:
                sendHelp(player);
        }
    }

    private void invite(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /bw party invite <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cThat player is not online.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cYou cannot invite yourself.");
            return;
        }

        boolean ok = plugin.getPartyManager().invite(player, target);
        if (!ok) {
            player.sendMessage("§cYou must be the party leader to invite players.");
            return;
        }

        player.sendMessage("§aInvited " + target.getName() + " to your party.");
        target.sendMessage("§e" + player.getName() + " invited you to a party. Type §f/bw party accept§e to join.");
    }

    private void accept(Player player) {
        UUID leaderId = plugin.getPartyManager().getInviteLeader(player.getUniqueId());
        if (leaderId == null) {
            player.sendMessage("§cYou do not have any pending party invite.");
            return;
        }

        boolean ok = plugin.getPartyManager().acceptInvite(player);
        if (!ok) {
            player.sendMessage("§cYour party invite expired or is no longer valid.");
            return;
        }

        Player leader = Bukkit.getPlayer(leaderId);
        String leaderName = leader != null ? leader.getName() : "leader";

        player.sendMessage("§aJoined " + leaderName + "'s party.");
        if (leader != null) {
            leader.sendMessage("§a" + player.getName() + " joined your party.");
        }
    }

    private void leave(Player player) {
        me.horiciastko.bedwars.logic.PartyManager.Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage("§cYou are not in a party.");
            return;
        }

        boolean wasLeader = party.getLeader().equals(player.getUniqueId());
        plugin.getPartyManager().leaveParty(player.getUniqueId());

        if (wasLeader) {
            player.sendMessage("§eYou left and disbanded your party.");
        } else {
            player.sendMessage("§eYou left the party.");
            Player leader = Bukkit.getPlayer(party.getLeader());
            if (leader != null) {
                leader.sendMessage("§e" + player.getName() + " left your party.");
            }
        }
    }

    private void kick(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /bw party kick <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            player.sendMessage("§cThat player is not online.");
            return;
        }

        boolean ok = plugin.getPartyManager().kick(player.getUniqueId(), target.getUniqueId());
        if (!ok) {
            player.sendMessage("§cYou must be the party leader to kick this player.");
            return;
        }

        player.sendMessage("§eKicked " + target.getName() + " from your party.");
        target.sendMessage("§cYou were kicked from the party.");
    }

    private void list(Player player) {
        me.horiciastko.bedwars.logic.PartyManager.Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage("§7You are not in a party.");
            return;
        }

        String names = party.getMembers().stream()
                .map(id -> {
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) {
                        return online.getName();
                    }
                    String offline = Bukkit.getOfflinePlayer(id).getName();
                    return offline != null ? offline : id.toString();
                })
                .collect(Collectors.joining("§7, §f"));

        player.sendMessage("§eParty Leader: §f" + nameOf(party.getLeader()));
        player.sendMessage("§eMembers (" + party.getMembers().size() + "): §f" + names);
    }

    private void disband(Player player) {
        if (!plugin.getPartyManager().isLeader(player.getUniqueId())) {
            player.sendMessage("§cOnly party leader can disband the party.");
            return;
        }

        me.horiciastko.bedwars.logic.PartyManager.Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage("§cYou are not in a party.");
            return;
        }

        for (UUID member : party.getMembers()) {
            if (!member.equals(player.getUniqueId())) {
                Player online = Bukkit.getPlayer(member);
                if (online != null) {
                    online.sendMessage("§eParty was disbanded by the leader.");
                }
            }
        }

        plugin.getPartyManager().disband(player.getUniqueId());
        player.sendMessage("§eParty disbanded.");
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String offline = Bukkit.getOfflinePlayer(uuid).getName();
        return offline != null ? offline : uuid.toString();
    }

    private void sendHelp(Player player) {
        player.sendMessage("§eParty commands:");
        player.sendMessage("§f/bw party invite <player>");
        player.sendMessage("§f/bw party accept");
        player.sendMessage("§f/bw party leave");
        player.sendMessage("§f/bw party kick <player>");
        player.sendMessage("§f/bw party list");
        player.sendMessage("§f/bw party disband");
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("invite", "accept", "leave", "kick", "list", "disband").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && (args[1].equalsIgnoreCase("invite") || args[1].equalsIgnoreCase("kick"))) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    names.add(online.getName());
                }
            }
            return names.stream()
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
